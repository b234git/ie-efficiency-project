package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.User;
import thienloc.manage.entity.UserLineAssignment;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.SplitEntryRepository;
import thienloc.manage.repository.UserLineAssignmentRepository;
import thienloc.manage.repository.UserRepository;

import java.util.*;

/**
 * Data-level (row) authorization for production entry. A user can be assigned
 * specific (section, line) pairs — or a whole section (blank line). While they
 * have at least one assignment they may only enter/see those scopes; with none
 * they are unrestricted. ADMIN and MANAGER are always exempt.
 *
 * Endpoint/page access stays with {@link PermissionService}; this service only
 * governs WHICH section+line rows a user may write and view.
 */
@Service
public class LineAssignmentService {

    /** Roles that bypass line scoping entirely. */
    private static final Set<String> EXEMPT_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER");

    @Autowired
    private UserLineAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyProductionRepository dailyProductionRepository;

    @Autowired
    private SplitEntryRepository splitEntryRepository;

    @Autowired
    private SystemLogService systemLogService;

    /** A user's effective scope: what (section, line) they may write/view. */
    public static final class LineScope {
        private final boolean restricted;
        private final Set<String> wholeSections; // upper-cased section names
        private final Set<String> pairs;         // upper-cased "SECTION|LINE"

        private LineScope(boolean restricted, Set<String> wholeSections, Set<String> pairs) {
            this.restricted = restricted;
            this.wholeSections = wholeSections;
            this.pairs = pairs;
        }

        static LineScope unrestricted() {
            return new LineScope(false, Set.of(), Set.of());
        }

        public boolean isRestricted() {
            return restricted;
        }

        public boolean allows(String section, String line) {
            if (!restricted) return true;
            if (section == null) return false;
            String s = section.trim().toUpperCase(Locale.ROOT);
            if (wholeSections.contains(s)) return true;
            String l = line == null ? "" : line.trim().toUpperCase(Locale.ROOT);
            return pairs.contains(s + "|" + l);
        }
    }

    /** Build the scope for a username (one DB lookup, used per save and per list view). */
    @Transactional(readOnly = true)
    public LineScope scopeFor(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return LineScope.unrestricted();
        if (EXEMPT_ROLES.contains(user.getRole())) return LineScope.unrestricted();

        List<UserLineAssignment> rows = assignmentRepository.findByUserId(user.getId());
        if (rows.isEmpty()) return LineScope.unrestricted();

        Set<String> wholeSections = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (UserLineAssignment a : rows) {
            String s = a.getSection().trim().toUpperCase(Locale.ROOT);
            String l = a.getLine() == null ? "" : a.getLine().trim().toUpperCase(Locale.ROOT);
            if (l.isEmpty()) wholeSections.add(s);
            else pairs.add(s + "|" + l);
        }
        return new LineScope(true, wholeSections, pairs);
    }

    /** Throw if the user is not allowed to write this (section, line). */
    public void assertCanWrite(String username, String section, String line) {
        if (!scopeFor(username).allows(section, line)) {
            throw new AccessDeniedException(
                    "Bạn không được phân quyền nhập dữ liệu cho xưởng/chuyền này.");
        }
    }

    // ─── Admin management ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserLineAssignment> getAssignments(Long userId) {
        return assignmentRepository.findByUserId(userId);
    }

    /**
     * Replace all of a user's assignments. Each pair is (section, line); a blank
     * line means the whole section. Duplicates and blank sections are dropped.
     */
    @Transactional
    public void setAssignments(Long userId, List<SectionLine> pairs) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        assignmentRepository.deleteByUserId(userId);
        // Force the DELETE to hit the DB before the INSERTs below; Hibernate
        // otherwise orders inserts first within a flush and a re-save of an
        // unchanged (user, section, line) row trips uk_ula_user_section_line.
        assignmentRepository.flush();

        List<UserLineAssignment> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (pairs != null) {
            for (SectionLine p : pairs) {
                if (p == null || p.section() == null || p.section().isBlank()) continue;
                String section = p.section().trim();
                String line = p.line() == null ? "" : p.line().trim();
                String key = section.toUpperCase(Locale.ROOT) + "|" + line.toUpperCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                rows.add(UserLineAssignment.builder()
                        .userId(userId)
                        .section(section)
                        .line(line)
                        .build());
            }
        }
        assignmentRepository.saveAll(rows);
        systemLogService.logAction("UPDATE_LINE_ASSIGN",
                "User ID " + userId + " (" + user.getUsername() + ") — " + rows.size() + " assignment(s) set");
    }

    public void deleteAllForUser(Long userId) {
        assignmentRepository.deleteByUserId(userId);
    }

    /** Distinct (section -> lines) discovered in existing data, for the picker. */
    @Transactional(readOnly = true)
    public Map<String, List<String>> availableSectionLines() {
        Map<String, TreeSet<String>> map = new TreeMap<>();
        collect(dailyProductionRepository.findDistinctSectionLinePairs(), map);
        collect(splitEntryRepository.findDistinctSectionLinePairs(), map);

        Map<String, List<String>> result = new LinkedHashMap<>();
        map.forEach((section, lines) -> result.put(section, new ArrayList<>(lines)));
        return result;
    }

    private void collect(List<Object[]> pairs, Map<String, TreeSet<String>> map) {
        for (Object[] row : pairs) {
            if (row[0] == null || row[1] == null) continue;
            String section = row[0].toString().trim();
            String line = row[1].toString().trim();
            if (section.isEmpty() || line.isEmpty()) continue;
            map.computeIfAbsent(section, k -> new TreeSet<>()).add(line);
        }
    }

    /** Simple (section, line) pair carried from the admin UI. */
    public record SectionLine(String section, String line) {}
}
