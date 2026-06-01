package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-user override of a feature grant. If a row exists for a (user, feature)
 * pair, it overrides whatever the user's role would say:
 *   granted = true  -> user has access even if role doesn't
 *   granted = false -> user is denied even if role grants it
 */
@Entity
@Table(name = "user_feature_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feature_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(nullable = false)
    private boolean granted;
}
