package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class BusinessAuthorizationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void verifyDatabase() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
    }

    private static RequestPostProcessor permission(String authority) {
        return user("rbac-business-user")
                .authorities(new SimpleGrantedAuthority(authority));
    }

    @Test void unknownMethodsPathsAndUnauthenticatedClientsFailClosed() throws Exception {
        mvc.perform(get("/api/sales").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(get("/api/purchases").with(permission("CATALOG_ACCESS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/unknown-business-action").with(permission("SALES_CREATE"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(request(HttpMethod.HEAD,"/api/finance/accounts")
                        .with(permission("FINANCE_READ")))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.OPTIONS,"/api/payments")
                        .with(permission("PAYMENTS_CREATE")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/sales/"+UUID.randomUUID())
                        .with(permission("SALES_CANCEL")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void p22RelatedDataAndMutationEndpointsEnforceExplicitPermissions() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String path: List.of("/api/customers/overview",
                "/api/sales/customer/"+id)) {
            mvc.perform(get(path).with(permission("CUSTOMERS_READ")))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).with(permission("SALES_READ")))
                    .andExpect(status().isForbidden());
        }
        for (String path: List.of("/api/inventory/overview",
                "/api/purchases/overview", "/api/finance/closings/2026-10-10/sales")) {
            mvc.perform(get(path).with(permission("SALES_READ")))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/api/sales/drafts").with(permission("SALES_READ"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/sales/"+id+"/draft").with(permission("SALES_CREATE"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sales/"+id+"/confirm").with(permission("SALES_CREATE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/deliveries/"+id+"/planned-date")
                        .with(permission("DELIVERIES_READ")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedDeliveryDate\":\"2026-10-15\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/sales/drafts").with(permission("SALES_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()); // CSRF still mandatory
        mvc.perform(put("/api/deliveries/"+id+"/planned-date")
                        .with(permission("DELIVERIES_MANAGE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedDeliveryDate\":\"2026-10-15\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void catalogCanReadButCannotChangeCatalogOrReadBusinessData() throws Exception {
        mvc.perform(get("/api/products").with(httpBasic("catalog","integration-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/"+UUID.randomUUID()+"/variants")
                        .with(httpBasic("catalog","integration-test-password")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/products").with(httpBasic("catalog","integration-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Not permitted\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/product-variants/"+UUID.randomUUID())
                        .with(httpBasic("catalog","integration-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        for (String path:List.of("/api/customers","/api/sales","/api/finance/accounts",
                "/api/inventory/balances?warehouseId="+UUID.randomUUID(),"/api/admin/users")) {
            mvc.perform(get(path).with(httpBasic("catalog","integration-test-password")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void sensitiveOperationsAreIsolatedFromTheirRelatedReadOrCreatePermissions()
            throws Exception {
        UUID id=UUID.randomUUID();
        String json="{}";
        record Denial(String path,String granted) {}
        for(var test:List.of(
                new Denial("/api/purchases/"+id+"/payments","PURCHASE_CREATE"),
                new Denial("/api/payments/"+id+"/cancel","PAYMENTS_CREATE"),
                new Denial("/api/refunds","RETURNS_CREATE"),
                new Denial("/api/exchanges/"+id+"/settlements","EXCHANGES_CREATE"),
                new Denial("/api/defects/"+id+"/write-off","DEFECTS_MANAGE"),
                new Denial("/api/deliveries/"+id+"/cancel","DELIVERIES_MANAGE"),
                new Denial("/api/sales/"+id+"/cancel","SALES_READ"),
                new Denial("/api/inventory/transfers","INVENTORY_READ"),
                new Denial("/api/finance/transfers","FINANCE_READ"),
                new Denial("/api/finance/closings/2026-10-10","FINANCE_READ"),
                new Denial("/api/expenses","EXPENSES_READ"))) {
            mvc.perform(post(test.path()).with(permission(test.granted()))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test void deniedFinancialAndStockOperationsHaveNoDatabaseEffects() throws Exception {
        UUID id=UUID.randomUUID();
        BigDecimal cash=balance("CASH");
        BigDecimal bank=balance("BANK");
        long movements=count("finance_movements");
        long stock=count("stock_movements");
        long payments=count("payments");
        long refunds=count("refunds");
        long expenses=count("expenses");
        long purchasePayments=count("purchase_payments");
        long sales=count("sales");
        long transfers=count("inventory_transfers");
        long audit=count("audit_entries");
        long securityRoleAssignments=count("security_user_roles");

        for(String target:List.of("/api/expenses","/api/refunds",
                "/api/purchases/"+id+"/payments","/api/payments",
                "/api/sales","/api/inventory/transfers")) {
            mvc.perform(post(target).with(permission("CATALOG_READ")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/users/"+id+"/roles")
                        .with(permission("CATALOG_READ")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        assertThat(balance("CASH")).isEqualByComparingTo(cash);
        assertThat(balance("BANK")).isEqualByComparingTo(bank);
        assertThat(count("finance_movements")).isEqualTo(movements);
        assertThat(count("stock_movements")).isEqualTo(stock);
        assertThat(count("payments")).isEqualTo(payments);
        assertThat(count("refunds")).isEqualTo(refunds);
        assertThat(count("expenses")).isEqualTo(expenses);
        assertThat(count("purchase_payments")).isEqualTo(purchasePayments);
        assertThat(count("sales")).isEqualTo(sales);
        assertThat(count("inventory_transfers")).isEqualTo(transfers);
        assertThat(count("audit_entries")).isEqualTo(audit);
        assertThat(count("security_user_roles")).isEqualTo(securityRoleAssignments);
    }

    @Test void validPermissionReachesValidationButCsrfRemainsMandatory()
            throws Exception {
        String purchasePath="/api/purchases/"+UUID.randomUUID()+"/payments";
        mvc.perform(post(purchasePath).with(permission("PURCHASE_PAYMENTS_CREATE"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(purchasePath).with(permission("PURCHASE_PAYMENTS_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/defects").with(permission("DEFECTS_MANAGE"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/defects").with(permission("DEFECTS_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private long count(String table) {
        // These are fixed internal fixture tables, not user-supplied SQL.
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);
    }
    private BigDecimal balance(String account) {
        return jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code=?",
                BigDecimal.class,account);
    }
}
