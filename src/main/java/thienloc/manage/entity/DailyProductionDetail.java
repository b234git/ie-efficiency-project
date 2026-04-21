package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_production_details",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_dpd_prod_slot", columnNames = {"daily_production_id", "time_slot"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyProductionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_production_id", nullable = false)
    private DailyProduction dailyProduction;

    @Column(nullable = false)
    private String timeSlot; // E.g: "07:30", "08:30"

    @Column(nullable = false)
    private String articleNo;

    @Column(nullable = false)
    private Integer output;
}
