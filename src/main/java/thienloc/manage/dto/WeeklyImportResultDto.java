package thienloc.manage.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WeeklyImportResultDto {
    private int inserted;
    private int updated;
    private int skipped;
    private List<String> errors = new ArrayList<>();

    public String toFlashMessage() {
        String base = inserted + " inserted, " + updated + " updated, " + skipped + " skipped";
        if (!errors.isEmpty()) {
            base += " | Error: " + errors.get(0);
            if (errors.size() > 1) base += " (+" + (errors.size() - 1) + " more)";
        }
        return base;
    }
}
