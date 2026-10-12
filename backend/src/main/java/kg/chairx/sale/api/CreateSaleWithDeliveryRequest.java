package kg.chairx.sale.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kg.chairx.sale.domain.FulfillmentType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSaleWithDeliveryRequest(
        @NotNull @Valid CreateDraftSaleRequest sale,
        @NotNull @Valid DeliveryDetails delivery
) {
    public record DeliveryDetails(
            @Size(max=200) String recipientName,
            @NotBlank @Size(max=50) String recipientPhone,
            @NotBlank @Size(max=500) String address,
            @Size(max=200) String cityRegion,
            @NotNull @DecimalMin("0") @Digits(integer=19,fraction=0) BigDecimal deliveryCost,
            @Size(max=200) String carrierName,
            @Size(max=200) String trackingNumber,
            @Size(max=2000) String comment,
            LocalDate plannedDeliveryDate
    ) {}
}
