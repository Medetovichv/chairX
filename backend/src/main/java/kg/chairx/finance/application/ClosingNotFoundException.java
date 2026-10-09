package kg.chairx.finance.application;

public class ClosingNotFoundException extends IllegalArgumentException {
    public ClosingNotFoundException(String message) { super(message); }
}
