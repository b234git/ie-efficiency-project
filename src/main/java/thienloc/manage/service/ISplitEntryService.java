package thienloc.manage.service;

import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.entity.SplitEntry;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Contract for split-entry (3-page form) operations.
 * Controllers depend on this interface, not the concrete {@link SplitEntryService}.
 */
public interface ISplitEntryService {

    SplitEntry saveManpower(SplitEntryDto dto, String username);

    SplitEntry saveOutput(SplitEntryDto dto, String username);

    SplitEntry saveArticles(SplitEntryDto dto, String username);

    void deleteEntry(Long id);

    void deleteMultiple(List<Long> ids);

    Optional<SplitEntryDto> getByDateSectionLine(LocalDate date, String section, String line);

    List<SplitEntryDto> getEntriesForDate(LocalDate date);

    List<SplitEntryDto> getEntriesForMonth(YearMonth month);
}
