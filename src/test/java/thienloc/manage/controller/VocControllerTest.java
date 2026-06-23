package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReconcileRowDto;
import thienloc.manage.dto.VocReconcileWeekDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocReportFilter;
import thienloc.manage.dto.VocSubconReportDto;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.security.TestRbacSecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.VocService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Render checks for the three reworked VOC pages: MockMvc fully renders the
 * Thymeleaf views, so a broken {@code th:*} expression (e.g. the new ranking
 * panel, the R reference columns, or the SUBCON pivot) fails the test.
 */
@WebMvcTest(VocController.class)
@Import({SecurityConfig.class, TestRbacSecurityConfig.class})
class VocControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocService vocService;

    @MockitoBean
    private thienloc.manage.service.VocExcelExportService vocExcelExportService;

    @MockitoBean
    private NotificationService notificationService;

    /** §1: Report (%) renders week blocks (band + ranking chips), date rows,
     *  weekly Total, grand Total, and the filter bar — full Thymeleaf render. */
    @Test
    void reportPageRendersWeekBlocksAndFilters() throws Exception {
        VocReportDto report = new VocReportDto();
        report.setSelectedMonth("2026-04");
        report.setAllMonths(List.of("2026-04"));
        report.setReconcileChemicals(List.of("577NT3"));
        report.setFilter(VocReportFilter.ofMonth("2026-04"));
        report.setAllLines(List.of("1A"));
        report.setAllSections(List.of("SEW"));
        report.setAllChemCodes(List.of("577NT3"));
        report.getWeekOptions().put(1, "01/04–07/04");

        VocReconcileCellDto cell = new VocReconcileCellDto();
        cell.setActualKg(7.9);
        cell.setAllowanceKg(9.5125);
        cell.setDiffKg(1.6125);
        cell.setRatio(0.8305);
        cell.setStatus("OK");
        VocReconcileRowDto row = new VocReconcileRowDto();
        row.setDate(LocalDate.of(2026, 4, 1));
        row.setOutput(700);
        row.setVocGrams(1234.0);
        row.getCells().put("577NT3", cell);

        VocReconcileRowDto totalRow = new VocReconcileRowDto();
        totalRow.setOutput(700);
        totalRow.setVocGrams(1234.0);
        totalRow.getCells().put("577NT3", cell);

        VocReconcileWeekDto week = new VocReconcileWeekDto();
        week.setLabel("01/04–07/04");
        week.setRows(List.of(row));
        week.setTotalRow(totalRow);
        week.getHigh().add(new VocReportDto.ChemRank("98NH1", 1.2));
        week.getLow().add(new VocReportDto.ChemRank("311A5", 0.5));
        report.setReconcileWeeks(List.of(week));
        report.setReconcileTotal(totalRow);

        // EFF per-line tab data (exercises the lcells fragment + ranking band)
        VocReconcileRowDto lineRow = new VocReconcileRowDto();
        lineRow.setLine("1A");
        lineRow.getCells().put("577NT3", cell);
        report.setByLineRows(List.of(lineRow));
        report.setByLineTotal(totalRow);
        report.getByLineHigh().add(new VocReportDto.ChemRank("98NH1", 1.2));
        report.getByLineLow().add(new VocReportDto.ChemRank("311A5", 0.5));

        when(vocService.getMonthlyReport(any())).thenReturn(report);

        mockMvc.perform(get("/voc/").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("voc"))
                .andExpect(model().attributeExists("report"));

        // filter params bind (date range, week, section, line, repeated chems)
        mockMvc.perform(get("/voc/")
                        .param("month", "2026-04")
                        .param("from", "2026-04-01").param("to", "2026-04-07")
                        .param("week", "1")
                        .param("section", "SEW").param("line", "1A")
                        .param("chems", "577NT3").param("chems", "311A5")
                        .with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("voc"));
    }

    /** §2: Chemicals (R) renders the four new reference columns. */
    @Test
    void chemicalsPageRendersReferenceColumns() throws Exception {
        VocChemical c = VocChemical.builder()
                .code("577NT").materialType("SOLVENT").classification("Adhesive").manufacturer("Greco")
                .vocFactor(0.745).pricePerKg(3.47)
                .unit("1").containerSizeKg(15.0).containerPrice(52.05).priceRefNote("2022-05-23")
                .active(true).build();
        when(vocService.getAllChemicals()).thenReturn(List.of(c));

        mockMvc.perform(get("/voc/chemicals").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("voc-chemicals"))
                .andExpect(model().attributeExists("chemicals"));
    }

    /** §3: SUBCON page renders the standard/actual/shortage pivot. */
    @Test
    void subconPageRenders() throws Exception {
        VocSubconReportDto report = new VocSubconReportDto();
        report.setSelectedMonth("2026-04");
        report.setAllMonths(List.of("2026-04"));
        report.setChemicals(List.of("577NT3"));

        VocReconcileCellDto cell = new VocReconcileCellDto();
        cell.setActualKg(8.0);
        cell.setAllowanceKg(10.0);
        cell.setDiffKg(2.0);
        cell.setRatio(0.8);
        cell.setStatus("OK");
        VocSubconReportDto.Row row = VocSubconReportDto.Row.builder()
                .id(1L).date(LocalDate.of(2026, 4, 1)).subcontractor("TH2").articleNo("399028")
                .output(2768).totalStandardKg(10.0).totalActualKg(8.0).totalShortageKg(2.0).vocKg(5.96).build();
        row.getCells().put("577NT3", cell);
        report.setRows(List.of(row));

        when(vocService.getSubconReport(any())).thenReturn(report);
        when(vocService.getSubcontractors()).thenReturn(List.of("TH2"));
        when(vocService.getActiveChemicals()).thenReturn(List.of(
                VocChemical.builder().code("577NT3").materialType("SOLVENT").vocFactor(0.745).active(true).build()));

        mockMvc.perform(get("/voc/subcon").with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("voc-subcon"))
                .andExpect(model().attributeExists("report", "subcontractors", "chemicals"));
    }

    /** §6: the Entry page renders the batch grid (one row per active chemical). */
    @Test
    void entryPageRendersBatchGrid() throws Exception {
        when(vocService.getSections()).thenReturn(List.of("SEW"));
        when(vocService.getLines()).thenReturn(List.of("1A"));
        when(vocService.getActiveChemicals()).thenReturn(List.of(
                VocChemical.builder().code("577NT3").materialType("SOLVENT").vocFactor(0.745).active(true).build()));
        when(vocService.getActualRows(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/voc/entry").param("date", "2026-04-01").param("section", "SEW").param("line", "1A")
                        .with(user("u").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("voc-entry"))
                .andExpect(model().attributeExists("chemicals", "rows"));
    }

    /** §6: batch consumption save delegates to the service and redirects to Entry. */
    @Test
    void saveBatchEntryRedirects() throws Exception {
        when(vocService.saveConsumptionBatch(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new VocService.BatchResult(2, List.of()));

        mockMvc.perform(post("/voc/entry/save-batch").with(csrf()).with(user("u").roles("ADMIN"))
                        .param("date", "2026-04-01").param("section", "SEW").param("line", "1A")
                        .param("chemicalCode", "577NT3").param("chemicalCode", "GH-7055")
                        .param("quantityKg", "0.9").param("quantityKg", "")
                        .param("reuseKg", "0").param("reuseKg", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/voc/entry*"));

        verify(vocService).saveConsumptionBatch(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    /** §6: batch subcon save delegates to the service and redirects to Subcon. */
    @Test
    void saveBatchSubconRedirects() throws Exception {
        when(vocService.saveSubconBatch(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new VocService.BatchResult(1, List.of()));

        mockMvc.perform(post("/voc/subcon/save-batch").with(csrf()).with(user("u").roles("ADMIN"))
                        .param("date", "2026-04-01").param("subcontractor", "1A").param("articleNo", "ART1")
                        .param("output", "600")
                        .param("chemicalCode", "577NT3").param("actualKg", "8.0").param("reuseKg", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/voc/subcon*"));

        verify(vocService).saveSubconBatch(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }
}
