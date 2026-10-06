package kg.chairx.purchase.application;

public class PurchaseRuleViolationException extends RuntimeException {
    private final String code;
    public PurchaseRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}
