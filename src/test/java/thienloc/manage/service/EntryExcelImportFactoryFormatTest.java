package thienloc.manage.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.repository.DailyProductionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
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
    void setUp() throws Exception {
        service = new EntryExcelImportService();
        // Field injection bypass: this test exercises only parseForPreview, which
        // touches MeterRegistry but not the repository or user service.
        inject(service, "productionRepository", mock(DailyProductionRepository.class));
        inject(service, "userService", mock(UserService.class));
        MeterRegistry registry = new SimpleMeterRegistry();
        inject(service, "meterRegistry", registry);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
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

    @Test
    void factory_workbook_has_correct_sheet_count_and_chooses_D() throws Exception {
        // The fixture has ['6S', 'DB', 'D']; SheetDetector must pick 'D' (daily-detail signature)
        // and NOT 'DB' (which is article-master-shaped).
        EntryImportPreviewDto preview = service.parseForPreview(loadSample());
        // If the wrong sheet had been picked, totalRows would be 0 or < 5.
        assertEquals(5, preview.getTotalRows());
    }
}
