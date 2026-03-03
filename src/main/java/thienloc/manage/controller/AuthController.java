package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import thienloc.manage.entity.User;
import thienloc.manage.service.UserService;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(User user, Model model) {
        if (userService.findByUsername(user.getUsername()) != null) {
            model.addAttribute("error", "Username already exists.");
            return "register";
        }
        userService.registerUser(user);
        systemLogService.logAction("REGISTER", "New user registered: " + user.getUsername());
        return "redirect:/login?success";
    }
}
