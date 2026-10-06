package kg.chairx.customer.application;

public class CustomerRuleViolationException extends RuntimeException {

    public CustomerRuleViolationException(String message) {
        super(message);
    }
}