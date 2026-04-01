package thienloc.manage.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;

@Service
public class ExcelService {

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private UserService userService;

    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    // Column layout (must be kept in sync between template and import):
    // 0=Date 1=Section 2=Line 3=sub-line 4=sub-section 5=DL 6=DLI 7=IDL
    // 8=Output 9=WT 10=RFT 11..25=Article time slots 26=ARTICLE 27=Allowance
    private static final int COL_DATE = 0;
    private static final int COL_SECTION = 1;
    private static final int COL_LINE = 2;
    private static final int COL_SUBLINE = 3;
    private static final int COL_SUBSECTION = 4;
    private static final int COL_DL = 5;
    private static final int COL_DLI = 6;
    private static final int COL_IDL = 7;
    private static final int COL_OUTPUT = 8;
    private static final int COL_WT = 9;
    private static final int COL_RFT = 10;
    private static final int COL_SLOTS_START = 11;
    private static final int COL_ARTICLE = 26;
    private static final int COL_ALLOWANCE = 27;
    private static final int DATA_START_ROW = 2;

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DB");

            // ── Styles ───────────────────────────────────────────────────────
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            // Blue header style (for main columns)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            // Yellow style (for merged "Article" header)
            CellStyle articleHeaderStyle = workbook.createCellStyle();
            articleHeaderStyle.setFont(boldFont);
            articleHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            articleHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            articleHeaderStyle.setBorderBottom(BorderStyle.THIN);
            articleHeaderStyle.setBorderTop(BorderStyle.THIN);
            articleHeaderStyle.setBorderLeft(BorderStyle.THIN);
            articleHeaderStyle.setBorderRight(BorderStyle.THIN);
            articleHeaderStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            articleHeaderStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);

            // Yellow style for time slot sub-headers (smaller font)
            Font slotFont = workbook.createFont();
            slotFont.setBold(true);
            slotFont.setFontHeightInPoints((short) 9);
            CellStyle slotStyle = workbook.createCellStyle();
            slotStyle.setFont(slotFont);
            slotStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            slotStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            slotStyle.setBorderBottom(BorderStyle.THIN);
            slotStyle.setBorderTop(BorderStyle.THIN);
            slotStyle.setBorderLeft(BorderStyle.THIN);
            slotStyle.setBorderRight(BorderStyle.THIN);
            slotStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            slotStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            slotStyle.setWrapText(true);

            // ── Row 0: main headers ──────────────────────────────────────────
            Row row0 = sheet.createRow(0);
            createStyledCell(row0, COL_DATE, "Date", headerStyle);
            createStyledCell(row0, COL_SECTION, "Section", headerStyle);
            createStyledCell(row0, COL_LINE, "Line", headerStyle);
            createStyledCell(row0, COL_SUBLINE, "sub-line", headerStyle);
            createStyledCell(row0, COL_SUBSECTION, "sub-section", headerStyle);
            createStyledCell(row0, COL_DL, "DL", headerStyle);
            createStyledCell(row0, COL_DLI, "DLI", headerStyle);
            createStyledCell(row0, COL_IDL, "IDL", headerStyle);
            createStyledCell(row0, COL_OUTPUT, "Output", headerStyle);
            createStyledCell(row0, COL_WT, "WT", headerStyle);
            createStyledCell(row0, COL_RFT, "RFT", headerStyle);
            // Merged "Article" header spanning time slot columns
            createStyledCell(row0, COL_SLOTS_START, "Article", articleHeaderStyle);
            for (int c = COL_SLOTS_START + 1; c <= COL_SLOTS_START + 14; c++) {
                createStyledCell(row0, c, "", articleHeaderStyle);
            }
            createStyledCell(row0, COL_ARTICLE, "ARTICLE", headerStyle);
            createStyledCell(row0, COL_ALLOWANCE, "Allowance", headerStyle);

            // Merge "Article" across cols 11-25 in row 0
            sheet.addMergedRegion(new CellRangeAddress(0, 0, COL_SLOTS_START, COL_SLOTS_START + 14));
            // Vertically merge main headers across rows 0-1
            for (int c = 0; c <= COL_RFT; c++) {
                sheet.addMergedRegion(new CellRangeAddress(0, 1, c, c));
            }
            sheet.addMergedRegion(new CellRangeAddress(0, 1, COL_ARTICLE, COL_ARTICLE));
            sheet.addMergedRegion(new CellRangeAddress(0, 1, COL_ALLOWANCE, COL_ALLOWANCE));

            // ── Row 1: time slot sub-headers ─────────────────────────────────
            Row row1 = sheet.createRow(1);
            int colIdx = COL_SLOTS_START;
            for (String slot : TIME_SLOTS) {
                String[] parts = slot.split("-");
                createStyledCell(row1, colIdx++, parts[0] + "\n" + parts[1], slotStyle);
            }

            // ── Row 2: sample data ───────────────────────────────────────────
            Row dataRow = sheet.createRow(DATA_START_ROW);
            dataRow.createCell(COL_DATE).setCellValue(LocalDate.now().toString());
            dataRow.createCell(COL_SECTION).setCellValue("SEW");
            dataRow.createCell(COL_LINE).setCellValue("1");
            dataRow.createCell(COL_SUBLINE).setCellValue("A");
            dataRow.createCell(COL_SUBSECTION).setCellValue("");
            dataRow.createCell(COL_DL).setCellValue(30);
            dataRow.createCell(COL_DLI).setCellValue(0);
            dataRow.createCell(COL_IDL).setCellValue(0);
            dataRow.createCell(COL_OUTPUT).setCellValue(1200);
            dataRow.createCell(COL_WT).setCellValue(10);
            dataRow.createCell(COL_RFT).setCellValue(95);
            dataRow.createCell(COL_SLOTS_START).setCellValue("311872");
            dataRow.createCell(COL_SLOTS_START + 1).setCellValue("311872");
            dataRow.createCell(COL_SLOTS_START + 2).setCellValue("311872");
            dataRow.createCell(COL_SLOTS_START + 3).setCellValue("311872");
            // 11:00 & 12:00 blank (lunch break)
            dataRow.createCell(COL_SLOTS_START + 6).setCellValue("311872");
            dataRow.createCell(COL_ARTICLE).setCellValue("311872");
            dataRow.createCell(COL_ALLOWANCE).setCellValue(100);

            // Freeze top 2 header rows
            sheet.createFreezePane(0, DATA_START_ROW);

            // Auto-size columns
            for (int i = 0; i <= COL_ALLOWANCE; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Transactional
    public void importExcel(MultipartFile file, String username) throws IOException {
        importExcel(file.getInputStream(), username);
    }

    @Transactional
    public void importExcel(byte[] fileBytes, String username) throws IOException {
        importExcel(new java.io.ByteArrayInputStream(fileBytes), username);
    }

    @Transactional
    public void importExcel(java.io.InputStream inputStream, String username) throws IOException {
        User user = userService.findByUsername(username);
        if (user == null)
            throw new thienloc.manage.exception.ResourceNotFoundException("User not found");

        List<DailyProduction> productions = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // Try to find the "DB" sheet; fall back to first sheet
            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null)
                sheet = workbook.getSheetAt(0);

            // Rows 0-1 are headers — start from row 2 (iterating backwards ensures
            // correct "Recent Entries" display order)
            for (int i = sheet.getLastRowNum(); i >= DATA_START_ROW; i--) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                Cell dateCell = row.getCell(COL_DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK)
                    continue;

                // Parse date
                LocalDate productionDate;
                if (dateCell.getCellType() == CellType.NUMERIC) {
                    Date javaDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
                    productionDate = javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } else {
                    productionDate = LocalDate.parse(dateCell.getStringCellValue().trim());
                }

                String section = getCellValueAsString(row.getCell(COL_SECTION));
                String subsection = getCellValueAsString(row.getCell(COL_SUBSECTION));
                if (section != null && subsection != null && !subsection.isBlank()) {
                    section = section + " " + subsection.trim().toUpperCase();
                }
                section = SectionMetrics.normalize(section);
                String line = combineLine(
                        getCellValueAsString(row.getCell(COL_LINE)),
                        getCellValueAsString(row.getCell(COL_SUBLINE)));
                Double mp = getCellValueAsDouble(row.getCell(COL_DL));
                Double dli = getCellValueAsDouble(row.getCell(COL_DLI));
                Double idl = getCellValueAsDouble(row.getCell(COL_IDL));
                Integer output = getCellValueAsInteger(row.getCell(COL_OUTPUT));
                Double wt = getCellValueAsDouble(row.getCell(COL_WT));
                Double rft = getCellValueAsDouble(row.getCell(COL_RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                Double allowance = getCellValueAsDouble(row.getCell(COL_ALLOWANCE));
                if (allowance != null && allowance > 0 && allowance <= 1.0) {
                    allowance = allowance * 100.0;
                }

                if (output == null)
                    output = 0;
                if (allowance == null)
                    allowance = 100.0;

                // Skip rows missing required fields
                if (section == null || section.isBlank() || line == null || line.isBlank()
                        || mp == null || wt == null)
                    continue;

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

                // Time slot details
                List<DailyProductionDetail> details = new ArrayList<>();
                int colIdx = COL_SLOTS_START;
                for (String slot : TIME_SLOTS) {
                    String article = getCellValueAsString(row.getCell(colIdx++));
                    if (article != null && !article.trim().isEmpty()) {
                        details.add(DailyProductionDetail.builder()
                                .dailyProduction(production)
                                .timeSlot(slot)
                                .output(0)
                                .articleNo(article.trim())
                                .build());
                    }
                }

                // Fallback: if no time-slot articles but ARTICLE column has value,
                // fill all slots with the main article
                if (details.isEmpty()) {
                    String mainArticle = getCellValueAsString(row.getCell(COL_ARTICLE));
                    if (mainArticle != null && !mainArticle.trim().isEmpty()) {
                        for (String slot : TIME_SLOTS) {
                            details.add(DailyProductionDetail.builder()
                                    .dailyProduction(production)
                                    .timeSlot(slot)
                                    .output(0)
                                    .articleNo(mainArticle.trim())
                                    .build());
                        }
                    }
                }

                production.getDetails().addAll(details);
                productions.add(production);
            }
        }

        // Batch save — 1 transaction thay vì N transaction riêng lẻ
        productionRepository.saveAll(productions);
    }

    /**
     * Parse Excel file and return preview data (without saving).
     */
    public EntryImportPreviewDto parseForPreview(MultipartFile file) throws IOException {
        List<EntryImportPreviewDto.RowPreview> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null)
                sheet = workbook.getSheetAt(0);

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell dateCell = row.getCell(COL_DATE);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK)
                    continue;

                EntryImportPreviewDto.RowPreview preview = new EntryImportPreviewDto.RowPreview();
                preview.setRowNum(i + 1); // 1-based Excel row number
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

                String previewSection = getCellValueAsString(row.getCell(COL_SECTION));
                String previewSubsection = getCellValueAsString(row.getCell(COL_SUBSECTION));
                if (previewSubsection != null && !previewSubsection.isBlank()) {
                    previewSection = previewSection + " " + previewSubsection.trim().toUpperCase();
                }
                preview.setSection(previewSection);
                preview.setLine(combineLine(
                        getCellValueAsString(row.getCell(COL_LINE)),
                        getCellValueAsString(row.getCell(COL_SUBLINE))));
                preview.setMp(getCellValueAsDouble(row.getCell(COL_DL)));
                preview.setDli(getCellValueAsDouble(row.getCell(COL_DLI)));
                preview.setIdl(getCellValueAsDouble(row.getCell(COL_IDL)));
                preview.setTotalOutput(getCellValueAsInteger(row.getCell(COL_OUTPUT)));
                preview.setWt(getCellValueAsDouble(row.getCell(COL_WT)));
                Double rft = getCellValueAsDouble(row.getCell(COL_RFT));
                if (rft != null && rft > 0 && rft <= 1.0) {
                    rft = rft * 100.0;
                }
                preview.setRft(rft);
                Double allowance = getCellValueAsDouble(row.getCell(COL_ALLOWANCE));
                if (allowance != null && allowance > 0 && allowance <= 1.0) {
                    allowance = allowance * 100.0;
                }
                preview.setAllowance(allowance != null ? allowance : 100.0);

                // Count time-slot articles
                Map<String, String> articles = new LinkedHashMap<>();
                int colIdx = COL_SLOTS_START;
                for (String slot : TIME_SLOTS) {
                    String article = getCellValueAsString(row.getCell(colIdx++));
                    if (article != null && !article.trim().isEmpty()) {
                        articles.put(slot, article.trim());
                    }
                }

                // Fallback: ARTICLE column fills all slots if individual slots empty
                String mainArticle = getCellValueAsString(row.getCell(COL_ARTICLE));
                if (articles.isEmpty() && mainArticle != null && !mainArticle.trim().isEmpty()) {
                    for (String slot : TIME_SLOTS) {
                        articles.put(slot, mainArticle.trim());
                    }
                }
                preview.setMainArticle(mainArticle);
                preview.setArticles(articles);
                preview.setArticleCount(articles.size());

                // Validate required fields
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

        return EntryImportPreviewDto.builder()
                .filename(file.getOriginalFilename())
                .totalRows(rows.size())
                .validRows(validCount)
                .errorRows(rows.size() - validCount)
                .rows(rows)
                .build();
    }

    public ByteArrayInputStream exportWeeklyReport(List<WeeklyReportDto> blocks, LocalDate weekStart) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Shared styles (created once per workbook) ─────────────────────
            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Pink title style for section headers ("ASSY — Line 1", etc.)
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Bold style for total rows
            CellStyle totalStyle = workbook.createCellStyle();
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Group blocks by section category (preserve insertion order) ───
            // Order: ASSY, SEW, BUFFING, SF  (matches HTML page order)
            Map<String, List<WeeklyReportDto>> grouped = new LinkedHashMap<>();
            for (WeeklyReportDto block : blocks) {
                String cat = getSectionCategory(block.getSection());
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(block);
            }

            String[] headers = {"DATE", "PATTERN#", "STYLE", "ARTICLE",
                                 "ACTUAL OUTPUT", "TARGET OUTPUT", "MP (DL)", "ACTUAL MP", "WT (h)", "EFF%",
                                 "ACTUAL PPH", "STD PPH", "COMPARE"};

            String[] categoryOrder = {"ASSY", "SEW", "BUFFING", "SF"};
            for (String cat : categoryOrder) {
                List<WeeklyReportDto> catBlocks = grouped.get(cat);
                if (catBlocks == null || catBlocks.isEmpty()) continue;

                Sheet sheet = workbook.createSheet(cat);
                int rowIdx = 0;
                int filterHeaderRow = -1;

                for (WeeklyReportDto block : catBlocks) {
                    // ── Section title row ("ASSY — Line 1") ───────────────────
                    String lineLabel = cat + " \u2014 Line " + (block.getLine() != null ? block.getLine() : "");
                    Row titleRow = sheet.createRow(rowIdx);
                    Cell titleCell = titleRow.createCell(0);
                    titleCell.setCellValue(lineLabel);
                    titleCell.setCellStyle(titleStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, headers.length - 1));
                    rowIdx++;

                    // ── Section header row ─────────────────────────────────────
                    if (filterHeaderRow == -1) filterHeaderRow = rowIdx;
                    Row headerRow = sheet.createRow(rowIdx++);
                    for (int c = 0; c < headers.length; c++) {
                        createStyledCell(headerRow, c, headers[c], headerStyle);
                    }

                    // ── Data rows ──────────────────────────────────────────────
                    for (WeeklyReportDto.DailyRow row : block.getDailyRows()) {
                        Row dataRow = sheet.createRow(rowIdx++);
                        dataRow.createCell(0).setCellValue(row.getDate() != null ? row.getDate().toString() : "");
                        dataRow.createCell(1).setCellValue(row.getPatternNo() != null ? row.getPatternNo() : "-");
                        dataRow.createCell(2).setCellValue(row.getShoeName() != null ? row.getShoeName() : "-");
                        dataRow.createCell(3).setCellValue(row.getArticleNo() != null ? row.getArticleNo() : "-");
                        dataRow.createCell(4).setCellValue(row.getOutput() != null ? row.getOutput() : 0);
                        dataRow.createCell(5).setCellValue(row.getTargetOutput() != null ? row.getTargetOutput() : 0);
                        dataRow.createCell(6).setCellValue(row.getMp() != null ? round1(row.getMp()) : 0);
                        dataRow.createCell(7).setCellValue(row.getDli() != null ? round1(row.getDli()) : 0);
                        dataRow.createCell(8).setCellValue(row.getWt() != null ? round1(row.getWt()) : 0);
                        dataRow.createCell(9).setCellValue(row.getEff() != null ? round1(row.getEff() * 100) + "%" : "-");
                        dataRow.createCell(10).setCellValue(row.getActualPph() != null ? round1(row.getActualPph().doubleValue()) : 0);
                        dataRow.createCell(11).setCellValue(row.getStdPph() != null ? round1(row.getStdPph().doubleValue()) : 0);
                        if (row.getActualPph() != null && row.getStdPph() != null && row.getStdPph().doubleValue() > 0) {
                            double pct = (row.getActualPph().doubleValue() - row.getStdPph().doubleValue()) / row.getStdPph().doubleValue() * 100;
                            dataRow.createCell(12).setCellValue((pct >= 0 ? "▲ +" : "▼ ") + round1(pct) + "%");
                        } else {
                            dataRow.createCell(12).setCellValue("—");
                        }
                    }

                    // ── Total row ──────────────────────────────────────────────
                    WeeklyReportDto.SummaryRow total = block.getTotal();
                    Row totalRow = sheet.createRow(rowIdx++);
                    createStyledCell(totalRow, 0, "Total", totalStyle);
                    for (int c = 1; c < headers.length; c++) totalRow.createCell(c).setCellStyle(totalStyle);
                    if (total != null) {
                        totalRow.getCell(4).setCellValue(total.getTotalOutput() != null ? total.getTotalOutput() : 0);
                        totalRow.getCell(5).setCellValue(total.getTotalTargetOutput() != null ? total.getTotalTargetOutput() : 0);
                        totalRow.getCell(6).setCellValue(total.getAvgMp() != null ? round1(total.getAvgMp()) : 0);
                        totalRow.getCell(7).setCellValue(total.getAvgDli() != null ? round1(total.getAvgDli()) : 0);
                        totalRow.getCell(8).setCellValue(total.getAvgWt() != null ? round1(total.getAvgWt()) : 0);
                        totalRow.getCell(9).setCellValue(total.getAvgEff() != null ? round1(total.getAvgEff() * 100) + "%" : "-");
                        totalRow.getCell(10).setCellValue(total.getAvgActualPph() != null ? round1(total.getAvgActualPph()) : 0);
                        totalRow.getCell(11).setCellValue(total.getAvgStdPph() != null ? round1(total.getAvgStdPph()) : 0);
                        if (total.getAvgActualPph() != null && total.getAvgStdPph() != null && total.getAvgStdPph() > 0) {
                            double pct = (total.getAvgActualPph() - total.getAvgStdPph()) / total.getAvgStdPph() * 100;
                            totalRow.getCell(12).setCellValue((pct >= 0 ? "▲ +" : "▼ ") + round1(pct) + "%");
                        }
                    }

                    // ── Blank separator row ────────────────────────────────────
                    sheet.createRow(rowIdx++);
                }

                // AutoFilter on first header row, covering all rows to the bottom
                if (filterHeaderRow >= 0) {
                    sheet.setAutoFilter(new CellRangeAddress(filterHeaderRow, rowIdx - 1, 0, headers.length - 1));
                    sheet.createFreezePane(0, filterHeaderRow + 1);
                }

                // Auto-size all columns
                for (int c = 0; c < headers.length; c++) sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public ByteArrayInputStream exportDailyReport(List<DailyProductionDto> records,
                                                   LocalDate from, LocalDate to) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN); headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN); headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle effHiStyle = workbook.createCellStyle();
            Font hiFont = workbook.createFont();
            hiFont.setColor(IndexedColors.GREEN.getIndex()); hiFont.setBold(true);
            effHiStyle.setFont(hiFont);

            CellStyle effMidStyle = workbook.createCellStyle();
            Font midFont = workbook.createFont();
            midFont.setColor(IndexedColors.ORANGE.getIndex()); midFont.setBold(true);
            effMidStyle.setFont(midFont);

            CellStyle effLowStyle = workbook.createCellStyle();
            Font lowFont = workbook.createFont();
            lowFont.setColor(IndexedColors.RED.getIndex()); lowFont.setBold(true);
            effLowStyle.setFont(lowFont);

            Sheet sheet = workbook.createSheet("Daily Report");
            String[] headers = {"Date", "Section", "Line", "Pattern#", "Style", "Article#",
                                 "Output", "Target", "WT(h)", "DL(MP)", "Actual MP",
                                 "Actual PPH", "Std PPH", "EFF KPI", "RFT%"};
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                createStyledCell(headerRow, c, headers[c], headerStyle);
            }

            int rowIdx = 1;
            for (DailyProductionDto rec : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rec.getProductionDate() != null ? rec.getProductionDate().toString() : "");
                row.createCell(1).setCellValue(rec.getSection() != null ? rec.getSection() : "-");
                row.createCell(2).setCellValue(rec.getLine() != null ? rec.getLine() : "-");
                row.createCell(3).setCellValue(rec.getPatternNo() != null ? rec.getPatternNo() : "-");
                row.createCell(4).setCellValue(rec.getShoeName() != null ? rec.getShoeName() : "-");
                row.createCell(5).setCellValue(rec.getArticle() != null ? rec.getArticle() : "-");
                row.createCell(6).setCellValue(rec.getOutput() != null ? rec.getOutput() : 0);
                row.createCell(7).setCellValue(rec.getTarget() != null ? round1(rec.getTarget()) : 0);
                row.createCell(8).setCellValue(rec.getWt() != null ? round1(rec.getWt()) : 0);
                row.createCell(9).setCellValue(rec.getMp() != null ? round1(rec.getMp()) : 0);
                row.createCell(10).setCellValue(rec.getDli() != null ? round1(rec.getDli()) : 0);
                row.createCell(11).setCellValue(rec.getActualPph() != null ? round1(rec.getActualPph()) : 0);
                row.createCell(12).setCellValue(rec.getStdPph() != null ? round1(rec.getStdPph()) : 0);

                Cell kpiCell = row.createCell(13);
                if (rec.getEffKpi() != null) {
                    kpiCell.setCellValue(round1(rec.getEffKpi() * 100) + "%");
                    kpiCell.setCellStyle(rec.getEffKpi() >= 1.0 ? effHiStyle
                            : rec.getEffKpi() >= 0.8 ? effMidStyle : effLowStyle);
                } else {
                    kpiCell.setCellValue("-");
                }

                row.createCell(14).setCellValue(rec.getRft() != null ? round1(rec.getRft()) + "%" : "-");
            }

            sheet.setAutoFilter(new CellRangeAddress(0, records.size(), 0, 14));
            sheet.createFreezePane(0, 1);
            for (int c = 0; c < headers.length; c++) sheet.autoSizeColumn(c);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private String getSectionCategory(String section) {
        if (section == null) return "ASSY";
        String s = section.toUpperCase();
        if (s.contains("BUFF")) return "BUFFING";
        if (s.contains("SEW"))  return "SEW";
        if (s.contains("STOCKFIT") || s.equals("SF") || s.contains(" SF")) return "SF";
        return "ASSY";
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private Cell createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }

    private String combineLine(String lineNumber, String subLine) {
        if (lineNumber == null || lineNumber.isBlank()) return null;
        String result = lineNumber.trim();
        if (subLine != null && !subLine.isBlank()) {
            result += subLine.trim().toUpperCase();
        }
        return result;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Integer.parseInt(cell.getStringCellValue().trim());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
