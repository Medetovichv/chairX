package kg.chairx.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only KG normalized numbers can be auto-linked; never guess identities. */
public record ResolveCustomerPhoneRequest(
        @NotBlank @Size(max=50) String phone
) {}
