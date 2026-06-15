package thienloc.manage.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One 7-day block of the reconciliation pivot, mirroring the % sheet layout:
 * date rows, a weekly Total row (same NC/NA logic applied to the weekly sums),
 * and the week's HIGH/LOW consumption ranking (LARGE/SMALL over the Total row).
 */
@Data
public class VocReconcileWeekDto {

    /** "dd/MM–dd/MM" over the dates actually present in the block (like %!AD2). */
    private String label;

    private List<VocReconcileRowDto> rows = new ArrayList<>();

    /** Weekly Total: cells from Σactual/Σallowance, output/vocGrams summed. date is null. */
    private VocReconcileRowDto totalRow;

    /** Ranking from the Total row ratios: HIGH ≥ 1.1 desc, LOW ≤ 0.9 asc. */
    private List<VocReportDto.ChemRank> high = new ArrayList<>();
    private List<VocReportDto.ChemRank> low = new ArrayList<>();
}
