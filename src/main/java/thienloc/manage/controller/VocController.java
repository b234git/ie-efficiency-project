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
import thienloc.manage.dto.VocReportFilter;
import thienloc.manage.dto.VocSubconReportDto;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocRecipeArticle;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.service.VocService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/voc")
public class VocController {

    @Autowired
    private VocService vocService;

    // ════════════════════════════════════════════════════════════════════════
    // Report
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping({"", "/"})
    public String report(@RequestParam(required = false) String month,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) Integer week,
                         @RequestParam(required = false) String section,
                         @RequestParam(required = false) String line,
                         @RequestParam(required = false) List<String> chems,
                         Model model) {
        VocReportFilter filter = new VocReportFilter(month, from, to, week, section, line, chems);
        VocReportDto report = vocService.getMonthlyReport(filter);
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
            ra.addFlashAttribute("importResult", result);
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/chemicals";
    }

    @GetMapping("/chemicals/template")
    public ResponseEntity<InputStreamResource> chemicalTemplate() throws IOException {
        String[] headers = {"Code", "Material Type", "Classification", "Manufacturer", "VOC",
                "UNIT", "kg", "Container", "Price", "$ / KG", "Date"};
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
        // Actual log for the day — whole day across all lines when no line is picked.
        model.addAttribute("rows", vocService.getActualRows(date, section, line));
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

    /** Save every chemical entered for a (date, section, line) in one submit. */
    @PostMapping("/entry/save-batch")
    public String saveConsumptionBatch(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false, defaultValue = VocService.DEFAULT_SECTION) String section,
                                       @RequestParam String line,
                                       @RequestParam(required = false) List<String> chemicalCode,
                                       @RequestParam(required = false) List<String> quantityKg,
                                       @RequestParam(required = false) List<String> reuseKg,
                                       RedirectAttributes ra) {
        try {
            int n = vocService.saveConsumptionBatch(date, section, line, chemicalCode,
                    parseDoubles(quantityKg), parseDoubles(reuseKg));
            ra.addFlashAttribute("success", "Saved " + n + " chemical(s)");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        ra.addAttribute("date", date);
        ra.addAttribute("section", section);
        ra.addAttribute("line", line);
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
            ra.addFlashAttribute("importResult", result);
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
    // SUBCON (subcontractor) — CEMENT / ACTUAL CEMENT
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/subcon")
    public String subcon(@RequestParam(required = false) String month, Model model) {
        VocSubconReportDto report = vocService.getSubconReport(month);
        model.addAttribute("report", report);
        model.addAttribute("subcontractors", vocService.getSubcontractors());
        model.addAttribute("chemicals", vocService.getActiveChemicals());
        return "voc-subcon";
    }

    @PostMapping("/subcon/save")
    public String saveSubcon(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam String subcontractor,
                             @RequestParam String articleNo,
                             @RequestParam(required = false) Integer output,
                             @RequestParam String chemicalCode,
                             @RequestParam(required = false) Double actualKg,
                             @RequestParam(required = false) Double reuseKg,
                             @RequestParam(required = false) String month,
                             RedirectAttributes ra) {
        try {
            vocService.saveSubconDetail(date, subcontractor, articleNo, output, chemicalCode, actualKg, reuseKg);
            ra.addFlashAttribute("success", "Saved " + chemicalCode);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        if (month != null && !month.isBlank()) ra.addAttribute("month", month);
        return "redirect:/voc/subcon";
    }

    /** Save every chemical actual for a (date, subcontractor, article) in one submit. */
    @PostMapping("/subcon/save-batch")
    public String saveSubconBatch(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam String subcontractor,
                                  @RequestParam String articleNo,
                                  @RequestParam(required = false) Integer output,
                                  @RequestParam(required = false) List<String> chemicalCode,
                                  @RequestParam(required = false) List<String> actualKg,
                                  @RequestParam(required = false) List<String> reuseKg,
                                  @RequestParam(required = false) String month,
                                  RedirectAttributes ra) {
        try {
            int n = vocService.saveSubconBatch(date, subcontractor, articleNo, output, chemicalCode,
                    parseDoubles(actualKg), parseDoubles(reuseKg));
            ra.addFlashAttribute("success", "Saved " + n + " chemical(s)");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        if (month != null && !month.isBlank()) ra.addAttribute("month", month);
        return "redirect:/voc/subcon";
    }

    @PostMapping("/subcon/delete/{id}")
    public String deleteSubcon(@PathVariable Long id,
                               @RequestParam(required = false) String month,
                               RedirectAttributes ra) {
        vocService.deleteSubconEntry(id);
        ra.addFlashAttribute("success", "Deleted");
        if (month != null && !month.isBlank()) ra.addAttribute("month", month);
        return "redirect:/voc/subcon";
    }

    @PostMapping("/subcon/import")
    public String importSubcon(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file.");
            return "redirect:/voc/subcon";
        }
        try {
            WeeklyImportResultDto result = vocService.importSubconFromExcel(file);
            ra.addFlashAttribute(result.getErrors().isEmpty() ? "success" : "error", result.toFlashMessage());
            ra.addFlashAttribute("importResult", result);
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/subcon";
    }

    @GetMapping("/subcon/template")
    public ResponseEntity<InputStreamResource> subconTemplate() throws IOException {
        String[] headers = {"Date", "Subcontractor", "Article", "Output", "Chemical", "Actual Kg", "Reuse Kg"};
        ByteArrayInputStream stream = buildTemplate("Subcon", headers);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VOC_Subcon_Template.xlsx")
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
        Page<VocRecipeArticle> articles = vocService.getRecipeArticlesPage(page, q);
        model.addAttribute("grid", vocService.buildRecipeGrid(articles.getContent()));
        model.addAttribute("currentPage", articles.getNumber());
        model.addAttribute("totalPages", articles.getTotalPages());
        model.addAttribute("totalElements", articles.getTotalElements());
        model.addAttribute("q", q);
        model.addAttribute("chemicals", vocService.getActiveChemicals());
        return "voc-recipe";
    }

    @PostMapping("/recipe/article/save")
    public String saveRecipeArticle(@ModelAttribute VocRecipeArticle article,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) String q,
                                    RedirectAttributes ra) {
        try {
            vocService.saveRecipeArticle(article);
            ra.addFlashAttribute("success", "Saved article " + article.getArticleNo());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        ra.addAttribute("page", page);
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        return "redirect:/voc/recipe";
    }

    /** Save a whole DB row (identity + every chemical formula) in one submit. */
    @PostMapping("/recipe/model/save")
    public String saveRecipeModel(@RequestParam String articleNo,
                                  @RequestParam(required = false) String modelCode,
                                  @RequestParam(required = false) String modelName,
                                  @RequestParam(required = false) Double baseE,
                                  @RequestParam(required = false) Double baseF,
                                  @RequestParam(required = false) List<String> chemicalCode,
                                  @RequestParam(required = false) List<String> formula,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(required = false) String q,
                                  RedirectAttributes ra) {
        try {
            vocService.saveRecipeModel(articleNo, modelCode, modelName, baseE, baseF, chemicalCode, formula);
            ra.addFlashAttribute("success", "Saved model " + articleNo);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Save failed: " + e.getMessage());
        }
        ra.addAttribute("page", page);
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        return "redirect:/voc/recipe";
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

    /** Add a new chemical type (a new matrix column) by hand. */
    @PostMapping("/recipe/chemical/save")
    public String addRecipeChemical(@RequestParam String code,
                                    @RequestParam(required = false) String manufacturer,
                                    @RequestParam(required = false) String materialType,
                                    @RequestParam(required = false) String classification,
                                    @RequestParam(required = false) Double pricePerKg,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) String q,
                                    RedirectAttributes ra) {
        try {
            vocService.saveChemical(VocChemical.builder()
                    .code(code).manufacturer(manufacturer).materialType(materialType)
                    .classification(classification).pricePerKg(pricePerKg)
                    .vocFactor(0.0).active(true).build());
            ra.addFlashAttribute("success", "Added chemical " + code);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Add chemical failed: " + e.getMessage());
        }
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
            ra.addFlashAttribute("importResult", result);
        } catch (IOException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Import error: " + e.getMessage());
        }
        return "redirect:/voc/recipe";
    }

    @GetMapping("/recipe/template")
    public ResponseEntity<InputStreamResource> recipeTemplate() throws IOException {
        ByteArrayInputStream stream = buildRecipeWideTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=VOC_DB_Template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }

    /** Wide "DB"-shaped template that round-trips through the wide importer:
     *  row 0 = identity headers + manufacturer, rows 1–2 = base/type, row 3 = chemical
     *  codes (col G onward), row 4 = a sample row to delete. */
    private ByteArrayInputStream buildRecipeWideTemplate() throws IOException {
        List<String> chems = vocService.getRecipeChemicalOrder();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DB");
            CellStyle hdr = wb.createCellStyle();
            hdr.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            hdr.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hdr.setAlignment(HorizontalAlignment.CENTER);
            Font font = wb.createFont();
            font.setBold(true);
            hdr.setFont(font);

            Row r0 = sheet.createRow(0);   // anchor: identity headers + manufacturer
            Row r1 = sheet.createRow(1);   // base (Water/Solvent)
            Row r2 = sheet.createRow(2);   // type (Adhesive/Hot melt/Primer)
            Row r3 = sheet.createRow(3);   // chemical code
            String[] idHeaders = {"Article #", "Article #", "Pattern # (Code)", "Style (Name)", "Quota 2L", "Quota 1L"};
            for (int i = 0; i < idHeaders.length; i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(idHeaders[i]);
                c.setCellStyle(hdr);
                sheet.setColumnWidth(i, 18 * 256);
            }
            for (int j = 0; j < chems.size(); j++) {
                int col = 6 + j;
                String code = chems.get(j);
                String[] meta = vocService.getChemicalMeta(code);   // {manufacturer, base, type}
                if (meta != null) {
                    Cell mc = r0.createCell(col); mc.setCellValue(meta[0]); mc.setCellStyle(hdr);
                    r1.createCell(col).setCellValue(meta[1]);
                    r2.createCell(col).setCellValue(meta[2]);
                }
                Cell cc = r3.createCell(col);
                cc.setCellValue(code);
                cc.setCellStyle(hdr);
                sheet.setColumnWidth(col, 12 * 256);
            }

            Row ex = sheet.createRow(4);   // sample row — delete before importing real data
            ex.createCell(1).setCellValue("EXAMPLE (delete this row)");
            ex.createCell(2).setCellValue("PM-0000");
            ex.createCell(3).setCellValue("MODEL NAME");
            ex.createCell(4).setCellValue(1600);
            ex.createCell(5).setCellValue(800);
            ex.createCell(6).setCellFormula("1/1300+1/1500");   // reciprocal sum → kg/pair

            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** Parse parallel numeric form arrays leniently: blank/invalid → null (so the
     *  service treats the row as empty), keeping index alignment with chemicalCode. */
    private static List<Double> parseDoubles(List<String> in) {
        if (in == null) return null;
        List<Double> out = new ArrayList<>(in.size());
        for (String s : in) {
            Double d = null;
            if (s != null && !s.isBlank()) {
                try { d = Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { }
            }
            out.add(d);
        }
        return out;
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
