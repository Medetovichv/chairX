package kg.chairx.finance.application;

import kg.chairx.finance.application.DailyClosingAccessPolicy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Manager-only overview. Never reused by the personal closing endpoint. */
@Service
public class EmployeeClosingManagerReportService {
    private final JdbcClient jdbc;
    private final DailyClosingAccessPolicy policy;

    public EmployeeClosingManagerReportService(JdbcClient jdbc,java.time.Clock clock) {
        this.jdbc=jdbc;
        this.policy=new DailyClosingAccessPolicy(clock);
    }

    public record EmployeeClose(UUID employeeId,String username,String displayName,
        boolean closed,BigDecimal expectedCash,BigDecimal expectedBank,
        BigDecimal actualCash,BigDecimal actualBank,BigDecimal actualTotal,String totalNote) {}
    public record ExpenseLine(UUID id,String createdBy,String category,
        String paymentMethod,BigDecimal amount,String comment) {}
    public record ManagerReport(LocalDate businessDate,List<EmployeeClose> employees,
        List<ExpenseLine> expenses,BigDecimal expenseTotal,BigDecimal cashExpenses,
        BigDecimal bankExpenses,BigDecimal totalExpectedCash,BigDecimal totalExpectedBank,
        BigDecimal totalActualCash,BigDecimal totalActualBank,
        int closedEmployees,int totalEmployees,BigDecimal expectedTotal,BigDecimal actualTotal,
        boolean operationalClosed,String operationalCloser) {}

    @Transactional(readOnly=true)
    public ManagerReport report(LocalDate date){
        if(date==null || date.isAfter(policy.today()))
            throw new FinanceValidationException("Дата отчёта недоступна");
        var employees=jdbc.sql("""
            SELECT u.id,u.username,u.display_name,c.id AS closing_id,
                   c.expected_cash,c.expected_bank,c.actual_cash,c.actual_bank,
                   c.actual_total,c.total_note
            FROM app_users u
            LEFT JOIN employee_daily_closings c
              ON c.employee_id=u.id AND c.business_date=:date
            WHERE u.active=TRUE
              AND EXISTS (
                SELECT 1 FROM security_user_roles ur
                JOIN security_roles er ON er.id=ur.role_id
                WHERE ur.user_id=u.id AND er.system_role=TRUE AND er.code='EMPLOYEE'
              )
              AND NOT EXISTS (
                SELECT 1 FROM security_user_roles ur
                JOIN security_roles mr ON mr.id=ur.role_id
                WHERE ur.user_id=u.id AND mr.system_role=TRUE
                  AND mr.code IN ('ADMIN','MANAGER')
              )
            ORDER BY u.display_name,u.username
            """).param("date",date).query((rs,i)->new EmployeeClose(
                rs.getObject("id",UUID.class),rs.getString("username"),
                rs.getString("display_name"),rs.getObject("closing_id")!=null,
                rs.getBigDecimal("expected_cash"),rs.getBigDecimal("expected_bank"),
                rs.getBigDecimal("actual_cash"),rs.getBigDecimal("actual_bank"),
                rs.getBigDecimal("actual_total"),rs.getString("total_note"))).list();
        var expenses=jdbc.sql("""
            SELECT id,created_by,category,payment_method,amount,comment
            FROM expenses WHERE expense_date=:date ORDER BY created_by,created_at,id
            """).param("date",date).query((rs,i)->new ExpenseLine(
                rs.getObject("id",UUID.class),rs.getString("created_by"),
                rs.getString("category"),rs.getString("payment_method"),
                rs.getBigDecimal("amount"),rs.getString("comment"))).list();
        BigDecimal total=expenses.stream().map(ExpenseLine::amount)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cash=expenses.stream().filter(x->"CASH".equals(x.paymentMethod()))
            .map(ExpenseLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal bank=expenses.stream().filter(x->"BANK".equals(x.paymentMethod()))
            .map(ExpenseLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal expectedCash=employees.stream().map(EmployeeClose::expectedCash)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal expectedBank=employees.stream().map(EmployeeClose::expectedBank)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal actualCash=employees.stream().map(EmployeeClose::actualCash)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal actualBank=employees.stream().map(EmployeeClose::actualBank)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal combinedActual=employees.stream().filter(EmployeeClose::closed)
            .map(e->e.actualTotal()!=null?e.actualTotal():
                e.actualCash().add(e.actualBank()))
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        String closer=jdbc.sql("""
            SELECT u.display_name FROM employee_daily_closings closing
            JOIN app_users u ON u.id=closing.employee_id
            WHERE closing.business_date=:day AND closing.company_wide=TRUE
            """).param("day",date).query(String.class).optional().orElse(null);
        return new ManagerReport(date,employees,expenses,total,cash,bank,
            expectedCash,expectedBank,actualCash,actualBank,
            (int)employees.stream().filter(EmployeeClose::closed).count(),employees.size(),
            expectedCash.add(expectedBank),combinedActual,
            closer!=null,closer);
    }
}
