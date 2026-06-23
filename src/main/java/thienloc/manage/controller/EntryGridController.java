package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.service.IEfficiencyCalculatorService;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.LineAssignmentService;
import thienloc.manage.service.SectionMetrics;
import thienloc.manage.service.SystemLogService;
import thienloc.manage.util.EntryExcelLayout;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast multi-line daily entry grid. Where {@code /entry} captures one record at a
 * time, this page lets a user pick a date once and type/edit every production line
 * for that day in a spreadsheet-like table, then save them all in one submit — the
 * ergonomic replacement for typing rows into the Excel "D" sheet.
 *
 * Quick mode only: one article per row fills all 15 hourly slots (mirrors the import
 * fallback). Per-slot detail stays on the single {@code /entry} form.
 */
@Controller
@RequestMapping("/entry/grid")
public class EntryGridController {

    @Autowired
    private IProductionService productionService;

    @Autowired
    private IEfficiencyCalculatorService efficiencyCalculator;

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private LineAssignmentService lineAssignmentService;

    @GetMapping({"", "/"})
    public String showGrid(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Principal principal,
            Model model) {
        if (date == null) date = LocalDate.now();
        // Prefill: the day's existing rows (with EFF already computed) become editable,
        // narrowed to the caller's assigned section/line scope (mirrors split-entry).
        LineAssignmentService.LineScope scope = lineAssignmentService.scopeFor(principal.getName());
        List<DailyProductionDto> rows = productionService.getDashboardData(date);
        if (scope.isRestricted()) {
            rows = rows.stream().filter(r -> scope.allows(r.getSection(), r.getLine())).toList();
        }
        model.addAttribute("rows", rows);
        model.addAttribute("date", date);
        model.addAttribute("sectionOptions", sectionOptions());
        model.addAttribute("lines", productionRepository.findDistinctLines());
        return "entry-grid";
    }

    /**
     * Save every filled row for the chosen date in one submit. Existing rows (carrying
     * an id) update in place; new rows insert. Overwrite is implicit because the grid
     * already shows the day's existing rows — the accidental-duplicate prompt lives on
     * the single {@code /entry} form. Per-row scope violations are reported, not fatal.
     */
    @PostMapping("/save-batch")
    public String saveBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<String> id,
            @RequestParam(required = false) List<String> section,
            @RequestParam(required = false) List<String> line,
            @RequestParam(required = false) List<String> article,
            @RequestParam(required = false) List<String> mp,
            @RequestParam(required = false) List<String> dli,
            @RequestParam(required = false) List<String> idl,
            @RequestParam(required = false) List<String> output,
            @RequestParam(required = false) List<String> wt,
            @RequestParam(required = false) List<String> rft,
            @RequestParam(required = false) List<String> allowance,
            Principal principal,
            HttpServletRequest request,
            RedirectAttributes ra) {

        int saved = 0;
        List<String> errors = new ArrayList<>();
        int n = section == null ? 0 : section.size();
        for (int i = 0; i < n; i++) {
            String sec = at(section, i);
            String ln = at(line, i);
            // Skip fully-blank rows (a section + line are the minimum to save a row).
            if (isBlank(sec) || isBlank(ln)) continue;

            DailyProductionDto dto = new DailyProductionDto();
            Long rowId = parseL(at(id, i));
            if (rowId != null) dto.setId(rowId);
            dto.setProductionDate(date);
            dto.setSection(sec.trim());
            dto.setLine(ln.trim());
            dto.setMp(parseD(at(mp, i)));
            dto.setDli(parseD(at(dli, i)));
            dto.setIdl(parseD(at(idl, i)));
            dto.setWt(parseD(at(wt, i)));
            dto.setRft(parseD(at(rft, i)));
            dto.setOutput(parseI(at(output, i)));
            Double allow = parseD(at(allowance, i));
            dto.setAllowance(allow != null ? allow : 100.0);

            String art = at(article, i);
            dto.setArticle(art);
            // Quick mode: one article fills all 15 hourly slots.
            if (!isBlank(art)) {
                for (String slot : EntryExcelLayout.TIME_SLOTS) {
                    DailyProductionDetailDto d = new DailyProductionDetailDto();
                    d.setTimeSlot(slot);
                    d.setArticleNo(art.trim());
                    dto.getDetails().add(d);
                }
            }

            if (dto.getMp() == null || dto.getWt() == null) {
                errors.add(ln + ": thiếu MP/WT");
                continue;
            }
            try {
                productionService.saveDailyProduction(dto, principal.getName(), true);
                saved++;
            } catch (AccessDeniedException ex) {
                errors.add(ln + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                errors.add(ln + ": " + ex.getMessage());
            }
        }

        systemLogService.logAction("GRID_SAVE_ENTRY", "Date=" + date + ", saved=" + saved, request);
        ra.addFlashAttribute("gridSaved", saved);
        if (!errors.isEmpty()) ra.addFlashAttribute("gridErrors", errors);
        ra.addAttribute("date", date);
        return "redirect:/entry/grid";
    }

    /** Live per-row EFF preview for the grid (no DB write). */
    @PostMapping("/preview-eff")
    @ResponseBody
    public Map<String, Object> previewEff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String section,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) Double mp,
            @RequestParam(required = false) Double dli,
            @RequestParam(required = false) Double wt,
            @RequestParam(required = false) Integer output,
            @RequestParam(required = false) Double allowance) {

        Map<String, Object> result = new LinkedHashMap<>();
        if (mp == null || wt == null || output == null) return result;

        String sec = SectionMetrics.applyAssemblyLine(section, null);
        DailyProduction entity = DailyProduction.builder()
                .productionDate(date)
                .section(sec)
                .mp(mp)
                .dli(dli)
                .wt(wt)
                .totalOutput(output)
                .allowance(allowance != null ? allowance : 100.0)
                .build();
        if (article != null && !article.isBlank()) {
            for (String slot : EntryExcelLayout.TIME_SLOTS) {
                entity.getDetails().add(thienloc.manage.entity.DailyProductionDetail.builder()
                        .dailyProduction(entity).timeSlot(slot).articleNo(article.trim()).output(0).build());
            }
        }

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle(article);
        efficiencyCalculator.populateEfficiencyMetrics(dto, entity);

        result.put("effKpi", dto.getEffKpi());
        result.put("effSalary", dto.getEffSalary());
        result.put("target", dto.getTarget());
        result.put("actualPph", dto.getActualPph());
        result.put("stdPph", dto.getStdPph());
        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private static List<String> sectionOptions() {
        List<String> out = new ArrayList<>();
        for (SectionMetrics sm : SectionMetrics.values()) out.add(sm.getSectionName());
        return out;
    }

    private static <T> T at(List<T> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Double parseD(String s) {
        if (isBlank(s)) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseI(String s) {
        if (isBlank(s)) return null;
        try { return (int) Math.round(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { return null; }
    }

    private static Long parseL(String s) {
        if (isBlank(s)) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
