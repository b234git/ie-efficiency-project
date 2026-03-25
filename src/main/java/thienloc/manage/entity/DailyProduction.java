package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_production", indexes = {
    @Index(name = "idx_dp_production_date", columnList = "productionDate"),
    @Index(name = "idx_dp_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyProduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    // Allowance = % sản lượng cho phép (1.0 = 100%, 0.8 = 80%)
    @Builder.Default
    @Column(nullable = false)
    private Double allowance = 1.0;

    @Builder.Default
    @Column(name = "total_output", nullable = false)
    private Integer totalOutput = 0;

    @Builder.Default
    @OneToMany(mappedBy = "dailyProduction", cascade = CascadeType.ALL, orphanRemoval = true)
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
        this.mp = round(this.mp);
        this.dli = round(this.dli);
        this.idl = round(this.idl);
        this.wt = round(this.wt);
        this.rft = round(this.rft);
        this.allowance = round(this.allowance);
    }

    private Double round(Double val) {
        if (val == null)
            return null;
        return Math.round(val * 100.0) / 100.0;
    }
}
