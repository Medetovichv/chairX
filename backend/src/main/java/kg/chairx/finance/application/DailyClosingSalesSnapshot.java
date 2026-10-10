package kg.chairx.finance.application;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Operational sales snapshot for P21 closing. No finance postings are generated here.
 * Complete self-pickups are determined by sales.fulfilled_at, deliveries by delivered_at.
 */
public final class DailyClosingSalesSnapshot {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bishkek");
    private static final long COMPLETION_LOCK_ID = 772200921L;
    private DailyClosingSalesSnapshot() {}

    /** Serialize completed-sale writes and report snapshots before any domain row locks. */
    public static void acquireCompletionGate(JdbcClient jdbc) {
        jdbc.sql("SELECT count(*) FROM pg_advisory_xact_lock(" + COMPLETION_LOCK_ID + ")")
                .query(Long.class).single();
    }

    public record CompletedSale(UUID saleId, String saleNumber, String customerName,
                                String phone, String products, long quantity, String address,
                                String fulfillmentType, Instant completedAt, BigDecimal total,
                                String paymentStatus) {
    }

    public record Totals(Long orders, Long chairs, BigDecimal value) {}

    public record Result(LocalDate businessDate, boolean closed, boolean snapshotAvailable,
                         boolean preliminary, List<CompletedSale> items,
                         Totals totals, Long lateCompletionCount) {}

    /** Called inside DailyClosingService.closeInternal's existing transaction only. */
    public static void capture(JdbcClient jdbc, UUID closingId, LocalDate day) {
        jdbc.sql("""
                INSERT INTO finance_daily_closing_sales_snapshots(closing_id, captured_at)
                VALUES (:closing, clock_timestamp())
                """).param("closing", closingId).update();
        for (CompletedSale sale : live(jdbc, day)) {
            jdbc.sql("""
                    INSERT INTO finance_daily_closing_sales(
                        closing_id,sale_id,sale_number,customer_name,phone,
                        products,quantity,address,fulfillment_type,
                        completed_at,total,payment_status)
                    VALUES (:closing,:sale,:number,:name,:phone,:products,:quantity,
                            :address,:fulfillment,:completed,:total,:payment)
                    """)
                    .param("closing", closingId)
                    .param("sale", sale.saleId())
                    .param("number",sale.saleNumber())
                    .param("name",sale.customerName(),Types.VARCHAR)
                    .param("phone",sale.phone(),Types.VARCHAR)
                    .param("products",sale.products(),Types.VARCHAR)
                    .param("quantity",sale.quantity())
                    .param("address",sale.address(),Types.VARCHAR)
                    .param("fulfillment",sale.fulfillmentType())
                    .param("completed",sale.completedAt().atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("total",sale.total())
                    .param("payment",sale.paymentStatus())
                    .update();
        }
    }

    public static Result read(JdbcClient jdbc, LocalDate date) {
        if (date == null) throw new FinanceValidationException("Дата отчёта обязательна");
        UUID closingId = jdbc.sql("""
                SELECT id FROM finance_daily_closings WHERE business_date=:date
                """).param("date",date).query(UUID.class).optional().orElse(null);
        if (closingId == null) {
            var items = live(jdbc,date);
            return new Result(date,false,false,true,items,totals(items),null);
        }

        Boolean available = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM finance_daily_closing_sales_snapshots
                              WHERE closing_id=:id)
                """).param("id",closingId).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(available)) {
            // Never reconstruct a historical report from later mutable data.
            return new Result(date,true,false,false,List.of(),
                    new Totals(null,null,null),null);
        }
        List<CompletedSale> saved = jdbc.sql("""
                SELECT sale_id,sale_number,customer_name,phone,products,quantity,
                       address,fulfillment_type,completed_at,total,payment_status
                FROM finance_daily_closing_sales
                WHERE closing_id=:id
                ORDER BY completed_at, sale_id
                """).param("id",closingId).query((rs,row) -> new CompletedSale(
                rs.getObject("sale_id",UUID.class),rs.getString("sale_number"),
                rs.getString("customer_name"),rs.getString("phone"),
                rs.getString("products"),rs.getLong("quantity"),
                rs.getString("address"),rs.getString("fulfillment_type"),
                rs.getTimestamp("completed_at").toInstant(),rs.getBigDecimal("total"),
                rs.getString("payment_status"))).list();
        Set<UUID> ids = new HashSet<>();
        saved.forEach(row -> ids.add(row.saleId()));
        long late = live(jdbc,date).stream().filter(row -> !ids.contains(row.saleId())).count();
        return new Result(date,true,true,false,saved,totals(saved),late);
    }

    private static Totals totals(List<CompletedSale> rows) {
        return new Totals((long)rows.size(),
                rows.stream().mapToLong(CompletedSale::quantity).sum(),
                rows.stream().map(CompletedSale::total)
                        .reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    public static List<CompletedSale> live(JdbcClient jdbc, LocalDate day) {
        Instant from = day.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant until = day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return jdbc.sql("""
                SELECT s.id AS sale_id,s.sale_number,c.full_name AS customer_name,
                       COALESCE(c.phone,c.whatsapp_phone,c.secondary_phone) AS phone,
                       detail.products,detail.quantity,dl.address,
                       s.fulfillment_type,
                       CASE WHEN s.fulfillment_type='SELF_PICKUP'
                            THEN s.fulfilled_at ELSE dl.delivered_at END AS completed_at,
                       detail.total,
                       CASE WHEN pay.amount IS NULL THEN 'UNPAID'
                            WHEN pay.amount < detail.total THEN 'PARTIAL'
                            ELSE 'PAID' END AS payment_status
                FROM sales s
                LEFT JOIN customers c ON c.id=s.customer_id
                LEFT JOIN deliveries dl ON dl.sale_id=s.id
                LEFT JOIN payments pay ON pay.sale_id=s.id AND pay.status='PAID'
                LEFT JOIN LATERAL (
                    SELECT COALESCE(SUM(si.quantity),0) AS quantity,
                           COALESCE(SUM(si.quantity*si.unit_sale_price),0) AS total,
                           string_agg(p.name||' / '||v.name||' ×'||si.quantity::text,
                                      ', ' ORDER BY p.name,v.name) AS products
                    FROM sale_items si
                    JOIN product_variants v ON v.id=si.product_variant_id
                    JOIN products p ON p.id=v.product_id
                    WHERE si.sale_id=s.id
                ) detail ON TRUE
                WHERE
                    (s.fulfillment_type='SELF_PICKUP'
                       AND s.status='FULFILLED'
                       AND s.fulfilled_at >= :begin AND s.fulfilled_at < :until)
                    OR
                    (s.fulfillment_type IN ('CITY_DELIVERY','REGION_DELIVERY')
                       AND dl.status='DELIVERED'
                       AND dl.delivered_at >= :begin AND dl.delivered_at < :until)
                ORDER BY completed_at,s.id
                """)
                .param("begin",from.atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .param("until",until.atOffset(ZoneOffset.UTC),Types.TIMESTAMP_WITH_TIMEZONE)
                .query((rs,row)->new CompletedSale(
                        rs.getObject("sale_id",UUID.class),rs.getString("sale_number"),
                        rs.getString("customer_name"),rs.getString("phone"),
                        rs.getString("products"),rs.getLong("quantity"),rs.getString("address"),
                        rs.getString("fulfillment_type"),
                        rs.getTimestamp("completed_at").toInstant(),rs.getBigDecimal("total"),
                        rs.getString("payment_status"))).list();
    }
}
