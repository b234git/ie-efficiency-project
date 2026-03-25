package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class SplitEntryTemplateService {

    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    /**
     * Output template: Date | Section | Line | WT (hrs) | Total Output | RFT (%)
     */
    public byte[] generateOutputTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Output");

            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle sampleStyle = createSampleStyle(wb);

            // Row 0: Headers
            String[] headers = {"Date", "Section", "Line", "WT (hrs)", "Total Output", "RFT (%)"};
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Row 1: Sample data
            Row sample = sheet.createRow(1);
            Cell dateCell = sample.createCell(0);
            dateCell.setCellValue("2026-03-25");
            dateCell.setCellStyle(sampleStyle);

            Cell secCell = sample.createCell(1);
            secCell.setCellValue("SEW");
            secCell.setCellStyle(sampleStyle);

            Cell lineCell = sample.createCell(2);
            lineCell.setCellValue("1A");
            lineCell.setCellStyle(sampleStyle);

            setNumCell(sample, 3, 10.0, sampleStyle);
            setNumCell(sample, 4, 1200, sampleStyle);
            setNumCell(sample, 5, 95, sampleStyle);

            // Column widths
            sheet.setColumnWidth(0, 4000); // Date
            sheet.setColumnWidth(1, 5000); // Section
            sheet.setColumnWidth(2, 3000); // Line
            sheet.setColumnWidth(3, 3500); // WT
            sheet.setColumnWidth(4, 4500); // Total Output
            sheet.setColumnWidth(5, 3500); // RFT

            // Freeze header
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Articles template: Date | Section | Line | Allowance (%) | 07:00-08:00 | ... | 21:00-22:00
     */
    public byte[] generateArticlesTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Articles");

            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle slotHeaderStyle = createSlotHeaderStyle(wb);
            XSSFCellStyle sampleStyle = createSampleStyle(wb);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);

            String[] baseHeaders = {"Date", "Section", "Line", "Allowance (%)"};
            for (int i = 0; i < baseHeaders.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(baseHeaders[i]);
                c.setCellStyle(headerStyle);
            }
            for (int i = 0; i < TIME_SLOTS.size(); i++) {
                Cell c = headerRow.createCell(4 + i);
                c.setCellValue(TIME_SLOTS.get(i));
                c.setCellStyle(slotHeaderStyle);
            }

            // Row 1: Sample data
            Row sample = sheet.createRow(1);
            Cell dateCell = sample.createCell(0);
            dateCell.setCellValue("2026-03-25");
            dateCell.setCellStyle(sampleStyle);

            Cell secCell = sample.createCell(1);
            secCell.setCellValue("SEW");
            secCell.setCellStyle(sampleStyle);

            Cell lineCell = sample.createCell(2);
            lineCell.setCellValue("1A");
            lineCell.setCellStyle(sampleStyle);

            setNumCell(sample, 3, 100, sampleStyle);

            // Sample articles for first few slots
            String[] sampleArticles = {"Y01748", "Y01748", "Y01748", "Y01748", "Y01748",
                    "", "Y01748", "Y01748", "Y01748", "Y01748"};
            for (int i = 0; i < sampleArticles.length && i < TIME_SLOTS.size(); i++) {
                Cell c = sample.createCell(4 + i);
                c.setCellValue(sampleArticles[i]);
                c.setCellStyle(sampleStyle);
            }

            // Column widths
            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 5000);
            sheet.setColumnWidth(2, 3000);
            sheet.setColumnWidth(3, 4500);
            for (int i = 0; i < TIME_SLOTS.size(); i++) {
                sheet.setColumnWidth(4 + i, 3800);
            }

            // Freeze header
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─── Style factories ────────────────────────────────────────────────

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(
                new byte[]{(byte) 189, (byte) 215, (byte) 238}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style);
        return style;
    }

    private XSSFCellStyle createSlotHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(
                new byte[]{(byte) 255, (byte) 230, (byte) 100}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
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
