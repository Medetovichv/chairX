package kg.chairx.operations;

import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.UUID;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.application.InventoryService;
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

    public ReceivePurchase(PurchaseService purchases,InventoryService inventory) {
        this.purchases=purchases;
        this.inventory=inventory;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse receive(UUID purchaseId,@Valid CreatePurchaseReceiptRequest request) {
        var posting=purchases.prepareReceipt(purchaseId,request);
        var receipt=posting.receipt();
        if (!posting.replayed()) {
            // One warehouse per receipt. Consistent variant ordering prevents lock-order cycles across purchases.
            for (var item:receipt.items().stream().sorted(Comparator.comparing(i -> i.productVariantId().toString())).toList()) {
                inventory.recordMovement(new RecordStockMovement(item.id(),receipt.warehouseId(),item.productVariantId(),
                        StockMovementType.PURCHASE_IN,item.quantity(),"PURCHASE_RECEIPT",receipt.id(),receipt.createdBy()));
            }
        }
        return receipt;
    }
}
