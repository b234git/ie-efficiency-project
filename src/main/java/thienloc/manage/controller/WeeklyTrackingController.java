package thienloc.manage.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.ReprocessRecord;
import thienloc.manage.entity.SixSRecord;
import thienloc.manage.service.SystemLogService;
import thienloc.manage.service.WeeklyTrackingService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/weekly-tracking")
public class WeeklyTrackingController {

    @Autowired
    private WeeklyTrackingService service;

    @Autowired
    private SystemLogService systemLogService;

    // ── GET: render page ──────────────────────────────────────────────────────

    private static final String ALL_MONTHS = "ALL";

    @GetMapping({"", "/"})
    public String index(@RequestParam(required = false) String month,
                        @RequestParam(required = false) String activeTab,
                        Model model) {

        List<String> allMonths = service.getAllDistinctMonths();

        // Default to most recent month if none selected
        if (month == null || month.isBlank()) {
            month = allMonths.isEmpty() ? null : allMonths.get(0);
        }

        List<SixSRecord> sixsList;
        List<ReprocessRecord> reproList;
        if (ALL_MONTHS.equals(month)) {
            sixsList  = service.getAllSixS();
            reproList = service.getAllReprocess();
        } else {
            sixsList  = month != null ? service.getSixSByMonth(month)      : List.of();
            reproList = month != null ? service.getReprocessByMonth(month) : List.of();
        }

        model.addAttribute("allMonths", allMonths);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("sixsList", sixsList);
        model.addAttribute("reproList", reproList);

        if (activeTab == null) activeTab = "sixs";
        model.addAttribute("activeTab", activeTab);

        return "weekly-tracking";
    }

    // ── 6S: Save ──────────────────────────────────────────────────────────────

    @PostMapping("/sixs/save")
    public String saveSixS(@ModelAttribute SixSRecord record,
                           @RequestParam(required = false) String selectedMonth,
                           RedirectAttributes ra) {
        try {
            boolean isNew = (record.getId() == null);
            service.saveSixS(record);
            systemLogService.logAction(
                    isNew ? "ADD_SIXS_RECORD" : "EDIT_SIXS_RECORD",
                    record.getDataMonth() + " | " + record.getSection() + " " + record.getLine());
            ra.addFlashAttribute("success", isNew ? "6S record added successfully!" : "6S record updated successfully!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "sixs");
        ra.addAttribute("month", record.getDataMonth());
        return "redirect:/weekly-tracking/";
    }

    // ── 6S: Delete ────────────────────────────────────────────────────────────

    @PostMapping("/sixs/delete")
    public String deleteSixS(@RequestParam Long id,
                             @RequestParam(required = false) String selectedMonth,
                             RedirectAttributes ra) {
        try {
            service.findSixSById(id).ifPresent(r -> {
                service.deleteSixS(id);
                systemLogService.logAction("DELETE_SIXS_RECORD",
                        r.getDataMonth() + " | " + r.getSection() + " " + r.getLine());
            });
            ra.addFlashAttribute("success", "6S record deleted!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Delete error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "sixs");
        if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
        return "redirect:/weekly-tracking/";
    }

    // ── 6S: Mass Delete ───────────────────────────────────────────────────────

    @PostMapping("/sixs/delete-mass")
    public String deleteSixSMass(@RequestParam(name = "ids", required = false) List<Long> ids,
                                  @RequestParam(required = false) String selectedMonth,
                                  RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "No records selected.");
        } else {
            service.deleteSixSByIds(ids);
            systemLogService.logAction("DELETE_MASS_SIXS", ids.size() + " 6S records deleted");
            ra.addFlashAttribute("success", "Deleted " + ids.size() + " 6S record(s).");
        }
        ra.addFlashAttribute("activeTab", "sixs");
        if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
        return "redirect:/weekly-tracking/";
    }

    // ── Reprocess: Save ───────────────────────────────────────────────────────

    @PostMapping("/reprocess/save")
    public String saveReprocess(@ModelAttribute ReprocessRecord record,
                                @RequestParam(required = false) String selectedMonth,
                                RedirectAttributes ra) {
        try {
            boolean isNew = (record.getId() == null);
            service.saveReprocess(record);
            systemLogService.logAction(
                    isNew ? "ADD_REPROCESS_RECORD" : "EDIT_REPROCESS_RECORD",
                    record.getDataMonth() + " | " + record.getSection() + " " + record.getLine());
            ra.addFlashAttribute("success", isNew ? "Reprocess record added successfully!" : "Reprocess record updated successfully!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "reprocess");
        ra.addAttribute("month", record.getDataMonth());
        return "redirect:/weekly-tracking/";
    }

    // ── Reprocess: Delete ─────────────────────────────────────────────────────

    @PostMapping("/reprocess/delete")
    public String deleteReprocess(@RequestParam Long id,
                                  @RequestParam(required = false) String selectedMonth,
                                  RedirectAttributes ra) {
        try {
            service.findReprocessById(id).ifPresent(r -> {
                service.deleteReprocess(id);
                systemLogService.logAction("DELETE_REPROCESS_RECORD",
                        r.getDataMonth() + " | " + r.getSection() + " " + r.getLine());
            });
            ra.addFlashAttribute("success", "Reprocess record deleted!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Delete error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "reprocess");
        if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
        return "redirect:/weekly-tracking/";
    }

    // ── Reprocess: Mass Delete ────────────────────────────────────────────────

    @PostMapping("/reprocess/delete-mass")
    public String deleteReprocessMass(@RequestParam(name = "ids", required = false) List<Long> ids,
                                       @RequestParam(required = false) String selectedMonth,
                                       RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "No records selected.");
        } else {
            service.deleteReprocessByIds(ids);
            systemLogService.logAction("DELETE_MASS_REPROCESS", ids.size() + " reprocess records deleted");
            ra.addFlashAttribute("success", "Deleted " + ids.size() + " Reprocess record(s).");
        }
        ra.addFlashAttribute("activeTab", "reprocess");
        if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
        return "redirect:/weekly-tracking/";
    }

    // ── Import Templates ──────────────────────────────────────────────────────

    @GetMapping("/import/template/sixs")
    public ResponseEntity<InputStreamResource> downloadSixsTemplate() throws IOException {
        String[] headers = {"Section", "Line", "Week 1", "Week 2", "Week 3", "Week 4", "Week 5"};
        ByteArrayInputStream stream = buildTemplate("6S", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=6S_Score_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/import/template/reprocess")
    public ResponseEntity<InputStreamResource> downloadReprocessTemplate() throws IOException {
        String[] headers = {"Section", "Line", "Week 1", "Week 2", "Week 3", "Week 4", "Week 5", "Output"};
        ByteArrayInputStream stream = buildTemplate("Reprocess", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Reprocess_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    private ByteArrayInputStream buildTemplate(String sheetName, String[] headers) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 14 * 256);
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // ── Import: 6S ────────────────────────────────────────────────────────────

    @PostMapping("/sixs/import")
    public String importSixS(@RequestParam("file") MultipartFile file,
                             @RequestParam("importMonth") String importMonth,
                             @RequestParam(required = false) String selectedMonth,
                             RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            ra.addFlashAttribute("activeTab", "sixs");
            if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
            return "redirect:/weekly-tracking/";
        }
        try {
            WeeklyImportResultDto result = service.importSixSFromExcel(file, importMonth);
            systemLogService.logAction("IMPORT_SIXS", importMonth + " | " + result.toFlashMessage());
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "sixs");
        ra.addAttribute("month", importMonth);
        return "redirect:/weekly-tracking/";
    }

    // ── Import: Reprocess ─────────────────────────────────────────────────────

    @PostMapping("/reprocess/import")
    public String importReprocess(@RequestParam("file") MultipartFile file,
                                  @RequestParam("importMonth") String importMonth,
                                  @RequestParam(required = false) String selectedMonth,
                                  RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            ra.addFlashAttribute("activeTab", "reprocess");
            if (selectedMonth != null) ra.addAttribute("month", selectedMonth);
            return "redirect:/weekly-tracking/";
        }
        try {
            WeeklyImportResultDto result = service.importReprocessFromExcel(file, importMonth);
            systemLogService.logAction("IMPORT_REPROCESS", importMonth + " | " + result.toFlashMessage());
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "reprocess");
        ra.addAttribute("month", importMonth);
        return "redirect:/weekly-tracking/";
    }
}
