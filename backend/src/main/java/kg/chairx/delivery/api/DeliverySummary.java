package kg.chairx.delivery.api;
import java.time.Instant;
import java.util.UUID;
import kg.chairx.delivery.domain.DeliveryStatus;
public record DeliverySummary(
        UUID id,UUID saleId,DeliveryStatus status,String recipientName,
        String cityRegion,Instant createdAt) {}
