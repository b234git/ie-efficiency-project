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
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocProduction;
import thienloc.manage.entity.VocRecipeArticle;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.entity.VocSubconDetail;
import thienloc.manage.entity.VocSubconEntry;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocProductionRepository;
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
    @Mock private VocProductionRepository vocProductionRepo;
    @Mock private DailyProductionRepository productionRepo;
    @Mock private SystemLogService systemLogService;

    @InjectMocks private VocService vocService;

    /** §1: allowance is weighted by each slot's article × slot output (not first-article ×
     *  whole-day output), and the High/Low ranking flags ratio ≥1.1 / ≤0.9. */
    @Test
    void perSlotAllowanceAndConsumptionRanking() {
        LocalDate d = LocalDate.of(2026, 4, 1);

        // voc_production: one row per (date, section, line, article), output already
        // apportioned from the "Data" sheet (here ART1=100, ART2=200 on line 1A).
        List<VocProduction> prod = List.of(
                VocProduction.builder().productionDate(d).section("SEW").line("1A").articleNo("ART1").output(100.0).build(),
                VocProduction.builder().productionDate(d).section("SEW").line("1A").articleNo("ART2").output(200.0).build());

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
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prod);
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

        List<VocProduction> prod = List.of(
                VocProduction.builder().productionDate(d1).section("SEW").line("1A").articleNo("ART1").output(100.0).build(),
                VocProduction.builder().productionDate(d2).section("SEW").line("1A").articleNo("ART2").output(80.0).build());

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d1).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(4.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d2).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(9.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build()));
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prod);
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

        // voc_production carries the VOC section directly (SEW, ASSY) — no EFF→VOC mapping.
        List<VocProduction> prod = List.of(
                VocProduction.builder().productionDate(d).section("SEW").line("1A").articleNo("ART1").output(100.0).build(),
                VocProduction.builder().productionDate(d).section("ASSY").line("2B").articleNo("ART2").output(50.0).build());

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(6.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d).section("ASSY").line("2B")
                                .chemicalCode("HCB").quantityKg(2.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build(),
                VocChemical.builder().code("HCB").materialType("SOLVENT").vocFactor(1.0).build()));
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prod);
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().section("SEW").articleNo("ART1").chemicalCode("HCA").kgPerPair(0.01).build(),
                VocStandardRate.builder().section("ASSY").articleNo("ART2").chemicalCode("HCB").kgPerPair(0.02).build()));

        // line=1A (the $A$1 case): only 1A's actual + allowance + output remain
        VocReportDto byLine = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, null, null, "1A", null));
        assertEquals(List.of("HCA"), byLine.getReconcileChemicals());
        assertEquals(3.0, byLine.getTotalVocKg(), 1e-9);          // 6 × 0.5, HCB gone
        assertEquals(100, byLine.getReconcileTotal().getOutput()); // p2's 50 excluded
        // option lists still cover the whole month
        assertEquals(List.of("1A", "2B"), byLine.getAllLines());
        assertEquals(List.of("ASSY", "SEW"), byLine.getAllSections());
        assertEquals(List.of("HCA", "HCB"), byLine.getAllChemCodes());

        // section=ASSY: the mirror slice (filter is the VOC section, mapped from ASSEMBLY BIG)
        VocReportDto bySection = vocService.getMonthlyReport(
                new VocReportFilter("2026-04", null, null, null, "ASSY", null, null));
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

    /** §3: re-saving an existing consumption row carries the new "Throw" through the merge
     *  branch (regression: it used to overwrite quantity+reuse but drop throwKg). */
    @Test
    void saveConsumptionUpdateKeepsThrow() {
        LocalDate d = LocalDate.of(2026, 5, 4);
        VocConsumption existing = VocConsumption.builder()
                .id(5L).productionDate(d).section("ASSY").line("1").chemicalCode("312PM")
                .quantityKg(10.0).throwKg(0.0).reuseKg(0.0).build();
        when(consumptionRepo.findByProductionDateAndSectionAndLineAndChemicalCode(d, "ASSY", "1", "312PM"))
                .thenReturn(Optional.of(existing));
        ArgumentCaptor<VocConsumption> captor = ArgumentCaptor.forClass(VocConsumption.class);
        when(consumptionRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Incoming row (id null → upsert/merge path) with a new throw value.
        vocService.saveConsumption(VocConsumption.builder()
                .productionDate(d).section("ASSY").line("1").chemicalCode("312PM")
                .quantityKg(16.0).throwKg(2.5).reuseKg(1.0).build());

        VocConsumption saved = captor.getValue();
        assertSame(existing, saved);                       // merged onto the existing entity
        assertEquals(16.0, saved.getQuantityKg(), 1e-9);
        assertEquals(2.5, saved.getThrowKg(), 1e-9);       // 0.0 before the fix
        assertEquals(1.0, saved.getReuseKg(), 1e-9);
    }

    /** §3b: a free-typed chemical repeating a code already in the same submit must not produce
     *  two rows for one natural key (date,section,line,chemical) — first occurrence wins. */
    @Test
    void saveConsumptionBatchDedupsRepeatedCode() {
        LocalDate d = LocalDate.of(2026, 5, 4);
        when(consumptionRepo.findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(d, "ASSY", "1"))
                .thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VocConsumption>> captor = ArgumentCaptor.forClass(List.class);
        when(consumptionRepo.saveAll(captor.capture())).thenReturn(List.of());

        VocService.BatchResult res = vocService.saveConsumptionBatch(d, "ASSY", "1",
                List.of("XYZ", "XYZ"),          // same free-typed code twice
                List.of(1.0, 2.0),              // quantity
                List.of(0.0, 0.0),              // throw
                List.of(0.0, 0.0),              // reuse
                true);

        assertEquals(1, res.saved());
        assertEquals(1, captor.getValue().size());
        assertEquals("XYZ", captor.getValue().get(0).getChemicalCode());
        assertEquals(1.0, captor.getValue().get(0).getQuantityKg(), 1e-9);   // first occurrence wins
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
        List<VocProduction> prod = List.of(
                VocProduction.builder().productionDate(d).section("SEW").line("1A").articleNo("ART1").output(100.0).build(),
                VocProduction.builder().productionDate(d).section("SEW").line("2A").articleNo("ART1").output(100.0).build());

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(
                        VocConsumption.builder().productionDate(d).section("SEW").line("1A")
                                .chemicalCode("HCA").quantityKg(2.0).reuseKg(0.0).build(),
                        VocConsumption.builder().productionDate(d).section("SEW").line("2A")
                                .chemicalCode("HCA").quantityKg(1.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("HCA").materialType("SOLVENT").vocFactor(0.5).build()));
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prod);
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

    /** A3: batch save with overwrite=false reports an existing key as a conflict (no save). */
    @Test
    void saveConsumptionBatch_conflictReportedWhenNotOverwrite() {
        LocalDate d = LocalDate.of(2026, 4, 1);
        VocConsumption existing = new VocConsumption();
        existing.setChemicalCode("577NT3");
        when(consumptionRepo.findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(d, "SEW", "1A"))
                .thenReturn(List.of(existing));

        VocService.BatchResult r = vocService.saveConsumptionBatch(
                d, "SEW", "1A", List.of("577NT3"), List.of(0.9), List.of(0.0), List.of(0.0), false);

        assertEquals(0, r.saved());
        assertEquals(List.of("577NT3"), r.conflicts());
    }

    /** A3: batch save with overwrite=true updates the existing row (no conflict reported). */
    @Test
    void saveConsumptionBatch_overwriteUpdatesExisting() {
        LocalDate d = LocalDate.of(2026, 4, 1);
        VocConsumption existing = new VocConsumption();
        existing.setChemicalCode("577NT3");
        when(consumptionRepo.findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(any(), any(), any()))
                .thenReturn(List.of(existing));
        when(consumptionRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        VocService.BatchResult r = vocService.saveConsumptionBatch(
                d, "SEW", "1A", List.of("577NT3"), List.of(0.9), List.of(0.0), List.of(0.0), true);

        assertEquals(1, r.saved());
        assertTrue(r.conflicts().isEmpty());
    }

    /** C1: the DB-sheet recipe parser is header-driven and matches the workbook's own join key.
     *  For the SF ("OS VOC") layout — REF in column A, chemicals from column H, and the matrix
     *  repeated in a second block — it keys on column A (DB!$A) and reads only the first block. */
    @Test
    void importRecipeReadsShiftedOsLayout() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("DB");
            Row r0 = s.createRow(0);   // anchor: manufacturer tier + key-column labels
            r0.createCell(0).setCellValue("REF");
            r0.createCell(1).setCellValue("Pattern #");
            r0.createCell(2).setCellValue("Article #");
            r0.createCell(3).setCellValue("OS Code");
            r0.createCell(4).setCellValue("Pattern #");
            r0.createCell(5).setCellValue("Style");
            r0.createCell(6).setCellValue("Quota");
            r0.createCell(7).setCellValue("Nanpao");
            r0.createCell(8).setCellValue("Nanpao");
            r0.createCell(10).setCellValue("Nanpao");   // second (duplicate) block
            r0.createCell(11).setCellValue("Nanpao");
            Row r1 = s.createRow(1);   // base tier
            r1.createCell(7).setCellValue("Solvent Base");
            r1.createCell(8).setCellValue("Solvent Base");
            r1.createCell(10).setCellValue("Solvent Base");
            r1.createCell(11).setCellValue("Solvent Base");
            Row r2 = s.createRow(2);   // classification tier
            r2.createCell(7).setCellValue("Cleaner");
            r2.createCell(8).setCellValue("Primer");
            r2.createCell(10).setCellValue("Cleaner");
            r2.createCell(11).setCellValue("Primer");
            Row r3 = s.createRow(3);   // chemical-code row (anchor + 3): block 1 then duplicate block 2
            r3.createCell(7).setCellValue("NO.29");
            r3.createCell(8).setCellValue("NUV-24N");
            r3.createCell(10).setCellValue("NO.29");     // duplicate (block 2) — must be ignored
            r3.createCell(11).setCellValue("NUV-24N");
            Row r4 = s.createRow(4);   // first data row (anchor + 4)
            r4.createCell(0).setCellValue("376682");      // REF = the recipe key (DB col A)
            r4.createCell(1).setCellValue("PM-1940");
            r4.createCell(2).setCellValue("376682-01");   // Article # — NOT the key
            r4.createCell(5).setCellValue("MB. 01");
            r4.createCell(6).setCellValue(1500);
            r4.createCell(8).setCellValue(0.0074);        // NUV-24N dosage, block 1 (NO.29 blank)
            r4.createCell(11).setCellValue(0.0299);       // NUV-24N dosage, block 2 — must be ignored
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        when(chemicalRepo.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(recipeArticleRepo.findById(any())).thenReturn(Optional.empty());
        ArgumentCaptor<List<VocStandardRate>> rateCaptor = ArgumentCaptor.forClass(List.class);
        when(rateRepo.saveAll(rateCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<VocRecipeArticle> artCaptor = ArgumentCaptor.forClass(VocRecipeArticle.class);
        when(recipeArticleRepo.save(artCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importRecipe(new MockMultipartFile("file", "OS.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        // exactly one dosage captured: keyed by REF (col A = DB!$A), chemical from block 1 only
        List<VocStandardRate> capturedRates = rateCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertEquals(1, capturedRates.size());
        VocStandardRate rate = capturedRates.get(0);
        assertEquals("376682", rate.getArticleNo());     // DB col A (REF), not Article # (col C)
        assertEquals("NUV-24N", rate.getChemicalCode());
        assertEquals("SEW", rate.getSection());          // no Data sheet → default section
        assertEquals(0.0074, rate.getKgPerPair(), 1e-9); // block 1 value; block 2 (0.0299) ignored
        // identity row resolved by header label, not by fixed C/D/E/F columns
        VocRecipeArticle art = artCaptor.getValue();
        assertEquals("376682", art.getArticleNo());
        assertEquals("PM-1940", art.getModelCode());   // Pattern # (col B)
        assertEquals("MB. 01", art.getModelName());     // Style (col F)
        assertEquals(1500.0, art.getBaseE(), 1e-9);     // Quota (col G)
    }

    /** Section keying: the same (article, chemical) carries a different dosage per section
     *  (e.g. 98NH1/577NT differ 30x between SEW and ASSY). Reconciliation must use the
     *  PRODUCTION row's section, not collide into one global rate. */
    @Test
    void allowanceUsesProductionSectionRecipe() {
        LocalDate d = LocalDate.of(2026, 4, 1);
        List<VocProduction> prod = List.of(
                VocProduction.builder().productionDate(d).section("ASSY").line("1A").articleNo("ART1").output(100.0).build());

        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of("2026-04"));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(List.of(VocConsumption.builder().productionDate(d).section("ASSY").line("1A")
                        .chemicalCode("577NT").quantityKg(3.0).reuseKg(0.0).build()));
        when(chemicalRepo.findAll()).thenReturn(List.of(
                VocChemical.builder().code("577NT").materialType("SOLVENT").vocFactor(0.5).build()));
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prod);
        when(rateRepo.findAll()).thenReturn(List.of(
                VocStandardRate.builder().section("SEW").articleNo("ART1").chemicalCode("577NT").kgPerPair(0.001).build(),
                VocStandardRate.builder().section("ASSY").articleNo("ART1").chemicalCode("577NT").kgPerPair(0.04).build()));

        VocReportDto report = vocService.getMonthlyReport(VocReportFilter.ofMonth("2026-04"));

        var row = report.getReconcileWeeks().get(0).getRows().get(0);
        // ASSY production → 100 * 0.04 = 4.0, NOT the SEW rate (100 * 0.001 = 0.1)
        assertEquals(4.0, row.getCells().get("577NT").getAllowanceKg(), 1e-9);
    }

    /** C2: re-importing a section whose R sheet leaves VOC=0 must NOT wipe an existing real factor. */
    @Test
    void importChemicalsDoesNotOverwriteRealFactorWithZero() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("R");
            Row h = s.createRow(0);
            String[] headers = {"Code", "Material Type", "Classification", "Manufacturer", "VOC",
                    "UNIT", "kg", "Container", "Price", "$ / KG", "Date"};
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("577NT");
            r.createCell(1).setCellValue("Solvent Base");
            r.createCell(2).setCellValue("Adhesive");
            r.createCell(3).setCellValue("Greco");
            r.createCell(4).setCellValue(0);          // VOC = 0 (ASSY/OS R sheets leave it 0)
            // price columns intentionally left blank
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        VocChemical existing = VocChemical.builder().code("577NT").vocFactor(0.745).pricePerKg(2.5)
                .materialType("SOLVENT").active(true).build();
        when(chemicalRepo.findByCodeIgnoreCase("577NT")).thenReturn(Optional.of(existing));
        ArgumentCaptor<VocChemical> captor = ArgumentCaptor.forClass(VocChemical.class);
        when(chemicalRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importChemicalsFromExcel(new MockMultipartFile("file", "R.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        VocChemical saved = captor.getValue();
        assertEquals(0.745, saved.getVocFactor(), 1e-9);   // real factor preserved
        assertEquals(2.5, saved.getPricePerKg(), 1e-9);    // real price preserved
        assertEquals("Greco", saved.getManufacturer());     // present text still updates
    }

    /** C3: the full 11-col "Actual" sheet reads reuse from column 6 (Reuse), not column 5 (Throw),
     *  and the Section column drives the section (here SF). */
    @Test
    void importConsumptionFullActualReadsReuseFromColumnSix() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Actual");
            Row h = s.createRow(0);
            String[] headers = {"Date", "Section", "Line", "Chemicals", "Production", "Throw", "Reuse",
                    "Material Type", "Classification", "Manufacturer", "VOC"};
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("2026-05-04");
            r.createCell(1).setCellValue("SF");
            r.createCell(2).setCellValue("1");
            r.createCell(3).setCellValue("NP-72KMN");
            r.createCell(4).setCellValue(20.0);   // Production = consumed qty
            r.createCell(5).setCellValue(7.0);    // Throw — stored, but not the reuse column
            r.createCell(6).setCellValue(3.0);    // Reuse — the real reuse
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        ArgumentCaptor<List<VocConsumption>> captor = ArgumentCaptor.forClass(List.class);
        when(consumptionRepo.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importConsumptionFromExcel(new MockMultipartFile("file", "Actual.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        VocConsumption c = captor.getAllValues().stream().flatMap(List::stream).toList().get(0);
        assertEquals(LocalDate.of(2026, 5, 4), c.getProductionDate());
        assertEquals("SF", c.getSection());
        assertEquals("1", c.getLine());
        assertEquals("NP-72KMN", c.getChemicalCode());
        assertEquals(20.0, c.getQuantityKg(), 1e-9);
        assertEquals(3.0, c.getReuseKg(), 1e-9);   // column 6 (Reuse), NOT column 5 (Throw=7.0)
        assertEquals(7.0, c.getThrowKg(), 1e-9);   // column 5 (Throw) stored for sheet parity
    }
}
