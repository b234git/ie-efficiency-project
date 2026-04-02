package thienloc.manage.testutil;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import thienloc.manage.entity.*;
import thienloc.manage.dto.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared test data builders used across all test classes.
 */
public final class TestDataFactory {

    private TestDataFactory() {}

    // ─── User ────────────────────────────────────────────────────────────────────

    public static User createUser(String username, String role) {
        return User.builder()
                .id(1L)
                .username(username)
                .password("encoded")
                .role(role)
                .build();
    }

    // ─── DailyProduction ─────────────────────────────────────────────────────────

    public static DailyProduction createDailyProduction(String section, String line,
                                                         Integer output, Double mp, Double wt) {
        DailyProduction dp = DailyProduction.builder()
                .id(1L)
                .productionDate(LocalDate.of(2026, 3, 15))
                .section(section)
                .line(line)
                .mp(mp)
                .dli(25.0)
                .idl(5.0)
                .wt(wt)
                .totalOutput(output != null ? output : 0)
                .rft(95.0)
                .allowance(0.85)
                .createdBy(createUser("admin", "ROLE_ADMIN"))
                .createdAt(LocalDateTime.now())
                .build();
        dp.setDetails(new ArrayList<>());
        return dp;
    }

    public static DailyProductionDetail createDailyProductionDetail(String timeSlot, String articleNo) {
        return DailyProductionDetail.builder()
                .timeSlot(timeSlot)
                .articleNo(articleNo)
                .output(0)
                .build();
    }

    // ─── DailyProductionDto ──────────────────────────────────────────────────────

    public static DailyProductionDto createDailyProductionDto() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setProductionDate(LocalDate.of(2026, 3, 15));
        dto.setSection("SEW");
        dto.setLine("1A");
        dto.setMp(30.0);
        dto.setDli(25.0);
        dto.setIdl(5.0);
        dto.setWt(8.0);
        dto.setOutput(1000);
        dto.setRft(95.0);
        dto.setAllowance(85.0);
        dto.setDetails(new ArrayList<>());
        return dto;
    }

    // ─── MasterDb ────────────────────────────────────────────────────────────────

    public static MasterDb createMasterDb(String ref, String articleNo, String dataMonth) {
        return MasterDb.builder()
                .id(1L)
                .ref(ref)
                .articleNo(articleNo)
                .dataMonth(dataMonth)
                .patternNo("P001")
                .shoeName("TestShoe")
                .osCode("DSO-001")
                .sewCt(1681.0)
                .sewMp(30.0)
                .sewQuotaDb(450.0)
                .sewPph(72.0)
                .build();
    }

    // ─── SplitEntry ──────────────────────────────────────────────────────────────

    public static SplitEntry createSplitEntry(LocalDate date, String section, String line) {
        SplitEntry entry = SplitEntry.builder()
                .id(1L)
                .productionDate(date)
                .section(section)
                .line(line)
                .status("PARTIAL")
                .build();
        entry.setDetails(new ArrayList<>());
        return entry;
    }

    public static SplitEntryDto createSplitEntryDto() {
        SplitEntryDto dto = new SplitEntryDto();
        dto.setProductionDate(LocalDate.of(2026, 3, 15));
        dto.setSection("SEW");
        dto.setLine("1A");
        dto.setMp(30.0);
        dto.setDli(25.0);
        dto.setIdl(5.0);
        return dto;
    }

    // ─── Notification ────────────────────────────────────────────────────────────

    public static Notification createNotification(String role, String title, boolean isRead) {
        return Notification.builder()
                .id(1L)
                .recipientRole(role)
                .title(title)
                .message("Test message")
                .type("WARNING")
                .isRead(isRead)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── SystemLog ───────────────────────────────────────────────────────────────

    public static SystemLog createSystemLog(String username, String action) {
        return SystemLog.builder()
                .id(1L)
                .username(username)
                .action(action)
                .details("Test details")
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ─── Excel Workbook Builders ─────────────────────────────────────────────────

    /**
     * Build production Excel (.xlsx) bytes matching ExcelService template format.
     * Compact input format per row: [Date, Section, Line, DL(MP), DLI, IDL, WT, Output, RFT, Allowance, articles...]
     * Mapped to physical columns: [0, 1, 2, 5, 6, 7, 9, 8, 10, 27, 11, 12, ...]
     * Data starts at row 2 (rows 0-1 are headers) matching ExcelService.DATA_START_ROW=2.
     */
    public static byte[] buildProductionExcelBytes(List<Object[]> dataRows) throws IOException {
        // Compact index → physical column mapping (indices 0-9, then articles at 10+ → cols 11+)
        int[] colMap = {0, 1, 2, 5, 6, 7, 9, 8, 10, 27};
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DB");
            // Row 0: main headers (simplified)
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Section");
            header.createCell(2).setCellValue("Line");
            header.createCell(5).setCellValue("DL");
            header.createCell(11).setCellValue("Article");
            // Row 1: blank sub-header row (ExcelService skips rows 0-1)
            sheet.createRow(1);
            // Data rows start at row 2
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 2);
                Object[] vals = dataRows.get(r);
                for (int c = 0; c < vals.length; c++) {
                    if (vals[c] == null) continue;
                    int physCol = (c < colMap.length) ? colMap[c] : (11 + c - colMap.length);
                    if (vals[c] instanceof Number) {
                        row.createCell(physCol).setCellValue(((Number) vals[c]).doubleValue());
                    } else {
                        row.createCell(physCol).setCellValue(vals[c].toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Build MasterDb Excel (.xlsx) bytes matching MasterDbImportService format.
     * Row 0-1: headers. Row 2+: data.
     * Each data row: REF, ArticleNo, PatternNo, ShoeName, OSCode, [32 section metrics]
     */
    public static byte[] buildMasterDbExcelBytes(List<Object[]> dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DB");
            // Header rows (simplified)
            sheet.createRow(0).createCell(0).setCellValue("REF");
            sheet.createRow(1).createCell(0).setCellValue("Sub-header");
            // Data rows start at row 2
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 2);
                Object[] vals = dataRows.get(r);
                for (int c = 0; c < vals.length; c++) {
                    if (vals[c] == null) continue;
                    if (vals[c] instanceof Number) {
                        row.createCell(c).setCellValue(((Number) vals[c]).doubleValue());
                    } else {
                        row.createCell(c).setCellValue(vals[c].toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Build LineSummaryReport (.xls) bytes matching LineSummaryImportService format.
     * Row 2, col 15: date (dd/MM/yyyy). Row 5+: data rows.
     * Data row: col0=Line, col1=Total, col2=MP, cols8-11=supervisor/mechanic/monitor/lineLeader
     */
    public static byte[] buildLineSummaryExcelBytes(String dateStr, List<Object[]> lineRows) throws IOException {
        try (Workbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            // Date at row 2, col 15
            Row dateRow = sheet.createRow(2);
            dateRow.createCell(15).setCellValue(dateStr);
            // Blank rows 3-4
            sheet.createRow(3);
            sheet.createRow(4);
            // Data rows from row 5
            for (int r = 0; r < lineRows.size(); r++) {
                Row row = sheet.createRow(r + 5);
                Object[] vals = lineRows.get(r);
                for (int c = 0; c < vals.length; c++) {
                    if (vals[c] == null) continue;
                    if (vals[c] instanceof Number) {
                        row.createCell(c).setCellValue(((Number) vals[c]).doubleValue());
                    } else {
                        row.createCell(c).setCellValue(vals[c].toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
