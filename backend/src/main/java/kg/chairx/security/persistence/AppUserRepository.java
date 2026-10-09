package kg.chairx.security.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AppUserRepository {

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<AppUserRecord> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return jdbc.sql("""
                SELECT id, username, password_hash, display_name, active
                FROM app_users
                WHERE username = :username
                """)
                .param("username", username)
                .query((rs, rowNum) -> new AppUserRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getBoolean("active")
                ))
                .optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean deactivate(UUID userId) {
        return jdbc.sql("""
            UPDATE app_users
            SET active = FALSE
            WHERE id = :id
              AND active = TRUE
            """)
                .param("id", userId)
                .update() == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(
            UUID id,
            String username,
            String passwordHash,
            String displayName
    ) {
        jdbc.sql("""
            INSERT INTO app_users (
                id,
                username,
                password_hash,
                display_name,
                active
            )
            VALUES (
                :id,
                :username,
                :passwordHash,
                :displayName,
                TRUE
            )
            """)
                .param("id", id)
                .param("username", username)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .update();
    }

    @Transactional(readOnly = true)
    public Optional<AppUserRecord> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }

        return jdbc.sql("""
                SELECT id, username, password_hash, display_name, active
                FROM app_users
                WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new AppUserRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("display_name"),
                        rs.getBoolean("active")
                ))
                .optional();
    }

    public record AppUserRecord(
            UUID id,
            String username,
            String passwordHash,
            String displayName,
            boolean active
    ) {
    }
}