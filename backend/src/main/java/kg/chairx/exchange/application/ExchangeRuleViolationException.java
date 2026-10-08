package kg.chairx.exchange.application;

public class ExchangeRuleViolationException extends RuntimeException {

    private final String code;

    public ExchangeRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}