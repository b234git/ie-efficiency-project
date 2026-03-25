package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import thienloc.manage.service.NotificationService;

@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private NotificationService notificationService;

    @ModelAttribute("notificationCount")
    public long notificationCount(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return 0;

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_MANAGER".equals(role)) {
                return notificationService.getUnreadCount(role);
            }
        }
        return 0;
    }
}
