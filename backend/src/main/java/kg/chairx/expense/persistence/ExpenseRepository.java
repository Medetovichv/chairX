package kg.chairx.expense.persistence;

import kg.chairx.expense.domain.Expense;
import kg.chairx.expense.domain.ExpenseCategory;
import kg.chairx.expense.domain.ExpensePaymentMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExpenseRepository {

    private final JdbcTemplate jdbc;

    public ExpenseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Expense> MAPPER = (rs, rowNum) ->
            new Expense(
                    rs.getObject("id", UUID.class),
                    ExpenseCategory.valueOf(rs.getString("category")),
                    rs.getBigDecimal("amount"),
                    ExpensePaymentMethod.valueOf(rs.getString("payment_method")),
                    rs.getDate("expense_date").toLocalDate(),
                    rs.getString("comment"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_at").toInstant()
            );

    public void insert(Expense expense) {
        jdbc.update("""
                INSERT INTO expenses (
                    id, category, amount, payment_method,
                    expense_date, comment, created_by, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                expense.id(),
                expense.category().name(),
                expense.amount(),
                expense.paymentMethod().name(),
                Date.valueOf(expense.expenseDate()),
                expense.comment(),
                expense.createdBy(),
                Timestamp.from(expense.createdAt())
        );
    }

    /**
     * Find the next operational day not already confirmed company-wide.
     * The caller holds CompletionGuard so the closing cannot race this read.
     * This does not alter expense_date or the finance movement posting date.
     */
    private LocalDate operationalDate(LocalDate financialDate, String actor) {
        // Manager/admin expenses remain on their financial day. Only staff
        // expenses roll after the employee operational report is confirmed.
        Boolean manager = jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM app_users u
                JOIN security_user_roles ur ON ur.user_id=u.id
                JOIN security_roles r ON r.id=ur.role_id
                WHERE u.username=? AND r.system_role=TRUE
                  AND r.code IN ('MANAGER','ADMIN')
            )
            """,Boolean.class,actor);
        if (Boolean.TRUE.equals(manager)) return financialDate;
        LocalDate current = financialDate;
        for (int i = 0; i < 366; i++) {
            Boolean closed = jdbc.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM employee_daily_closings
                  WHERE company_wide = TRUE AND business_date = ?
                )
                """, Boolean.class, Date.valueOf(current));
            if (!Boolean.TRUE.equals(closed)) return current;
            current = current.plusDays(1);
        }
        throw new IllegalStateException("Слишком много закрытых операционных дней подряд");
    }

    // PostgreSQL's unique partial index serializes concurrent requests with
    // the same key. The financial posting happens only when insertion wins.
    public boolean tryInsert(Expense expense, UUID key, String fingerprint) {
        return jdbc.update("""
                INSERT INTO expenses (
                    id, category, amount, payment_method, expense_date,
                    comment, created_by, created_at, idempotency_key,
                    request_fingerprint, operational_report_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                expense.id(), expense.category().name(), expense.amount(),
                expense.paymentMethod().name(), Date.valueOf(expense.expenseDate()),
                expense.comment(), expense.createdBy(), Timestamp.from(expense.createdAt()),
                key, fingerprint, Date.valueOf(operationalDate(expense.expenseDate(),expense.createdBy()))
        ) == 1;
    }

    public Optional<Expense> findByIdempotencyKey(UUID key) {
        return jdbc.query("""
                SELECT * FROM expenses WHERE idempotency_key = ?
                """, MAPPER, key).stream().findFirst();
    }

    public Optional<String> requestFingerprint(UUID expenseId) {
        return jdbc.query("""
                SELECT request_fingerprint FROM expenses WHERE id = ?
                """, (rs, row) -> rs.getString(1), expenseId).stream().findFirst();
    }

    public Optional<Expense> find(UUID id) {
        return jdbc.query("""
                SELECT *
                FROM expenses
                WHERE id = ?
                """, MAPPER, id).stream().findFirst();
    }

    public List<Expense> findByPeriod(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT *
                FROM expenses
                WHERE expense_date >= ?
                  AND expense_date < ?
                ORDER BY expense_date DESC, created_at DESC, id DESC
                """,
                MAPPER,
                Date.valueOf(from),
                Date.valueOf(to)
        );
    }

    public BigDecimal totalByPeriod(LocalDate from, LocalDate to) {
        BigDecimal result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM expenses
                WHERE expense_date >= ?
                  AND expense_date < ?
                """,
                BigDecimal.class,
                Date.valueOf(from),
                Date.valueOf(to)
        );

        return result == null ? BigDecimal.ZERO : result;
    }
}