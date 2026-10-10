package kg.chairx.security.application;

import kg.chairx.security.persistence.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kg.chairx.security.persistence.SecurityAuditRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserManagementService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String reservedUsername;
    private final SecurityAuditRepository audit;
    private final SecurityRoleRepository roles;

    public UserManagementService(
            AppUserRepository users,
            SecurityAuditRepository audit,
            SecurityRoleRepository roles,
            @Value("${CHAIRX_CATALOG_USERNAME:catalog}")
            String reservedUsername
    ) {
        this.users = users;
        this.audit = audit;
        this.roles = roles;
        this.passwordEncoder =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();
        this.reservedUsername = reservedUsername;
    }

    @Transactional
    public boolean deactivateUser(
            UUID actorUserId,
            UUID targetUserId
    ) {
        if (actorUserId == null || targetUserId == null) {
            throw new IllegalArgumentException(
                    "Необходимо указать администратора и сотрудника"
            );
        }

        // Та же блокировка, что используется при изменении ролей.
        roles.lockRoleAssignments();

        // Проверяем актуальные права инициатора.
        if (!roles.isActiveSystemAdministrator(actorUserId)
                || !roles.userHasPermission(actorUserId, "USERS_DEACTIVATE")) {
            throw new AccessDeniedException(
                    "Недостаточно прав для деактивации сотрудников"
            );
        }

        var target = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Сотрудник не найден"
                ));

        // Повторная деактивация не изменяет состояние.
        if (!target.active()) {
            return false;
        }

        // Защита последнего активного администратора.
        if (roles.findRoleCodesByUserId(targetUserId).contains("ADMIN")
                && roles.countActiveAdministrators() <= 1) {
            throw new IllegalStateException(
                    "Нельзя деактивировать последнего активного администратора"
            );
        }

        boolean deactivated = users.deactivate(targetUserId);

        if (deactivated) {
            audit.record(
                    actorUserId,
                    "USER_DEACTIVATED",
                    "USER",
                    targetUserId,
                    "Deactivated employee: " + target.username()
            );
        }

        return deactivated;
    }

    @Transactional
    public UUID createUser(UUID actorUserId, CreateUserCommand command) {
        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Не указан администратор, выполняющий операцию"
            );
        }

        if (command == null) {
            throw new IllegalArgumentException(
                    "Данные сотрудника обязательны"
            );
        }

        // Serialize administrative writes with role edits and removals, so
        // stale credentials cannot authorize user creation after revocation.
        roles.lockRoleAssignments();

        if (!roles.isActiveSystemAdministrator(actorUserId)
                || !roles.userHasPermission(actorUserId, "USERS_CREATE")) {
            throw new AccessDeniedException(
                    "Недостаточно прав для создания сотрудников"
            );
        }

        String username = normalizeUsername(command.username());
        String displayName = validateDisplayName(command.displayName());
        String password = validatePassword(command.password());

        if (username.equalsIgnoreCase(reservedUsername)) {
            throw new IllegalArgumentException(
                    "Это имя пользователя зарезервировано"
            );
        }

        UUID userId = UUID.randomUUID();

        users.create(
                userId,
                username,
                passwordEncoder.encode(password),
                displayName
        );

        audit.record(
                actorUserId,
                "USER_CREATED",
                "USER",
                userId,
                "Created employee: " + username
        );

        return userId;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException(
                    "Имя пользователя обязательно"
            );
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);

        if (!normalized.matches("[a-z0-9._-]{3,100}")) {
            throw new IllegalArgumentException(
                    "Имя пользователя должно содержать 3–100 символов: a-z, 0-9, точку, дефис или подчёркивание"
            );
        }

        return normalized;
    }

    private String validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Имя сотрудника обязательно"
            );
        }

        String normalized = displayName.trim();

        if (normalized.length() > 200) {
            throw new IllegalArgumentException(
                    "Имя сотрудника слишком длинное"
            );
        }

        return normalized;
    }

    private String validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 128) {
            throw new IllegalArgumentException(
                    "Пароль должен содержать от 12 до 128 символов"
            );
        }

        return password;
    }
}