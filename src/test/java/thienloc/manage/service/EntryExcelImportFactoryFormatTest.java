package thienloc.manage.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.repository.DailyProductionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link EntryExcelImportService} can parse the factory's
 * "EFF APR V6" workbook layout (sheet "D", REF columns, mislabelled section
 * header, time slots starting at col 13, ARTICLE at col 28).
 */
class EntryExcelImportFactoryFormatTest {

    private EntryExcelImportService service;

    @BeforeEach
    void setUp() {
        // parseForPreview touches MeterRegistry but not the repository or user service.
        service = new EntryExcelImportService(
                mock(DailyProductionRepository.class),
                new SimpleMeterRegistry(),
                mock(UserService.class));
    }

    private MockMultipartFile loadSample() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/excel/eff-apr-v6-sample.xlsx")) {
            assertNotNull(in, "Test fixture /excel/eff-apr-v6-sample.xlsx not found");
            return new MockMultipartFile("file", "eff-apr-v6-sample.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    in.readAllBytes());
        }
    }

    @Test
    void imports_eff_apr_v6_sample_successfully() throws Exception {
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());

        assertEquals(5, preview.getTotalRows());
        assertEquals(5, preview.getValidRows(), () -> "Errors: " + preview.getRows());
        assertEquals(0, preview.getErrorRows());
    }

    @Test
    void preserves_dual_line_minus_2_suffix_in_articles() throws Exception {
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());

        // Find the row with article 309354-2 (SF5 parallel-line variant).
        EntryImportPreviewDto.RowPreview parallel = preview.getRows().stream()
                .filter(r -> "309354-2".equals(r.getMainArticle()))
                .findFirst().orElseThrow(() ->
                        new AssertionError("No -2 article row found in preview"));

        assertEquals("STOCKFIT 1ST", parallel.getSection(),
                "Bare 'SF' section should normalize to STOCKFIT 1ST; -2 suffix on the article triggers 2ND lookup downstream.");
        assertEquals("5", parallel.getLine());
        // Every slot should carry the -2 suffix.
        Map<String, String> articles = parallel.getArticles();
        assertFalse(articles.isEmpty());
        for (Map.Entry<String, String> e : articles.entrySet()) {
            assertEquals("309354-2", e.getValue(),
                    "Slot " + e.getKey() + " should keep the -2 suffix; was " + e.getValue());
        }
    }

    @Test
    void detects_uv_line_promoting_section_to_stockfit_uv() throws Exception {
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());

        EntryImportPreviewDto.RowPreview uv = preview.getRows().stream()
                .filter(r -> "UV".equals(r.getLine()))
                .findFirst().orElseThrow(() ->
                        new AssertionError("No UV row found in preview"));

        assertEquals("STOCKFIT UV", uv.getSection());
    }

    @Test
    void resolves_buff_section_from_short_form() throws Exception {
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());

        EntryImportPreviewDto.RowPreview buff = preview.getRows().stream()
                .filter(r -> r.getSection() != null && r.getSection().startsWith("BUFFING"))
                .findFirst().orElseThrow(() ->
                        new AssertionError("No BUFF row found in preview"));

        assertEquals("BUFFING 1ST", buff.getSection());
        assertEquals("5A", buff.getLine());
    }

    @Test
    void minus_2_suffix_routes_to_buff_2nd_via_resolve_slot() {
        SectionMetrics.ResolvedSection resolved =
                SectionMetrics.resolveSlot("BUFFING 1ST", "407272-2");
        assertEquals(SectionMetrics.BUFF_2ND, resolved.primary(),
                "-2 suffix should route the slot lookup to BUFFING 2ND.");
    }

    @Test
    void minus_2_suffix_routes_to_stockfit_2nd_via_resolve_slot() {
        SectionMetrics.ResolvedSection resolved =
                SectionMetrics.resolveSlot("STOCKFIT 1ST", "309354-2");
        assertEquals(SectionMetrics.STOCKFIT_2ND, resolved.primary());
    }

    /**
     * Regression for the MAY V6 "D" sheet, whose Section/Line/Subline block is shifted
     * one column left vs APR V6 (Section=3 instead of 4). Before the content-based column
     * detection, ASSEMBLY line-5 rows (no sub-line) were read with an empty Line and
     * silently SKIPPED ("Missing: Line"), and line-6 rows got a garbage section ("6").
     */
    @Test
    void may_v6_shifted_layout_imports_assembly_line5_as_small_not_skipped() throws Exception {
        MockMultipartFile file = buildMayV6Workbook();
        EntryImportPreviewDto preview = service.parseForPreview(file);

        EntryImportPreviewDto.RowPreview line5 = preview.getRows().stream()
                .filter(r -> "5".equals(r.getLine()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "ASSEMBLY line-5 row was skipped/misread: " + preview.getRows()));
        assertTrue(line5.isValid(), () -> "line-5 row invalid: " + line5.getErrorMessage());
        assertEquals("ASSEMBLY SMALL", line5.getSection());

        EntryImportPreviewDto.RowPreview line6 = preview.getRows().stream()
                .filter(r -> "6A".equals(r.getLine()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "ASSEMBLY line-6A row missing: " + preview.getRows()));
        assertEquals("ASSEMBLY BIG", line6.getSection());

        // Slot over-capture guard: the helper hour-label grids at col 35.. must not be
        // read as extra slots (fallback fills exactly the 15 real slots, not ~30).
        assertEquals(15, line5.getArticleCount(),
                () -> "expected 15 real slots, helper grids leaked: " + line5.getArticleCount());
    }

    /** Build a minimal MAY V6 "D" sheet: Section=3, Line=4, Subline=5, DL..RFT=7-12,
     *  time slots in row 2 at 13.., ARTICLE at 28. */
    private MockMultipartFile buildMayV6Workbook() throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("D");

            org.apache.poi.ss.usermodel.Row r1 = sheet.createRow(0);
            r1.createCell(0).setCellValue("REF 1");
            r1.createCell(1).setCellValue("REF 2");
            r1.createCell(2).setCellValue("Date");
            r1.createCell(4).setCellValue("Line");
            r1.createCell(7).setCellValue("DL");
            r1.createCell(8).setCellValue("DLI");
            r1.createCell(9).setCellValue("IDL");
            r1.createCell(10).setCellValue("Output");
            r1.createCell(11).setCellValue("WT");
            r1.createCell(12).setCellValue("RFT");
            r1.createCell(13).setCellValue("Article");
            r1.createCell(28).setCellValue("ARTICLE");

            org.apache.poi.ss.usermodel.Row r2 = sheet.createRow(1);
            for (int i = 0; i < 15; i++) {
                r2.createCell(13 + i).setCellValue(String.format("%02d:00\n%02d:00", 7 + i, 8 + i));
            }
            // Helper CT/Quota/MP grids repeat the hour labels at 35.. (gap-separated) —
            // the importer must NOT treat these as time slots.
            for (int i = 0; i < 15; i++) {
                r2.createCell(35 + i).setCellValue(String.format("%02d:00\n%02d:00", 7 + i, 8 + i));
            }

            // ASSY line 5 (no sub-line) — the previously-skipped case.
            org.apache.poi.ss.usermodel.Row d5 = sheet.createRow(2);
            d5.createCell(0).setCellValue("401287ASSY5");
            d5.createCell(1).setCellValue("ASSY5");
            d5.createCell(2).setCellValue("2026-05-04");
            d5.createCell(3).setCellValue("ASSY");
            d5.createCell(4).setCellValue(5);
            d5.createCell(7).setCellValue(21);
            d5.createCell(10).setCellValue(652);
            d5.createCell(11).setCellValue(10.9);
            d5.createCell(28).setCellValue("401287");

            // ASSY line 6 with sub-line A — previously got garbage section.
            org.apache.poi.ss.usermodel.Row d6 = sheet.createRow(3);
            d6.createCell(0).setCellValue("399028ASSY6A");
            d6.createCell(1).setCellValue("ASSY6A");
            d6.createCell(2).setCellValue("2026-05-04");
            d6.createCell(3).setCellValue("ASSY");
            d6.createCell(4).setCellValue(6);
            d6.createCell(5).setCellValue("A");
            d6.createCell(7).setCellValue(25);
            d6.createCell(10).setCellValue(800);
            d6.createCell(11).setCellValue(10.8);
            d6.createCell(28).setCellValue("399028");

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "may-v6.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void factory_workbook_has_correct_sheet_count_and_chooses_D() throws Exception {
        // The fixture has ['6S', 'DB', 'D']; SheetDetector must pick 'D' (daily-detail signature)
        // and NOT 'DB' (which is article-master-shaped).
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());
        // If the wrong sheet had been picked, totalRows would be 0 or < 5.
        assertEquals(5, preview.getTotalRows());
    }
}
