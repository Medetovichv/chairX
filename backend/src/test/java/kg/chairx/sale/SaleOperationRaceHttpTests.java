package kg.chairx.sale;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A real HTTP-level race, not two mocked React panels. PostgreSQL row locks
 * must allow only one actor's distinct operation ID to commit a cancellation.
 */
@SpringBootTest(properties = {
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class SaleOperationRaceHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID sale;

    @AfterEach
    void cleanup() {
        if (sale == null) return;
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("delete from sale_operation_receipts where sale_id=?",sale);
        jdbc.update("delete from audit_entries where entity_type='SALE' and entity_id=?",sale);
        jdbc.update("delete from sales where id=?",sale);
    }

    @Test
    void differentEmployeesCannotBothClaimTheSameSaleCancellation() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        sale=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales(id,sale_number,fulfillment_type,status,
                idempotency_key,request_fingerprint,created_by,created_at)
            VALUES (?,?,'SELF_PICKUP','DRAFT',?,'race-test','qa97-test',now())
            """,sale,"QA97-"+sale.toString().substring(0,8),UUID.randomUUID());
        UUID firstOperation=UUID.randomUUID(), secondOperation=UUID.randomUUID();
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> postCancel("operator-one",firstOperation,ready,start));
            var second=pool.submit(() -> postCancel("operator-two",secondOperation,ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus=first.get(30,TimeUnit.SECONDS);
            int secondStatus=second.get(30,TimeUnit.SECONDS);
            assertThat(firstStatus+secondStatus).isEqualTo(200+409);
            assertThat(firstStatus).isIn(200,409);
            assertThat(secondStatus).isIn(200,409);
            UUID winner=firstStatus==200?firstOperation:secondOperation;
            UUID loser=firstStatus==409?firstOperation:secondOperation;
            String winnerActor=firstStatus==200?"operator-one":"operator-two";
            String loserActor=firstStatus==409?"operator-one":"operator-two";
            assertThat(jdbc.queryForObject("select status from sales where id=?",String.class,sale))
                .isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("""
                select count(*) from audit_entries
                where entity_type='SALE' and entity_id=? and action='CANCELLED'
                """,Long.class,sale)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                select count(*) from sale_operation_receipts where sale_id=?
                and response_json is not null
                """,Long.class,sale)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                select actor from sale_operation_receipts where operation_id=?
                """,String.class,winner)).isEqualTo(winnerActor);
            assertThat(mvc.perform(get("/api/sales/"+sale+"/operations/"+winner)
                .with(user(winnerActor).authorities(new SimpleGrantedAuthority("SALES_READ"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(mvc.perform(get("/api/sales/"+sale+"/operations/"+loser)
                .with(user(loserActor).authorities(new SimpleGrantedAuthority("SALES_READ"))))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
            assertThat(mvc.perform(get("/api/sales/"+sale+"/operations/"+winner)
                .with(user(loserActor).authorities(new SimpleGrantedAuthority("SALES_READ"))))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        }
    }

    private int postCancel(String actor,UUID operation,CountDownLatch ready,CountDownLatch start)
            throws Exception {
        ready.countDown();
        if(!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("Workers not ready");
        return mvc.perform(post("/api/sales/"+sale+"/cancel-with-reason").with(csrf())
            .with(user(actor).authorities(new SimpleGrantedAuthority("SALES_CANCEL")))
            .header("X-Sale-Operation-Id",operation)
            .contentType("application/json")
            .content("{\"reason\":\"CUSTOMER_CHANGED_MIND\"}"))
            .andReturn().getResponse().getStatus();
    }
}
