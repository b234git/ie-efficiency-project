package thienloc.manage.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * One row of the "Actual" consumption log: the user-entered columns
 * (date, line, chemical, kg, reuse) plus the derived columns joined from the
 * chemical master (R sheet) — material base, classification, manufacturer,
 * VOC factor — and the emitted VOC.
 *
 * vocEmittedKg = vocFactor * (quantityKg - reuseKg) — the "Production Total"
 * column of the VOC workbook.
 */
@Data
@Builder
public class VocActualRowDto {

    private Long id;
    private LocalDate date;
    private String line;
    private String chemicalCode;

    // Derived from the chemical master (R sheet)
    private String materialType;     // "Water Base" / "Solvent Base"
    private String classification;   // Adhesive / Hot melt / Primer ...
    private String manufacturer;
    private double vocFactor;

    private double quantityKg;
    private double reuseKg;
    private double vocEmittedKg;      // factor * (qty - reuse)
}
