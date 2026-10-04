package kg.chairx.warehouse.application;

public class WarehouseCodeAlreadyExistsException extends RuntimeException {
    public WarehouseCodeAlreadyExistsException() { super("Склад с таким кодом уже существует"); }
}
