package kg.chairx.sale;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SaleHistoryApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID draftSale() {
        UUID sale = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sales(id,sale_number,fulfillment_type,status,
                    idempotency_key,request_fingerprint,created_by,created_at)
                VALUES (?,?,'SELF_PICKUP','DRAFT',?,'test-fingerprint','sales-history-test',now())
                """, sale, "CHX-TEST-" + sale.toString().substring(0,8), UUID.randomUUID());
        return sale;
    }

    private void event(UUID saleId, String type, String action, String actor, String secret) {
        jdbc.update("""
                INSERT INTO audit_entries(id,entity_type,entity_id,action,actor,
                    occurred_at,before_state,after_state)
                VALUES (?,?,?, ?,?, clock_timestamp(),CAST(? AS jsonb),CAST(? AS jsonb))
                """, UUID.randomUUID(), type, saleId, action, actor,
                "{\"secret\":\""+secret+"\"}", "{\"secret\":\""+secret+"\"}");
    }

    @Test
    @WithMockUser(authorities = {"SALES_READ"})
    void historyIsPaginatedOrderedAndNeverLeaksAuditJsonOrOtherEntities() throws Exception {
        UUID sale = draftSale();
        event(sale, "SALE", "DRAFT_CREATED", "manager-1", "private-customer-data");
        event(sale, "SALE", "DRAFT_UPDATED", "manager-2", "private-costs");
        event(sale, "PAYMENT", "PAID", "finance-only", "private-payment");
        event(UUID.randomUUID(), "SALE", "CONFIRMED", "another-sale", "private-another-sale");

        var response = mvc.perform(get("/api/sales/" + sale + "/history?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].action").value("DRAFT_CREATED"))
                .andExpect(jsonPath("$.items[0].actor").value("manager-1"))
                .andExpect(jsonPath("$.items[0].occurredAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("beforeState","afterState","private-customer-data",
                "private-costs","private-payment","finance-only");

        mvc.perform(get("/api/sales/" + sale + "/history?page=1&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("DRAFT_UPDATED"))
                .andExpect(jsonPath("$.items[0].actor").value("manager-2"));
    }

    @Test
    @WithMockUser(authorities = {"SALES_READ"})
    void missingSaleIs404AndInvalidPageIs400() throws Exception {
        mvc.perform(get("/api/sales/" + UUID.randomUUID() + "/history"))
                .andExpect(status().isNotFound());
        UUID sale = draftSale();
        mvc.perform(get("/api/sales/" + sale + "/history?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CUSTOMERS_READ"})
    void nonSalesUserCannotReadHistory() throws Exception {
        UUID sale = draftSale();
        mvc.perform(get("/api/sales/" + sale + "/history"))
                .andExpect(status().isForbidden());
    }
}
