package kg.chairx.purchase.api;

import kg.chairx.purchase.domain.PurchasePayment;
import kg.chairx.purchase.domain.PurchasePaymentKind;
import kg.chairx.finance.domain.FinanceAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchasePaymentResponse(
        UUID id, UUID purchaseId, PurchasePaymentKind paymentKind,
        FinanceAccount account, BigDecimal amount, UUID idempotencyKey,
        String reference, String comment, String createdBy, Instant createdAt
) {
    public static PurchasePaymentResponse from(PurchasePayment value) {
        return new PurchasePaymentResponse(value.id(), value.purchaseId(),
                value.paymentKind(), value.account(), value.amount(),
                value.idempotencyKey(), value.reference(), value.comment(),
                value.createdBy(), value.createdAt());
    }
}
