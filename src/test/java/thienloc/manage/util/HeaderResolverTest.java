package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeaderResolverTest {

    /** Build a sheet with row 1 = headers, row 2 = optional sub-headers. */
    private Sheet buildSheet(String[] row1, String[] row2) {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("X");
        Row r1 = sheet.createRow(0);
        for (int i = 0; i < row1.length; i++) {
            if (row1[i] != null) r1.createCell(i).setCellValue(row1[i]);
        }
        if (row2 != null) {
            Row r2 = sheet.createRow(1);
            for (int i = 0; i < row2.length; i++) {
                if (row2[i] != null) r2.createCell(i).setCellValue(row2[i]);
            }
        }
        return sheet;
    }

    @Test
    void resolves_project_template_layout() {
        // Project template: headers all in row 1.
        String[] r1 = new String[28];
        r1[0] = "Date"; r1[1] = "Section"; r1[2] = "Line"; r1[3] = "sub-line";
        r1[4] = "sub-section"; r1[5] = "DL"; r1[6] = "DLI"; r1[7] = "IDL";
        r1[8] = "Output"; r1[9] = "WT"; r1[10] = "RFT";
        for (int i = 11; i <= 25; i++) r1[i] = String.format("%02d:00-%02d:00", 7+i-11, 8+i-11);
        r1[26] = "Article"; r1[27] = "Allowance";

        HeaderResolver h = HeaderResolver.resolve(buildSheet(r1, null));
        assertEquals(HeaderResolver.Format.PROJECT_TEMPLATE, h.getFormat());
        assertEquals(0, h.get(CanonicalColumn.DATE));
        assertEquals(1, h.get(CanonicalColumn.SECTION));
        assertEquals(2, h.get(CanonicalColumn.LINE));
        assertEquals(3, h.get(CanonicalColumn.SUBLINE));
        assertEquals(4, h.get(CanonicalColumn.SUBSECTION));
        assertEquals(5, h.get(CanonicalColumn.DL));
        assertEquals(8, h.get(CanonicalColumn.OUTPUT));
        assertEquals(26, h.get(CanonicalColumn.ARTICLE));
        assertEquals(27, h.get(CanonicalColumn.ALLOWANCE));
        assertEquals(15, h.getTimeSlotColumns().size());
        assertEquals(11, h.getTimeSlotColumns().get(0));
        assertEquals(25, h.getTimeSlotColumns().get(14));
    }

    @Test
    void resolves_factory_layout() {
        // Factory EFF APR V6 layout: REF 1, REF 2, Date, blank, "Line"-labelled-but-Section,
        // blank, blank, DL..RFT, Article (also slot-1), 14 blank, ARTICLE, Allowance.
        String[] r1 = new String[31];
        r1[0] = "REF 1"; r1[1] = "REF 2"; r1[2] = "Date";
        r1[4] = "Line"; // mislabelled — actual Section column
        r1[7] = "DL"; r1[8] = "DLI"; r1[9] = "IDL"; r1[10] = "Output";
        r1[11] = "WT"; r1[12] = "RFT"; r1[13] = "Article";
        r1[28] = "ARTICLE"; r1[29] = "Allowance";

        String[] r2 = new String[31];
        for (int i = 0; i < 15; i++) {
            r2[13 + i] = String.format("%02d:00\n%02d:00", 7+i, 8+i);
        }

        HeaderResolver h = HeaderResolver.resolve(buildSheet(r1, r2));
        assertEquals(HeaderResolver.Format.FACTORY_XLSX, h.getFormat());
        assertEquals(0, h.get(CanonicalColumn.REF1));
        assertEquals(1, h.get(CanonicalColumn.REF2));
        assertEquals(2, h.get(CanonicalColumn.DATE));
        assertEquals(4, h.get(CanonicalColumn.SECTION));
        assertEquals(5, h.get(CanonicalColumn.LINE));
        assertEquals(6, h.get(CanonicalColumn.SUBLINE));
        assertEquals(7, h.get(CanonicalColumn.DL));
        assertEquals(10, h.get(CanonicalColumn.OUTPUT));
        // Article must be the col 28 instance (not col 13, which is also a time slot).
        assertEquals(28, h.get(CanonicalColumn.ARTICLE));
        assertEquals(29, h.get(CanonicalColumn.ALLOWANCE));
        assertEquals(15, h.getTimeSlotColumns().size());
        assertEquals(13, h.getTimeSlotColumns().get(0));
        assertEquals(27, h.getTimeSlotColumns().get(14));
    }

    @Test
    void fails_on_missing_required_column() {
        // Project template missing the Output column -> fail with name in message
        String[] r1 = new String[28];
        r1[0] = "Date"; r1[1] = "Section"; r1[2] = "Line";
        r1[5] = "DL"; r1[6] = "DLI"; r1[7] = "IDL";
        // r1[8] = "Output";  // intentionally missing
        r1[9] = "WT"; r1[10] = "RFT";
        for (int i = 11; i <= 25; i++) r1[i] = String.format("%02d:00-%02d:00", 7+i-11, 8+i-11);
        r1[26] = "Article";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> HeaderResolver.resolve(buildSheet(r1, null)));
        assertTrue(ex.getMessage().contains("OUTPUT"));
    }

    @Test
    void detects_time_slots_in_row2() {
        // Slot labels live in row 2 (factory style).
        String[] r1 = new String[28];
        r1[0] = "REF 1"; r1[2] = "Date"; r1[4] = "Line";
        r1[7] = "DL"; r1[8] = "DLI"; r1[9] = "IDL"; r1[10] = "Output";
        r1[11] = "WT"; r1[12] = "RFT"; r1[13] = "Article";
        r1[26] = "ARTICLE";

        String[] r2 = new String[28];
        r2[13] = "07:00\n08:00";
        r2[14] = "08:00\n09:00";

        HeaderResolver h = HeaderResolver.resolve(buildSheet(r1, r2));
        List<Integer> slots = h.getTimeSlotColumns();
        assertEquals(2, slots.size());
        assertEquals(13, slots.get(0));
        assertEquals(14, slots.get(1));
        assertEquals(2, h.getDataStartRow());
    }

    @Test
    void synonym_match() {
        // Vietnamese label for Article should resolve.
        String[] r1 = new String[28];
        r1[0] = "Date"; r1[1] = "Section"; r1[2] = "Line";
        r1[5] = "DL"; r1[6] = "DLI"; r1[7] = "IDL"; r1[8] = "Output";
        r1[9] = "WT"; r1[10] = "RFT";
        for (int i = 11; i <= 25; i++) r1[i] = String.format("%02d:00-%02d:00", 7+i-11, 8+i-11);
        r1[26] = "Mã hàng";  // Vietnamese for Article

        HeaderResolver h = HeaderResolver.resolve(buildSheet(r1, null));
        assertEquals(26, h.get(CanonicalColumn.ARTICLE));
    }

    @Test
    void time_slot_label_helper_recognises_both_separators() {
        assertTrue(HeaderResolver.isTimeSlotLabel("07:00-08:00"));
        assertTrue(HeaderResolver.isTimeSlotLabel("07:00\n08:00"));
        assertTrue(HeaderResolver.isTimeSlotLabel("  21:00-22:00  "));
        assertFalse(HeaderResolver.isTimeSlotLabel("Article"));
        assertFalse(HeaderResolver.isTimeSlotLabel(null));
    }

    @Test
    void slot_label_returns_canonical_strings() {
        assertEquals("07:00-08:00", HeaderResolver.slotLabel(0));
        assertEquals("21:00-22:00", HeaderResolver.slotLabel(14));
        assertEquals("slot-15", HeaderResolver.slotLabel(15));
    }
}
