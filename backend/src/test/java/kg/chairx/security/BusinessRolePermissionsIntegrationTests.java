package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real users, real security_role_permissions, actual HTTP Basic auth. Unlike
 * @WithMockUser, this verifies persisted role assignments and active status.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class BusinessRolePermissionsIntegrationTests {
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void dbIsTestOnly() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
    }

    @AfterEach void cleanupOnlyCreatedAccounts() {
        // Our real-auth tests share chairx_test with existing security suites.
        // Never remove application users if this is not the isolated test DB.
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        jdbc.update("""
                DELETE FROM security_user_roles
                WHERE user_id IN (SELECT id FROM app_users
                                  WHERE username LIKE 'p17\\_role\\_%' ESCAPE '\\')
                """);
        jdbc.update("""
                DELETE FROM app_users
                WHERE username LIKE 'p17\\_role\\_%' ESCAPE '\\'
                """);
    }

    private record Employee(UUID id,String username) {}

    private Employee createEmployee(UUID role) {
        UUID id=UUID.randomUUID();
        String username="p17_role_"+id.toString().replace("-","");
        String hash=PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode("p17-test-password");
        jdbc.update("""
                INSERT INTO app_users(id,username,password_hash,display_name,active)
                VALUES (?,?,?,'P17 Test User',true)
                """,id,username,hash);
        if(role!=null) jdbc.update(
                "INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",id,role);
        return new Employee(id,username);
    }

    @Test void rolePermissionMatrixIsSeededWithoutBroadMoneyAuthority() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM security_permissions WHERE code='PURCHASE_PAYMENTS_CREATE'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM security_permissions WHERE code='DEFECTS_WRITE_OFF'",
                Long.class)).isEqualTo(1);

        for(String permission : new String[]{"PURCHASE_PAYMENTS_CREATE",
                "REFUNDS_CREATE","PAYMENTS_CANCEL","EXCHANGES_SETTLE",
                "DEFECTS_WRITE_OFF","FINANCE_CLOSE","FINANCE_TRANSFER",
                "FINANCE_INITIALIZE","USERS_CREATE","ROLES_ASSIGN"}) {
            assertRolePermission(ADMIN,permission,true);
            assertRolePermission(MANAGER,permission,false);
            assertRolePermission(EMPLOYEE,permission,false);
        }

        for(String permission:new String[]{"SALES_CREATE","SALES_READ",
                "INVENTORY_READ","CATALOG_READ","CUSTOMERS_CREATE"}) {
            assertRolePermission(EMPLOYEE,permission,true);
            assertRolePermission(MANAGER,permission,true);
        }
        for(String permission:new String[]{"PURCHASE_CONFIRM",
                "DELIVERIES_MANAGE","PAYMENTS_CREATE","INVENTORY_TRANSFER"}) {
            assertRolePermission(MANAGER,permission,true);
            assertRolePermission(EMPLOYEE,permission,false);
        }
    }

    @Test void transferHistoryIsCostFreeForRealEmployeeAndCustomInventoryReader() throws Exception {
        Employee employee = createEmployee(EMPLOYEE);
        UUID roleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO security_roles(id, code, name, system_role)
                VALUES (?, ?, 'Cost-free inventory reader test', FALSE)
                """, roleId, "COSTFREE_READER_" + roleId.toString().substring(0, 8));
        try {
            jdbc.update("""
                    INSERT INTO security_role_permissions(role_id, permission_code)
                    VALUES (?, 'INVENTORY_READ')
                    """, roleId);
            Employee custom = createEmployee(roleId);
            for (Employee reader : java.util.List.of(employee, custom)) {
                mvc.perform(get("/api/inventory/transfers")
                                .with(httpBasic(reader.username(), "p17-test-password")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items").isArray())
                        .andExpect(result -> assertThat(
                                result.getResponse().getContentAsString())
                                .doesNotContain(
                                        "\"totalCost\"", "\"unitCost\"",
                                        "\"purchasePrice\"", "\"costAllocations\""));
                mvc.perform(get("/api/finance/accounts")
                                .with(httpBasic(reader.username(), "p17-test-password")))
                        .andExpect(status().isForbidden());
                mvc.perform(post("/api/inventory/transfers")
                                .with(httpBasic(reader.username(), "p17-test-password"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            }
            // A custom role with transfer-only authority can access POST but
            // must not gain FINANCE_READ just to transfer stock.
            jdbc.update("""
                    INSERT INTO security_role_permissions(role_id, permission_code)
                    VALUES (?, 'INVENTORY_TRANSFER')
                    """, roleId);
            mvc.perform(post("/api/inventory/transfers")
                            .with(httpBasic(custom.username(), "p17-test-password"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/finance/accounts")
                            .with(httpBasic(custom.username(), "p17-test-password")))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM security_user_roles WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM security_role_permissions WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM security_roles WHERE id=?", roleId);
        }
    }

    @Test void realEmployeeAndManagerReachOnlyPermittedEndpoints() throws Exception {
        Employee frontline=createEmployee(EMPLOYEE);
        Employee manager=createEmployee(MANAGER);
        mvc.perform(get("/api/products").with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sales").with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/accounts")
                        .with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/deliveries")
                        .with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/purchases").with(httpBasic(manager.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/accounts")
                        .with(httpBasic(manager.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(httpBasic(manager.username(),"p17-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isForbidden());
    }

    @Test void realAdminCanReadRestrictedBusinessAndAdminEndpoints() throws Exception {
        Employee admin=createEmployee(ADMIN);
        mvc.perform(get("/api/admin/users").with(httpBasic(admin.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/purchases").with(httpBasic(admin.username(),"p17-test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance/accounts")
                        .with(httpBasic(admin.username(),"p17-test-password")))
                .andExpect(status().isOk());
    }

    @Test void realBasicUsersRequirePermissionsForStateChangesEvenWithCsrf() throws Exception {
        Employee employee=createEmployee(EMPLOYEE);
        Employee manager=createEmployee(MANAGER);
        Employee admin=createEmployee(ADMIN);
        String purchases="/api/purchases/"+UUID.randomUUID()+"/payments";

        mvc.perform(post("/api/sales")
                        .with(httpBasic(employee.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses")
                        .with(httpBasic(employee.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/purchases")
                        .with(httpBasic(manager.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(purchases)
                        .with(httpBasic(manager.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(purchases)
                        .with(httpBasic(admin.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/transfers")
                        .with(httpBasic(admin.username(),"p17-test-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void changingRolesChangesEffectiveAccessOnFreshBasicAuthentication() throws Exception {
        Employee frontline=createEmployee(EMPLOYEE);
        mvc.perform(get("/api/purchases")
                        .with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isForbidden());

        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",
                frontline.id(),MANAGER);
        mvc.perform(get("/api/purchases")
                        .with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isOk());

        jdbc.update("DELETE FROM security_user_roles WHERE user_id=? AND role_id=?",
                frontline.id(),MANAGER);
        mvc.perform(get("/api/purchases")
                        .with(httpBasic(frontline.username(),"p17-test-password")))
                .andExpect(status().isForbidden());
    }

    @Test void disabledOrUnassignedEmployeesCannotReadBusinessData() throws Exception {
        Employee noRole=createEmployee(null);
        Employee disabled=createEmployee(EMPLOYEE);
        jdbc.update("UPDATE app_users SET active=false WHERE id=?",disabled.id());
        mvc.perform(get("/api/products").with(httpBasic(noRole.username(),"p17-test-password")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products").with(httpBasic(disabled.username(),"p17-test-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").with(httpBasic(disabled.username(),"wrong")))
                .andExpect(status().isUnauthorized());
    }

    private void assertRolePermission(UUID role,String permission,boolean allowed) {
        Boolean exists=jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM security_role_permissions
                              WHERE role_id=? AND permission_code=?)
                """,Boolean.class,role,permission);
        assertThat(exists).describedAs("%s for role %s",permission,role)
                .isEqualTo(allowed);
    }
}
