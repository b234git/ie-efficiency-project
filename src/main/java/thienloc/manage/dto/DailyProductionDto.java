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
    private Double mp; // DL - Direct Labor (Manpower)
    private Double dli; // Direct Labor Indirect
    private Double idl; // Indirect Labor
    private Double wt; // Working Time
    private Double rft; // Right First Time (%)
    private String patternNo; // Pattern #
    private String shoeName; // Style
    private Double actualPph; // Actual PPH
    private Double stdPph; // Standard PPH
    // Sheet D KPI fields
    private Double effKpi;
    private Double effSalary;
    // Allowance = % sản lượng cho phép (1.0 = 100%, 0.8 = 80%)
    private Double allowance;
    private String createdAt;
    private String createdBy; // username of creator

    // For calculating stats
    private String ref;
    private Double tct;
    private Double target;
    private Double eff;
    private Double pph;
    private Double gap;

    // Reasons when EFF values cannot be calculated
    private String effKpiReason;
    private String effSalaryReason;
    // Reason when Pattern # / Style cannot be resolved from Master DB
    private String masterDbReason;

    // Tooltip text listing all distinct articles (when multiple)
    private String articleTooltip;

    // Time slots breakdown
    @Builder.Default
    private java.util.List<DailyProductionDetailDto> details = new java.util.ArrayList<>();

    // JSON map of timeSlot -> articleNo for client-side edit population
    private String articlesJson;

    // "ENTRY" for direct DailyProduction, "SPLIT" for rows from SplitEntry
    private String source;

}
