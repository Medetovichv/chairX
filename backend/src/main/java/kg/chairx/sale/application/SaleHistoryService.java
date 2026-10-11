package kg.chairx.sale.application;

import java.time.Instant;
import java.util.UUID;
import kg.chairx.sale.api.SaleHistoryEvent;
import kg.chairx.sale.api.SaleHistoryPage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads existing transactional audit events without exposing before/after JSON,
 * customer information, purchase costs, or other entity audit streams.
 */
@Service
@Transactional(readOnly = true)
public class SaleHistoryService {
    private final JdbcClient jdbc;
    private final SaleService sales;

    public SaleHistoryService(JdbcClient jdbc, SaleService sales) {
        this.jdbc = jdbc;
        this.sales = sales;
    }

    public SaleHistoryPage list(UUID saleId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new kg.chairx.common.web.InvalidQueryException("Некорректные параметры истории продажи");
        }
        // Preserve normal SALE_NOT_FOUND semantics, including non-existent IDs.
        sales.get(saleId);
        long count = jdbc.sql("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type = 'SALE' AND entity_id = :saleId
                  AND action IN ('DRAFT_CREATED','DRAFT_UPDATED','CONFIRMED','FULFILLED','CANCELLED')
                """).param("saleId", saleId).query(Long.class).single();
        var items = jdbc.sql("""
                SELECT action, actor, occurred_at FROM audit_entries
                WHERE entity_type = 'SALE' AND entity_id = :saleId
                  AND action IN ('DRAFT_CREATED','DRAFT_UPDATED','CONFIRMED','FULFILLED','CANCELLED')
                ORDER BY occurred_at, id
                LIMIT :size OFFSET :offset
                """)
                .param("saleId", saleId)
                .param("size", size)
                .param("offset", (long) page * size)
                .query((rs, row) -> new SaleHistoryEvent(
                        rs.getString("action"), rs.getString("actor"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
        return new SaleHistoryPage(items, page, size, count);
    }
}
