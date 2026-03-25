package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import thienloc.manage.service.UserService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    @GetMapping("/")
    public String adminDashboard(Model model,
            @RequestParam(defaultValue = "0") int logPage) {
        model.addAttribute("users", userService.findAllUsers());
        model.addAttribute("logsPage", systemLogService.getLogsPage(
                PageRequest.of(logPage, 20)));
        return "admin";
    }

    @PostMapping("/update-role")
    public String updateRole(@RequestParam Long userId, @RequestParam String newRole) {
        userService.updateRole(userId, newRole);
        systemLogService.logAction("UPDATE_ROLE", "User ID " + userId + " changed to role: " + newRole);
        return "redirect:/admin/?success";
    }
}
