package kg.chairx.returning.application;

public class ReturnNotFoundException extends RuntimeException {

    public ReturnNotFoundException() {
        super("Возврат не найден");
    }
}