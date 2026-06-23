package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-(date, section, line, article) production output — the VOC workbook's "Data" sheet,
 * which is the source the Excel uses to compute allowance (allowance = output × recipe).
 * A line that runs several styles in a day yields one row per article, with {@code output}
 * already apportioned by the sheet's per-slot weights at import time (so reconciliation is a
 * plain Σ output × recipe, matching the workbook's weighted formula). Section is the VOC
 * section (SEW/ASSY/SF) as written in the file — no EFF→VOC mapping needed here.
 */
@Entity
@Table(name = "voc_production",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"production_date", "section", "line", "article_no"}),
        indexes = {
                @Index(name = "idx_voc_prod_date",          columnList = "production_date"),
                @Index(name = "idx_voc_prod_date_sec_line", columnList = "production_date, section, line")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String section = "SEW";

    @Column(nullable = false, length = 10)
    private String line;                 // '1A'

    @Column(name = "article_no", nullable = false, length = 40)
    private String articleNo;            // style run on this line/day

    @Builder.Default
    @Column(nullable = false)
    private Double output = 0.0;         // pairs, apportioned to this article by slot weight

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
