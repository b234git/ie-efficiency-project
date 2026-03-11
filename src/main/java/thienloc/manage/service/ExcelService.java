package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
    // 0=Date 1=Section 2=Line 3=MP(DL) 4=DLI 5=IDL 6=WT 7=Output 8=RFT(%)
    // 9=Allowance(%)
    // 10..24 = 15 time slots (Article No.)
    private static final int COL_DATE = 0;
    private static final int COL_SECTION = 1;
    private static final int COL_LINE = 2;
    private static final int COL_MP = 3;
    private static final int COL_DLI = 4;
    private static final int COL_IDL = 5;
    private static final int COL_WT = 6;
    private static final int COL_OUTPUT = 7;
    private static final int COL_RFT = 8;
    private static final int COL_ALLOWANCE = 9;
    private static final int COL_SLOTS_START = 10;

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DB");

            // ── Style: bold header ────────────────────────────────────────────
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // ── Row 0: group labels ───────────────────────────────────────────
            Row labelRow = sheet.createRow(0);
            createStyledCell(labelRow, COL_DATE, "Date", headerStyle);
            createStyledCell(labelRow, COL_SECTION, "Section", headerStyle);
            createStyledCell(labelRow, COL_LINE, "Line", headerStyle);
            createStyledCell(labelRow, COL_MP, "MP (DL)", headerStyle);
            createStyledCell(labelRow, COL_DLI, "DLI", headerStyle);
            createStyledCell(labelRow, COL_IDL, "IDL", headerStyle);
            createStyledCell(labelRow, COL_WT, "WT (h)", headerStyle);
            createStyledCell(labelRow, COL_OUTPUT, "Total Output", headerStyle);
            createStyledCell(labelRow, COL_RFT, "RFT (%)", headerStyle);
            createStyledCell(labelRow, COL_ALLOWANCE, "Allowance (%)", headerStyle);

            // Time slot columns
            int colIdx = COL_SLOTS_START;
            for (String slot : TIME_SLOTS) {
                createStyledCell(labelRow, colIdx++, slot + " (Article)", headerStyle);
            }

            // ── Row 1: sample data ────────────────────────────────────────────
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(COL_DATE).setCellValue(LocalDate.now().toString());
            dataRow.createCell(COL_SECTION).setCellValue("SEW");
            dataRow.createCell(COL_LINE).setCellValue("1A");
            dataRow.createCell(COL_MP).setCellValue(30);
            dataRow.createCell(COL_DLI).setCellValue(0);
            dataRow.createCell(COL_IDL).setCellValue(0);
            dataRow.createCell(COL_WT).setCellValue(10);
            dataRow.createCell(COL_OUTPUT).setCellValue(1200);
            dataRow.createCell(COL_RFT).setCellValue(95); // 95%
            dataRow.createCell(COL_ALLOWANCE).setCellValue(100); // 100%
            dataRow.createCell(COL_SLOTS_START).setCellValue("311872"); // 07:00
            dataRow.createCell(COL_SLOTS_START + 1).setCellValue("311872"); // 08:00
            dataRow.createCell(COL_SLOTS_START + 2).setCellValue("311872"); // 09:00
            dataRow.createCell(COL_SLOTS_START + 3).setCellValue("311872"); // 10:00
            // 11:00 & 12:00 blank (lunch break)
            dataRow.createCell(COL_SLOTS_START + 6).setCellValue("311872"); // 13:00

            // Auto-size key columns
            for (int i = 0; i <= COL_ALLOWANCE; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void importExcel(MultipartFile file, String username) throws IOException {
        User user = userService.findByUsername(username);
        if (user == null)
            throw new RuntimeException("User not found");

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Try to find the "DB" sheet; fall back to first sheet
            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null)
                sheet = workbook.getSheetAt(0);

            // Row 0 is header — start from row 1 (iterating backwards ensures correct
            // "Recent Entries" display order)
            for (int i = sheet.getLastRowNum(); i >= 1; i--) {
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
                String line = getCellValueAsString(row.getCell(COL_LINE));
                Double mp = getCellValueAsDouble(row.getCell(COL_MP));
                Double dli = getCellValueAsDouble(row.getCell(COL_DLI));
                Double idl = getCellValueAsDouble(row.getCell(COL_IDL));
                Double wt = getCellValueAsDouble(row.getCell(COL_WT));
                Integer output = getCellValueAsInteger(row.getCell(COL_OUTPUT));
                Double rft = getCellValueAsDouble(row.getCell(COL_RFT));
                Double allowance = getCellValueAsDouble(row.getCell(COL_ALLOWANCE));

                if (output == null)
                    output = 0;
                if (allowance == null)
                    allowance = 100.0; // default 100%

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

                production.getDetails().addAll(details);
                productionRepository.save(production);
            }
        }
    }

    private Cell createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
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
