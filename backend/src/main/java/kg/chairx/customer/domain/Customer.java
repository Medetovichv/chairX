package kg.chairx.customer.domain;

import java.time.Instant;
import java.util.UUID;

public record Customer(
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

    public Customer {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Customer id обязателен"
            );
        }

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException(
                    "Имя клиента обязательно"
            );
        }

        if (fullName.length() > 200) {
            throw new IllegalArgumentException(
                    "Имя клиента не должно превышать 200 символов"
            );
        }
    }
}