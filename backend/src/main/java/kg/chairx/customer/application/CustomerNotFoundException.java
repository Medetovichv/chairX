package kg.chairx.customer.application;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID id) {
        super("Клиент не найден: " + id);
    }
}