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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders production data into downloadable Excel reports.
 * Handles both the weekly block report and the flat daily-record report.
 */
@Service
public class EntryExcelExportService {

    public ByteArrayInputStream exportWeeklyReport(List<WeeklyReportDto> blocks, LocalDate weekStart) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle totalStyle = workbook.createCellStyle();
            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Group by section category, preserving HTML page order
            Map<String, List<WeeklyReportDto>> grouped = new LinkedHashMap<>();
            for (WeeklyReportDto block : blocks) {
                String cat = sectionCategory(block.getSection());
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
                    String lineLabel = cat + " \u2014 Line " + (block.getLine() != null ? block.getLine() : "");
                    Row titleRow = sheet.createRow(rowIdx);
                    Cell titleCell = titleRow.createCell(0);
                    titleCell.setCellValue(lineLabel);
                    titleCell.setCellStyle(titleStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, headers.length - 1));
                    rowIdx++;

                    if (filterHeaderRow == -1) filterHeaderRow = rowIdx;
                    Row headerRow = sheet.createRow(rowIdx++);
                    for (int c = 0; c < headers.length; c++) {
                        styled(headerRow, c, headers[c], headerStyle);
                    }

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
                        dataRow.createCell(10).setCellValue(row.getActualPph() != null ? round1(row.getActualPph()) : 0);
                        dataRow.createCell(11).setCellValue(row.getStdPph() != null ? round1(row.getStdPph()) : 0);
                        if (row.getActualPph() != null && row.getStdPph() != null && row.getStdPph() > 0) {
                            double pct = (row.getActualPph() - row.getStdPph()) / row.getStdPph() * 100;
                            dataRow.createCell(12).setCellValue((pct >= 0 ? "\u25B2 +" : "\u25BC ") + round1(pct) + "%");
                        } else {
                            dataRow.createCell(12).setCellValue("\u2014");
                        }
                    }

                    WeeklyReportDto.SummaryRow total = block.getTotal();
                    Row totalRow = sheet.createRow(rowIdx++);
                    styled(totalRow, 0, "Total", totalStyle);
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
                            totalRow.getCell(12).setCellValue((pct >= 0 ? "\u25B2 +" : "\u25BC ") + round1(pct) + "%");
                        }
                    }

                    sheet.createRow(rowIdx++);
                }

                if (filterHeaderRow >= 0) {
                    sheet.setAutoFilter(new CellRangeAddress(filterHeaderRow, rowIdx - 1, 0, headers.length - 1));
                    sheet.createFreezePane(0, filterHeaderRow + 1);
                }

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
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
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
                styled(headerRow, c, headers[c], headerStyle);
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

    private static String sectionCategory(String section) {
        if (section == null) return "ASSY";
        String s = section.toUpperCase();
        if (s.contains("BUFF")) return "BUFFING";
        if (s.contains("SEW")) return "SEW";
        if (s.contains("STOCKFIT") || s.equals("SF") || s.contains(" SF")) return "SF";
        return "ASSY";
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private static Cell styled(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }
}
