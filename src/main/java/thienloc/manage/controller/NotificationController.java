package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.service.NotificationService;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public String list(Authentication auth, Model model) {
        String role = getPrimaryRole(auth);
        model.addAttribute("notifications", notificationService.getAll(role));
        return "notifications";
    }

    @PostMapping("/read/{id}")
    public String markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return "redirect:/notifications";
    }

    @PostMapping("/dismiss-all")
    public String dismissAll(Authentication auth, RedirectAttributes redirectAttributes) {
        String role = getPrimaryRole(auth);
        notificationService.dismissAll(role);
        redirectAttributes.addFlashAttribute("success", "All notifications marked as read.");
        return "redirect:/notifications";
    }

    private String getPrimaryRole(Authentication auth) {
        if (auth == null) return "ROLE_USER";
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_MANAGER".equals(role)) {
                return role;
            }
        }
        return "ROLE_USER";
    }
}
