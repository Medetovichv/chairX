package kg.chairx.exchange.application;

public class ExchangeNotFoundException extends RuntimeException {

    public ExchangeNotFoundException() {
        super("Обмен не найден");
    }
}