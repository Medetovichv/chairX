package kg.chairx.payment.persistence;

import kg.chairx.payment.domain.Payment;
import kg.chairx.payment.domain.PaymentMethod;
import kg.chairx.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Payment payment) {
        jdbc.sql("""
                INSERT INTO payments (
                    id,
                    sale_id,
                    amount,
                    method,
                    status,
                    reference,
                    comment,
                    paid_by,
                    paid_at,
                    cancelled_by,
                    cancelled_at,
                    cancellation_reason,
                    payment_channel
                )
                VALUES (
                    :id,
                    :saleId,
                    :amount,
                    :method,
                    :status,
                    :reference,
                    :comment,
                    :paidBy,
                    :paidAt,
                    :cancelledBy,
                    :cancelledAt,
                    :cancellationReason,
                    :paymentChannel
                )
                """)
                .param("id", payment.id())
                .param("saleId", payment.saleId())
                .param("amount", payment.amount())
                .param("method", payment.method().name())
                .param("status", payment.status().name())
                .param("paymentChannel", payment.channel().name())
                .param(
                        "reference",
                        payment.reference(),
                        Types.VARCHAR
                )
                .param(
                        "comment",
                        payment.comment(),
                        Types.VARCHAR
                )
                .param("paidBy", payment.paidBy())
                .param(
                        "paidAt",
                        toOffsetDateTime(payment.paidAt())
                )
                .param(
                        "cancelledBy",
                        payment.cancelledBy(),
                        Types.VARCHAR
                )
                .param(
                        "cancelledAt",
                        toOffsetDateTime(payment.cancelledAt()),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "cancellationReason",
                        payment.cancellationReason(),
                        Types.VARCHAR
                )
                .update();
    }

    public Optional<Payment> find(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM payments
                WHERE id = :id
                """)
                .param("id", id)
                .query(PaymentRepository::mapPayment)
                .optional();
    }

    public Optional<Payment> lock(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM payments
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", id)
                .query(PaymentRepository::mapPayment)
                .optional();
    }

    public Optional<Payment> findActiveBySale(UUID saleId) {
        return jdbc.sql("""
                SELECT *
                FROM payments
                WHERE sale_id = :saleId
                  AND status = 'PAID'
                """)
                .param("saleId", saleId)
                .query(PaymentRepository::mapPayment)
                .optional();
    }

    public Optional<Payment> lockActiveBySale(UUID saleId) {
        return jdbc.sql("""
                SELECT *
                FROM payments
                WHERE sale_id = :saleId
                  AND status = 'PAID'
                FOR UPDATE
                """)
                .param("saleId", saleId)
                .query(PaymentRepository::mapPayment)
                .optional();
    }

    public List<Payment> findBySale(UUID saleId) {
        return jdbc.sql("""
                SELECT *
                FROM payments
                WHERE sale_id = :saleId
                ORDER BY paid_at, id
                """)
                .param("saleId", saleId)
                .query(PaymentRepository::mapPayment)
                .list();
    }

    public void cancel(
            UUID paymentId,
            String actor,
            Instant cancelledAt,
            String reason
    ) {
        int updated = jdbc.sql("""
                UPDATE payments
                SET status = 'CANCELLED',
                    cancelled_by = :actor,
                    cancelled_at = :cancelledAt,
                    cancellation_reason = :reason
                WHERE id = :id
                  AND status = 'PAID'
                """)
                .param("id", paymentId)
                .param("actor", actor)
                .param(
                        "cancelledAt",
                        toOffsetDateTime(cancelledAt)
                )
                .param("reason", reason)
                .update();

        if (updated != 1) {
            throw new IllegalStateException(
                    "Оплата уже аннулирована или не существует"
            );
        }
    }

    private static Payment mapPayment(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new Payment(
                rs.getObject("id", UUID.class),
                rs.getObject("sale_id", UUID.class),
                rs.getBigDecimal("amount"),
                PaymentMethod.valueOf(
                        rs.getString("method")
                ),
                PaymentStatus.valueOf(
                        rs.getString("status")
                ),
                rs.getString("reference"),
                rs.getString("comment"),
                rs.getString("paid_by"),
                instant(rs, "paid_at"),
                rs.getString("cancelled_by"),
                instant(rs, "cancelled_at"),
                rs.getString("cancellation_reason"),
                rs.getString("payment_channel") == null
                        ? kg.chairx.payment.domain.PaymentChannel.defaultFor(
                                PaymentMethod.valueOf(rs.getString("method")))
                        : kg.chairx.payment.domain.PaymentChannel.valueOf(
                                rs.getString("payment_channel"))
        );
    }

    private static OffsetDateTime toOffsetDateTime(
            Instant instant
    ) {
        return instant == null
                ? null
                : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(
            ResultSet rs,
            String column
    ) throws SQLException {
        OffsetDateTime value =
                rs.getObject(
                        column,
                        OffsetDateTime.class
                );

        return value == null
                ? null
                : value.toInstant();
    }
}