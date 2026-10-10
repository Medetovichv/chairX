package kg.chairx.customer.api;

import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @Size(max = 200, message = "Имя клиента не должно превышать 200 символов")
        String fullName,

        @Size(max = 50)
        String phone,

        @Size(max = 50)
        String secondaryPhone,

        @Size(max = 50)
        String whatsappPhone,

        @Size(max = 100)
        String instagramUsername,

        @Size(max = 500)
        String address,

        @Size(max = 200)
        String cityRegion,

        @Size(max = 2000)
        String comment
) {
}