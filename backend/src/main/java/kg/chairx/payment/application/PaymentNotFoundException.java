package kg.chairx.payment.application;

public class PaymentNotFoundException
        extends RuntimeException {

    public PaymentNotFoundException() {
        super("Оплата не найдена");
    }
}