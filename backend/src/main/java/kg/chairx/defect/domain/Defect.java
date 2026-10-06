package kg.chairx.defect.domain;

import java.time.Instant;
import java.util.UUID;

public record Defect(
        UUID id,
        UUID warehouseId,
        UUID productVariantId,
        UUID supplierId,
        UUID purchaseReceiptItemId,
        long quantity,
        DefectStatus status,
        String description,
        String resolutionNote,
        String createdBy,
        Instant createdAt,
        String resolvedBy,
        Instant resolvedAt
) {

    public Defect {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Defect id обязателен"
            );
        }

        if (warehouseId == null) {
            throw new IllegalArgumentException(
                    "Склад обязателен"
            );
        }

        if (productVariantId == null) {
            throw new IllegalArgumentException(
                    "Вариант товара обязателен"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Количество должно быть положительным"
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Статус дефекта обязателен"
            );
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Описание дефекта обязательно"
            );
        }
    }

    public boolean closed() {
        return status == DefectStatus.RESOLVED
                || status == DefectStatus.WRITTEN_OFF;
    }

    public Defect waitingParts() {
        if (closed()) {
            throw new IllegalStateException(
                    "Закрытый дефект нельзя изменить"
            );
        }

        if (status == DefectStatus.WAITING_PARTS) {
            return this;
        }

        return new Defect(
                id,
                warehouseId,
                productVariantId,
                supplierId,
                purchaseReceiptItemId,
                quantity,
                DefectStatus.WAITING_PARTS,
                description,
                resolutionNote,
                createdBy,
                createdAt,
                resolvedBy,
                resolvedAt
        );
    }

    public Defect resolve(
            String resolutionNote,
            String actor,
            Instant resolvedAt
    ) {
        if (closed()) {
            throw new IllegalStateException(
                    "Дефект уже закрыт"
            );
        }

        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new IllegalArgumentException(
                    "Укажите результат устранения дефекта"
            );
        }

        if (resolvedAt == null) {
            throw new IllegalArgumentException(
                    "Время устранения обязательно"
            );
        }

        return new Defect(
                id,
                warehouseId,
                productVariantId,
                supplierId,
                purchaseReceiptItemId,
                quantity,
                DefectStatus.RESOLVED,
                description,
                resolutionNote.trim(),
                createdBy,
                createdAt,
                actor,
                resolvedAt
        );
    }
}