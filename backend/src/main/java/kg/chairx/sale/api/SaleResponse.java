package kg.chairx.sale.api;

import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        String saleNumber,
        UUID customerId,
        FulfillmentType fulfillmentType,
        SaleStatus status,
        List<SaleItemResponse> items,
        BigDecimal total,
        String createdBy,
        Instant createdAt,
        String fulfilledBy,
        Instant fulfilledAt,
        String cancelledBy,
        Instant cancelledAt,
        String comment
) {

    public SaleResponse(UUID id, String saleNumber, UUID customerId, FulfillmentType fulfillmentType, SaleStatus status, List<SaleItemResponse> items, BigDecimal total, String createdBy, Instant createdAt, String fulfilledBy, Instant fulfilledAt, String cancelledBy, Instant cancelledAt) {
        this(id, saleNumber, customerId, fulfillmentType, status, items, total, createdBy, createdAt, fulfilledBy, fulfilledAt, cancelledBy, cancelledAt, null);
    }

}