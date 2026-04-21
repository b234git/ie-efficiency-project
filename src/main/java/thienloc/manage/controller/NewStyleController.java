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
import thienloc.manage.entity.NewStyleEntry;
import thienloc.manage.service.NewStyleService;
import thienloc.manage.service.SystemLogService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/new-style")
public class NewStyleController {

    @Autowired
    private NewStyleService service;

    @Autowired
    private SystemLogService systemLogService;

    @GetMapping({"", "/"})
    public String index(@RequestParam(required = false) String month, Model model) {
        String selectedMonth = (month != null && !month.isBlank()) ? month
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        model.addAttribute("entries", service.getByMonth(selectedMonth));
        model.addAttribute("selectedMonth", selectedMonth);
        return "new-style";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute NewStyleEntry entry,
                       @RequestParam(required = false) String redirectMonth,
                       RedirectAttributes ra) {
        try {
            boolean isNew = (entry.getId() == null);
            service.save(entry);
            systemLogService.logAction(
                    isNew ? "ADD_NEW_STYLE" : "EDIT_NEW_STYLE",
                    entry.getDataMonth() + " | " + entry.getSection() + " | " + entry.getLine() + " | " + entry.getStyle());
            ra.addFlashAttribute("success",
                    isNew ? "New Style added successfully!" : "New Style updated successfully!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        String month = (redirectMonth != null && !redirectMonth.isBlank()) ? redirectMonth
                : (entry.getDataMonth() != null ? entry.getDataMonth() : "");
        return "redirect:/new-style/?month=" + month;
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String month,
                              RedirectAttributes ra) {
        try {
            String importMonth = (month != null && !month.isBlank()) ? month
                    : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            int count = service.importFromExcel(file, importMonth);
            systemLogService.logAction("IMPORT_NEW_STYLE", importMonth + " - " + count + " rows");
            ra.addFlashAttribute("success", "Excel imported successfully! " + count + " rows added.");
            return "redirect:/new-style/?month=" + importMonth;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Excel import error: " + e.getMessage());
            return "redirect:/new-style/";
        }
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("NewStyle");

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = {"DataMonth", "Section", "Line", "Style", "Quantity"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Object[][] samples = {
                {currentMonth, "SEW",          "1A", "NV-SEW-A",   2},
                {currentMonth, "SEW",          "1A", "NV-SEW-B",   3},
                {currentMonth, "ASSEMBLY BIG", "8",  "NV-ASSY-C",  1},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue((String) samples[r][0]);
                row.createCell(1).setCellValue((String) samples[r][1]);
                row.createCell(2).setCellValue((String) samples[r][2]);
                row.createCell(3).setCellValue((String) samples[r][3]);
                row.createCell(4).setCellValue((Integer) samples[r][4]);
            }

            wb.write(out);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=NewStyle_Template.xlsx");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(in));
        }
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id,
                         @RequestParam(required = false) String month,
                         RedirectAttributes ra) {
        try {
            service.findById(id).ifPresent(e -> {
                service.deleteById(id);
                systemLogService.logAction("DELETE_NEW_STYLE",
                        e.getSection() + " | " + e.getLine() + " | " + e.getStyle());
            });
            ra.addFlashAttribute("success", "New Style deleted!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Delete error: " + e.getMessage());
        }
        String redirectMonth = (month != null && !month.isBlank()) ? month : "";
        return "redirect:/new-style/?month=" + redirectMonth;
    }
}
