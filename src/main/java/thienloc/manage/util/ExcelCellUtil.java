package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Centralized Excel cell parsing utilities.
 * Eliminates the duplicated getCellString / getCellDouble / getCellInteger / parseDateCell
 * methods that were scattered across MasterDbImportService, SplitEntryImportService,
 * NewStyleService, LineSummaryImportService, and ExcelService.
 */
public final class ExcelCellUtil {

    private ExcelCellUtil() {}

    /**
     * Read a cell as String. Returns {@code null} for null/blank/unsupported cells.
     */
    public static String getString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String v = cell.getStringCellValue();
                yield (v == null || v.isBlank()) ? null : v;
            }
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == (long) d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    double fd = cell.getNumericCellValue();
                    yield (fd == (long) fd) ? String.valueOf((long) fd) : String.valueOf(fd);
                }
            }
            default -> null;
        };
    }

    /**
     * Read a cell as String. Returns {@code ""} (empty string) instead of {@code null}
     * for null/blank/unsupported cells. Useful for LineSummaryImportService-style code.
     */
    public static String getStringOrEmpty(Cell cell) {
        String v = getString(cell);
        return v != null ? v : "";
    }

    /**
     * Read a cell as Double. Returns {@code null} if the cell is missing or unparseable.
     */
    public static Double getDouble(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC, FORMULA -> cell.getNumericCellValue();
                case STRING -> {
                    String s = cell.getStringCellValue().trim();
                    yield s.isEmpty() ? null : Double.parseDouble(s);
                }
                default -> null;
            };
        } catch (NumberFormatException | IllegalStateException e) {
            return null;
        }
    }

    /**
     * Read a cell as double (primitive). Returns {@code 0.0} if the cell is missing or
     * unparseable. Useful for LineSummaryImportService-style code where 0 is the default.
     */
    public static double getNumericOrZero(Cell cell) {
        Double v = getDouble(cell);
        return v != null ? v : 0.0;
    }

    /**
     * Read a cell as Integer. Returns {@code null} if the cell is missing or unparseable.
     */
    public static Integer getInteger(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        return switch (type) {
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

    /**
     * Read a cell as LocalDate. Handles both Excel numeric date cells (via DateUtil)
     * and ISO-formatted string cells. Returns {@code null} on any failure.
     */
    public static LocalDate parseDateCell(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            // Date columns in the EFF workbooks are often fill-down formulas (=C3+1);
            // resolve to the cached result type so numeric/string dates still parse.
            if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
            if (type == CellType.NUMERIC) {
                Date javaDate = DateUtil.getJavaDate(cell.getNumericCellValue());
                return javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } else if (type == CellType.STRING) {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : LocalDate.parse(val);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
