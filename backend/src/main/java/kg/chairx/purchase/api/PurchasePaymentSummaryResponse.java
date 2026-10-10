package kg.chairx.purchase.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchasePaymentSummaryResponse(
        UUID purchaseId,
        BigDecimal supplierTotal,
        BigDecimal cargoTotal,
        BigDecimal paidSupplier,
        BigDecimal paidCargo,
        BigDecimal remainingSupplier,
        BigDecimal remainingCargo,
        List<PurchasePaymentResponse> payments
) {}
