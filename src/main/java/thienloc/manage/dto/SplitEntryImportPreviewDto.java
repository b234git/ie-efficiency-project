package thienloc.manage.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitEntryImportPreviewDto implements Serializable {

    private String filename;
    private String importType; // "OUTPUT" or "ARTICLES"
    private int totalRows;
    private int validRows;
    private int errorRows;
    /** Of the valid rows, how many target a new (date,section,line) vs an existing one. */
    private int newCount;
    private int updateCount;

    @Builder.Default
    private List<RowPreview> rows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowPreview implements Serializable {
        private int rowNum;
        private LocalDate productionDate;
        private String section;
        private String line;

        // Output fields
        private Double wt;
        private Integer totalOutput;
        private Double rft;

        // Articles fields
        private Double allowance;
        private int articleCount;
        private Map<String, String> articles; // timeSlot -> articleNo

        private boolean valid;
        private String errorMessage;
        private String status; // "NEW" or "UPDATE" (valid rows only)
    }
}
