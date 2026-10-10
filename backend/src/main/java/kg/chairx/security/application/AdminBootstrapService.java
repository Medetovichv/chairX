package kg.chairx.security.application;

import kg.chairx.security.persistence.SecurityBootstrapRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityUserRoleRepository;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminBootstrapService {

    private static final UUID ADMIN_ROLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SecurityBootstrapRepository bootstrap;
    private final SecurityRoleRepository roles;
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final SecurityUserRoleRepository userRoles;
    private final String reservedUsername;

    public AdminBootstrapService(
            SecurityBootstrapRepository bootstrap,
            SecurityRoleRepository roles,
            AppUserRepository users,
            SecurityUserRoleRepository userRoles,
            JdbcTemplate jdbc,
            @Value("${CHAIRX_CATALOG_USERNAME:catalog}") String reservedUsername
    ) {
        this.bootstrap = bootstrap;
        this.roles = roles;
        this.users = users;
        this.userRoles = userRoles;
        this.jdbc = jdbc;
        this.reservedUsername = reservedUsername;
    }

    @Transactional
    public boolean initialize(
            String username,
            String password,
            String displayName
    ) {
        // Соблюдаем единый порядок блокировок.
        roles.lockRoleAssignments();

        if (bootstrap.lockAndCheckInitialized()) {
            return false;
        }

        // Если в базе уже есть администратор,
        // повторно создавать его нельзя.
        if (roles.hasActiveAdministrator()) {
            bootstrap.markInitialized();
            return false;
        }

        // Если пользователей уже создавали,
        // автоматическая инициализация небезопасна.
        Integer existingUsers = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM app_users
                """, Integer.class);

        if (existingUsers == null || existingUsers > 0) {
            throw new IllegalStateException(
                    "Bootstrap refused: database already contains users"
            );
        }

        // Проверяем конфигурацию.
        if (username == null || username.isBlank()
                || password == null || password.isBlank()
                || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Bootstrap administrator credentials are required"
            );
        }

        String normalizedUsername =
                username.trim().toLowerCase(Locale.ROOT);

        if (!normalizedUsername.matches("[a-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException(
                    "Invalid bootstrap administrator username"
            );
        }

        if (normalizedUsername.equalsIgnoreCase(reservedUsername.trim())) {
            throw new IllegalArgumentException(
                    "Reserved administrator username"
            );
        }

        if (password.length() < 12 || password.length() > 200) {
            throw new IllegalArgumentException(
                    "Bootstrap password must contain 12-200 characters"
            );
        }

        String normalizedDisplayName = displayName.trim();

        if (normalizedDisplayName.length() > 200) {
            throw new IllegalArgumentException(
                    "Administrator display name is too long"
            );
        }

        // Пароль сохраняется только в виде хеша.
        var encoder =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();

        String passwordHash = encoder.encode(password);
        UUID adminId = UUID.randomUUID();

        users.create(
                adminId,
                normalizedUsername,
                passwordHash,
                normalizedDisplayName
        );

        boolean roleAssigned = userRoles.assignRole(
                adminId,
                ADMIN_ROLE_ID
        );

        if (!roleAssigned) {
            throw new IllegalStateException(
                    "Failed to assign ADMIN role during bootstrap"
            );
        }

        bootstrap.markInitialized();

        return true;
    }
}