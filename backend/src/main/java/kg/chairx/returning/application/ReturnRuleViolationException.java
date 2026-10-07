package kg.chairx.returning.application;

public class ReturnRuleViolationException extends RuntimeException {

    private final String code;

    public ReturnRuleViolationException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}