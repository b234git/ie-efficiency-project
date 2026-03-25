package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import thienloc.manage.entity.MasterDb;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewDto {

    private String dataMonth;
    private String filename;
    private int totalRows;
    private int newCount;
    private int updateCount;

    @Builder.Default
    private List<ImportRowPreview> rows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowPreview {
        private String ref;
        private String articleNo;
        private String patternNo;
        private String shoeName;
        private String osCode;
        private String status; // "NEW" or "UPDATE"

        // Full entity ready to save
        private MasterDb entity;
    }
}
