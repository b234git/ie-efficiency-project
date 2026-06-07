package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily per-line chemical consumption — mirrors the "Actual" sheet of the VOC workbook.
 * Net VOC emitted = (quantityKg - reuseKg) * chemical.vocFactor, computed in VocService
 * (the factor lives on {@link VocChemical}). Keyed (date, section, line, chemical).
 */
@Entity
@Table(name = "voc_consumption",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"production_date", "section", "line", "chemical_code"}),
        indexes = {
                @Index(name = "idx_voc_cons_date",          columnList = "production_date"),
                @Index(name = "idx_voc_cons_date_sec_line", columnList = "production_date, section, line")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocConsumption {

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

    @Column(name = "chemical_code", nullable = false, length = 40)
    private String chemicalCode;         // FK by code to VocChemical

    @Builder.Default
    @Column(name = "quantity_kg", nullable = false)
    private Double quantityKg = 0.0;

    @Builder.Default
    @Column(name = "reuse_kg", nullable = false)
    private Double reuseKg = 0.0;

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
