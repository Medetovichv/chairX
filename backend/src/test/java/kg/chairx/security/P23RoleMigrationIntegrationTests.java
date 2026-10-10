package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Migration V50 -> V51 on an isolated, pre-existing schema with custom grants and audit. */
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class P23RoleMigrationIntegrationTests {
    @Autowired DataSource dataSource;

    @Test void upgradesV50WithoutResettingUsersCustomRolesOrAudit() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class))
                .isEqualTo("chairx_test");

        String schema = "p23_upgrade_" + UUID.randomUUID().toString().replace("-","");
        Flyway before = Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).target("50").load();
        Flyway upgrade = Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).cleanDisabled(false).load();
        UUID user = UUID.randomUUID(), customRole = UUID.randomUUID(), log = UUID.randomUUID();
        try {
            before.migrate();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'security_roles'
                      AND column_name = 'version'
                    """,Long.class,schema)).isZero();

            jdbc.update(("INSERT INTO %s.app_users(id,username,password_hash,display_name,active) "
                    + "VALUES (?,'p23-migrating-user','{noop}unchanged','Historic User',TRUE)").formatted(schema),user);
            jdbc.update(("INSERT INTO %s.security_roles(id,code,name,system_role) "
                    + "VALUES (?,'P23_PERSISTED_ROLE','Legacy custom',FALSE)").formatted(schema),customRole);
            jdbc.update(("INSERT INTO %s.security_user_roles(user_id,role_id) VALUES (?,?)")
                    .formatted(schema),user,customRole);
            jdbc.update(("INSERT INTO %s.security_role_permissions(role_id,permission_code) "
                    + "VALUES (?,'SALES_READ')").formatted(schema),customRole);
            jdbc.update(("INSERT INTO %s.security_audit_log("
                    + "id,actor_user_id,action,target_type,target_id,details) "
                    + "VALUES (?,?,'ROLE_ASSIGNED','USER',?,'Do not discard')")
                    .formatted(schema),log,user,user);

            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();

            assertThat(jdbc.queryForObject("SELECT password_hash FROM "+schema+".app_users WHERE id=?",
                    String.class,user)).isEqualTo("{noop}unchanged");
            assertThat(jdbc.queryForObject("SELECT version FROM "+schema+".security_roles WHERE id=?",
                    Long.class,customRole)).isZero();
            assertThat(jdbc.queryForObject("SELECT system_role FROM "+schema+".security_roles WHERE id=?",
                    Boolean.class,customRole)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".security_user_roles "
                    + "WHERE user_id=? AND role_id=?",Long.class,user,customRole)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".security_role_permissions "
                    + "WHERE role_id=? AND permission_code='SALES_READ'",Long.class,customRole))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT details FROM "+schema+".security_audit_log "
                    + "WHERE id=?",String.class,log)).isEqualTo("Do not discard");

            for (String role : new String[]{"ADMIN","MANAGER","EMPLOYEE"}) {
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM %s.security_role_permissions rp
                        JOIN %s.security_roles r ON r.id = rp.role_id
                        WHERE r.code=? AND rp.permission_code='SALES_DRAFT_MANAGE'
                        """.formatted(schema,schema),Long.class,role)).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".security_role_permissions "
                    + "WHERE role_id=? AND permission_code='SALES_DRAFT_MANAGE'",Long.class,customRole))
                    .isZero();
            long all = jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".security_permissions",
                    Long.class);
            long admin = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM %s.security_role_permissions rp
                    JOIN %s.security_roles r ON r.id=rp.role_id
                    WHERE r.code='ADMIN'
                    """.formatted(schema,schema),Long.class);
            assertThat(admin).isEqualTo(all);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally {
            upgrade.clean();
        }
    }
}
