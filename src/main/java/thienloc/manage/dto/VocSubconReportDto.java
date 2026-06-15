package thienloc.manage.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SUBCON report (workbook "CEMENT"/"ACTUAL CEMENT"): one row per (date,
 * subcontractor, article) comparing standard usage (output × recipe) to actual,
 * with shortage = standard − actual. Per-chemical cells reuse {@link VocReconcileCellDto}
 * (allowanceKg = standard, diffKg = shortage).
 */
@Data
public class VocSubconReportDto {

    private String selectedMonth;
    private List<String> allMonths = new ArrayList<>();
    private List<String> chemicals = new ArrayList<>();   // chemical columns present
    private List<Row> rows = new ArrayList<>();

    @Data
    @Builder
    public static class Row {
        private Long id;                  // entry id (for delete)
        private LocalDate date;
        private String subcontractor;
        private String articleNo;
        private int output;
        @Builder.Default
        private Map<String, VocReconcileCellDto> cells = new LinkedHashMap<>();
        private double totalStandardKg;
        private double totalActualKg;
        private double totalShortageKg;
        private double vocKg;             // net VOC for the entry = Σ (actual − reuse) × factor
    }
}
