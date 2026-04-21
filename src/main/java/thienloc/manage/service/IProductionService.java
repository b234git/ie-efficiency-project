package thienloc.manage.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Contract for production data CRUD and reporting operations.
 * Controllers depend on this interface, not the concrete {@link ProductionService}.
 */
public interface IProductionService {

    Long saveDailyProduction(DailyProductionDto dto, String username);

    List<DailyProductionDto> getDashboardData(LocalDate date);

    List<DailyProductionDto> getDashboardDataRange(LocalDate from, LocalDate to);

    List<DailyProductionDto> getMyDataRange(String username, LocalDate from, LocalDate to);

    List<DailyProductionDto> getMyDataRangeWithSplitEntries(String username, LocalDate from, LocalDate to);

    /**
     * DB-level paginated version của getMyDataRangeWithSplitEntries.
     * section/line được lọc tại DB (Pass 1 query); article/errorsOnly/source lọc in-memory trên trang.
     */
    Page<DailyProductionDto> getMyDataRangeWithSplitEntriesPaged(
            String username, LocalDate from, LocalDate to,
            String section, String line, int page, int size);

    Page<DailyProductionDto> getUserEntries(String username, Pageable pageable);

    Page<DailyProductionDto> getAllEntries(Pageable pageable);

    DailyProductionDto getById(Long id);

    void deleteRecord(Long id);

    void deleteMultipleRecords(List<Long> ids);

    void deleteOwnRecord(Long id, String username);

    List<WeeklyReportDto> getWeeklyReport(LocalDate weekStart);

    List<String> getDistinctMonths();
}
