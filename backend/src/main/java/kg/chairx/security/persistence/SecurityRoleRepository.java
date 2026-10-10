package kg.chairx.security.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
public class SecurityRoleRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public SecurityRoleRepository(
            JdbcClient jdbc,
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Only an active database employee holding the real system ADMIN role is an administrator. */
    @Transactional(readOnly = true)
    public boolean isActiveSystemAdministrator(UUID userId) {
        if (userId == null) return false;
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM app_users u
                    JOIN security_user_roles ur ON ur.user_id = u.id
                    JOIN security_roles r ON r.id = ur.role_id
                    WHERE u.id = :id AND u.active = TRUE
                      AND r.code = 'ADMIN' AND r.system_role = TRUE
                )
                """).param("id", userId).query(Boolean.class).single();
    }

    public boolean hasActiveAdministrator() {
        return countActiveAdministrators() > 0;
    }
    /**
     * Возвращает все разрешения пользователя
     * с учётом всех назначенных ему ролей.
     */
    @Transactional(readOnly = true)
    public Set<String> findPermissionsByUserId(UUID userId) {
        return new HashSet<>(
                jdbc.sql("""
                        SELECT DISTINCT rp.permission_code
                        FROM security_user_roles ur
                        JOIN security_role_permissions rp
                            ON rp.role_id = ur.role_id
                        WHERE ur.user_id = :userId
                        """)
                        .param("userId", userId)
                        .query(String.class)
                        .list()
        );
    }

    /**
     * Возвращает коды всех ролей пользователя.
     */
    @Transactional(readOnly = true)
    public Set<String> findRoleCodesByUserId(UUID userId) {
        return new HashSet<>(
                jdbc.sql("""
                        SELECT DISTINCT r.code
                        FROM security_user_roles ur
                        JOIN security_roles r
                            ON r.id = ur.role_id
                        WHERE ur.user_id = :userId
                        """)
                        .param("userId", userId)
                        .query(String.class)
                        .list()
        );
    }

    /**
     * Проверяет существование роли по UUID.
     */
    @Transactional(readOnly = true)
    public boolean existsById(UUID roleId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM security_roles
                    WHERE id = :roleId
                )
                """)
                .param("roleId", roleId)
                .query(Boolean.class)
                .single();
    }

    @Transactional(readOnly = true)
    public boolean userHasPermission(UUID userId, String permissionCode) {
        return jdbc.sql("""
            SELECT EXISTS (
                SELECT 1
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                JOIN security_role_permissions rp
                    ON rp.role_id = ur.role_id
                WHERE u.id = :userId
                  AND u.active = TRUE
                  AND rp.permission_code = :permissionCode
            )
            """)
                .param("userId", userId)
                .param("permissionCode", permissionCode)
                .query(Boolean.class)
                .single();
    }


    /**
     * Возвращает количество активных пользователей,
     * которым назначена роль ADMIN.
     */
    @Transactional(readOnly = true)
    public long countActiveAdministrators() {
        return jdbc.sql("""
                SELECT COUNT(DISTINCT u.id)
                FROM app_users u
                JOIN security_user_roles ur
                    ON ur.user_id = u.id
                JOIN security_roles r
                    ON r.id = ur.role_id
                WHERE r.code = 'ADMIN'
                  AND u.active = TRUE
                """)
                .query(Long.class)
                .single();
    }

    /**
     * Устанавливает транзакционную блокировку PostgreSQL.
     *
     * Используется для сериализации операций,
     * которые могут изменить количество активных ADMIN.
     *
     * Блокировка автоматически освобождается
     * после COMMIT или ROLLBACK.
     *
     * Важно: все операции изменения роли ADMIN
     * должны использовать эту блокировку.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRoleAssignments() {
        jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Void>)
                        connection -> {
                            try (var statement =
                                         connection.createStatement()) {

                                statement.execute(
                                        "SELECT pg_advisory_xact_lock(20261009, 1)"
                                );
                            }

                            return null;
                        }
        );
    }
    /**
     * Resolve an already active ADMIN without relying on its login.
     * Used by local development seeding with pre-existing databases.
     * The caller already holds the transaction-wide role assignment lock.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findFirstActiveAdministratorId() {
        return jdbc.sql("""
                SELECT u.id
                FROM app_users u
                WHERE u.active = TRUE
                  AND EXISTS (
                    SELECT 1
                    FROM security_user_roles ur
                    JOIN security_roles r ON r.id = ur.role_id
                    WHERE ur.user_id = u.id AND r.code = 'ADMIN'
                  )
                ORDER BY u.username
                LIMIT 1
                """)
                .query(UUID.class)
                .optional();
    }


}