package thienloc.manage.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class SplitEntryTemplateService {

    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    /**
     * Output template: Date | Section | Subsection | Line | WT (hrs) | Total Output | RFT (%)
     */
    public byte[] generateOutputTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Output");

            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle sampleStyle = createSampleStyle(wb);

            // Row 0: Headers
            String[] headers = {"Date", "Section", "Subsection", "Line", "WT (hrs)", "Total Output", "RFT (%)"};
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Row 1: Sample data (SEW - no subsection)
            Row sample = sheet.createRow(1);
            Cell dateCell = sample.createCell(0);
            dateCell.setCellValue("2026-03-25");
            dateCell.setCellStyle(sampleStyle);

            Cell secCell = sample.createCell(1);
            secCell.setCellValue("SEW");
            secCell.setCellStyle(sampleStyle);

            Cell subSecCell = sample.createCell(2);
            subSecCell.setCellValue("");
            subSecCell.setCellStyle(sampleStyle);

            Cell lineCell = sample.createCell(3);
            lineCell.setCellValue("1A");
            lineCell.setCellStyle(sampleStyle);

            setNumCell(sample, 4, 10.0, sampleStyle);
            setNumCell(sample, 5, 1200, sampleStyle);
            setNumCell(sample, 6, 95, sampleStyle);

            // Row 2: Sample data (BUFFING with subsection)
            Row sample2 = sheet.createRow(2);
            Cell dateCell2 = sample2.createCell(0);
            dateCell2.setCellValue("2026-03-25");
            dateCell2.setCellStyle(sampleStyle);

            Cell secCell2 = sample2.createCell(1);
            secCell2.setCellValue("BUFFING");
            secCell2.setCellStyle(sampleStyle);

            Cell subSecCell2 = sample2.createCell(2);
            subSecCell2.setCellValue("1ST");
            subSecCell2.setCellStyle(sampleStyle);

            Cell lineCell2 = sample2.createCell(3);
            lineCell2.setCellValue("1A");
            lineCell2.setCellStyle(sampleStyle);

            setNumCell(sample2, 4, 10.0, sampleStyle);
            setNumCell(sample2, 5, 800, sampleStyle);
            setNumCell(sample2, 6, 98, sampleStyle);

            // Column widths
            sheet.setColumnWidth(0, 4000); // Date
            sheet.setColumnWidth(1, 5000); // Section
            sheet.setColumnWidth(2, 4000); // Subsection
            sheet.setColumnWidth(3, 3000); // Line
            sheet.setColumnWidth(4, 3500); // WT
            sheet.setColumnWidth(5, 4500); // Total Output
            sheet.setColumnWidth(6, 3500); // RFT

            // Freeze header
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Articles template: Date | Section | Subsection | Line | Allowance (%) | 07:00-08:00 | ... | 21:00-22:00
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

            String[] baseHeaders = {"Date", "Section", "Subsection", "Line", "Allowance (%)"};
            for (int i = 0; i < baseHeaders.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(baseHeaders[i]);
                c.setCellStyle(headerStyle);
            }
            for (int i = 0; i < TIME_SLOTS.size(); i++) {
                Cell c = headerRow.createCell(5 + i);
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

            Cell subSecCell = sample.createCell(2);
            subSecCell.setCellValue("");
            subSecCell.setCellStyle(sampleStyle);

            Cell lineCell = sample.createCell(3);
            lineCell.setCellValue("1A");
            lineCell.setCellStyle(sampleStyle);

            setNumCell(sample, 4, 100, sampleStyle);

            // Sample articles for first few slots
            String[] sampleArticles = {"Y01748", "Y01748", "Y01748", "Y01748", "Y01748",
                    "", "Y01748", "Y01748", "Y01748", "Y01748"};
            for (int i = 0; i < sampleArticles.length && i < TIME_SLOTS.size(); i++) {
                Cell c = sample.createCell(5 + i);
                c.setCellValue(sampleArticles[i]);
                c.setCellStyle(sampleStyle);
            }

            // Column widths
            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 5000);
            sheet.setColumnWidth(2, 4000);
            sheet.setColumnWidth(3, 3000);
            sheet.setColumnWidth(4, 4500);
            for (int i = 0; i < TIME_SLOTS.size(); i++) {
                sheet.setColumnWidth(5 + i, 3800);
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
