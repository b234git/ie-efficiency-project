package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SUBCON chemical-usage header — mirrors one row of the workbook "CEMENT" /
 * "ACTUAL CEMENT" sheets: a (date, subcontractor, article) with its own OUTPUT
 * (entered directly — there is no daily_production link for subcontractors) and
 * the per-chemical actuals in {@link VocSubconDetail}. Standard usage = output ×
 * recipe(article, chemical); shortage = standard − actual, computed in VocService.
 */
@Entity
@Table(name = "voc_subcon_entry",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"production_date", "subcontractor", "article_no"}),
        indexes = @Index(name = "idx_voc_subcon_entry_date", columnList = "production_date"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocSubconEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(nullable = false, length = 40)
    private String subcontractor;        // 'TH2'

    @Column(name = "article_no", nullable = false, length = 40)
    private String articleNo;

    @Builder.Default
    @Column(nullable = false)
    private Integer output = 0;          // pairs (entered directly)

    @Builder.Default
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chemicalCode ASC")
    private List<VocSubconDetail> details = new ArrayList<>();

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
