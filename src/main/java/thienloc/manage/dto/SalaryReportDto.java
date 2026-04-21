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
        private double sixSPercent;        // SixSRecord.getTotalPercent()
        private double reprocessPercent;   // ReprocessRecord.getTotalPercent()
        private double effectivePct;       // max(0, sixS - reprocess)
        private int newStyleCount;         // number of distinct styles
        private long newStyleIncentive;    // sum(qty) × 30,000
        private List<String> gradeLabels;  // ["AA","A","B","C","D","E","LL1","LL2","LL3"] for SEW
        private List<DayRow> dailyRows;
        private long[] gradeTotals;        // monthly total per grade (9 elements)
    }

    @Data
    public static class DayRow {
        private LocalDate date;
        private int mp;
        private double wt;
        private String sec;        // e.g. "SEW10"
        private double effSalary;  // from DailyProductionDto
        private double baseRate;   // floor-looked-up from EffIncentiveRate
        private long[] gradeAmounts; // baseRate × multiplier[i] × effectivePct / 100 (9 elements)
    }
}
