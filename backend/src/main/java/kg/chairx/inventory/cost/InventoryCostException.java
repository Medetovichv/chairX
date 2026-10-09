package kg.chairx.inventory.cost;

/**
 * A business conflict requiring correction of the request
 * or reconciliation of historical data.
 */
public class InventoryCostException extends RuntimeException {

    private final String code;

    public InventoryCostException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}