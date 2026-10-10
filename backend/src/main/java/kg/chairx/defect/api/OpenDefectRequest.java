package kg.chairx.defect.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record OpenDefectRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID productVariantId,
        UUID supplierId,
        UUID purchaseReceiptItemId,
        @Min(1) long quantity,
        @NotBlank @Size(max=4000) String description
) {}
