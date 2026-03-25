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

@Entity
@Table(name = "split_entry",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_split_entry_date_section_line",
            columnNames = {"productionDate", "section", "line"})
    },
    indexes = {
        @Index(name = "idx_se_production_date", columnList = "productionDate"),
        @Index(name = "idx_se_status", columnList = "status")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate productionDate;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String line;

    // ── Page 1: Manpower ──
    private Double mp;
    private Double dli;
    private Double idl;

    // ── Page 2: Output ──
    private Double wt;
    private Integer totalOutput;
    private Double rft;

    // ── Page 3: Allowance & Articles ──
    @Builder.Default
    private Double allowance = 1.0;

    @Builder.Default
    @OneToMany(mappedBy = "splitEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SplitEntryDetail> details = new ArrayList<>();

    // ── Tracking who filled each page ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manpower_filled_by")
    private User manpowerFilledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_filled_by")
    private User outputFilledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_filled_by")
    private User articleFilledBy;

    // ── Sync tracking ──
    private Long linkedDailyProductionId;

    @Builder.Default
    @Column(nullable = false)
    private String status = "PARTIAL"; // PARTIAL | READY | SYNCED

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
        if (val == null) return null;
        return Math.round(val * 100.0) / 100.0;
    }
}
