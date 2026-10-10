package kg.chairx.purchase.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kg.chairx.finance.domain.FinanceAccount;

public record PurchasePayment(
        UUID id, UUID purchaseId, PurchasePaymentKind paymentKind,
        FinanceAccount account, BigDecimal amount, UUID idempotencyKey,
        String requestFingerprint, String reference, String comment,
        String createdBy, Instant createdAt
) {}
