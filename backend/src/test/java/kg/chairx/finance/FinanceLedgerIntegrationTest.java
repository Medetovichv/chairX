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
import static org.assertj.core.api.Assertions.assertThat;
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
class FinanceLedgerIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;

 @Test void listingIsDateBoundPaginatedAndRequiresFinancePermission() throws Exception {
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
  var source=UUID.randomUUID();
  var id=UUID.randomUUID();
  jdbc.update("""
     INSERT INTO finance_movements(id,account_code,amount,movement_type,source_type,
                                   source_id,created_by,business_date,business_date_source)
     VALUES (?,'CASH',1234,'SALE_PAYMENT','PAYMENT',?,'ledger-test','2026-10-19','POSTING_DATE')
     """,id,source);
  // Existing movement insert receives a database timestamp in this transaction.
  String day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek")).toString();
  String tomorrow=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Bishkek")).plusDays(1).toString();
  try {
    mvc.perform(get("/api/finance/ledger").param("from",day).param("to",tomorrow)
        .with(user("reader").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.items").isArray());
    mvc.perform(get("/api/finance/ledger").param("from",day).param("to",tomorrow)
        .with(user("warehouse").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/finance/ledger/export").param("from",day).param("to",tomorrow)
        .with(user("reader").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment;")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("1234")));
    mvc.perform(get("/api/finance/ledger/export").param("from",day).param("to",tomorrow)
        .with(user("warehouse").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/finance/ledger").param("from",day).param("to",tomorrow)
        .param("size","101")
        .with(user("reader").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"))))
        .andExpect(status().is4xxClientError());
  } finally {jdbc.update("DELETE FROM finance_movements WHERE id=?",id);}
 }
}
