package kg.chairx.product.api;

import java.time.Instant;
import java.util.UUID;


public record ProductResponse(
        UUID id,
        String name,
        String description,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt) { }
