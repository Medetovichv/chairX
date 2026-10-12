package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.api.AdminBalanceAdjustmentRequest;
import kg.chairx.finance.application.FinanceAdminAdjustmentService;
import kg.chairx.finance.application.FinanceOpeningBalanceService;
import kg.chairx.finance.domain.FinanceAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class FinanceAdminAdjustmentIntegrationTest {
    @Autowired FinanceAdminAdjustmentService corrections;
    @Autowired FinanceOpeningBalanceService opening;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("chairx_test");
        jdbc.update("delete from finance_admin_adjustments");
        jdbc.update("delete from finance_movements");
        jdbc.update("update finance_accounts set balance=0,opening_balance_initialized=false");
    }

    @Test void writesOneMovementAndKeepsIdempotentReplayStable() {
        opening.initialize(FinanceAccount.CASH,new BigDecimal("10000"),"admin");
        UUID key=UUID.randomUUID();
        var command=new AdminBalanceAdjustmentRequest(FinanceAccount.CASH,new BigDecimal("12500"),key,"Инвентаризация кассы");
        var result=corrections.adjust(command,"admin");
        assertThat(result.previousBalance()).isEqualByComparingTo("10000");
        assertThat(result.newBalance()).isEqualByComparingTo("12500");
        assertThat(result.delta()).isEqualByComparingTo("2500");
        assertThat(corrections.adjust(command,"admin")).isEqualTo(result);
        assertThat(jdbc.queryForObject("select balance from finance_accounts where code='CASH'",BigDecimal.class))
                .isEqualByComparingTo("12500");
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_movements where source_type='ADMIN_BALANCE_ADJUSTMENT' and source_id=?",
                Integer.class,key)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from finance_admin_adjustments where id=?",Integer.class,key)).isEqualTo(1);
        assertThatThrownBy(()->corrections.adjust(
                new AdminBalanceAdjustmentRequest(FinanceAccount.CASH,new BigDecimal("13000"),key,
                        "Инвентаризация кассы"),"admin"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void requiresInitializedAccountAndNeverOverdraws() {
        UUID key=UUID.randomUUID();
        assertThatThrownBy(()->corrections.adjust(
                new AdminBalanceAdjustmentRequest(FinanceAccount.BANK,new BigDecimal("500"),key,
                        "Коррекция остатка"),"admin")).isInstanceOf(IllegalStateException.class);
        opening.initialize(FinanceAccount.BANK,new BigDecimal("1000"),"admin");
        assertThatThrownBy(()->corrections.adjust(
                new AdminBalanceAdjustmentRequest(FinanceAccount.BANK,new BigDecimal("-100"),UUID.randomUUID(),
                        "Коррекция остатка"),"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select balance from finance_accounts where code='BANK'",BigDecimal.class))
                .isEqualByComparingTo("1000");
    }
}
