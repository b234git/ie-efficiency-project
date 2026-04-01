package thienloc.manage.dto;

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
    private LocalDate productionDate;
    private String section;
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
