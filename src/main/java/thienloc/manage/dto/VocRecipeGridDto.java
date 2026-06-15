package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Wide-matrix view of the VOC "DB" sheet: one {@link Row} per article, the
 * chemical codes as columns, and a {@link Cell} (evaluated kg/pair + the raw
 * reciprocal formula) at each intersection. Mirrors the workbook layout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocRecipeGridDto {

    private List<String> chemicals;       // chemical codes — the column order
    private List<String> manufacturers;   // tier 1, one entry per chemical (repeated, not merged)
    private List<String> bases;           // tier 2, one entry per chemical
    private List<String> types;           // tier 3, one entry per chemical
    private Map<String, Double> prices;   // chemicalCode -> $/kg (for live cost in the editor)
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private String articleNo;
        private String modelCode;
        private String modelName;
        private Double baseE;
        private Double baseF;
        private Map<String, Cell> cells;    // chemicalCode -> cell (kg/pair)
        private Map<String, Double> costs;  // chemicalCode -> cost $/pair (kg/pair × price)
        private Double totalCost;           // Σ cost across chemicals
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cell {
        private Long id;
        private Double kgPerPair;
        private String formula;
    }
}
