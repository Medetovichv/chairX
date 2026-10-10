package kg.chairx.purchase.application;

import kg.chairx.common.web.InvalidQueryException;
import kg.chairx.purchase.api.PurchaseOverviewPage;
import kg.chairx.purchase.domain.PurchaseStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PurchaseOverviewService {
    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;

    public PurchaseOverviewService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PurchaseOverviewPage list(PurchaseStatus status, UUID supplierId, LocalDate from,
                                      LocalDate to, String number, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))
                || (number != null && number.length() > 100)) {
            throw new InvalidQueryException("Некорректные параметры списка закупок");
        }
        Instant start = from == null ? null : from.atStartOfDay(BISHKEK).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(BISHKEK).toInstant();
        String query = number == null || number.isBlank() ? null : "%" + number.strip() + "%";
        String filter = """
                WHERE (CAST(:status AS varchar) IS NULL OR p.status = :status)
                  AND (CAST(:supplier AS uuid) IS NULL OR p.supplier_id = :supplier)
                  AND (CAST(:fromDate AS timestamptz) IS NULL OR p.created_at >= :fromDate)
                  AND (CAST(:toDate AS timestamptz) IS NULL OR p.created_at < :toDate)
                  AND (CAST(:number AS varchar) IS NULL OR p.id::text ILIKE :number)
                """;
        var args = new Object[] {status == null ? null : status.name(), supplierId, start, end, query};

        var sql = """
                SELECT p.id, p.supplier_id, s.name AS supplier_name, p.status, p.created_at,
                       items.item_count, items.ordered_quantity, items.received_quantity,
                       items.goods_cost, COALESCE(p.cargo_cost,0) AS cargo_cost,
                       items.goods_cost + COALESCE(p.cargo_cost,0) AS total_cost,
                       r.last_receipt_at
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                LEFT JOIN LATERAL (
                   SELECT COUNT(*) AS item_count,
                          COALESCE(SUM(i.ordered_quantity),0) AS ordered_quantity,
                          COALESCE(SUM(i.received_quantity),0) AS received_quantity,
                          COALESCE(SUM(i.ordered_quantity * i.purchase_unit_cost),0) AS goods_cost
                   FROM purchase_items i WHERE i.purchase_id = p.id
                ) items ON TRUE
                LEFT JOIN LATERAL (
                    SELECT MAX(pr.posted_at) AS last_receipt_at
                    FROM purchase_receipts pr WHERE pr.purchase_id = p.id
                ) r ON TRUE
                """ + filter + """
                ORDER BY p.created_at DESC,p.id DESC
                LIMIT :size OFFSET :offset
                """;
        // Common predicates intentionally identical for rows and total; no per-purchase SELECT.
        var rows = bind(jdbc.sql(sql),args).param("size",size).param("offset",(long)page*size)
                .query((rs,row) -> {
                    long ordered = rs.getLong("ordered_quantity");
                    long received = rs.getLong("received_quantity");
                    return new PurchaseOverviewPage.Line(
                            rs.getObject("id",UUID.class),rs.getObject("supplier_id",UUID.class),
                            rs.getString("supplier_name"),
                            PurchaseStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getLong("item_count"),ordered,received,ordered-received,
                            rs.getBigDecimal("goods_cost"),rs.getBigDecimal("cargo_cost"),
                            rs.getBigDecimal("total_cost"),
                            rs.getTimestamp("last_receipt_at") == null ? null :
                                    rs.getTimestamp("last_receipt_at").toInstant());
                }).list();
        long count = bind(jdbc.sql("SELECT COUNT(*) FROM purchases p " + filter),args).query(Long.class).single();
        return new PurchaseOverviewPage(rows,page,size,count);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Object[] args) {
        return statement.param("status",args[0],Types.VARCHAR)
                .param("supplier",args[1],Types.OTHER)
                .param("fromDate",args[2] == null ? null :
                        ((Instant)args[2]).atOffset(java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("toDate",args[3] == null ? null :
                        ((Instant)args[3]).atOffset(java.time.ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("number",args[4],Types.VARCHAR);
    }
}
