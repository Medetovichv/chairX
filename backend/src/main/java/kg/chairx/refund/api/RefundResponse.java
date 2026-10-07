package kg.chairx.refund.api;

import kg.chairx.refund.domain.RefundMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID saleId,
        UUID returnId,
        BigDecimal amount,
        RefundMethod method,
        String reason,
        String reference,
        String comment,
        String refundedBy,
        Instant refundedAt
) {
}