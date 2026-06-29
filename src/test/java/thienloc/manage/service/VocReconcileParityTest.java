package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReconcileRowDto;
import thienloc.manage.dto.VocReconcileWeekDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocReportFilter;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocProduction;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocProductionRepository;
import thienloc.manage.repository.VocRecipeArticleRepository;
import thienloc.manage.repository.VocStandardRateRepository;
import thienloc.manage.repository.VocSubconEntryRepository;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end VOC reconciliation parity against the REAL workbooks in {@code info/new/}
 * (untracked sample data; each test skips when its file is absent).
 *
 * <p>It runs the production importers (recipe + Data + Actual) over an actual file, drives the real
 * {@link VocService#getMonthlyReport} reconcile, then reads the workbook's own {@code %} sheet
 * (the Allowance and Actual blocks) and asserts the app reproduces them per (date, chemical) within
 * tolerance — proving allowance = Σ output×recipe and actual = Σ qty match the file 1:1.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocReconcileParityTest {

    @Mock private VocChemicalRepository chemicalRepo;
    @Mock private VocConsumptionRepository consumptionRepo;
    @Mock private VocStandardRateRepository rateRepo;
    @Mock private VocRecipeArticleRepository recipeArticleRepo;
    @Mock private VocSubconEntryRepository subconRepo;
    @Mock private DailyProductionRepository productionRepo;
    @Mock private VocProductionRepository vocProductionRepo;
    @Mock private SystemLogService systemLogService;

    @InjectMocks private VocService vocService;

    private static final double TOL = 0.005;   // 0.5% — display rounding in the % sheet

    // Allowance (Σ output×recipe) and actual both reproduce the % sheet exactly — verified after the
    // recipe key is matched verbatim (DB!$A, no "-2" stripping) the way the workbook's AVERAGEIFS does.
    @Test void sewReconcileMatchesPercentSheet() throws Exception {
        runParity("SEW VOC - JUN.xlsx", Set.of());
    }

    @Test void osReconcileMatchesPercentSheet() throws Exception {
        runParity("OS VOC - MAY.xlsx", Set.of());
    }

    // ASSY is 1:1 EXCEPT 6001LVN, and that is a defect in the source workbook, not the app:
    // its numbered sheet 21 multiplies slot 1 by Data!AA (the scaling factor) instead of Data!AB
    // (slot-1 output) — sheets 1..20 correctly use AB. The app uses the right column, so its 6001LVN
    // allowance is correct and the file's is low. Pinned to document the file bug (≈1.33× on a sparse
    // chemical; negligible for dense ones). If the file is corrected this test trips → drop 6001LVN.
    @Test void assyReconcileMatchesPercentSheet() throws Exception {
        runParity("ASSY VOC - MAY.xlsx", Set.of("6001LVN"));
    }

    private void runParity(String fileName, Set<String> knownDivergentAllowChems) throws Exception {
        Path p = Path.of("info", "new", fileName);
        Assumptions.assumeTrue(Files.exists(p), "sample file not present: " + p);
        byte[] bytes = Files.readAllBytes(p);

        // ── Import recipe (DB), Data (production), Actual (consumption), capturing what is saved ──
        when(recipeArticleRepo.findById(any())).thenReturn(Optional.empty());
        when(recipeArticleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(rateRepo.findBySectionAndArticleNoAndChemicalCode(any(), any(), any())).thenReturn(Optional.empty());
        when(chemicalRepo.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        ArgumentCaptor<VocStandardRate> rateCap = ArgumentCaptor.forClass(VocStandardRate.class);
        when(rateRepo.save(rateCap.capture())).thenAnswer(i -> i.getArgument(0));
        vocService.importRecipe(file(fileName, bytes));

        // Chemical master (R sheet → sheet 0) so canonicalChem can merge case/alias variants
        // ("Latex" recipe vs "LATEX" consumption) into one reconcile column — as the real app does.
        ArgumentCaptor<thienloc.manage.entity.VocChemical> chemCap =
                ArgumentCaptor.forClass(thienloc.manage.entity.VocChemical.class);
        when(chemicalRepo.save(chemCap.capture())).thenAnswer(i -> i.getArgument(0));
        vocService.importChemicalsFromExcel(file(fileName, extractSheetAsFirst(bytes, "R")));
        List<thienloc.manage.entity.VocChemical> chems = chemCap.getAllValues();

        when(vocProductionRepo.findByProductionDateAndSectionAndLineAndArticleNo(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<VocProduction> prodCap = ArgumentCaptor.forClass(VocProduction.class);
        when(vocProductionRepo.save(prodCap.capture())).thenAnswer(i -> i.getArgument(0));
        vocService.importVocProduction(file(fileName, bytes));

        when(consumptionRepo.findByProductionDateAndSectionAndLineAndChemicalCode(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<VocConsumption> consCap = ArgumentCaptor.forClass(VocConsumption.class);
        when(consumptionRepo.save(consCap.capture())).thenAnswer(i -> i.getArgument(0));
        vocService.importConsumptionFromExcel(file(fileName, bytes));

        List<VocStandardRate> rates = rateCap.getAllValues();
        List<VocProduction> prods = prodCap.getAllValues();
        List<VocConsumption> cons = consCap.getAllValues();
        assertTrue(!cons.isEmpty() && !prods.isEmpty() && !rates.isEmpty(),
                "imports produced no rows for " + fileName);

        // Month = YearMonth of the earliest consumption date (these files carry one month).
        LocalDate min = cons.stream().map(VocConsumption::getProductionDate).min(LocalDate::compareTo).orElseThrow();
        String month = YearMonth.from(min).toString();

        // ── Drive the real report off the captured data ──
        when(consumptionRepo.findDistinctMonths()).thenReturn(List.of(month));
        when(consumptionRepo.findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(any(), any()))
                .thenReturn(cons);
        when(vocProductionRepo.findByProductionDateBetween(any(), any())).thenReturn(prods);
        when(rateRepo.findAll()).thenReturn(rates);
        when(chemicalRepo.findAll()).thenReturn(chems);

        VocReportDto report = vocService.getMonthlyReport(
                new VocReportFilter(month, null, null, null, null, null, null));

        // app cell map: date||CHEM -> [allowanceKg, actualKg]
        Map<String, double[]> app = new HashMap<>();
        for (VocReconcileWeekDto wk : report.getReconcileWeeks()) {
            for (VocReconcileRowDto row : wk.getRows()) {
                if (row.getDate() == null) continue;
                for (Map.Entry<String, VocReconcileCellDto> e : row.getCells().entrySet()) {
                    app.put(row.getDate() + "||" + norm(e.getKey()),
                            new double[]{e.getValue().getAllowanceKg(), e.getValue().getActualKg()});
                }
            }
        }

        // ── Oracle: the workbook's own % sheet (Allowance + Actual blocks) ──
        Map<String, Double> fAllow = new HashMap<>();
        Map<String, Double> fActual = new HashMap<>();
        readPercentSheet(bytes, fAllow, fActual);

        // ── Compare every cell the % sheet states an allowance for ──
        int comparedAllow = 0, comparedActual = 0;
        double worstAllow = 0, worstActual = 0;
        String worstAllowKey = "", worstActualKey = "";
        List<String> misses = new ArrayList<>();
        for (Map.Entry<String, Double> fe : fAllow.entrySet()) {
            double[] a = app.get(fe.getKey());
            if (a == null || fe.getValue() == null || fe.getValue() == 0) continue;
            comparedAllow++;
            double rel = Math.abs(a[0] - fe.getValue()) / Math.abs(fe.getValue());
            if (rel > worstAllow) { worstAllow = rel; worstAllowKey = fe.getKey(); }
            if (rel > TOL && misses.size() < 15)
                misses.add(String.format("ALLOW %s app=%.4f file=%.4f rel=%.4f", fe.getKey(), a[0], fe.getValue(), rel));
        }
        for (Map.Entry<String, Double> fe : fActual.entrySet()) {
            double[] a = app.get(fe.getKey());
            if (a == null || fe.getValue() == null || fe.getValue() == 0) continue;
            comparedActual++;
            double rel = Math.abs(a[1] - fe.getValue()) / Math.abs(fe.getValue());
            if (rel > worstActual) { worstActual = rel; worstActualKey = fe.getKey(); }
            if (rel > TOL && misses.size() < 15)
                misses.add(String.format("ACTUAL %s app=%.4f file=%.4f rel=%.4f", fe.getKey(), a[1], fe.getValue(), rel));
        }

        // distribution + per-chem worst (allowance)
        int b05 = 0, b2 = 0, b5 = 0, bover = 0;
        Map<String, Double> perChem = new HashMap<>();
        for (Map.Entry<String, Double> fe : fAllow.entrySet()) {
            double[] a = app.get(fe.getKey());
            if (a == null || fe.getValue() == null || fe.getValue() == 0) continue;
            double rel = Math.abs(a[0] - fe.getValue()) / Math.abs(fe.getValue());
            if (rel <= 0.005) b05++; else if (rel <= 0.02) b2++; else if (rel <= 0.05) b5++; else bover++;
            String chem = fe.getKey().substring(fe.getKey().indexOf("||") + 2);
            perChem.merge(chem, rel, Math::max);
        }
        System.out.printf("%n[PARITY %s] month=%s comparedAllow=%d comparedActual=%d worstAllow=%.4f(%s) worstActual=%.4f(%s)%n"
                        + "  ALLOW buckets: <=0.5%%=%d  <=2%%=%d  <=5%%=%d  >5%%=%d%n",
                fileName, month, comparedAllow, comparedActual, worstAllow, worstAllowKey, worstActual, worstActualKey,
                b05, b2, b5, bover);
        perChem.entrySet().stream().filter(e -> e.getValue() > TOL)
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .forEach(e -> System.out.printf("  chem %-12s worstRel=%.4f%n", e.getKey(), e.getValue()));

        // Chemicals whose allowance diverges from the % sheet beyond display rounding.
        Set<String> divergent = new TreeSet<>();
        perChem.forEach((chem, rel) -> { if (rel > TOL) divergent.add(chem); });

        assertTrue(comparedAllow >= 10 && comparedActual >= 10, "too few cells overlapped — key/section mismatch?");
        // Actual consumption is imported faithfully for every section.
        assertTrue(worstActual <= TOL, "actual diverges from % sheet: worst " + worstActual + " at " + worstActualKey);
        // Allowance is 1:1 except the documented multi-article set (empty for SEW).
        assertEquals(new TreeSet<>(knownDivergentAllowChems), divergent,
                "allowance divergence set changed for " + fileName + " (worst " + worstAllow + " at " + worstAllowKey + ")");
    }

    private static MockMultipartFile file(String name, byte[] b) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", b);
    }

    /** Copy one sheet into a fresh single-sheet workbook (sheet 0) so importers reading
     *  {@code getSheetAt(0)} can be pointed at a named sheet of the real file. */
    private static byte[] extractSheetAsFirst(byte[] src, String sheetName) throws Exception {
        try (XSSFWorkbook in = new XSSFWorkbook(new ByteArrayInputStream(src));
             XSSFWorkbook out = new XSSFWorkbook();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            Sheet from = in.getSheet(sheetName);
            Sheet to = out.createSheet(sheetName);
            for (int i = 0; i <= from.getLastRowNum(); i++) {
                Row fr = from.getRow(i);
                if (fr == null) continue;
                Row tr = to.createRow(i);
                for (int c = 0; c < fr.getLastCellNum(); c++) {
                    Cell fc = fr.getCell(c);
                    if (fc == null) continue;
                    Cell tc = tr.createCell(c);
                    boolean formula = fc.getCellType() == CellType.FORMULA;
                    CellType et = formula ? fc.getCachedFormulaResultType() : fc.getCellType();
                    if (et == CellType.NUMERIC && !formula && DateUtil.isCellDateFormatted(fc)) {
                        tc.setCellValue(fc.getLocalDateTimeCellValue().toLocalDate().toString());
                    } else switch (et) {
                        case NUMERIC -> tc.setCellValue(fc.getNumericCellValue());
                        case BOOLEAN -> tc.setCellValue(fc.getBooleanCellValue());
                        default -> tc.setCellValue(fc.toString());
                    }
                }
            }
            out.write(bos);
            return bos.toByteArray();
        }
    }

    /** chem key normaliser so "Latex"/"LATEX" and numeric code 5611 line up across app & sheet. */
    private static String norm(Object code) {
        if (code instanceof Double d && d == Math.floor(d)) return String.valueOf((long) (double) d);
        return String.valueOf(code).trim().toUpperCase();
    }

    /**
     * Read the % sheet's Allowance and Actual blocks into date||CHEM → kg maps. Layout: row 1 =
     * chem headers (col C.. until a "VOC"/"OUTPUT" header); blocks start at a col-A label
     * ("Allowance"/"Actual") whose first data row carries a date in col B; date rows run until a
     * "Total" in col B.
     */
    private static void readPercentSheet(byte[] bytes, Map<String, Double> allow, Map<String, Double> actual)
            throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet ws = wb.getSheet("%");
            // chemical columns from row 0
            Map<Integer, String> cols = new HashMap<>();
            Row hdr = ws.getRow(0);
            for (int c = 2; c <= 40 && hdr != null; c++) {
                Cell cell = hdr.getCell(c);
                if (cell == null) continue;
                String s = cellString(cell);
                if (s == null || s.isBlank()) continue;
                String u = s.trim().toUpperCase();
                if (u.equals("VOC") || u.startsWith("OUTPUT") || u.startsWith("VOC ")) break;
                cols.put(c, norm(cellRaw(cell)));
            }
            for (int r = 0; r <= ws.getLastRowNum(); r++) {
                Row row = ws.getRow(r);
                if (row == null) continue;
                String label = cellString(row.getCell(0));
                if (label == null) continue;
                Map<String, Double> target = label.trim().equalsIgnoreCase("Allowance") ? allow
                        : label.trim().equalsIgnoreCase("Actual") ? actual : null;
                if (target == null) continue;
                readBlock(ws, r, cols, target);
            }
        }
    }

    private static void readBlock(Sheet ws, int start, Map<Integer, String> cols, Map<String, Double> out) {
        for (int r = start; r <= ws.getLastRowNum(); r++) {
            Row row = ws.getRow(r);
            if (row == null) continue;
            Cell b = row.getCell(1);
            if (b != null && b.getCellType() == CellType.STRING
                    && "total".equalsIgnoreCase(b.getStringCellValue().trim())) {
                break;
            }
            LocalDate d = dateOf(b);
            if (d == null) continue;
            for (Map.Entry<Integer, String> ce : cols.entrySet()) {
                Double v = numberOf(row.getCell(ce.getKey()));
                if (v != null) out.put(d + "||" + ce.getValue(), v);
            }
        }
    }

    private static LocalDate dateOf(Cell c) {
        if (c == null) return null;
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (t == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        return null;
    }

    private static Double numberOf(Cell c) {
        if (c == null) return null;
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        return t == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c) ? c.getNumericCellValue() : null;
    }

    private static String cellString(Cell c) {
        if (c == null) return null;
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (t == CellType.STRING) return c.getStringCellValue();
        if (t == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) return String.valueOf(c.getNumericCellValue());
        return null;
    }

    private static Object cellRaw(Cell c) {
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (t == CellType.NUMERIC) return c.getNumericCellValue();
        return c.getStringCellValue();
    }
}
