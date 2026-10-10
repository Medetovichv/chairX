package kg.chairx.delivery.persistence;

import kg.chairx.delivery.domain.Delivery;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.delivery.api.DeliverySummary;
import java.util.List;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeliveryRepository {

    private final JdbcClient jdbc;

    public DeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeliverySummary> list(
            DeliveryStatus status,Instant from,Instant to,int page,int size) {
        return jdbc.sql("""
                SELECT id,sale_id,status,recipient_name,city_region,created_at
                FROM deliveries WHERE
                    (CAST(:status AS varchar) IS NULL OR status=:status)
                AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at>=:fromDate)
                AND (CAST(:toDate AS timestamptz) IS NULL OR created_at<:toDate)
                ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset
                """).param("status",status==null?null:status.name(),Types.VARCHAR)
                .param("fromDate",from==null?null:toOffsetDateTime(from),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("toDate",to==null?null:toOffsetDateTime(to),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("size",size).param("offset",(long)page*size)
                .query((rs,row)->new DeliverySummary(
                        rs.getObject("id",UUID.class),rs.getObject("sale_id",UUID.class),
                        DeliveryStatus.valueOf(rs.getString("status")),
                        rs.getString("recipient_name"),rs.getString("city_region"),
                        rs.getTimestamp("created_at").toInstant())).list();
    }

    public long count(DeliveryStatus status,Instant from,Instant to) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM deliveries WHERE
                    (CAST(:status AS varchar) IS NULL OR status=:status)
                AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at>=:fromDate)
                AND (CAST(:toDate AS timestamptz) IS NULL OR created_at<:toDate)
                """).param("status",status==null?null:status.name(),Types.VARCHAR)
                .param("fromDate",from==null?null:toOffsetDateTime(from),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("toDate",to==null?null:toOffsetDateTime(to),Types.TIMESTAMP_WITH_TIMEZONE)
                .query(Long.class).single();
    }

    public boolean tryInsert(Delivery delivery) {
        int updated = jdbc.sql("""
                        INSERT INTO deliveries (
                            id,
                            sale_id,
                            status,
                            recipient_name,
                            recipient_phone,
                            address,
                            city_region,
                            delivery_cost,
                            carrier_name,
                            tracking_number,
                            comment,
                            created_by,
                            created_at
                        )
                        VALUES (
                            :id,
                            :saleId,
                            :status,
                            :recipientName,
                            :recipientPhone,
                            :address,
                            :cityRegion,
                            :deliveryCost,
                            :carrierName,
                            :trackingNumber,
                            :comment,
                            :createdBy,
                            :createdAt
                        )
                        ON CONFLICT (sale_id) DO NOTHING
                        """)
                .param("id", delivery.id())
                .param("saleId", delivery.saleId())
                .param("status", delivery.status().name())
                .param("recipientName", delivery.recipientName())
                .param("recipientPhone", delivery.recipientPhone())
                .param("address", delivery.address())
                .param("cityRegion", delivery.cityRegion())
                .param("deliveryCost", delivery.deliveryCost())
                .param("carrierName", delivery.carrierName())
                .param("trackingNumber", delivery.trackingNumber())
                .param("comment", delivery.comment())
                .param("createdBy", delivery.createdBy())
                .param(
                        "createdAt",
                        toOffsetDateTime(delivery.createdAt())
                )
                .update();

        return updated == 1;
    }

    public Optional<Delivery> find(UUID deliveryId) {
        return jdbc.sql("""
                        SELECT *
                        FROM deliveries
                        WHERE id = :deliveryId
                        """)
                .param("deliveryId", deliveryId)
                .query(this::map)
                .optional();
    }

    public Optional<Delivery> findBySaleId(UUID saleId) {
        return jdbc.sql("""
                        SELECT *
                        FROM deliveries
                        WHERE sale_id = :saleId
                        """)
                .param("saleId", saleId)
                .query(this::map)
                .optional();
    }

    public Optional<Delivery> lock(UUID deliveryId) {
        return jdbc.sql("""
                        SELECT *
                        FROM deliveries
                        WHERE id = :deliveryId
                        FOR UPDATE
                        """)
                .param("deliveryId", deliveryId)
                .query(this::map)
                .optional();
    }

    public void dispatch(
            UUID deliveryId,
            String actor,
            Instant time
    ) {
        int updated = jdbc.sql("""
                        UPDATE deliveries
                        SET status = 'IN_TRANSIT',
                            dispatched_by = :actor,
                            dispatched_at = :time
                        WHERE id = :deliveryId
                          AND status = 'READY'
                        """)
                .param("deliveryId", deliveryId)
                .param("actor", actor)
                .param("time", toOffsetDateTime(time))
                .update();

        requireUpdated(updated);
    }

    public void deliver(
            UUID deliveryId,
            String actor,
            Instant time
    ) {
        int updated = jdbc.sql("""
                        UPDATE deliveries
                        SET status = 'DELIVERED',
                            delivered_by = :actor,
                            delivered_at = :time
                        WHERE id = :deliveryId
                          AND status = 'IN_TRANSIT'
                        """)
                .param("deliveryId", deliveryId)
                .param("actor", actor)
                .param("time", toOffsetDateTime(time))
                .update();

        requireUpdated(updated);
    }

    public void fail(
            UUID deliveryId,
            String actor,
            Instant time,
            String reason
    ) {
        int updated = jdbc.sql("""
                        UPDATE deliveries
                        SET status = 'FAILED',
                            failed_by = :actor,
                            failed_at = :time,
                            failure_reason = :reason
                        WHERE id = :deliveryId
                          AND status = 'IN_TRANSIT'
                        """)
                .param("deliveryId", deliveryId)
                .param("actor", actor)
                .param("time", toOffsetDateTime(time))
                .param("reason", reason)
                .update();

        requireUpdated(updated);
    }

    public void cancel(
            UUID deliveryId,
            String actor,
            Instant time
    ) {
        int updated = jdbc.sql("""
                        UPDATE deliveries
                        SET status = 'CANCELLED',
                            cancelled_by = :actor,
                            cancelled_at = :time
                        WHERE id = :deliveryId
                          AND status = 'READY'
                        """)
                .param("deliveryId", deliveryId)
                .param("actor", actor)
                .param("time", toOffsetDateTime(time))
                .update();

        requireUpdated(updated);
    }

    public void returnToWarehouse(
            UUID deliveryId,
            UUID warehouseId,
            String actor,
            Instant time
    ) {
        int updated = jdbc.sql("""
                        UPDATE deliveries
                        SET returned_to_warehouse_by = :actor,
                            returned_to_warehouse_at = :time,
                            return_warehouse_id = :warehouseId
                        WHERE id = :deliveryId
                          AND status = 'FAILED'
                          AND returned_to_warehouse_at IS NULL
                        """)
                .param("deliveryId", deliveryId)
                .param("warehouseId", warehouseId)
                .param("actor", actor)
                .param("time", toOffsetDateTime(time))
                .update();

        requireUpdated(updated);
    }

    private Delivery map(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new Delivery(
                rs.getObject("id", UUID.class),
                rs.getObject("sale_id", UUID.class),
                DeliveryStatus.valueOf(
                        rs.getString("status")
                ),
                rs.getString("recipient_name"),
                rs.getString("recipient_phone"),
                rs.getString("address"),
                rs.getString("city_region"),
                rs.getBigDecimal("delivery_cost"),
                rs.getString("carrier_name"),
                rs.getString("tracking_number"),
                rs.getString("comment"),
                rs.getString("created_by"),
                getInstant(rs, "created_at"),
                rs.getString("dispatched_by"),
                getInstant(rs, "dispatched_at"),
                rs.getString("delivered_by"),
                getInstant(rs, "delivered_at"),
                rs.getString("failed_by"),
                getInstant(rs, "failed_at"),
                rs.getString("failure_reason"),
                rs.getString("cancelled_by"),
                getInstant(rs, "cancelled_at"),
                rs.getString("returned_to_warehouse_by"),
                getInstant(rs, "returned_to_warehouse_at"),
                rs.getObject(
                        "return_warehouse_id",
                        UUID.class
                )
        );
    }

    private static Instant getInstant(
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

    private static OffsetDateTime toOffsetDateTime(
            Instant instant
    ) {
        return instant.atOffset(
                ZoneOffset.UTC
        );
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Состояние доставки изменилось параллельно"
            );
        }
    }
}