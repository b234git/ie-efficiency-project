package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves canonical column positions from an Excel header row, supporting
 * both the project's template layout and the factory's "EFF APR V6" layout.
 *
 * The factory file labels its REF/REF 2 prefix columns and uses different
 * positions for Section/Line/Subline (which carry no header label, or a
 * mislabelled "Line" header). When REF 1 is detected, this resolver applies
 * the known factory positions for the prefix block and falls back to header
 * search for the remaining columns. Otherwise it relies entirely on header
 * labels.
 */
public final class HeaderResolver {

    private static final Pattern TIME_SLOT = Pattern.compile(
            "^\\s*(\\d{1,2}):(\\d{2})\\s*[\\n\\-]\\s*(\\d{1,2}):(\\d{2})\\s*$");

    public enum Format { PROJECT_TEMPLATE, FACTORY_XLSX }

    private final Format format;
    private final Map<CanonicalColumn, Integer> columns;
    private final List<Integer> timeSlotColumns;
    private final int dataStartRow;

    private HeaderResolver(Format format, Map<CanonicalColumn, Integer> columns,
                          List<Integer> timeSlotColumns, int dataStartRow) {
        this.format = format;
        this.columns = columns;
        this.timeSlotColumns = Collections.unmodifiableList(timeSlotColumns);
        this.dataStartRow = dataStartRow;
    }

    public Format getFormat() { return format; }
    public List<Integer> getTimeSlotColumns() { return timeSlotColumns; }
    public int getDataStartRow() { return dataStartRow; }

    public Integer get(CanonicalColumn column) { return columns.get(column); }
    public boolean has(CanonicalColumn column) { return columns.containsKey(column); }

    public Cell cell(Row row, CanonicalColumn column) {
        if (row == null) return null;
        Integer idx = columns.get(column);
        return idx == null ? null : row.getCell(idx);
    }

    /** Cells aligned with {@link #getTimeSlotColumns()} (size matches). */
    public List<Cell> slotCells(Row row) {
        if (row == null) return List.of();
        List<Cell> out = new ArrayList<>(timeSlotColumns.size());
        for (int idx : timeSlotColumns) out.add(row.getCell(idx));
        return out;
    }

    /** Standardised slot label (HH:mm-HH:mm) for the time-slot at the given index. */
    public static String slotLabel(int index) {
        // The project's existing TIME_SLOTS list (07:00-08:00 ... 21:00-22:00) is
        // what downstream code expects. Use it when the index is in range; otherwise
        // synthesise a fallback so per-slot data is still keyed deterministically.
        if (index >= 0 && index < EntryExcelLayout.TIME_SLOTS.size()) {
            return EntryExcelLayout.TIME_SLOTS.get(index);
        }
        return "slot-" + index;
    }

    public static HeaderResolver resolve(Sheet sheet) {
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet is null");
        }
        Row row1 = sheet.getRow(0);
        Row row2 = sheet.getRow(1);
        if (row1 == null) {
            throw new IllegalStateException("Sheet has no header row");
        }

        // 1. Detect time-slot columns (in row 1 or row 2).
        List<Integer> slots = detectTimeSlots(row1, row2);
        Set<Integer> slotSet = new HashSet<>(slots);

        // 2. Detect format.
        Format fmt = findHeader(row1, CanonicalColumn.REF1, slotSet) >= 0
                ? Format.FACTORY_XLSX
                : Format.PROJECT_TEMPLATE;

        // 3. Build column map.
        Map<CanonicalColumn, Integer> map = new EnumMap<>(CanonicalColumn.class);

        if (fmt == Format.FACTORY_XLSX) {
            // Fixed-position prefix block (cols 0-12 in EFF APR V6 format).
            putIfPresent(map, CanonicalColumn.REF1, 0);
            putIfPresent(map, CanonicalColumn.REF2, 1);
            putIfPresent(map, CanonicalColumn.DATE, 2);
            // SECTION/LINE/SUBLINE form a 3-column block whose offset shifted between the
            // factory's APR V6 (Section=4, Line=5, Subline=6) and MAY V6 (Section=3, Line=4,
            // Subline=5) layouts. The "Line" header label is unreliable (in APR V6 it sits
            // above the Section column, in MAY V6 above the Line column), so detect the
            // Section column by its data content and derive Line/Subline from it.
            int sectionCol = detectSectionColumn(sheet, slotSet, 4, 3);
            putIfPresent(map, CanonicalColumn.SECTION, sectionCol);
            putIfPresent(map, CanonicalColumn.LINE, sectionCol + 1);
            putIfPresent(map, CanonicalColumn.SUBLINE, sectionCol + 2);
            putIfPresent(map, CanonicalColumn.DL, 7);
            putIfPresent(map, CanonicalColumn.DLI, 8);
            putIfPresent(map, CanonicalColumn.IDL, 9);
            putIfPresent(map, CanonicalColumn.OUTPUT, 10);
            putIfPresent(map, CanonicalColumn.WT, 11);
            putIfPresent(map, CanonicalColumn.RFT, 12);
            // Article: pick the labelled cell that is NOT inside the time-slot range
            // (xlsx has "Article" both at slot 1 and a dedicated col). Allowance: search by label.
            int article = findHeader(row1, CanonicalColumn.ARTICLE, slotSet);
            if (article >= 0) map.put(CanonicalColumn.ARTICLE, article);
            int allowance = findHeader(row1, CanonicalColumn.ALLOWANCE, slotSet);
            if (allowance >= 0) map.put(CanonicalColumn.ALLOWANCE, allowance);
        } else {
            // Project template: fully header-driven.
            for (CanonicalColumn col : CanonicalColumn.values()) {
                int idx = findHeader(row1, col, slotSet);
                if (idx >= 0) map.put(col, idx);
            }
        }

        // 4. Validate required columns.
        List<CanonicalColumn> missing = new ArrayList<>();
        for (CanonicalColumn col : CanonicalColumn.values()) {
            if (col.isRequired() && !map.containsKey(col)) missing.add(col);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Excel sheet missing required columns: " + missing
                            + " (resolved format: " + fmt + ", slot count: " + slots.size() + ")");
        }

        // Data starts at row 2 if row 2 carries time-slot sub-headers; row 1 otherwise.
        int dataStart = (row2 != null && rowHasTimeSlotLabels(row2)) ? 2 : 1;
        return new HeaderResolver(fmt, map, slots, dataStart);
    }

    /** Lighter version: returns null instead of throwing on missing required cols. */
    public static HeaderResolver tryResolve(Sheet sheet) {
        try {
            return resolve(sheet);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<Integer> detectTimeSlots(Row row1, Row row2) {
        List<Integer> fromRow1 = scanForTimeSlots(row1);
        if (!fromRow1.isEmpty()) return fromRow1;
        return scanForTimeSlots(row2);
    }

    private static List<Integer> scanForTimeSlots(Row row) {
        if (row == null) return List.of();
        List<Integer> all = new ArrayList<>();
        int last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String v = ExcelCellUtil.getString(row.getCell(c));
            if (v != null && TIME_SLOT.matcher(v).matches()) {
                all.add(c);
            }
        }
        if (all.isEmpty()) return List.of();
        // The daily-detail block is a single contiguous run of slots. The factory "D"
        // sheet's helper CT/Quota/MP grids repeat the same hour labels further right,
        // separated by gaps — keep only the longest contiguous run (first on a tie) so
        // those helper columns aren't mistaken for time slots.
        List<Integer> best = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        for (int idx : all) {
            if (cur.isEmpty() || idx == cur.get(cur.size() - 1) + 1) {
                cur.add(idx);
            } else {
                if (cur.size() > best.size()) best = cur;
                cur = new ArrayList<>();
                cur.add(idx);
            }
        }
        if (cur.size() > best.size()) best = cur;
        // Safeguard: never exceed the canonical 15 hourly slots.
        if (best.size() > EntryExcelLayout.TIME_SLOTS.size()) {
            best = new ArrayList<>(best.subList(0, EntryExcelLayout.TIME_SLOTS.size()));
        }
        return best;
    }

    private static boolean rowHasTimeSlotLabels(Row row) {
        return !scanForTimeSlots(row).isEmpty();
    }

    /** Find the first cell in {@code row} whose value matches one of {@code col}'s synonyms,
     *  excluding any column position in {@code skip}. Returns -1 when no match. */
    private static int findHeader(Row row, CanonicalColumn col, Set<Integer> skip) {
        if (row == null) return -1;
        int last = row.getLastCellNum();
        for (int c = 0; c < last; c++) {
            if (skip != null && skip.contains(c)) continue;
            String v = ExcelCellUtil.getString(row.getCell(c));
            if (col.matches(v)) return c;
        }
        return -1;
    }

    private static void putIfPresent(Map<CanonicalColumn, Integer> map, CanonicalColumn col, int idx) {
        map.put(col, idx);
    }

    /** Section codes seen in the factory workbooks' Section column (short forms + canonical). */
    private static final Set<String> SECTION_TOKENS = Set.of(
            "SEW", "ASSY", "ASSEMBLY", "BUFF", "BUFFING", "SF", "STOCKFIT", "CP");

    /**
     * Detect which candidate column holds the Section, by counting how many sampled
     * data rows carry a recognised section code. The first candidate is returned on a
     * tie or when no data rows are present, so header-only sheets keep the historical
     * APR V6 position. {@code skip} excludes time-slot columns from being mistaken.
     */
    private static int detectSectionColumn(Sheet sheet, Set<Integer> skip, int... candidates) {
        Row row2 = sheet.getRow(1);
        int firstData = (row2 != null && rowHasTimeSlotLabels(row2)) ? 2 : 1;
        int lastSample = Math.min(sheet.getLastRowNum(), firstData + 50);

        int bestCol = candidates[0];
        int bestScore = -1;
        for (int cand : candidates) {
            if (skip != null && skip.contains(cand)) continue;
            int score = 0;
            for (int r = firstData; r <= lastSample; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String v = ExcelCellUtil.getString(row.getCell(cand));
                if (v == null) continue;
                String u = v.trim().toUpperCase();
                for (String tok : SECTION_TOKENS) {
                    if (u.startsWith(tok)) { score++; break; }
                }
            }
            if (score > bestScore) { bestScore = score; bestCol = cand; }
        }
        return bestCol;
    }

    /** Test helper: parse a value as a time-slot label. */
    public static boolean isTimeSlotLabel(String v) {
        return v != null && TIME_SLOT.matcher(v).matches();
    }
}
