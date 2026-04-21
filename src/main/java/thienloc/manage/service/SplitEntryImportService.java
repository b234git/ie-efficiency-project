package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.dto.SplitEntryImportPreviewDto;
import thienloc.manage.dto.SplitEntryImportPreviewDto.RowPreview;
import thienloc.manage.util.ExcelCellUtil;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Service
public class SplitEntryImportService {

    @Autowired
    private ISplitEntryService splitEntryService;

    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    private static final List<String> VALID_SECTIONS = Arrays.asList(
            "SEW", "BUFFING", "BUFFING 1ST", "BUFFING 2ND",
            "STOCKFIT", "STOCKFIT UV", "STOCKFIT 1ST", "STOCKFIT 2ND",
            "ASSEMBLY", "ASSEMBLY BIG", "ASSEMBLY SMALL");

    // ─── Parse Output Excel ─────────────────────────────────────────────

    public SplitEntryImportPreviewDto parseOutputFile(MultipartFile file) throws IOException {
        SplitEntryImportPreviewDto preview = new SplitEntryImportPreviewDto();
        preview.setFilename(file.getOriginalFilename());
        preview.setImportType("OUTPUT");

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            int valid = 0, errors = 0;

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                RowPreview rp = new RowPreview();
                rp.setRowNum(i + 1);
                List<String> missing = new ArrayList<>();

                try {
                    // Date (col 0)
                    LocalDate date = parseDateCell(row.getCell(0));
                    if (date == null) missing.add("Date");
                    rp.setProductionDate(date);

                    // Section (col 1) + Subsection (col 2, optional)
                    String section = getCellString(row.getCell(1));
                    String subsection = getCellString(row.getCell(2));
                    if (section == null || section.isBlank()) {
                        missing.add("Section");
                    } else {
                        section = section.trim().toUpperCase();
                        if (subsection != null && !subsection.isBlank()) {
                            section = section + " " + subsection.trim().toUpperCase();
                        }
                        section = SectionMetrics.normalize(section);
                        if (!VALID_SECTIONS.contains(section)) {
                            missing.add("Section invalid: " + section);
                        }
                    }
                    rp.setSection(section);

                    // Line (col 3)
                    String line = getCellString(row.getCell(3));
                    if (line == null || line.isBlank()) missing.add("Line");
                    rp.setLine(line != null ? line.trim() : null);

                    // WT (col 4)
                    Double wt = getCellDouble(row.getCell(4));
                    if (wt == null || wt <= 0) missing.add("WT");
                    rp.setWt(wt);

                    // Total Output (col 5)
                    Integer output = getCellInteger(row.getCell(5));
                    if (output == null || output < 0) missing.add("Total Output");
                    rp.setTotalOutput(output);

                    // RFT (col 6) - optional
                    Double rft = getCellDouble(row.getCell(6));
                    if (rft != null && rft <= 1.0 && rft > 0) rft = rft * 100;
                    rp.setRft(rft);

                    if (!missing.isEmpty()) {
                        rp.setValid(false);
                        rp.setErrorMessage("Missing: " + String.join(", ", missing));
                        errors++;
                    } else {
                        rp.setValid(true);
                        valid++;
                    }
                } catch (Exception e) {
                    rp.setValid(false);
                    rp.setErrorMessage("Parse error: " + e.getMessage());
                    errors++;
                }

                preview.getRows().add(rp);
            }

            preview.setTotalRows(preview.getRows().size());
            preview.setValidRows(valid);
            preview.setErrorRows(errors);
        }

        return preview;
    }

    // ─── Parse Articles Excel ───────────────────────────────────────────

    public SplitEntryImportPreviewDto parseArticlesFile(MultipartFile file) throws IOException {
        SplitEntryImportPreviewDto preview = new SplitEntryImportPreviewDto();
        preview.setFilename(file.getOriginalFilename());
        preview.setImportType("ARTICLES");

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            int valid = 0, errors = 0;

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                RowPreview rp = new RowPreview();
                rp.setRowNum(i + 1);
                List<String> missing = new ArrayList<>();

                try {
                    // Date (col 0)
                    LocalDate date = parseDateCell(row.getCell(0));
                    if (date == null) missing.add("Date");
                    rp.setProductionDate(date);

                    // Section (col 1) + Subsection (col 2, optional)
                    String section = getCellString(row.getCell(1));
                    String subsection = getCellString(row.getCell(2));
                    if (section == null || section.isBlank()) {
                        missing.add("Section");
                    } else {
                        section = section.trim().toUpperCase();
                        if (subsection != null && !subsection.isBlank()) {
                            section = section + " " + subsection.trim().toUpperCase();
                        }
                        section = SectionMetrics.normalize(section);
                        if (!VALID_SECTIONS.contains(section)) {
                            missing.add("Section invalid: " + section);
                        }
                    }
                    rp.setSection(section);

                    // Line (col 3)
                    String line = getCellString(row.getCell(3));
                    if (line == null || line.isBlank()) missing.add("Line");
                    rp.setLine(line != null ? line.trim() : null);

                    // Allowance (col 4) - optional, defaults to 100
                    Double allowance = getCellDouble(row.getCell(4));
                    if (allowance == null) {
                        allowance = 100.0;
                    } else if (allowance <= 1.0 && allowance > 0) {
                        allowance = allowance * 100;
                    }
                    rp.setAllowance(allowance);

                    // Time slot articles (col 5 through 19)
                    Map<String, String> articles = new LinkedHashMap<>();
                    int articleCount = 0;
                    for (int j = 0; j < TIME_SLOTS.size(); j++) {
                        String articleNo = cleanArticleNo(getCellString(row.getCell(5 + j)));
                        if (articleNo != null && !articleNo.isBlank()) {
                            articles.put(TIME_SLOTS.get(j), articleNo);
                            articleCount++;
                        }
                    }
                    rp.setArticles(articles);
                    rp.setArticleCount(articleCount);

                    if (!missing.isEmpty()) {
                        rp.setValid(false);
                        rp.setErrorMessage("Missing: " + String.join(", ", missing));
                        errors++;
                    } else if (articleCount == 0) {
                        rp.setValid(false);
                        rp.setErrorMessage("No articles");
                        errors++;
                    } else {
                        rp.setValid(true);
                        valid++;
                    }
                } catch (Exception e) {
                    rp.setValid(false);
                    rp.setErrorMessage("Parse error: " + e.getMessage());
                    errors++;
                }

                preview.getRows().add(rp);
            }

            preview.setTotalRows(preview.getRows().size());
            preview.setValidRows(valid);
            preview.setErrorRows(errors);
        }

        return preview;
    }

    // ─── Commit Imports ─────────────────────────────────────────────────

    public int commitOutputImport(SplitEntryImportPreviewDto preview, String username) {
        int count = 0;
        for (RowPreview row : preview.getRows()) {
            if (!row.isValid()) continue;

            SplitEntryDto dto = new SplitEntryDto();
            dto.setProductionDate(row.getProductionDate());
            dto.setSection(row.getSection());
            dto.setLine(row.getLine());
            dto.setWt(row.getWt());
            dto.setTotalOutput(row.getTotalOutput());
            dto.setRft(row.getRft());

            splitEntryService.saveOutput(dto, username);
            count++;
        }
        return count;
    }

    public int commitArticlesImport(SplitEntryImportPreviewDto preview, String username) {
        int count = 0;
        for (RowPreview row : preview.getRows()) {
            if (!row.isValid()) continue;

            SplitEntryDto dto = new SplitEntryDto();
            dto.setProductionDate(row.getProductionDate());
            dto.setSection(row.getSection());
            dto.setLine(row.getLine());
            dto.setAllowance(row.getAllowance());

            List<DailyProductionDetailDto> details = new ArrayList<>();
            if (row.getArticles() != null) {
                for (String slot : TIME_SLOTS) {
                    String articleNo = cleanArticleNo(row.getArticles().get(slot));
                    if (articleNo != null && !articleNo.isBlank()) {
                        details.add(DailyProductionDetailDto.builder()
                                .timeSlot(slot)
                                .articleNo(articleNo)
                                .build());
                    }
                }
            }
            dto.setDetails(details);

            splitEntryService.saveArticles(dto, username);
            count++;
        }
        return count;
    }

    // ─── Cell helpers ───────────────────────────────────────────────────

    private LocalDate parseDateCell(Cell cell) {
        return ExcelCellUtil.parseDateCell(cell);
    }

    private String cleanArticleNo(String articleNo) {
        if (articleNo == null) return null;
        String cleaned = articleNo.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String getCellString(Cell cell) {
        return ExcelCellUtil.getString(cell);
    }

    private Double getCellDouble(Cell cell) {
        return ExcelCellUtil.getDouble(cell);
    }

    private Integer getCellInteger(Cell cell) {
        return ExcelCellUtil.getInteger(cell);
    }
}
