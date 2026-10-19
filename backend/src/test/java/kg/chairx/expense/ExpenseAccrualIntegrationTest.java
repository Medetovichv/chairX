package kg.chairx.expense;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.expense.application.ExpenseAccrualService;
import kg.chairx.expense.domain.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties={
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class ExpenseAccrualIntegrationTest {
    @Autowired ExpenseAccrualService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void accrualPersistsAndReportsWithoutFinancialPostings() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
            .isEqualTo("chairx_test");
        UUID key=UUID.randomUUID();
        LocalDate start=LocalDate.of(2026,10,30);
        var input=new ExpenseAccrualService.Create(key,ExpenseCategory.RENT,
                "F14 rent test "+key,new BigDecimal("100"),start,start.plusDays(3));
        int movements=jdbc.queryForObject("SELECT count(*) FROM finance_movements",Integer.class);
        try {
            var saved=service.create(input,"f14-integration");
            assertThat(service.create(input,"f14-integration").id()).isEqualTo(saved.id());
            var report=service.report(LocalDate.of(2026,11,1),LocalDate.of(2026,12,1));
            var row=report.stream().filter(r->r.accrual().id().equals(saved.id())).findFirst().orElseThrow();
            assertThat(row.recognized()).isEqualByComparingTo("34");
            assertThat(row.paid()).isEqualByComparingTo("0");
            assertThat(row.outstanding()).isEqualByComparingTo("34");
            assertThat(row.prepaid()).isEqualByComparingTo("0");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_movements",Integer.class))
                    .isEqualTo(movements);
            assertThatThrownBy(()->service.create(new ExpenseAccrualService.Create(key,
                    ExpenseCategory.RENT,"Changed description",new BigDecimal("100"),
                    start,start.plusDays(3)),"f14-integration"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='EXPENSE_ACCRUAL' AND entity_id IN (SELECT id FROM expense_accruals WHERE idempotency_key=?)",key);
            jdbc.update("DELETE FROM expense_accruals WHERE idempotency_key=?",key);
        }
    }
    @Test void linkingAlreadyPaidExpenseDoesNotCreateSecondMovement() {
        UUID key=UUID.randomUUID();
        UUID expenseId=UUID.randomUUID();
        LocalDate day=LocalDate.of(2026,11,4);
        int movements=jdbc.queryForObject("SELECT COUNT(*) FROM finance_movements",Integer.class);
        var accrual=service.create(new ExpenseAccrualService.Create(key,ExpenseCategory.RENT,
                "Test linked rent "+key,new BigDecimal("900"),day,day.plusDays(3)),"f14-integration");
        jdbc.update("""
            INSERT INTO expenses(id,category,amount,payment_method,expense_date,comment,created_by,created_at)
            VALUES (?,'RENT',300,'CASH',?,'Existing payment','f14-integration',clock_timestamp())
            """,expenseId,day);
        try {
            service.linkExistingPayment(accrual.id(),expenseId,"f14-integration");
            service.linkExistingPayment(accrual.id(),expenseId,"f14-integration");
            var report=service.report(day,day.plusDays(3)).stream()
                    .filter(r->r.accrual().id().equals(accrual.id())).findFirst().orElseThrow();
            assertThat(report.recognized()).isEqualByComparingTo("900");
            assertThat(report.paid()).isEqualByComparingTo("300");
            assertThat(report.outstanding()).isEqualByComparingTo("600");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_movements",Integer.class))
                    .isEqualTo(movements);
        } finally {
            jdbc.update("DELETE FROM expense_accrual_payments WHERE expense_id=?",expenseId);
            jdbc.update("DELETE FROM expenses WHERE id=?",expenseId);
            jdbc.update("DELETE FROM expense_accruals WHERE idempotency_key=?",key);
        }
    }

    @Test
    @WithMockUser(authorities={"EXPENSES_READ"})
    void readGrantDoesNotAllowCreationOrPaymentLink() throws Exception {
        mvc.perform(get("/api/expenses/accruals")
                .param("from","2026-10-01").param("to","2026-11-01"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/expenses/accruals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\""+UUID.randomUUID()+"\",\"category\":\"RENT\","+
                        "\"description\":\"Unauthorized\",\"amount\":\"100\","+
                        "\"periodStart\":\"2026-10-01\",\"periodEnd\":\"2026-11-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/expenses/accruals/"+UUID.randomUUID()+"/payments").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expenseId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isForbidden());
    }

}
