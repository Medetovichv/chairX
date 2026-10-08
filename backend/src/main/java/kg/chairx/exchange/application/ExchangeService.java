package kg.chairx.exchange.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.api.ExchangeResponse;
import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.payment.application.PaymentBalanceService;
import kg.chairx.payment.persistence.PaymentRepository;
import kg.chairx.refund.persistence.RefundRepository;
import kg.chairx.returning.persistence.ReturnRepository;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.persistence.SaleRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Validated
public class ExchangeService {

    private final ExchangeRepository exchanges;
    private final RefundRepository refunds;
    private final ReturnRepository returns;
    private final SaleRepository saleRepository;
    private final SaleService sales;
    private final PaymentRepository payments;
    private final PaymentBalanceService balances;

    public ExchangeService(
            ExchangeRepository exchanges,
            RefundRepository refunds,
            ReturnRepository returns,
            SaleRepository saleRepository,
            SaleService sales,
            PaymentRepository payments,
            PaymentBalanceService balances
    ) {
        this.exchanges = exchanges;
        this.refunds = refunds;
        this.returns = returns;
        this.saleRepository = saleRepository;
        this.sales = sales;
        this.payments = payments;
        this.balances = balances;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExchangeResponse create(
            @NotNull @Valid CreateExchangeRequest request
    ) {
        String fingerprint = fingerprint(request);

        // 1. Проверяем, не был ли обмен уже создан.
        Exchange existing = exchanges
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replay(existing, fingerprint);
        }

        // 2. Получаем возврат товара.
        var saleReturn = returns.find(request.returnId())
                .orElseThrow(() -> new ExchangeRuleViolationException(
                        "RETURN_NOT_FOUND",
                        "Возврат товара не найден"
                ));

        UUID originalSaleId = saleReturn.saleId();

        // 3. Блокируем активную оплату исходной продажи.
        // Это синхронизирует обмены и денежные возвраты
        // по одной продаже.
        payments.lockActiveBySale(originalSaleId)
                .orElseThrow(() -> new ExchangeRuleViolationException(
                        "SALE_NOT_PAID",
                        "Для исходной продажи нет активной оплаты"
                ));

        // 4. Повторно проверяем идемпотентность после блокировки.
        existing = exchanges
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replay(existing, fingerprint);
        }

        // 5. Один возврат нельзя компенсировать дважды.
        if (refunds.existsByReturn(request.returnId())) {
            throw new ExchangeRuleViolationException(
                    "RETURN_ALREADY_REFUNDED",
                    "По этому возврату товара уже возвращены деньги"
            );
        }

        if (exchanges.existsByReturn(request.returnId())) {
            throw new ExchangeRuleViolationException(
                    "RETURN_ALREADY_EXCHANGED",
                    "Этот возврат товара уже использован для обмена"
            );
        }

        // 6. Получаем исходную продажу.
        var originalSale = saleRepository.find(originalSaleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Нарушена целостность данных: исходная продажа не найдена"
                ));

        // 7. Рассчитываем стоимость возвращённого товара
        // по ценам исходной продажи.
        BigDecimal returnedValue = BigDecimal.ZERO;

        for (var returnItem : saleReturn.items()) {

            var originalItem = originalSale.items()
                    .stream()
                    .filter(item ->
                            item.id().equals(returnItem.saleItemId())
                    )
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Позиция возврата отсутствует в исходной продаже"
                    ));

            BigDecimal itemValue = originalItem.unitSalePrice()
                    .multiply(
                            BigDecimal.valueOf(returnItem.quantity())
                    );

            returnedValue = returnedValue.add(itemValue);
        }

        if (returnedValue.signum() <= 0) {
            throw new ExchangeRuleViolationException(
                    "INVALID_RETURN_VALUE",
                    "Стоимость возвращённого товара должна быть положительной"
            );
        }

        // 8. Проверяем доступный финансовый остаток.
        BigDecimal availableBalance =
                balances.availableBalance(originalSaleId);

        if (returnedValue.compareTo(availableBalance) > 0) {
            throw new ExchangeRuleViolationException(
                    "EXCHANGE_AMOUNT_EXCEEDED",
                    "Стоимость возврата превышает доступный остаток оплаты"
            );
        }

        // 9. Создаём детерминированный ключ новой продажи.
        // Повторный запрос обмена не должен создавать
        // вторую продажу.
        UUID saleIdempotencyKey = UUID.nameUUIDFromBytes(
                ("EXCHANGE_SALE:" + request.idempotencyKey())
                        .getBytes(StandardCharsets.UTF_8)
        );

        // 10. Создаём новую продажу.
        // SaleService самостоятельно проверяет товар,
        // склад и резервирует остатки.
        //
        // Всё выполняется внутри одной транзакции.
        var newSale = sales.create(
                new CreateSaleRequest(
                        saleIdempotencyKey,
                        originalSale.customerId(),
                        request.fulfillmentType(),
                        request.items()
                )
        );

        // 11. Рассчитываем финансовую разницу.
        BigDecimal newSaleTotal = newSale.total();

        BigDecimal creditApplied =
                returnedValue.min(newSaleTotal);

        BigDecimal additionalPaymentDue =
                newSaleTotal.subtract(creditApplied);

        BigDecimal refundDue =
                returnedValue.subtract(creditApplied);

        // 12. Создаём запись обмена.
        Exchange exchange = new Exchange(
                UUID.randomUUID(),
                originalSaleId,
                newSale.id(),
                request.returnId(),
                ExchangeStatus.PENDING_SETTLEMENT,
                returnedValue,
                newSaleTotal,
                creditApplied,
                additionalPaymentDue,
                refundDue,
                request.idempotencyKey(),
                fingerprint,
                actor(),
                Instant.now(),
                null,
                null
        );

        if (!exchanges.tryInsert(exchange)) {
            throw new ExchangeRuleViolationException(
                    "EXCHANGE_IDEMPOTENCY_CONFLICT",
                    "Ключ идемпотентности уже используется другим обменом"
            );
        }

        // 13. Если разницы в стоимости нет,
        // автоматически завершаем финансовый расчёт.
        if (additionalPaymentDue.signum() == 0
                && refundDue.signum() == 0) {

            int updated = exchanges.complete(
                    exchange.id(),
                    actor()
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Не удалось завершить обмен без доплаты"
                );
            }

            return get(exchange.id());
        }

        // 14. Если есть доплата или возврат,
        // обмен ожидает финансового расчёта.
        return ExchangeResponse.from(exchange);
    }

    @Transactional(readOnly = true)
    public ExchangeResponse get(
            @NotNull UUID exchangeId
    ) {
        return exchanges.find(exchangeId)
                .map(ExchangeResponse::from)
                .orElseThrow(ExchangeNotFoundException::new);
    }

    /**
     * Повторное выполнение запроса с тем же ключом.
     *
     * Если содержимое запроса совпадает,
     * возвращаем существующий обмен.
     *
     * Если содержимое отличается,
     * отклоняем запрос.
     */
    private ExchangeResponse replay(
            Exchange existing,
            String fingerprint
    ) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new ExchangeRuleViolationException(
                    "EXCHANGE_IDEMPOTENCY_CONFLICT",
                    "Ключ идемпотентности уже использован с другими данными"
            );
        }

        return ExchangeResponse.from(existing);
    }

    /**
     * Создаёт SHA-256 отпечаток запроса.
     *
     * Порядок позиций не влияет на результат.
     * Денежные значения нормализуются.
     */
    private static String fingerprint(
            CreateExchangeRequest request
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(request.returnId())
                .append('|')
                .append(request.fulfillmentType());

        request.items()
                .stream()
                .sorted((a, b) -> {

                    int variant = a.productVariantId()
                            .compareTo(b.productVariantId());

                    if (variant != 0) {
                        return variant;
                    }

                    return a.warehouseId()
                            .compareTo(b.warehouseId());
                })
                .forEach(item -> canonical
                        .append('|')
                        .append(item.productVariantId())
                        .append(':')
                        .append(item.warehouseId())
                        .append(':')
                        .append(item.quantity())
                        .append(':')
                        .append(
                                item.unitSalePrice()
                                        .stripTrailingZeros()
                                        .toPlainString()
                        )
                );

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            canonical.toString()
                                    .getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 недоступен",
                    exception
            );
        }
    }

    /**
     * Текущий авторизованный пользователь.
     */
    private static String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }
}