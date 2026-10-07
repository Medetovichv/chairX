package kg.chairx.returning.persistence;

import kg.chairx.returning.domain.Return;
import kg.chairx.returning.domain.ReturnCondition;
import kg.chairx.returning.domain.ReturnItem;
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
public class ReturnRepository {

    private final JdbcClient jdbc;

    public ReturnRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryInsert(
            UUID id,
            UUID saleId,
            UUID warehouseId,
            UUID idempotencyKey,
            String requestFingerprint,
            String reason,
            String comment,
            String createdBy
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO returns (
                    id,
                    sale_id,
                    warehouse_id,
                    idempotency_key,
                    request_fingerprint,
                    reason,
                    comment,
                    created_by,
                    created_at
                )
                VALUES (
                    :id,
                    :saleId,
                    :warehouseId,
                    :idempotencyKey,
                    :requestFingerprint,
                    :reason,
                    :comment,
                    :createdBy,
                    clock_timestamp()
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id", id)
                .param("saleId", saleId)
                .param("warehouseId", warehouseId)
                .param("idempotencyKey", idempotencyKey)
                .param("requestFingerprint", requestFingerprint)
                .param("reason", reason)
                .param("comment", comment, Types.VARCHAR)
                .param("createdBy", createdBy)
                .update();

        return inserted == 1;
    }

    public void insertItem(ReturnItem item) {
        jdbc.sql("""
                INSERT INTO return_items (
                    id,
                    return_id,
                    sale_item_id,
                    quantity,
                    condition
                )
                VALUES (
                    :id,
                    :returnId,
                    :saleItemId,
                    :quantity,
                    :condition
                )
                """)
                .param("id", item.id())
                .param("returnId", item.returnId())
                .param("saleItemId", item.saleItemId())
                .param("quantity", item.quantity())
                .param("condition", item.condition().name())
                .update();
    }

    public Optional<Return> find(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM returns
                WHERE id = :id
                """)
                .param("id", id)
                .query(this::mapReturn)
                .optional();
    }

    public Optional<Return> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbc.sql("""
                SELECT *
                FROM returns
                WHERE idempotency_key = :idempotencyKey
                """)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapReturn)
                .optional();
    }

    public String requestFingerprint(UUID returnId) {
        return jdbc.sql("""
                SELECT request_fingerprint
                FROM returns
                WHERE id = :returnId
                """)
                .param("returnId", returnId)
                .query(String.class)
                .single();
    }

    public long returnedQuantity(UUID saleItemId) {
        Long quantity = jdbc.sql("""
                SELECT COALESCE(SUM(quantity), 0)
                FROM return_items
                WHERE sale_item_id = :saleItemId
                """)
                .param("saleItemId", saleItemId)
                .query(Long.class)
                .single();

        return quantity == null ? 0L : quantity;
    }

    public List<ReturnItem> items(UUID returnId) {
        return jdbc.sql("""
                SELECT *
                FROM return_items
                WHERE return_id = :returnId
                ORDER BY id
                """)
                .param("returnId", returnId)
                .query(ReturnRepository::mapItem)
                .list();
    }

    private Return mapReturn(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);

        return new Return(
                id,
                rs.getObject("sale_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                items(id),
                rs.getString("reason"),
                rs.getString("comment"),
                rs.getString("created_by"),
                instant(rs, "created_at")
        );
    }

    private static ReturnItem mapItem(ResultSet rs, int rowNum)
            throws SQLException {

        return new ReturnItem(
                rs.getObject("id", UUID.class),
                rs.getObject("return_id", UUID.class),
                rs.getObject("sale_item_id", UUID.class),
                rs.getLong("quantity"),
                ReturnCondition.valueOf(rs.getString("condition"))
        );
    }

    private static Instant instant(ResultSet rs, String column)
            throws SQLException {

        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}