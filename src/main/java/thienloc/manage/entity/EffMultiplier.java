package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "eff_multiplier",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sec"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffMultiplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String sec;       // e.g. "SEW8", "ASSY10", "BUFF4"

    @Column(nullable = false, length = 10)
    private String section;   // "SEW", "ASSY", "SF", "BUFF"

    @Column(nullable = false)
    private Integer wt;       // 4, 8, or 10

    @Column(name = "rate_alias", nullable = true, length = 10)
    private String rateAlias; // null for non-BUFF; e.g. "SF8" for BUFF8

    @Builder.Default
    private Double grade1 = 1.0;
    private Double grade2;
    private Double grade3;
    private Double grade4;
    private Double grade5;
    private Double grade6;
    private Double grade7;
    private Double grade8;
    private Double grade9;
}
