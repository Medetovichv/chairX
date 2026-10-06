package kg.chairx.supplier.api;

import java.time.Instant;
import java.util.UUID;


public record SupplierResponse(
        UUID id,
        String name,
        String contactInformation,
        String comment,
        boolean active,
        Instant createdAt,
        Instant updatedAt) { }
