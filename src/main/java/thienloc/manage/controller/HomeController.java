package thienloc.manage.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if (auth.getAuthority().equals("ROLE_ADMIN")) {
                    return "redirect:/admin/";
                }
                if (auth.getAuthority().equals("ROLE_MANAGER")) {
                    return "redirect:/dashboard/";
                }
                if (auth.getAuthority().equals("ROLE_USER")) {
                    return "redirect:/split-entry/";
                }
            }
        }
        return "redirect:/login";
    }
}
