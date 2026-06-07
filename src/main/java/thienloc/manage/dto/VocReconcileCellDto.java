package thienloc.manage.dto;

import lombok.Data;

/**
 * One cell of the reconciliation pivot (% sheet): actual vs allowance for a
 * (date, chemical). status: OK (ratio ≤ 1), OVER (ratio > 1),
 * NC (allowance but no actual), NA (actual but no recipe/allowance).
 *
 * allowanceKg has NO buffer — it is output × standard recipe, exactly like the
 * % sheet (which reads the numbered sheets 1–21). ratio and diffKg therefore
 * match the % sheet's two rows per date: ratio = actual/allowance, diff = allowance−actual.
 */
@Data
public class VocReconcileCellDto {

    private double actualKg;
    private double allowanceKg;
    private double diffKg;     // allowanceKg − actualKg (% sheet "Difference" row)
    private Double ratio;      // actualKg / allowanceKg; null when status is NC/NA
    private String status;     // OK | OVER | NC | NA
}
