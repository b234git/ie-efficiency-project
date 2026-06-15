package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * VOC standard recipe — mirrors the "DB" sheet of the VOC workbook (one normalized
 * row per article × chemical). kgPerPair keeps full precision (values like 0.000625),
 * so it is NOT rounded. Allowance = output × kgPerPair × 1.1.
 */
@Entity
@Table(name = "voc_standard_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"article_no", "chemical_code"}),
        indexes = @Index(name = "idx_voc_std_rate_article", columnList = "article_no"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocStandardRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_no", nullable = false, length = 40)
    private String articleNo;

    @Column(name = "chemical_code", nullable = false, length = 40)
    private String chemicalCode;

    @Builder.Default
    @Column(name = "kg_per_pair", nullable = false)
    private Double kgPerPair = 0.0;

    /** Raw reciprocal expression from the DB sheet, e.g. "1/1300+1/1500+1/1400".
     *  kgPerPair is its evaluated value. Null when the user typed a plain number. */
    @Column(name = "formula", length = 255)
    private String formula;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
