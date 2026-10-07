package kg.chairx.payment.application;

public class PaymentRuleViolationException
        extends RuntimeException {

    private final String code;

    public PaymentRuleViolationException(
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