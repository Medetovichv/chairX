package kg.chairx.sale;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SaleCommentsApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID sale() {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales(id,sale_number,fulfillment_type,status,
                idempotency_key,request_fingerprint,created_by,created_at)
            VALUES (?,?,'SELF_PICKUP','DRAFT',?,'test-comment','sale-comment-test',now())
            """,id,"CHX-CMT-"+id.toString().substring(0,8),UUID.randomUUID());
        return id;
    }
    private String request(UUID key,String body) {
        return "{\"idempotencyKey\":\""+key+"\",\"text\":\""+body+"\"}";
    }

    @Test
    @WithMockUser(username="employee1",authorities={"SALES_READ","SALES_COMMENT_CREATE"})
    void commentsAreImmutablePaginatedAndExactlyOncePerIdempotencyKey() throws Exception {
        UUID sale=sale(), key=UUID.randomUUID();
        var first=mvc.perform(post("/api/sales/"+sale+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request(key,"Клиент ждёт звонка")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value("employee1"))
            .andExpect(jsonPath("$.text").value("Клиент ждёт звонка"))
            .andReturn().getResponse().getContentAsString();
        var second=mvc.perform(post("/api/sales/"+sale+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request(key,"Клиент ждёт звонка")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sale_comments WHERE sale_id=?",
            Long.class,sale)).isEqualTo(1L);
        mvc.perform(get("/api/sales/"+sale+"/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].author").value("employee1"));
        Long auditCount=jdbc.queryForObject(
            "SELECT count(*) FROM audit_entries WHERE entity_type='SALE_COMMENT' AND entity_id=(SELECT id FROM sale_comments WHERE sale_id=?)",
            Long.class,sale);
        assertThat(auditCount).isEqualTo(1L);
        String auditPayload=jdbc.queryForObject(
            "SELECT after_state::text FROM audit_entries WHERE entity_type='SALE_COMMENT' AND entity_id=(SELECT id FROM sale_comments WHERE sale_id=?)",
            String.class,sale);
        assertThat(auditPayload).doesNotContain("Клиент ждёт звонка");
    }

    @Test
    @WithMockUser(username="employee1",authorities={"SALES_READ","SALES_COMMENT_CREATE"})
    void changedPayloadCannotReuseSameCommentKey() throws Exception {
        UUID id=sale(),key=UUID.randomUUID();
        mvc.perform(post("/api/sales/"+id+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request(key,"Первый")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/sales/"+id+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request(key,"Второй")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/sales/"+id+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID()," ")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities={"SALES_READ"})
    void readOnlyStaffCannotCreateComment() throws Exception {
        UUID id=sale();
        mvc.perform(get("/api/sales/"+id+"/comments")).andExpect(status().isOk());
        mvc.perform(post("/api/sales/"+id+"/comments").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(request(UUID.randomUUID(),"Нельзя")))
            .andExpect(status().isForbidden());
    }
}
