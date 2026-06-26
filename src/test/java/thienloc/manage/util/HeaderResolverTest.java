package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("resource") // in-memory XSSFWorkbooks built for assertions; not worth lifecycle plumbing in tests
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
    void resolves_may_v6_layout_detecting_shifted_section_by_content() {
        // MAY V6 shifted the Section/Line/Subline block one column left vs APR V6:
        // Section=3, Line=4, Subline=5 (DL..RFT still at 7-12). The "Line" header still
        // sits at col 4, so the column must be detected by data content, not the label.
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("D");

        Row r1 = sheet.createRow(0);
        r1.createCell(0).setCellValue("REF 1");
        r1.createCell(1).setCellValue("REF 2");
        r1.createCell(2).setCellValue("Date");
        r1.createCell(4).setCellValue("Line");          // label above the Line column now
        r1.createCell(7).setCellValue("DL");
        r1.createCell(8).setCellValue("DLI");
        r1.createCell(9).setCellValue("IDL");
        r1.createCell(10).setCellValue("Output");
        r1.createCell(11).setCellValue("WT");
        r1.createCell(12).setCellValue("RFT");
        r1.createCell(13).setCellValue("Article");
        r1.createCell(28).setCellValue("ARTICLE");

        Row r2 = sheet.createRow(1);
        for (int i = 0; i < 15; i++) {
            r2.createCell(13 + i).setCellValue(String.format("%02d:00\n%02d:00", 7 + i, 8 + i));
        }

        // Data rows: Section codes live in col 3, line numbers in col 4.
        String[] secs = {"ASSY", "SEW", "SF", "BUFF"};
        for (int i = 0; i < secs.length; i++) {
            Row d = sheet.createRow(2 + i);
            d.createCell(3).setCellValue(secs[i]);
            d.createCell(4).setCellValue(5);
        }

        HeaderResolver h = HeaderResolver.resolve(sheet);
        assertEquals(HeaderResolver.Format.FACTORY_XLSX, h.getFormat());
        assertEquals(3, h.get(CanonicalColumn.SECTION));
        assertEquals(4, h.get(CanonicalColumn.LINE));
        assertEquals(5, h.get(CanonicalColumn.SUBLINE));
        assertEquals(7, h.get(CanonicalColumn.DL));
        assertEquals(28, h.get(CanonicalColumn.ARTICLE));
    }

    @Test
    void keeps_only_first_contiguous_slot_run_ignoring_helper_grids() {
        // MAY V6 "D" sheet repeats hour labels in helper CT/Quota/MP grids further right,
        // separated by gaps. Only the real 15-slot run (13..27) must be kept.
        String[] r1 = new String[60];
        r1[0] = "REF 1"; r1[1] = "REF 2"; r1[2] = "Date"; r1[4] = "Line";
        r1[7] = "DL"; r1[8] = "DLI"; r1[9] = "IDL"; r1[10] = "Output";
        r1[11] = "WT"; r1[12] = "RFT"; r1[13] = "Article";
        r1[28] = "ARTICLE";

        String[] r2 = new String[60];
        for (int i = 0; i < 15; i++) r2[13 + i] = String.format("%02d:00\n%02d:00", 7 + i, 8 + i);
        // Helper grid: a second run of hour labels at 35..49 (gap at 28..34 in between).
        for (int i = 0; i < 15; i++) r2[35 + i] = String.format("%02d:00\n%02d:00", 7 + i, 8 + i);

        HeaderResolver h = HeaderResolver.resolve(buildSheet(r1, r2));
        List<Integer> slots = h.getTimeSlotColumns();
        assertEquals(15, slots.size(), () -> "should keep only the real run, got " + slots);
        assertEquals(13, slots.get(0));
        assertEquals(27, slots.get(14));
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
