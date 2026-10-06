package kg.chairx.purchase.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SetCargoCostRequest(
        @NotNull(message = "Укажите стоимость карго")
        @DecimalMin(value = "0", message = "Карго не может быть отрицательным")
        @Digits(integer = 17, fraction = 2, message = "Карго: не более 17 целых и 2 дробных знаков") BigDecimal cargoCost) { }
