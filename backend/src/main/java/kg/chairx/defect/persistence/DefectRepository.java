package kg.chairx.defect.persistence;

import kg.chairx.defect.domain.Defect;
import kg.chairx.defect.domain.DefectStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import kg.chairx.defect.domain.ReceiptItemOrigin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DefectRepository {

    private final JdbcClient jdbc;

    public DefectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Defect defect) {
        jdbc.sql("""
                INSERT INTO defects (
                    id,
                    warehouse_id,
                    product_variant_id,
                    supplier_id,
                    purchase_receipt_item_id,
                    quantity,
                    status,
                    description,
                    resolution_note,
                    created_by,
                    created_at,
                    resolved_by,
                    resolved_at
                )
                VALUES (
                    :id,
                    :warehouseId,
                    :productVariantId,
                    :supplierId,
                    :purchaseReceiptItemId,
                    :quantity,
                    :status,
                    :description,
                    :resolutionNote,
                    :createdBy,
                    :createdAt,
                    :resolvedBy,
                    :resolvedAt
                )
                """)
                .param("id", defect.id())
                .param("warehouseId", defect.warehouseId())
                .param("productVariantId", defect.productVariantId())
                .param(
                        "supplierId",
                        defect.supplierId(),
                        Types.OTHER
                )
                .param(
                        "purchaseReceiptItemId",
                        defect.purchaseReceiptItemId(),
                        Types.OTHER
                )
                .param("quantity", defect.quantity())
                .param("status", defect.status().name())
                .param("description", defect.description())
                .param(
                        "resolutionNote",
                        defect.resolutionNote(),
                        Types.VARCHAR
                )
                .param(
                        "createdBy",
                        defect.createdBy(),
                        Types.VARCHAR
                )
                .param(
                        "createdAt",
                        OffsetDateTime.ofInstant(
                                defect.createdAt(),
                                ZoneOffset.UTC
                        ),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "resolvedBy",
                        defect.resolvedBy(),
                        Types.VARCHAR
                )
                .param(
                        "resolvedAt",
                        defect.resolvedAt() == null
                                ? null
                                : OffsetDateTime.ofInstant(
                                defect.resolvedAt(),
                                ZoneOffset.UTC
                        ),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .update();
    }

    public Optional<Defect> findByIdForUpdate(UUID id) {
        return jdbc.sql("""
                SELECT
                    id,
                    warehouse_id,
                    product_variant_id,
                    supplier_id,
                    purchase_receipt_item_id,
                    quantity,
                    status,
                    description,
                    resolution_note,
                    created_by,
                    created_at,
                    resolved_by,
                    resolved_at
                FROM defects
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public Optional<ReceiptItemOrigin> findReceiptItemOrigin(
            UUID receiptItemId
    ) {
        return jdbc.sql("""
            SELECT
                ri.id AS receipt_item_id,
                pr.warehouse_id,
                pi.product_variant_id,
                p.supplier_id
            FROM purchase_receipt_items ri
            JOIN purchase_receipts pr
              ON pr.id = ri.receipt_id
             AND pr.purchase_id = ri.purchase_id
            JOIN purchase_items pi
              ON pi.id = ri.purchase_item_id
             AND pi.purchase_id = ri.purchase_id
            JOIN purchases p
              ON p.id = ri.purchase_id
            WHERE ri.id = :id
            """)
                .param("id", receiptItemId)
                .query((rs, rowNum) -> new ReceiptItemOrigin(
                        rs.getObject(
                                "receipt_item_id",
                                UUID.class
                        ),
                        rs.getObject(
                                "warehouse_id",
                                UUID.class
                        ),
                        rs.getObject(
                                "product_variant_id",
                                UUID.class
                        ),
                        rs.getObject(
                                "supplier_id",
                                UUID.class
                        )
                ))
                .optional();
    }

    public void update(Defect defect) {
        jdbc.sql("""
                UPDATE defects
                SET status = :status,
                    resolution_note = :resolutionNote,
                    resolved_by = :resolvedBy,
                    resolved_at = :resolvedAt
                WHERE id = :id
                """)
                .param("status", defect.status().name())
                .param(
                        "resolutionNote",
                        defect.resolutionNote(),
                        Types.VARCHAR
                )
                .param(
                        "resolvedBy",
                        defect.resolvedBy(),
                        Types.VARCHAR
                )
                .param(
                        "resolvedAt",
                        defect.resolvedAt() == null
                                ? null
                                : OffsetDateTime.ofInstant(
                                defect.resolvedAt(),
                                ZoneOffset.UTC
                        ),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param("id", defect.id())
                .update();
    }

    private Defect map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime createdAt = rs.getObject(
                "created_at",
                OffsetDateTime.class
        );

        OffsetDateTime resolvedAt = rs.getObject(
                "resolved_at",
                OffsetDateTime.class
        );

        return new Defect(
                rs.getObject("id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("product_variant_id", UUID.class),
                rs.getObject("supplier_id", UUID.class),
                rs.getObject("purchase_receipt_item_id", UUID.class),
                rs.getLong("quantity"),
                DefectStatus.valueOf(rs.getString("status")),
                rs.getString("description"),
                rs.getString("resolution_note"),
                rs.getString("created_by"),
                createdAt.toInstant(),
                rs.getString("resolved_by"),
                resolvedAt == null
                        ? null
                        : resolvedAt.toInstant()
        );
    }
}