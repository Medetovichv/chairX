package kg.chairx.security.application;

import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityAuditRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Database-backed role administration. Custom roles never confer administrator status. */
@Service
public class RoleManagementService {
    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final SecurityRoleRepository roles;
    private final SecurityAuditRepository audit;

    public RoleManagementService(JdbcClient jdbc, AppUserRepository users,
                                 SecurityRoleRepository roles, SecurityAuditRepository audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.roles = roles;
        this.audit = audit;
    }

    public record RoleView(UUID id, String code, String name, boolean systemRole,
                           List<String> permissions, long assignedUsersCount, long version) { }
    public record PermissionView(String code, String description, String group,
                                 boolean sensitive, boolean adminOnly) { }
    public record AuditLine(UUID id, UUID actorUserId, String action, String targetType,
                            UUID targetId, String details, java.time.Instant createdAt) { }
    public record AuditPage(List<AuditLine> items, int page, int size, long total) { }
    public record CreateRole(String code, String name, List<String> permissions) { }
    public record UpdateRole(String name, List<String> permissions,
                             Long expectedVersion, String reason) { }

    public static boolean adminOnly(String code) {
        return code != null && (code.startsWith("USERS_") || code.startsWith("ROLES_")
                || code.equals("ADMIN_ACCESS") || code.equals("DAILY_CLOSING_UNLOCK_ADMIN"));
    }

    private UUID requireAdmin(Authentication authentication, String authority) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        UUID id = users.findByUsername(authentication.getName())
                .filter(AppUserRepository.AppUserRecord::active)
                .map(AppUserRepository.AppUserRecord::id)
                .orElseThrow(() -> new AccessDeniedException("Active employee required"));
        if (!roles.isActiveSystemAdministrator(id) || !roles.userHasPermission(id, authority)) {
            throw new AccessDeniedException("Active system ADMIN role required");
        }
        return id;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list(Authentication auth) {
        requireAdmin(auth, "ROLES_READ");
        return jdbc.sql("SELECT id FROM security_roles ORDER BY code")
                .query(UUID.class).list().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(Authentication auth, UUID roleId) {
        requireAdmin(auth, "ROLES_READ");
        return view(roleId);
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissions(Authentication auth) {
        requireAdmin(auth, "ROLES_READ");
        return jdbc.sql("SELECT code, description FROM security_permissions ORDER BY code")
                .query((rs, row) -> {
                    String code = rs.getString("code");
                    String group = code.startsWith("DAILY_CLOSING_") ? "DAILY_CLOSING"
                            : code.startsWith("ROLES_") || code.startsWith("USERS_")
                              || code.equals("ADMIN_ACCESS") ? "ADMINISTRATION"
                            : code.startsWith("PAYMENTS_") || code.startsWith("REFUNDS_")
                              || code.startsWith("EXCHANGES_") ? "FINANCE"
                            : code.startsWith("DELIVERIES_") ? "DELIVERY"
                            : code.startsWith("PURCHASE_") ? "PURCHASES"
                            : code.startsWith("RETURNS_") ? "RETURNS"
                            : code.startsWith("SALES_") ? "SALES"
                            : code.startsWith("CUSTOMERS_") ? "CUSTOMERS"
                            : code.startsWith("INVENTORY_") || code.startsWith("WAREHOUSES_") ? "INVENTORY"
                            : code.startsWith("CATALOG_") || code.startsWith("SUPPLIERS_") ? "CATALOG"
                            : "FINANCE";
                    boolean sensitive = Set.of("PAYMENTS_CANCEL", "REFUNDS_CREATE",
                            "FINANCE_TRANSFER", "FINANCE_INITIALIZE", "DEFECTS_WRITE_OFF")
                            .contains(code);
                    return new PermissionView(code, rs.getString("description"), group,
                            sensitive, adminOnly(code));
                }).list();
    }

    @Transactional(readOnly = true)
    public AuditPage audit(Authentication auth, UUID targetId, int page, int size) {
        requireAdmin(auth, "ROLES_READ");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Некорректные параметры журнала аудита");
        }
        String filter = targetId == null ? "" : " WHERE target_id = :target";
        var query = jdbc.sql("""
                SELECT id,actor_user_id,action,target_type,target_id,details,created_at
                FROM security_audit_log
                """ + filter + " ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",(long)page*size);
        if(targetId!=null)query=query.param("target",targetId);
        List<AuditLine> rows=query.query((rs,n)->new AuditLine(
                rs.getObject("id",UUID.class),rs.getObject("actor_user_id",UUID.class),
                rs.getString("action"),rs.getString("target_type"),
                rs.getObject("target_id",UUID.class),rs.getString("details"),
                rs.getTimestamp("created_at").toInstant())).list();
        var count=jdbc.sql("SELECT COUNT(*) FROM security_audit_log" + filter);
        if(targetId!=null)count=count.param("target",targetId);
        return new AuditPage(rows,page,size,count.query(Long.class).single());
    }

    @Transactional
    public RoleView create(Authentication auth, CreateRole command) {
        UUID actor = requireAdmin(auth, "ROLES_CREATE");
        roles.lockRoleAssignments();
        // Recheck after the lock, because permissions or membership may have changed.
        requireAdmin(auth, "ROLES_CREATE");
        if (command == null) throw new IllegalArgumentException("Данные роли обязательны");
        String code = command.code() == null ? "" : command.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_]{2,99}")
                || Set.of("ADMIN", "MANAGER", "EMPLOYEE").contains(code)) {
            throw new IllegalArgumentException("Недопустимый код пользовательской роли");
        }
        String name = checkedName(command.name());
        List<String> permissions = checkedPermissions(command.permissions(), false);
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO security_roles(id, code, name, system_role, version) VALUES (:id,:code,:name,FALSE,0)")
                .param("id", id).param("code", code).param("name", name).update();
        replacePermissions(id, permissions);
        audit.record(actor, "ROLE_CREATED", "ROLE", id,
                "code=" + code + "; permissions=" + permissions);
        return view(id);
    }

    @Transactional
    public RoleView update(Authentication auth, UUID roleId, UpdateRole command) {
        UUID actor = requireAdmin(auth, "ROLES_UPDATE");
        roles.lockRoleAssignments();
        requireAdmin(auth, "ROLES_UPDATE");
        if (command == null || command.expectedVersion() == null
                || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("expectedVersion обязателен и должен быть >= 0");
        }
        String reason = checkedReason(command.reason());
        String name = checkedName(command.name());
        RoleView old = view(roleId);
        if (old.code().equals("ADMIN") || (old.systemRole()
                && !Set.of("MANAGER", "EMPLOYEE").contains(old.code()))) {
            throw new IllegalArgumentException("Системную роль ADMIN изменять нельзя");
        }
        List<String> permissions = checkedPermissions(command.permissions(), old.code().equals("ADMIN"));
        if (old.version() != command.expectedVersion()) {
            throw new RoleVersionConflictException("Роль была изменена другим администратором");
        }
        int changed = jdbc.sql("""
                UPDATE security_roles SET name=:name,version=version+1
                WHERE id=:id AND version=:version AND code <> 'ADMIN'
                """).param("name", name).param("id", roleId)
                .param("version", command.expectedVersion()).update();
        if (changed != 1) throw new RoleVersionConflictException("Устаревшая версия роли");
        replacePermissions(roleId, permissions);
        audit.record(actor, "ROLE_UPDATED", "ROLE", roleId,
                "name: " + old.name() + " -> " + name + "; reason=" + reason);
        if (!old.permissions().equals(permissions)) {
            audit.record(actor, "ROLE_PERMISSIONS_CHANGED", "ROLE", roleId,
                    "old=" + old.permissions() + "; new=" + permissions + "; reason=" + reason);
        }
        return view(roleId);
    }

    private void replacePermissions(UUID id, List<String> permissions) {
        jdbc.sql("DELETE FROM security_role_permissions WHERE role_id=:id")
                .param("id", id).update();
        for (String code : permissions) {
            jdbc.sql("INSERT INTO security_role_permissions(role_id,permission_code) VALUES (:id,:code)")
                    .param("id", id).param("code", code).update();
        }
    }

    private List<String> checkedPermissions(List<String> requested, boolean allowAdmin) {
        if (requested == null) throw new IllegalArgumentException("permissions обязательны");
        if (requested.stream().anyMatch(p -> p == null || p.isBlank())
                || new HashSet<>(requested).size() != requested.size()) {
            throw new IllegalArgumentException("Пустые и повторяющиеся permissions запрещены");
        }
        Set<String> existing = new HashSet<>(jdbc.sql("SELECT code FROM security_permissions")
                .query(String.class).list());
        for (String code : requested) {
            if (!existing.contains(code) || code.equals("CATALOG_ACCESS")
                    || (!allowAdmin && adminOnly(code))) {
                throw new IllegalArgumentException("Недопустимое разрешение: " + code);
            }
        }
        return requested.stream().sorted().toList();
    }

    private String checkedName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 200) {
            throw new IllegalArgumentException("Название роли обязательно (до 200 символов)");
        }
        return value.strip();
    }

    private String checkedReason(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw new IllegalArgumentException("Причина изменения обязательна (до 500 символов)");
        }
        return value.strip();
    }

    private RoleView view(UUID id) {
        var row = jdbc.sql("SELECT id,code,name,system_role,version FROM security_roles WHERE id=:id")
                .param("id", id).query((rs, n) -> new RoleView(
                        rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("system_role"),
                        List.of(), 0L, rs.getLong("version")))
                .optional().orElseThrow(() -> new IllegalArgumentException("Роль не найдена"));
        List<String> perms = jdbc.sql("""
                SELECT permission_code FROM security_role_permissions
                WHERE role_id=:id ORDER BY permission_code
                """).param("id", id).query(String.class).list();
        long users = jdbc.sql("SELECT COUNT(*) FROM security_user_roles WHERE role_id=:id")
                .param("id", id).query(Long.class).single();
        return new RoleView(row.id(), row.code(), row.name(), row.systemRole(),
                perms, users, row.version());
    }

    public static class RoleVersionConflictException extends RuntimeException {
        public RoleVersionConflictException(String message) { super(message); }
    }
}
