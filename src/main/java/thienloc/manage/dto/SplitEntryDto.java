package thienloc.manage.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitEntryDto {

    private Long id;

    @NotNull(message = "Ngày sản xuất không được để trống")
    private LocalDate productionDate;

    @NotBlank(message = "Section không được để trống")
    private String section;

    @NotBlank(message = "Line không được để trống")
    private String line;

    // Page 1: Manpower (range checks only — wizard pages submit partial DTOs, so null passes)
    @PositiveOrZero(message = "Manpower (MP) không được âm")
    private Double mp;
    private Double dli;
    private Double idl;

    // Page 2: Output
    @PositiveOrZero(message = "Working Time (WT) không được âm")
    private Double wt;
    @PositiveOrZero(message = "Sản lượng (Output) không được âm")
    private Integer totalOutput;
    @DecimalMin(value = "0.0", message = "RFT phải từ 0 đến 100%")
    @DecimalMax(value = "100.0", message = "RFT phải từ 0 đến 100%")
    private Double rft;

    // Page 3: Allowance & Articles
    @DecimalMin(value = "0.1", message = "Allowance phải từ 10% trở lên")
    @DecimalMax(value = "100.0", message = "Allowance không được vượt quá 100%")
    private Double allowance;

    @Builder.Default
    private List<DailyProductionDetailDto> details = new ArrayList<>();

    // Status tracking
    private String status;
    private String source; // "SPLIT" or "DIRECT"
    private boolean manpowerFilled;
    private boolean outputFilled;
    private boolean articlesFilled;
    private String manpowerFilledByUsername;
    private String outputFilledByUsername;
    private String articleFilledByUsername;
}
