package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "master_db")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String ref; // E.g: 46024SEW1A

    @Column(nullable = false)
    private String articleNo;

    private String patternNo;

    private String shoeName;

    private String osCode;

    private Double sewQuota;

    @Column(nullable = false)
    private Double tct; // Target Cycle Time

    // Section Specific PPH
    private Double sewPph;
    private Double buff1stPph;
    private Double buff2ndPph;
    private Double stockfitUvPph;
    private Double stockfit1stPph;
    private Double stockfit2ndPph;
    private Double assemBigPph;
    private Double assemSmallPph;
}
