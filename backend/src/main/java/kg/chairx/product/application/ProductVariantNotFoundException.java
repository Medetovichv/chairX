package kg.chairx.product.application;

public class ProductVariantNotFoundException extends RuntimeException {
    public ProductVariantNotFoundException() {
        super("Вариант товара не найден");
    }
}
