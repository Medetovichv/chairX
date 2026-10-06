package kg.chairx.defect.application;

import kg.chairx.defect.domain.Defect;
import kg.chairx.defect.domain.DefectStatus;
import kg.chairx.defect.persistence.DefectRepository;
import kg.chairx.inventory.api.ChangeBlockedStock;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DefectService {

    private static final String STOCK_SOURCE_TYPE = "DEFECT";

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
            throw new DefectRuleViolationException(
                    "Укажите склад"
            );
        }

        if (productVariantId == null) {
            throw new DefectRuleViolationException(
                    "Укажите вариант товара"
            );
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

        Defect defect = findForUpdate(defectId);

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
        validateClosingRequest(
                defectId,
                resolutionNote
        );

        Defect defect = findForUpdate(defectId);

        ensureOpen(defect);

        /*
         * Товар исправлен и физически остаётся на складе.
         * Поэтому StockMovement не создаётся.
         * Снимается только blocked.
         */
        inventoryService.unblock(
                blockedStockCommand(
                        defect,
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

    @Transactional
    public Defect writeOff(
            UUID defectId,
            String resolutionNote,
            String actor
    ) {
        validateClosingRequest(
                defectId,
                resolutionNote
        );

        Defect defect = findForUpdate(defectId);

        ensureOpen(defect);

        /*
         * Дефектный товар до списания находится в blocked.
         *
         * Сначала снимаем blocked, чтобы количество снова стало
         * доступно для физического движения.
         *
         * Затем WRITE_OFF уменьшает onHand и создаёт
         * неизменяемый StockMovement.
         *
         * Всё выполняется в одной транзакции.
         */
        inventoryService.unblock(
                blockedStockCommand(
                        defect,
                        actor
                )
        );

        inventoryService.recordMovement(
                new RecordStockMovement(
                        defect.id(),
                        defect.warehouseId(),
                        defect.productVariantId(),
                        StockMovementType.WRITE_OFF,
                        defect.quantity(),
                        STOCK_SOURCE_TYPE,
                        defect.id(),
                        actor
                )
        );

        try {
            Defect writtenOff = defect.writeOff(
                    resolutionNote,
                    actor,
                    Instant.now()
            );

            repository.update(writtenOff);

            return writtenOff;

        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new DefectRuleViolationException(
                    exception.getMessage()
            );
        }
    }

    private Defect findForUpdate(UUID defectId) {
        return repository.findByIdForUpdate(defectId)
                .orElseThrow(
                        () -> new DefectNotFoundException(
                                defectId
                        )
                );
    }

    private void ensureOpen(Defect defect) {
        if (defect.closed()) {
            throw new DefectRuleViolationException(
                    "Дефект уже закрыт"
            );
        }
    }

    private void validateClosingRequest(
            UUID defectId,
            String resolutionNote
    ) {
        if (defectId == null) {
            throw new DefectRuleViolationException(
                    "Укажите дефект"
            );
        }

        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new DefectRuleViolationException(
                    "Укажите результат обработки дефекта"
            );
        }
    }

    private ChangeBlockedStock blockedStockCommand(
            Defect defect,
            String actor
    ) {
        return new ChangeBlockedStock(
                defect.warehouseId(),
                defect.productVariantId(),
                defect.quantity(),
                defect.id(),
                actor
        );
    }
}