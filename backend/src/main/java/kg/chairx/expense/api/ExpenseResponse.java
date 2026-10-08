package kg.chairx.expense.api;

import kg.chairx.expense.domain.Expense;
import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        ExpenseCategory category,
        BigDecimal amount,
        ExpensePaymentMethod paymentMethod,
        LocalDate expenseDate,
        String comment,
        String createdBy,
        Instant createdAt
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.id(),
                expense.category(),
                expense.amount(),
                expense.paymentMethod(),
                expense.expenseDate(),
                expense.comment(),
                expense.createdBy(),
                expense.createdAt()
        );
    }
}