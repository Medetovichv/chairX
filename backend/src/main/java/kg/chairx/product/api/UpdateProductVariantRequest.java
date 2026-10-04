package kg.chairx.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record UpdateProductVariantRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name,
        @Size(max = 100, message = "SKU не должен превышать 100 символов")
        String sku,
        @Size(max = 100, message = "Цвет не должен превышать 100 символов")
        String color,
        @NotNull(message = "Укажите рекомендуемую цену")
        @DecimalMin(value = "0", message = "Цена не может быть отрицательной")
        @Digits(integer = 17, fraction = 2, message = "Цена должна содержать не более 17 цифр до запятой и 2 после")
        BigDecimal recommendedSalePrice) { }
