package kg.chairx.security.application;

import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityAuditRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import kg.chairx.security.persistence.SecurityUserRoleRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoleAssignmentService {

    private final AppUserRepository users;
    private final SecurityRoleRepository roles;
    private final SecurityUserRoleRepository userRoles;
    private final SecurityAuditRepository audit;

    public RoleAssignmentService(
            AppUserRepository users,
            SecurityRoleRepository roles,
            SecurityUserRoleRepository userRoles,
            SecurityAuditRepository audit
    ) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.audit = audit;
    }

    @Transactional
    public boolean removeRole(
            UUID actorUserId,
            UUID targetUserId,
            UUID roleId
    ) {
        if (actorUserId == null
                || targetUserId == null
                || roleId == null) {
            throw new IllegalArgumentException(
                    "Необходимо указать администратора, сотрудника и роль"
            );
        }

        // Сериализуем операции изменения ролей.
        roles.lockRoleAssignments();

        // Проверяем актуальные права инициатора.
        if (!roles.isActiveSystemAdministrator(actorUserId)
                || !roles.userHasPermission(actorUserId, "ROLES_ASSIGN")) {
            throw new AccessDeniedException(
                    "Недостаточно прав для изменения ролей"
            );
        }

        // Проверяем существование сотрудника.
        var targetUser = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Сотрудник не найден"
                ));

        // Проверяем существование роли.
        if (!roles.existsById(roleId)) {
            throw new IllegalArgumentException(
                    "Роль не найдена"
            );
        }

        // Если роль не назначена, ничего не меняем.
        if (!userRoles.hasRole(targetUserId, roleId)) {
            return false;
        }

        // Проверяем, является ли снимаемая роль ADMIN.
        boolean removingAdmin = roles.findRoleCodesByUserId(targetUserId)
                .contains("ADMIN")
                && roleId.equals(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"
                )
        );

        // Защищаем последнего активного администратора.
        if (removingAdmin && targetUser.active()) {
            long activeAdmins = roles.countActiveAdministrators();

            if (activeAdmins <= 1) {
                throw new IllegalStateException(
                        "Нельзя снять роль последнего активного администратора"
                );
            }
        }

        boolean removed = userRoles.removeRole(targetUserId, roleId);

        if (removed) {
            audit.record(
                    actorUserId,
                    "ROLE_REMOVED",
                    "USER",
                    targetUserId,
                    "Removed role: " + roleId
            );
        }

        return removed;
    }

    @Transactional
    public boolean assignRole(
            UUID actorUserId,
            UUID targetUserId,
            UUID roleId
    ) {
        if (actorUserId == null
                || targetUserId == null
                || roleId == null) {
            throw new IllegalArgumentException(
                    "Необходимо указать администратора, сотрудника и роль"
            );
        }

        // Сериализуем изменения назначений ролей.
        roles.lockRoleAssignments();

        // Проверяем актуальные полномочия инициатора.
        if (!roles.isActiveSystemAdministrator(actorUserId)
                || !roles.userHasPermission(actorUserId, "ROLES_ASSIGN")) {
            throw new AccessDeniedException(
                    "Недостаточно прав для назначения ролей"
            );
        }

        // Проверяем существование и активность сотрудника.
        var targetUser = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Сотрудник не найден"
                ));

        if (!targetUser.active()) {
            throw new IllegalArgumentException(
                    "Нельзя назначить роль неактивному сотруднику"
            );
        }

        // Проверяем существование роли.
        if (!roles.existsById(roleId)) {
            throw new IllegalArgumentException(
                    "Роль не найдена"
            );
        }

        // Повторное назначение не создаёт дубликат.
        boolean assigned = userRoles.assignRole(
                targetUserId,
                roleId
        );

        // Записываем аудит только при фактическом изменении.
        if (assigned) {
            audit.record(
                    actorUserId,
                    "ROLE_ASSIGNED",
                    "USER",
                    targetUserId,
                    "Assigned role: " + roleId
            );
        }

        return assigned;
    }
}