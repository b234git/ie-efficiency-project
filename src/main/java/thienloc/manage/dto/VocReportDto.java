package thienloc.manage.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full VOC report for a selected month: per (date, line) rows + per-chemical totals + headline totals. */
@Data
public class VocReportDto {

    private String selectedMonth;
    private List<String> allMonths = new ArrayList<>();

    private List<VocReportRowDto> rows = new ArrayList<>();
    private List<VocChemicalSummaryDto> chemicals = new ArrayList<>();

    // Reconciliation pivot (% sheet): chemical columns present + weekly blocks + grand total
    private List<String> reconcileChemicals = new ArrayList<>();
    private List<VocReconcileWeekDto> reconcileWeeks = new ArrayList<>();
    private VocReconcileRowDto reconcileTotal;

    // EFF per-line pivot (EFF sheet): one row per line × chemical cells, total + ranking.
    private List<VocReconcileRowDto> byLineRows = new ArrayList<>();
    private VocReconcileRowDto byLineTotal;
    private List<ChemRank> byLineHigh = new ArrayList<>();
    private List<ChemRank> byLineLow = new ArrayList<>();

    /** One ranked chemical: its ratio = Σactual / Σallowance over a week block. */
    public record ChemRank(String code, double ratio) {}

    // Applied filter (echoed back to the view) + option lists derived from the
    // FULL month so changing one filter never starves the other dropdowns.
    private VocReportFilter filter;
    private List<String> allLines = new ArrayList<>();
    private List<String> allSections = new ArrayList<>();
    private List<String> allChemCodes = new ArrayList<>();
    /** Week blocks of the month that have data: week number -> "dd/MM–dd/MM" calendar range. */
    private Map<Integer, String> weekOptions = new LinkedHashMap<>();

    private int totalOutput;
    private double totalVocKg;
    private double totalWaterKg;
    private double totalSolventKg;
    private double totalCost;

    /** Total VOC grams = totalVocKg * 1000. */
    public double getTotalVocGrams() {
        return totalVocKg * 1000.0;
    }

    /** Average VOC g/pair across the month = totalVocGrams / totalOutput. */
    public double getAvgPerPair() {
        return totalOutput > 0 ? getTotalVocGrams() / totalOutput : 0.0;
    }

    /** Water-based share over the month (0..100). */
    public double getWaterPct() {
        double total = totalWaterKg + totalSolventKg;
        return total > 0 ? totalWaterKg / total * 100.0 : 0.0;
    }
}
