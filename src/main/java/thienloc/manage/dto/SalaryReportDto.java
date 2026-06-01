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
        private double effectivePct;       // max(0, sixS - reprocess defect rate)
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
        private int targetMp;        // standard MP from MasterDb.{section}Mp
        private int mp;              // actual MP
        private double wt;
        private double output;       // actual produced quantity
        private String sec;          // e.g. "SEW10"
        private double effSalary;    // from DailyProductionDto
        private double baseRate;     // floor-looked-up from EffIncentiveRate
        private long[] gradeAmounts; // baseRate × multiplier[i] × effectivePct / 100 (9 elements)
    }
}
