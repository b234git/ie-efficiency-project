package thienloc.manage.service;

import thienloc.manage.entity.MasterDb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Enum that maps each production section to its corresponding MasterDb field extractors.
 * Replaces the 4 duplicate switch methods (getDynamicPphBySection, getDynamicCtBySection,
 * getDynamicMpBySection, getDynamicQuotaBySection) in ProductionService.
 *
 * Each enum constant encapsulates CT, MP, Quota, PPH extraction for one section.
 */
public enum SectionMetrics {

    SEW("SEW",
            MasterDb::getSewCt, MasterDb::getSewMp,
            MasterDb::getSewQuotaDb, MasterDb::getSewPph),

    BUFF_1ST("BUFFING 1ST",
            MasterDb::getBuff1stCt, MasterDb::getBuff1stMp,
            MasterDb::getBuff1stQuotaDb, MasterDb::getBuff1stPph),

    BUFF_2ND("BUFFING 2ND",
            MasterDb::getBuff2ndCt, MasterDb::getBuff2ndMp,
            MasterDb::getBuff2ndQuotaDb, MasterDb::getBuff2ndPph),

    STOCKFIT_UV("STOCKFIT UV",
            MasterDb::getStockfitUvCt, MasterDb::getStockfitUvMp,
            MasterDb::getStockfitUvQuotaDb, MasterDb::getStockfitUvPph),

    STOCKFIT_1ST("STOCKFIT 1ST",
            MasterDb::getStockfit1stCt, MasterDb::getStockfit1stMp,
            MasterDb::getStockfit1stQuotaDb, MasterDb::getStockfit1stPph),

    STOCKFIT_2ND("STOCKFIT 2ND",
            MasterDb::getStockfit2ndCt, MasterDb::getStockfit2ndMp,
            MasterDb::getStockfit2ndQuotaDb, MasterDb::getStockfit2ndPph),

    ASSEMBLY_BIG("ASSEMBLY BIG",
            m -> m.getAssemBigCt() != null ? m.getAssemBigCt() : m.getAssemSmallCt(),
            m -> m.getAssemBigMp() != null ? m.getAssemBigMp() : m.getAssemSmallMp(),
            m -> m.getAssemBigQuotaDb() != null ? m.getAssemBigQuotaDb() : m.getAssemSmallQuotaDb(),
            m -> m.getAssemBigPph() != null ? m.getAssemBigPph() : m.getAssemSmallPph()),

    ASSEMBLY_SMALL("ASSEMBLY SMALL",
            MasterDb::getAssemSmallCt, MasterDb::getAssemSmallMp,
            MasterDb::getAssemSmallQuotaDb, MasterDb::getAssemSmallPph);

    private final String sectionName;
    private final Function<MasterDb, Double> ctExtractor;
    private final Function<MasterDb, Double> mpExtractor;
    private final Function<MasterDb, Double> quotaExtractor;
    private final Function<MasterDb, Double> pphExtractor;

    // Static lookup map: uppercase section name → enum constant (includes aliases)
    private static final Map<String, SectionMetrics> LOOKUP = new HashMap<>();

    static {
        for (SectionMetrics sm : values()) {
            LOOKUP.put(sm.sectionName.toUpperCase(), sm);
        }
        // Aliases for ASSEMBLY BIG
        LOOKUP.put("ASSY", ASSEMBLY_BIG);
        LOOKUP.put("ASSEMBLY", ASSEMBLY_BIG);
        LOOKUP.put("ASSY BIG", ASSEMBLY_BIG);
        LOOKUP.put("ASSY SMALL", ASSEMBLY_SMALL);

        // Buffing aliases
        LOOKUP.put("BUFF 1", BUFF_1ST);
        LOOKUP.put("BUFF 1ST", BUFF_1ST);
        LOOKUP.put("BUFF 2", BUFF_2ND);
        LOOKUP.put("BUFF 2ND", BUFF_2ND);

        // Stockfit aliases
        LOOKUP.put("SF UV", STOCKFIT_UV);
        LOOKUP.put("SF 1", STOCKFIT_1ST);
        LOOKUP.put("SF 1ST", STOCKFIT_1ST);
        LOOKUP.put("SF1", STOCKFIT_1ST);
        LOOKUP.put("SF 2", STOCKFIT_2ND);
        LOOKUP.put("SF 2ND", STOCKFIT_2ND);
        LOOKUP.put("SF2", STOCKFIT_2ND);
    }

    SectionMetrics(String sectionName,
                   Function<MasterDb, Double> ctExtractor,
                   Function<MasterDb, Double> mpExtractor,
                   Function<MasterDb, Double> quotaExtractor,
                   Function<MasterDb, Double> pphExtractor) {
        this.sectionName = sectionName;
        this.ctExtractor = ctExtractor;
        this.mpExtractor = mpExtractor;
        this.quotaExtractor = quotaExtractor;
        this.pphExtractor = pphExtractor;
    }

    /**
     * Resolve a section name (case-insensitive) to its SectionMetrics enum.
     */
    public static Optional<SectionMetrics> fromSection(String section) {
        if (section == null) return Optional.empty();
        return Optional.ofNullable(LOOKUP.get(section.toUpperCase().trim()));
    }

    /**
     * Normalize an abbreviation or alias to its canonical section name.
     * E.g. "BUFF 1" → "BUFFING 1ST", "SF UV" → "STOCKFIT UV".
     * Returns the input uppercased if no mapping is found.
     */
    public static String normalize(String section) {
        if (section == null) return null;
        return fromSection(section)
                .map(SectionMetrics::getSectionName)
                .orElse(section.trim().toUpperCase());
    }

    public String getSectionName() {
        return sectionName;
    }

    public Double getCt(MasterDb m) {
        return (m == null) ? null : ctExtractor.apply(m);
    }

    public Double getMp(MasterDb m) {
        return (m == null) ? null : mpExtractor.apply(m);
    }

    public Double getQuota(MasterDb m) {
        return (m == null) ? null : quotaExtractor.apply(m);
    }

    /**
     * Get PPH with fallback: if PPH is null/zero but CT > 0, derive PPH = 3600 / CT.
     */
    public Double getPph(MasterDb m) {
        if (m == null) return null;
        Double pph = pphExtractor.apply(m);
        if (pph != null && pph > 0) return pph;
        // Fallback: derive PPH from CT
        Double ct = ctExtractor.apply(m);
        return (ct != null && ct > 0) ? 3600.0 / ct : null;
    }

    /**
     * Get raw PPH without fallback (used when you need to check the actual stored value).
     */
    public Double getRawPph(MasterDb m) {
        return (m == null) ? null : pphExtractor.apply(m);
    }
}
