package kg.chairx.purchase.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.chairx.purchase.api.PurchaseItemRequest;
import kg.chairx.purchase.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PurchaseRepository {
    private final JdbcClient jdbc;
    public PurchaseRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Purchase> find(UUID id) { return header(id, false); }
    public Optional<Purchase> lock(UUID id) { return header(id, true); }

    private Optional<Purchase> header(UUID id, boolean lock) {
        return jdbc.sql("SELECT * FROM purchases WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query(PurchaseRepository::purchase).optional();
    }

    public void insert(UUID id, UUID supplierId, BigDecimal cargo, String comment, String actor) {
        jdbc.sql("""
                INSERT INTO purchases(id,supplier_id,status,cargo_cost,cargo_allocation_method,
                    comment,created_by,created_at,updated_at)
                VALUES (:id,:supplier,'DRAFT',:cargo,'BY_QUANTITY',:comment,:actor,clock_timestamp(),clock_timestamp())
                """).param("id", id).param("supplier", supplierId).param("cargo", cargo, Types.NUMERIC)
                .param("comment", comment, Types.VARCHAR).param("actor", actor).update();
    }

    public void updateDraft(UUID id, UUID supplierId, BigDecimal cargo, String comment) {
        jdbc.sql("""
                UPDATE purchases SET supplier_id=:supplier,cargo_cost=:cargo,comment=:comment,updated_at=clock_timestamp()
                WHERE id=:id
                """).param("id",id).param("supplier",supplierId).param("cargo",cargo,Types.NUMERIC)
                .param("comment",comment,Types.VARCHAR).update();
    }

    public void replaceDraftItems(UUID purchaseId, List<PurchaseItemRequest> items) {
        jdbc.sql("DELETE FROM purchase_items WHERE purchase_id=:id").param("id", purchaseId).update();
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            jdbc.sql("""
                    INSERT INTO purchase_items(id,purchase_id,product_variant_id,line_number,ordered_quantity,purchase_unit_cost)
                    VALUES (:id,:purchase,:variant,:line,:quantity,:cost)
                    """).param("id",UUID.randomUUID()).param("purchase",purchaseId).param("variant",item.productVariantId())
                    .param("line",index+1).param("quantity",item.orderedQuantity()).param("cost",item.purchaseUnitCost()).update();
        }
    }

    public List<PurchaseItem> items(UUID purchaseId) {
        return jdbc.sql("SELECT * FROM purchase_items WHERE purchase_id=:id ORDER BY line_number")
                .param("id",purchaseId).query(PurchaseRepository::item).list();
    }

    public void setStatus(UUID id, PurchaseStatus status) {
        jdbc.sql("""
                UPDATE purchases SET status=:status,updated_at=clock_timestamp(),
                    confirmed_at=CASE WHEN :status='CONFIRMED' THEN COALESCE(confirmed_at,clock_timestamp()) ELSE confirmed_at END
                WHERE id=:id
                """).param("status",status.name()).param("id",id).update();
    }

    public void setCargo(UUID id, BigDecimal cargo) {
        jdbc.sql("UPDATE purchases SET cargo_cost=:cargo,updated_at=clock_timestamp() WHERE id=:id")
                .param("cargo",cargo,Types.NUMERIC).param("id",id).update();
    }

    public void setItemCosts(UUID id, BigDecimal cargo, BigDecimal finalUnitCost) {
        jdbc.sql("UPDATE purchase_items SET allocated_cargo_cost=:cargo,final_unit_cost=:unit WHERE id=:id")
                .param("cargo",cargo,Types.NUMERIC).param("unit",finalUnitCost,Types.NUMERIC).param("id",id).update();
    }

    public List<Purchase> list(int page, int size) {
        return jdbc.sql("SELECT * FROM purchases ORDER BY id LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",(long)page*size).query(PurchaseRepository::purchase).list();
    }
    public long count() { return jdbc.sql("SELECT count(*) FROM purchases").query(Long.class).single(); }

    public Optional<PurchaseReceipt> receiptByKey(UUID purchaseId, UUID key) {
        return jdbc.sql("SELECT id FROM purchase_receipts WHERE purchase_id=:purchase AND idempotency_key=:key")
                .param("purchase",purchaseId).param("key",key).query(UUID.class).optional()
                .flatMap(id -> receipt(purchaseId,id));
    }

    public Optional<PurchaseReceipt> receipt(UUID purchaseId, UUID id) {
        var header = jdbc.sql("SELECT * FROM purchase_receipts WHERE purchase_id=:purchase AND id=:id")
                .param("purchase",purchaseId).param("id",id).query(PurchaseRepository::receiptHeader).optional();
        return header.map(value -> new PurchaseReceipt(value.id(),value.purchaseId(),value.warehouseId(),
                value.idempotencyKey(),value.requestFingerprint(),value.postedAt(),value.createdBy(),value.comment(),receiptItems(id)));
    }

    private List<PurchaseReceiptItem> receiptItems(UUID receiptId) {
        return jdbc.sql("""
                SELECT ri.*, pi.product_variant_id FROM purchase_receipt_items ri
                JOIN purchase_items pi ON pi.id=ri.purchase_item_id
                WHERE ri.receipt_id=:id ORDER BY pi.line_number
                """).param("id",receiptId).query((r,n) -> new PurchaseReceiptItem(
                        r.getObject("id",UUID.class),r.getObject("purchase_item_id",UUID.class),
                        r.getObject("product_variant_id",UUID.class),r.getLong("quantity"),
                        r.getBigDecimal("allocated_cargo_cost"),r.getBigDecimal("total_cost"))).list();
    }

    public void insertReceipt(UUID id, UUID purchaseId, UUID warehouse, UUID key, String fingerprint, String comment, String actor) {
        jdbc.sql("""
                INSERT INTO purchase_receipts(id,purchase_id,warehouse_id,idempotency_key,request_fingerprint,posted_at,created_by,comment)
                VALUES (:id,:purchase,:warehouse,:key,:fingerprint,clock_timestamp(),:actor,:comment)
                """).param("id",id).param("purchase",purchaseId).param("warehouse",warehouse).param("key",key)
                .param("fingerprint",fingerprint).param("actor",actor).param("comment",comment,Types.VARCHAR).update();
    }

    public void insertReceiptItem(UUID id, UUID purchaseId, UUID receiptId, UUID itemId, long quantity,
            BigDecimal cargo, BigDecimal total) {
        jdbc.sql("""
                INSERT INTO purchase_receipt_items(id,purchase_id,receipt_id,purchase_item_id,quantity,allocated_cargo_cost,total_cost)
                VALUES (:id,:purchase,:receipt,:item,:quantity,:cargo,:total)
                """).param("id",id).param("purchase",purchaseId).param("receipt",receiptId).param("item",itemId)
                .param("quantity",quantity).param("cargo",cargo).param("total",total).update();
        jdbc.sql("UPDATE purchase_items SET received_quantity=received_quantity+:quantity WHERE id=:id")
                .param("quantity",quantity).param("id",itemId).update();
    }

    public void finishReceipt(UUID purchaseId, PurchaseStatus status) {
        jdbc.sql("""
                UPDATE purchases SET
                    status=:status,
                    costs_locked_at=COALESCE(costs_locked_at,clock_timestamp()),updated_at=clock_timestamp()
                WHERE id=:id
                """).param("id",purchaseId).param("status",status.name()).update();
    }

    public List<PurchaseReceipt> listReceipts(UUID purchaseId, int page, int size) {
        return jdbc.sql("SELECT * FROM purchase_receipts WHERE purchase_id=:id ORDER BY posted_at,id LIMIT :size OFFSET :offset")
                .param("id",purchaseId).param("size",size).param("offset",(long)page*size)
                .query(PurchaseRepository::receiptHeader).list();
    }
    public long receiptCount(UUID purchaseId) {
        return jdbc.sql("SELECT count(*) FROM purchase_receipts WHERE purchase_id=:id")
                .param("id",purchaseId).query(Long.class).single();
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        var timestamp = r.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static Purchase purchase(ResultSet r, int n) throws SQLException {
        return new Purchase(r.getObject("id",UUID.class),r.getObject("supplier_id",UUID.class),
                PurchaseStatus.valueOf(r.getString("status")),r.getBigDecimal("cargo_cost"),r.getString("cargo_allocation_method"),
                instant(r,"costs_locked_at"),instant(r,"confirmed_at"),r.getString("comment"),r.getString("created_by"),
                instant(r,"created_at"),instant(r,"updated_at"));
    }
    private static PurchaseItem item(ResultSet r, int n) throws SQLException {
        return new PurchaseItem(r.getObject("id",UUID.class),r.getObject("purchase_id",UUID.class),
                r.getObject("product_variant_id",UUID.class),r.getInt("line_number"),r.getLong("ordered_quantity"),
                r.getLong("received_quantity"),r.getBigDecimal("purchase_unit_cost"),
                r.getBigDecimal("allocated_cargo_cost"),r.getBigDecimal("final_unit_cost"));
    }
    private static PurchaseReceipt receiptHeader(ResultSet r, int n) throws SQLException {
        return new PurchaseReceipt(r.getObject("id",UUID.class),r.getObject("purchase_id",UUID.class),
                r.getObject("warehouse_id",UUID.class),r.getObject("idempotency_key",UUID.class),r.getString("request_fingerprint"),
                instant(r,"posted_at"),r.getString("created_by"),r.getString("comment"),List.of());
    }
}
