package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sixs_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"data_month", "section", "line"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SixSRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_month", nullable = false, length = 7)
    private String dataMonth;   // "2026-01"

    @Column(nullable = false, length = 20)
    private String section;     // "SEW", "CP", "BUFF"

    @Column(nullable = false, length = 10)
    private String line;        // "1A", "2B", "7"

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

    // ── Computed (not stored) ─────────────────────────────────────────────────

    @Transient
    public int getTotalPhotos() {
        return (week1 != null ? week1 : 0)
             + (week2 != null ? week2 : 0)
             + (week3 != null ? week3 : 0)
             + (week4 != null ? week4 : 0)
             + (week5 != null ? week5 : 0);
    }

    /** 6S total score: 100% minus 0.5% per photo (max 200 photos = 100%). */
    @Transient
    public double getTotalPercent() {
        return 100.0 - (getTotalPhotos() / 200.0 * 100.0);
    }
}
