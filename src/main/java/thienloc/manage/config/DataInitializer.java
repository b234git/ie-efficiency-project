package thienloc.manage.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import thienloc.manage.entity.User;
import thienloc.manage.repository.UserRepository;

@Component
@RequiredArgsConstructor
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.default-admin-password:}")
    private String defaultAdminPassword;

    @Value("${app.default-manager-password:}")
    private String defaultManagerPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()
                || defaultManagerPassword == null || defaultManagerPassword.isBlank()) {
            throw new IllegalStateException(
                    "Seed user passwords are not configured. Set app.default-admin-password "
                            + "and app.default-manager-password (env: APP_DEFAULT_ADMIN_PASSWORD, "
                            + "APP_DEFAULT_MANAGER_PASSWORD) before first startup.");
        }
        createUserIfNotExists("admin", "ROLE_ADMIN", defaultAdminPassword);
        createUserIfNotExists("manager", "ROLE_MANAGER", defaultManagerPassword);
    }

    private void createUserIfNotExists(String username, String role, String rawPassword) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .build());
        }
    }
}
