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

/** Migrates a disposable schema from historical V39 through the latest version. */
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class BusinessRbacMigrationIntegrationTests {
    @Autowired DataSource dataSource;

    @Test void upgradingHistoricalUsersRolesAndAuditFromV39PreservesEverything() {
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        String schema="p17_upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway before=Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).target("39").load();
        Flyway upgrade=Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).cleanDisabled(false).load();
        try {
            before.migrate();
            UUID user=UUID.randomUUID(),customRole=UUID.randomUUID(),audit=UUID.randomUUID();
            String password="{noop}unchanged-password";
            jdbc.update("""
                    INSERT INTO %s.app_users(id,username,password_hash,display_name,active)
                    VALUES (?, 'existing-p17-user',?, 'Existing user',true)
                    """.formatted(schema),user,password);
            jdbc.update("""
                    INSERT INTO %s.security_roles(id,code,name,system_role)
                    VALUES (?,'CUSTOM_ROLE','Retain custom role',false)
                    """.formatted(schema),customRole);
            jdbc.update("""
                    INSERT INTO %s.security_user_roles(user_id,role_id) VALUES (?,?)
                    """.formatted(schema),user,customRole);
            jdbc.update("""
                    INSERT INTO %s.security_audit_log(id,actor_user_id,action,target_type,target_id,details)
                    VALUES (?,?,'ROLE_ASSIGNED','USER',?,'History must survive')
                    """.formatted(schema),audit,user,user);
            jdbc.update("""
                    INSERT INTO %s.security_role_permissions(role_id,permission_code)
                    VALUES (?,'SALES_READ')
                    """.formatted(schema),customRole);

            // V39 -> V40 originally had one migration; P21 adds V41-V46.
            // Verify the upgrade advances through those versions as well.
            assertThat(upgrade.migrate().migrationsExecuted).isGreaterThanOrEqualTo(7);
            upgrade.validate();
            assertThat(jdbc.queryForObject(
                    "select password_hash from "+schema+".app_users where id=?",
                    String.class,user)).isEqualTo(password);
            assertThat(jdbc.queryForObject(
                    "select count(*) from "+schema+".security_user_roles WHERE user_id=? AND role_id=?",
                    Long.class,user,customRole)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from "+schema+".security_audit_log WHERE id=?",
                    Long.class,audit)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from %s.security_role_permissions
                    where role_id=? and permission_code='SALES_READ'
                    """.formatted(schema),Long.class,customRole)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from %s.security_role_permissions
                    where role_id=? and permission_code='PURCHASE_PAYMENTS_CREATE'
                    """.formatted(schema),Long.class,customRole)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from %s.security_role_permissions rp
                    join %s.security_roles r on rp.role_id=r.id
                    where r.code='ADMIN' and rp.permission_code='PURCHASE_PAYMENTS_CREATE'
                    """.formatted(schema,schema),Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from %s.security_role_permissions rp
                    join %s.security_roles r on rp.role_id=r.id
                    where r.code='MANAGER' and rp.permission_code='PURCHASE_CONFIRM'
                    """.formatted(schema,schema),Long.class)).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally {upgrade.clean();}
    }

    @Test void freshSchemaContainsAllSecurityPermissionsWithoutDuplicateGrants() {
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        String schema="p17_clean_"+UUID.randomUUID().toString().replace("-","");
        Flyway migrator=Flyway.configure().dataSource(dataSource)
                .schemas(schema).defaultSchema(schema).cleanDisabled(false).load();
        try {
            assertThat(migrator.migrate().migrationsExecuted).isGreaterThan(39);
            migrator.validate();
            Long duplicate=jdbc.queryForObject("""
                    SELECT count(*) FROM (
                      SELECT role_id,permission_code,count(*) as n
                      FROM %s.security_role_permissions
                      GROUP BY role_id,permission_code HAVING count(*)>1
                    ) x
                    """.formatted(schema),Long.class);
            assertThat(duplicate).isZero();
            assertThat(jdbc.queryForObject(
                    "select count(*) from "+schema+".security_permissions where code='REFUNDS_CREATE'",
                    Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from "+schema+".security_roles where system_role=true",
                    Long.class)).isEqualTo(3);
        } finally {migrator.clean();}
    }
}
