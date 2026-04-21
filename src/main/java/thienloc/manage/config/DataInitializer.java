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

    @Value("${app.default-admin-password:Admin@123}")
    private String defaultAdminPassword;

    @Value("${app.default-manager-password:Manager@123}")
    private String defaultManagerPassword;

    @Override
    public void run(ApplicationArguments args) {
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
