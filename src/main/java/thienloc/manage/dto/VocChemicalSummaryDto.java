package thienloc.manage.dto;

import lombok.Data;

/**
 * Per-chemical monthly totals — the "by material" view of the VOC report
 * (echoes the SF/SP material-index breakdown).
 */
@Data
public class VocChemicalSummaryDto {

    private String code;
    private String materialType;
    private String classification;

    private double quantityKg;   // Σ quantity
    private double vocKg;         // Σ (quantity - reuse) * vocFactor
    private double cost;          // Σ quantity * price_per_kg
}
