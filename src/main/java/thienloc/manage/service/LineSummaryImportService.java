package thienloc.manage.service;

import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LineSummaryImportService {

    private static final Map<String, String> SECTION_MAP = Map.of(
            "ASSY", "ASSEMBLY",
            "SEW", "SEW",
            "SF", "STOCKFIT"
    );

    @Data
    public static class LineSummaryPreview {
        private String filename;
        private String section;
        private LocalDate date;
        private List<LineRow> rows = new ArrayList<>();
        private int totalLines;
        private int skippedLines;
    }

    @Data
    public static class LineRow {
        private String line;
        private double dli;   // Excel "MP" column → app DLI field
        private double idl;   // Supervisor + Mechanic + Monitor + LineLeader
        private double total;
        private boolean active;
    }

    public LineSummaryPreview parseFile(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        String section = extractSection(filename);

        LineSummaryPreview preview = new LineSummaryPreview();
        preview.setFilename(filename);
        preview.setSection(section);

        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);

            // Date is at row 3 (index 2), col 15
            Row dateRow = sheet.getRow(2);
            String dateStr = getCellString(dateRow.getCell(15));
            preview.setDate(parseDate(dateStr));

            // Data rows start at row 6 (index 5)
            int total = 0;
            int skipped = 0;

            for (int r = 5; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String lineName = getCellString(row.getCell(0)).trim();
                if (lineName.isEmpty()) continue;

                double mp = getNumeric(row.getCell(2));
                double totalVal = getNumeric(row.getCell(1));
                double supervisor = getNumeric(row.getCell(8));
                double mechanic = getNumeric(row.getCell(9));
                double monitor = getNumeric(row.getCell(10));
                double lineLeader = getNumeric(row.getCell(11));
                double idl = supervisor + mechanic + monitor + lineLeader;

                total++;
                boolean active = mp > 0 || idl > 0;
                if (!active) skipped++;

                LineRow lineRow = new LineRow();
                lineRow.setLine(lineName);
                lineRow.setDli(mp);
                lineRow.setIdl(idl);
                lineRow.setTotal(totalVal);
                lineRow.setActive(active);
                preview.getRows().add(lineRow);
            }

            preview.setTotalLines(total);
            preview.setSkippedLines(skipped);
        }

        return preview;
    }

    private String extractSection(String filename) {
        if (filename == null)
            throw new IllegalArgumentException("Filename is null");

        String upper = filename.toUpperCase();
        for (Map.Entry<String, String> entry : SECTION_MAP.entrySet()) {
            if (upper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException(
                "Cannot determine section from filename: " + filename
                        + ". Expected filename containing ASSY, SEW, or SF.");
    }

    private LocalDate parseDate(String dateStr) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.parse(dateStr.trim(), fmt);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> "";
        };
    }

    private double getNumeric(Cell cell) {
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 0.0;
                }
            }
            default -> 0.0;
        };
    }
}
