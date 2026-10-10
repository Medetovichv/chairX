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

    // PostgreSQL's unique partial index serializes concurrent requests with
    // the same key. The financial posting happens only when insertion wins.
    public boolean tryInsert(Expense expense, UUID key, String fingerprint) {
        return jdbc.update("""
                INSERT INTO expenses (
                    id, category, amount, payment_method, expense_date,
                    comment, created_by, created_at, idempotency_key,
                    request_fingerprint
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                expense.id(), expense.category().name(), expense.amount(),
                expense.paymentMethod().name(), Date.valueOf(expense.expenseDate()),
                expense.comment(), expense.createdBy(), Timestamp.from(expense.createdAt()),
                key, fingerprint
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