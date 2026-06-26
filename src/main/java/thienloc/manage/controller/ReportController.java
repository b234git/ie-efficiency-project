package thienloc.manage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.service.IExcelService;
import thienloc.manage.service.IProductionService;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.util.ProductionStatsUtil;
import thienloc.manage.util.RecordFilterUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final IProductionService productionService;

    private final IExcelService excelService;

    @GetMapping({ "/", "" })
    public String report(
            @RequestParam(required = false, defaultValue = "1M") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model) {

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
                rangeLabel = "Last 6 months (" + today.minusMonths(6) + " \u2192 " + today + ")";
                break;
            case "ALL":
                records = productionService.getDashboardDataRange(today.minusMonths(12), today);
                rangeLabel = "Last 12 months (" + today.minusMonths(12) + " \u2192 " + today + ")";
                break;
            case "CUSTOM":
                LocalDate from = (dateFrom != null) ? dateFrom : today.minusMonths(1);
                LocalDate to = (dateTo != null) ? dateTo : today;
                records = productionService.getDashboardDataRange(from, to);
                rangeLabel = "Custom (" + from + " \u2192 " + to + ")";
                break;
            default: // 1M
                records = productionService.getDashboardDataRange(today.minusMonths(1), today);
                rangeLabel = "Last 1 month (" + today.minusMonths(1) + " \u2192 " + today + ")";
                break;
        }

        // Extract distinct parent sections for dropdowns (before filtering)
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

        model.addAttribute("records", records);
        model.addAttribute("rangeLabel", rangeLabel);
        model.addAttribute("selectedRange", range);
        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("article", article);
        model.addAttribute("sections", sections);
        model.addAttribute("lines", lines);
        model.addAttribute("selectedSection", section);
        model.addAttribute("selectedLine", line);
        model.addAttribute("dateFrom", dateFrom != null ? dateFrom : today.minusMonths(1));
        model.addAttribute("dateTo", dateTo != null ? dateTo : today);

        // Pre-compute stats (Thymeleaf SpEL does NOT support lambdas)
        model.addAttribute("totalOutput", ProductionStatsUtil.totalOutput(records));
        model.addAttribute("avgEff", ProductionStatsUtil.averageEff(records));
        return "report";

    }

    @GetMapping("/weekly")
    public String weeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDate,
            Model model) {

        if (weekStart == null) {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        }

        LocalDate weekEnd = weekStart.plusDays(7); // Fri→Fri inclusive = 8 days

        List<WeeklyReportDto> blocks = productionService.getWeeklyReport(weekStart);

        // Sunday filter: only ADMIN/MANAGER can view Sunday data
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canViewSunday = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER"));

        if (!canViewSunday) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() != null && row.getDate().getDayOfWeek() == DayOfWeek.SUNDAY);
                block.recalculateSummary();
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        // Date filter: show only a specific day within the week
        if (filterDate != null) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() == null || !row.getDate().equals(filterDate));
                block.recalculateSummary();
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("blocks", blocks);
        model.addAttribute("canViewSunday", canViewSunday);
        model.addAttribute("filterDate", filterDate);

        return "weekly-report";
    }

    @GetMapping("/weekly/export")
    public ResponseEntity<byte[]> exportWeeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart)
            throws java.io.IOException {

        if (weekStart == null) {
            weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        }

        List<WeeklyReportDto> blocks = productionService.getWeeklyReport(weekStart);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canViewSunday = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER"));

        if (!canViewSunday) {
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

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDailyReport(
            @RequestParam(required = false, defaultValue = "1M") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo)
            throws java.io.IOException {

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
                to   = (dateTo  != null) ? dateTo  : today;
                records = productionService.getDashboardDataRange(from, to);
                break;
            default: // 1M
                from = today.minusMonths(1); to = today;
                records = productionService.getDashboardDataRange(from, to);
        }

        records = RecordFilterUtil.filterByArticle(records, article);
        records = RecordFilterUtil.filterBySectionPrefix(records, section);
        records = RecordFilterUtil.filterByLineExact(records, line);

        byte[] bytes = excelService.exportDailyReport(records, from, to).readAllBytes();
        String filename = "Daily_Report_" + from + "_to_" + to + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

}
