package kg.chairx.inventory.domain;

public class InsufficientStockException extends RuntimeException {
    private final long requested;
    private final long available;

    public InsufficientStockException(long requested, long available) {
        super("Недостаточно доступного товара на складе");
        this.requested = requested;
        this.available = available;
    }

    public long getRequested() { return requested; }
    public long getAvailable() { return available; }
}
