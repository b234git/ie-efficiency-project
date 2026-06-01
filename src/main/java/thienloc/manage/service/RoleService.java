package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.Feature;
import thienloc.manage.entity.Role;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.repository.FeatureRepository;
import thienloc.manage.repository.RoleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SystemLogService systemLogService;

    @Transactional(readOnly = true)
    public List<Role> findAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Feature> findAllFeatures() {
        return featureRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    @Transactional
    public Role createRole(String rawName, String displayName) {
        String normalized = normalizeRoleName(rawName);
        if (roleRepository.existsByName(normalized)) {
            throw new IllegalArgumentException("Role already exists: " + normalized);
        }
        Role role = Role.builder()
                .name(normalized)
                .displayName(displayName == null || displayName.isBlank()
                        ? normalized.replace("ROLE_", "")
                        : displayName.trim())
                .system(false)
                .features(new HashSet<>())
                .build();
        Role saved = roleRepository.save(role);
        permissionService.reload();
        systemLogService.logAction("CREATE_ROLE", "Created role: " + normalized);
        return saved;
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = findById(id);
        if (role.isSystem()) {
            throw new IllegalArgumentException("Cannot delete system role: " + role.getName());
        }
        roleRepository.delete(role);
        permissionService.reload();
        systemLogService.logAction("DELETE_ROLE", "Deleted role: " + role.getName());
    }

    @Transactional
    public Role updatePermissions(Long roleId, Set<Long> featureIds) {
        Role role = findById(roleId);
        Set<Feature> chosen = new HashSet<>(featureRepository.findAllById(
                featureIds == null ? Set.of() : featureIds));
        role.setFeatures(chosen);
        Role saved = roleRepository.save(role);
        permissionService.reload();
        systemLogService.logAction("UPDATE_ROLE_PERMS",
                "Role " + role.getName() + " now has " + chosen.size() + " features");
        return saved;
    }

    private static String normalizeRoleName(String raw) {
        if (raw == null) throw new IllegalArgumentException("Role name is required");
        String trimmed = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Role name is required");
        if (!trimmed.startsWith("ROLE_")) trimmed = "ROLE_" + trimmed;
        if (!trimmed.matches("ROLE_[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Role name may only contain letters, digits, and underscore");
        }
        return trimmed;
    }
}
