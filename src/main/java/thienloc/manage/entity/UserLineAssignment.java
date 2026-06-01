package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Restricts which production (section, line) a user may enter data for.
 * A blank line ("") means the whole section (every line in it).
 * A user with no rows is unrestricted — the restriction only activates
 * once at least one assignment exists. ADMIN/MANAGER are always exempt.
 */
@Entity
@Table(name = "user_line_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ula_user_section_line",
                columnNames = {"user_id", "section", "line"}),
        indexes = @Index(name = "idx_ula_user", columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLineAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String section;

    // "" = whole section (all lines)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private String line = "";
}
