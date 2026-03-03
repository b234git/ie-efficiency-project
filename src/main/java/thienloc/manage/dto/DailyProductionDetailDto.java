package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyProductionDetailDto {
    private Long id;
    private String timeSlot;
    private String articleNo;
    private Integer output;
}
