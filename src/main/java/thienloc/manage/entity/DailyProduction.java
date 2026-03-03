package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_production")
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
    private Double mp; // Manpower

    @Column(nullable = false)
    private Double wt; // Working Time

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
