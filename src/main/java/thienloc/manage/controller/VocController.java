package thienloc.manage.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.service.VocService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/voc")
public class VocController {

    @Autowired
    private VocService vocService;

    // ════════════════════════════════════════════════════════════════════════
    // Report
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String report(@RequestParam(required = false) String month, Model model) {
        VocReportDto report = vocService.getMonthlyReport(month);
        model.addAttribute("report", report);
        return "voc";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Chemical master
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/chemicals")
    public String chemicals(Model model) {
        model.addAttribute("chemicals", vocService.getAllChemicals());
        return "voc-chemicals";
    }

    @PostMapping("/chemicals/save")
    public String saveChemical(@ModelAttribute VocChemical chemical, RedirectAttributes ra) {
        try {
            vocService.saveChemical(chemical);
            ra.addFlashAttribute("success", "Saved chemical " + chemical.getCode());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        return "redirect:/voc/chemicals";
    }

    @PostMapping("/chemicals/delete/{id}")
    public String deleteChemical(@PathVariable Long id, RedirectAttributes ra) {
        vocService.deleteChemical(id);
        ra.addFlashAttribute("success", "Chemical deleted");
        return "redirect:/voc/chemicals";
    }

    @PostMapping("/chemicals/import")
    public String importChemicals(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            return "redirect:/voc/chemicals";
        }
        try {
            WeeklyImportResultDto result = vocService.importChemicalsFromExcel(file);
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/chemicals";
    }

    @GetMapping("/chemicals/template")
    public ResponseEntity<InputStreamResource> chemicalTemplate() throws IOException {
        String[] headers = {"Code", "Material Type", "Classification", "Manufacturer", "VOC Factor", "Price per KG"};
        ByteArrayInputStream stream = buildTemplate("Chemicals", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VOC_Chemicals_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Daily consumption — manual entry
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/entry")
    public String entry(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @RequestParam(required = false, defaultValue = VocService.DEFAULT_SECTION) String section,
                        @RequestParam(required = false) String line,
                        Model model) {
        if (date == null) date = LocalDate.now();

        // Section dropdown: distinct production sections, ensuring the current pick is present
        java.util.List<String> sections = vocService.getSections();
        if (section != null && !section.isBlank() && !sections.contains(section)) {
            sections = new java.util.ArrayList<>(sections);
            sections.add(0, section);
        }

        model.addAttribute("date", date);
        model.addAttribute("section", section);
        model.addAttribute("line", line);
        model.addAttribute("sections", sections);
        model.addAttribute("lines", vocService.getLines());
        model.addAttribute("chemicals", vocService.getActiveChemicals());
        if (line != null && !line.isBlank()) {
            model.addAttribute("rows", vocService.getConsumption(date, section, line));
        }
        return "voc-entry";
    }

    @PostMapping("/entry/save")
    public String saveConsumption(@ModelAttribute VocConsumption consumption, RedirectAttributes ra) {
        try {
            vocService.saveConsumption(consumption);
            ra.addFlashAttribute("success", "Saved " + consumption.getChemicalCode());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        ra.addAttribute("date", consumption.getProductionDate());
        ra.addAttribute("section", consumption.getSection());
        ra.addAttribute("line", consumption.getLine());
        return "redirect:/voc/entry";
    }

    @PostMapping("/entry/delete/{id}")
    public String deleteConsumption(@PathVariable Long id,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam String section,
                                    @RequestParam String line,
                                    RedirectAttributes ra) {
        vocService.deleteConsumption(id);
        ra.addFlashAttribute("success", "Deleted");
        ra.addAttribute("date", date);
        ra.addAttribute("section", section);
        ra.addAttribute("line", line);
        return "redirect:/voc/entry";
    }

    @PostMapping("/consumption/import")
    public String importConsumption(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            return "redirect:/voc/entry";
        }
        try {
            WeeklyImportResultDto result = vocService.importConsumptionFromExcel(file);
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/entry";
    }

    @GetMapping("/consumption/template")
    public ResponseEntity<InputStreamResource> consumptionTemplate() throws IOException {
        String[] headers = {"Date", "Section", "Line", "Chemical", "Quantity Kg", "Reuse Kg"};
        ByteArrayInputStream stream = buildTemplate("Consumption", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VOC_Consumption_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    // ════════════════════════════════════════════════════════════════════════
    // Standard recipe (VOC "DB")
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/recipe")
    public String recipe(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) String q,
                         Model model) {
        Page<VocStandardRate> rates = vocService.getRatesPage(page, q);
        model.addAttribute("rates", rates);
        model.addAttribute("currentPage", rates.getNumber());
        model.addAttribute("totalPages", rates.getTotalPages());
        model.addAttribute("q", q);
        model.addAttribute("chemicals", vocService.getActiveChemicals());
        return "voc-recipe";
    }

    @PostMapping("/recipe/save")
    public String saveRate(@ModelAttribute VocStandardRate rate,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) String q,
                           RedirectAttributes ra) {
        try {
            vocService.saveRate(rate);
            ra.addFlashAttribute("success", "Saved " + rate.getArticleNo() + " / " + rate.getChemicalCode());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        ra.addAttribute("page", page);
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        return "redirect:/voc/recipe";
    }

    @PostMapping("/recipe/delete/{id}")
    public String deleteRate(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(required = false) String q,
                             RedirectAttributes ra) {
        vocService.deleteRate(id);
        ra.addFlashAttribute("success", "Deleted");
        ra.addAttribute("page", page);
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        return "redirect:/voc/recipe";
    }

    @PostMapping("/recipe/import")
    public String importRecipe(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            return "redirect:/voc/recipe";
        }
        try {
            WeeklyImportResultDto result = vocService.importRecipe(file);
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/recipe";
    }

    @GetMapping("/recipe/template")
    public ResponseEntity<InputStreamResource> recipeTemplate() throws IOException {
        String[] headers = {"Article", "Chemical", "Kg per pair"};
        ByteArrayInputStream stream = buildTemplate("Recipe", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VOC_Recipe_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    // ── Shared template builder ───────────────────────────────────────────────

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
                sheet.setColumnWidth(i, 16 * 256);
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
