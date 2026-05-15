package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/force-delete")
    public String forceDeleteForm() {
        return "admin/force-delete";
    }
}
