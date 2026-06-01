package thienloc.manage.controller.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.service.IExcelService;
import thienloc.manage.service.IProductionService;
import thienloc.manage.util.ProductionStatsUtil;
import thienloc.manage.util.RecordFilterUtil;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportApiController {

    private final IProductionService productionService;
    private final IExcelService excelService;

    public ReportApiController(IProductionService productionService, IExcelService excelService) {
        this.productionService = productionService;
        this.excelService = excelService;
    }

    @GetMapping
    public ResponseEntity<ReportResponse> get(
            @RequestParam(required = false, defaultValue = "1M") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        LocalDate today = LocalDate.now();
        List<DailyProductionDto> records;
        String rangeLabel;

        switch (range) {
            case "TODAY":
                LocalDate d = (date != null) ? date : today;
                records = productionService.getDashboardData(d);
                rangeLabel = "";
                break;
            case "6M":
                records = productionService.getDashboardDataRange(today.minusMonths(6), today);
                rangeLabel = "Last 6 months (" + today.minusMonths(6) + " → " + today + ")";
                break;
            case "ALL":
                records = productionService.getDashboardDataRange(today.minusMonths(12), today);
                rangeLabel = "Last 12 months (" + today.minusMonths(12) + " → " + today + ")";
                break;
            case "CUSTOM":
                LocalDate from = (dateFrom != null) ? dateFrom : today.minusMonths(1);
                LocalDate to = (dateTo != null) ? dateTo : today;
                records = productionService.getDashboardDataRange(from, to);
                rangeLabel = "Custom (" + from + " → " + to + ")";
                break;
            default:
                records = productionService.getDashboardDataRange(today.minusMonths(1), today);
                rangeLabel = "Last 1 month (" + today.minusMonths(1) + " → " + today + ")";
                break;
        }

        List<String> sections = records.stream()
                .map(DailyProductionDto::getSection).filter(Objects::nonNull)
                .map(s -> s.contains(" ") ? s.split(" ")[0] : s)
                .distinct().sorted().collect(Collectors.toList());
        List<String> lines = records.stream()
                .map(DailyProductionDto::getLine).filter(Objects::nonNull)
                .distinct().sorted().collect(Collectors.toList());

        records = RecordFilterUtil.filterByArticle(records, article);
        records = RecordFilterUtil.filterBySectionPrefix(records, section);
        records = RecordFilterUtil.filterByLineExact(records, line);

        return ResponseEntity.ok(new ReportResponse(
                records, rangeLabel, range,
                date != null ? date : today,
                article, sections, lines, section, line,
                dateFrom != null ? dateFrom : today.minusMonths(1),
                dateTo != null ? dateTo : today,
                ProductionStatsUtil.totalOutput(records),
                ProductionStatsUtil.averageEff(records)));
    }

    @GetMapping("/weekly")
    public ResponseEntity<WeeklyReportResponse> weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDate) {

        if (weekStart == null) {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        }
        LocalDate weekEnd = weekStart.plusDays(7);

        List<WeeklyReportDto> blocks = productionService.getWeeklyReport(weekStart);
        boolean canViewSunday = canViewSunday();

        if (!canViewSunday) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() != null && row.getDate().getDayOfWeek() == DayOfWeek.SUNDAY);
                block.recalculateSummary();
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        if (filterDate != null) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() == null || !row.getDate().equals(filterDate));
                block.recalculateSummary();
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        return ResponseEntity.ok(new WeeklyReportResponse(
                blocks, weekStart, weekEnd,
                weekStart.minusWeeks(1), weekStart.plusWeeks(1),
                canViewSunday, filterDate));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false, defaultValue = "1M") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo)
            throws IOException {

        LocalDate today = LocalDate.now();
        LocalDate from, to;
        List<DailyProductionDto> records;

        switch (range) {
            case "TODAY":
                from = to = (date != null) ? date : today;
                records = productionService.getDashboardData(from);
                break;
            case "6M":
                from = today.minusMonths(6); to = today;
                records = productionService.getDashboardDataRange(from, to);
                break;
            case "ALL":
                from = today.minusMonths(12); to = today;
                records = productionService.getDashboardDataRange(from, to);
                break;
            case "CUSTOM":
                from = (dateFrom != null) ? dateFrom : today.minusMonths(1);
                to = (dateTo != null) ? dateTo : today;
                records = productionService.getDashboardDataRange(from, to);
                break;
            default:
                from = today.minusMonths(1); to = today;
                records = productionService.getDashboardDataRange(from, to);
        }

        records = RecordFilterUtil.filterByArticle(records, article);
        records = RecordFilterUtil.filterBySectionPrefix(records, section);
        records = RecordFilterUtil.filterByLineExact(records, line);

        byte[] bytes = excelService.exportDailyReport(records, from, to).readAllBytes();
        String filename = "Daily_Report_" + from + "_to_" + to + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    @GetMapping("/weekly/export")
    public ResponseEntity<byte[]> weeklyExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart)
            throws IOException {

        if (weekStart == null) {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        }
        List<WeeklyReportDto> blocks = productionService.getWeeklyReport(weekStart);

        if (!canViewSunday()) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() != null && row.getDate().getDayOfWeek() == DayOfWeek.SUNDAY);
                block.recalculateSummary();
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        byte[] bytes = excelService.exportWeeklyReport(blocks, weekStart).readAllBytes();
        String filename = "Weekly_Report_" + weekStart + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private boolean canViewSunday() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }

    public record ReportResponse(
            List<DailyProductionDto> records,
            String rangeLabel,
            String selectedRange,
            LocalDate selectedDate,
            String article,
            List<String> sections,
            List<String> lines,
            String selectedSection,
            String selectedLine,
            LocalDate dateFrom,
            LocalDate dateTo,
            long totalOutput,
            Double avgEff) {}

    public record WeeklyReportResponse(
            List<WeeklyReportDto> blocks,
            LocalDate weekStart,
            LocalDate weekEnd,
            LocalDate prevWeek,
            LocalDate nextWeek,
            boolean canViewSunday,
            LocalDate filterDate) {}
}
