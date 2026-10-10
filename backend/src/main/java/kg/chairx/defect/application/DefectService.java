package kg.chairx.defect.application;

import kg.chairx.defect.domain.Defect;
import kg.chairx.defect.api.DefectPageResponse;
import kg.chairx.defect.domain.DefectStatus;
import kg.chairx.defect.domain.ReceiptItemOrigin;
import kg.chairx.defect.persistence.DefectRepository;
import kg.chairx.inventory.api.ChangeBlockedStock;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.cost.InventoryCostPostingService;
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
    private final InventoryCostPostingService costPosting;

    public DefectService(
            DefectRepository repository,
            InventoryService inventoryService,
            InventoryCostPostingService costPosting
    ) {
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.costPosting = costPosting;
    }

    @Transactional(readOnly=true)
    public Defect get(UUID id) {
        return repository.findById(id).orElseThrow(()->new DefectNotFoundException(id));
    }

    @Transactional(readOnly=true)
    public DefectPageResponse list(DefectStatus status,int page,int size) {
        if(page<0 || size<1 || size>100) throw new DefectRuleViolationException(
                "Некорректные параметры поиска дефектов");
        return new DefectPageResponse(repository.list(status,page,size),page,size,
                repository.count(status));
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
        requireWarehouse(warehouseId);
        requireProductVariant(productVariantId);
        requirePositiveQuantity(quantity);
        requireDescription(description);
        requireActor(actor);

        validateOrigin(
                warehouseId,
                productVariantId,
                supplierId,
                purchaseReceiptItemId
        );

        UUID defectId = UUID.randomUUID();

        inventoryService.block(
                new ChangeBlockedStock(
                        warehouseId,
                        productVariantId,
                        quantity,
                        defectId,
                        actor.trim()
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
                actor.trim(),
                Instant.now(),
                null,
                null
        );

        repository.insert(defect);

        return defect;
    }

    @Transactional
    public Defect waitForParts(UUID defectId) {
        requireDefectId(defectId);

        Defect defect = findForUpdate(defectId);

        ensureOpen(defect);

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
                resolutionNote,
                actor
        );

        Defect defect = findForUpdate(defectId);

        ensureOpen(defect);

        inventoryService.unblock(
                blockedStockCommand(
                        defect,
                        actor.trim()
                )
        );

        try {
            Defect resolved = defect.resolve(
                    resolutionNote,
                    actor.trim(),
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
                resolutionNote,
                actor
        );

        Defect defect = findForUpdate(defectId);

        ensureOpen(defect);

        inventoryService.unblock(
                blockedStockCommand(
                        defect,
                        actor.trim()
                )
        );

        costPosting.postWriteOff(
                new RecordStockMovement(
                        defect.id(),
                        defect.warehouseId(),
                        defect.productVariantId(),
                        StockMovementType.WRITE_OFF,
                        defect.quantity(),
                        STOCK_SOURCE_TYPE,
                        defect.id(),
                        actor.trim()
                ), defect.purchaseReceiptItemId()
        );

        try {
            Defect writtenOff = defect.writeOff(
                    resolutionNote,
                    actor.trim(),
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

    private void validateOrigin(
            UUID warehouseId,
            UUID productVariantId,
            UUID supplierId,
            UUID purchaseReceiptItemId
    ) {
        if (purchaseReceiptItemId == null) {
            return;
        }

        ReceiptItemOrigin origin = repository
                .findReceiptItemOrigin(purchaseReceiptItemId)
                .orElseThrow(
                        () -> new DefectRuleViolationException(
                                "Позиция поступления не найдена"
                        )
                );

        if (!origin.warehouseId().equals(warehouseId)) {
            throw new DefectRuleViolationException(
                    "Позиция поступления относится к другому складу"
            );
        }

        if (!origin.productVariantId().equals(productVariantId)) {
            throw new DefectRuleViolationException(
                    "Позиция поступления относится к другому варианту товара"
            );
        }

        if (supplierId != null
                && !origin.supplierId().equals(supplierId)) {
            throw new DefectRuleViolationException(
                    "Поставщик не соответствует позиции поступления"
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
            String resolutionNote,
            String actor
    ) {
        requireDefectId(defectId);

        if (resolutionNote == null
                || resolutionNote.isBlank()) {
            throw new DefectRuleViolationException(
                    "Укажите результат обработки дефекта"
            );
        }

        requireActor(actor);
    }

    private void requireDefectId(UUID defectId) {
        if (defectId == null) {
            throw new DefectRuleViolationException(
                    "Укажите дефект"
            );
        }
    }

    private void requireWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            throw new DefectRuleViolationException(
                    "Укажите склад"
            );
        }
    }

    private void requireProductVariant(UUID productVariantId) {
        if (productVariantId == null) {
            throw new DefectRuleViolationException(
                    "Укажите вариант товара"
            );
        }
    }

    private void requirePositiveQuantity(long quantity) {
        if (quantity <= 0) {
            throw new DefectRuleViolationException(
                    "Количество должно быть положительным"
            );
        }
    }

    private void requireDescription(String description) {
        if (description == null
                || description.isBlank()) {
            throw new DefectRuleViolationException(
                    "Описание дефекта обязательно"
            );
        }
    }

    private void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new DefectRuleViolationException(
                    "Укажите инициатора операции"
            );
        }

        if (actor.trim().length() > 200) {
            throw new DefectRuleViolationException(
                    "Имя инициатора не должно превышать 200 символов"
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