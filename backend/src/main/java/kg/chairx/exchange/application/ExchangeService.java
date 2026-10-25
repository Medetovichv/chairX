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
    public ExchangeResponse create(@NotNull @Valid CreateExchangeRequest request) {
        throw new ExchangeRuleViolationException(
                "EXCHANGE_CREATION_RETIRED",
                "Обмены отключены. Оформите возврат денег и отдельную продажу."
        );
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