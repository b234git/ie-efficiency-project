package thienloc.manage.util;

import thienloc.manage.dto.DailyProductionDto;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized filtering utilities for {@link DailyProductionDto} lists.
 * Eliminates duplicated filter logic that was repeated across
 * ProductionController, ReportController, and DashboardController.
 *
 * Each method is a no-op when the filter value is null or blank.
 */
public final class RecordFilterUtil {

    private RecordFilterUtil() {}

    /**
     * Filter records by article keyword (case-insensitive substring match).
     * Checks both the top-level {@code article} field and any slot-level
     * {@code details.articleNo} entries.
     */
    public static List<DailyProductionDto> filterByArticle(List<DailyProductionDto> records, String article) {
        if (article == null || article.trim().isEmpty()) return records;
        String lower = article.toLowerCase().trim();
        return records.stream()
                .filter(r -> (r.getArticle() != null && r.getArticle().toLowerCase().contains(lower)) ||
                        (r.getDetails() != null && r.getDetails().stream()
                                .anyMatch(d -> d.getArticleNo() != null
                                        && d.getArticleNo().toLowerCase().contains(lower))))
                .collect(Collectors.toList());
    }

    /**
     * Filter records by exact section (case-insensitive). ReportController uses
     * a prefix match ("starts-with"), so use {@link #filterBySectionPrefix} for that.
     */
    public static List<DailyProductionDto> filterBySection(List<DailyProductionDto> records, String section) {
        if (section == null || section.trim().isEmpty()) return records;
        String sec = section.trim();
        return records.stream()
                .filter(r -> sec.equalsIgnoreCase(r.getSection()))
                .collect(Collectors.toList());
    }

    /**
     * Filter records where {@code section} starts with the given prefix (case-insensitive).
     * Used by ReportController to match both "ASSEMBLY BIG" and "ASSEMBLY SMALL" when
     * the user selects the "ASSEMBLY" category.
     */
    public static List<DailyProductionDto> filterBySectionPrefix(List<DailyProductionDto> records, String section) {
        if (section == null || section.trim().isEmpty()) return records;
        String sec = section.trim();
        return records.stream()
                .filter(r -> r.getSection() != null && r.getSection().startsWith(sec))
                .collect(Collectors.toList());
    }

    /**
     * Filter records by line (case-insensitive substring match).
     */
    public static List<DailyProductionDto> filterByLine(List<DailyProductionDto> records, String line) {
        if (line == null || line.trim().isEmpty()) return records;
        String lower = line.toLowerCase().trim();
        return records.stream()
                .filter(r -> r.getLine() != null && r.getLine().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    /**
     * Filter records by exact line (case-sensitive equality).
     * Used by ReportController where lines are exact identifiers.
     */
    public static List<DailyProductionDto> filterByLineExact(List<DailyProductionDto> records, String line) {
        if (line == null || line.trim().isEmpty()) return records;
        String ln = line.trim();
        return records.stream()
                .filter(r -> ln.equals(r.getLine()))
                .collect(Collectors.toList());
    }

    /**
     * Filter records to only those where {@code effKpi} is null (records with errors).
     */
    public static List<DailyProductionDto> filterErrorsOnly(List<DailyProductionDto> records) {
        return records.stream()
                .filter(r -> r.getEffKpi() == null)
                .collect(Collectors.toList());
    }

    /**
     * Filter records by source: "ENTRY" excludes SPLIT records; "SPLIT" keeps only SPLIT.
     * Any other value (null, empty, "ALL") is a no-op.
     */
    public static List<DailyProductionDto> filterBySource(List<DailyProductionDto> records, String source) {
        if (source == null || source.trim().isEmpty()) return records;
        return switch (source.trim().toUpperCase()) {
            case "ENTRY" -> records.stream()
                    .filter(r -> !"SPLIT".equals(r.getSource()))
                    .collect(Collectors.toList());
            case "SPLIT" -> records.stream()
                    .filter(r -> "SPLIT".equals(r.getSource()))
                    .collect(Collectors.toList());
            default -> records;
        };
    }
}
