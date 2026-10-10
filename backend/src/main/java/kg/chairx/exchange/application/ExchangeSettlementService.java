package kg.chairx.exchange.application;

import kg.chairx.finance.application.FinancePostingService;
import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository;
import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository.Settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ExchangeSettlementService {

    private final FinancePostingService finance;
    private final ExchangeRepository exchanges;
    private final ExchangeSettlementRepository settlements;

    public ExchangeSettlementService(
            FinancePostingService finance,
            ExchangeRepository exchanges,
            ExchangeSettlementRepository settlements
    ) {
        this.finance = finance;
        this.exchanges = exchanges;
        this.settlements = settlements;
    }

    @Transactional
    public Settlement settle(
            UUID exchangeId,
            UUID idempotencyKey,
            String direction,
            String method,
            BigDecimal amount,
            String reference,
            String actor
    ) {
        if (exchangeId == null || idempotencyKey == null) {
            throw invalid("Exchange ID and idempotency key are required");
        }

        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            throw invalid("Invalid settlement direction");
        }

        if (!"CASH".equals(method) && !"TRANSFER".equals(method)) {
            throw invalid("Invalid payment method");
        }

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw invalid("Amount must be a positive integer");
        }

        if (actor == null || actor.isBlank() || actor.length() > 200) {
            throw invalid("Invalid actor");
        }

        if (reference != null && reference.length() > 200) {
            throw invalid("Reference is too long");
        }

        String fingerprint = fingerprint(
                exchangeId,
                direction,
                method,
                amount,
                reference
        );

        // Блокируем обмен для последовательного выполнения расчётов.
        Exchange exchange = exchanges.lock(exchangeId)
                .orElseThrow(ExchangeNotFoundException::new);

        // Повторный запрос должен возвращать первоначальный результат.
        Settlement previous = settlements
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (previous != null) {
            if (!previous.exchangeId().equals(exchangeId)
                    || !previous.requestFingerprint().equals(fingerprint)) {
                throw rule(
                        "EXCHANGE_SETTLEMENT_IDEMPOTENCY_CONFLICT",
                        "Ключ идемпотентности уже использован с другими данными"
                );
            }

            return previous;
        }

        if (exchange.status() == ExchangeStatus.COMPLETED) {
            throw rule(
                    "EXCHANGE_ALREADY_COMPLETED",
                    "Финансовые расчёты по обмену уже завершены"
            );
        }

        BigDecimal due = "IN".equals(direction)
                ? exchange.additionalPaymentDue()
                : exchange.refundDue();

        if (due.signum() == 0) {
            throw rule(
                    "EXCHANGE_SETTLEMENT_NOT_REQUIRED",
                    "Для обмена не требуется расчёт в направлении " + direction
            );
        }

        BigDecimal alreadySettled =
                settlements.total(exchangeId, direction);

        BigDecimal remaining = due.subtract(alreadySettled);

        if (remaining.signum() <= 0) {
            throw rule(
                    "EXCHANGE_SETTLEMENT_ALREADY_PAID",
                    "Расчёт по этому обмену уже выполнен"
            );
        }

        if (amount.compareTo(remaining) > 0) {
            throw rule(
                    "EXCHANGE_SETTLEMENT_EXCEEDS_REMAINING",
                    "Сумма превышает остаток: " + remaining
            );
        }

        UUID settlementId = UUID.randomUUID();

        boolean inserted = settlements.tryInsert(
                settlementId,
                exchangeId,
                direction,
                method,
                amount,
                reference,
                idempotencyKey,
                fingerprint,
                actor
        );

        if (!inserted) {
            throw rule(
                    "EXCHANGE_SETTLEMENT_IDEMPOTENCY_CONFLICT",
                    "Ключ идемпотентности уже используется"
            );
        }

        finance.post(method, "IN".equals(direction) ? amount : amount.negate(),
                "IN".equals(direction) ? "SALE_PAYMENT" : "CUSTOMER_REFUND",
                "EXCHANGE_SETTLEMENT", settlementId, actor);

        // Последняя часть доплаты или возврата завершает обмен.
        if (amount.compareTo(remaining) == 0) {
            int updated = exchanges.complete(exchangeId, actor);

            if (updated != 1) {
                throw new IllegalStateException(
                        "Не удалось завершить финансовый расчёт по обмену"
                );
            }
        }

        return settlements.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Settlement was not persisted"
                ));
    }

    @Transactional(readOnly = true)
    public List<Settlement> getSettlements(UUID exchangeId) {
        if (exchangeId == null) {
            throw invalid("Exchange ID is required");
        }

        exchanges.find(exchangeId)
                .orElseThrow(ExchangeNotFoundException::new);

        return settlements.findByExchange(exchangeId);
    }

    private static ExchangeRuleViolationException invalid(String message) {
        return rule("INVALID_EXCHANGE_SETTLEMENT", message);
    }

    private static ExchangeRuleViolationException rule(
            String code,
            String message
    ) {
        return new ExchangeRuleViolationException(code, message);
    }

    private static String fingerprint(
            UUID exchangeId,
            String direction,
            String method,
            BigDecimal amount,
            String reference
    ) {
        String canonical = String.join(
                "\n",
                exchangeId.toString(),
                direction,
                method,
                amount.toBigIntegerExact().toString(),
                reference == null ? "" : reference
        );

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception
            );
        }
    }
}