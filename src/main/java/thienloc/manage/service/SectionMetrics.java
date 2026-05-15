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

        // Buffing aliases. The factory's "EFF APR V6" workbook uses bare "BUFF"
        // (the 1ST/2ND distinction comes from the article's "-2" suffix), so map it
        // to BUFFING 1ST by default; resolveSlot() upgrades to 2ND when a slot
        // article ends with "-2". Line codes BF / SA / 7B fall back to 1ST until
        // dedicated MasterDb columns exist for them.
        LOOKUP.put("BUFF", BUFF_1ST);
        LOOKUP.put("BUFF 1", BUFF_1ST);
        LOOKUP.put("BUFF 1ST", BUFF_1ST);
        LOOKUP.put("BUFF 2", BUFF_2ND);
        LOOKUP.put("BUFF 2ND", BUFF_2ND);

        // Stockfit aliases. Bare "SF" defaults to STOCKFIT 1ST (same -2 mechanism).
        LOOKUP.put("SF", STOCKFIT_1ST);
        LOOKUP.put("SF UV", STOCKFIT_UV);
        LOOKUP.put("SFUV", STOCKFIT_UV);
        LOOKUP.put("STOCKFIT", STOCKFIT_1ST);
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
     * Per-slot article identity. For BUFF/SF, suffix "-2" marks the 2ND subsection
     * variant; the cleanedArticle strips that suffix so it can match MasterDb.articleNo.
     */
    public static record ArticleKey(String cleanedArticle, boolean isSecondSubsection) {
        public static ArticleKey parse(String rawArticle) {
            if (rawArticle == null) return new ArticleKey(null, false);
            String t = rawArticle.trim();
            if (t.isEmpty()) return new ArticleKey(null, false);
            if (t.endsWith("-2") && !t.endsWith("-02")) {
                return new ArticleKey(t.substring(0, t.length() - 2), true);
            }
            return new ArticleKey(t, false);
        }
    }

    /**
     * Slot-level resolution: primary metrics to use for a given slot, plus an optional
     * fallback metrics applied when the primary column has no data in MasterDb.
     */
    public static record ResolvedSection(SectionMetrics primary, SectionMetrics fallback) {}

    /**
     * Resolve primary + fallback SectionMetrics for one slot, based on row-level
     * section and the slot's raw article (which may carry a "-2" suffix).
     *
     * Rules (BUFF/STOCKFIT only — other sections pass through without fallback):
     *   - article ends with "-2"            → primary = 2ND,  no fallback (suffix wins).
     *   - no "-2", row is ...2ND             → primary = 2ND,  fallback = 1ST (try 2ND first).
     *   - no "-2", row is ...1ST/UV          → primary = row,  no fallback.
     */
    public static ResolvedSection resolveSlot(String rowSection, String rawArticle) {
        Optional<SectionMetrics> rowOpt = fromSection(rowSection);
        if (rowOpt.isEmpty()) return new ResolvedSection(null, null);
        SectionMetrics row = rowOpt.get();
        boolean isBuff = row == BUFF_1ST || row == BUFF_2ND;
        boolean isSf = row == STOCKFIT_UV || row == STOCKFIT_1ST || row == STOCKFIT_2ND;
        if (!isBuff && !isSf) return new ResolvedSection(row, null);

        ArticleKey key = ArticleKey.parse(rawArticle);
        if (isBuff) {
            if (key.isSecondSubsection()) return new ResolvedSection(BUFF_2ND, null);
            return (row == BUFF_2ND)
                    ? new ResolvedSection(BUFF_2ND, BUFF_1ST)
                    : new ResolvedSection(BUFF_1ST, null);
        }
        // STOCKFIT family
        if (row == STOCKFIT_UV) return new ResolvedSection(STOCKFIT_UV, null);
        if (key.isSecondSubsection()) return new ResolvedSection(STOCKFIT_2ND, null);
        return (row == STOCKFIT_2ND)
                ? new ResolvedSection(STOCKFIT_2ND, STOCKFIT_1ST)
                : new ResolvedSection(STOCKFIT_1ST, null);
    }

    /**
     * Resolve the SectionMetrics for a single slot, taking into account the article's
     * "-2" suffix. Only BUFF/SF families differentiate per-slot; other sections fall
     * back to the row-level section resolution.
     */
    public static Optional<SectionMetrics> fromSectionAndArticle(String rowSection, String rawArticle) {
        Optional<SectionMetrics> rowOpt = fromSection(rowSection);
        if (rowOpt.isEmpty()) return rowOpt;
        SectionMetrics row = rowOpt.get();
        boolean isBuff = row == BUFF_1ST || row == BUFF_2ND;
        boolean isSf = row == STOCKFIT_UV || row == STOCKFIT_1ST || row == STOCKFIT_2ND;
        if (!isBuff && !isSf) return rowOpt;

        ArticleKey key = ArticleKey.parse(rawArticle);
        if (isBuff) {
            return Optional.of(key.isSecondSubsection() ? BUFF_2ND : BUFF_1ST);
        }
        // STOCKFIT: keep UV as-is; map 1ST/2ND by suffix.
        if (row == STOCKFIT_UV) return rowOpt;
        return Optional.of(key.isSecondSubsection() ? STOCKFIT_2ND : STOCKFIT_1ST);
    }

    /** CT with fallback to another SectionMetrics' column if this one is null/zero. */
    public Double getCtOrFallback(MasterDb m, SectionMetrics fallback) {
        Double v = getCt(m);
        if (v != null && v > 0) return v;
        return (fallback != null) ? fallback.getCt(m) : v;
    }

    public Double getMpOrFallback(MasterDb m, SectionMetrics fallback) {
        Double v = getMp(m);
        if (v != null && v > 0) return v;
        return (fallback != null) ? fallback.getMp(m) : v;
    }

    public Double getQuotaOrFallback(MasterDb m, SectionMetrics fallback) {
        Double v = getQuota(m);
        if (v != null && v > 0) return v;
        return (fallback != null) ? fallback.getQuota(m) : v;
    }

    public Double getPphOrFallback(MasterDb m, SectionMetrics fallback) {
        Double v = getPph(m);
        if (v != null && v > 0) return v;
        return (fallback != null) ? fallback.getPph(m) : v;
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
