package thienloc.manage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.entity.User;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.UserRepository;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
            System.out.println("Created default admin user (admin/admin)");
        }

        // Create Manager if not exists
        if (userRepository.findByUsername("manager").isEmpty()) {
            User manager = User.builder()
                    .username("manager")
                    .password(passwordEncoder.encode("manager"))
                    .role("ROLE_MANAGER")
                    .build();
            userRepository.save(manager);
            System.out.println("Created default manager user (manager/manager)");
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
            System.out.println("Loaded Sample MasterDb data.");
        }
    }
}
