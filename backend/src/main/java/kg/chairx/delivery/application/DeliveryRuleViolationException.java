package kg.chairx.delivery.application;

public class DeliveryRuleViolationException
        extends RuntimeException {

    private final String code;

    public DeliveryRuleViolationException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}