package thienloc.manage.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String home(Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }
        model.addAttribute("username", authentication.getName());
        return "home";
    }
}
