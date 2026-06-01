package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.entity.Role;
import thienloc.manage.service.RoleService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleApiController {

    private final RoleService roleService;

    public RoleApiController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> list() {
        List<RoleResponse> body = roleService.findAllRoles().stream()
                .map(RoleResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@RequestBody CreateRoleRequest req) {
        Role created = roleService.createRole(req.name(), req.displayName());
        return ResponseEntity.ok(RoleResponse.from(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<RoleResponse> updatePermissions(@PathVariable Long id,
                                                          @RequestBody UpdatePermissionsRequest req) {
        Role saved = roleService.updatePermissions(id, req.featureIds());
        return ResponseEntity.ok(RoleResponse.from(saved));
    }

    public record CreateRoleRequest(String name, String displayName) {}

    public record UpdatePermissionsRequest(Set<Long> featureIds) {}

    public record RoleResponse(Long id, String name, String displayName, boolean system, Set<Long> featureIds) {
        static RoleResponse from(Role r) {
            return new RoleResponse(
                    r.getId(),
                    r.getName(),
                    r.getDisplayName(),
                    r.isSystem(),
                    r.getFeatures().stream().map(f -> f.getId()).collect(java.util.stream.Collectors.toSet()));
        }
    }
}
