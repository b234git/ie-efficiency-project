package thienloc.manage.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Full VOC report for a selected month: per (date, line) rows + per-chemical totals + headline totals. */
@Data
public class VocReportDto {

    private String selectedMonth;
    private List<String> allMonths = new ArrayList<>();

    private List<VocReportRowDto> rows = new ArrayList<>();
    private List<VocChemicalSummaryDto> chemicals = new ArrayList<>();

    // Reconciliation pivot (% sheet): chemical columns present + per-date rows
    private List<String> reconcileChemicals = new ArrayList<>();
    private List<VocReconcileRowDto> reconcileRows = new ArrayList<>();

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
