package thienloc.manage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import thienloc.manage.entity.Role;
import thienloc.manage.service.RoleService;

@Controller
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/")
    public String listRoles(Model model) {
        model.addAttribute("roles", roleService.findAllRoles());
        return "admin/roles";
    }

    @GetMapping("/{id}/permissions")
    public String editPermissions(@PathVariable Long id, Model model) {
        Role role = roleService.findById(id);
        model.addAttribute("role", role);
        model.addAttribute("features", roleService.findAllFeatures());
        return "admin/role-permissions";
    }
}
