package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Extracted from MasterDbController.downloadTemplate().
 * Generates the MasterDb Excel template with proper formatting.
 */
@Service
public class MasterDbTemplateService {

    private static final String[] ID_COLS = { "REF", "Article No.", "Pattern No.", "Shoe Name", "OS Code" };
    private static final String[] SECTIONS = { "SEW", "BUFFING 1ST", "BUFFING 2ND", "STOCKFIT UV",
            "STOCKFIT 1ST", "STOCKFIT 2ND", "ASSEMBLY BIG", "ASSEMBLY SMALL" };
    private static final String[] SUB_HEADERS = { "CT", "MP", "QUOTA", "PPH" };

    public byte[] generateTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("DB");

            // ── Cell styles ──────────────────────────────────────────
            XSSFCellStyle styleSection = createSectionStyle(wb);
            XSSFCellStyle styleId = createIdStyle(wb);
            XSSFCellStyle styleSub = createSubHeaderStyle(wb);
            XSSFCellStyle styleSample = createSampleStyle(wb);

            // ── Row 0: Section headers ───────────────────────────────
            Row header1 = sheet.createRow(0);
            header1.setHeightInPoints(20);

            for (int i = 0; i < ID_COLS.length; i++) {
                Cell c = header1.createCell(i);
                c.setCellValue(ID_COLS[i]);
                c.setCellStyle(styleId);
            }

            int startCol = 5;
            for (String sec : SECTIONS) {
                Cell c = header1.createCell(startCol);
                c.setCellValue(sec);
                c.setCellStyle(styleSection);
                for (int k = startCol + 1; k < startCol + 4; k++) {
                    Cell blank = header1.createCell(k);
                    blank.setCellStyle(styleSection);
                }
                sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, startCol + 3));
                startCol += 4;
            }

            // Merge identity cols vertically (rows 0-1)
            for (int i = 0; i < 5; i++) {
                sheet.addMergedRegion(new CellRangeAddress(0, 1, i, i));
            }

            // ── Row 1: Sub-headers (CT / MP / QUOTA / PPH) ──────────
            Row header2 = sheet.createRow(1);
            header2.setHeightInPoints(16);

            for (int i = 0; i < 5; i++) {
                Cell c = header2.createCell(i);
                c.setCellStyle(styleId);
            }

            for (int col = 5; col < 5 + (8 * 4); col++) {
                Cell c = header2.createCell(col);
                c.setCellValue(SUB_HEADERS[(col - 5) % 4]);
                c.setCellStyle(styleSub);
            }

            // ── Row 2: Sample data ──────────────────────────────────
            Row sample = sheet.createRow(2);
            String[] sampleVals = { "Y01748P7402H0038", "Y01748P7402", "DS-1628", "S-CLEVER LOW", "DSO-241" };
            for (int i = 0; i < sampleVals.length; i++) {
                Cell c = sample.createCell(i);
                c.setCellValue(sampleVals[i]);
                c.setCellStyle(styleSample);
            }
            setNumCell(sample, 5, 1681.0, styleSample);
            setNumCell(sample, 6, 30.0, styleSample);
            setNumCell(sample, 7, 600.0, styleSample);
            setNumCell(sample, 8, 2.0, styleSample);
            setNumCell(sample, 9, 71.5, styleSample);
            setNumCell(sample, 10, 4.0, styleSample);
            setNumCell(sample, 11, 2000.0, styleSample);
            setNumCell(sample, 12, 50.0, styleSample);

            // ── Column widths ────────────────────────────────────────
            for (int i = 0; i < 5; i++)
                sheet.setColumnWidth(i, 5000);
            for (int i = 5; i < 37; i++)
                sheet.setColumnWidth(i, 3200);

            // ── Freeze top 2 rows ────────────────────────────────────
            sheet.createFreezePane(0, 2);

            // ── Write to bytes ───────────────────────────────────────
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─── Style factories ─────────────────────────────────────────────────────────

    private XSSFCellStyle createSectionStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont fontBold = wb.createFont();
        fontBold.setBold(true);
        fontBold.setFontHeightInPoints((short) 10);
        style.setFont(fontBold);
        style.setFillForegroundColor(new XSSFColor(
                new byte[] { (byte) 255, (byte) 230, (byte) 100 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private XSSFCellStyle createIdStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont fontBold = wb.createFont();
        fontBold.setBold(true);
        fontBold.setFontHeightInPoints((short) 10);
        style.setFont(fontBold);
        style.setFillForegroundColor(new XSSFColor(
                new byte[] { (byte) 189, (byte) 215, (byte) 238 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setBorder(style);
        return style;
    }

    private XSSFCellStyle createSubHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont fontSubBold = wb.createFont();
        fontSubBold.setBold(true);
        fontSubBold.setFontHeightInPoints((short) 9);
        style.setFont(fontSubBold);
        style.setFillForegroundColor(new XSSFColor(
                new byte[] { (byte) 217, (byte) 217, (byte) 217 }, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private XSSFCellStyle createSampleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        setBorder(style);
        return style;
    }

    private void setBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private void setNumCell(Row row, int col, double val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }
}
