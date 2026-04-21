package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import thienloc.manage.util.NormalizationUtil;

@Entity
@Table(name = "master_db",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ref", "data_month"})
    },
    indexes = {
        @Index(name = "idx_mdb_article_no", columnList = "article_no"),
        @Index(name = "idx_mdb_data_month", columnList = "data_month")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ref; // E.g: 46024SEW1A

    @Column(name = "data_month", length = 7)
    private String dataMonth; // "2026-01", "2026-02", etc.

    @Column(nullable = false)
    private String articleNo;

    private String patternNo;

    private String shoeName;

    private String osCode;

    @Builder.Default
    private Double tct = 0.0;

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
        this.tct = (this.tct != null) ? NormalizationUtil.round(this.tct) : 0.0;

        this.sewCt           = NormalizationUtil.round(this.sewCt);
        this.sewMp           = NormalizationUtil.round(this.sewMp);
        this.sewQuotaDb      = NormalizationUtil.round(this.sewQuotaDb);
        this.sewPph          = NormalizationUtil.round(this.sewPph);

        this.buff1stCt       = NormalizationUtil.round(this.buff1stCt);
        this.buff1stMp       = NormalizationUtil.round(this.buff1stMp);
        this.buff1stQuotaDb  = NormalizationUtil.round(this.buff1stQuotaDb);
        this.buff1stPph      = NormalizationUtil.round(this.buff1stPph);

        this.buff2ndCt       = NormalizationUtil.round(this.buff2ndCt);
        this.buff2ndMp       = NormalizationUtil.round(this.buff2ndMp);
        this.buff2ndQuotaDb  = NormalizationUtil.round(this.buff2ndQuotaDb);
        this.buff2ndPph      = NormalizationUtil.round(this.buff2ndPph);

        this.stockfitUvCt       = NormalizationUtil.round(this.stockfitUvCt);
        this.stockfitUvMp       = NormalizationUtil.round(this.stockfitUvMp);
        this.stockfitUvQuotaDb  = NormalizationUtil.round(this.stockfitUvQuotaDb);
        this.stockfitUvPph      = NormalizationUtil.round(this.stockfitUvPph);

        this.stockfit1stCt      = NormalizationUtil.round(this.stockfit1stCt);
        this.stockfit1stMp      = NormalizationUtil.round(this.stockfit1stMp);
        this.stockfit1stQuotaDb = NormalizationUtil.round(this.stockfit1stQuotaDb);
        this.stockfit1stPph     = NormalizationUtil.round(this.stockfit1stPph);

        this.stockfit2ndCt      = NormalizationUtil.round(this.stockfit2ndCt);
        this.stockfit2ndMp      = NormalizationUtil.round(this.stockfit2ndMp);
        this.stockfit2ndQuotaDb = NormalizationUtil.round(this.stockfit2ndQuotaDb);
        this.stockfit2ndPph     = NormalizationUtil.round(this.stockfit2ndPph);

        this.assemBigCt       = NormalizationUtil.round(this.assemBigCt);
        this.assemBigMp       = NormalizationUtil.round(this.assemBigMp);
        this.assemBigQuotaDb  = NormalizationUtil.round(this.assemBigQuotaDb);
        this.assemBigPph      = NormalizationUtil.round(this.assemBigPph);

        this.assemSmallCt       = NormalizationUtil.round(this.assemSmallCt);
        this.assemSmallMp       = NormalizationUtil.round(this.assemSmallMp);
        this.assemSmallQuotaDb  = NormalizationUtil.round(this.assemSmallQuotaDb);
        this.assemSmallPph      = NormalizationUtil.round(this.assemSmallPph);
    }
}
