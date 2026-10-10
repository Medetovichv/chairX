package kg.chairx.security.api;

import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Authentication is HTTP Basic; identity, roles and permissions come only from PostgreSQL. */
@RestController
public class CurrentUserController {
    private final AppUserRepository users;
    private final SecurityRoleRepository roles;

    public CurrentUserController(AppUserRepository users, SecurityRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    public record CurrentUser(UUID id, String username, String displayName,
                              List<String> roles, List<String> permissions) { }

    @GetMapping("/api/auth/me")
    @Transactional(readOnly = true)
    public CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        // Technical 'catalog' is not a database employee, and a disabled employee
        // cannot authenticate through ChairxUserDetailsService.
        var employee = users.findByUsername(authentication.getName())
                .filter(AppUserRepository.AppUserRecord::active)
                .orElseThrow(() -> new AccessDeniedException("Active employee account required"));

        List<String> roleCodes = roles.findRoleCodesByUserId(employee.id()).stream().sorted().toList();
        if (roleCodes.isEmpty()) {
            throw new AccessDeniedException("Employee has no assigned role");
        }
        List<String> permissions = roles.findPermissionsByUserId(employee.id()).stream().sorted().toList();
        return new CurrentUser(employee.id(), employee.username(),
                employee.displayName(), roleCodes, permissions);
    }
}
