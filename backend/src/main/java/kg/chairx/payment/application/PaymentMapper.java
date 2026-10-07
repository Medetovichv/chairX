package kg.chairx.payment.application;

import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.domain.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(
            Payment payment
    ) {
        return new PaymentResponse(
                payment.id(),
                payment.saleId(),
                payment.amount(),
                payment.method(),
                payment.status(),
                payment.reference(),
                payment.comment(),
                payment.paidBy(),
                payment.paidAt(),
                payment.cancelledBy(),
                payment.cancelledAt(),
                payment.cancellationReason()
        );
    }
}