package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "CHAIRX_CATALOG_PASSWORD=integration-test-password",
 "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class FinanceSourceDocumentIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;

 @Test void expenseSourceDocumentIsRealAndPermissionProtected() throws Exception {
  UUID id=UUID.randomUUID();
  jdbc.update("""
      INSERT INTO expenses(id,category,amount,payment_method,expense_date,created_by)
      VALUES (?,'ADVERTISING',250,'CASH','2026-10-20','source-test')
      """,id);
  String url="/api/finance/source-documents/EXPENSE/"+id;
  mvc.perform(get(url).with(user("reader").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"),
        new org.springframework.security.core.authority.SimpleGrantedAuthority("EXPENSES_READ"))))
     .andExpect(status().isOk())
     .andExpect(jsonPath("$.id").value(id.toString()))
     .andExpect(jsonPath("$.fields.Категория").value("ADVERTISING"))
     .andExpect(jsonPath("$.fields.\\u0421\\u0443\\u043c\\u043c\\u0430, KGS").exists());
  mvc.perform(get(url).with(user("finance").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
     .andExpect(status().isForbidden());
  mvc.perform(get(url).with(user("expense").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority("EXPENSES_READ"))))
     .andExpect(status().isForbidden());
  mvc.perform(get("/api/finance/source-documents/UNKNOWN/"+id).with(user("finance").authorities(
        new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
     .andExpect(status().isNotFound());
 }
}
