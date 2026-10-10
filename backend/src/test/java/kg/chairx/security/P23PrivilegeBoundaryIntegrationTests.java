package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class P23PrivilegeBoundaryIntegrationTests {
    static final UUID ADMIN=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID EMPLOYEE=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final String PASSWORD="p23-boundary-password";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    record TestUser(UUID id,String username) { }

    @BeforeEach void verifyDatabase() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
                .isEqualTo("chairx_test");
    }

    private TestUser user(UUID role) {
        UUID id=UUID.randomUUID();
        String name="p23_boundary_"+id.toString().replace("-","");
        jdbc.update("""
                INSERT INTO app_users(id, username, password_hash, display_name, active)
                VALUES (?, ?, ?, 'P23 boundary', TRUE)
                """,id,name,PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD));
        if (role != null) jdbc.update(
                "INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",id,role);
        return new TestUser(id,name);
    }

    @AfterEach void clean() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
                .isEqualTo("chairx_test");
        jdbc.update("DELETE FROM security_audit_log WHERE actor_user_id IN "
                +"(SELECT id FROM app_users WHERE username LIKE 'p23_boundary_%')");
        jdbc.update("DELETE FROM security_audit_log WHERE target_id IN "
                +"(SELECT id FROM security_roles WHERE code LIKE 'P23_BOUNDARY_%')");
        jdbc.update("DELETE FROM security_user_roles WHERE user_id IN "
                +"(SELECT id FROM app_users WHERE username LIKE 'p23_boundary_%')");
        jdbc.update("DELETE FROM security_user_roles WHERE role_id IN "
                +"(SELECT id FROM security_roles WHERE code LIKE 'P23_BOUNDARY_%')");
        jdbc.update("DELETE FROM security_role_permissions WHERE role_id IN "
                +"(SELECT id FROM security_roles WHERE code LIKE 'P23_BOUNDARY_%')");
        jdbc.update("DELETE FROM security_roles WHERE code LIKE 'P23_BOUNDARY_%'");
        jdbc.update("DELETE FROM app_users WHERE username LIKE 'p23_boundary_%'");
    }

    @Test void illegalLegacyCustomGrantsNeverGiveSystemAdministration() throws Exception {
        TestUser staff=user(null);
        UUID role=UUID.randomUUID();
        jdbc.update("INSERT INTO security_roles(id,code,name,system_role) "
                +"VALUES (?,'P23_BOUNDARY_ILLEGAL','Illegal legacy role',FALSE)",role);
        for(String permission:new String[]{"USERS_READ","ROLES_CREATE",
                "ADMIN_ACCESS","DAILY_CLOSING_UNLOCK_ADMIN"}) {
            jdbc.update("INSERT INTO security_role_permissions(role_id,permission_code) VALUES (?,?)",
                    role,permission);
        }
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",staff.id(),role);
        mvc.perform(get("/api/admin/users").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(staff.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P23_BOUNDARY_HACK","name":"Hack","permissions":[]}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/finance/closings/2026-10-10/unlock")
                        .with(httpBasic(staff.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Attempt"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ROLES_CREATE"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("DAILY_CLOSING_UNLOCK_ADMIN"))));
    }

    @Test void changingRoleGrantsImmediatelyRevokesExistingEmployeeAccess() throws Exception {
        TestUser admin=user(ADMIN), staff=user(EMPLOYEE);
        String created=mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P23_BOUNDARY_BUYER","name":"Buyer",
                                 "permissions":["PURCHASE_READ"]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        UUID role=UUID.fromString(created.substring(created.lastIndexOf('/')+1));
        mvc.perform(post("/api/admin/users/"+staff.id()+"/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\""+role+"\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/purchases").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/roles/"+role)
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Buyer","permissions":[],"expectedVersion":0,
                                 "reason":"Revoke purchase access"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/purchases").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test void warehouseProjectionContainsRealQuantitiesButNoPurchasePrices() throws Exception {
        TestUser clerk=user(null);
        UUID role=UUID.randomUUID(), supplier=UUID.randomUUID(), product=UUID.randomUUID();
        UUID variant=UUID.randomUUID(),purchase=UUID.randomUUID(),item=UUID.randomUUID();
        jdbc.update("INSERT INTO security_roles(id,code,name,system_role) "
                +"VALUES (?,'P23_BOUNDARY_RECEIVER','Receiver',FALSE)",role);
        jdbc.update("INSERT INTO security_role_permissions(role_id,permission_code) "
                +"VALUES (?,'INVENTORY_RECEIVE')",role);
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",clerk.id(),role);
        try {
            jdbc.update("INSERT INTO suppliers(id,name,active,created_at,updated_at) "
                    +"VALUES (?,'P23 supplier',TRUE,NOW(),NOW())",supplier);
            jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) "
                    +"VALUES (?,'P23 Model',TRUE,NOW(),NOW())",product);
            jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) "
                    +"VALUES (?,?,'P23 Brown',25000,TRUE,NOW(),NOW())",variant,product);
            jdbc.update("""
                    INSERT INTO purchases(id,supplier_id,status,cargo_cost,cargo_allocation_method,
                    comment,created_by,created_at,updated_at)
                    VALUES (?,?,'CONFIRMED',777,'BY_QUANTITY','P23','tester',NOW(),NOW())
                    """,purchase,supplier);
            jdbc.update("""
                    INSERT INTO purchase_items(id,purchase_id,product_variant_id,line_number,
                    ordered_quantity,purchase_unit_cost)
                    VALUES (?,?,?,?,7,8000)
                    """,item,purchase,variant,1);
            mvc.perform(get("/api/purchases/receiving")
                            .with(httpBasic(clerk.username(),PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.purchaseItemId == '"+item+"')].orderedQuantity")
                            .value(org.hamcrest.Matchers.hasItem(7)))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("P23 Model")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("purchaseUnitCost"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("cargoCost"))));
            mvc.perform(get("/api/purchases/"+purchase+"/receiving-summary")
                            .with(httpBasic(clerk.username(),PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].orderedQuantity").value(7))
                    .andExpect(jsonPath("$.items[0].receivedQuantity").value(0))
                    .andExpect(jsonPath("$.items[0].remainingQuantity").value(7));
            mvc.perform(get("/api/purchases/"+purchase)
                            .with(httpBasic(clerk.username(),PASSWORD)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/purchases/"+purchase+"/payments")
                            .with(httpBasic(clerk.username(),PASSWORD)))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM purchase_items WHERE purchase_id=?",purchase);
            jdbc.update("DELETE FROM purchases WHERE id=?",purchase);
            jdbc.update("DELETE FROM product_variants WHERE id=?",variant);
            jdbc.update("DELETE FROM products WHERE id=?",product);
            jdbc.update("DELETE FROM suppliers WHERE id=?",supplier);
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void realEmployeeCanCreateAndEditDraftButNotFulfillOrConfirmIncompleteDraft() throws Exception {
        TestUser employee=user(EMPLOYEE);
        UUID key=UUID.randomUUID();
        String created=mvc.perform(post("/api/sales/drafts")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"%s","items":[],"comment":"Phone enquiry"}
                                """.formatted(key)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getHeader("Location");
        UUID saleId=UUID.fromString(created.substring(created.lastIndexOf('/')+1));
        mvc.perform(put("/api/sales/"+saleId+"/draft")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[],"comment":"Updated phone enquiry"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post("/api/sales/"+saleId+"/confirm")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/sales/"+saleId+"/fulfill")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
        // Test-scoped transaction rolls back the DRAFT and its audit records.
    }

}
