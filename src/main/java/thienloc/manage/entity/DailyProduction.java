package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import thienloc.manage.util.NormalizationUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_production", indexes = {
    @Index(name = "idx_dp_production_date",  columnList = "production_date"),
    @Index(name = "idx_dp_user_id",           columnList = "user_id"),
    @Index(name = "idx_dp_section_line",      columnList = "section, line"),
    @Index(name = "idx_dp_date_section_line", columnList = "production_date, section, line")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private LocalDate productionDate;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String line;

    @Column(nullable = false)
    private Double mp; // Manpower (Direct Labor = DL)

    private Double dli; // Direct Labor Indirect

    private Double idl; // Indirect Labor

    @Column(nullable = false)
    private Double wt; // Working Time

    private Double rft; // Right First Time (%)

    // Allowance = allowed output percentage (1.0 = 100%, 0.8 = 80%)
    @Builder.Default
    @Column(nullable = false)
    private Double allowance = 1.0;

    @Builder.Default
    @Column(name = "total_output", nullable = false)
    private Integer totalOutput = 0;

    @Builder.Default
    @OneToMany(mappedBy = "dailyProduction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timeSlot ASC")
    private java.util.List<DailyProductionDetail> details = new java.util.ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User createdBy;

    // Derived fields for EFF calculation can be calculated dynamically or stored
    // eff = (output * TCT) / (mp * wt * 3600)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        roundFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        roundFields();
    }

    private void roundFields() {
        this.mp        = NormalizationUtil.round(this.mp);
        this.dli       = NormalizationUtil.round(this.dli);
        this.idl       = NormalizationUtil.round(this.idl);
        this.wt        = NormalizationUtil.round(this.wt);
        this.rft       = NormalizationUtil.round(this.rft);
        this.allowance = NormalizationUtil.round(this.allowance);
    }
}
