package thienloc.manage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.entity.User;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.UserRepository;

@Profile("dev")
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final MasterDbRepository masterDbRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserRepository userRepository,
                      MasterDbRepository masterDbRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.masterDbRepository = masterDbRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Create Admin if not exists
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .role("ROLE_ADMIN")
                    .build();
            userRepository.save(admin);
            log.info("Created default admin user (admin/admin)");
        }

        // Create Manager if not exists
        if (userRepository.findByUsername("manager").isEmpty()) {
            User manager = User.builder()
                    .username("manager")
                    .password(passwordEncoder.encode("manager"))
                    .role("ROLE_MANAGER")
                    .build();
            userRepository.save(manager);
            log.info("Created default manager user (manager/manager)");
        }

        // Add some dummy MasterDb data based on the Excel
        if (masterDbRepository.count() == 0) {
            masterDbRepository.save(MasterDb.builder()
                    .ref("SEW1A")
                    .articleNo("VN256L222")
                    .patternNo("2262")
                    .shoeName("GEL-LYTE III")
                    .sewCt(1904.65)
                    .sewQuotaDb(350.0)
                    .build());

            masterDbRepository.save(MasterDb.builder()
                    .ref("SEW1B")
                    .articleNo("VN256L223")
                    .patternNo("2262")
                    .shoeName("GEL-LYTE III")
                    .sewCt(2085.66)
                    .sewQuotaDb(350.0)
                    .build());
            log.info("Loaded Sample MasterDb data.");
        }
    }
}
