package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One chemical actual under a {@link VocSubconEntry}. The parent holds OUTPUT once;
 * standard (output × recipe) and shortage are computed in VocService. The back-
 * reference is excluded from toString/equals to avoid the bidirectional cycle.
 */
@Entity
@Table(name = "voc_subcon_detail",
        uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "chemical_code"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocSubconDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private VocSubconEntry entry;

    @Column(name = "chemical_code", nullable = false, length = 40)
    private String chemicalCode;

    @Builder.Default
    @Column(name = "actual_kg", nullable = false)
    private Double actualKg = 0.0;

    @Builder.Default
    @Column(name = "reuse_kg", nullable = false)
    private Double reuseKg = 0.0;
}
