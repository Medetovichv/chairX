package kg.chairx.security.application;

import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Local-profile-only, one-time development users.
 * Uses existing audited user-management and role-assignment boundaries.
 * Existing users and their passwords/roles are NEVER modified.
 */
@Service
@Profile("local")
public class LocalDevUsersService {
    private static final UUID MANAGER_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMPLOYEE_ROLE =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final AppUserRepository users;
    private final SecurityRoleRepository roles;
    private final UserManagementService userManagement;
    private final RoleAssignmentService roleAssignment;

    public LocalDevUsersService(
            AppUserRepository users,
            SecurityRoleRepository roles,
            UserManagementService userManagement,
            RoleAssignmentService roleAssignment
    ) {
        this.users = users;
        this.roles = roles;
        this.userManagement = userManagement;
        this.roleAssignment = roleAssignment;
    }

    @Transactional
    public void ensureUsers(String adminUsername, String managerPassword, String employeePassword) {
        validatePassword(managerPassword, "manager");
        validatePassword(employeePassword, "employee");

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException("Local administrator username is required");
        }

        // Prevent concurrent initialization and preserve role lock order.
        roles.lockRoleAssignments();

        // A previously initialized database may have an administrator under
        // a different login. Reuse that active administrator solely as an
        // authorized actor; never reset their password or create a second ADMIN.
        var configuredAdmin = users.findByUsername(adminUsername.trim().toLowerCase(java.util.Locale.ROOT));
        UUID adminId;
        if (configuredAdmin.isPresent()) {
            var admin = configuredAdmin.orElseThrow();
            if (!admin.active() || !roles.findRoleCodesByUserId(admin.id()).contains("ADMIN")) {
                throw new IllegalStateException(
                        "Configured local administrator account is not an active ADMIN; existing account unchanged");
            }
            adminId = admin.id();
        } else {
            adminId = roles.findFirstActiveAdministratorId()
                    .orElseThrow(() -> new IllegalStateException(
                            "No active ADMIN account in existing database; local seed refused. "
                            + "Restore an authorized administrator without deleting database data"));
        }

        if (!roles.existsById(MANAGER_ROLE) || !roles.existsById(EMPLOYEE_ROLE)) {
            throw new IllegalStateException("Local development roles are missing from migrations");
        }

        ensureOne(adminId, "manager", "Local Manager", managerPassword, "MANAGER", MANAGER_ROLE);
        ensureOne(adminId, "employee", "Local Employee", employeePassword, "EMPLOYEE", EMPLOYEE_ROLE);
    }

    private void ensureOne(
            UUID actorId, String username, String displayName, String password,
            String roleCode, UUID roleId
    ) {
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            var user = existing.orElseThrow();
            if (!user.active() || !roles.findRoleCodesByUserId(user.id()).equals(Set.of(roleCode))) {
                throw new IllegalStateException(
                        "Local user '" + username + "' already exists with unexpected status/roles; not modified");
            }
            return;
        }

        var userId = userManagement.createUser(
                actorId, new CreateUserCommand(username, password, displayName));
        if (!roleAssignment.assignRole(actorId, userId, roleId)) {
            throw new IllegalStateException("Failed to assign local development role for " + username);
        }
    }

    private static void validatePassword(String password, String account) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException(
                    "Local " + account + " password must contain 12-128 characters");
        }
    }
}

