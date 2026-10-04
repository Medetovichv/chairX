package kg.chairx.warehouse.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record UpdateWarehouseRequest(
        @NotBlank(message = "Укажите название склада")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name,

        @Size(max = 1000, message = "Адрес не должен превышать 1000 символов")
        String address) { }
