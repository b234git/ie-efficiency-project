package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "split_entry_details",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_sed_entry_slot", columnNames = {"split_entry_id", "time_slot"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitEntryDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_entry_id", nullable = false)
    private SplitEntry splitEntry;

    @Column(nullable = false)
    private String timeSlot;

    @Column(nullable = false)
    private String articleNo;

    @Column(nullable = false)
    private Integer output;
}
