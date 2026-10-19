package kg.chairx.expense.application;

import kg.chairx.expense.domain.ExpenseCategory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseAccrualService {
    private final JdbcClient jdbc;
    public ExpenseAccrualService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record Create(UUID idempotencyKey, ExpenseCategory category, String description,
                         BigDecimal amount, LocalDate periodStart, LocalDate periodEnd) {}
    public record Accrual(UUID id, UUID idempotencyKey, ExpenseCategory category, String description,
                          BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, String createdBy) {}
    public record ReportLine(Accrual accrual, BigDecimal recognized, BigDecimal paid,
                             BigDecimal outstanding, BigDecimal prepaid) {}

    private static void validate(Create input) {
        if (input == null || input.idempotencyKey() == null || input.category() == null
                || input.description() == null || input.description().isBlank()
                || input.description().length() > 1000) {
            throw new IllegalArgumentException("Missing accrual fields");
        }
        ExpensePeriodAllocation.allocate(input.amount(), input.periodStart(), input.periodEnd());
    }

    @Transactional
    public Accrual create(Create input, String actor) {
        validate(input);
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Actor required");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
             INSERT INTO expense_accruals
               (id,idempotency_key,category,description,amount,period_start,period_end,created_by)
             VALUES (:id,:key,:category,:description,:amount,:start,:end,:actor)
             ON CONFLICT (idempotency_key) DO NOTHING
             """).param("id",id).param("key",input.idempotencyKey())
             .param("category",input.category().name()).param("description",input.description().trim())
             .param("amount",input.amount()).param("start",input.periodStart())
             .param("end",input.periodEnd()).param("actor",actor).update();
        Accrual existing=jdbc.sql("""
                SELECT id,idempotency_key,category,description,amount,period_start,period_end,created_by
                FROM expense_accruals WHERE idempotency_key=:key
                """).param("key",input.idempotencyKey()).query(this::map).single();
        if (existing.category()!=input.category() || !existing.description().equals(input.description().trim())
                || existing.amount().compareTo(input.amount())!=0
                || !existing.periodStart().equals(input.periodStart())
                || !existing.periodEnd().equals(input.periodEnd())) {
            throw new IllegalStateException("Idempotency key belongs to another accrual");
        }
        return existing;
    }

    /**
     * Associate an existing paid expense with an accrual. This is bookkeeping
     * only; the ExpenseService has already posted the actual cash movement.
     */
    @Transactional
    public void linkExistingPayment(UUID accrualId, UUID expenseId, String actor) {
        if (accrualId == null || expenseId == null || actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Payment link fields required");
        }
        String accrualCategory=jdbc.sql("SELECT category FROM expense_accruals WHERE id=:id FOR UPDATE")
                .param("id",accrualId).query(String.class).single();
        String expenseCategory=jdbc.sql("SELECT category FROM expenses WHERE id=:id")
                .param("id",expenseId).query(String.class).single();
        if (!accrualCategory.equals(expenseCategory)) {
            throw new IllegalArgumentException("Payment category must match accrual category");
        }
        jdbc.sql("""
             INSERT INTO expense_accrual_payments(accrual_id,expense_id,linked_by)
             VALUES (:accrual,:expense,:actor) ON CONFLICT (expense_id) DO NOTHING
             """).param("accrual",accrualId).param("expense",expenseId)
             .param("actor",actor).update();
        UUID linked=jdbc.sql("SELECT accrual_id FROM expense_accrual_payments WHERE expense_id=:id")
                .param("id",expenseId).query(UUID.class).single();
        if (!accrualId.equals(linked)) {
            throw new IllegalStateException("Expense is already linked to another accrual");
        }
    }

    @Transactional(readOnly=true)
    public List<ReportLine> report(LocalDate from, LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)
                || java.time.temporal.ChronoUnit.DAYS.between(from,to) > 366) {
            throw new IllegalArgumentException("Invalid reporting period");
        }
        return jdbc.sql("""
            SELECT id,idempotency_key,category,description,amount,period_start,period_end,created_by
            FROM expense_accruals
            WHERE period_start < :to AND period_end > :from
            ORDER BY period_start,id
            """).param("from",from).param("to",to).query(this::map).list().stream().map(a->{
                BigDecimal accrued=ExpensePeriodAllocation.accruedWithin(
                     a.amount(),a.periodStart(),a.periodEnd(),from,to);
                BigDecimal paid=jdbc.sql("""
                    SELECT COALESCE(SUM(e.amount),0)
                    FROM expense_accrual_payments ap
                    JOIN expenses e ON e.id=ap.expense_id
                    WHERE ap.accrual_id=:id
                    """).param("id",a.id()).query(BigDecimal.class).single();
                return new ReportLine(a,accrued,paid,
                        ExpensePeriodAllocation.outstanding(accrued,paid),
                        ExpensePeriodAllocation.prepaid(accrued,paid));
            }).toList();
    }

    private Accrual map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new Accrual(rs.getObject("id",UUID.class),rs.getObject("idempotency_key",UUID.class),
                ExpenseCategory.valueOf(rs.getString("category")),rs.getString("description"),
                rs.getBigDecimal("amount"),rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),rs.getString("created_by"));
    }
}
