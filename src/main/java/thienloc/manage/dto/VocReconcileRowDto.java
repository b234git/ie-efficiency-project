package thienloc.manage.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row (per date) of the reconciliation pivot: a cell per chemical column,
 * plus the per-date headline metrics from the "%" sheet.
 */
@Data
public class VocReconcileRowDto {

    private LocalDate date;
    /** Set instead of {@code date} for the EFF per-line pivot (rows keyed by line). */
    private String line;
    private Map<String, VocReconcileCellDto> cells = new LinkedHashMap<>();

    private int output;
    private double vocGrams;

    /** VOC g/pair = vocGrams / output. */
    public double getVocPerPair() {
        return output > 0 ? vocGrams / output : 0.0;
    }
}
