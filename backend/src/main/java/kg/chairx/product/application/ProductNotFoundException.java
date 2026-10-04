package kg.chairx.product.application;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("Товар не найден");
    }
}
