package thienloc.manage.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import thienloc.manage.entity.User;
import thienloc.manage.service.UserService;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    @Autowired
    private MeterRegistry meterRegistry;

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
    public String registerUser(@Valid User user, BindingResult result,
                               @RequestParam(defaultValue = "") String confirmPassword,
                               Model model,
                               HttpServletRequest request) {
        if (result.hasErrors()) {
            meterRegistry.counter("validation.errors", "form", "register").increment();
            return "register";
        }
        if (!user.getPassword().equals(confirmPassword)) {
            model.addAttribute("confirmError", "Mật khẩu nhập lại không khớp.");
            return "register";
        }
        if (userService.findByUsername(user.getUsername()) != null) {
            model.addAttribute("error", "Tên đăng nhập đã tồn tại.");
            return "register";
        }
        userService.registerUser(user);
        systemLogService.logAction("REGISTER", "New user registered: " + user.getUsername(), request);
        return "redirect:/login?success";
    }

    @GetMapping("/change-password")
    public String changePasswordPage() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "confirmMismatch");
            return "redirect:/change-password";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "tooShort");
            return "redirect:/change-password";
        }
        boolean success = userService.changePassword(authentication.getName(), currentPassword, newPassword);
        if (!success) {
            redirectAttributes.addFlashAttribute("error", "wrongCurrent");
            return "redirect:/change-password";
        }
        systemLogService.logAction("CHANGE_PASSWORD", "User changed password: " + authentication.getName(), request);
        redirectAttributes.addFlashAttribute("success", true);
        return "redirect:/change-password";
    }
}
