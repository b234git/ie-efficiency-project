package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.util.ExcelCellUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static thienloc.manage.util.EntryExcelLayout.COL_ALLOWANCE;
import static thienloc.manage.util.EntryExcelLayout.COL_ARTICLE;
import static thienloc.manage.util.EntryExcelLayout.COL_DATE;
import static thienloc.manage.util.EntryExcelLayout.COL_DL;
import static thienloc.manage.util.EntryExcelLayout.COL_DLI;
import static thienloc.manage.util.EntryExcelLayout.COL_IDL;
import static thienloc.manage.util.EntryExcelLayout.COL_LINE;
import static thienloc.manage.util.EntryExcelLayout.COL_OUTPUT;
import static thienloc.manage.util.EntryExcelLayout.COL_RFT;
import static thienloc.manage.util.EntryExcelLayout.COL_SECTION;
import static thienloc.manage.util.EntryExcelLayout.COL_SLOTS_START;
import static thienloc.manage.util.EntryExcelLayout.COL_SUBLINE;
import static thienloc.manage.util.EntryExcelLayout.COL_SUBSECTION;
import static thienloc.manage.util.EntryExcelLayout.COL_WT;
import static thienloc.manage.util.EntryExcelLayout.DATA_START_ROW;
import static thienloc.manage.util.EntryExcelLayout.TIME_SLOTS;

/**
 * Imports the daily-production Excel template (DB sheet).
 * Provides both full import (persists records) and preview parsing (no persistence).
 */
@Service
public class EntryExcelImportService {

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private UserService userService;

    @Transactional
    public void importExcel(MultipartFile file, String username) throws IOException {
        importExcel(file.getInputStream(), username);
    }

    @Transactional
    public void importExcel(byte[] fileBytes, String username) throws IOException {
        importExcel(new ByteArrayInputStream(fileBytes), username);
    }

    @Transactional
    public void importExcel(InputStream inputStream, String username) throws IOException {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        List<DailyProduction> productions = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            // Iterating backwards ensures correct "Recent Entries" display order
            for (int i = sheet.getLastRowNum(); i >= DATA_START_ROW; i--) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = row.getCell(COL_DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK) continue;

                LocalDate productionDate;
                if (dateCell.getCellType() == CellType.NUMERIC) {
                    Date javaDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
                    productionDate = javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } else {
                    productionDate = LocalDate.parse(dateCell.getStringCellValue().trim());
                }

                String section = ExcelCellUtil.getString(row.getCell(COL_SECTION));
                String subsection = ExcelCellUtil.getString(row.getCell(COL_SUBSECTION));
                if (section != null && subsection != null && !subsection.isBlank()) {
                    section = section + " " + subsection.trim().toUpperCase();
                }
                section = SectionMetrics.normalize(section);
                String line = combineLine(
                        ExcelCellUtil.getString(row.getCell(COL_LINE)),
                        ExcelCellUtil.getString(row.getCell(COL_SUBLINE)));
                Double mp = ExcelCellUtil.getDouble(row.getCell(COL_DL));
                Double dli = ExcelCellUtil.getDouble(row.getCell(COL_DLI));
                Double idl = ExcelCellUtil.getDouble(row.getCell(COL_IDL));
                Integer output = ExcelCellUtil.getInteger(row.getCell(COL_OUTPUT));
                Double wt = ExcelCellUtil.getDouble(row.getCell(COL_WT));
                Double rft = ExcelCellUtil.getDouble(row.getCell(COL_RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                Double allowance = ExcelCellUtil.getDouble(row.getCell(COL_ALLOWANCE));
                if (allowance != null && allowance > 0 && allowance <= 1.0) {
                    allowance = allowance * 100.0;
                }

                if (output == null) output = 0;
                if (allowance == null) allowance = 100.0;

                if (section == null || section.isBlank() || line == null || line.isBlank()
                        || mp == null || wt == null) {
                    continue;
                }

                DailyProduction production = DailyProduction.builder()
                        .productionDate(productionDate)
                        .section(section)
                        .line(line)
                        .mp(mp)
                        .dli(dli)
                        .idl(idl)
                        .wt(wt)
                        .totalOutput(output)
                        .rft(rft)
                        .allowance(allowance)
                        .createdBy(user)
                        .build();

                List<DailyProductionDetail> details = new ArrayList<>();
                int colIdx = COL_SLOTS_START;
                for (String slot : TIME_SLOTS) {
                    String article = cleanArticleNo(ExcelCellUtil.getString(row.getCell(colIdx++)));
                    if (article != null && !article.isEmpty()) {
                        details.add(DailyProductionDetail.builder()
                                .dailyProduction(production)
                                .timeSlot(slot)
                                .output(0)
                                .articleNo(article)
                                .build());
                    }
                }

                // Fallback: if no time-slot articles but ARTICLE column has value,
                // fill all slots with the main article
                if (details.isEmpty()) {
                    String mainArticle = cleanArticleNo(ExcelCellUtil.getString(row.getCell(COL_ARTICLE)));
                    if (mainArticle != null && !mainArticle.isEmpty()) {
                        for (String slot : TIME_SLOTS) {
                            details.add(DailyProductionDetail.builder()
                                    .dailyProduction(production)
                                    .timeSlot(slot)
                                    .output(0)
                                    .articleNo(mainArticle)
                                    .build());
                        }
                    }
                }

                production.getDetails().addAll(details);
                productions.add(production);
            }
        }

        productionRepository.saveAll(productions);
    }

    public EntryImportPreviewDto parseForPreview(MultipartFile file) throws IOException {
        long t0 = System.nanoTime();
        List<EntryImportPreviewDto.RowPreview> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = row.getCell(COL_DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK) continue;

                EntryImportPreviewDto.RowPreview preview = new EntryImportPreviewDto.RowPreview();
                preview.setRowNum(i + 1);
                preview.setValid(true);

                try {
                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        Date javaDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
                        preview.setProductionDate(javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                    } else {
                        preview.setProductionDate(LocalDate.parse(dateCell.getStringCellValue().trim()));
                    }
                } catch (Exception e) {
                    preview.setValid(false);
                    preview.setErrorMessage("Invalid date: " + e.getMessage());
                }

                String previewSection = ExcelCellUtil.getString(row.getCell(COL_SECTION));
                String previewSubsection = ExcelCellUtil.getString(row.getCell(COL_SUBSECTION));
                if (previewSubsection != null && !previewSubsection.isBlank()) {
                    previewSection = previewSection + " " + previewSubsection.trim().toUpperCase();
                }
                preview.setSection(previewSection);
                preview.setLine(combineLine(
                        ExcelCellUtil.getString(row.getCell(COL_LINE)),
                        ExcelCellUtil.getString(row.getCell(COL_SUBLINE))));
                preview.setMp(ExcelCellUtil.getDouble(row.getCell(COL_DL)));
                preview.setDli(ExcelCellUtil.getDouble(row.getCell(COL_DLI)));
                preview.setIdl(ExcelCellUtil.getDouble(row.getCell(COL_IDL)));
                preview.setTotalOutput(ExcelCellUtil.getInteger(row.getCell(COL_OUTPUT)));
                preview.setWt(ExcelCellUtil.getDouble(row.getCell(COL_WT)));
                Double rft = ExcelCellUtil.getDouble(row.getCell(COL_RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                preview.setRft(rft);
                Double allowance = ExcelCellUtil.getDouble(row.getCell(COL_ALLOWANCE));
                if (allowance != null && allowance > 0 && allowance <= 1.0) {
                    allowance = allowance * 100.0;
                }
                preview.setAllowance(allowance != null ? allowance : 100.0);

                Map<String, String> articles = new LinkedHashMap<>();
                int colIdx = COL_SLOTS_START;
                for (String slot : TIME_SLOTS) {
                    String article = cleanArticleNo(ExcelCellUtil.getString(row.getCell(colIdx++)));
                    if (article != null && !article.isEmpty()) {
                        articles.put(slot, article);
                    }
                }

                String mainArticle = cleanArticleNo(ExcelCellUtil.getString(row.getCell(COL_ARTICLE)));
                if (articles.isEmpty() && mainArticle != null && !mainArticle.isEmpty()) {
                    for (String slot : TIME_SLOTS) {
                        articles.put(slot, mainArticle);
                    }
                }
                preview.setMainArticle(mainArticle);
                preview.setArticles(articles);
                preview.setArticleCount(articles.size());

                if (preview.isValid()) {
                    List<String> missing = new ArrayList<>();
                    if (preview.getSection() == null || preview.getSection().isBlank()) missing.add("Section");
                    if (preview.getLine() == null || preview.getLine().isBlank()) missing.add("Line");
                    if (preview.getMp() == null) missing.add("DL");
                    if (!missing.isEmpty()) {
                        preview.setValid(false);
                        preview.setErrorMessage("Missing: " + String.join(", ", missing));
                    }
                }

                rows.add(preview);
            }
        }

        int validCount = (int) rows.stream().filter(EntryImportPreviewDto.RowPreview::isValid).count();

        EntryImportPreviewDto result = EntryImportPreviewDto.builder()
                .filename(file.getOriginalFilename())
                .totalRows(rows.size())
                .validRows(validCount)
                .errorRows(rows.size() - validCount)
                .rows(rows)
                .build();
        meterRegistry.timer("excel.entry.parse").record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
        return result;
    }

    private static String combineLine(String lineNumber, String subLine) {
        if (lineNumber == null || lineNumber.isBlank()) return null;
        String result = lineNumber.trim();
        if (subLine != null && !subLine.isBlank()) {
            result += subLine.trim().toUpperCase();
        }
        return result;
    }

    /**
     * Normalize article number for storage: trim only. The "-2" suffix is preserved
     * so downstream calculation can resolve BUFF/SF 1ST vs 2ND per slot.
     */
    private static String cleanArticleNo(String articleNo) {
        if (articleNo == null) return null;
        String cleaned = articleNo.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
