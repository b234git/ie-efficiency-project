package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import thienloc.manage.util.CanonicalColumn;
import thienloc.manage.util.ExcelCellUtil;
import thienloc.manage.util.HeaderResolver;
import thienloc.manage.util.SheetDetector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports the daily-production Excel file. Accepts both:
 *   - the project's own template (sheet "DB", project column layout), and
 *   - the factory "EFF APR V6" production workbook (sheet "D",
 *     REF 1 / REF 2 prefix columns, different positions).
 * Sheet selection and column positions are resolved at parse time via
 * {@link SheetDetector} and {@link HeaderResolver}.
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
            Sheet sheet = SheetDetector.findDailyDetailSheet(workbook);
            HeaderResolver headers = HeaderResolver.resolve(sheet);
            int dataStart = headers.getDataStartRow();

            // Iterating backwards ensures correct "Recent Entries" display order
            for (int i = sheet.getLastRowNum(); i >= dataStart; i--) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = headers.cell(row, CanonicalColumn.DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK) continue;

                LocalDate productionDate = ExcelCellUtil.parseDateCell(dateCell);
                if (productionDate == null) continue;

                String line = combineLine(
                        ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.LINE)),
                        ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.SUBLINE)));
                String section = SectionMetrics.applyAssemblyLine(resolveSection(row, headers), line);
                Double mp = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.DL));
                Double dli = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.DLI));
                Double idl = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.IDL));
                Integer output = ExcelCellUtil.getInteger(headers.cell(row, CanonicalColumn.OUTPUT));
                Double wt = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.WT));
                Double rft = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                Double allowance = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.ALLOWANCE));
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
                List<Integer> slotCols = headers.getTimeSlotColumns();
                for (int s = 0; s < slotCols.size(); s++) {
                    String article = cleanArticleNo(
                            ExcelCellUtil.getString(row.getCell(slotCols.get(s))));
                    if (article != null && !article.isEmpty()) {
                        details.add(DailyProductionDetail.builder()
                                .dailyProduction(production)
                                .timeSlot(HeaderResolver.slotLabel(s))
                                .output(0)
                                .articleNo(article)
                                .build());
                    }
                }

                // Fallback: if no time-slot articles but ARTICLE column has value,
                // fill all slots with the main article
                if (details.isEmpty()) {
                    String mainArticle = cleanArticleNo(
                            ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.ARTICLE)));
                    if (mainArticle != null && !mainArticle.isEmpty()) {
                        for (int s = 0; s < slotCols.size(); s++) {
                            details.add(DailyProductionDetail.builder()
                                    .dailyProduction(production)
                                    .timeSlot(HeaderResolver.slotLabel(s))
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
            Sheet sheet = SheetDetector.findDailyDetailSheet(workbook);
            HeaderResolver headers = HeaderResolver.resolve(sheet);
            int dataStart = headers.getDataStartRow();
            List<Integer> slotCols = headers.getTimeSlotColumns();

            for (int i = dataStart; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = headers.cell(row, CanonicalColumn.DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK) continue;

                EntryImportPreviewDto.RowPreview preview = new EntryImportPreviewDto.RowPreview();
                preview.setRowNum(i + 1);
                preview.setValid(true);

                LocalDate parsedDate = ExcelCellUtil.parseDateCell(dateCell);
                if (parsedDate == null) {
                    preview.setValid(false);
                    preview.setErrorMessage("Invalid date");
                } else {
                    preview.setProductionDate(parsedDate);
                }

                String line = combineLine(
                        ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.LINE)),
                        ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.SUBLINE)));
                preview.setSection(SectionMetrics.applyAssemblyLine(resolveSection(row, headers), line));
                preview.setLine(line);
                preview.setMp(ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.DL)));
                preview.setDli(ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.DLI)));
                preview.setIdl(ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.IDL)));
                preview.setTotalOutput(ExcelCellUtil.getInteger(headers.cell(row, CanonicalColumn.OUTPUT)));
                preview.setWt(ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.WT)));
                Double rft = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                preview.setRft(rft);
                Double allowance = ExcelCellUtil.getDouble(headers.cell(row, CanonicalColumn.ALLOWANCE));
                if (allowance != null && allowance > 0 && allowance <= 1.0) {
                    allowance = allowance * 100.0;
                }
                preview.setAllowance(allowance != null ? allowance : 100.0);

                Map<String, String> articles = new LinkedHashMap<>();
                for (int s = 0; s < slotCols.size(); s++) {
                    String article = cleanArticleNo(
                            ExcelCellUtil.getString(row.getCell(slotCols.get(s))));
                    if (article != null && !article.isEmpty()) {
                        articles.put(HeaderResolver.slotLabel(s), article);
                    }
                }

                String mainArticle = cleanArticleNo(
                        ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.ARTICLE)));
                if (articles.isEmpty() && mainArticle != null && !mainArticle.isEmpty()) {
                    for (int s = 0; s < slotCols.size(); s++) {
                        articles.put(HeaderResolver.slotLabel(s), mainArticle);
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

    /**
     * Combine row-level Section and Subsection columns into the canonical
     * section name. The factory xlsx omits Subsection (the 1ST/2ND distinction
     * comes from the article's "-2" suffix instead), so an empty Subsection
     * leaves the short-form section to {@link SectionMetrics#normalize} for
     * alias resolution.
     */
    private static String resolveSection(Row row, HeaderResolver headers) {
        String section = ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.SECTION));
        String subsection = ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.SUBSECTION));
        if (section != null && subsection != null && !subsection.isBlank()) {
            section = section + " " + subsection.trim().toUpperCase();
        } else if (section != null && "SF".equalsIgnoreCase(section.trim())) {
            // Factory layout: when a STOCKFIT row's line code is "UV" (e.g. line "UV",
            // sub-line blank), promote section to "STOCKFIT UV" so downstream lookups
            // hit the right MasterDb columns. Subsection isn't used in this layout.
            String line = ExcelCellUtil.getString(headers.cell(row, CanonicalColumn.LINE));
            if (line != null && "UV".equalsIgnoreCase(line.trim())) {
                section = "STOCKFIT UV";
            }
        }
        return SectionMetrics.normalize(section);
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
