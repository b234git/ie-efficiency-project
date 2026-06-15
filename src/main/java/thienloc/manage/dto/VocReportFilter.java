package thienloc.manage.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Filters for the VOC monthly report. All narrowing happens BEFORE aggregation,
 * so every tab/card reflects the same slice — like the % sheet where $A$1
 * filters the whole sheet. Empty/null members mean "no filter".
 *
 * week is the workbook's 7-day block anchored at day 1 of the month
 * (days 1–7 = week 1, 8–14 = week 2, …), not an ISO week.
 */
public record VocReportFilter(String month, LocalDate from, LocalDate to, Integer week,
                              String section, String line, List<String> chems) {

    public static VocReportFilter ofMonth(String month) {
        return new VocReportFilter(month, null, null, null, null, null, null);
    }

    public boolean hasSection() { return section != null && !section.isBlank(); }
    public boolean hasLine()    { return line != null && !line.isBlank(); }
    public boolean hasChems()   { return chems != null && !chems.isEmpty(); }
}
