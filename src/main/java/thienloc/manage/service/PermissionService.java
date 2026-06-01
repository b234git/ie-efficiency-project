package thienloc.manage.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import thienloc.manage.entity.Feature;
import thienloc.manage.entity.Role;
import thienloc.manage.entity.User;
import thienloc.manage.entity.UserFeatureOverride;
import thienloc.manage.repository.FeatureRepository;
import thienloc.manage.repository.RoleRepository;
import thienloc.manage.repository.UserFeatureOverrideRepository;
import thienloc.manage.repository.UserRepository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Caches role -> feature-keys, effective per-user feature sets (role + overrides),
 * and (METHOD,path) -> featureKey lookups. Provides fast authorization checks
 * keyed by username; reload() invalidates all caches after any permission change.
 */
@Service
public class PermissionService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFeatureOverrideRepository overrideRepository;

    private final AntPathMatcher matcher = new AntPathMatcher();

    private volatile Map<String, Set<String>> rolePermissions = Collections.emptyMap();
    private volatile List<CachedFeature> features = Collections.emptyList();

    // Per-user effective-feature set. Sized for hundreds-of-users scale; TTL
    // bounded so dormant users don't pin memory and stale grants self-heal
    // even if invalidateUser was missed somewhere.
    private final Cache<String, Set<String>> userPermissionsCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    // (METHOD ":" path) -> matching featureKey ("" sentinel = no match).
    // AntPathMatcher across ~18 features × ~2 patterns each runs on every
    // authorized request, so caching is a measurable CPU win at any traffic.
    private final Cache<String, String> featureKeyCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    private static final String NO_MATCH = "";

    @PostConstruct
    public void init() {
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Role r : roleRepository.findAll()) {
            Set<String> keys = r.getFeatures().stream()
                    .map(Feature::getFeatureKey)
                    .collect(Collectors.toSet());
            map.put(r.getName(), keys);
        }
        this.rolePermissions = Map.copyOf(map);

        List<CachedFeature> list = featureRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Feature::getPriority).reversed())
                .map(f -> new CachedFeature(
                        f.getFeatureKey(),
                        splitCsv(f.getUrlPatterns()),
                        splitCsvUpper(f.getHttpMethods())))
                .toList();
        this.features = list;

        userPermissionsCache.invalidateAll();
        featureKeyCache.invalidateAll();
    }

    /** First feature (highest priority) whose URL pattern + method matches the request. */
    public Optional<String> findFeatureKey(String path, String method) {
        if (path == null) return Optional.empty();
        String upperMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String cacheKey = upperMethod + ":" + path;
        String cached = featureKeyCache.get(cacheKey, k -> resolveFeatureKey(path, upperMethod));
        return NO_MATCH.equals(cached) ? Optional.empty() : Optional.of(cached);
    }

    private String resolveFeatureKey(String path, String upperMethod) {
        for (CachedFeature f : features) {
            if (!f.methods.isEmpty() && !f.methods.contains(upperMethod)) continue;
            for (String pattern : f.patterns) {
                if (matcher.match(pattern, path)) return f.key;
            }
        }
        return NO_MATCH;
    }

    public boolean roleHasFeature(String roleName, String featureKey) {
        if (roleName == null || featureKey == null) return false;
        Set<String> keys = rolePermissions.get(roleName);
        return keys != null && keys.contains(featureKey);
    }

    /** True if user (by username) has access to the given feature, considering role + overrides. */
    public boolean userHasFeature(String username, String featureKey) {
        if (username == null || featureKey == null) return false;
        Set<String> set = userPermissionsCache.get(username, this::loadEffectiveFeatures);
        return set != null && set.contains(featureKey);
    }

    /** Effective feature-key set for a user — role grants merged with their overrides. */
    @Transactional(readOnly = true)
    public Set<String> effectiveFeatureKeys(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return Set.of();
        return computeEffective(user);
    }

    public void invalidateUser(String username) {
        if (username != null) userPermissionsCache.invalidate(username);
    }

    /** When a user is renamed: drop the stale username from cache. */
    public void renameUser(String oldUsername, String newUsername) {
        if (oldUsername != null) userPermissionsCache.invalidate(oldUsername);
        if (newUsername != null) userPermissionsCache.invalidate(newUsername);
    }

    public Map<String, Set<String>> snapshotRoles() {
        return rolePermissions;
    }

    @Transactional(readOnly = true)
    protected Set<String> loadEffectiveFeatures(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return Set.of();
        return computeEffective(user);
    }

    private Set<String> computeEffective(User user) {
        Set<String> result = new HashSet<>(
                rolePermissions.getOrDefault(user.getRole(), Set.of()));
        for (UserFeatureOverride ov : overrideRepository.findByUserId(user.getId())) {
            String key = ov.getFeature().getFeatureKey();
            if (ov.isGranted()) result.add(key);
            else result.remove(key);
        }
        return Set.copyOf(result);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Set<String> splitCsvUpper(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private record CachedFeature(String key, List<String> patterns, Set<String> methods) {}
}
