package thienloc.manage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import thienloc.manage.entity.User;
import thienloc.manage.service.LineAssignmentService;
import thienloc.manage.service.PermissionService;
import thienloc.manage.service.RoleService;
import thienloc.manage.service.UserService;
import thienloc.manage.repository.UserFeatureOverrideRepository;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    private final thienloc.manage.service.SystemLogService systemLogService;

    private final RoleService roleService;

    private final PermissionService permissionService;

    private final UserFeatureOverrideRepository overrideRepository;

    private final LineAssignmentService lineAssignmentService;

    @GetMapping("/")
    public String adminDashboard(Model model) {
        model.addAttribute("users", userService.findAllUsers());
        model.addAttribute("roles", roleService.findAllRoles());
        return "admin";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/logs")
    public String systemLogs(Model model,
            @RequestParam(defaultValue = "0") int logPage) {
        model.addAttribute("logsPage", systemLogService.getLogsPage(
                PageRequest.of(logPage, 20)));
        return "admin/logs";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/force-delete")
    public String forceDeleteForm() {
        return "admin/force-delete";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        User user = userService.findById(id);

        Map<String, Boolean> overrides = new HashMap<>();
        overrideRepository.findByUserId(id).forEach(o ->
                overrides.put(o.getFeature().getFeatureKey(), o.isGranted()));

        java.util.Set<String> roleFeatures = permissionService.snapshotRoles()
                .getOrDefault(user.getRole(), java.util.Set.of());

        model.addAttribute("user", user);
        model.addAttribute("roles", roleService.findAllRoles());
        model.addAttribute("features", roleService.findAllFeatures());
        model.addAttribute("overrides", overrides);
        model.addAttribute("roleFeatures", roleFeatures);
        model.addAttribute("lineAssignments", lineAssignmentService.getAssignments(id));
        model.addAttribute("availableLines", lineAssignmentService.availableSectionLines());
        return "admin/user-edit";
    }
}
