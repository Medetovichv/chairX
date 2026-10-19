package kg.chairx.expense;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.expense.application.ExpenseAccrualService;
import kg.chairx.expense.domain.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ExpenseAccrualIntegrationTest {
    @Autowired ExpenseAccrualService service;
    @Autowired JdbcTemplate jdbc;

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
            jdbc.update("DELETE FROM expense_accruals WHERE idempotency_key=?",key);
        }
    }
}
