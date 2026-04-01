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
