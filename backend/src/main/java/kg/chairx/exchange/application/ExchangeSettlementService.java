package kg.chairx.exchange.application;

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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ExchangeSettlementService {

    private final ExchangeRepository exchanges;
    private final ExchangeSettlementRepository settlements;

    public ExchangeSettlementService(
            ExchangeRepository exchanges,
            ExchangeSettlementRepository settlements
    ) {
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
            throw new IllegalArgumentException("Exchange ID and idempotency key are required");
        }

        if (!"IN".equals(direction) && !"OUT".equals(direction)) {
            throw new IllegalArgumentException("Invalid settlement direction");
        }

        if (!"CASH".equals(method) && !"TRANSFER".equals(method)) {
            throw new IllegalArgumentException("Invalid payment method");
        }

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Amount must be a positive integer");
        }

        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required");
        }

        if (actor.length() > 200) {
            throw new IllegalArgumentException("Actor is too long");
        }

        if (reference != null && reference.length() > 200) {
            throw new IllegalArgumentException("Reference is too long");
        }

        String fingerprint = fingerprint(
                exchangeId,
                direction,
                method,
                amount,
                reference
        );

        // Lock serializes settlements for the same exchange.
        Exchange exchange = exchanges.lock(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found"));

        Settlement previous = settlements.findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (previous != null) {
            if (!previous.exchangeId().equals(exchangeId)
                    || !previous.requestFingerprint().equals(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used for another request"
                );
            }

            return previous;
        }

        if (exchange.status() == ExchangeStatus.COMPLETED) {
            throw new IllegalStateException("Exchange is already completed");
        }

        BigDecimal due = switch (direction) {
            case "IN" -> exchange.additionalPaymentDue();
            case "OUT" -> exchange.refundDue();
            default -> throw new IllegalArgumentException("Invalid direction");
        };

        if (due.signum() == 0) {
            throw new IllegalStateException(
                    "This exchange does not require settlement in direction " + direction
            );
        }

        BigDecimal alreadySettled = settlements.total(exchangeId, direction);
        BigDecimal remaining = due.subtract(alreadySettled);

        if (amount.compareTo(remaining) > 0) {
            throw new IllegalStateException(
                    "Settlement exceeds remaining amount: " + remaining
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
            throw new IllegalStateException(
                    "Settlement idempotency conflict"
            );
        }

        if (amount.compareTo(remaining) == 0) {
            exchanges.complete(exchangeId, actor);
        }

        return settlements.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Settlement was not persisted"
                ));
    }

    @Transactional(readOnly = true)
    public List<Settlement> getSettlements(UUID exchangeId) {
        if (exchangeId == null) {
            throw new IllegalArgumentException("Exchange ID is required");
        }

        exchanges.find(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("Exchange not found"));

        return settlements.findByExchange(exchangeId);
    }

    private static String fingerprint(
            UUID exchangeId,
            String direction,
            String method,
            BigDecimal amount,
            String reference
    ) {
        String canonical = String.join("\n",
                exchangeId.toString(),
                direction,
                method,
                amount.toBigIntegerExact().toString(),
                reference == null ? "" : reference
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}