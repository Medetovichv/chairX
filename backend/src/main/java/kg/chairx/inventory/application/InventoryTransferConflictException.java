package kg.chairx.inventory.application;

public class InventoryTransferConflictException
        extends RuntimeException {

    public InventoryTransferConflictException(String message) {
        super(message);
    }
}
