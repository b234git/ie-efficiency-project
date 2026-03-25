package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.service.ProductionService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ProductionService productionService;

    @GetMapping({ "/", "" })
    public String report(
            @RequestParam(required = false, defaultValue = "1M") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String pattern,
            @RequestParam(required = false, defaultValue = "") String filterDate,
            @RequestParam(required = false, defaultValue = "") String filterSection,
            @RequestParam(required = false, defaultValue = "") String filterLineNum,
            @RequestParam(required = false, defaultValue = "") String filterLineChar,
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
                rangeLabel = "Last 12 months (" + today.minusMonths(12) + " → " + today + ")";
                break;
            default: // 1M
                records = productionService.getDashboardDataRange(today.minusMonths(1), today);
                rangeLabel = "Last 1 month (" + today.minusMonths(1) + " \u2192 " + today + ")";
                break;
        }

        // ── Extract dropdown options in single pass (before filtering) ─────────
        Set<LocalDate> dateSet = new TreeSet<>(Comparator.reverseOrder());
        Set<String> sectionSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<Long> lineNumSet = new TreeSet<>();
        for (DailyProductionDto r : records) {
            if (r.getProductionDate() != null) dateSet.add(r.getProductionDate());
            if (r.getSection() != null && !r.getSection().trim().isEmpty()) sectionSet.add(r.getSection());
            if (r.getLine() != null) {
                String numPart = r.getLine().replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) lineNumSet.add(Long.parseLong(numPart));
            }
        }
        List<LocalDate> availableDates = new ArrayList<>(dateSet);
        List<String> availableSections = new ArrayList<>(sectionSet);
        List<String> availableLineNums = lineNumSet.stream().map(String::valueOf).collect(Collectors.toList());

        // ── Build composite filter & apply in single pass ─────────────────────
        Predicate<DailyProductionDto> filter = r -> true;

        if (article != null && !article.trim().isEmpty()) {
            String lowerArticle = article.toLowerCase().trim();
            filter = filter.and(r ->
                    (r.getArticle() != null && r.getArticle().toLowerCase().contains(lowerArticle)) ||
                    (r.getShoeName() != null && r.getShoeName().toLowerCase().contains(lowerArticle)) ||
                    (r.getDetails() != null && r.getDetails().stream()
                            .anyMatch(d -> d.getArticleNo() != null && d.getArticleNo().toLowerCase().contains(lowerArticle))));
        }
        if (pattern != null && !pattern.trim().isEmpty()) {
            String lowerPattern = pattern.toLowerCase().trim();
            filter = filter.and(r -> r.getPatternNo() != null && r.getPatternNo().toLowerCase().contains(lowerPattern));
        }
        if (filterDate != null && !filterDate.isEmpty()) {
            try {
                LocalDate fd = LocalDate.parse(filterDate);
                filter = filter.and(r -> fd.equals(r.getProductionDate()));
            } catch (Exception e) { /* ignore invalid date */ }
        }
        if (filterSection != null && !filterSection.isEmpty()) {
            filter = filter.and(r -> filterSection.equalsIgnoreCase(r.getSection()));
        }
        if (filterLineNum != null && !filterLineNum.isEmpty()) {
            filter = filter.and(r -> {
                if (r.getLine() == null) return false;
                return r.getLine().replaceAll("[^0-9]", "").equals(filterLineNum);
            });
        }
        if (filterLineChar != null && !filterLineChar.isEmpty()) {
            filter = filter.and(r -> {
                if (r.getLine() == null) return false;
                return r.getLine().replaceAll("[0-9]", "").trim()
                        .toLowerCase().contains(filterLineChar.toLowerCase());
            });
        }

        records = records.stream()
                .filter(filter)
                .sorted(Comparator.comparing(DailyProductionDto::getProductionDate,
                        Comparator.nullsLast(LocalDate::compareTo)).reversed())
                .collect(Collectors.toList());

        model.addAttribute("records", records);
        model.addAttribute("rangeLabel", rangeLabel);
        model.addAttribute("selectedRange", range);
        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("article", article);
        model.addAttribute("pattern", pattern);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("availableSections", availableSections);
        model.addAttribute("availableLineNums", availableLineNums);
        model.addAttribute("filterDate", filterDate);
        model.addAttribute("filterSection", filterSection);
        model.addAttribute("filterLineNum", filterLineNum);
        model.addAttribute("filterLineChar", filterLineChar);

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

    private void recalculateSummary(WeeklyReportDto block) {
        List<WeeklyReportDto.DailyRow> rows = block.getDailyRows();
        int n = rows.size();
        int sumOutput = 0;
        double sumMp = 0, sumWt = 0, sumEff = 0, sumActPph = 0, sumStdPph = 0;
        int effCount = 0, stdCount = 0;

        for (WeeklyReportDto.DailyRow row : rows) {
            sumOutput += (row.getOutput() != null ? row.getOutput() : 0);
            sumMp += (row.getMp() != null ? row.getMp() : 0);
            sumWt += (row.getWt() != null ? row.getWt() : 0);
            if (row.getActualPph() != null) sumActPph += row.getActualPph();
            if (row.getStdPph() != null) { sumStdPph += row.getStdPph(); stdCount++; }
            if (row.getEff() != null) { sumEff += row.getEff(); effCount++; }
        }

        WeeklyReportDto.SummaryRow summary = block.getTotal();
        summary.setTotalOutput(sumOutput);
        summary.setDayCount(n);
        summary.setAvgMp(n > 0 ? sumMp / n : 0);
        summary.setAvgWt(n > 0 ? sumWt / n : 0);
        summary.setAvgEff(effCount > 0 ? sumEff / effCount : null);
        summary.setAvgActualPph(n > 0 ? sumActPph / n : null);
        summary.setAvgStdPph(stdCount > 0 ? sumStdPph / stdCount : null);
    }
}
