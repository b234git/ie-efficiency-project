package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.ProductionService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/entry")
public class ProductionController {

    @Autowired
    private ProductionService productionService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    @Autowired
    private NotificationService notificationService;

    /* ── Entry Form ───────────────────────────────────────────── */
    @GetMapping({ "", "/" })
    public String showEntryForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article,
            @RequestParam(required = false, defaultValue = "false") boolean errorsOnly,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            Principal principal) {

        DailyProductionDto dto = new DailyProductionDto();
        dto.setProductionDate(LocalDate.now());

        java.util.List<String> timeSlots = java.util.Arrays.asList(
                "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
                "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
                "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
                "19:00-20:00", "20:00-21:00", "21:00-22:00");
        for (String slot : timeSlots) {
            thienloc.manage.dto.DailyProductionDetailDto detail = new thienloc.manage.dto.DailyProductionDetailDto();
            detail.setTimeSlot(slot);
            dto.getDetails().add(detail);
        }

        model.addAttribute("production", dto);

        // ── Filtered history (current user only) ────────────────────────
        String username = principal.getName();
        LocalDate today = LocalDate.now();
        List<DailyProductionDto> records;

        java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(today);
        if ("MONTH".equals(range) && month != null && !month.isBlank()) {
            try { selectedYearMonth = java.time.YearMonth.parse(month); } catch (Exception ignored) {}
        }

        switch (range) {
            case "1M":
                records = productionService.getMyDataRange(username, today.minusMonths(1), today);
                break;
            case "6M":
                records = productionService.getMyDataRange(username, today.minusMonths(6), today);
                break;
            case "ALL":
                records = productionService.getMyDataRange(username, today.minusMonths(12), today);
                break;
            case "MONTH":
                records = productionService.getMyDataRange(username,
                        selectedYearMonth.atDay(1), selectedYearMonth.atEndOfMonth());
                break;
            default:
                if (date == null) date = today;
                records = productionService.getMyDataRange(username, date, date);
                break;
        }

        if (article != null && !article.trim().isEmpty()) {
            String lowerArticle = article.toLowerCase().trim();
            records = records.stream()
                    .filter(r -> (r.getArticle() != null && r.getArticle().toLowerCase().contains(lowerArticle)) ||
                            (r.getDetails() != null && r.getDetails().stream()
                                    .anyMatch(d -> d.getArticleNo() != null
                                            && d.getArticleNo().toLowerCase().contains(lowerArticle))))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (errorsOnly) {
            records = records.stream()
                    .filter(r -> r.getEffKpi() == null)
                    .collect(java.util.stream.Collectors.toList());
        }

        // ── Pagination ───────────────────────────────────────────────────────
        int pageSize = 25;
        int totalRecords = records.size();
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        page = Math.max(0, totalPages > 0 ? Math.min(page, totalPages - 1) : 0);
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalRecords);
        List<DailyProductionDto> pagedRecords = records.subList(fromIndex, toIndex);

        model.addAttribute("entries", pagedRecords);
        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("selectedRange", range);
        model.addAttribute("selectedMonth", selectedYearMonth.toString());
        model.addAttribute("article", article);
        model.addAttribute("errorsOnly", errorsOnly);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalRecords", totalRecords);
        model.addAttribute("pageSize", pageSize);

        int totalOutput = records.stream().mapToInt(r -> r.getOutput() != null ? r.getOutput() : 0).sum();
        long effCount = records.stream().filter(r -> r.getEff() != null).count();
        double avgEff = records.stream().filter(r -> r.getEff() != null).mapToDouble(r -> r.getEff()).average().orElse(0);
        model.addAttribute("totalOutput", totalOutput);
        model.addAttribute("avgEff", effCount > 0 ? avgEff : null);

        return "entry";
    }

    /* ── Save Entry ───────────────────────────────────────────── */
    @PostMapping("/save")
    public String saveEntry(DailyProductionDto dto, Principal principal) {
        productionService.saveDailyProduction(dto, principal.getName());
        systemLogService.logAction("ADD_ENTRY",
                "Added production entry for Section: " + dto.getSection() + ", Article: " + dto.getArticle());
        return "redirect:/entry/?success";
    }

    /* ── Edit Entry (MANAGER / ADMIN only) ────────────────────── */
    @PostMapping("/edit")
    public String editEntry(@ModelAttribute DailyProductionDto dto, Principal principal) {
        productionService.saveDailyProduction(dto, principal.getName());
        systemLogService.logAction("EDIT_ENTRY",
                "Edited production entry ID: " + dto.getId()
                        + ", Section: " + dto.getSection()
                        + ", Line: " + dto.getLine());
        return "redirect:/entry/?edited";
    }

    /* ── Delete Entry (MANAGER / ADMIN only) ──────────────────── */
    @PostMapping("/admin-delete")
    public String adminDeleteEntry(@RequestParam Long id) {
        productionService.deleteRecord(id);
        systemLogService.logAction("DELETE_ENTRY", "Deleted production entry ID: " + id);
        return "redirect:/entry/?deleted";
    }

    /* ── Request Edit (USER only — sends notification) ────────── */
    @PostMapping("/request-edit")
    public String requestEdit(@RequestParam Long id,
            @RequestParam String reason,
            Principal principal) {
        DailyProductionDto record = productionService.getById(id);
        String title = "Edit Request - Entry #" + id + " by " + principal.getName();
        String message = "User '" + principal.getName() + "' requests edit for Entry #" + id
                + "\nDate: " + record.getProductionDate()
                + " | Section: " + record.getSection()
                + " | Line: " + record.getLine()
                + " | Output: " + record.getOutput()
                + "\n\nReason: " + reason;
        notificationService.notifyAdminAndManager(title, message, "INFO");
        return "redirect:/entry/?requestSent";
    }

    /* ── Bulk Delete (MANAGER / ADMIN only) ──────────────────── */
    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam List<Long> ids) {
        ids.forEach(productionService::deleteRecord);
        systemLogService.logAction("BULK_DELETE_ENTRY",
                "Bulk deleted " + ids.size() + " entries: " + ids);
        return "redirect:/entry/?deleted";
    }

    /* ── Delete own entry ─────────────────────────────────────── */
    @PostMapping("/delete")
    public String deleteMyEntry(@RequestParam Long id,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article) {
        productionService.deleteRecord(id);
        String redirect = "redirect:/entry/?deleted&range=" + range;
        if (date != null)
            redirect += "&date=" + date;
        if (article != null && !article.trim().isEmpty())
            redirect += "&article=" + article;
        return redirect;
    }
}
