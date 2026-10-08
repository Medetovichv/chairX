package kg.chairx.expense.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Expense(
        UUID id,
        ExpenseCategory category,
        BigDecimal amount,
        ExpensePaymentMethod paymentMethod,
        LocalDate expenseDate,
        String comment,
        String createdBy,
        Instant createdAt
) {
}