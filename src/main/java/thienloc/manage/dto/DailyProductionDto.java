package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyProductionDto {
    private Long id;
    private LocalDate productionDate;
    private String section;
    private String line;
    private String article;
    private Integer output;
    private Double mp; // Manpower
    private Double wt; // Working Time
    private String createdAt;

    // For calculating stats
    private String ref; // section + line (e.g. SEW1A or ASSY1)
    private Double tct;
    private Double target;
    private Double eff;
    private Double pph;

    // Time slots breakdown
    @Builder.Default
    private java.util.List<DailyProductionDetailDto> details = new java.util.ArrayList<>();
}
