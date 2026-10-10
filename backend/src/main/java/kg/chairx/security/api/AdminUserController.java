package kg.chairx.security.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import kg.chairx.security.application.CreateUserCommand;
import kg.chairx.security.application.RoleAssignmentService;
import kg.chairx.security.application.UserManagementService;
import kg.chairx.security.persistence.AppUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final UserManagementService userManagement;
    private final RoleAssignmentService roleAssignment;

    public AdminUserController(JdbcClient jdbc, AppUserRepository users,
            UserManagementService userManagement, RoleAssignmentService roleAssignment) {
        this.jdbc = jdbc;
        this.users = users;
        this.userManagement = userManagement;
        this.roleAssignment = roleAssignment;
    }

    public record UserResponse(UUID id, String username, String displayName, boolean active,
                               List<String> roles) {}
    public record RoleResponse(UUID id, String code, String name) {}
    public record CreateUserRequest(String username, String password, String displayName) {}
    public record AssignRoleRequest(UUID roleId) {}

    private UUID actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        return users.findByUsername(authentication.getName())
                .filter(AppUserRepository.AppUserRecord::active)
                .map(AppUserRepository.AppUserRecord::id)
                .orElseThrow(() -> new AccessDeniedException("Employee account required"));
    }

    private List<String> roleCodes(UUID userId) {
        return jdbc.sql("""
                SELECT r.code FROM security_roles r
                JOIN security_user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = :id ORDER BY r.code
                """).param("id", userId).query(String.class).list();
    }

    private UserResponse toResponse(AppUserRepository.AppUserRecord user) {
        return new UserResponse(user.id(), user.username(), user.displayName(),
                user.active(), roleCodes(user.id()));
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return jdbc.sql("""
                SELECT id, username, display_name, active FROM app_users ORDER BY username
                """).query((rs, row) -> new UserResponse(
                        rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("display_name"), rs.getBoolean("active"),
                        roleCodes(rs.getObject("id", UUID.class)))).list();
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return users.findById(id).map(this::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        UUID id = userManagement.createUser(actorId(),
                new CreateUserCommand(request.username(), request.password(), request.displayName()));
        var user = users.findById(id).orElseThrow();
        return ResponseEntity.created(URI.create("/api/admin/users/" + id)).body(toResponse(user));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userManagement.deactivateUser(actorId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/roles")
    public ResponseEntity<Void> assignRole(@PathVariable UUID id, @RequestBody AssignRoleRequest request) {
        if (request.roleId() == null) throw new IllegalArgumentException("roleId is required");
        roleAssignment.assignRole(actorId(), id, request.roleId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    public ResponseEntity<Void> removeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        roleAssignment.removeRole(actorId(), id, roleId);
        return ResponseEntity.noContent().build();
    }
}
