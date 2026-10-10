package kg.chairx.finance.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Cash flow is based exclusively on committed journal entries and posting
 * timestamps, not document business dates (e.g. expenses.expense_date).
 */
@Repository
public class CashFlowRepository {
    private final JdbcTemplate jdbc;

    public CashFlowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal payments(Instant from, Instant to) {
        return sum("SALE_PAYMENT", "PAYMENT", from, to, false);
    }

    public BigDecimal paymentCorrections(Instant from, Instant to) {
        return sum("PAYMENT_REVERSAL", "PAYMENT_REVERSAL", from, to, true);
    }

    public BigDecimal refunds(Instant from, Instant to) {
        return sum("CUSTOMER_REFUND", "REFUND", from, to, true);
    }

    public BigDecimal exchangePayments(Instant from, Instant to) {
        return sum("SALE_PAYMENT", "EXCHANGE_SETTLEMENT", from, to, false);
    }

    public BigDecimal exchangeRefunds(Instant from, Instant to) {
        return sum("CUSTOMER_REFUND", "EXCHANGE_SETTLEMENT", from, to, true);
    }

    public BigDecimal purchasePayments(Instant from, Instant to) {
        return sum("PURCHASE_PAYMENT", "PURCHASE_PAYMENT", from, to, true);
    }

    public BigDecimal operatingExpenses(Instant from, Instant to) {
        return sum("EXPENSE", "EXPENSE", from, to, true);
    }

    private BigDecimal sum(
            String movementType, String sourceType,
            Instant from, Instant to, boolean outflow
    ) {
        // Movement amounts are signed: refunds, expenses and payment
        // reversals are negative, even if their business documents are not.
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM finance_movements
                WHERE movement_type = ? AND source_type = ?
                  AND created_at >= ? AND created_at < ?
                """, BigDecimal.class, movementType, sourceType,
                Timestamp.from(from), Timestamp.from(to));
        BigDecimal signed = value == null ? BigDecimal.ZERO : value;
        return outflow ? signed.negate() : signed;
    }
}
