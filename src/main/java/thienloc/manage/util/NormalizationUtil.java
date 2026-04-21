package thienloc.manage.util;

/**
 * Centralized normalization utilities for production data fields.
 * Eliminates duplicated allowance / RFT / WT / section normalization logic that was
 * scattered across ProductionService, SplitEntryService, SalaryService,
 * WeeklyTrackingService, and EfficiencyCalculatorService.
 *
 * Also provides the shared {@link #round(Double)} helper used by JPA entity lifecycle hooks.
 */
public final class NormalizationUtil {

    private NormalizationUtil() {}

    /**
     * Normalize allowance value to the 0.0–1.0 decimal range.
     * Forms may submit a percentage (e.g. 80) or a decimal (e.g. 0.8).
     * A null or non-positive value is treated as 100% (1.0).
     */
    public static double normalizeAllowance(Double allowance) {
        if (allowance == null || allowance <= 0) return 1.0;
        return allowance > 1.0 ? allowance / 100.0 : allowance;
    }

    /**
     * Normalize RFT (Right First Time): if the stored value is in the decimal range
     * (0–1], convert it to a percentage (0–100).
     */
    public static Double normalizeRft(Double rft) {
        if (rft != null && rft > 0 && rft <= 1.0) {
            return rft * 100.0;
        }
        return rft;
    }

    /**
     * Normalize actual working-time hours to the nearest standard WT bucket: 4 / 8 / 10.
     * Used by SalaryService to look up the correct EffMultiplier rate tier.
     */
    public static int normalizeWT(Double wt) {
        if (wt == null) return 8;
        if (wt >= 9.0) return 10;
        if (wt >= 6.0) return 8;
        return 4;
    }

    /**
     * Map any section name variant to the base category key used by SalaryService
     * for EffMultiplier / SECTION_GRADE_LABELS lookups:
     * SEW → "SEW", ASSEMBLY* / ASSY* → "ASSY", BUFF* → "BUFF", STOCKFIT* / SF* → "SF".
     * Falls back to the uppercased input if no mapping matches.
     */
    public static String normalizeBaseSection(String section) {
        if (section == null) return "SEW";
        String upper = section.toUpperCase().trim();
        if (upper.startsWith("SEW"))                                    return "SEW";
        if (upper.startsWith("ASSEMBLY") || upper.startsWith("ASSY"))  return "ASSY";
        if (upper.startsWith("BUFF"))                                   return "BUFF";
        if (upper.startsWith("STOCKFIT") || upper.startsWith("SF"))    return "SF";
        return upper;
    }

    /**
     * Round a Double to 2 decimal places. Returns {@code null} for null input.
     * Intended for use in JPA entity {@code @PrePersist}/{@code @PreUpdate} hooks
     * (DailyProduction, SplitEntry, MasterDb) to avoid duplicating the same
     * 2-line utility in every entity class.
     */
    public static Double round(Double val) {
        if (val == null) return null;
        return Math.round(val * 100.0) / 100.0;
    }
}
