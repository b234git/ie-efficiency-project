package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.service.MasterDbService;
import thienloc.manage.service.SystemLogService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Controller
@RequestMapping("/masterdb")
public class MasterDbController {

    @Autowired
    private MasterDbService masterDbService;

    @Autowired
    private SystemLogService systemLogService;

    // ─── List (Read) ───────────────────────────────────────────────────────────

    @GetMapping({ "", "/" })
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            Model model) {

        Page<MasterDb> resultPage = masterDbService.search(keyword, page);
        model.addAttribute("records", resultPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("emptyRecord", new MasterDb()); // for Add form
        return "masterdb";
    }

    // ─── Save (Create / Update) ────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(@ModelAttribute MasterDb entity,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isNew = (entity.getId() == null);
            masterDbService.save(entity);
            String action = isNew ? "ADD_MASTERDB" : "EDIT_MASTERDB";
            systemLogService.logAction(action, "Ref: " + entity.getRef() + ", Article: " + entity.getArticleNo());
            redirectAttributes.addFlashAttribute("success",
                    isNew ? "Thêm mới thành công!" : "Cập nhật thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return buildRedirect(keyword, page);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/delete")
    public String delete(@RequestParam Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes) {
        try {
            masterDbService.deleteById(id);
            systemLogService.logAction("DELETE_MASTERDB", "Deleted MasterDb ID: " + id);
            redirectAttributes.addFlashAttribute("success", "Xóa thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa: " + e.getMessage());
        }
        return buildRedirect(keyword, page);
    }

    // ─── Import Excel ──────────────────────────────────────────────────────────

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn file Excel.");
            return "redirect:/masterdb/";
        }
        try {
            int count = masterDbService.importFromExcel(file);
            systemLogService.logAction("IMPORT_MASTERDB", "Imported/Updated " + count + " records from Excel.");
            redirectAttributes.addFlashAttribute("success",
                    "Import thành công! Đã xử lý " + count + " bản ghi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi import: " + e.getMessage());
        }
        return "redirect:/masterdb/";
    }

    // ─── Download Template ─────────────────────────────────────────────────────

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("DB");

            // ── Cell styles ────────────────────────────────────────────────────────
            // Style: Row 1 section header (yellow bg, bold, center, border)
            org.apache.poi.xssf.usermodel.XSSFCellStyle styleSection = wb.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont fontBold = wb.createFont();
            fontBold.setBold(true);
            fontBold.setFontHeightInPoints((short) 10);
            styleSection.setFont(fontBold);
            styleSection.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[] { (byte) 255, (byte) 230, (byte) 100 }, null));
            styleSection.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleSection.setAlignment(HorizontalAlignment.CENTER);
            styleSection.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(styleSection);

            // Style: Row 1 identity cols (light blue, bold, center)
            org.apache.poi.xssf.usermodel.XSSFCellStyle styleId = wb.createCellStyle();
            styleId.setFont(fontBold);
            styleId.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[] { (byte) 189, (byte) 215, (byte) 238 }, null));
            styleId.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleId.setAlignment(HorizontalAlignment.CENTER);
            styleId.setVerticalAlignment(VerticalAlignment.CENTER);
            styleId.setWrapText(true);
            setBorder(styleId);

            // Style: Row 2 sub-header (light grey, bold)
            org.apache.poi.xssf.usermodel.XSSFCellStyle styleSub = wb.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont fontSubBold = wb.createFont();
            fontSubBold.setBold(true);
            fontSubBold.setFontHeightInPoints((short) 9);
            styleSub.setFont(fontSubBold);
            styleSub.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[] { (byte) 217, (byte) 217, (byte) 217 }, null));
            styleSub.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleSub.setAlignment(HorizontalAlignment.CENTER);
            setBorder(styleSub);

            // Style: Sample data row (white, normal)
            org.apache.poi.xssf.usermodel.XSSFCellStyle styleSample = wb.createCellStyle();
            styleSample.setAlignment(HorizontalAlignment.LEFT);
            setBorder(styleSample);

            // ── Row 0: Section headers ─────────────────────────────────────────────
            Row header1 = sheet.createRow(0);
            header1.setHeightInPoints(20);

            // 5 identity cols (A–E), merged vertically with row 1
            String[] idCols = { "REF", "Article No.", "Pattern No.", "Shoe Name", "OS Code" };
            for (int i = 0; i < idCols.length; i++) {
                Cell c = header1.createCell(i);
                c.setCellValue(idCols[i]);
                c.setCellStyle(styleId);
            }

            // 8 sections × 4 cols
            String[] sections = { "SEW", "BUFFING 1ST", "BUFFING 2ND", "STOCKFIT UV",
                    "STOCKFIT 1ST", "STOCKFIT 2ND", "ASSEMBLY BIG", "ASSEMBLY SMALL" };
            int startCol = 5; // F
            for (String sec : sections) {
                Cell c = header1.createCell(startCol);
                c.setCellValue(sec);
                c.setCellStyle(styleSection);
                // Fill merged area cells with same style (POI requires all cells in merge range
                // to be styled)
                for (int k = startCol + 1; k < startCol + 4; k++) {
                    Cell blank = header1.createCell(k);
                    blank.setCellStyle(styleSection);
                }
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, startCol, startCol + 3));
                startCol += 4;
            }

            // Merge identity cols vertically (rows 0–1)
            for (int i = 0; i < 5; i++) {
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 1, i, i));
            }

            // ── Row 1: Sub-headers (CT / MP / QUOTA / PPH) ───────────────────────
            Row header2 = sheet.createRow(1);
            header2.setHeightInPoints(16);

            // Identity cols: style them (merged, already have value from row0)
            for (int i = 0; i < 5; i++) {
                Cell c = header2.createCell(i);
                c.setCellStyle(styleId);
            }

            // Sub-headers for each section
            String[] subHeaders = { "CT", "MP", "QUOTA", "PPH" };
            for (int col = 5; col < 5 + (8 * 4); col++) {
                Cell c = header2.createCell(col);
                c.setCellValue(subHeaders[(col - 5) % 4]);
                c.setCellStyle(styleSub);
            }

            // ── Row 2: Sample data ─────────────────────────────────────────────────
            Row sample = sheet.createRow(2);
            String[] sampleVals = { "Y01748P7402H0038", "Y01748P7402", "DS-1628", "S-CLEVER LOW", "DSO-241" };
            for (int i = 0; i < sampleVals.length; i++) {
                Cell c = sample.createCell(i);
                c.setCellValue(sampleVals[i]);
                c.setCellStyle(styleSample);
            }
            // SEW sample: CT=1681, MP=30, QUOTA=600, PPH=2
            setNumCell(sample, 5, 1681.0, styleSample);
            setNumCell(sample, 6, 30.0, styleSample);
            setNumCell(sample, 7, 600.0, styleSample);
            setNumCell(sample, 8, 2.0, styleSample);
            // BUFFING 1ST sample: CT=71.5, MP=4, QUOTA=2000, PPH=50
            setNumCell(sample, 9, 71.5, styleSample);
            setNumCell(sample, 10, 4.0, styleSample);
            setNumCell(sample, 11, 2000.0, styleSample);
            setNumCell(sample, 12, 50.0, styleSample);

            // ── Column widths ──────────────────────────────────────────────────────
            for (int i = 0; i < 5; i++)
                sheet.setColumnWidth(i, 5000); // identity cols
            for (int i = 5; i < 37; i++)
                sheet.setColumnWidth(i, 3200); // data cols

            // ── Freeze top 2 rows ──────────────────────────────────────────────────
            sheet.createFreezePane(0, 2);

            // ── Write to bytes ─────────────────────────────────────────────────────
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);

            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.add("Content-Disposition", "attachment; filename=MasterDb_Template.xlsx");

            return ResponseEntity.ok()
                    .headers(respHeaders)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(new ByteArrayInputStream(out.toByteArray())));
        }
    }

    /** Set all 4 borders on a cell style */
    private void setBorder(org.apache.poi.ss.usermodel.CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    /** Create a numeric cell with a given style */
    private void setNumCell(Row row, int col, double val, org.apache.poi.ss.usermodel.CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private String buildRedirect(String keyword, int page) {
        StringBuilder sb = new StringBuilder("redirect:/masterdb/?page=").append(page);
        if (keyword != null && !keyword.trim().isEmpty()) {
            sb.append("&keyword=").append(keyword);
        }
        return sb.toString();
    }
}
