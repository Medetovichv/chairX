package kg.chairx.sale.persistence;

import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.Sale;
import kg.chairx.sale.domain.SaleItem;
import kg.chairx.sale.domain.SaleStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SaleRepository {

    private final JdbcClient jdbc;

    public SaleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long nextSaleNumber() {
        return jdbc.sql("SELECT nextval('sale_number_seq')")
                .query(Long.class)
                .single();
    }

    public boolean tryInsert(
            UUID id,
            String saleNumber,
            UUID customerId,
            FulfillmentType fulfillmentType,
            UUID idempotencyKey,
            String requestFingerprint,
            String createdBy
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO sales (
                    id,
                    sale_number,
                    customer_id,
                    fulfillment_type,
                    status,
                    idempotency_key,
                    request_fingerprint,
                    created_by,
                    created_at
                )
                VALUES (
                    :id,
                    :saleNumber,
                    :customerId,
                    :fulfillmentType,
                    'CONFIRMED',
                    :idempotencyKey,
                    :requestFingerprint,
                    :createdBy,
                    clock_timestamp()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id", id)
                .param("saleNumber", saleNumber)
                .param("customerId", customerId, Types.OTHER)
                .param("fulfillmentType", fulfillmentType.name())
                .param("idempotencyKey", idempotencyKey)
                .param("requestFingerprint", requestFingerprint)
                .param("createdBy", createdBy)
                .update();

        return inserted == 1;
    }

    public void insertItem(SaleItem item) {
        jdbc.sql("""
                INSERT INTO sale_items (
                    id,
                    sale_id,
                    product_variant_id,
                    warehouse_id,
                    quantity,
                    unit_sale_price
                )
                VALUES (
                    :id,
                    :saleId,
                    :productVariantId,
                    :warehouseId,
                    :quantity,
                    :unitSalePrice
                )
                """)
                .param("id", item.id())
                .param("saleId", item.saleId())
                .param("productVariantId", item.productVariantId())
                .param("warehouseId", item.warehouseId())
                .param("quantity", item.quantity())
                .param("unitSalePrice", item.unitSalePrice())
                .update();
    }

    public Optional<Sale> find(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM sales
                WHERE id = :id
                """)
                .param("id", id)
                .query(this::mapSale)
                .optional();
    }

    public Optional<Sale> lock(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM sales
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", id)
                .query(this::mapSale)
                .optional();
    }

    public Optional<Sale> findByIdempotencyKey(UUID key) {
        return jdbc.sql("""
                SELECT *
                FROM sales
                WHERE idempotency_key = :key
                """)
                .param("key", key)
                .query(this::mapSale)
                .optional();
    }

    public String requestFingerprint(UUID saleId) {
        return jdbc.sql("""
                SELECT request_fingerprint
                FROM sales
                WHERE id = :id
                """)
                .param("id", saleId)
                .query(String.class)
                .single();
    }

    public List<SaleItem> items(UUID saleId) {
        return jdbc.sql("""
                SELECT *
                FROM sale_items
                WHERE sale_id = :saleId
                ORDER BY id
                """)
                .param("saleId", saleId)
                .query(SaleRepository::mapItem)
                .list();
    }

    public void fulfill(
            UUID saleId,
            String actor
    ) {
        jdbc.sql("""
                UPDATE sales
                SET status = 'FULFILLED',
                    fulfilled_by = :actor,
                    fulfilled_at = clock_timestamp()
                WHERE id = :id
                  AND status = 'CONFIRMED'
                """)
                .param("id", saleId)
                .param("actor", actor)
                .update();
    }

    public void cancel(
            UUID saleId,
            String actor
    ) {
        jdbc.sql("""
                UPDATE sales
                SET status = 'CANCELLED',
                    cancelled_by = :actor,
                    cancelled_at = clock_timestamp()
                WHERE id = :id
                  AND status = 'CONFIRMED'
                """)
                .param("id", saleId)
                .param("actor", actor)
                .update();
    }

    private Sale mapSale(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);

        return new Sale(
                id,
                rs.getString("sale_number"),
                rs.getObject("customer_id", UUID.class),
                FulfillmentType.valueOf(
                        rs.getString("fulfillment_type")
                ),
                SaleStatus.valueOf(
                        rs.getString("status")
                ),
                items(id),
                rs.getString("created_by"),
                instant(rs, "created_at"),
                rs.getString("fulfilled_by"),
                instant(rs, "fulfilled_at"),
                rs.getString("cancelled_by"),
                instant(rs, "cancelled_at")
        );
    }

    private static SaleItem mapItem(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new SaleItem(
                rs.getObject("id", UUID.class),
                rs.getObject("sale_id", UUID.class),
                rs.getObject("product_variant_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getLong("quantity"),
                rs.getBigDecimal("unit_sale_price")
        );
    }

    private static Instant instant(
            ResultSet rs,
            String column
    ) throws SQLException {
        var timestamp = rs.getTimestamp(column);

        return timestamp == null
                ? null
                : timestamp.toInstant();
    }
}