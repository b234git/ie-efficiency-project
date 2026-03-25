package thienloc.manage.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.dto.SplitEntryImportPreviewDto;
import thienloc.manage.service.LineSummaryImportService;
import thienloc.manage.service.SplitEntryImportService;
import thienloc.manage.service.SplitEntryService;
import thienloc.manage.service.SplitEntryTemplateService;
import thienloc.manage.service.SystemLogService;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/split-entry")
public class SplitEntryController {

    @Autowired
    private SplitEntryService splitEntryService;

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private LineSummaryImportService lineSummaryImportService;

    @Autowired
    private SplitEntryImportService splitEntryImportService;

    @Autowired
    private SplitEntryTemplateService splitEntryTemplateService;

    private static final List<String> SECTIONS = Arrays.asList(
            "SEW",
            "BUFFING", "BUFFING 1ST", "BUFFING 2ND",
            "STOCKFIT", "STOCKFIT UV", "STOCKFIT 1ST", "STOCKFIT 2ND",
            "ASSEMBLY", "ASSEMBLY BIG", "ASSEMBLY SMALL");

    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    // ─── Landing Page ─────────────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String showLanding(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        if (date == null) date = LocalDate.now();
        List<SplitEntryDto> entries = splitEntryService.getEntriesForDate(date);

        model.addAttribute("entries", entries);
        model.addAttribute("selectedDate", date);
        return "split-entry";
    }

    // ─── Page 1: Manpower ─────────────────────────────────────────────────────────

    @GetMapping("/manpower")
    public String showManpowerForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            Model model) {

        SplitEntryDto dto = new SplitEntryDto();
        dto.setProductionDate(date != null ? date : LocalDate.now());

        // Pre-fill if existing record found
        if (date != null && section != null && line != null) {
            Optional<SplitEntryDto> existing = splitEntryService.getByDateSectionLine(date, section, line);
            if (existing.isPresent()) {
                dto = existing.get();
            } else {
                dto.setSection(section);
                dto.setLine(line);
            }
        }

        model.addAttribute("splitEntry", dto);
        model.addAttribute("sections", SECTIONS);
        return "split-entry-manpower";
    }

    @PostMapping("/manpower")
    public String saveManpower(@ModelAttribute SplitEntryDto dto,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        splitEntryService.saveManpower(dto, principal.getName());
        systemLogService.logAction("SPLIT_MANPOWER",
                "Saved manpower: Section=" + dto.getSection() + ", Line=" + dto.getLine());
        redirectAttributes.addFlashAttribute("success", true);
        return "redirect:/split-entry/manpower?date=" + dto.getProductionDate()
                + "&section=" + dto.getSection() + "&line=" + dto.getLine();
    }

    // ─── Page 2: Output ───────────────────────────────────────────────────────────

    @GetMapping("/output")
    public String showOutputForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            Model model) {

        SplitEntryDto dto = new SplitEntryDto();
        dto.setProductionDate(date != null ? date : LocalDate.now());

        if (date != null && section != null && line != null) {
            Optional<SplitEntryDto> existing = splitEntryService.getByDateSectionLine(date, section, line);
            if (existing.isPresent()) {
                dto = existing.get();
            } else {
                dto.setSection(section);
                dto.setLine(line);
            }
        }

        model.addAttribute("splitEntry", dto);
        model.addAttribute("sections", SECTIONS);
        return "split-entry-output";
    }

    @PostMapping("/output")
    public String saveOutput(@ModelAttribute SplitEntryDto dto,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        splitEntryService.saveOutput(dto, principal.getName());
        systemLogService.logAction("SPLIT_OUTPUT",
                "Saved output: Section=" + dto.getSection() + ", Line=" + dto.getLine());
        redirectAttributes.addFlashAttribute("success", true);
        return "redirect:/split-entry/output?date=" + dto.getProductionDate()
                + "&section=" + dto.getSection() + "&line=" + dto.getLine();
    }

    // ─── Page 3: Allowance & Articles ─────────────────────────────────────────────

    @GetMapping("/articles")
    public String showArticlesForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String line,
            Model model) {

        SplitEntryDto dto = new SplitEntryDto();
        dto.setProductionDate(date != null ? date : LocalDate.now());
        dto.setAllowance(100.0);

        if (date != null && section != null && line != null) {
            Optional<SplitEntryDto> existing = splitEntryService.getByDateSectionLine(date, section, line);
            if (existing.isPresent()) {
                dto = existing.get();
            } else {
                dto.setSection(section);
                dto.setLine(line);
            }
        }

        // Ensure 15 time slots exist for the form
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            for (String slot : TIME_SLOTS) {
                DailyProductionDetailDto detail = new DailyProductionDetailDto();
                detail.setTimeSlot(slot);
                dto.getDetails().add(detail);
            }
        }

        model.addAttribute("splitEntry", dto);
        model.addAttribute("sections", SECTIONS);
        return "split-entry-articles";
    }

    @PostMapping("/articles")
    public String saveArticles(@ModelAttribute SplitEntryDto dto,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        splitEntryService.saveArticles(dto, principal.getName());
        systemLogService.logAction("SPLIT_ARTICLES",
                "Saved articles: Section=" + dto.getSection() + ", Line=" + dto.getLine());
        redirectAttributes.addFlashAttribute("success", true);
        return "redirect:/split-entry/articles?date=" + dto.getProductionDate()
                + "&section=" + dto.getSection() + "&line=" + dto.getLine();
    }

    // ─── Delete (MANAGER/ADMIN only — enforced by SecurityConfig) ─────────────────

    @PostMapping("/delete/{id}")
    public String deleteEntry(@PathVariable Long id,
                              @RequestParam(required = false) LocalDate date,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        splitEntryService.deleteEntry(id);
        systemLogService.logAction("SPLIT_DELETE", "Deleted split entry id=" + id);
        redirectAttributes.addFlashAttribute("success", true);
        String redirect = "/split-entry/";
        if (date != null) redirect += "?date=" + date;
        return "redirect:" + redirect;
    }

    @PostMapping("/delete-bulk")
    public String deleteBulk(@RequestParam("ids") List<Long> ids,
                             @RequestParam(required = false) LocalDate date,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        splitEntryService.deleteMultiple(ids);
        systemLogService.logAction("SPLIT_DELETE_BULK", "Deleted " + ids.size() + " split entries");
        redirectAttributes.addFlashAttribute("success", true);
        String redirect = "/split-entry/";
        if (date != null) redirect += "?date=" + date;
        return "redirect:" + redirect;
    }

    // ─── Import LineSummaryReport (.xls) ─────────────────────────────────────────

    @PostMapping("/import/preview")
    public String importPreview(@RequestParam("file") MultipartFile file,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            LineSummaryImportService.LineSummaryPreview preview =
                    lineSummaryImportService.parseFile(file);
            session.setAttribute("lsrPreview", preview);
            model.addAttribute("preview", preview);
            return "split-entry-import-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return "redirect:/split-entry/manpower";
        }
    }

    @PostMapping("/import/commit")
    public String importCommit(HttpSession session,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        LineSummaryImportService.LineSummaryPreview preview =
                (LineSummaryImportService.LineSummaryPreview) session.getAttribute("lsrPreview");

        if (preview == null) {
            redirectAttributes.addFlashAttribute("importError", "No preview data. Please upload again.");
            return "redirect:/split-entry/manpower";
        }

        int imported = 0;
        for (LineSummaryImportService.LineRow row : preview.getRows()) {
            if (!row.isActive()) continue;

            SplitEntryDto dto = new SplitEntryDto();
            dto.setProductionDate(preview.getDate());
            dto.setSection(preview.getSection());
            dto.setLine(row.getLine());
            dto.setMp(row.getTotal());
            dto.setDli(row.getDli());
            dto.setIdl(row.getIdl());

            splitEntryService.saveManpower(dto, principal.getName());
            imported++;
        }

        session.removeAttribute("lsrPreview");
        systemLogService.logAction("SPLIT_IMPORT_MANPOWER",
                "Imported " + imported + " lines from " + preview.getFilename());
        redirectAttributes.addFlashAttribute("importSuccess", imported);
        return "redirect:/split-entry/?date=" + preview.getDate();
    }

    @PostMapping("/import/cancel")
    public String importCancel(HttpSession session) {
        session.removeAttribute("lsrPreview");
        return "redirect:/split-entry/manpower";
    }

    // ─── Import Output from Excel ───────────────────────────────────────────────

    @GetMapping("/output/template")
    public ResponseEntity<InputStreamResource> downloadOutputTemplate() throws IOException {
        byte[] bytes = splitEntryTemplateService.generateOutputTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=SplitEntry_Output_Template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(new java.io.ByteArrayInputStream(bytes)));
    }

    @PostMapping("/output/import/preview")
    public String outputImportPreview(@RequestParam("file") MultipartFile file,
                                      HttpSession session,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        try {
            SplitEntryImportPreviewDto preview = splitEntryImportService.parseOutputFile(file);
            session.setAttribute("outputImportPreview", preview);
            model.addAttribute("preview", preview);
            return "split-entry-output-import-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return "redirect:/split-entry/output";
        }
    }

    @PostMapping("/output/import/commit")
    public String outputImportCommit(HttpSession session,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        SplitEntryImportPreviewDto preview =
                (SplitEntryImportPreviewDto) session.getAttribute("outputImportPreview");
        if (preview == null) {
            redirectAttributes.addFlashAttribute("importError", "No preview data. Please upload again.");
            return "redirect:/split-entry/output";
        }
        int imported = splitEntryImportService.commitOutputImport(preview, principal.getName());
        session.removeAttribute("outputImportPreview");
        systemLogService.logAction("SPLIT_IMPORT_OUTPUT",
                "Imported " + imported + " output rows from " + preview.getFilename());
        redirectAttributes.addFlashAttribute("importSuccess", imported);
        LocalDate date = preview.getRows().stream()
                .filter(SplitEntryImportPreviewDto.RowPreview::isValid)
                .findFirst()
                .map(SplitEntryImportPreviewDto.RowPreview::getProductionDate)
                .orElse(LocalDate.now());
        return "redirect:/split-entry/?date=" + date;
    }

    @PostMapping("/output/import/cancel")
    public String outputImportCancel(HttpSession session) {
        session.removeAttribute("outputImportPreview");
        return "redirect:/split-entry/output";
    }

    // ─── Import Articles from Excel ─────────────────────────────────────────────

    @GetMapping("/articles/template")
    public ResponseEntity<InputStreamResource> downloadArticlesTemplate() throws IOException {
        byte[] bytes = splitEntryTemplateService.generateArticlesTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=SplitEntry_Articles_Template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(new java.io.ByteArrayInputStream(bytes)));
    }

    @PostMapping("/articles/import/preview")
    public String articlesImportPreview(@RequestParam("file") MultipartFile file,
                                        HttpSession session,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        try {
            SplitEntryImportPreviewDto preview = splitEntryImportService.parseArticlesFile(file);
            session.setAttribute("articlesImportPreview", preview);
            model.addAttribute("preview", preview);
            return "split-entry-articles-import-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return "redirect:/split-entry/articles";
        }
    }

    @PostMapping("/articles/import/commit")
    public String articlesImportCommit(HttpSession session,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        SplitEntryImportPreviewDto preview =
                (SplitEntryImportPreviewDto) session.getAttribute("articlesImportPreview");
        if (preview == null) {
            redirectAttributes.addFlashAttribute("importError", "No preview data. Please upload again.");
            return "redirect:/split-entry/articles";
        }
        int imported = splitEntryImportService.commitArticlesImport(preview, principal.getName());
        session.removeAttribute("articlesImportPreview");
        systemLogService.logAction("SPLIT_IMPORT_ARTICLES",
                "Imported " + imported + " articles rows from " + preview.getFilename());
        redirectAttributes.addFlashAttribute("importSuccess", imported);
        LocalDate date = preview.getRows().stream()
                .filter(SplitEntryImportPreviewDto.RowPreview::isValid)
                .findFirst()
                .map(SplitEntryImportPreviewDto.RowPreview::getProductionDate)
                .orElse(LocalDate.now());
        return "redirect:/split-entry/?date=" + date;
    }

    @PostMapping("/articles/import/cancel")
    public String articlesImportCancel(HttpSession session) {
        session.removeAttribute("articlesImportPreview");
        return "redirect:/split-entry/articles";
    }
}
