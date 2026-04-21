package thienloc.manage.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

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
 * Generates the daily-production Excel import template (DB sheet).
 * Kept separate from import/export logic so each class stays single-purpose.
 */
@Service
public class ExcelTemplateService {

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DB");

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle articleHeaderStyle = workbook.createCellStyle();
            articleHeaderStyle.setFont(boldFont);
            articleHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            articleHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            articleHeaderStyle.setBorderBottom(BorderStyle.THIN);
            articleHeaderStyle.setBorderTop(BorderStyle.THIN);
            articleHeaderStyle.setBorderLeft(BorderStyle.THIN);
            articleHeaderStyle.setBorderRight(BorderStyle.THIN);
            articleHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            articleHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

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
            slotStyle.setAlignment(HorizontalAlignment.CENTER);
            slotStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            slotStyle.setWrapText(true);

            Row row0 = sheet.createRow(0);
            styled(row0, COL_DATE, "Date", headerStyle);
            styled(row0, COL_SECTION, "Section", headerStyle);
            styled(row0, COL_LINE, "Line", headerStyle);
            styled(row0, COL_SUBLINE, "sub-line", headerStyle);
            styled(row0, COL_SUBSECTION, "sub-section", headerStyle);
            styled(row0, COL_DL, "DL", headerStyle);
            styled(row0, COL_DLI, "DLI", headerStyle);
            styled(row0, COL_IDL, "IDL", headerStyle);
            styled(row0, COL_OUTPUT, "Output", headerStyle);
            styled(row0, COL_WT, "WT", headerStyle);
            styled(row0, COL_RFT, "RFT", headerStyle);
            styled(row0, COL_SLOTS_START, "Article", articleHeaderStyle);
            for (int c = COL_SLOTS_START + 1; c <= COL_SLOTS_START + 14; c++) {
                styled(row0, c, "", articleHeaderStyle);
            }
            styled(row0, COL_ARTICLE, "ARTICLE", headerStyle);
            styled(row0, COL_ALLOWANCE, "Allowance", headerStyle);

            sheet.addMergedRegion(new CellRangeAddress(0, 0, COL_SLOTS_START, COL_SLOTS_START + 14));
            for (int c = 0; c <= COL_RFT; c++) {
                sheet.addMergedRegion(new CellRangeAddress(0, 1, c, c));
            }
            sheet.addMergedRegion(new CellRangeAddress(0, 1, COL_ARTICLE, COL_ARTICLE));
            sheet.addMergedRegion(new CellRangeAddress(0, 1, COL_ALLOWANCE, COL_ALLOWANCE));

            Row row1 = sheet.createRow(1);
            int colIdx = COL_SLOTS_START;
            for (String slot : TIME_SLOTS) {
                String[] parts = slot.split("-");
                styled(row1, colIdx++, parts[0] + "\n" + parts[1], slotStyle);
            }

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
            dataRow.createCell(COL_SLOTS_START + 6).setCellValue("311872");
            dataRow.createCell(COL_ARTICLE).setCellValue("311872");
            dataRow.createCell(COL_ALLOWANCE).setCellValue(100);

            sheet.createFreezePane(0, DATA_START_ROW);

            for (int i = 0; i <= COL_ALLOWANCE; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static Cell styled(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }
}
