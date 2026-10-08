package kg.chairx.finance.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class CashFlowRepository {

    private final JdbcTemplate jdbc;

    public CashFlowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal operatingExpenses(LocalDate from, LocalDate to) {
        BigDecimal result = jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM expenses
            WHERE expense_date >= ?
              AND expense_date < ?
            """,
                BigDecimal.class,
                java.sql.Date.valueOf(from),
                java.sql.Date.valueOf(to)
        );

        return result == null ? BigDecimal.ZERO : result;
    }

    public BigDecimal payments(Instant from, Instant to) {
        return sum("""
                SELECT COALESCE(SUM(amount), 0)
                FROM payments
                WHERE status = 'PAID'
                  AND paid_at >= ?
                  AND paid_at < ?
                """, from, to);
    }

    public BigDecimal refunds(Instant from, Instant to) {
        return sum("""
                SELECT COALESCE(SUM(amount), 0)
                FROM refunds
                WHERE refunded_at >= ?
                  AND refunded_at < ?
                """, from, to);
    }

    public BigDecimal exchangePayments(
            Instant from,
            Instant to
    ) {
        return exchangeSettlements("IN", from, to);
    }

    public BigDecimal exchangeRefunds(
            Instant from,
            Instant to
    ) {
        return exchangeSettlements("OUT", from, to);
    }

    private BigDecimal exchangeSettlements(
            String direction,
            Instant from,
            Instant to
    ) {
        BigDecimal result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM exchange_settlements
                WHERE direction = ?
                  AND created_at >= ?
                  AND created_at < ?
                """,
                BigDecimal.class,
                direction,
                Timestamp.from(from),
                Timestamp.from(to)
        );

        return result == null ? BigDecimal.ZERO : result;
    }

    private BigDecimal sum(
            String sql,
            Instant from,
            Instant to
    ) {
        BigDecimal result = jdbc.queryForObject(
                sql,
                BigDecimal.class,
                Timestamp.from(from),
                Timestamp.from(to)
        );

        return result == null ? BigDecimal.ZERO : result;
    }
}