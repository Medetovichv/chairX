package kg.chairx.sale.application;

import kg.chairx.sale.api.SaleStatistics;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/** Read-only server aggregate. Does not fetch customer data or make postings. */
@Service
@Transactional(readOnly = true)
public class SaleStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;

    public SaleStatisticsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SaleStatistics last30Days() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return jdbc.sql("""
                WITH per_sale AS (
                    SELECT s.id, s.status,
                           coalesce(sum(i.quantity * i.unit_sale_price), 0) AS total
                    FROM sales s
                    LEFT JOIN sale_items i ON i.sale_id = s.id
                    WHERE s.created_at >= :since
                      AND s.created_at < :until
                    GROUP BY s.id, s.status
                )
                SELECT count(*) AS order_count,
                       count(*) FILTER (WHERE status = 'FULFILLED') AS fulfilled_count,
                       count(*) FILTER (WHERE status IN ('DRAFT','CONFIRMED')) AS work_count,
                       coalesce(round(avg(total) FILTER (
                           WHERE status IN ('CONFIRMED','FULFILLED')
                       ), 0), 0) AS average_check
                FROM per_sale
                """)
                .param("since", today.minusDays(29).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())
                .param("until", today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime())
                .query((rs, index) -> new SaleStatistics(
                        rs.getLong("order_count"),
                        rs.getLong("fulfilled_count"),
                        rs.getLong("work_count"),
                        rs.getBigDecimal("average_check")))
                .single();
    }
}
