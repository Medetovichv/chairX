package kg.chairx.refund.persistence;

import kg.chairx.refund.domain.Refund;
import kg.chairx.refund.domain.RefundMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class RefundRepository {

    private final JdbcClient jdbc;

    public RefundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryInsert(
            Refund refund,
            UUID idempotencyKey,
            String requestFingerprint
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO refunds (
                    id,
                    sale_id,
                    return_id,
                    amount,
                    method,
                    reason,
                    reference,
                    comment,
                    idempotency_key,
                    request_fingerprint,
                    refunded_by,
                    refunded_at
                )
                VALUES (
                    :id,
                    :saleId,
                    :returnId,
                    :amount,
                    :method,
                    :reason,
                    :reference,
                    :comment,
                    :idempotencyKey,
                    :requestFingerprint,
                    :refundedBy,
                    :refundedAt
                )
                ON CONFLICT (idempotency_key)
                DO NOTHING
                """)
                .param("id", refund.id())
                .param("saleId", refund.saleId())
                .param(
                        "returnId",
                        refund.returnId(),
                        Types.OTHER
                )
                .param("amount", refund.amount())
                .param("method", refund.method().name())
                .param("reason", refund.reason())
                .param(
                        "reference",
                        refund.reference(),
                        Types.VARCHAR
                )
                .param(
                        "comment",
                        refund.comment(),
                        Types.VARCHAR
                )
                .param("idempotencyKey", idempotencyKey)
                .param(
                        "requestFingerprint",
                        requestFingerprint
                )
                .param("refundedBy", refund.refundedBy())
                .param(
                        "refundedAt",
                        refund.refundedAt()
                                .atOffset(ZoneOffset.UTC)
                )
                .update();

        return inserted == 1;
    }

    public Optional<Refund> find(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM refunds
                WHERE id = :id
                """)
                .param("id", id)
                .query(RefundRepository::mapRefund)
                .optional();
    }

    public Optional<Refund> findByIdempotencyKey(
            UUID idempotencyKey
    ) {
        return jdbc.sql("""
                SELECT *
                FROM refunds
                WHERE idempotency_key = :idempotencyKey
                """)
                .param(
                        "idempotencyKey",
                        idempotencyKey
                )
                .query(RefundRepository::mapRefund)
                .optional();
    }

    public Optional<String> requestFingerprint(
            UUID refundId
    ) {
        return jdbc.sql("""
                SELECT request_fingerprint
                FROM refunds
                WHERE id = :id
                """)
                .param("id", refundId)
                .query(String.class)
                .optional();
    }

    public BigDecimal refundedAmount(UUID saleId) {
        return jdbc.sql("""
                SELECT COALESCE(
                    SUM(amount),
                    0
                )
                FROM refunds
                WHERE sale_id = :saleId
                """)
                .param("saleId", saleId)
                .query(BigDecimal.class)
                .single();
    }

    public List<Refund> findBySale(UUID saleId) {
        return jdbc.sql("""
                SELECT *
                FROM refunds
                WHERE sale_id = :saleId
                ORDER BY refunded_at, id
                """)
                .param("saleId", saleId)
                .query(RefundRepository::mapRefund)
                .list();
    }

    private static Refund mapRefund(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        OffsetDateTime refundedAt =
                rs.getObject(
                        "refunded_at",
                        OffsetDateTime.class
                );

        return new Refund(
                rs.getObject("id", UUID.class),
                rs.getObject("sale_id", UUID.class),
                rs.getObject("return_id", UUID.class),
                rs.getBigDecimal("amount"),
                RefundMethod.valueOf(
                        rs.getString("method")
                ),
                rs.getString("reason"),
                rs.getString("reference"),
                rs.getString("comment"),
                rs.getString("refunded_by"),
                refundedAt.toInstant()
        );
    }
}