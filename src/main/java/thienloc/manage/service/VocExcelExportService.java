package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import thienloc.manage.dto.VocChemicalSummaryDto;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReconcileRowDto;
import thienloc.manage.dto.VocReconcileWeekDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocSubconReportDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exports the VOC monthly report to .xlsx — the output role the Excel workbook
 * used to fill. The headline sheet mirrors the workbook's SF/SP "VOC INDEX &
 * water/solvent consumption" regulatory rollup (the piece the app had no way to
 * produce before), built from the already-computed {@link VocReportDto}.
 */
@Service
public class VocExcelExportService {

    public ByteArrayInputStream exportMonthlyReport(VocReportDto report) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle title = titleStyle(wb);

            buildRollupSheet(wb, report, header, title);
            buildReconcileSheet(wb, report, header, title);
            buildLineSheet(wb, report, header, title);

            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** Subcon report (CEMENT/ACTUAL CEMENT): rows per (date, subcon, article) × chemical, standard vs actual. */
    public ByteArrayInputStream exportSubcon(VocSubconReportDto report) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);
            CellStyle title = titleStyle(wb);
            Sheet sheet = wb.createSheet("SUBCON");
            int row = 0;

            cell(sheet.createRow(row++), 0, "SUBCON — STANDARD vs ACTUAL (kg)", title);
            cell(sheet.createRow(row++), 0,
                    "Tháng: " + (report.getSelectedMonth() != null ? report.getSelectedMonth() : "-"), null);
            row++;

            List<String> chems = report.getChemicals();
            // Header: identity + per-chemical (Std | Act | Thiếu) + totals
            Row h = sheet.createRow(row++);
            int c = 0;
            cell(h, c++, "Ngày", header);
            cell(h, c++, "Nhà thầu", header);
            cell(h, c++, "Mã hàng", header);
            cell(h, c++, "Output", header);
            for (String ch : chems) {
                cell(h, c++, ch + " (Std)", header);
                cell(h, c++, ch + " (Act)", header);
                cell(h, c++, ch + " (Thiếu)", header);
            }
            cell(h, c++, "Σ Std", header);
            cell(h, c++, "Σ Act", header);
            cell(h, c++, "Σ Thiếu", header);
            cell(h, c, "VOC kg", header);

            for (VocSubconReportDto.Row r : report.getRows()) {
                Row d = sheet.createRow(row++);
                c = 0;
                cell(d, c++, r.getDate() != null ? r.getDate().toString() : "", null);
                cell(d, c++, r.getSubcontractor(), null);
                cell(d, c++, r.getArticleNo(), null);
                numCell(d, c++, r.getOutput());
                for (String ch : chems) {
                    VocReconcileCellDto cell = r.getCells().get(ch);
                    numCell(d, c++, cell != null ? round(cell.getAllowanceKg(), 3) : 0);
                    numCell(d, c++, cell != null ? round(cell.getActualKg(), 3) : 0);
                    numCell(d, c++, cell != null ? round(cell.getDiffKg(), 3) : 0);
                }
                numCell(d, c++, round(r.getTotalStandardKg(), 3));
                numCell(d, c++, round(r.getTotalActualKg(), 3));
                numCell(d, c++, round(r.getTotalShortageKg(), 3));
                numCell(d, c, round(r.getVocKg(), 3));
            }

            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** "%" reconcile sheet: weekly bands of per-date ratio cells + weekly/grand totals + HIGH/LOW ranking. */
    private void buildReconcileSheet(Workbook wb, VocReportDto r, CellStyle header, CellStyle title) {
        Sheet sheet = wb.createSheet("%");
        List<String> chems = r.getReconcileChemicals();
        int row = 0;
        cell(sheet.createRow(row++), 0, "VOC % — Định mức vs Thực tế (Actual/Allowance)", title);
        row++;

        for (VocReconcileWeekDto week : r.getReconcileWeeks()) {
            Row band = sheet.createRow(row++);
            cell(band, 0, "Tuần " + (week.getLabel() != null ? week.getLabel() : "")
                    + "   HIGH: " + rankText(week.getHigh()) + "   LOW: " + rankText(week.getLow()), title);

            row = writeReconcileHeader(sheet, row, chems, header);
            if (week.getRows() != null) {
                for (VocReconcileRowDto dr : week.getRows()) {
                    row = writeReconcileRow(sheet, row, chems,
                            dr.getDate() != null ? dr.getDate().toString() : "", dr);
                }
            }
            if (week.getTotalRow() != null) {
                row = writeReconcileRow(sheet, row, chems, "Total tuần", week.getTotalRow());
            }
            row++;
        }
        if (r.getReconcileTotal() != null) {
            row = writeReconcileHeader(sheet, row, chems, header);
            writeReconcileRow(sheet, row, chems, "GRAND TOTAL", r.getReconcileTotal());
        }
    }

    /** "EFF" sheet: one row per line × chemical ratio cells + total + ranking. */
    private void buildLineSheet(Workbook wb, VocReportDto r, CellStyle header, CellStyle title) {
        if (r.getByLineRows() == null || r.getByLineRows().isEmpty()) return;
        Sheet sheet = wb.createSheet("EFF");
        List<String> chems = r.getReconcileChemicals();
        int row = 0;
        cell(sheet.createRow(row++), 0, "VOC theo Line   HIGH: " + rankText(r.getByLineHigh())
                + "   LOW: " + rankText(r.getByLineLow()), title);
        row++;

        Row h = sheet.createRow(row++);
        int c = 0;
        cell(h, c++, "Line", header);
        for (String ch : chems) cell(h, c++, ch, header);
        cell(h, c++, "g/pair", header);
        cell(h, c++, "Output", header);
        cell(h, c, "VOC(g)", header);

        for (VocReconcileRowDto lr : r.getByLineRows()) {
            row = writeReconcileRow(sheet, row, chems, lr.getLine() != null ? lr.getLine() : "", lr);
        }
        if (r.getByLineTotal() != null) {
            writeReconcileRow(sheet, row, chems, "TOTAL", r.getByLineTotal());
        }
    }

    private int writeReconcileHeader(Sheet sheet, int row, List<String> chems, CellStyle header) {
        Row h = sheet.createRow(row++);
        int c = 0;
        cell(h, c++, "Ngày", header);
        for (String ch : chems) cell(h, c++, ch, header);
        cell(h, c++, "g/pair", header);
        cell(h, c++, "Output", header);
        cell(h, c, "VOC(g)", header);
        return row;
    }

    private int writeReconcileRow(Sheet sheet, int row, List<String> chems, String label, VocReconcileRowDto dr) {
        Row d = sheet.createRow(row++);
        int c = 0;
        cell(d, c++, label, null);
        for (String ch : chems) {
            cell(d, c++, ratioText(dr.getCells().get(ch)), null);
        }
        numCell(d, c++, round(dr.getVocPerPair(), 2));
        numCell(d, c++, dr.getOutput());
        numCell(d, c, round(dr.getVocGrams(), 1));
        return row;
    }

    /** Cell text mirroring the % sheet: "85% (+0.40)" or status NC/NA. */
    private static String ratioText(VocReconcileCellDto cell) {
        if (cell == null) return "";
        if ("NC".equals(cell.getStatus()) || "NA".equals(cell.getStatus())) return cell.getStatus();
        if (cell.getRatio() == null) return "";
        String diff = (cell.getDiffKg() >= 0 ? "+" : "") + round(cell.getDiffKg(), 2);
        return round(cell.getRatio() * 100, 1) + "% (" + diff + ")";
    }

    private static String rankText(List<VocReportDto.ChemRank> ranks) {
        if (ranks == null || ranks.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (VocReportDto.ChemRank r : ranks) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.code()).append(" ").append(round(r.ratio() * 100, 0)).append("%");
        }
        return sb.toString();
    }

    /** SF/SP-style regulatory rollup: headline VOC index + per-chemical breakdown. */
    private void buildRollupSheet(Workbook wb, VocReportDto r, CellStyle header, CellStyle title) {
        Sheet sheet = wb.createSheet("VOC Index (SF-SP)");
        int row = 0;

        Row t = sheet.createRow(row++);
        cell(t, 0, "VOC INDEX & WATER/SOLVENT CONSUMPTION", title);
        cell(sheet.createRow(row++), 0,
                "Tháng: " + (r.getSelectedMonth() != null ? r.getSelectedMonth() : "-"), null);
        row++;

        // ── Headline totals ──────────────────────────────────────────────
        kv(sheet.createRow(row++), "Số đôi (No. of Pairs)", r.getTotalOutput());
        kv(sheet.createRow(row++), "Tổng VOC (kg)", round(r.getTotalVocKg(), 3));
        kv(sheet.createRow(row++), "Tổng VOC (grams)", round(r.getTotalVocGrams(), 1));
        kv(sheet.createRow(row++), "VOC Index (g/đôi)", round(r.getAvgPerPair(), 4));
        kv(sheet.createRow(row++), "Water-based (kg)", round(r.getTotalWaterKg(), 2));
        kv(sheet.createRow(row++), "Solvent (kg)", round(r.getTotalSolventKg(), 2));
        kv(sheet.createRow(row++), "Water-based %", round(r.getWaterPct(), 1));
        kv(sheet.createRow(row++), "Tổng chi phí ($)", round(r.getTotalCost(), 2));
        row++;

        // ── Per-chemical breakdown ───────────────────────────────────────
        String[] cols = {"Mã hoá chất", "Loại (Water/Solvent)", "Phân loại",
                "Lượng dùng (kg)", "VOC (kg)", "Chi phí ($)"};
        Row h = sheet.createRow(row++);
        for (int c = 0; c < cols.length; c++) cell(h, c, cols[c], header);

        for (VocChemicalSummaryDto chem : r.getChemicals()) {
            Row d = sheet.createRow(row++);
            cell(d, 0, chem.getCode(), null);
            cell(d, 1, chem.getMaterialType(), null);
            cell(d, 2, chem.getClassification(), null);
            numCell(d, 3, round(chem.getQuantityKg(), 3));
            numCell(d, 4, round(chem.getVocKg(), 3));
            numCell(d, 5, round(chem.getCost(), 2));
        }

        for (int c = 0; c < cols.length; c++) sheet.autoSizeColumn(c);
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
        f.setFontHeightInPoints((short) 13);
        s.setFont(f);
        return s;
    }

    private static void kv(Row row, String label, double value) {
        cell(row, 0, label, null);
        numCell(row, 1, value);
    }

    private static void kv(Row row, String label, int value) {
        cell(row, 0, label, null);
        Cell c = row.createCell(1);
        c.setCellValue(value);
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
