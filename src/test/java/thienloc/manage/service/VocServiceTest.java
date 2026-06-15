package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocReportFilter;
import thienloc.manage.dto.VocSubconReportDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.entity.VocSubconDetail;
import thienloc.manage.entity.VocSubconEntry;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocRecipeArticleRepository;
import thienloc.manage.repository.VocStandardRateRepository;
import thienloc.manage.repository.VocSubconEntryRepository;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * VOC formula/behaviour locks for the three reworks:
 *   §1 per-slot allowance + High/Low ranking (the "%" sheet),
 *   §3 SUBCON standard/shortage,
 *   §2 chemical-master "R" import column mapping.
 * Repos are mocked so the assertions pin the calculation, not the persistence.
 */
@ExtendWith(MockitoExtension.class)
class VocServiceTest {

    @Mock private VocChemicalRepository chemicalRepo;
    @Mock private VocConsumptionRepository consumptionRepo;
    @Mock private VocStandardRateRepository rateRepo;
    @Mock private VocRecipeArticleRepository recipeArticleRepo;
    @Mock private VocSubconEntryRepository subconRepo;
    @Mock private DailyProductionRepository productionRepo;
    @Mock private SystemLogService systemLogService;

    @InjectMocks private VocService vocService;

    /** §1: allowance is weighted by each slot's article × slot output (not first-article ×
     *  whole-day output), and the High/Low ranking flags ratio ≥1.1 / ≤0.9. */
    @Test
    void perSlotAllowanceAndConsumptionRanking() {
        LocalDate d = LocalDate.of(2026, 4, 1);

        DailyProduction p = new DailyProduction();
        p.setProductionDate(d);
        p.setSection("SEW");
        p.setLine("1A");
        p.setTotalOutput(300);
        p.setDetails(List.of(
                DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART1").output(100).build(),
                DailyProductionDetail.builder().timeSlot("09:00").articleNo("ART2").output(200).build()));

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        // lower-case entry code must merge into the master's "HCA" column
                        // (Excel SUMIFS is case-insensitive)
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("hca").quantityKg(6.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("HCB").quantityKg(1.5).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build(),
                VocChemical.builder().code("HCB").materialType("SOLVENT").vocFactor(0.5).build()));
        when(productionRepo.sumOutputByDateSectionLine(any(), any())).thenReturn(List.of());
        when(productionRepo.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(any(), any()))
                .thenReturn(List.of(p));
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCA").kgPerPair(0.01).build(),
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCB").kgPerPair(0.02).build(),
                VocStandardRate.builder().articleNo("ART2").chemicalCode("HCA").kgPerPair(0.02).build(),
                VocStandardRate.builder().articleNo("ART2").chemicalCode("HCB").kgPerPair(0.005).build()));

        VocReportDto report = vocService.getMonthlyReport(VocReportFilter.ofMonth("2026-04"));

        var week = report.getReconcileWeeks().get(0);
        var row = week.getRows().get(0);
        // per-slot: HCA = 100*0.01 + 200*0.02 = 5.0 ; HCB = 100*0.02 + 200*0.005 = 3.0
        assertEquals(5.0, row.getCells().get("HCA").getAllowanceKg(), 1e-9);
        assertEquals(3.0, row.getCells().get("HCB").getAllowanceKg(), 1e-9);
        // ratio = actual/allowance ; diff = allowance - actual  (the "%" sheet formulas)
        assertEquals(6.0 / 5.0, row.getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(5.0 - 6.0, row.getCells().get("HCA").getDiffKg(), 1e-9);
        // weekly ranking (%!AC/AF): HCA 1.2 ≥ 1.1 → HIGH ; HCB 0.5 ≤ 0.9 → LOW
        assertEquals(1, week.getHigh().size());
        assertEquals("HCA", week.getHigh().get(0).code());
        assertEquals(1, week.getLow().size());
        assertEquals("HCB", week.getLow().get(0).code());
    }

    /** Week blocks (days 1–7, 8–14, …): per-week Total = ratio of SUMS (not average of
     *  ratios), grand Total over the period, NC carried onto totals, VOC(g)/output summed. */
    @Test
    void weeklyBlocksTotalsAndGrandTotal() {
        LocalDate d1 = LocalDate.of(2026, 4, 5);   // week 1
        LocalDate d2 = LocalDate.of(2026, 4, 9);   // week 2

        DailyProduction p1 = new DailyProduction();
        p1.setProductionDate(d1); p1.setSection("SEW"); p1.setLine("1A"); p1.setTotalOutput(100);
        p1.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART1").output(100).build()));
        DailyProduction p2 = new DailyProduction();
        p2.setProductionDate(d2); p2.setSection("SEW"); p2.setLine("1A"); p2.setTotalOutput(80);
        p2.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART2").output(80).build()));

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d1).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(4.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d2).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(9.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build()));
        when(productionRepo.sumOutputByDateSectionLine(any(), any())).thenReturn(List.of());
        when(productionRepo.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(any(), any()))
                .thenReturn(List.of(p1, p2));
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCA").kgPerPair(0.05).build(),
                VocStandardRate.builder().articleNo("ART2").chemicalCode("HCA").kgPerPair(0.05).build(),
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCC").kgPerPair(0.01).build()));

        VocReportDto report = vocService.getMonthlyReport(VocReportFilter.ofMonth("2026-04"));

        assertEquals(2, report.getReconcileWeeks().size());
        var w1 = report.getReconcileWeeks().get(0);
        var w2 = report.getReconcileWeeks().get(1);
        assertEquals("05/04", w1.getLabel());
        assertEquals("09/04", w2.getLabel());

        // week 1: HCA act 4 / allow 5 → 0.8 LOW ; HCC allowance-only → NC (also on the Total)
        assertEquals(0.8, w1.getTotalRow().getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(1.0, w1.getTotalRow().getCells().get("HCA").getDiffKg(), 1e-9);
        assertEquals("NC", w1.getTotalRow().getCells().get("HCC").getStatus());
        assertNull(w1.getTotalRow().getCells().get("HCC").getRatio());
        assertEquals(List.of("HCA"), w1.getLow().stream().map(VocReportDto.ChemRank::code).toList());
        assertTrue(w1.getHigh().isEmpty());

        // week 2: HCA act 9 / allow 4 → 2.25 HIGH
        assertEquals(2.25, w2.getTotalRow().getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(List.of("HCA"), w2.getHigh().stream().map(VocReportDto.ChemRank::code).toList());

        // grand Total = Σact/Σallow = 13/9 (NOT the average of 0.8 and 2.25)
        var grand = report.getReconcileTotal();
        assertEquals(13.0 / 9.0, grand.getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(9.0 - 13.0, grand.getCells().get("HCA").getDiffKg(), 1e-9);
        assertEquals("NC", grand.getCells().get("HCC").getStatus());
        assertEquals(180, grand.getOutput());
        assertEquals(6500.0, grand.getVocGrams(), 1e-9);   // (4 + 9) × 0.5 × 1000

        // week dropdown options cover the blocks that have data, calendar-bounded labels
        assertEquals(Set.of(1, 2), report.getWeekOptions().keySet());
        assertEquals("01/04–07/04", report.getWeekOptions().get(1));
        assertEquals("08/04–14/04", report.getWeekOptions().get(2));
    }

    /** Filters narrow the data BEFORE aggregation (like %!$A$1), while the dropdown
     *  option lists keep coming from the full month. */
    @Test
    void filtersNarrowDataBeforeAggregation() {
        LocalDate d = LocalDate.of(2026, 4, 2);   // week 1

        DailyProduction p1 = new DailyProduction();
        p1.setProductionDate(d); p1.setSection("SEW"); p1.setLine("1A"); p1.setTotalOutput(100);
        p1.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART1").output(100).build()));
        DailyProduction p2 = new DailyProduction();
        p2.setProductionDate(d); p2.setSection("ASSEMBLY"); p2.setLine("2B"); p2.setTotalOutput(50);
        p2.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART2").output(50).build()));

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(6.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d).section("ASSEMBLY").line("2B")
                                .chemicalCode("HCB").quantityKg(2.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build(),
                VocChemical.builder().code("HCB").materialType("SOLVENT").vocFactor(1.0).build()));
        when(productionRepo.sumOutputByDateSectionLine(any(), any())).thenReturn(List.of());
        when(productionRepo.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(any(), any()))
                .thenReturn(List.of(p1, p2));
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCA").kgPerPair(0.01).build(),
                VocStandardRate.builder().articleNo("ART2").chemicalCode("HCB").kgPerPair(0.02).build()));

        // line=1A (the $A$1 case): only 1A's actual + allowance + output remain
        VocReportDto byLine = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, null, null, "1A", null));
        assertEquals(List.of("HCA"), byLine.getReconcileChemicals());
        assertEquals(3.0, byLine.getTotalVocKg(), 1e-9);          // 6 × 0.5, HCB gone
        assertEquals(100, byLine.getReconcileTotal().getOutput()); // p2's 50 excluded
        // option lists still cover the whole month
        assertEquals(List.of("1A", "2B"), byLine.getAllLines());
        assertEquals(List.of("ASSEMBLY", "SEW"), byLine.getAllSections());
        assertEquals(List.of("HCA", "HCB"), byLine.getAllChemCodes());

        // section=ASSEMBLY: the mirror slice
        VocReportDto bySection = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, null, "ASSEMBLY", null, null));
        assertEquals(List.of("HCB"), bySection.getReconcileChemicals());
        assertEquals(2.0, bySection.getTotalVocKg(), 1e-9);

        // chems=[HCB]: actual AND allowance restricted to the chosen chemicals
        VocReportDto byChem = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, null, null, null, List.of("HCB")));
        assertEquals(List.of("HCB"), byChem.getReconcileChemicals());
        assertEquals(2.0, byChem.getTotalVocKg(), 1e-9);
        assertEquals(1.0, byChem.getReconcileTotal().getCells().get("HCB").getAllowanceKg(), 1e-9);

        // week=2: Apr 2 falls outside days 8–14 → empty slice, options intact
        VocReportDto byWeek = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, 2, null, null, null));
        assertTrue(byWeek.getReconcileWeeks().isEmpty());
        assertEquals(List.of("1A", "2B"), byWeek.getAllLines());

        // from/to inside the month: Apr 3.. excludes the Apr 2 data
        VocReportDto byRange = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", LocalDate.of(2026, 4, 3), null, null, null, null, null));
        assertTrue(byRange.getReconcileWeeks().isEmpty());
    }

    /** §3: SUBCON standard = output × recipe; shortage = standard − actual; netVOC = (actual−reuse)×factor. */
    @Test
    void subconStandardAndShortage() {
        LocalDate d = LocalDate.of(2026, 4, 1);
        VocSubconEntry e = VocSubconEntry.builder()
                .id(7L).productionDate(d).subcontractor("TH2").articleNo("ART1").output(1000)
                .details(List.of(VocSubconDetail.builder().chemicalCode("HCA").actualKg(8.0).reuseKg(0.0).build()))
                .build();

        when(subconRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(subconRepo.findWithDetailsBetween(any(), any())).thenReturn(List.of(e));
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCA").kgPerPair(0.01).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.745).build()));

        VocSubconReportDto report = vocService.getSubconReport("2026-04");

        var row = report.getRows().get(0);
        assertEquals(10.0, row.getTotalStandardKg(), 1e-9);   // 1000 * 0.01
        assertEquals(8.0, row.getTotalActualKg(), 1e-9);
        assertEquals(2.0, row.getTotalShortageKg(), 1e-9);    // standard − actual
        assertEquals(2.0, row.getCells().get("HCA").getDiffKg(), 1e-9);
        assertEquals(0.8, row.getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(8.0 * 0.745, row.getVocKg(), 1e-9);
    }

    /** §2: importing the full "R" sheet reads price from $/KG (col 9), not UNIT (col 5). */
    @Test
    void importChemicalsReadsPriceFromDollarPerKgColumn() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("R");
            Row h = s.createRow(0);
            String[] headers = {"Code", "Material Type", "Classification", "Manufacturer", "VOC",
                    "UNIT", "kg", "Container", "Price", "$ / KG", "Date"};
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("Latex");
            r.createCell(1).setCellValue("Water Base");
            r.createCell(2).setCellValue("Adhesive");
            r.createCell(3).setCellValue("Dinh Thinh");
            r.createCell(4).setCellValue(0);          // VOC
            r.createCell(5).setCellValue(1);          // UNIT
            r.createCell(6).setCellValue(25);         // kg (container size)
            r.createCell(7).setCellValue(25);         // Container
            r.createCell(8).setCellValue(34.59);      // Price (per container)
            r.createCell(9).setCellValue(1.3837);     // $/KG
            r.createCell(10).setCellValue("2022-10-05");
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        when(chemicalRepo.findByCodeIgnoreCase("Latex")).thenReturn(Optional.empty());
        ArgumentCaptor<VocChemical> captor = ArgumentCaptor.forClass(VocChemical.class);
        when(chemicalRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importChemicalsFromExcel(new MockMultipartFile("file", "R.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        VocChemical saved = captor.getValue();
        assertEquals(1.3837, saved.getPricePerKg(), 1e-9);   // $/KG col 9, NOT UNIT col 5
        assertEquals("1", saved.getUnit());
        assertEquals(25.0, saved.getContainerSizeKg(), 1e-9);
        assertEquals(34.59, saved.getContainerPrice(), 1e-9);
        assertEquals("2022-10-05", saved.getPriceRefNote());
        assertEquals("WATER", saved.getMaterialType());
    }

    /** §1b: the EFF per-line pivot re-groups the same actual/allowance by line, with a
     *  Total row and High/Low ranking over that total (EFF sheet cols Y/Z). */
    @Test
    void perLinePivotGroupsByLine() {
        LocalDate d = LocalDate.of(2026, 4, 1);
        DailyProduction a = new DailyProduction();
        a.setProductionDate(d); a.setSection("SEW"); a.setLine("1A"); a.setTotalOutput(100);
        a.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART1").output(100).build()));
        DailyProduction b = new DailyProduction();
        b.setProductionDate(d); b.setSection("SEW"); b.setLine("2A"); b.setTotalOutput(100);
        b.setDetails(List.of(DailyProductionDetail.builder().timeSlot("08:00").articleNo("ART1").output(100).build()));

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(2.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d).section("SEW").line("2A")
                                .chemicalCode("HCA").quantityKg(1.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build()));
        when(productionRepo.sumOutputByDateSectionLine(any(), any())).thenReturn(List.of());
        when(productionRepo.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(any(), any()))
                .thenReturn(List.of(a, b));
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().articleNo("ART1").chemicalCode("HCA").kgPerPair(0.01).build()));

        VocReportDto report = vocService.getMonthlyReport(VocReportFilter.ofMonth("2026-04"));

        assertEquals(2, report.getByLineRows().size());
        var r1a = report.getByLineRows().stream().filter(r -> "1A".equals(r.getLine())).findFirst().orElseThrow();
        var r2a = report.getByLineRows().stream().filter(r -> "2A".equals(r.getLine())).findFirst().orElseThrow();
        assertEquals(1.0, r1a.getCells().get("HCA").getAllowanceKg(), 1e-9);   // 100 * 0.01
        assertEquals(2.0, r1a.getCells().get("HCA").getRatio(), 1e-9);         // 2.0 / 1.0
        assertEquals(1.0, r2a.getCells().get("HCA").getRatio(), 1e-9);         // 1.0 / 1.0
        // Total ratio = (2.0 + 1.0) / (1.0 + 1.0) = 1.5 → HIGH
        assertEquals(1.5, report.getByLineTotal().getCells().get("HCA").getRatio(), 1e-9);
        assertEquals(1, report.getByLineHigh().size());
        assertEquals("HCA", report.getByLineHigh().get(0).code());
    }

    /** §3b: the wide "ACTUAL CEMENT" importer reads the right (Actual) block, maps the
     *  line into subcontractor, aliases 705AN→GH-705AN, and skips the BA shortage column. */
    @Test
    void importSubconWideReadsActualBlock() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("ACTUAL CEMENT");
            s.createRow(0).createCell(1).setCellValue("2026-04-01");   // date at headerRow-3, col B
            Row hdr = s.createRow(3);                                  // block header (col B & AB == "LINE")
            hdr.createCell(1).setCellValue("LINE");
            hdr.createCell(27).setCellValue("LINE");
            hdr.createCell(28).setCellValue("ARTICLE #");
            hdr.createCell(29).setCellValue("OUTPUT");
            hdr.createCell(30).setCellValue("HCA");
            hdr.createCell(31).setCellValue("705AN");                  // alias → GH-705AN
            Row data = s.createRow(4);                                 // right (Actual) block
            data.createCell(27).setCellValue("1A");
            data.createCell(28).setCellValue("ART1");
            data.createCell(29).setCellValue(600);
            data.createCell(30).setCellValue(8.0);
            data.createCell(31).setCellValue(3.8);
            data.createCell(52).setCellValue(50);                      // BA shortage — must be ignored
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        when(subconRepo.findByProductionDateAndSubcontractorAndArticleNo(any(), any(), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<VocSubconEntry> captor = ArgumentCaptor.forClass(VocSubconEntry.class);
        when(subconRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importSubconFromExcel(new MockMultipartFile("file", "ACTUAL CEMENT.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        VocSubconEntry e = captor.getValue();
        assertEquals(LocalDate.of(2026, 4, 1), e.getProductionDate());
        assertEquals("1A", e.getSubcontractor());        // line mapped into subcontractor
        assertEquals("ART1", e.getArticleNo());
        assertEquals(600, e.getOutput());
        assertEquals(2, e.getDetails().size());
        assertTrue(e.getDetails().stream().anyMatch(d -> d.getChemicalCode().equals("HCA") && d.getActualKg() == 8.0));
        assertTrue(e.getDetails().stream().anyMatch(d -> d.getChemicalCode().equals("GH-705AN") && d.getActualKg() == 3.8));
    }
}
