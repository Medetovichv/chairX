package kg.chairx.expense.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(

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
}