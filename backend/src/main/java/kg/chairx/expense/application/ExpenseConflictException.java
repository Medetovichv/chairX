package kg.chairx.expense.application;

/** Conflict when a client retries an expense key with a different payload. */
public class ExpenseConflictException extends RuntimeException {
    private final String code;

    public ExpenseConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
