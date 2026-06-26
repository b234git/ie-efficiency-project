package thienloc.manage.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SalaryReportDto {

    private String month;
    private List<SectionLineBlock> blocks;

    @Data
    public static class SectionLineBlock {
        private String section;
        private String line;
        private double sixSPercent;        // 6S pass rate; no record = 100%
        private double reprocessPercent;   // reprocess pass rate (100 - defect); no record = 100%
        private double effectivePct;       // 6S × (1 − reprocess defect rate), as % (Excel G1×(1−G3))
        private int newStyleCount;         // number of distinct styles
        private long newStyleIncentive;    // sum(qty) × 30,000
        private List<String> gradeLabels;  // ["AA","A","B","C","D","E","LL1","LL2","LL3"] for SEW
        private List<DayRow> dailyRows;
        private long[] gradeTotals;        // monthly total per grade (9 elements)
    }

    @Data
    public static class DayRow {
        private LocalDate date;
        private double targetQuota;  // planned daily quota (from DailyProductionDto.target)
        private double targetMp;     // standard MP resolved by EFF calc (Excel sheet S col C)
        private double mp;           // actual MP = DLI (Excel sheet S col F = D!I), not DL
        private double wt;
        private double output;       // actual produced quantity
        private String sec;          // e.g. "SEW10"
        private double effSalary;    // from DailyProductionDto
        private double baseRate;     // floor-looked-up from EffIncentiveRate
        private long[] gradeAmounts; // ROUNDUP(baseRate × mult[i] × effectiveFraction + newStyle/days, −2)
    }
}
