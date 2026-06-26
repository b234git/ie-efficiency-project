package thienloc.manage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import thienloc.manage.service.NotificationService;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(Authentication auth, Model model) {
        String role = getPrimaryRole(auth);
        model.addAttribute("notifications", notificationService.getAll(role));
        return "notifications";
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
