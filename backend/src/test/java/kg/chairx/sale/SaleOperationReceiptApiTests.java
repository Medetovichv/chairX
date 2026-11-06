package kg.chairx.sale;

import kg.chairx.PostgresTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL and authenticated HTTP coverage for request-specific sale outcomes. */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SaleOperationReceiptApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID draftSale() {
        UUID sale = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sales(id,sale_number,fulfillment_type,status,
                    idempotency_key,request_fingerprint,created_by,created_at)
                VALUES (?,?,'SELF_PICKUP','DRAFT',?,'receipt-fixture','receipt-test',now())
                """, sale, "CHX-OP-" + sale.toString().substring(0,8), UUID.randomUUID());
        return sale;
    }

    @Test
    @WithMockUser(username = "worker-1", authorities = {"SALES_CANCEL","SALES_READ"})
    void lostResponseReplayIsBoundToSameOperationAndNotJustSaleStatus() throws Exception {
        UUID sale = draftSale();
        UUID operation = UUID.randomUUID();
        String url = "/api/sales/" + sale + "/cancel-with-reason";
        String body = "{\"reason\":\"CUSTOMER_CHANGED_MIND\",\"comment\":\"customer request\"}";
        mvc.perform(post(url).with(csrf()).header("X-Sale-Operation-Id", operation)
                .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        long count = jdbc.queryForObject("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='SALE' AND entity_id=? AND action='CANCELLED'
                """, Long.class, sale);
        mvc.perform(post(url).with(csrf()).header("X-Sale-Operation-Id", operation)
                .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_entries
                WHERE entity_type='SALE' AND entity_id=? AND action='CANCELLED'
                """, Long.class, sale)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sale_operation_receipts WHERE sale_id=?",
                Long.class, sale)).isEqualTo(1L);
        mvc.perform(get("/api/sales/" + sale + "/operations/" + operation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operation.toString()))
                .andExpect(jsonPath("$.action").value("cancel"))
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                .andExpect(jsonPath("$.sale.status").value("CANCELLED"));
        mvc.perform(post(url).with(csrf()).header("X-Sale-Operation-Id", operation)
                .contentType("application/json")
                .content("{\"reason\":\"DUPLICATE_ORDER\"}"))
                .andExpect(status().isConflict());
    }


    @Test
    @WithMockUser(username = "worker-1", authorities = {"SALES_DRAFT_MANAGE","SALES_READ"})
    void matchingStatusFromAnotherMutationMustNotCreateFalseReceipt() throws Exception {
        UUID sale = draftSale();
        UUID operation = UUID.randomUUID();
        // Simulate a different operator confirming before the request starts.
        jdbc.update("UPDATE sales SET status='CONFIRMED' WHERE id=?", sale);
        mvc.perform(post("/api/sales/" + sale + "/confirm").with(csrf())
                .header("X-Sale-Operation-Id", operation))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sale_operation_receipts WHERE operation_id=?",
                Long.class, operation)).isZero();
        mvc.perform(get("/api/sales/" + sale + "/operations/" + operation))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "worker-1", authorities = {"SALES_CANCEL","SALES_READ"})
    void operationResultsArePrivateToOriginalActor() throws Exception {
        UUID sale = draftSale();
        UUID operation = UUID.randomUUID();
        mvc.perform(post("/api/sales/" + sale + "/cancel-with-reason").with(csrf())
                .header("X-Sale-Operation-Id", operation)
                .contentType("application/json")
                .content("{\"reason\":\"CUSTOMER_CHANGED_MIND\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sales/" + sale + "/operations/" + operation)
                .with(user("other-worker").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SALES_READ"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/sales/" + sale + "/operations/" + operation)
                .with(user("catalog-only").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PRODUCTS_READ"))))
                .andExpect(status().isForbidden());
    }
}
