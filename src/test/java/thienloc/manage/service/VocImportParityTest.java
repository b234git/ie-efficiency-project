package thienloc.manage.service;

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
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocRecipeArticleRepository;
import thienloc.manage.repository.VocStandardRateRepository;
import thienloc.manage.repository.VocSubconEntryRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Parity check against the REAL workbooks in {@code info/new/} (untracked sample data; each test
 * is skipped when its file is absent). It runs the production importers over the actual files and
 * compares the results to values independently extracted from the same sheets — proving the
 * multi-section import reproduces what the workbook itself computes.
 *
 * Recipe oracle = AVERAGEIFS(DB!firstBlockColumn, DB!$A, key) per the workbook's own per-chemical
 * sheets: key on column A, first chemical block only, average rows sharing a key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocImportParityTest {

    @Mock private VocChemicalRepository chemicalRepo;
    @Mock private VocConsumptionRepository consumptionRepo;
    @Mock private VocStandardRateRepository rateRepo;
    @Mock private VocRecipeArticleRepository recipeArticleRepo;
    @Mock private VocSubconEntryRepository subconRepo;
    @Mock private DailyProductionRepository productionRepo;
    @Mock private SystemLogService systemLogService;

    @InjectMocks private VocService vocService;

    /** Import the real file's "DB" sheet and return both the (article||chem→kgPerPair) map and
     *  the distinct chemical-code set the parser registered. */
    private record RecipeResult(Map<String, Double> rates, Set<String> chemicals) {}

    private RecipeResult importRecipe(String fileName) throws Exception {
        Path p = Path.of("info", "new", fileName);
        Assumptions.assumeTrue(Files.exists(p), "sample file not present: " + p);

        when(chemicalRepo.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(rateRepo.findBySectionAndArticleNoAndChemicalCode(any(), any(), any())).thenReturn(Optional.empty());
        when(recipeArticleRepo.findById(any())).thenReturn(Optional.empty());
        when(recipeArticleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<VocStandardRate> rateCaptor = ArgumentCaptor.forClass(VocStandardRate.class);
        when(rateRepo.save(rateCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importRecipe(new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(p)));

        Map<String, Double> rates = new HashMap<>();
        Set<String> chems = new HashSet<>();
        for (VocStandardRate r : rateCaptor.getAllValues()) {
            rates.put(r.getArticleNo() + "||" + r.getChemicalCode(), r.getKgPerPair());
            chems.add(r.getChemicalCode());
        }
        return new RecipeResult(rates, chems);
    }

    @Test
    void sewRecipeMatchesFile() throws Exception {
        RecipeResult r = importRecipe("SEW VOC - JUN.xlsx");
        assertEquals(2327, r.rates().size(), "distinct (article,chemical) dosage pairs");
        assertEquals(0.0076923077, r.rates().get("379217||GH-7055"), 1e-9);
        assertEquals(0.0117647059, r.rates().get("379217||GH-708"), 1e-9);
    }

    @Test
    void assyRecipeMatchesFile() throws Exception {
        RecipeResult r = importRecipe("ASSY VOC - MAY.xlsx");
        assertEquals(4037, r.rates().size(), "distinct (article,chemical) dosage pairs");
        assertEquals(0.0038461538, r.rates().get("379223||320NUV"), 1e-9);
        assertEquals(0.0400000000, r.rates().get("379223||98NH1"), 1e-9);
    }

    @Test
    void osRecipeMatchesFile() throws Exception {
        RecipeResult r = importRecipe("OS VOC - MAY.xlsx");
        assertEquals(21, r.chemicals().size(), "first-block chemicals only (block 2 ignored)");
        assertEquals(3856, r.rates().size(), "distinct (article,chemical) dosage pairs");
        // keyed by REF (col A), block-1 dosages
        assertEquals(0.0075187970, r.rates().get("377766||256"), 1e-9);
        assertEquals(0.0091743119, r.rates().get("377766||NUV-24N"), 1e-9);
    }

    @Test
    void osConsumptionImportsSfSectionFromActual() throws Exception {
        Path p = Path.of("info", "new", "OS VOC - MAY.xlsx");
        Assumptions.assumeTrue(Files.exists(p), "sample file not present: " + p);
        // Feed only the "Actual" sheet (the consumption importer reads sheet 0), copied from the file.
        byte[] actualOnly = extractSheetAsFirst(Files.readAllBytes(p), "Actual");

        when(consumptionRepo.findByProductionDateAndSectionAndLineAndChemicalCode(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<VocConsumption> cap = ArgumentCaptor.forClass(VocConsumption.class);
        when(consumptionRepo.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        vocService.importConsumptionFromExcel(new MockMultipartFile("file", "Actual.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", actualOnly));

        // every consumption row from the OS file carries section SF (driven by the Section column)
        Set<String> sections = new HashSet<>();
        for (VocConsumption c : cap.getAllValues()) sections.add(c.getSection());
        assertEquals(Set.of("SF"), sections, "OS VOC Actual sheet section column = SF");
    }

    /** Copy one sheet of a workbook into a fresh single-sheet workbook (sheet 0), so importers that
     *  read {@code getSheetAt(0)} can be pointed at a specific sheet of the real file. */
    private static byte[] extractSheetAsFirst(byte[] src, String sheetName) throws Exception {
        try (var in = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(src));
             var out = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var bos = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet from = in.getSheet(sheetName);
            org.apache.poi.ss.usermodel.Sheet to = out.createSheet(sheetName);
            for (int i = 0; i <= from.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row fr = from.getRow(i);
                if (fr == null) continue;
                org.apache.poi.ss.usermodel.Row tr = to.createRow(i);
                for (int c = 0; c < fr.getLastCellNum(); c++) {
                    org.apache.poi.ss.usermodel.Cell fc = fr.getCell(c);
                    if (fc == null) continue;
                    org.apache.poi.ss.usermodel.Cell tc = tr.createCell(c);
                    boolean formula = fc.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA;
                    org.apache.poi.ss.usermodel.CellType et = formula ? fc.getCachedFormulaResultType() : fc.getCellType();
                    if (et == org.apache.poi.ss.usermodel.CellType.NUMERIC && !formula
                            && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(fc)) {
                        tc.setCellValue(fc.getLocalDateTimeCellValue().toLocalDate().toString());  // ISO string
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
}
