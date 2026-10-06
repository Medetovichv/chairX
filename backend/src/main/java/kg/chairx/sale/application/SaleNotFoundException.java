package kg.chairx.sale.application;

public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException() {
        super("Продажа не найдена");
    }
}
