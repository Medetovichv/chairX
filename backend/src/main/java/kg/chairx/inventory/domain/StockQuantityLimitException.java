package kg.chairx.inventory.domain;

public class StockQuantityLimitException extends RuntimeException {
    public StockQuantityLimitException() { super("Превышено максимально допустимое количество товара"); }
}
