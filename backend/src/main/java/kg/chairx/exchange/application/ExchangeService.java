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

        Exchange existing = exchanges
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replay(existing, fingerprint);
        }

        var saleReturn = returns.find(request.returnId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Возврат товара не найден"
                ));

        UUID originalSaleId = saleReturn.saleId();

        // Все денежные компенсации исходной продажи
        // используют одну и ту же блокировку оплаты.
        payments.lockActiveBySale(originalSaleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Для исходной продажи нет активной оплаты"
                ));

        // Повторная проверка после получения блокировки.
        existing = exchanges
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replay(existing, fingerprint);
        }

        if (refunds.existsByReturn(request.returnId())) {
            throw new IllegalStateException(
                    "По этому возврату товара уже возвращены деньги"
            );
        }

        if (exchanges.existsByReturn(request.returnId())) {
            throw new IllegalStateException(
                    "Этот возврат товара уже использован для обмена"
            );
        }

        var originalSale = saleRepository.find(originalSaleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Исходная продажа не найдена"
                ));

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
                    .multiply(BigDecimal.valueOf(returnItem.quantity()));

            returnedValue = returnedValue.add(itemValue);
        }

        if (returnedValue.signum() <= 0) {
            throw new IllegalStateException(
                    "Стоимость возвращённого товара должна быть положительной"
            );
        }

        BigDecimal availableBalance =
                balances.availableBalance(originalSaleId);

        if (returnedValue.compareTo(availableBalance) > 0) {
            throw new IllegalStateException(
                    "Стоимость возврата превышает доступный остаток оплаты"
            );
        }

        // Идемпотентность новой продажи связана с обменом,
        // а не с произвольным новым UUID.
        UUID saleIdempotencyKey = UUID.nameUUIDFromBytes(
                ("EXCHANGE_SALE:" + request.idempotencyKey())
                        .getBytes(StandardCharsets.UTF_8)
        );

        var newSale = sales.create(
                new CreateSaleRequest(
                        saleIdempotencyKey,
                        originalSale.customerId(),
                        request.fulfillmentType(),
                        request.items()
                )
        );

        BigDecimal newSaleTotal = newSale.total();

        BigDecimal creditApplied =
                returnedValue.min(newSaleTotal);

        BigDecimal additionalPaymentDue =
                newSaleTotal.subtract(creditApplied);

        BigDecimal refundDue =
                returnedValue.subtract(creditApplied);

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
            throw new IllegalStateException(
                    "Не удалось создать обмен: конфликт идемпотентности"
            );
        }

// Если стоимость старого и нового товара совпадает,
// дополнительных расчётов с клиентом не требуется.
        if (additionalPaymentDue.signum() == 0
                && refundDue.signum() == 0) {

            int updated = exchanges.complete(exchange.id(), actor());

            if (updated != 1) {
                throw new IllegalStateException(
                        "Не удалось завершить обмен без доплаты"
                );
            }

            return get(exchange.id());
        }

        return ExchangeResponse.from(exchange);
    }

    @Transactional(readOnly = true)
    public ExchangeResponse get(UUID exchangeId) {
        return exchanges.find(exchangeId)
                .map(ExchangeResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Обмен не найден"
                ));
    }

    private ExchangeResponse replay(
            Exchange existing,
            String fingerprint
    ) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IllegalStateException(
                    "Ключ идемпотентности уже использован с другими данными"
            );
        }

        return ExchangeResponse.from(existing);
    }

    private static String fingerprint(
            CreateExchangeRequest request
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(request.returnId())
                .append('|')
                .append(request.fulfillmentType());

        // Сортировка нужна, чтобы порядок позиций
        // не влиял на идентичность запроса.
        request.items().stream()
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
                        .append(item.unitSalePrice()
                                .stripTrailingZeros()
                                .toPlainString())
                );

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString()
                            .getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 недоступен",
                    exception
            );
        }
    }

    private static String actor() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
    }
}