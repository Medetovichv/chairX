package kg.chairx.inventory.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.cost.InventoryCostRepository;
import kg.chairx.inventory.cost.InventoryCostService;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.inventory.persistence.InventoryRepository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryTransferService {

    private final JdbcClient jdbc;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventory;
    private final InventoryCostRepository costRepository;
    private final InventoryCostService costs;

    public InventoryTransferService(
            JdbcClient jdbc,
            InventoryRepository inventoryRepository,
            InventoryService inventory,
            InventoryCostRepository costRepository,
            InventoryCostService costs
    ) {
        this.jdbc = jdbc;
        this.inventoryRepository = inventoryRepository;
        this.inventory = inventory;
        this.costRepository = costRepository;
        this.costs = costs;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResult transfer(
            UUID transferId,
            UUID sourceWarehouse,
            UUID destinationWarehouse,
            UUID variantId,
            long quantity,
            String actor
    ) {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(sourceWarehouse, "sourceWarehouse");
        Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        Objects.requireNonNull(variantId, "variantId");

        if (sourceWarehouse.equals(destinationWarehouse)) {
            throw new IllegalArgumentException(
                    "Склад отправления и назначения должны отличаться"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Количество должно быть положительным"
            );
        }

        if (actor != null &&
                (actor.isBlank() || actor.length() > 200)) {
            throw new IllegalArgumentException(
                    "Некорректное имя инициатора"
            );
        }

        // Уникальный ID операции обеспечивает идемпотентность.
        jdbc.sql("""
                INSERT INTO inventory_transfers (
                    id,
                    source_warehouse_id,
                    destination_warehouse_id,
                    product_variant_id,
                    quantity,
                    actor
                )
                VALUES (
                    :id,
                    :source,
                    :destination,
                    :variant,
                    :quantity,
                    :actor
                )
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", transferId)
                .param("source", sourceWarehouse)
                .param("destination", destinationWarehouse)
                .param("variant", variantId)
                .param("quantity", quantity)
                .param("actor", actor, java.sql.Types.VARCHAR)
                .update();

        var saved = jdbc.sql("""
                SELECT
                    source_warehouse_id,
                    destination_warehouse_id,
                    product_variant_id,
                    quantity,
                    actor,
                    out_movement_id,
                    in_movement_id
                FROM inventory_transfers
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", transferId)
                .query((rs, row) -> new SavedTransfer(
                        rs.getObject(
                                "source_warehouse_id", UUID.class),
                        rs.getObject(
                                "destination_warehouse_id", UUID.class),
                        rs.getObject(
                                "product_variant_id", UUID.class),
                        rs.getLong("quantity"),
                        rs.getString("actor"),
                        rs.getObject(
                                "out_movement_id", UUID.class),
                        rs.getObject(
                                "in_movement_id", UUID.class)
                ))
                .single();

        // Повторное использование ID с другими параметрами запрещено.
        if (!saved.source().equals(sourceWarehouse)
                || !saved.destination().equals(destinationWarehouse)
                || !saved.variant().equals(variantId)
                || saved.quantity() != quantity
                || !Objects.equals(saved.actor(), actor)) {

            throw new IllegalStateException(
                    "TRANSFER_OPERATION_CONFLICT"
            );
        }

        // Повторный запрос возвращает прежний результат.
        if (saved.outMovement() != null) {
            BigDecimal totalCost = costRepository.consumedCost(
                    saved.outMovement()
            );

            if (totalCost == null || saved.inMovement() == null) {
                throw new IllegalStateException(
                        "TRANSFER_COST_INTEGRITY_ERROR"
                );
            }

            return new TransferResult(
                    transferId,
                    saved.outMovement(),
                    saved.inMovement(),
                    quantity,
                    totalCost
            );
        }

        /*
         * Блокируем балансы в одинаковом порядке.
         * Это предотвращает взаимную блокировку при
         * одновременных HOME -> OFFICE и OFFICE -> HOME.
         */
        List<UUID> orderedWarehouses = List.of(
                sourceWarehouse,
                destinationWarehouse
        ).stream().sorted().toList();

        for (UUID warehouse : orderedWarehouses) {
            inventoryRepository.lockOrCreate(warehouse, variantId);
        }

        UUID outOperationId = derivedId(
                transferId, "TRANSFER_OUT"
        );

        UUID inOperationId = derivedId(
                transferId, "TRANSFER_IN"
        );

        // 1. Физическое списание со склада отправления.
        var out = inventory.recordMovement(
                new RecordStockMovement(
                        outOperationId,
                        sourceWarehouse,
                        variantId,
                        StockMovementType.TRANSFER_OUT,
                        quantity,
                        "INVENTORY_TRANSFER",
                        transferId,
                        actor
                )
        );

        // 2. FIFO-списание фактической себестоимости.
        BigDecimal totalCost = costs.consumeTransfer(out);

        // 3. Физическое поступление на склад назначения.
        var in = inventory.recordMovement(
                new RecordStockMovement(
                        inOperationId,
                        destinationWarehouse,
                        variantId,
                        StockMovementType.TRANSFER_IN,
                        quantity,
                        "INVENTORY_TRANSFER",
                        transferId,
                        actor
                )
        );

        // 4. Новая партия на складе назначения.
        costRepository.createReceiptLayer(
                in.id(),
                destinationWarehouse,
                variantId,
                quantity,
                totalCost,
                in.occurredAt()
        );

        // 5. Сохраняем происхождение FIFO-себестоимости.
        int origins = jdbc.sql("""
                INSERT INTO inventory_transfer_cost_origins (
                    transfer_id,
                    source_allocation_id,
                    quantity,
                    amount
                )
                SELECT
                    :transferId,
                    a.id,
                    a.quantity,
                    a.allocated_cost
                FROM inventory_cost_allocations a
                WHERE a.stock_movement_id = :outMovement
                """)
                .param("transferId", transferId)
                .param("outMovement", out.id())
                .update();

        if (origins == 0) {
            throw new IllegalStateException(
                    "TRANSFER_FIFO_ORIGIN_MISSING"
            );
        }

        // 6. Помечаем перемещение завершённым.
        jdbc.sql("""
                UPDATE inventory_transfers
                SET
                    out_movement_id = :outMovement,
                    in_movement_id = :inMovement
                WHERE id = :id
                """)
                .param("id", transferId)
                .param("outMovement", out.id())
                .param("inMovement", in.id())
                .update();

        return new TransferResult(
                transferId,
                out.id(),
                in.id(),
                quantity,
                totalCost
        );
    }

    private static UUID derivedId(UUID transferId, String effect) {
        return UUID.nameUUIDFromBytes(
                ("chairx:inventory-transfer:"
                        + transferId + ":" + effect)
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private record SavedTransfer(
            UUID source,
            UUID destination,
            UUID variant,
            long quantity,
            String actor,
            UUID outMovement,
            UUID inMovement
    ) {}

    public record TransferResult(
            UUID transferId,
            UUID outMovementId,
            UUID inMovementId,
            long quantity,
            BigDecimal totalCost
    ) {}
}