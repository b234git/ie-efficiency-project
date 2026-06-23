package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.dto.SalaryReportDto.DayRow;
import thienloc.manage.dto.SalaryReportDto.SectionLineBlock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exports the incentive/salary report to .xlsx, laid out like the workbook's "S"
 * sheet: a vertical stack of per-(section,line) blocks, each with a title row
 * (6S / new-style incentive), a grade header, the daily rows, and a grade-totals
 * row. Built from the already-computed {@link SalaryReportDto}.
 */
@Service
public class SalaryExcelExportService {

    /** Fixed columns before the per-grade amount columns. */
    private static final String[] FIXED = {
            "Date", "Quota", "Std MP", "MP", "WT", "Output", "SEC", "EFF%", "Rate"};

    public ByteArrayInputStream export(SalaryReportDto report) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle title = titleStyle(wb);
            CellStyle total = totalStyle(wb);

            Sheet sheet = wb.createSheet("S");
            int r = 0;

            if (report != null && report.getBlocks() != null) {
                for (SectionLineBlock b : report.getBlocks()) {
                    List<String> grades = b.getGradeLabels() != null ? b.getGradeLabels() : List.of();
                    int lastCol = FIXED.length + grades.size() - 1;

                    // Block title (merged across the row)
                    Row tr = sheet.createRow(r);
                    cell(tr, 0, blockTitle(b), title);
                    if (lastCol > 0) sheet.addMergedRegion(new CellRangeAddress(r, r, 0, lastCol));
                    r++;

                    // Grade header
                    Row hr = sheet.createRow(r++);
                    int c = 0;
                    for (String h : FIXED) cell(hr, c++, h, header);
                    for (String g : grades) cell(hr, c++, g, header);

                    // Daily rows
                    if (b.getDailyRows() != null) {
                        for (DayRow d : b.getDailyRows()) {
                            Row dr = sheet.createRow(r++);
                            c = 0;
                            cell(dr, c++, d.getDate() != null ? d.getDate().toString() : "", null);
                            numCell(dr, c++, round(d.getTargetQuota(), 1));
                            numCell(dr, c++, round(d.getTargetMp(), 1));
                            numCell(dr, c++, d.getMp());
                            numCell(dr, c++, round(d.getWt(), 1));
                            numCell(dr, c++, round(d.getOutput(), 0));
                            cell(dr, c++, d.getSec(), null);
                            cell(dr, c++, round(d.getEffSalary(), 1) + "%", null);
                            numCell(dr, c++, round(d.getBaseRate(), 0));
                            long[] ga = d.getGradeAmounts();
                            for (int i = 0; i < grades.size(); i++) {
                                numCell(dr, c++, ga != null && i < ga.length ? ga[i] : 0);
                            }
                        }
                    }

                    // Grade totals row (aligned under the grade columns)
                    Row totr = sheet.createRow(r++);
                    cell(totr, 0, "TOTAL", total);
                    long[] gt = b.getGradeTotals();
                    for (int i = 0; i < grades.size(); i++) {
                        Cell cc = totr.createCell(FIXED.length + i);
                        cc.setCellValue(gt != null && i < gt.length ? gt[i] : 0);
                        cc.setCellStyle(total);
                    }
                    r++; // blank separator between blocks
                }
            }

            for (int c = 0; c < FIXED.length; c++) sheet.autoSizeColumn(c);
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static String blockTitle(SectionLineBlock b) {
        return (b.getSection() != null ? b.getSection() : "") + " — Line "
                + (b.getLine() != null ? b.getLine() : "")
                + "   |   6S=" + round(b.getSixSPercent(), 1) + "%"
                + "   Reprocess=" + round(b.getReprocessPercent(), 1) + "%"
                + "   |   NEW STYLE=" + b.getNewStyleCount()
                + " (" + b.getNewStyleIncentive() + ")";
    }

    // ── POI helpers ─────────────────────────────────────────────────────────
    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle totalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    private static void numCell(Row row, int col, double value) {
        row.createCell(col).setCellValue(value);
    }

    private static double round(double v, int dp) {
        double f = Math.pow(10, dp);
        return Math.round(v * f) / f;
    }
}
