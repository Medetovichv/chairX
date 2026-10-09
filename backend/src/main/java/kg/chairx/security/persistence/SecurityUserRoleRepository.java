package kg.chairx.security.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class SecurityUserRoleRepository {

    private final JdbcClient jdbc;

    public SecurityUserRoleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean assignRole(UUID userId, UUID roleId) {
        int inserted = jdbc.sql("""
                INSERT INTO security_user_roles (
                    user_id,
                    role_id
                )
                VALUES (
                    :userId,
                    :roleId
                )
                ON CONFLICT (user_id, role_id) DO NOTHING
                """)
                .param("userId", userId)
                .param("roleId", roleId)
                .update();

        return inserted == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean removeRole(UUID userId, UUID roleId) {
        int deleted = jdbc.sql("""
                DELETE FROM security_user_roles
                WHERE user_id = :userId
                  AND role_id = :roleId
                """)
                .param("userId", userId)
                .param("roleId", roleId)
                .update();

        return deleted == 1;
    }

    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, UUID roleId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM security_user_roles
                    WHERE user_id = :userId
                      AND role_id = :roleId
                )
                """)
                .param("userId", userId)
                .param("roleId", roleId)
                .query(Boolean.class)
                .single();
    }
}