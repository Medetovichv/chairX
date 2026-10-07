package kg.chairx.refund.application;

public class RefundRuleViolationException extends RuntimeException {

    private final String code;

    public RefundRuleViolationException(
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