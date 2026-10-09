package kg.chairx.inventory.application;

public class InvalidTransferQueryException extends RuntimeException {

    public InvalidTransferQueryException(String message) {
        super(message);
    }
}
