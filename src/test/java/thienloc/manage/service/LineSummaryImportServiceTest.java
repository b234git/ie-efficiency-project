package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.testutil.TestDataFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineSummaryImportServiceTest {

    private final LineSummaryImportService service = new LineSummaryImportService();

    @Test
    void testParseFile_ASSY_SectionMapping() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 1, 1, 1, 1)));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport ASSY.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals("ASSEMBLY", preview.getSection());
    }

    @Test
    void testParseFile_SEW_SectionMapping() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 0, 0, 0, 0)));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport SEW.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals("SEW", preview.getSection());
    }

    @Test
    void testParseFile_SF_SectionMapping() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 0, 0, 0, 0)));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport SF.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals("STOCKFIT", preview.getSection());
    }

    @Test
    void testParseFile_CaseInsensitiveSection() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 0, 0, 0, 0)));
        MockMultipartFile file = new MockMultipartFile("file", "report_assy_march.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals("ASSEMBLY", preview.getSection());
    }

    @Test
    void testParseFile_UnknownSection_ThrowsException() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", new ArrayList<>());
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport UNKNOWN.xls",
                "application/vnd.ms-excel", bytes);

        assertThrows(IllegalArgumentException.class, () -> service.parseFile(file));
    }

    @Test
    void testParseFile_DateParsing() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("20/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 0, 0, 0, 0)));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport SEW.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals(2026, preview.getDate().getYear());
        assertEquals(3, preview.getDate().getMonthValue());
        assertEquals(20, preview.getDate().getDayOfMonth());
    }

    @Test
    void testParseFile_IDLCalculation() throws Exception {
        // cols 8=supervisor(2), 9=mechanic(3), 10=monitor(1), 11=lineLeader(1) -> IDL=7
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 25.0, 2, 3, 1, 1)));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport SEW.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals(1, preview.getRows().size());
        assertEquals(7.0, preview.getRows().get(0).getIdl(), 0.001);
    }

    @Test
    void testParseFile_ActiveVsInactiveLines() throws Exception {
        byte[] bytes = TestDataFactory.buildLineSummaryExcelBytes("15/03/2026", rows(
                buildLineRow("1A", 10.0, 20.0, 1, 0, 0, 0),  // active (mp > 0)
                buildLineRow("2A", 0.0, 0.0, 0, 0, 0, 1),    // active (idl > 0)
                buildLineRow("3A", 0.0, 0.0, 0, 0, 0, 0)     // inactive (mp=0, idl=0)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "LineSummaryReport SEW.xls",
                "application/vnd.ms-excel", bytes);

        LineSummaryImportService.LineSummaryPreview preview = service.parseFile(file);

        assertEquals(3, preview.getTotalLines());
        assertEquals(1, preview.getSkippedLines());
        assertTrue(preview.getRows().get(0).isActive());
        assertTrue(preview.getRows().get(1).isActive());
        assertFalse(preview.getRows().get(2).isActive());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static List<Object[]> rows(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) {
            list.add(row);
        }
        return list;
    }

    private Object[] buildLineRow(String line, double mp, double total,
                                   double supervisor, double mechanic, double monitor, double lineLeader) {
        Object[] row = new Object[12];
        row[0] = line;
        row[1] = total;
        row[2] = mp;
        row[8] = supervisor;
        row[9] = mechanic;
        row[10] = monitor;
        row[11] = lineLeader;
        return row;
    }
}
