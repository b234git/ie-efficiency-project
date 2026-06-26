package thienloc.manage.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import thienloc.manage.entity.Feature;
import thienloc.manage.entity.Role;
import thienloc.manage.repository.FeatureRepository;
import thienloc.manage.repository.RoleRepository;
import thienloc.manage.repository.UserFeatureOverrideRepository;
import thienloc.manage.repository.UserRepository;
import thienloc.manage.service.PermissionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared @WebMvcTest fixture for the feature-based authorization model.
 *
 * Production {@link FeatureBasedAuthorizationManager} resolves permissions by
 * username → DB, which @WebMvcTest slices cannot provide. The controller tests
 * instead express intent via {@code .with(user(..).roles("ADMIN"/"MANAGER"/"USER"))},
 * so this fixture supplies a manager that decides from the request's ROLE using
 * the exact V8 feature seed (mirrors V8__add_role_feature_permissions.sql).
 *
 * A real {@link PermissionService} (wired to seeded mock repositories) performs
 * the URL→feature resolution, so matching stays in lockstep with production.
 */
@TestConfiguration
public class TestRbacSecurityConfig {

    @Bean
    public RoleRepository roleRepository() {
        RoleRepository repo = mock(RoleRepository.class);
        when(repo.findAll()).thenReturn(seedRoles(seedFeatures()));
        return repo;
    }

    @Bean
    public FeatureRepository featureRepository() {
        FeatureRepository repo = mock(FeatureRepository.class);
        when(repo.findAll()).thenReturn(seedFeatures());
        return repo;
    }

    @Bean
    public UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    @Bean
    public UserFeatureOverrideRepository userFeatureOverrideRepository() {
        return mock(UserFeatureOverrideRepository.class);
    }

    /**
     * Real service so feature resolution mirrors prod; Spring injects the seeded
     * repository beans and runs @PostConstruct reload() to build the indexes.
     */
    @Bean
    public PermissionService permissionService(RoleRepository roleRepository,
                                               FeatureRepository featureRepository,
                                               UserRepository userRepository,
                                               UserFeatureOverrideRepository userFeatureOverrideRepository) {
        return new PermissionService(roleRepository, featureRepository, userRepository, userFeatureOverrideRepository);
    }

    @Bean
    public FeatureBasedAuthorizationManager featureBasedAuthorizationManager(PermissionService permissionService) {
        return new RoleAwareAuthorizationManager(permissionService);
    }

    /** Several controllers (@Timed paths) autowire a MeterRegistry. */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    // ── V8 seed (mirrors V8__add_role_feature_permissions.sql) ─────────────────

    private static List<Feature> seedFeatures() {
        List<Feature> f = new ArrayList<>();
        f.add(feature("SYSTEM_HEALTH", "/actuator/**,/api/v1/system-health/**", null, 900));
        f.add(feature("ADMIN", "/admin/**,/api/v1/admin/**", null, 900));
        f.add(feature("MASTERDB", "/masterdb/**,/api/v1/masterdb/**", null, 500));
        f.add(feature("DASHBOARD", "/dashboard/**,/api/v1/dashboard/**", null, 500));
        f.add(feature("REPORT", "/report/**,/api/v1/reports/**", null, 500));
        f.add(feature("ENTRY_MUTATE", "/entry/edit,/entry/admin-delete,/entry/delete", null, 800));
        f.add(feature("ENTRY", "/entry/**,/api/v1/entries/**", null, 400));
        f.add(feature("SPLIT_ENTRY_DELETE", "/split-entry/delete/**,/api/v1/split-entries/bulk-delete", "POST,DELETE", 800));
        f.add(feature("SPLIT_ENTRY_API_DELETE", "/api/v1/split-entries/**", "DELETE", 850));
        f.add(feature("SPLIT_ENTRY_OUTPUT", "/split-entry/output,/split-entry/output/**", null, 700));
        f.add(feature("SPLIT_ENTRY_ARTICLES", "/split-entry/articles,/split-entry/articles/**", null, 700));
        f.add(feature("SPLIT_ENTRY", "/split-entry/**,/api/v1/split-entries/**", null, 300));
        f.add(feature("NOTIFICATIONS", "/notifications/**,/api/v1/notifications/**", null, 500));
        f.add(feature("EFF_CONFIG", "/eff-config/**,/api/v1/eff-config/**", null, 500));
        f.add(feature("WEEKLY_TRACKING", "/weekly-tracking/**,/api/v1/weekly-tracking/**", null, 500));
        f.add(feature("NEW_STYLE", "/new-style/**,/api/v1/new-styles/**", null, 500));
        f.add(feature("SALARY", "/incentive/**,/api/v1/incentive/**", null, 500));
        f.add(feature("IMPORTS", "/api/v1/imports/**", null, 500));
        return f;
    }

    private static List<Role> seedRoles(List<Feature> all) {
        // ADMIN → all; MANAGER → all except SYSTEM_HEALTH+ADMIN; USER → only SPLIT_ENTRY.
        Set<Feature> admin = new HashSet<>(all);
        Set<Feature> manager = new HashSet<>();
        Set<Feature> user = new HashSet<>();
        for (Feature f : all) {
            if (!f.getFeatureKey().equals("SYSTEM_HEALTH") && !f.getFeatureKey().equals("ADMIN")) {
                manager.add(f);
            }
            if (f.getFeatureKey().equals("SPLIT_ENTRY")) {
                user.add(f);
            }
        }
        return List.of(
                role("ROLE_ADMIN", admin),
                role("ROLE_MANAGER", manager),
                role("ROLE_USER", user));
    }

    private static Feature feature(String key, String patterns, String methods, int priority) {
        return Feature.builder()
                .featureKey(key).displayName(key)
                .urlPatterns(patterns).httpMethods(methods)
                .priority(priority).build();
    }

    private static Role role(String name, Set<Feature> features) {
        return Role.builder().name(name).displayName(name).system(true).features(features).build();
    }

    /**
     * Builds a seeded {@link PermissionService} WITHOUT Spring (for @SpringBootTest, where
     * Flyway is disabled so the features table is empty). reload() runs on a plain instance,
     * so the @Transactional annotation is a no-op (no proxy) and needs no transaction manager.
     */
    public static PermissionService buildSeededPermissionService() {
        RoleRepository roleRepo = mock(RoleRepository.class);
        FeatureRepository featureRepo = mock(FeatureRepository.class);
        when(roleRepo.findAll()).thenReturn(seedRoles(seedFeatures()));
        when(featureRepo.findAll()).thenReturn(seedFeatures());

        PermissionService ps = new PermissionService(roleRepo, featureRepo,
                mock(UserRepository.class), mock(UserFeatureOverrideRepository.class));
        ps.reload();
        return ps;
    }

    // ── Role-driven manager ────────────────────────────────────────────────────

    /**
     * Decides from the authentication's ROLE authorities (what @WebMvcTest sets)
     * instead of a username→DB lookup, applying the same feature resolution as prod.
     */
    static final class RoleAwareAuthorizationManager extends FeatureBasedAuthorizationManager {

        private final PermissionService permissionService;

        RoleAwareAuthorizationManager(PermissionService permissionService) {
            super(permissionService);
            this.permissionService = permissionService;
        }

        @Override
        public AuthorizationDecision authorize(Supplier<? extends Authentication> authenticationSupplier,
                                               RequestAuthorizationContext context) {
            Authentication auth = authenticationSupplier.get();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return new AuthorizationDecision(false);
            }

            HttpServletRequest req = context.getRequest();
            String path = req.getServletPath();
            if (path == null || path.isEmpty()) {
                path = req.getRequestURI();
            }

            Optional<String> featureKey = permissionService.findFeatureKey(path, req.getMethod());
            if (featureKey.isEmpty()) {
                return new AuthorizationDecision(true); // unclaimed URL → any authenticated user
            }

            boolean granted = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role -> permissionService.roleHasFeature(role, featureKey.get()));
            return new AuthorizationDecision(granted);
        }
    }
}
