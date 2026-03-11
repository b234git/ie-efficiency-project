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

    private Double tct;

    // SEW
    private Double sewCt;
    private Double sewMp;
    private Double sewQuotaDb;
    private Double sewPph;

    // BUFFING 1ST
    private Double buff1stCt;
    private Double buff1stMp;
    private Double buff1stQuotaDb;
    private Double buff1stPph;

    // BUFFING 2ND
    private Double buff2ndCt;
    private Double buff2ndMp;
    private Double buff2ndQuotaDb;
    private Double buff2ndPph;

    // STOCKFIT UV
    private Double stockfitUvCt;
    private Double stockfitUvMp;
    private Double stockfitUvQuotaDb;
    private Double stockfitUvPph;

    // STOCKFIT 1ST
    private Double stockfit1stCt;
    private Double stockfit1stMp;
    private Double stockfit1stQuotaDb;
    private Double stockfit1stPph;

    // STOCKFIT 2ND
    private Double stockfit2ndCt;
    private Double stockfit2ndMp;
    private Double stockfit2ndQuotaDb;
    private Double stockfit2ndPph;

    // ASSEMBLY BIG
    private Double assemBigCt;
    private Double assemBigMp;
    private Double assemBigQuotaDb;
    private Double assemBigPph;

    // ASSEMBLY SMALL
    private Double assemSmallCt;
    private Double assemSmallMp;
    private Double assemSmallQuotaDb;
    private Double assemSmallPph;

    @PrePersist
    @PreUpdate
    protected void onSaveOrUpdate() {
        this.tct = round(this.tct);

        this.sewCt = round(this.sewCt);
        this.sewMp = round(this.sewMp);
        this.sewQuotaDb = round(this.sewQuotaDb);
        this.sewPph = round(this.sewPph);

        this.buff1stCt = round(this.buff1stCt);
        this.buff1stMp = round(this.buff1stMp);
        this.buff1stQuotaDb = round(this.buff1stQuotaDb);
        this.buff1stPph = round(this.buff1stPph);

        this.buff2ndCt = round(this.buff2ndCt);
        this.buff2ndMp = round(this.buff2ndMp);
        this.buff2ndQuotaDb = round(this.buff2ndQuotaDb);
        this.buff2ndPph = round(this.buff2ndPph);

        this.stockfitUvCt = round(this.stockfitUvCt);
        this.stockfitUvMp = round(this.stockfitUvMp);
        this.stockfitUvQuotaDb = round(this.stockfitUvQuotaDb);
        this.stockfitUvPph = round(this.stockfitUvPph);

        this.stockfit1stCt = round(this.stockfit1stCt);
        this.stockfit1stMp = round(this.stockfit1stMp);
        this.stockfit1stQuotaDb = round(this.stockfit1stQuotaDb);
        this.stockfit1stPph = round(this.stockfit1stPph);

        this.stockfit2ndCt = round(this.stockfit2ndCt);
        this.stockfit2ndMp = round(this.stockfit2ndMp);
        this.stockfit2ndQuotaDb = round(this.stockfit2ndQuotaDb);
        this.stockfit2ndPph = round(this.stockfit2ndPph);

        this.assemBigCt = round(this.assemBigCt);
        this.assemBigMp = round(this.assemBigMp);
        this.assemBigQuotaDb = round(this.assemBigQuotaDb);
        this.assemBigPph = round(this.assemBigPph);

        this.assemSmallCt = round(this.assemSmallCt);
        this.assemSmallMp = round(this.assemSmallMp);
        this.assemSmallQuotaDb = round(this.assemSmallQuotaDb);
        this.assemSmallPph = round(this.assemSmallPph);
    }

    private Double round(Double val) {
        if (val == null)
            return null;
        return Math.round(val * 100.0) / 100.0;
    }
}
