package thienloc.manage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.Feature;
import thienloc.manage.entity.User;
import thienloc.manage.entity.UserFeatureOverride;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.repository.FeatureRepository;
import thienloc.manage.repository.UserFeatureOverrideRepository;
import thienloc.manage.repository.UserLineAssignmentRepository;
import thienloc.manage.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final SystemLogService systemLogService;

    private final FeatureRepository featureRepository;

    private final UserFeatureOverrideRepository overrideRepository;

    private final UserLineAssignmentRepository lineAssignmentRepository;

    private final PermissionService permissionService;

    private final JdbcTemplate jdbcTemplate;

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("ROLE_USER");
        }
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateRole(Long id, String newRole) {
        User user = findById(id);
        user.setRole(newRole);
        User saved = userRepository.save(user);
        permissionService.invalidateUser(user.getUsername());
        systemLogService.logAction("UPDATE_ROLE", "User ID " + id + " changed to role: " + newRole);
        return saved;
    }

    @Transactional
    public User updateUsername(Long id, String newUsername) {
        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String trimmed = newUsername.trim();
        if (trimmed.length() < 3 || trimmed.length() > 50) {
            throw new IllegalArgumentException("Username must be between 3 and 50 characters");
        }
        User user = findById(id);
        String oldUsername = user.getUsername();
        if (!trimmed.equals(oldUsername) && userRepository.findByUsername(trimmed).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + trimmed);
        }
        user.setUsername(trimmed);
        User saved = userRepository.save(user);
        permissionService.renameUser(oldUsername, trimmed);
        systemLogService.logAction("RENAME_USER", "User ID " + id + ": " + oldUsername + " -> " + trimmed);
        return saved;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        User user = findById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        systemLogService.logAction("RESET_PASSWORD", "User ID " + id + " password reset by admin");
    }

    @Transactional
    public User setEnabled(Long id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        permissionService.invalidateUser(user.getUsername());
        systemLogService.logAction(enabled ? "ENABLE_USER" : "DISABLE_USER",
                "User ID " + id + " (" + user.getUsername() + ")");
        return saved;
    }

    /**
     * Completely deletes a user and every record that references them:
     *  • user_feature_overrides    — explicit grants/denies
     *  • split_entry.*_filled_by   — set to NULL (tracking columns, nullable)
     *  • daily_production_details  — cascade via daily_production
     *  • daily_production          — production rows owned by this user
     *  • users                     — the row itself
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);

        overrideRepository.deleteByUserId(id);
        lineAssignmentRepository.deleteByUserId(id);

        jdbcTemplate.update(
                "UPDATE split_entry SET manpower_filled_by = NULL WHERE manpower_filled_by = ?", id);
        jdbcTemplate.update(
                "UPDATE split_entry SET output_filled_by = NULL WHERE output_filled_by = ?", id);
        jdbcTemplate.update(
                "UPDATE split_entry SET article_filled_by = NULL WHERE article_filled_by = ?", id);

        int detailRows = jdbcTemplate.update(
                "DELETE FROM daily_production_details WHERE daily_production_id IN " +
                "(SELECT id FROM daily_production WHERE user_id = ?)", id);
        int prodRows = jdbcTemplate.update(
                "DELETE FROM daily_production WHERE user_id = ?", id);

        userRepository.delete(user);
        permissionService.invalidateUser(user.getUsername());
        systemLogService.logAction("DELETE_USER",
                "User ID " + id + " (" + user.getUsername() + ") — cascaded "
                        + prodRows + " production rows, " + detailRows + " details");
    }

    /**
     * Replace the user's feature overrides. The map is feature_key -> Boolean
     * (true = explicit grant, false = explicit deny). A feature_key absent
     * from the map means "inherit from role" (no override row).
     */
    @Transactional
    public void updateOverrides(Long userId, Map<String, Boolean> overrides) {
        User user = findById(userId);
        overrideRepository.deleteByUserId(userId);
        if (overrides == null || overrides.isEmpty()) {
            permissionService.invalidateUser(user.getUsername());
            return;
        }
        Map<String, Feature> byKey = new HashMap<>();
        for (Feature f : featureRepository.findAll()) byKey.put(f.getFeatureKey(), f);

        List<UserFeatureOverride> rows = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : overrides.entrySet()) {
            Feature f = byKey.get(e.getKey());
            if (f == null || e.getValue() == null) continue;
            rows.add(UserFeatureOverride.builder()
                    .userId(userId)
                    .feature(f)
                    .granted(e.getValue())
                    .build());
        }
        overrideRepository.saveAll(rows);
        permissionService.invalidateUser(user.getUsername());
        systemLogService.logAction("UPDATE_USER_PERMS",
                "User ID " + userId + " — " + rows.size() + " override(s) set");
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }
}
