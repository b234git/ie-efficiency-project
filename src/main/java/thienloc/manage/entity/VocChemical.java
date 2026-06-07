package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chemical master for the VOC module — mirrors the "R" sheet of the VOC workbook.
 * Each chemical carries the VOC factor used to convert consumed kg into emitted VOC.
 * VOC factors keep 3-decimal precision (e.g. 0.745, 0.835), so values are NOT rounded.
 */
@Entity
@Table(name = "voc_chemical",
        uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocChemical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;                 // '577NT3'

    @Builder.Default
    @Column(name = "material_type", nullable = false, length = 20)
    private String materialType = "SOLVENT";   // 'WATER' | 'SOLVENT'

    @Column(length = 40)
    private String classification;       // Adhesive / Hot melt / Primer / Hardener ...

    @Column(length = 60)
    private String manufacturer;

    @Builder.Default
    @Column(name = "voc_factor", nullable = false)
    private Double vocFactor = 0.0;      // 0..1

    @Column(name = "price_per_kg")
    private Double pricePerKg;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public boolean isWaterBase() {
        return "WATER".equalsIgnoreCase(materialType);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
