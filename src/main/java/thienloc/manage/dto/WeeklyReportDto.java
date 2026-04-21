package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one block in the weekly report (e.g., BUFF Line A).
 * Contains daily rows + a summary/total row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportDto {

    private String section; // e.g. "SEW", "BUFFING 1ST"
    private String line; // e.g. "1A", "6B"

    @Builder.Default
    private List<DailyRow> dailyRows = new ArrayList<>();

    private SummaryRow total;

    /**
     * Recompute the {@link #total} summary row from the current {@link #dailyRows}.
     * Called after controllers mutate {@code dailyRows} (e.g. removing Sunday rows
     * for non-privileged users) so the summary stays in sync.
     */
    public void recalculateSummary() {
        if (total == null) total = new SummaryRow();
        int n = dailyRows.size();
        int sumOutput = 0, sumTargetOutput = 0, targetCount = 0;
        double sumMp = 0, sumWt = 0, sumEff = 0, sumActPph = 0, sumStdPph = 0, sumDli = 0;
        int effCount = 0, stdCount = 0;

        for (DailyRow row : dailyRows) {
            sumOutput += (row.getOutput() != null ? row.getOutput() : 0);
            sumMp += (row.getMp() != null ? row.getMp() : 0);
            sumWt += (row.getWt() != null ? row.getWt() : 0);
            if (row.getActualPph() != null) sumActPph += row.getActualPph();
            if (row.getStdPph() != null) { sumStdPph += row.getStdPph(); stdCount++; }
            if (row.getEff() != null) { sumEff += row.getEff(); effCount++; }
            sumDli += (row.getDli() != null ? row.getDli() : 0);
            if (row.getTargetOutput() != null) { sumTargetOutput += row.getTargetOutput(); targetCount++; }
        }

        total.setTotalOutput(sumOutput);
        total.setDayCount(n);
        total.setAvgMp(n > 0 ? sumMp / n : 0);
        total.setAvgWt(n > 0 ? sumWt / n : 0);
        total.setAvgEff(effCount > 0 ? sumEff / effCount : null);
        total.setAvgActualPph(n > 0 ? sumActPph / n : null);
        total.setAvgStdPph(stdCount > 0 ? sumStdPph / stdCount : null);
        total.setAvgDli(n > 0 ? sumDli / n : 0);
        total.setTotalTargetOutput(targetCount > 0 ? sumTargetOutput : null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRow {
        private LocalDate date;
        private String articleNo;
        private String patternNo;
        private String shoeName;
        private Integer output;
        private Double mp; // Manpower (DL)
        private Double wt; // Working Time
        private Double eff; // EFF%
        private Double actualPph; // Output / MP / WT
        private Double stdPph; // From MasterDb
        private Double dli;           // Actual MP (Direct Labor Indirect)
        private Integer targetOutput; // round(stdPph × dli × wt)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryRow {
        private Double avgEff;
        private Double avgActualPph;
        private Double avgStdPph;
        private Integer totalOutput;
        private Double avgMp;
        private Double avgWt;
        private int dayCount;
        private Double avgDli;
        private Integer totalTargetOutput;
    }
}
