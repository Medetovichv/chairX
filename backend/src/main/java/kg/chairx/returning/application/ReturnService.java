package kg.chairx.returning.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kg.chairx.audit.AuditService;
import kg.chairx.inventory.api.ChangeBlockedStock;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.cost.InventoryCostPostingService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.returning.api.CreateReturnItemRequest;
import kg.chairx.returning.api.CreateReturnRequest;
import kg.chairx.returning.domain.Return;
import kg.chairx.returning.domain.ReturnCondition;
import kg.chairx.returning.domain.ReturnItem;
import kg.chairx.returning.application.ReturnNotFoundException;
import kg.chairx.returning.application.ReturnRuleViolationException;
import kg.chairx.returning.persistence.ReturnRepository;
import kg.chairx.sale.domain.Sale;
import kg.chairx.sale.domain.SaleItem;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.persistence.SaleRepository;
import kg.chairx.warehouse.persistence.WarehouseRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReturnService {

    private static final String SOURCE_TYPE = "SALE_RETURN";

    private final ReturnRepository returnRepository;
    private final SaleRepository saleRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final InventoryCostPostingService costPosting;

    public ReturnService(
            ReturnRepository returnRepository,
            SaleRepository saleRepository,
            WarehouseRepository warehouseRepository,
            InventoryService inventoryService,
            AuditService auditService,
            InventoryCostPostingService costPosting
    ) {
        this.returnRepository = returnRepository;
        this.saleRepository = saleRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
        this.costPosting = costPosting;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Return create(
            @NotNull @Valid CreateReturnRequest request
    ) {
        String actor = currentActor();
        String fingerprint = fingerprint(request);

        /*
         * Fast idempotency path.
         *
         * This allows an already completed request to be safely repeated
         * without touching inventory again.
         */
        var existing = returnRepository.findByIdempotencyKey(
                request.idempotencyKey()
        );

        if (existing.isPresent()) {
            return requireSameRequest(existing.get(), fingerprint, request);
        }

        /*
         * The Sale lock serializes all returns for the same sale.
         *
         * This is important because returnedQuantity() is calculated from
         * previous returns. Without this lock two concurrent returns could
         * both observe the same remaining quantity.
         */
        Sale sale = saleRepository.lock(request.saleId())
                .orElseThrow(() -> new ReturnRuleViolationException(
                        "SALE_NOT_FOUND",
                        "Продажа не найдена"
                ));

        /*
         * A second idempotency check is required after obtaining the Sale
         * lock. Another transaction may have completed the same request
         * while this transaction was waiting for the lock.
         */
        existing = returnRepository.findByIdempotencyKey(
                request.idempotencyKey()
        );

        if (existing.isPresent()) {
            return requireSameRequest(existing.get(), fingerprint, request);
        }

        requireFulfilledSale(sale);

        // The FIFO restoration budget shared by DeliveryService and
        // ReturnService prevents the same shipped units from being credited
        // twice, including concurrent and partial return scenarios.
        requireActiveWarehouse(request.warehouseId());
        requireUniqueSaleItems(request.items());

        List<SaleItem> saleItems = sale.items();

        for (CreateReturnItemRequest requestedItem : request.items().stream()
                .sorted(Comparator.comparing(i -> findSaleItem(saleItems, i.saleItemId()).productVariantId().toString())).toList()) {
            SaleItem saleItem = findSaleItem(
                    saleItems,
                    requestedItem.saleItemId()
            );

            validateRemainingQuantity(
                    saleItem,
                    requestedItem.quantity()
            );
        }

        UUID returnId = UUID.randomUUID();

        boolean inserted = returnRepository.tryInsert(
                returnId,
                sale.id(),
                request.warehouseId(),
                request.idempotencyKey(),
                fingerprint,
                normalizeRequired(request.reason()),
                normalizeOptional(request.comment()),
                actor
        );

        /*
         * Normally the Sale lock prevents this branch for requests belonging
         * to the same Sale. The unique idempotency key remains the final
         * database-level protection.
         */
        if (!inserted) {
            Return concurrent = returnRepository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Не удалось получить существующий возврат"
                    ));

            return requireSameRequest(concurrent, fingerprint, request);
        }

        for (CreateReturnItemRequest requestedItem : request.items().stream()
                .sorted(Comparator.comparing(i -> findSaleItem(saleItems, i.saleItemId()).productVariantId().toString())).toList()) {
            SaleItem saleItem = findSaleItem(
                    saleItems,
                    requestedItem.saleItemId()
            );

            ReturnItem returnItem = new ReturnItem(
                    UUID.randomUUID(),
                    returnId,
                    saleItem.id(),
                    requestedItem.quantity(),
                    requestedItem.condition()
            );

            returnRepository.insertItem(returnItem);

            /*
             * A physical return always increases onHand first.
             */
            costPosting.postReturn(
                    new RecordStockMovement(
                            returnItem.id(),
                            request.warehouseId(),
                            saleItem.productVariantId(),
                            StockMovementType.RETURN_IN,
                            returnItem.quantity(),
                            SOURCE_TYPE,
                            returnId,
                            actor
                    ), saleItem.id()
            );

            /*
             * A returned item that requires inspection/damage handling is
             * physically present, but immediately unavailable for sale.
             *
             * Blocking is not a physical movement and therefore does not
             * create another StockMovement.
             */
            if (returnItem.condition() == ReturnCondition.BLOCKED) {
                inventoryService.block(
                        new ChangeBlockedStock(
                                request.warehouseId(),
                                saleItem.productVariantId(),
                                returnItem.quantity(),
                                returnItem.id(),
                                actor
                        )
                );
            }
        }

        Return created = returnRepository.find(returnId)
                .orElseThrow(() -> new IllegalStateException(
                        "Созданный возврат не найден"
                ));

        auditService.recordAs(
                actor,
                "RETURN",
                created.id(),
                "CREATED",
                null,
                created
        );

        return created;
    }

    @Transactional(readOnly = true)
    public Return get(@NotNull UUID returnId) {
        return returnRepository.find(returnId)
                .orElseThrow(ReturnNotFoundException::new);
    }

    private Return requireSameRequest(
            Return existing,
            String fingerprint, CreateReturnRequest request
    ) {
        String storedFingerprint =
                returnRepository.requestFingerprint(existing.id());

        // Keep historical fingerprints, but reject ambiguous delimiter encodings.
        boolean sameItems = existing.items().size() == request.items().size()
                && request.items().stream().allMatch(item -> existing.items().stream().anyMatch(saved ->
                        saved.saleItemId().equals(item.saleItemId())
                                && saved.quantity() == item.quantity() && saved.condition() == item.condition()));
        if (!storedFingerprint.equals(fingerprint)
                || !existing.saleId().equals(request.saleId())
                || !existing.warehouseId().equals(request.warehouseId())
                || !existing.reason().equals(normalizeRequired(request.reason()))
                || !java.util.Objects.equals(existing.comment(), normalizeOptional(request.comment()))
                || !sameItems) {
            throw new ReturnRuleViolationException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Ключ операции уже использован для другого возврата"
            );
        }

        return existing;
    }

    private static void requireFulfilledSale(Sale sale) {
        if (sale.status() != SaleStatus.FULFILLED) {
            throw new ReturnRuleViolationException(
                    "SALE_NOT_FULFILLED",
                    "Возврат возможен только для выданной продажи"
            );
        }
    }

    private void requireActiveWarehouse(UUID warehouseId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ReturnRuleViolationException(
                        "WAREHOUSE_NOT_FOUND",
                        "Склад не найден"
                ));

        if (!warehouse.isActive()) {
            throw new ReturnRuleViolationException(
                    "WAREHOUSE_INACTIVE",
                    "Нельзя принять возврат на неактивный склад"
            );
        }
    }

    private static void requireUniqueSaleItems(
            List<CreateReturnItemRequest> items
    ) {
        Set<UUID> ids = new HashSet<>();

        for (CreateReturnItemRequest item : items) {
            if (!ids.add(item.saleItemId())) {
                throw new ReturnRuleViolationException(
                        "DUPLICATE_SALE_ITEM",
                        "Одна позиция продажи не может повторяться в одном возврате"
                );
            }
        }
    }

    private static SaleItem findSaleItem(
            List<SaleItem> saleItems,
            UUID saleItemId
    ) {
        return saleItems.stream()
                .filter(item -> item.id().equals(saleItemId))
                .findFirst()
                .orElseThrow(() -> new ReturnRuleViolationException(
                        "SALE_ITEM_NOT_FOUND",
                        "Позиция не относится к указанной продаже"
                ));
    }

    private void validateRemainingQuantity(
            SaleItem saleItem,
            long requestedQuantity
    ) {
        long alreadyReturned =
                returnRepository.returnedQuantity(saleItem.id());

        long remaining;

        try {
            remaining = Math.subtractExact(
                    saleItem.quantity(),
                    alreadyReturned
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Некорректное количество ранее возвращённого товара",
                    exception
            );
        }

        if (requestedQuantity > remaining) {
            throw new ReturnRuleViolationException(
                    "RETURN_QUANTITY_EXCEEDED",
                    "Нельзя вернуть больше товара, чем было продано и ещё не возвращено"
            );
        }
    }

    private static String currentActor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new IllegalStateException(
                    "Не удалось определить пользователя"
            );
        }

        return authentication.getName();
    }

    private static String normalizeRequired(String value) {
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.strip();
    }

    private static String fingerprint(CreateReturnRequest request) {
        String reason = normalizeRequired(request.reason());
        String comment = normalizeOptional(request.comment());

        /*
         * Item order must not change the meaning of the request.
         *
         * Therefore the fingerprint uses a deterministic order by SaleItem id.
         */
        List<CreateReturnItemRequest> orderedItems =
                request.items().stream()
                        .sorted(Comparator.comparing(
                                item -> item.saleItemId().toString()
                        ))
                        .toList();

        StringBuilder canonical = new StringBuilder();

        canonical.append(request.saleId())
                .append('|')
                .append(request.warehouseId())
                .append('|')
                .append(reason)
                .append('|')
                .append(comment == null ? "" : comment);

        for (CreateReturnItemRequest item : orderedItems) {
            canonical.append('|')
                    .append(item.saleItemId())
                    .append(':')
                    .append(item.quantity())
                    .append(':')
                    .append(item.condition().name());
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonical.toString()
                            .getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 недоступен",
                    exception
            );
        }
    }
}