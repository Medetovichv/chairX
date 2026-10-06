package kg.chairx.sale.application;

public class SaleRuleViolationException
        extends RuntimeException {

    private final String code;

    public SaleRuleViolationException(
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