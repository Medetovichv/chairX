package kg.chairx.payment.api;

import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentStatus;
import kg.chairx.payment.domain.PaymentChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID saleId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        String comment,
        String paidBy,
        Instant paidAt,
        String cancelledBy,
        Instant cancelledAt,
        String cancellationReason,
        PaymentChannel channel
) {
}