package kg.chairx.defect.application;

import java.util.UUID;

public class DefectNotFoundException extends RuntimeException {

    public DefectNotFoundException(UUID id) {
        super("Дефект не найден: " + id);
    }
}