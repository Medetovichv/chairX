package kg.chairx.expense.application;

public class ExpenseValidationException extends RuntimeException {

    public ExpenseValidationException(String message) {
        super(message);
    }
}