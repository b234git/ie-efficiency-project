package thienloc.manage.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * One VOC report row per (date, line). Reproduces the headline columns of the
 * VOC workbook "%" sheet: VOC kg, VOC grams, VOC g/pair, water-based %, cost.
 * Mutable primitives so the service can accumulate across consumption rows.
 */
@Data
public class VocReportRowDto {

    private LocalDate date;
    private String line;
    private int output;

    private double vocKg;        // Σ (quantity - reuse) * vocFactor
    private double waterKg;      // Σ quantity where material_type = WATER
    private double solventKg;    // Σ quantity where material_type = SOLVENT
    private double cost;         // Σ quantity * price_per_kg

    /** VOC grams = vocKg * 1000. */
    public double getVocGrams() {
        return vocKg * 1000.0;
    }

    /** VOC g/pair = vocGrams / output (0 when no output). */
    public double getVocPerPair() {
        return output > 0 ? getVocGrams() / output : 0.0;
    }

    /** Water-based share of total chemical mass, as a percentage (0..100). */
    public double getWaterPct() {
        double total = waterKg + solventKg;
        return total > 0 ? waterKg / total * 100.0 : 0.0;
    }
}
