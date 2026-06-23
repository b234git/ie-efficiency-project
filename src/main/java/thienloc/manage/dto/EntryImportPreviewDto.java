package thienloc.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryImportPreviewDto {

    private String filename;
    private int totalRows;
    private int validRows;
    private int errorRows;
    /** Of the valid rows, how many are new inserts vs updates of an existing (date,section,line). */
    private int newCount;
    private int updateCount;

    @Builder.Default
    private List<RowPreview> rows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowPreview {
        private int rowNum;
        private LocalDate productionDate;
        private String section;
        private String line;
        private Double mp;
        private Double dli;
        private Double idl;
        private Double wt;
        private Integer totalOutput;
        private Double rft;
        private Double allowance;
        private String mainArticle; // from ARTICLE column
        private int articleCount;
        private Map<String, String> articles; // timeSlot -> articleNo

        private boolean valid;
        private String errorMessage;
        private String status; // "NEW" or "UPDATE" (only meaningful for valid rows)
    }
}
