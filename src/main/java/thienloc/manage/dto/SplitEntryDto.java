package thienloc.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    // Page 1: Manpower
    private Double mp;
    private Double dli;
    private Double idl;

    // Page 2: Output
    private Double wt;
    private Integer totalOutput;
    private Double rft;

    // Page 3: Allowance & Articles
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
