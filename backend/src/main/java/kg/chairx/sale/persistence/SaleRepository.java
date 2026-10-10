package kg.chairx.sale.persistence;

import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.api.SaleSummary;
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

    public List<SaleSummary> listSummaries(
            SaleStatus status, Instant from, Instant to, String number,
            int page, int size
    ) {
        return jdbc.sql("""
                SELECT s.id,s.sale_number,s.created_at,s.customer_id,
                       c.full_name AS customer_name,s.status,s.fulfillment_type,
                       COALESCE(SUM(si.quantity*si.unit_sale_price),0) AS total
                FROM sales s
                LEFT JOIN customers c ON c.id=s.customer_id
                LEFT JOIN sale_items si ON si.sale_id=s.id
                WHERE (CAST(:status AS varchar) IS NULL OR s.status=:status)
                  AND (CAST(:fromDate AS timestamptz) IS NULL OR s.created_at>=:fromDate)
                  AND (CAST(:toDate AS timestamptz) IS NULL OR s.created_at<:toDate)
                  AND (CAST(:number AS varchar) IS NULL OR s.sale_number ILIKE '%'||:number||'%')
                GROUP BY s.id,c.full_name
                ORDER BY s.created_at DESC,s.id DESC LIMIT :size OFFSET :offset
                """).param("status",status==null?null:status.name(),Types.VARCHAR)
                .param("fromDate",from==null?null:java.time.OffsetDateTime.ofInstant(from,java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("toDate",to==null?null:java.time.OffsetDateTime.ofInstant(to,java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("number",number,Types.VARCHAR).param("size",size)
                .param("offset",(long)page*size)
                .query((rs,row)->new SaleSummary(
                        rs.getObject("id",UUID.class),rs.getString("sale_number"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("customer_id",UUID.class),rs.getString("customer_name"),
                        rs.getBigDecimal("total"),SaleStatus.valueOf(rs.getString("status")),
                        FulfillmentType.valueOf(rs.getString("fulfillment_type")))).list();
    }

    public long countSummaries(SaleStatus status,Instant from,Instant to,String number) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM sales s
                WHERE (CAST(:status AS varchar) IS NULL OR s.status=:status)
                  AND (CAST(:fromDate AS timestamptz) IS NULL OR s.created_at>=:fromDate)
                  AND (CAST(:toDate AS timestamptz) IS NULL OR s.created_at<:toDate)
                  AND (CAST(:number AS varchar) IS NULL OR s.sale_number ILIKE '%'||:number||'%')
                """).param("status",status==null?null:status.name(),Types.VARCHAR)
                .param("fromDate",from==null?null:java.time.OffsetDateTime.ofInstant(from,java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("toDate",to==null?null:java.time.OffsetDateTime.ofInstant(to,java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("number",number,Types.VARCHAR).query(Long.class).single();
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

    public boolean tryInsertDraft(UUID id, String saleNumber, UUID customerId,
                                  FulfillmentType fulfillmentType, UUID key,
                                  String fingerprint, String comment, String actor) {
        int inserted = jdbc.sql("""
                INSERT INTO sales(id, sale_number, customer_id, fulfillment_type,
                                  status, idempotency_key, request_fingerprint,
                                  created_by, created_at, comment)
                VALUES (:id,:number,:customerId,:fulfillmentType,'DRAFT',
                        :key,:fingerprint,:actor,clock_timestamp(),:comment)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id",id).param("number",saleNumber)
                .param("customerId",customerId,Types.OTHER)
                .param("fulfillmentType",fulfillmentType == null ? null : fulfillmentType.name(),Types.VARCHAR)
                .param("key",key).param("fingerprint",fingerprint).param("actor",actor)
                .param("comment",comment,Types.VARCHAR).update();
        return inserted == 1;
    }

    public void updateDraft(UUID saleId, UUID customerId,
                            FulfillmentType fulfillmentType, String comment) {
        int changed = jdbc.sql("""
                UPDATE sales SET customer_id = :customerId,
                                 fulfillment_type = :fulfillmentType,
                                 comment = :comment
                WHERE id = :saleId AND status = 'DRAFT'
                """).param("saleId",saleId).param("customerId",customerId,Types.OTHER)
                .param("fulfillmentType",fulfillmentType == null ? null : fulfillmentType.name(),Types.VARCHAR)
                .param("comment",comment,Types.VARCHAR).update();
        if (changed != 1) throw new IllegalStateException("Черновик изменён параллельно");
    }

    public void deleteDraftItems(UUID saleId) {
        jdbc.sql("DELETE FROM sale_items WHERE sale_id = :saleId")
                .param("saleId",saleId).update();
    }

    public void confirmDraft(UUID saleId) {
        int changed = jdbc.sql("""
                UPDATE sales SET status = 'CONFIRMED'
                WHERE id = :saleId AND status = 'DRAFT' AND fulfillment_type IS NOT NULL
                """).param("saleId",saleId).update();
        if (changed != 1) throw new IllegalStateException("Черновик изменён параллельно");
    }

    public void cancelDraft(UUID saleId, String actor) {
        int changed = jdbc.sql("""
                UPDATE sales SET status = 'CANCELLED', cancelled_by = :actor,
                                 cancelled_at = clock_timestamp()
                WHERE id = :saleId AND status = 'DRAFT'
                """).param("saleId",saleId).param("actor",actor).update();
        if (changed != 1) throw new IllegalStateException("Черновик изменён параллельно");
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
                instant(rs, "cancelled_at"),
                rs.getString("comment")
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