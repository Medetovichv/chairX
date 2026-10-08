package kg.chairx.exchange.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Exchange(
        UUID id,
        UUID originalSaleId,
        UUID newSaleId,
        UUID returnId,
        ExchangeStatus status,
        BigDecimal returnedValue,
        BigDecimal newSaleTotal,
        BigDecimal creditApplied,
        BigDecimal additionalPaymentDue,
        BigDecimal refundDue,
        UUID idempotencyKey,
        String requestFingerprint,
        String createdBy,
        Instant createdAt,
        String completedBy,
        Instant completedAt
) {
    public Exchange {
        if (id == null
                || originalSaleId == null
                || newSaleId == null
                || returnId == null
                || status == null
                || returnedValue == null
                || newSaleTotal == null
                || creditApplied == null
                || additionalPaymentDue == null
                || refundDue == null
                || idempotencyKey == null
                || requestFingerprint == null
                || createdBy == null
                || createdAt == null) {
            throw new IllegalArgumentException(
                    "Required exchange fields must not be null"
            );
        }

        if (originalSaleId.equals(newSaleId)) {
            throw new IllegalArgumentException(
                    "Original and new sale must be different"
            );
        }

        if (returnedValue.signum() < 0
                || newSaleTotal.signum() < 0
                || creditApplied.signum() < 0
                || additionalPaymentDue.signum() < 0
                || refundDue.signum() < 0) {
            throw new IllegalArgumentException(
                    "Exchange amounts must not be negative"
            );
        }

        if (creditApplied.compareTo(
                returnedValue.min(newSaleTotal)) != 0) {
            throw new IllegalArgumentException(
                    "Invalid exchange credit"
            );
        }

        if (additionalPaymentDue.compareTo(
                newSaleTotal.subtract(creditApplied)) != 0) {
            throw new IllegalArgumentException(
                    "Invalid additional payment"
            );
        }

        if (refundDue.compareTo(
                returnedValue.subtract(creditApplied)) != 0) {
            throw new IllegalArgumentException(
                    "Invalid refund amount"
            );
        }

        if (status == ExchangeStatus.COMPLETED
                && (completedBy == null || completedAt == null)) {
            throw new IllegalArgumentException(
                    "Completed exchange requires completion metadata"
            );
        }

        if (status == ExchangeStatus.PENDING_SETTLEMENT
                && (completedBy != null || completedAt != null)) {
            throw new IllegalArgumentException(
                    "Pending exchange cannot have completion metadata"
            );
        }
    }
}