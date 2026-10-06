package kg.chairx.supplier.application;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException() {
        super("Поставщик не найден");
    }
}
