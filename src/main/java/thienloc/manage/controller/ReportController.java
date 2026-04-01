package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.service.ExcelService;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.service.ProductionService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ProductionService productionService;

    @Autowired
    private ExcelService excelService;

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

        // Extract distinct sections and lines for dropdowns (before filtering)
        List<String> sections = records.stream()
                .map(DailyProductionDto::getSection).filter(Objects::nonNull)
                .distinct().sorted().collect(Collectors.toList());
        List<String> lines = records.stream()
                .map(DailyProductionDto::getLine).filter(Objects::nonNull)
                .distinct().sorted().collect(Collectors.toList());

        // Filter by article if provided
        if (article != null && !article.trim().isEmpty()) {
            String lowerArticle = article.toLowerCase().trim();
            records = records.stream()
                    .filter(r -> (r.getArticle() != null && r.getArticle().toLowerCase().contains(lowerArticle)) ||
                            (r.getDetails() != null && r.getDetails().stream()
                                    .anyMatch(d -> d.getArticleNo() != null
                                            && d.getArticleNo().toLowerCase().contains(lowerArticle))))
                    .collect(Collectors.toList());
        }

        // Filter by section if provided
        if (section != null && !section.trim().isEmpty()) {
            String sec = section.trim();
            records = records.stream().filter(r -> sec.equals(r.getSection())).collect(Collectors.toList());
        }

        // Filter by line if provided
        if (line != null && !line.trim().isEmpty()) {
            String ln = line.trim();
            records = records.stream().filter(r -> ln.equals(r.getLine())).collect(Collectors.toList());
        }

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
        int totalOutput = records.stream().mapToInt(r -> r.getOutput() != null ? r.getOutput() : 0).sum();
        double avgEff = records.stream().filter(r -> r.getEff() != null)
                .mapToDouble(r -> r.getEff()).average().orElse(0);
        long effCount = records.stream().filter(r -> r.getEff() != null).count();
        model.addAttribute("totalOutput", totalOutput);
        model.addAttribute("avgEff", effCount > 0 ? avgEff : null);
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
                recalculateSummary(block);
            }
            blocks.removeIf(b -> b.getDailyRows().isEmpty());
        }

        // Date filter: show only a specific day within the week
        if (filterDate != null) {
            for (WeeklyReportDto block : blocks) {
                block.getDailyRows().removeIf(row ->
                        row.getDate() == null || !row.getDate().equals(filterDate));
                recalculateSummary(block);
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
                recalculateSummary(block);
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

        if (article != null && !article.trim().isEmpty()) {
            String lowerArticle = article.toLowerCase().trim();
            records = records.stream()
                    .filter(r -> (r.getArticle() != null && r.getArticle().toLowerCase().contains(lowerArticle)) ||
                            (r.getDetails() != null && r.getDetails().stream()
                                    .anyMatch(d -> d.getArticleNo() != null
                                            && d.getArticleNo().toLowerCase().contains(lowerArticle))))
                    .collect(Collectors.toList());
        }
        if (section != null && !section.trim().isEmpty()) {
            records = records.stream().filter(r -> section.trim().equals(r.getSection())).collect(Collectors.toList());
        }
        if (line != null && !line.trim().isEmpty()) {
            records = records.stream().filter(r -> line.trim().equals(r.getLine())).collect(Collectors.toList());
        }

        byte[] bytes = excelService.exportDailyReport(records, from, to).readAllBytes();
        String filename = "Daily_Report_" + from + "_to_" + to + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private void recalculateSummary(WeeklyReportDto block) {
        List<WeeklyReportDto.DailyRow> rows = block.getDailyRows();
        int n = rows.size();
        int sumOutput = 0;
        double sumMp = 0, sumWt = 0, sumEff = 0, sumActPph = 0, sumStdPph = 0, sumDli = 0;
        int effCount = 0, stdCount = 0, sumTargetOutput = 0, targetCount = 0;

        for (WeeklyReportDto.DailyRow row : rows) {
            sumOutput += (row.getOutput() != null ? row.getOutput() : 0);
            sumMp += (row.getMp() != null ? row.getMp() : 0);
            sumWt += (row.getWt() != null ? row.getWt() : 0);
            if (row.getActualPph() != null) sumActPph += row.getActualPph();
            if (row.getStdPph() != null) { sumStdPph += row.getStdPph(); stdCount++; }
            if (row.getEff() != null) { sumEff += row.getEff(); effCount++; }
            sumDli += (row.getDli() != null ? row.getDli() : 0);
            if (row.getTargetOutput() != null) { sumTargetOutput += row.getTargetOutput(); targetCount++; }
        }

        WeeklyReportDto.SummaryRow summary = block.getTotal();
        summary.setTotalOutput(sumOutput);
        summary.setDayCount(n);
        summary.setAvgMp(n > 0 ? sumMp / n : 0);
        summary.setAvgWt(n > 0 ? sumWt / n : 0);
        summary.setAvgEff(effCount > 0 ? sumEff / effCount : null);
        summary.setAvgActualPph(n > 0 ? sumActPph / n : null);
        summary.setAvgStdPph(stdCount > 0 ? sumStdPph / stdCount : null);
        summary.setAvgDli(n > 0 ? sumDli / n : 0);
        summary.setTotalTargetOutput(targetCount > 0 ? sumTargetOutput : null);
    }
}
