package thienloc.manage.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotNull(message = "Ngày sản xuất không được để trống")
    private LocalDate productionDate;

    @NotBlank(message = "Section không được để trống")
    private String section;

    @NotBlank(message = "Line không được để trống")
    private String line;

    private String article;
    private Integer output;

    @NotNull(message = "Manpower (MP) không được để trống")
    @Positive(message = "Manpower (MP) phải lớn hơn 0")
    private Double mp; // DL - Direct Labor (Manpower)

    private Double dli; // Direct Labor Indirect
    private Double idl; // Indirect Labor

    @NotNull(message = "Working Time (WT) không được để trống")
    @Positive(message = "Working Time (WT) phải lớn hơn 0")
    private Double wt; // Working Time

    private Double rft; // Right First Time (%)
    private String patternNo; // Pattern #
    private String shoeName; // Style
    private Double actualPph; // Actual PPH
    private Double stdPph; // Standard PPH
    // Sheet D KPI fields
    private Double effKpi;
    private Double effSalary;

    // Allowance = allowed output percentage. Form gửi thang phần trăm (80–100);
    // NormalizationUtil.normalizeAllowance quy đổi >1 về thang 0–1 khi lưu, nên
    // ràng buộc phải chấp nhận cả hai thang đo (decimal 0.1–1.0 và percent 1–100).
    @DecimalMin(value = "0.1", message = "Allowance phải từ 10% trở lên")
    @DecimalMax(value = "100.0", message = "Allowance không được vượt quá 100%")
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
