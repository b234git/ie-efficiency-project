package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reprocess_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"data_month", "section", "line"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReprocessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_month", nullable = false, length = 7)
    private String dataMonth;   // "2026-01"

    @Column(nullable = false, length = 20)
    private String section;

    @Column(nullable = false, length = 10)
    private String line;

    @Column
    private Integer week1;

    @Column
    private Integer week2;

    @Column
    private Integer week3;

    @Column
    private Integer week4;

    @Column
    private Integer week5;

    @Column
    private Integer output;

    // ── Computed (not stored) ─────────────────────────────────────────────────

    @Transient
    public int getTotalReprocess() {
        return (week1 != null ? week1 : 0)
             + (week2 != null ? week2 : 0)
             + (week3 != null ? week3 : 0)
             + (week4 != null ? week4 : 0)
             + (week5 != null ? week5 : 0);
    }

    /** Reprocess %: total reprocessed / output * 100. Returns 0 if output is null/zero. */
    @Transient
    public double getTotalPercent() {
        if (output == null || output == 0) return 0.0;
        return getTotalReprocess() / (double) output * 100.0;
    }
}
