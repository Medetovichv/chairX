package kg.chairx.inventory.application;

public class InventoryOperationConflictException extends RuntimeException {
    public InventoryOperationConflictException() {
        super("Ключ складской операции уже использован с другими данными");
    }
}
