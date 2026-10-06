package kg.chairx.supplier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name,
        @Size(max = 1000, message = "Контактная информация не должна превышать 1000 символов")
        String contactInformation,
        @Size(max = 4000, message = "Комментарий не должен превышать 4000 символов")
        String comment) { }
