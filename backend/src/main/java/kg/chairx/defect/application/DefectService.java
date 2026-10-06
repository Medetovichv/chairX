package kg.chairx.defect.application;

import kg.chairx.defect.domain.Defect;
import kg.chairx.defect.domain.DefectStatus;
import kg.chairx.defect.persistence.DefectRepository;
import kg.chairx.inventory.api.ChangeBlockedStock;
import kg.chairx.inventory.application.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DefectService {

    private final DefectRepository repository;
    private final InventoryService inventoryService;

    public DefectService(
            DefectRepository repository,
            InventoryService inventoryService
    ) {
        this.repository = repository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Defect open(
            UUID warehouseId,
            UUID productVariantId,
            UUID supplierId,
            UUID purchaseReceiptItemId,
            long quantity,
            String description,
            String actor
    ) {
        if (warehouseId == null) {
            throw new DefectRuleViolationException("Укажите склад");
        }

        if (productVariantId == null) {
            throw new DefectRuleViolationException("Укажите вариант товара");
        }

        if (quantity <= 0) {
            throw new DefectRuleViolationException(
                    "Количество должно быть положительным"
            );
        }

        if (description == null || description.isBlank()) {
            throw new DefectRuleViolationException(
                    "Описание дефекта обязательно"
            );
        }

        UUID defectId = UUID.randomUUID();

        inventoryService.block(
                new ChangeBlockedStock(
                        warehouseId,
                        productVariantId,
                        quantity,
                        defectId,
                        actor
                )
        );

        Defect defect = new Defect(
                defectId,
                warehouseId,
                productVariantId,
                supplierId,
                purchaseReceiptItemId,
                quantity,
                DefectStatus.OPEN,
                description.trim(),
                null,
                actor,
                Instant.now(),
                null,
                null
        );

        repository.insert(defect);

        return defect;
    }

    @Transactional
    public Defect waitForParts(UUID defectId) {
        if (defectId == null) {
            throw new DefectRuleViolationException(
                    "Укажите дефект"
            );
        }

        Defect defect = repository.findByIdForUpdate(defectId)
                .orElseThrow(
                        () -> new DefectNotFoundException(defectId)
                );

        if (defect.closed()) {
            throw new DefectRuleViolationException(
                    "Закрытый дефект нельзя изменить"
            );
        }

        try {
            Defect changed = defect.waitingParts();

            repository.update(changed);

            return changed;

        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new DefectRuleViolationException(
                    exception.getMessage()
            );
        }
    }

    @Transactional
    public Defect resolve(
            UUID defectId,
            String resolutionNote,
            String actor
    ) {
        if (defectId == null) {
            throw new DefectRuleViolationException(
                    "Укажите дефект"
            );
        }

        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new DefectRuleViolationException(
                    "Укажите результат устранения дефекта"
            );
        }

        Defect defect = repository.findByIdForUpdate(defectId)
                .orElseThrow(
                        () -> new DefectNotFoundException(defectId)
                );

        if (defect.closed()) {
            throw new DefectRuleViolationException(
                    "Дефект уже закрыт"
            );
        }

        /*
         * Товар физически остаётся на складе.
         * Поэтому StockMovement здесь НЕ создаётся.
         * Мы только снимаем blocked.
         */
        inventoryService.unblock(
                new ChangeBlockedStock(
                        defect.warehouseId(),
                        defect.productVariantId(),
                        defect.quantity(),
                        defect.id(),
                        actor
                )
        );

        try {
            Defect resolved = defect.resolve(
                    resolutionNote,
                    actor,
                    Instant.now()
            );

            repository.update(resolved);

            return resolved;

        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new DefectRuleViolationException(
                    exception.getMessage()
            );
        }
    }
}