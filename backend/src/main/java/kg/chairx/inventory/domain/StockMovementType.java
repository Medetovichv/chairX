package kg.chairx.inventory.domain;

public enum StockMovementType {
    PURCHASE_IN(true), SALE_OUT(false), RETURN_IN(true), TRANSFER_OUT(false),
    TRANSFER_IN(true), WRITE_OFF(false), ADJUSTMENT_IN(true), ADJUSTMENT_OUT(false);

    private final boolean incoming;

    StockMovementType(boolean incoming) { this.incoming = incoming; }

    public boolean isIncoming() { return incoming; }
}
