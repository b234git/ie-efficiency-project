package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "eff_incentive_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sec", "eff_percent"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffIncentiveRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String sec;       // e.g. "ASSY10", "SEW8"

    @Column(nullable = false, length = 10)
    private String section;   // "SEW", "ASSY", "SF"

    @Column(nullable = false)
    private Integer wt;       // 4, 8, 10

    @Column(name = "eff_percent", nullable = false)
    private Double effPercent; // stored as %, e.g. 79.5 (not 0.795)

    @Column(nullable = false)
    private Double rate;       // VND amount, e.g. 8121.0
}
