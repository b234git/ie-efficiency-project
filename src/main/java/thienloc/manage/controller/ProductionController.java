package thienloc.manage.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.util.ProductionStatsUtil;
import thienloc.manage.util.RecordFilterUtil;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/entry")
@RequiredArgsConstructor
public class ProductionController {

    private static final Logger log = LoggerFactory.getLogger(ProductionController.class);

    private final IProductionService productionService;

    private final thienloc.manage.service.SystemLogService systemLogService;

    private final NotificationService notificationService;

    private final MeterRegistry meterRegistry;

    /* ── Entry Form ───────────────────────────────────────────── */
    @GetMapping({ "", "/" })
    public String showEntryForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article,
            @RequestParam(required = false, defaultValue = "false") boolean errorsOnly,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "") String section,
            @RequestParam(required = false, defaultValue = "") String line,
            @RequestParam(required = false, defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
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

        // ── Resolve date range trước để truyền vào service một lần ──────
        java.time.YearMonth selectedYearMonth = java.time.YearMonth.from(today);
        if ("MONTH".equals(range) && month != null && !month.isBlank()) {
            try {
                selectedYearMonth = java.time.YearMonth.parse(month);
            } catch (Exception e) {
                log.warn("Invalid month parameter '{}', falling back to current month", month);
            }
        }

        if (date == null) date = today;
        final LocalDate effectiveDate = date;
        final java.time.YearMonth ym = selectedYearMonth;
        LocalDate[] dateRange = switch (range) {
            case "1M"    -> new LocalDate[]{ today.minusMonths(1),       today };
            case "6M"    -> new LocalDate[]{ today.minusMonths(6),       today };
            case "ALL"   -> new LocalDate[]{ today.minusMonths(12),      today };
            case "MONTH" -> new LocalDate[]{ ym.atDay(1), ym.atEndOfMonth() };
            default      -> new LocalDate[]{ effectiveDate, effectiveDate };
        };
        LocalDate from = dateRange[0];
        LocalDate to   = dateRange[1];

        // ── DB-level pagination: section/line lọc tại DB ─────────────────
        // article, errorsOnly, source lọc in-memory chỉ trên bản ghi của trang
        int[] allowed = {25, 50, 100, 150, 1000};
        boolean valid = false;
        for (int v : allowed) if (v == pageSize) { valid = true; break; }
        if (!valid) pageSize = 25;
        Page<DailyProductionDto> recordPage = productionService.getMyDataRangeWithSplitEntriesPaged(
                username, from, to, section, line, page, pageSize);

        List<DailyProductionDto> pagedRecords = new java.util.ArrayList<>(recordPage.getContent());
        pagedRecords = RecordFilterUtil.filterByArticle(pagedRecords, article);
        if (errorsOnly) pagedRecords = RecordFilterUtil.filterErrorsOnly(pagedRecords);
        pagedRecords = RecordFilterUtil.filterBySource(pagedRecords, source);

        int totalRecords = (int) recordPage.getTotalElements();
        int totalPages   = recordPage.getTotalPages();
        page = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        model.addAttribute("entries", pagedRecords);
        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("selectedRange", range);
        model.addAttribute("selectedMonth", selectedYearMonth.toString());
        model.addAttribute("article", article);
        model.addAttribute("errorsOnly", errorsOnly);
        model.addAttribute("selectedSection", section);
        model.addAttribute("selectedLine", line);
        model.addAttribute("selectedSource", source);
        java.util.List<String> sectionOptions = java.util.Arrays.stream(thienloc.manage.service.SectionMetrics.values())
                .map(thienloc.manage.service.SectionMetrics::getSectionName)
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("sectionOptions", sectionOptions);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalRecords", totalRecords);
        model.addAttribute("pageSize", pageSize);

        model.addAttribute("totalOutput", ProductionStatsUtil.totalOutput(pagedRecords));
        model.addAttribute("avgEff", ProductionStatsUtil.averageEff(pagedRecords));

        return "entry";
    }

    /** Session key holding a submitted entry that collided with an existing record, awaiting confirm. */
    private static final String DUP_PENDING = "entryDuplicatePending";

    /* ── Save Entry ───────────────────────────────────────────── */
    @PostMapping("/save")
    public String saveEntry(@Valid @ModelAttribute DailyProductionDto dto,
                            BindingResult result,
                            RedirectAttributes redirectAttributes,
                            Principal principal,
                            HttpSession session,
                            HttpServletRequest request) {
        if (result.hasErrors()) {
            meterRegistry.counter("validation.errors", "form", "entry").increment();
            String errorMsg = result.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst().orElse("Dữ liệu nhập không hợp lệ.");
            redirectAttributes.addFlashAttribute("validationError", errorMsg);
            return "redirect:/entry/?error";
        }
        try {
            productionService.saveDailyProduction(dto, principal.getName());
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("validationError", ex.getMessage());
            return "redirect:/entry/?error";
        } catch (thienloc.manage.exception.DuplicateRecordException ex) {
            // A record for this (date, section, line) already exists — stash the
            // submission and ask the user whether to overwrite it.
            session.setAttribute(DUP_PENDING, dto);
            redirectAttributes.addFlashAttribute("duplicateConflict", ex.getMessage());
            return "redirect:/entry/?duplicate";
        }
        systemLogService.logAction("ADD_ENTRY",
                "Section=" + dto.getSection() + ", Article=" + dto.getArticle(), request);
        return "redirect:/entry/?success";
    }

    /* ── Confirm overwrite of a duplicate (date, section, line) ── */
    @PostMapping("/save/confirm")
    public String confirmDuplicateSave(RedirectAttributes redirectAttributes,
                                       Principal principal,
                                       HttpSession session,
                                       HttpServletRequest request) {
        DailyProductionDto dto = (DailyProductionDto) session.getAttribute(DUP_PENDING);
        session.removeAttribute(DUP_PENDING);
        if (dto == null) {
            return "redirect:/entry/";
        }
        try {
            productionService.saveDailyProduction(dto, principal.getName(), true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("validationError", ex.getMessage());
            return "redirect:/entry/?error";
        }
        systemLogService.logAction("EDIT_ENTRY",
                "Overwrite duplicate | Date=" + dto.getProductionDate()
                        + ", Section=" + dto.getSection() + ", Line=" + dto.getLine(), request);
        return "redirect:/entry/?edited";
    }

    /* ── Cancel a pending duplicate overwrite ─────────────────── */
    @PostMapping("/save/cancel")
    public String cancelDuplicateSave(HttpSession session) {
        session.removeAttribute(DUP_PENDING);
        return "redirect:/entry/";
    }

    /* ── Edit Entry (MANAGER / ADMIN only) ────────────────────── */
    @PostMapping("/edit")
    public String editEntry(@Valid @ModelAttribute DailyProductionDto dto,
                            BindingResult result,
                            RedirectAttributes redirectAttributes,
                            Principal principal,
                            HttpServletRequest request) {
        if (result.hasErrors()) {
            meterRegistry.counter("validation.errors", "form", "entry").increment();
            String errorMsg = result.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst().orElse("Dữ liệu nhập không hợp lệ.");
            redirectAttributes.addFlashAttribute("validationError", errorMsg);
            return "redirect:/entry/?error";
        }
        DailyProductionDto before = productionService.getById(dto.getId());
        try {
            productionService.saveDailyProduction(dto, principal.getName());
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("validationError", ex.getMessage());
            return "redirect:/entry/?error";
        }
        systemLogService.logAction("EDIT_ENTRY",
                "ID=" + dto.getId()
                        + " | before: output=" + before.getOutput() + ", article=" + before.getArticle()
                        + " | after: output=" + dto.getOutput() + ", article=" + dto.getArticle(),
                request);
        return "redirect:/entry/?edited";
    }

    /* ── Delete Entry (MANAGER / ADMIN only) ──────────────────── */
    @PostMapping("/admin-delete")
    public String adminDeleteEntry(@RequestParam Long id, HttpServletRequest request) {
        DailyProductionDto before = productionService.getById(id);
        productionService.deleteRecord(id);
        systemLogService.logAction("DELETE_ENTRY",
                "ID=" + id + " | Section=" + before.getSection()
                        + ", Line=" + before.getLine()
                        + ", Date=" + before.getProductionDate()
                        + ", Article=" + before.getArticle()
                        + ", Output=" + before.getOutput(),
                request);
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
    public String bulkDelete(@RequestParam List<Long> ids, HttpServletRequest request) {
        ids.forEach(productionService::deleteRecord);
        systemLogService.logAction("BULK_DELETE_ENTRY",
                "Bulk deleted " + ids.size() + " entries: " + ids, request);
        return "redirect:/entry/?deleted";
    }

    /* ── JSON sibling: single delete with diagnostic response ─ */
    // @Deprecated — use DELETE /api/v1/entries/{id}
    @PostMapping("/admin-delete.json")
    @ResponseBody
    public Map<String, Object> adminDeleteEntryJson(@RequestParam Long id, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> deleted = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        try {
            DailyProductionDto before = null;
            try { before = productionService.getById(id); } catch (Exception ignore) { /* missing → handled below */ }
            boolean removed = productionService.deleteIfPresent(id);
            if (removed) {
                deleted.add(id);
                String detail = before == null ? ("ID=" + id)
                        : "ID=" + id + " | Section=" + before.getSection()
                          + ", Line=" + before.getLine()
                          + ", Date=" + before.getProductionDate()
                          + ", Article=" + before.getArticle()
                          + ", Output=" + before.getOutput();
                systemLogService.logAction("DELETE_ENTRY", detail, request);
            } else {
                missing.add(id);
            }
        } catch (Exception ex) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", id);
            f.put("reason", ex.getMessage());
            failed.add(f);
        }
        result.put("deleted", deleted);
        result.put("missing", missing);
        result.put("failed", failed);
        return result;
    }

    /* ── JSON sibling: bulk delete with per-id status ────────── */
    // @Deprecated — use POST /api/v1/entries/bulk-delete
    @PostMapping("/bulk-delete.json")
    @ResponseBody
    public Map<String, Object> bulkDeleteJson(@RequestParam("ids") List<Long> ids, HttpServletRequest request) {
        List<Long> deleted = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                try {
                    if (productionService.deleteIfPresent(id)) deleted.add(id);
                    else missing.add(id);
                } catch (Exception ex) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("id", id);
                    f.put("reason", ex.getMessage());
                    failed.add(f);
                }
            }
        }
        systemLogService.logAction("BULK_DELETE_ENTRY",
                "Bulk delete: deleted=" + deleted + ", missing=" + missing + ", failed=" + failed.size(),
                request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("missing", missing);
        result.put("failed", failed);
        return result;
    }

    /* ── Filtered IDs for "select all matching" ───────────────── */
    // @Deprecated — use GET /api/v1/entries/admin-deletable-ids
    @GetMapping("/api/filtered-ids")
    @ResponseBody
    public List<Long> getFilteredIds(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "") String section,
            @RequestParam(required = false, defaultValue = "") String line,
            Principal principal) {
        String username = principal.getName();
        LocalDate today = LocalDate.now();
        java.time.YearMonth ym = java.time.YearMonth.from(today);
        if ("MONTH".equals(range) && month != null && !month.isBlank()) {
            try { ym = java.time.YearMonth.parse(month); } catch (Exception ignored) {}
        }
        if (date == null) date = today;
        final LocalDate effectiveDate = date;
        final java.time.YearMonth finalYm = ym;
        LocalDate[] dr = switch (range) {
            case "1M"    -> new LocalDate[]{ today.minusMonths(1),  today };
            case "6M"    -> new LocalDate[]{ today.minusMonths(6),  today };
            case "ALL"   -> new LocalDate[]{ today.minusMonths(12), today };
            case "MONTH" -> new LocalDate[]{ finalYm.atDay(1), finalYm.atEndOfMonth() };
            default      -> new LocalDate[]{ effectiveDate, effectiveDate };
        };
        return productionService.getFilteredIds(username, dr[0], dr[1], section, line);
    }

    /* ── Delete own entry ─────────────────────────────────────── */
    @PostMapping("/delete")
    public String deleteMyEntry(@RequestParam Long id,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article,
            @RequestParam(required = false, defaultValue = "") String section,
            @RequestParam(required = false, defaultValue = "") String line,
            @RequestParam(required = false, defaultValue = "ALL") String source,
            Principal principal) {
        productionService.deleteOwnRecord(id, principal.getName());
        String redirect = "redirect:/entry/?deleted&range=" + range;
        if (date != null)
            redirect += "&date=" + date;
        if (article != null && !article.trim().isEmpty())
            redirect += "&article=" + article;
        if (!section.trim().isEmpty())
            redirect += "&section=" + section;
        if (!line.trim().isEmpty())
            redirect += "&line=" + line;
        if (!"ALL".equals(source))
            redirect += "&source=" + source;
        return redirect;
    }
}
