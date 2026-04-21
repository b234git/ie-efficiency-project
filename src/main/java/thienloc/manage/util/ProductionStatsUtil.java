package thienloc.manage.util;

import thienloc.manage.dto.DailyProductionDto;

import java.util.List;

/**
 * Aggregate statistics over a list of {@link DailyProductionDto}.
 * Centralizes summary math previously duplicated in ProductionController
 * and ReportController for the view layer.
 */
public final class ProductionStatsUtil {

    private ProductionStatsUtil() {}

    public static int totalOutput(List<DailyProductionDto> records) {
        if (records == null || records.isEmpty()) return 0;
        return records.stream()
                .mapToInt(r -> r.getOutput() != null ? r.getOutput() : 0)
                .sum();
    }

    /**
     * Average of non-null {@code eff} values. Returns {@code null} when no record
     * has an EFF value (so the view can distinguish "no data" from "0%").
     */
    public static Double averageEff(List<DailyProductionDto> records) {
        if (records == null || records.isEmpty()) return null;
        long count = records.stream().filter(r -> r.getEff() != null).count();
        if (count == 0) return null;
        return records.stream()
                .filter(r -> r.getEff() != null)
                .mapToDouble(DailyProductionDto::getEff)
                .average()
                .orElse(0);
    }
}
