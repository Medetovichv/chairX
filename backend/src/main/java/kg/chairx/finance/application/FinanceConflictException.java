package kg.chairx.finance.application;

public class FinanceConflictException extends IllegalStateException {
    private final String code;

    public FinanceConflictException(String message) {
        this("FINANCE_OPERATION_CONFLICT", message);
    }

    public FinanceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
