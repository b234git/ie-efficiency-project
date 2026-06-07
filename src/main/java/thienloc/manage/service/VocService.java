package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.dto.VocChemicalSummaryDto;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReconcileRowDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocReportRowDto;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocStandardRateRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * VOC module service: chemical-master CRUD, daily-consumption CRUD, the monthly
 * report (VOC kg / g-per-pair / water-based % / cost), and Excel import for both
 * the chemical master ("R" sheet) and consumption ("Actual" sheet).
 *
 * Core formula: netVocKg = (quantityKg - reuseKg) * chemical.vocFactor.
 * Output for VOC g/pair comes from the existing daily_production table.
 */
@Service
public class VocService {

    @Autowired private VocChemicalRepository chemicalRepo;
    @Autowired private VocConsumptionRepository consumptionRepo;
    @Autowired private VocStandardRateRepository rateRepo;
    @Autowired private DailyProductionRepository productionRepo;
    @Autowired private SystemLogService systemLogService;

    public static final String DEFAULT_SECTION = "SEW";

    // ── Material-type normalization ───────────────────────────────────────────

    public static String normalizeMaterialType(String raw) {
        if (raw == null) return "SOLVENT";
        return raw.trim().toUpperCase().startsWith("WATER") ? "WATER" : "SOLVENT";
    }

    // ════════════════════════════════════════════════════════════════════════
    // Chemical master CRUD
    // ════════════════════════════════════════════════════════════════════════

    public List<VocChemical> getAllChemicals() {
        return chemicalRepo.findAllByOrderByCodeAsc();
    }

    public List<VocChemical> getActiveChemicals() {
        return chemicalRepo.findByActiveTrueOrderByCodeAsc();
    }

    public Optional<VocChemical> findChemicalById(Long id) {
        return chemicalRepo.findById(id);
    }

    public VocChemical saveChemical(VocChemical c) {
        boolean isNew = (c.getId() == null);
        c.setCode(c.getCode() != null ? c.getCode().trim() : null);
        c.setMaterialType(normalizeMaterialType(c.getMaterialType()));
        if (c.getActive() == null) c.setActive(true);
        if (c.getVocFactor() == null) c.setVocFactor(0.0);
        VocChemical saved = chemicalRepo.save(c);
        systemLogService.logAction(isNew ? "ADD_VOC_CHEMICAL" : "EDIT_VOC_CHEMICAL", saved.getCode());
        return saved;
    }

    public void deleteChemical(Long id) {
        chemicalRepo.findById(id).ifPresent(c -> {
            chemicalRepo.deleteById(id);
            systemLogService.logAction("DELETE_VOC_CHEMICAL", c.getCode());
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Consumption CRUD (manual entry)
    // ════════════════════════════════════════════════════════════════════════

    public List<VocConsumption> getConsumption(LocalDate date, String section, String line) {
        return consumptionRepo.findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(date, section, line);
    }

    public Optional<VocConsumption> findConsumptionById(Long id) {
        return consumptionRepo.findById(id);
    }

    /** Upsert on the natural key (date, section, line, chemical) so re-entry overwrites. */
    public VocConsumption saveConsumption(VocConsumption c) {
        if (c.getSection() == null || c.getSection().isBlank()) c.setSection(DEFAULT_SECTION);
        c.setLine(c.getLine() != null ? c.getLine().trim() : null);
        c.setChemicalCode(c.getChemicalCode() != null ? c.getChemicalCode().trim() : null);
        if (c.getQuantityKg() == null) c.setQuantityKg(0.0);
        if (c.getReuseKg() == null) c.setReuseKg(0.0);

        if (c.getId() == null) {
            Optional<VocConsumption> existing = consumptionRepo
                    .findByProductionDateAndSectionAndLineAndChemicalCode(
                            c.getProductionDate(), c.getSection(), c.getLine(), c.getChemicalCode());
            if (existing.isPresent()) {
                VocConsumption e = existing.get();
                e.setQuantityKg(c.getQuantityKg());
                e.setReuseKg(c.getReuseKg());
                c = e;
            }
        }
        boolean isNew = (c.getId() == null);
        VocConsumption saved = consumptionRepo.save(c);
        systemLogService.logAction(isNew ? "ADD_VOC_CONSUMPTION" : "EDIT_VOC_CONSUMPTION",
                saved.getProductionDate() + " | " + saved.getLine() + " | " + saved.getChemicalCode());
        return saved;
    }

    public void deleteConsumption(Long id) {
        consumptionRepo.findById(id).ifPresent(c -> {
            consumptionRepo.deleteById(id);
            systemLogService.logAction("DELETE_VOC_CONSUMPTION",
                    c.getProductionDate() + " | " + c.getLine() + " | " + c.getChemicalCode());
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Standard recipe CRUD (the VOC "DB")
    // ════════════════════════════════════════════════════════════════════════

    /** Page size for the recipe list — avoids loading the whole table into memory. */
    public static final int RECIPE_PAGE_SIZE = 25;

    /**
     * One page of recipe rows, ordered by (article, chemical). DB-level LIMIT/OFFSET;
     * optional search matches article OR chemical. No associations ⇒ no N+1.
     */
    public Page<VocStandardRate> getRatesPage(int page, String q) {
        Pageable pageable = PageRequest.of(Math.max(0, page), RECIPE_PAGE_SIZE,
                Sort.by("articleNo").and(Sort.by("chemicalCode")));
        if (q != null && !q.isBlank()) {
            String term = q.trim();
            return rateRepo.findByArticleNoContainingIgnoreCaseOrChemicalCodeContainingIgnoreCase(
                    term, term, pageable);
        }
        return rateRepo.findAll(pageable);
    }

    public Optional<VocStandardRate> findRateById(Long id) {
        return rateRepo.findById(id);
    }

    /** Upsert on (articleNo, chemicalCode). */
    public VocStandardRate saveRate(VocStandardRate r) {
        r.setArticleNo(r.getArticleNo() != null ? r.getArticleNo().trim() : null);
        r.setChemicalCode(r.getChemicalCode() != null ? r.getChemicalCode().trim() : null);
        if (r.getKgPerPair() == null) r.setKgPerPair(0.0);

        if (r.getId() == null) {
            Optional<VocStandardRate> existing =
                    rateRepo.findByArticleNoAndChemicalCode(r.getArticleNo(), r.getChemicalCode());
            if (existing.isPresent()) {
                VocStandardRate e = existing.get();
                e.setKgPerPair(r.getKgPerPair());
                r = e;
            }
        }
        boolean isNew = (r.getId() == null);
        VocStandardRate saved = rateRepo.save(r);
        systemLogService.logAction(isNew ? "ADD_VOC_RATE" : "EDIT_VOC_RATE",
                saved.getArticleNo() + " | " + saved.getChemicalCode());
        return saved;
    }

    public void deleteRate(Long id) {
        rateRepo.findById(id).ifPresent(r -> {
            rateRepo.deleteById(id);
            systemLogService.logAction("DELETE_VOC_RATE", r.getArticleNo() + " | " + r.getChemicalCode());
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — standard recipe. Auto-detects two layouts:
    //   WIDE  : the original "DB" sheet (anchor "Article #" in col A; chemical
    //           codes 3 rows below starting at column G; article rows below).
    //   LONG  : the template (Article | Chemical | Kg per pair), data from row 2.
    // Duplicate (article, chemical) pairs are averaged (matches Excel AVERAGEIFS).
    // ════════════════════════════════════════════════════════════════════════

    private static final int RECIPE_CHEM_COL_START = 6;   // column G (0-based)

    @Transactional
    public WeeklyImportResultDto importRecipe(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            // Detect WIDE layout: an "Article #" anchor with a chemical header row 3 below
            int anchor = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
                Row r = sheet.getRow(i);
                String a = r != null ? getString(r.getCell(0)) : null;
                if (a != null && a.toLowerCase().contains("article")) { anchor = i; break; }
            }
            Row chemRow = anchor >= 0 ? sheet.getRow(anchor + 3) : null;
            boolean wide = chemRow != null
                    && getString(chemRow.getCell(RECIPE_CHEM_COL_START)) != null;

            // Accumulate sum + count per (article, chemical) for averaging
            Map<String, double[]> agg = new LinkedHashMap<>();   // key -> {sum, count}
            Map<String, String[]> keyParts = new HashMap<>();    // key -> {article, chemical}

            if (wide) {
                Map<Integer, String> chemCols = new LinkedHashMap<>();
                for (int c = RECIPE_CHEM_COL_START; c < chemRow.getLastCellNum(); c++) {
                    String name = getString(chemRow.getCell(c));
                    if (name != null && !name.isBlank()) chemCols.put(c, name.trim());
                }
                if (chemCols.isEmpty()) {
                    result.getErrors().add("No chemical columns found in the DB header row.");
                    return result;
                }
                for (int i = anchor + 4; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    // Column B holds the literal article; column A is a "=B" mirror formula.
                    String article = getString(row.getCell(1));
                    if (article == null || article.isBlank()) article = getString(row.getCell(0));
                    if (article == null || article.isBlank()) continue;
                    article = article.trim();
                    for (Map.Entry<Integer, String> e : chemCols.entrySet()) {
                        Double v = getDouble(row.getCell(e.getKey()));
                        if (v == null || v == 0) continue;
                        accumulate(agg, keyParts, article, e.getValue(), v);
                    }
                }
            } else {
                // LONG layout: header row 0, data from row 1: Article | Chemical | Kg per pair
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    String article = getString(row.getCell(0));
                    String chemical = getString(row.getCell(1));
                    Double v = getDouble(row.getCell(2));
                    if (article == null || article.isBlank()
                            || chemical == null || chemical.isBlank()) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }
                    if (v == null) {
                        result.getErrors().add("Row " + (i + 1) + ": Kg per pair is required/invalid");
                        continue;
                    }
                    accumulate(agg, keyParts, article.trim(), chemical.trim(), v);
                }
            }

            for (Map.Entry<String, double[]> e : agg.entrySet()) {
                String[] parts = keyParts.get(e.getKey());
                double avg = e.getValue()[0] / e.getValue()[1];
                Optional<VocStandardRate> existing =
                        rateRepo.findByArticleNoAndChemicalCode(parts[0], parts[1]);
                VocStandardRate r = existing.orElseGet(VocStandardRate::new);
                r.setArticleNo(parts[0]);
                r.setChemicalCode(parts[1]);
                r.setKgPerPair(avg);
                rateRepo.save(r);
                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        systemLogService.logAction("IMPORT_VOC_RECIPE", result.toFlashMessage());
        return result;
    }

    private static void accumulate(Map<String, double[]> agg, Map<String, String[]> keyParts,
                                   String article, String chemical, double value) {
        String key = article + "||" + chemical;
        double[] sc = agg.computeIfAbsent(key, k -> new double[2]);
        sc[0] += value;
        sc[1] += 1;
        keyParts.putIfAbsent(key, new String[]{article, chemical});
    }

    // ════════════════════════════════════════════════════════════════════════
    // Monthly report
    // ════════════════════════════════════════════════════════════════════════

    public List<String> getAllMonths() {
        return consumptionRepo.findDistinctMonths();
    }

    /** Distinct sections from production data — feeds the entry dropdown; defaults to SEW. */
    public List<String> getSections() {
        List<String> sections = productionRepo.findDistinctSections();
        if (sections.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            fallback.add(DEFAULT_SECTION);
            return fallback;
        }
        return sections;
    }

    /** Distinct lines from production data — feeds the entry line suggestions (datalist). */
    public List<String> getLines() {
        return productionRepo.findDistinctLines();
    }

    public VocReportDto getMonthlyReport(String month) {
        VocReportDto report = new VocReportDto();
        List<String> months = getAllMonths();
        report.setAllMonths(months);

        if (month == null || month.isBlank()) {
            month = months.isEmpty() ? null : months.get(0);
        }
        report.setSelectedMonth(month);
        if (month == null) return report;

        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (RuntimeException e) {
            return report;
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<VocConsumption> consumptions = consumptionRepo
                .findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(from, to);
        if (consumptions.isEmpty()) return report;

        // Chemical master, keyed by lower-case code
        Map<String, VocChemical> chemMap = new HashMap<>();
        for (VocChemical c : chemicalRepo.findAll()) {
            chemMap.put(c.getCode().toLowerCase(), c);
        }

        // Output per (date, section, line)
        Map<String, Integer> outputMap = new HashMap<>();
        for (Object[] o : productionRepo.sumOutputByDateSectionLine(from, to)) {
            LocalDate d = (LocalDate) o[0];
            String sec = (String) o[1];
            String ln = (String) o[2];
            long out = o[3] != null ? ((Number) o[3]).longValue() : 0L;
            outputMap.put(outputKey(d, sec, ln), (int) out);
        }

        Map<String, VocReportRowDto> rowMap = new LinkedHashMap<>();
        Map<String, VocChemicalSummaryDto> chemSummary = new LinkedHashMap<>();

        for (VocConsumption c : consumptions) {
            VocChemical chem = chemMap.get(c.getChemicalCode().toLowerCase());
            double factor = chem != null ? chem.getVocFactor() : 0.0;
            double price = (chem != null && chem.getPricePerKg() != null) ? chem.getPricePerKg() : 0.0;
            boolean water = chem != null && chem.isWaterBase();

            double qty = c.getQuantityKg();
            double net = (qty - c.getReuseKg()) * factor;
            double cost = qty * price;

            // Per (date, line) row
            String rk = c.getProductionDate() + "|" + c.getLine();
            VocReportRowDto row = rowMap.computeIfAbsent(rk, k -> {
                VocReportRowDto r = new VocReportRowDto();
                r.setDate(c.getProductionDate());
                r.setLine(c.getLine());
                r.setOutput(outputMap.getOrDefault(
                        outputKey(c.getProductionDate(), c.getSection(), c.getLine()), 0));
                return r;
            });
            row.setVocKg(row.getVocKg() + net);
            row.setCost(row.getCost() + cost);
            if (water) row.setWaterKg(row.getWaterKg() + qty);
            else       row.setSolventKg(row.getSolventKg() + qty);

            // Per chemical summary
            String ck = c.getChemicalCode().toLowerCase();
            VocChemicalSummaryDto cs = chemSummary.computeIfAbsent(ck, k -> {
                VocChemicalSummaryDto s = new VocChemicalSummaryDto();
                s.setCode(c.getChemicalCode());
                s.setMaterialType(chem != null ? chem.getMaterialType() : "?");
                s.setClassification(chem != null ? chem.getClassification() : null);
                return s;
            });
            cs.setQuantityKg(cs.getQuantityKg() + qty);
            cs.setVocKg(cs.getVocKg() + net);
            cs.setCost(cs.getCost() + cost);

            // Headline totals
            report.setTotalVocKg(report.getTotalVocKg() + net);
            report.setTotalCost(report.getTotalCost() + cost);
            if (water) report.setTotalWaterKg(report.getTotalWaterKg() + qty);
            else       report.setTotalSolventKg(report.getTotalSolventKg() + qty);
        }

        // Total output = distinct (date, line) outputs that actually had consumption
        int totalOutput = 0;
        for (VocReportRowDto r : rowMap.values()) totalOutput += r.getOutput();
        report.setTotalOutput(totalOutput);

        report.setRows(new ArrayList<>(rowMap.values()));
        report.setChemicals(new ArrayList<>(chemSummary.values()));

        buildReconciliation(report, consumptions, chemMap, from, to);
        return report;
    }

    private static String outputKey(LocalDate d, String section, String line) {
        return d + "|" + section + "|" + line;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Reconciliation pivot — Actual vs Allowance (the "%" sheet)
    //   allowance(date, chem) = Σ_lines output × recipe(primaryArticle, chem)
    //   ratio(date, chem)     = actualKg / allowanceKg          (% sheet ratio row)
    //   diff(date, chem)      = allowanceKg − actualKg          (% sheet "Difference" row)
    // No buffer: the % sheet's allowance comes from the numbered sheets 1–21, which
    // compute output × standard recipe with NO markup. The "1.1" in the workbook is
    // only the standalone CALCULATOR scratch sheet (nothing reads it) and the ">110%"
    // highlight threshold — neither is part of the displayed allowance/ratio.
    // Aggregated across lines per date; scoped to the sections present in consumption.
    // ════════════════════════════════════════════════════════════════════════

    private void buildReconciliation(VocReportDto report, List<VocConsumption> consumptions,
                                     Map<String, VocChemical> chemMap, LocalDate from, LocalDate to) {
        // Sections that VOC actually tracks this month
        Set<String> consSections = new HashSet<>();
        for (VocConsumption c : consumptions) consSections.add(c.getSection());

        // ── Allowance + output per date, from production (output × recipe × 1.1) ──
        List<DailyProduction> production =
                productionRepo.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to);

        // recipe map: cleanedArticle (lower) -> (chemicalCode -> kgPerPair). Recipe table
        // is small, so load all and match in-memory (avoids a fragile In+IgnoreCase query).
        Map<String, Map<String, Double>> recipe = new HashMap<>();
        for (VocStandardRate r : rateRepo.findAll()) {
            String a = SectionMetrics.ArticleKey.parse(r.getArticleNo()).cleanedArticle();
            if (a == null) continue;
            recipe.computeIfAbsent(a.toLowerCase(), k -> new HashMap<>())
                    .put(r.getChemicalCode(), r.getKgPerPair());
        }

        Map<LocalDate, Integer> outputByDate = new TreeMap<>();
        Map<LocalDate, Map<String, Double>> allowance = new TreeMap<>();
        Set<String> chemsPresent = new TreeSet<>();

        for (DailyProduction p : production) {
            if (!consSections.contains(p.getSection())) continue;
            LocalDate d = p.getProductionDate();
            int out = p.getTotalOutput() != null ? p.getTotalOutput() : 0;
            outputByDate.merge(d, out, Integer::sum);

            String a = primaryArticle(p);
            Map<String, Double> rec = a != null ? recipe.get(a.toLowerCase()) : null;
            if (rec != null) {
                for (Map.Entry<String, Double> e : rec.entrySet()) {
                    double allowKg = out * e.getValue();
                    allowance.computeIfAbsent(d, k -> new HashMap<>()).merge(e.getKey(), allowKg, Double::sum);
                    chemsPresent.add(e.getKey());
                }
            }
        }

        // ── Actual + net VOC per date, from consumption ──
        Map<LocalDate, Map<String, Double>> actual = new TreeMap<>();
        Map<LocalDate, Double> netVocByDate = new TreeMap<>();
        for (VocConsumption c : consumptions) {
            VocChemical chem = chemMap.get(c.getChemicalCode().toLowerCase());
            double factor = chem != null ? chem.getVocFactor() : 0.0;
            double qty = c.getQuantityKg();
            actual.computeIfAbsent(c.getProductionDate(), k -> new HashMap<>())
                    .merge(c.getChemicalCode(), qty, Double::sum);
            netVocByDate.merge(c.getProductionDate(), (qty - c.getReuseKg()) * factor, Double::sum);
            chemsPresent.add(c.getChemicalCode());
        }

        // ── Build pivot rows (dates) × columns (chemicals present) ──
        report.setReconcileChemicals(new ArrayList<>(chemsPresent));

        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(allowance.keySet());
        allDates.addAll(actual.keySet());

        List<VocReconcileRowDto> rows = new ArrayList<>();
        for (LocalDate d : allDates) {
            VocReconcileRowDto row = new VocReconcileRowDto();
            row.setDate(d);
            row.setOutput(outputByDate.getOrDefault(d, 0));
            row.setVocGrams(netVocByDate.getOrDefault(d, 0.0) * 1000.0);

            Map<String, Double> dayActual = actual.getOrDefault(d, Map.of());
            Map<String, Double> dayAllow = allowance.getOrDefault(d, Map.of());
            for (String chem : chemsPresent) {
                double act = dayActual.getOrDefault(chem, 0.0);
                Double allow = dayAllow.get(chem);
                boolean hasAllow = allow != null && allow > 0;
                if (act == 0 && !hasAllow) continue;   // empty cell

                VocReconcileCellDto cell = new VocReconcileCellDto();
                cell.setActualKg(act);
                cell.setAllowanceKg(hasAllow ? allow : 0.0);
                cell.setDiffKg(cell.getAllowanceKg() - act);   // % sheet "Difference"
                if (hasAllow && act > 0) {
                    double ratio = act / allow;
                    cell.setRatio(ratio);
                    cell.setStatus(ratio > 1.0 ? "OVER" : "OK");
                } else if (hasAllow) {
                    cell.setStatus("NC");   // allowance but nothing consumed
                } else {
                    cell.setStatus("NA");   // consumed but no recipe/allowance
                }
                row.getCells().put(chem, cell);
            }
            rows.add(row);
        }
        report.setReconcileRows(rows);
    }

    /** Dominant article of a production row: first non-blank detail articleNo, cleaned. */
    private String primaryArticle(DailyProduction p) {
        if (p.getDetails() == null) return null;
        for (DailyProductionDetail d : p.getDetails()) {
            String a = d.getArticleNo();
            if (a != null && !a.trim().isEmpty()) {
                return SectionMetrics.ArticleKey.parse(a).cleanedArticle();
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — chemical master ("R" sheet layout)
    // Columns: Code | Material Type | Classification | Manufacturer | VOC Factor | Price/kg
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public WeeklyImportResultDto importChemicalsFromExcel(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

                String code = getString(row.getCell(0));
                if (code == null || code.isBlank()) { result.setSkipped(result.getSkipped() + 1); continue; }
                code = code.trim();

                Double factor = getDouble(row.getCell(4));
                if (factor != null && (factor < 0 || factor > 1)) {
                    result.getErrors().add("Row " + (i + 1) + ": VOC factor must be between 0 and 1");
                    continue;
                }

                Optional<VocChemical> existing = chemicalRepo.findByCodeIgnoreCase(code);
                VocChemical c = existing.orElseGet(VocChemical::new);
                c.setCode(code);
                c.setMaterialType(normalizeMaterialType(getString(row.getCell(1))));
                c.setClassification(getString(row.getCell(2)));
                c.setManufacturer(getString(row.getCell(3)));
                c.setVocFactor(factor != null ? factor : 0.0);
                c.setPricePerKg(getDouble(row.getCell(5)));
                if (c.getActive() == null) c.setActive(true);
                chemicalRepo.save(c);

                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        systemLogService.logAction("IMPORT_VOC_CHEMICALS", result.toFlashMessage());
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — consumption ("Actual" sheet layout)
    // Columns: Date | Section | Line | Chemical | Quantity Kg | Reuse Kg
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public WeeklyImportResultDto importConsumptionFromExcel(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

                LocalDate date = getDate(row.getCell(0));
                String line = getString(row.getCell(2));
                String chemical = getString(row.getCell(3));

                if (date == null && (line == null || line.isBlank())
                        && (chemical == null || chemical.isBlank())) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (date == null) {
                    result.getErrors().add("Row " + (i + 1) + ": Date is required/invalid");
                    continue;
                }
                if (line == null || line.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Line is required");
                    continue;
                }
                if (chemical == null || chemical.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Chemical is required");
                    continue;
                }

                String section = getString(row.getCell(1));
                if (section == null || section.isBlank()) section = DEFAULT_SECTION;
                else section = section.trim();
                line = line.trim();
                chemical = chemical.trim();

                Double qty = getDouble(row.getCell(4));
                Double reuse = getDouble(row.getCell(5));
                if (qty != null && qty < 0) {
                    result.getErrors().add("Row " + (i + 1) + ": Quantity cannot be negative");
                    continue;
                }

                Optional<VocConsumption> existing = consumptionRepo
                        .findByProductionDateAndSectionAndLineAndChemicalCode(date, section, line, chemical);
                VocConsumption c = existing.orElseGet(VocConsumption::new);
                c.setProductionDate(date);
                c.setSection(section);
                c.setLine(line);
                c.setChemicalCode(chemical);
                c.setQuantityKg(qty != null ? qty : 0.0);
                c.setReuseKg(reuse != null ? reuse : 0.0);
                consumptionRepo.save(c);

                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        systemLogService.logAction("IMPORT_VOC_CONSUMPTION", result.toFlashMessage());
        return result;
    }

    // ── Cell parsing helpers ──────────────────────────────────────────────────

    /** Effective type — resolves a formula cell to its cached result type. */
    private static CellType effectiveType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    private String getString(Cell cell) {
        if (cell == null) return null;
        switch (effectiveType(cell)) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) return String.valueOf((long) v);
                return String.valueOf(v);
            default:      return null;
        }
    }

    private Double getDouble(Cell cell) {
        if (cell == null) return null;
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC) return cell.getNumericCellValue();
        if (type == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return null;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private LocalDate getDate(Cell cell) {
        if (cell == null) return null;
        CellType type = effectiveType(cell);
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (type == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return null;
            try { return LocalDate.parse(s); } catch (RuntimeException e) { return null; }
        }
        return null;
    }
}
