package kg.chairx.warehouse.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateWarehouseRequest(
        @NotBlank(message = "Укажите название склада")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name,

        @NotBlank(message = "Укажите код склада")
        @Size(max = 50, message = "Код не должен превышать 50 символов")
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}",
                message = "Код должен содержать заглавные латинские буквы, цифры, дефис или подчёркивание и начинаться с буквы или цифры")
        String code,

        @Size(max = 1000, message = "Адрес не должен превышать 1000 символов")
        String address) { }
