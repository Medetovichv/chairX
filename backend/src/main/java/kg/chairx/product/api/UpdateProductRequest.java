package kg.chairx.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name,
        @Size(max = 4000, message = "Описание не должно превышать 4000 символов")
        String description,
        @Size(max = 120, message = "Категория не должна превышать 120 символов")
        String category) { }
