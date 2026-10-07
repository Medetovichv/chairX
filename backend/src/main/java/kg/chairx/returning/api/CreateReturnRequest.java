package kg.chairx.returning.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateReturnRequest(
        @NotNull(message = "Укажите продажу")
        UUID saleId,

        @NotNull(message = "Укажите склад возврата")
        UUID warehouseId,

        @NotNull(message = "Укажите ключ операции")
        UUID idempotencyKey,

        @NotEmpty(message = "Добавьте хотя бы одну позицию возврата")
        List<@Valid CreateReturnItemRequest> items,

        @NotNull(message = "Укажите причину возврата")
        @Size(min = 1, max = 500, message = "Причина возврата должна содержать от 1 до 500 символов")
        String reason,

        @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов")
        String comment
) {
}