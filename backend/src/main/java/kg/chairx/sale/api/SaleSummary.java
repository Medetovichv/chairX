package kg.chairx.sale.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.domain.FulfillmentType;

public record SaleSummary(
        UUID id,String saleNumber,Instant createdAt,
        UUID customerId,String customerName,BigDecimal total,
        SaleStatus status,FulfillmentType fulfillmentType
) {}
