package kg.chairx.returning.api;
import java.time.Instant;
import java.util.UUID;
public record ReturnSummary(
        UUID id,UUID saleId,UUID warehouseId,Instant createdAt,
        String reason,String createdBy) {}
