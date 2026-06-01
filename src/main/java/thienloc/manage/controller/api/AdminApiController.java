package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.entity.User;
import thienloc.manage.service.AdminService;
import thienloc.manage.service.LineAssignmentService;
import thienloc.manage.service.LineAssignmentService.SectionLine;
import thienloc.manage.service.UserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final UserService userService;
    private final AdminService adminService;
    private final LineAssignmentService lineAssignmentService;

    public AdminApiController(UserService userService, AdminService adminService,
                              LineAssignmentService lineAssignmentService) {
        this.userService = userService;
        this.adminService = adminService;
        this.lineAssignmentService = lineAssignmentService;
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable Long id, @RequestBody RoleUpdateRequest body) {
        User saved = userService.updateRole(id, body.role());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/users/{id}/username")
    public ResponseEntity<User> updateUsername(@PathVariable Long id, @RequestBody UsernameUpdateRequest body) {
        User saved = userService.updateUsername(id, body.username());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @RequestBody PasswordUpdateRequest body) {
        userService.resetPassword(id, body.password());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<User> setEnabled(@PathVariable Long id, @RequestBody EnabledUpdateRequest body) {
        User saved = userService.setEnabled(id, body.enabled());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails caller) {
        User target = userService.findById(id);
        if (caller != null && target.getUsername().equals(caller.getUsername())) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/permissions")
    public ResponseEntity<Void> updatePermissions(@PathVariable Long id,
                                                  @RequestBody PermissionsUpdateRequest body) {
        userService.updateOverrides(id, body.overrides());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/line-assignments")
    public ResponseEntity<Void> updateLineAssignments(@PathVariable Long id,
                                                       @RequestBody LineAssignmentsUpdateRequest body) {
        lineAssignmentService.setAssignments(id, body.assignments());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/force-delete")
    public ResponseEntity<AdminService.ForceDeleteResult> forceDelete(@RequestBody ForceDeleteRequest body) {
        AdminService.ForceDeleteResult result = adminService.forceDeleteByIds(body.source(), body.ids());
        return ResponseEntity.ok(result);
    }

    public record RoleUpdateRequest(String role) {}
    public record UsernameUpdateRequest(String username) {}
    public record PasswordUpdateRequest(String password) {}
    public record EnabledUpdateRequest(boolean enabled) {}
    public record PermissionsUpdateRequest(Map<String, Boolean> overrides) {}
    public record ForceDeleteRequest(String source, List<Long> ids) {}
    public record LineAssignmentsUpdateRequest(List<SectionLine> assignments) {}
}
