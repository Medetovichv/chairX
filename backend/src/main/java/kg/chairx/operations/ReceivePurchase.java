package kg.chairx.operations;

import jakarta.validation.Valid;

import java.util.Comparator;
import java.util.UUID;

import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.cost.InventoryCostService;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.purchase.api.CreatePurchaseReceiptRequest;
import kg.chairx.purchase.api.PurchaseReceiptResponse;
import kg.chairx.purchase.application.PurchaseService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ReceivePurchase {

    private final PurchaseService purchases;
    private final InventoryService inventory;
    private final InventoryCostService costs;

    public ReceivePurchase(
            PurchaseService purchases,
            InventoryService inventory,
            InventoryCostService costs
    ) {
        this.purchases = purchases;
        this.inventory = inventory;
        this.costs = costs;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse receive(
            UUID purchaseId,
            @Valid CreatePurchaseReceiptRequest request
    ) {
        var posting = purchases.prepareReceipt(purchaseId, request);
        var receipt = posting.receipt();

        // Повторный запрос не должен создавать новые складские
        // движения или дублировать партии себестоимости.
        if (!posting.replayed()) {

            // Стабильный порядок обработки вариантов товара
            // уменьшает риск взаимных блокировок.
            var sortedItems = receipt.items()
                    .stream()
                    .sorted(Comparator.comparing(
                            item -> item.productVariantId().toString()
                    ))
                    .toList();

            for (var item : sortedItems) {

                // 1. Регистрируем поступление товара на склад.
                var movement = inventory.recordMovement(
                        new RecordStockMovement(
                                item.id(),
                                receipt.warehouseId(),
                                item.productVariantId(),
                                StockMovementType.PURCHASE_IN,
                                item.quantity(),
                                "PURCHASE_RECEIPT",
                                receipt.id(),
                                receipt.createdBy()
                        )
                );

                // 2. Создаём FIFO-партию с фактической себестоимостью.
                // totalCost включает закупочную стоимость
                // и распределённые расходы на доставку.
                costs.recordPurchaseReceipt(
                        movement,
                        receipt.warehouseId(),
                        item.productVariantId(),
                        item.quantity(),
                        item.totalCost()
                );
            }
        }

        return receipt;
    }
}