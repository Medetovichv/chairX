package kg.chairx.delivery.application;

public class DeliveryNotFoundException
        extends RuntimeException {

    public DeliveryNotFoundException() {
        super("Доставка не найдена");
    }
}