package kg.chairx.expense.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseRequest(

        @NotNull
        UUID idempotencyKey,

        @NotNull
        ExpenseCategory category,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        ExpensePaymentMethod paymentMethod,

        @NotNull
        LocalDate expenseDate,

        @Size(max = 1000)
        String comment
) {
    // Source-level compatibility for existing Java callers. The HTTP API
    // requires an explicit key through the record component above.
    public CreateExpenseRequest(
            ExpenseCategory category, BigDecimal amount,
            ExpensePaymentMethod paymentMethod, LocalDate expenseDate,
            String comment
    ) {
        this(UUID.randomUUID(), category, amount, paymentMethod, expenseDate, comment);
    }
}