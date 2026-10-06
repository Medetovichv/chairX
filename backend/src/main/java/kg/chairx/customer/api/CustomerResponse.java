package kg.chairx.customer.api;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String phone,
        String secondaryPhone,
        String whatsappPhone,
        String instagramUsername,
        String address,
        String cityRegion,
        String comment,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}