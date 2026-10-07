package kg.chairx.refund.application;

import java.util.UUID;

public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(UUID id) {
        super(
                "Возврат денег не найден: " + id
        );
    }
}