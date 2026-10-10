package kg.chairx.delivery.api;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kg.chairx.delivery.domain.DeliveryStatus;
public record DeliverySummary(
        UUID id,UUID saleId,DeliveryStatus status,String recipientName,
        String cityRegion,Instant createdAt,LocalDate plannedDeliveryDate) {
    public DeliverySummary(UUID id,UUID saleId,DeliveryStatus status,String recipientName,
                           String cityRegion,Instant createdAt) {
        this(id,saleId,status,recipientName,cityRegion,createdAt,null);
    }
}
