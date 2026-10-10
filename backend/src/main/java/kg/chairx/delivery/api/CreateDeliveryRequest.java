package kg.chairx.delivery.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDeliveryRequest(
        @NotNull
        UUID saleId,

        @Size(max = 200)
        String recipientName,

        @NotBlank
        @Size(max = 50)
        String recipientPhone,

        @NotBlank
        @Size(max = 500)
        String address,

        @Size(max = 200)
        String cityRegion,

        @NotNull
        @DecimalMin("0")
        @Digits(integer = 19, fraction = 0)
        BigDecimal deliveryCost,

        @Size(max = 200)
        String carrierName,

        @Size(max = 200)
        String trackingNumber,

        @Size(max = 2000)
        String comment,

        LocalDate plannedDeliveryDate
) {
    public CreateDeliveryRequest(UUID saleId, String recipientName, String recipientPhone,
                                 String address, String cityRegion, BigDecimal deliveryCost,
                                 String carrierName, String trackingNumber, String comment) {
        this(saleId, recipientName, recipientPhone, address, cityRegion, deliveryCost,
                carrierName, trackingNumber, comment, null);
    }
}