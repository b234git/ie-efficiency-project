package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.entity.User;
import thienloc.manage.service.AdminService;
import thienloc.manage.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final UserService userService;
    private final AdminService adminService;

    public AdminApiController(UserService userService, AdminService adminService) {
        this.userService = userService;
        this.adminService = adminService;
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<User> updateRole(@PathVariable Long id, @RequestBody RoleUpdateRequest body) {
        User saved = userService.updateRole(id, body.role());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/force-delete")
    public ResponseEntity<AdminService.ForceDeleteResult> forceDelete(@RequestBody ForceDeleteRequest body) {
        AdminService.ForceDeleteResult result = adminService.forceDeleteByIds(body.source(), body.ids());
        return ResponseEntity.ok(result);
    }

    public record RoleUpdateRequest(String role) {}

    public record ForceDeleteRequest(String source, List<Long> ids) {}
}
