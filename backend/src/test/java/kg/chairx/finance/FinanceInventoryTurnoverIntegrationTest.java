package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
class FinanceInventoryTurnoverIntegrationTest {
 @Autowired MockMvc mvc;
 @Test void requiresFinanceReadAndRefusesInvalidPeriod() throws Exception {
   var reader=user("finance").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("FINANCE_READ"));
   mvc.perform(get("/api/finance/inventory-turnover").param("from","2026-09-01").param("to","2026-10-01").with(reader))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.openingCost").exists())
      .andExpect(jsonPath("$.closingCost").exists())
      .andExpect(jsonPath("$.available").isBoolean());
   mvc.perform(get("/api/finance/inventory-turnover").param("from","2026-09-01").param("to","2026-10-01")
       .with(user("stock").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("INVENTORY_READ"))))
      .andExpect(status().isForbidden());
   mvc.perform(get("/api/finance/inventory-turnover").param("from","2026-10-01").param("to","2026-09-01").with(reader))
      .andExpect(status().isBadRequest());
 }
}
