package kg.chairx.warehouse.application;

public class WarehouseNotFoundException extends RuntimeException {
    public WarehouseNotFoundException() { super("Склад не найден"); }
}
