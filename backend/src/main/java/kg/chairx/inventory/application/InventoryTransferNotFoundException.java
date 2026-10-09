package kg.chairx.inventory.application;

import java.util.UUID;

public class InventoryTransferNotFoundException extends RuntimeException {

    public InventoryTransferNotFoundException(UUID id) {
        super("Inventory transfer not found: " + id);
    }
}
