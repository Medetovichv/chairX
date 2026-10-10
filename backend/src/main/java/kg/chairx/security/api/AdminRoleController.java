package kg.chairx.security.api;

import kg.chairx.security.application.RoleManagementService;
import kg.chairx.security.application.RoleManagementService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** System administrators configure global roles; existing user-role APIs remain untouched. */
@RestController
@RequestMapping("/api/admin")
public class AdminRoleController {
    private final RoleManagementService service;
    public AdminRoleController(RoleManagementService service) { this.service = service; }

    @GetMapping("/roles")
    public List<RoleView> list(Authentication auth) { return service.list(auth); }

    @GetMapping("/roles/{id}")
    public RoleView get(@PathVariable UUID id, Authentication auth) {
        return service.get(auth, id);
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleView> create(@RequestBody CreateRole request, Authentication auth) {
        RoleView role = service.create(auth, request);
        return ResponseEntity.created(URI.create("/api/admin/roles/" + role.id())).body(role);
    }

    @PutMapping("/roles/{id}")
    public RoleView update(@PathVariable UUID id, @RequestBody UpdateRole request,
                           Authentication auth) {
        return service.update(auth, id, request);
    }

    @GetMapping("/audit")
    public AuditPage audit(@RequestParam(required=false) UUID targetId,
                           @RequestParam(defaultValue="0") int page,
                           @RequestParam(defaultValue="20") int size,
                           Authentication auth) {
        return service.audit(auth, targetId, page, size);
    }

    @GetMapping("/permissions")
    public List<PermissionView> permissions(Authentication auth) {
        return service.permissions(auth);
    }
}
