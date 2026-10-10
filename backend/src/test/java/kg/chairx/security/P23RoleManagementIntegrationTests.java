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

/** P23 uses real PostgreSQL roles and HTTP Basic, not @WithMockUser. */
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class P23RoleManagementIntegrationTests {
    static final UUID ADMIN=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID MANAGER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID EMPLOYEE=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final String PASSWORD="test-password-p23";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    record User(UUID id, String username) { }

    @BeforeEach void verifyIsolatedDb() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("chairx_test");
    }

    User user(UUID role) {
        UUID id=UUID.randomUUID();
        String name="p23_role_"+id.toString().replace("-","");
        jdbc.update("INSERT INTO app_users(id,username,password_hash,display_name,active) "
                + "VALUES (?,?,?,'P23 Integration',TRUE)",id,name,
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD));
        if (role!=null) {
            jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",id,role);
        }
        return new User(id,name);
    }

    @AfterEach void cleanupSuiteFixtures() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
                .isEqualTo("chairx_test");
        jdbc.update("DELETE FROM security_audit_log WHERE actor_user_id IN "
                + "(SELECT id FROM app_users WHERE username LIKE 'p23_role_%')");
        jdbc.update("DELETE FROM security_audit_log WHERE target_id IN "
                + "(SELECT id FROM security_roles WHERE code LIKE 'P23_%')");
        jdbc.update("DELETE FROM security_user_roles WHERE user_id IN "
                + "(SELECT id FROM app_users WHERE username LIKE 'p23_role_%')");
        jdbc.update("DELETE FROM security_user_roles WHERE role_id IN "
                + "(SELECT id FROM security_roles WHERE code LIKE 'P23_%')");
        jdbc.update("DELETE FROM security_role_permissions WHERE role_id IN "
                + "(SELECT id FROM security_roles WHERE code LIKE 'P23_%')");
        jdbc.update("DELETE FROM security_roles WHERE code LIKE 'P23_%'");
        jdbc.update("DELETE FROM app_users WHERE username LIKE 'p23_role_%'");
    }

    @Test void createUpdateGetVersionAndAudit() throws Exception {
        User admin=user(ADMIN);
        var created=mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P23_STOCK","name":"Кладовщик",
                                 "permissions":["INVENTORY_RECEIVE","CATALOG_READ"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("P23_STOCK"))
                .andExpect(jsonPath("$.systemRole").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.assignedUsersCount").value(0))
                .andReturn();
        String location=created.getResponse().getHeader("Location");
        assertThat(location).isNotBlank();
        UUID id=UUID.fromString(location.substring(location.lastIndexOf('/')+1));
        mvc.perform(get("/api/admin/roles/"+id).with(httpBasic(admin.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("CATALOG_READ"));
        mvc.perform(get("/api/admin/permissions").with(httpBasic(admin.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SALES_DRAFT_MANAGE')]").exists());
        mvc.perform(put("/api/admin/roles/"+id)
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Склад","permissions":["INVENTORY_READ"],
                                 "expectedVersion":0,"reason":"Изменение обязанностей"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("INVENTORY_READ"));
        mvc.perform(put("/api/admin/roles/"+id)
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Conflict","permissions":[],
                                 "expectedVersion":0,"reason":"Stale edit"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_log "
                +"WHERE target_id=? AND action='ROLE_PERMISSIONS_CHANGED'",Long.class,id)).isEqualTo(1);
        mvc.perform(get("/api/admin/audit?targetId="+id)
                        .with(httpBasic(admin.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].targetId").value(id.toString()));
    }

    @Test void forbiddenElevationAndAdminImmutability() throws Exception {
        User admin=user(ADMIN), manager=user(MANAGER),employee=user(EMPLOYEE);
        mvc.perform(get("/api/admin/audit").with(httpBasic(manager.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        for (User principal : new User[]{manager,employee}) {
            mvc.perform(post("/api/admin/roles")
                            .with(httpBasic(principal.username(),PASSWORD)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"P23_BLOCKED","name":"Denied","permissions":["SALES_READ"]}
                                    """))
                    .andExpect(status().isForbidden());
        }
        for (String permission : new String[]{"ROLES_ASSIGN","CATALOG_ACCESS",
                "DAILY_CLOSING_UNLOCK_ADMIN","NO_SUCH_PERMISSION"}) {
            mvc.perform(post("/api/admin/roles")
                            .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"P23_HACK\",\"name\":\"Hack\",\"permissions\":[\""
                                    + permission + "\"]}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ADMIN","name":"Fake admin","permissions":[]}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/roles/"+ADMIN)
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Modified admin","permissions":[],
                                 "expectedVersion":0,"reason":"Attempt"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test void assignmentUnionReflectsOnFreshRequestsAndStaffCannotManageRoles() throws Exception {
        User admin=user(ADMIN), staff=user(EMPLOYEE);
        mvc.perform(get("/api/purchases").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P23_PURCHASE","name":"Purchaser","permissions":["PURCHASE_READ"]}
                                """)).andExpect(status().isCreated()).andReturn();
        String location=response.getResponse().getHeader("Location");
        UUID id=UUID.fromString(location.substring(location.lastIndexOf('/')+1));
        mvc.perform(post("/api/admin/users/"+staff.id()+"/roles")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\""+id+"\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/purchases").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray());
        mvc.perform(delete("/api/admin/users/"+staff.id()+"/roles/"+id)
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/purchases").with(httpBasic(staff.username(),PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test void saleReadAndDraftPermissionsHaveNoFinanceOrFulfillmentGrants() throws Exception {
        User employee=user(EMPLOYEE);
        mvc.perform(get("/api/sales").with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/accounts").with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/sale/"+UUID.randomUUID())
                        .with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sales/"+UUID.randomUUID()+"/fulfill")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sales/"+UUID.randomUUID()+"/confirm")
                        .with(httpBasic(employee.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_role_permissions "
                +"WHERE role_id=? AND permission_code='SALES_DRAFT_MANAGE'",Long.class,EMPLOYEE))
                .isEqualTo(1);
    }

    @Test void receivingIsPriceFreeWithoutPurchaseOrFinanceRead() throws Exception {
        User clerk=user(null);
        UUID role=UUID.randomUUID();
        jdbc.update("INSERT INTO security_roles(id,code,name,system_role) "
                +"VALUES (?,'P23_RECEIVER','Receiver',FALSE)",role);
        jdbc.update("INSERT INTO security_role_permissions(role_id,permission_code) "
                +"VALUES (?,'INVENTORY_RECEIVE')",role);
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",clerk.id(),role);
        mvc.perform(get("/api/purchases/receiving")
                        .with(httpBasic(clerk.username(),PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("purchaseUnitCost"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cargoCost"))));
        mvc.perform(get("/api/purchases").with(httpBasic(clerk.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/purchases/overview").with(httpBasic(clerk.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/cash-flow").with(httpBasic(clerk.username(),PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test void dailyClosingCanBeReadWithoutGeneralFinanceAndExpenseRead() throws Exception {
        User employee=user(EMPLOYEE);
        mvc.perform(get("/api/finance/closings")
                        .with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/closings/2026-10-10/sales")
                        .with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/cash-flow")
                        .with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/expenses")
                        .with(httpBasic(employee.username(),PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test void csrfTechnicalCatalogAndUnknownRoutesStillFailClosed() throws Exception {
        User admin=user(ADMIN);
        mvc.perform(post("/api/admin/roles")
                        .with(httpBasic(admin.username(),PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"P23_CSRF","name":"Role","permissions":[]}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles")
                        .with(httpBasic("catalog","integration-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/roles").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/role-escalation")
                        .with(httpBasic(admin.username(),PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void builtInManagerAndEmployeeCanBeEditedWithoutChangingTheirIdentity() throws Exception {
        User admin=user(ADMIN);
        for (UUID id : new UUID[]{MANAGER, EMPLOYEE}) {
            String original=jdbc.queryForObject("SELECT name FROM security_roles WHERE id=?",String.class,id);
            Long version=jdbc.queryForObject("SELECT version FROM security_roles WHERE id=?",Long.class,id);
            var permissionCodes=jdbc.queryForList(
                    "SELECT permission_code FROM security_role_permissions WHERE role_id=? ORDER BY permission_code",
                    String.class,id);
            String permissionsJson=permissionCodes.stream()
                    .map(p -> "\"" + p + "\"")
                    .collect(java.util.stream.Collectors.joining(",","[","]"));
            String body="""
                    {"name":"P23 temporary name","permissions":%s,
                     "expectedVersion":%d,"reason":"Test default role edit"}
                    """.formatted(permissionsJson,version);
            try {
                mvc.perform(put("/api/admin/roles/"+id)
                                .with(httpBasic(admin.username(),PASSWORD)).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.version").value(version+1));
                assertThat(jdbc.queryForObject(
                        "SELECT code FROM security_roles WHERE id=?",String.class,id))
                        .isIn("MANAGER","EMPLOYEE");
                assertThat(jdbc.queryForObject(
                        "SELECT system_role FROM security_roles WHERE id=?",Boolean.class,id))
                        .isTrue();
            } finally {
                jdbc.update("UPDATE security_roles SET name=? WHERE id=?",original,id);
            }
        }
    }

}
