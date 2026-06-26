package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.dto.VocActualRowDto;
import thienloc.manage.dto.VocChemicalSummaryDto;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReconcileRowDto;
import thienloc.manage.dto.VocReconcileWeekDto;
import thienloc.manage.dto.VocRecipeGridDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocReportFilter;
import thienloc.manage.dto.VocReportRowDto;
import thienloc.manage.dto.VocSubconReportDto;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.VocChemical;
import thienloc.manage.entity.VocConsumption;
import thienloc.manage.entity.VocRecipeArticle;
import thienloc.manage.entity.VocStandardRate;
import thienloc.manage.entity.VocSubconDetail;
import thienloc.manage.entity.VocSubconEntry;
import thienloc.manage.entity.VocProduction;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.VocChemicalRepository;
import thienloc.manage.repository.VocConsumptionRepository;
import thienloc.manage.repository.VocProductionRepository;
import thienloc.manage.repository.VocRecipeArticleRepository;
import thienloc.manage.repository.VocStandardRateRepository;
import thienloc.manage.repository.VocSubconEntryRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VOC module service: chemical-master CRUD, daily-consumption CRUD, the monthly
 * report (VOC kg / g-per-pair / water-based % / cost), and Excel import for both
 * the chemical master ("R" sheet) and consumption ("Actual" sheet).
 *
 * Core formula: netVocKg = (quantityKg - reuseKg) * chemical.vocFactor.
 * Output for VOC g/pair comes from the existing daily_production table.
 */
@Service
@RequiredArgsConstructor
public class VocService {

    private final VocChemicalRepository chemicalRepo;
    private final VocConsumptionRepository consumptionRepo;
    private final VocStandardRateRepository rateRepo;
    private final VocRecipeArticleRepository recipeArticleRepo;
    private final VocSubconEntryRepository subconRepo;
    private final VocProductionRepository vocProductionRepo;
    private final DailyProductionRepository productionRepo;
    private final SystemLogService systemLogService;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    public static final String DEFAULT_SECTION = "SEW";

    /**
     * Dry-run an import to get exact NEW(inserted)/UPDATE(updated)/skipped/error counts
     * WITHOUT persisting — runs the real importer inside a rolled-back transaction so the
     * preview→confirm screen reuses the exact same parser as commit (no duplicated parsing).
     * {@code type} = consumption | chemicals | recipe | subcon.
     */
    public WeeklyImportResultDto previewImport(MultipartFile file, String type, String section) {
        org.springframework.transaction.support.TransactionTemplate tt =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        return tt.execute(status -> {
            status.setRollbackOnly();   // never persist a preview
            try {
                return runImport(file, type, section);
            } catch (IOException e) {
                throw new IllegalArgumentException("Không đọc được file: " + e.getMessage(), e);
            }
        });
    }

    /** Commit the real import (persists, atomically). Used after the user confirms the preview. */
    @Transactional
    public WeeklyImportResultDto commitImport(MultipartFile file, String type, String section) throws IOException {
        return runImport(file, type, section);
    }

    private WeeklyImportResultDto runImport(MultipartFile file, String type, String section) throws IOException {
        return switch (type) {
            case "consumption" -> importConsumptionFromExcel(file);
            case "chemicals"   -> importChemicalsFromExcel(file);
            case "recipe"      -> importRecipe(file, section);   // section: null → auto-detect from Data sheet
            case "subcon"      -> importSubconFromExcel(file);
            case "production"  -> importVocProduction(file);     // "Data" sheet → allowance source
            default -> throw new IllegalArgumentException("Loại import không hợp lệ: " + type);
        };
    }

    /** Chemical-code aliases: the CEMENT sheets abbreviate some master codes
     *  (e.g. "705AN" vs the master/recipe "GH-705AN"). Without this they land in a
     *  separate column / show NA. Keyed upper-case. */
    private static final Map<String, String> CHEM_ALIAS = Map.of("705AN", "GH-705AN");

    /** Resolve a known sheet alias to the master code (e.g. 705AN → GH-705AN). */
    public static String aliasChem(String code) {
        if (code == null) return null;
        String c = code.trim();
        return CHEM_ALIAS.getOrDefault(c.toUpperCase(), c);
    }

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

    /**
     * Build the "Actual" log for a date: the consumption rows joined with the
     * chemical master so each row carries the derived block (material base,
     * classification, manufacturer, VOC factor) and the emitted VOC. When
     * {@code line} is blank the whole day across every line is returned.
     */
    public List<VocActualRowDto> getActualRows(LocalDate date, String section, String line) {
        List<VocConsumption> list = (line == null || line.isBlank())
                ? consumptionRepo.findByProductionDateAndSectionOrderByLineAscChemicalCodeAsc(date, section)
                : consumptionRepo.findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(date, section, line);
        if (list.isEmpty()) return List.of();

        Map<String, VocChemical> master = new HashMap<>();
        for (VocChemical c : chemicalRepo.findAllByOrderByCodeAsc()) {
            master.put(c.getCode().toUpperCase(), c);
        }

        List<VocActualRowDto> rows = new ArrayList<>();
        for (VocConsumption c : list) {
            String key = c.getChemicalCode() == null ? "" : c.getChemicalCode().toUpperCase();
            VocChemical ch = master.get(key);
            double factor = (ch != null && ch.getVocFactor() != null) ? ch.getVocFactor() : 0.0;
            double qty = c.getQuantityKg() != null ? c.getQuantityKg() : 0.0;
            double reuse = c.getReuseKg() != null ? c.getReuseKg() : 0.0;
            rows.add(VocActualRowDto.builder()
                    .id(c.getId())
                    .date(c.getProductionDate())
                    .line(c.getLine())
                    .chemicalCode(c.getChemicalCode())
                    .materialType(ch != null ? baseLabel(ch.getMaterialType()) : "")
                    .classification(ch != null ? nz(ch.getClassification()) : "")
                    .manufacturer(ch != null ? nz(ch.getManufacturer()) : "")
                    .vocFactor(factor)
                    .quantityKg(qty)
                    .reuseKg(reuse)
                    .vocEmittedKg(factor * (qty - reuse))
                    .build());
        }
        return rows;
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

    /** Result of a confirm-aware batch save: how many were written + which natural keys
     *  already existed and were skipped (so the caller can ask before overwriting). */
    public record BatchResult(int saved, List<String> conflicts) {}

    /** True if a consumption row already exists for this (date, section, line, chemical). */
    public boolean consumptionExists(LocalDate date, String section, String line, String chem) {
        String sec = (section == null || section.isBlank()) ? DEFAULT_SECTION : section.trim();
        return consumptionRepo.findByProductionDateAndSectionAndLineAndChemicalCode(
                date, sec, line != null ? line.trim() : null, chem != null ? chem.trim() : null).isPresent();
    }

    /** Batch upsert (legacy: always overwrites). Returns the number saved. */
    @Transactional
    public int saveConsumptionBatch(LocalDate date, String section, String line,
                                    List<String> chemicalCode, List<Double> quantityKg, List<Double> reuseKg) {
        return saveConsumptionBatch(date, section, line, chemicalCode, quantityKg, reuseKg, true).saved();
    }

    /** Batch save with confirm-before-overwrite. New rows are saved immediately; rows whose
     *  natural key already exists are skipped and reported as conflicts unless {@code overwrite}.
     *  Rows with neither quantity nor reuse are skipped entirely. */
    @Transactional
    public BatchResult saveConsumptionBatch(LocalDate date, String section, String line,
                                    List<String> chemicalCode, List<Double> quantityKg, List<Double> reuseKg,
                                    boolean overwrite) {
        List<String> conflicts = new ArrayList<>();
        if (date == null || line == null || line.isBlank() || chemicalCode == null) return new BatchResult(0, conflicts);
        String sec = (section == null || section.isBlank()) ? DEFAULT_SECTION : section.trim();
        String ln = line.trim();
        int saved = 0;
        for (int i = 0; i < chemicalCode.size(); i++) {
            String chem = chemicalCode.get(i);
            if (chem == null || chem.isBlank()) continue;
            Double qty = (quantityKg != null && i < quantityKg.size()) ? quantityKg.get(i) : null;
            Double reuse = (reuseKg != null && i < reuseKg.size()) ? reuseKg.get(i) : null;
            if ((qty == null || qty == 0.0) && (reuse == null || reuse == 0.0)) continue;   // blank row
            String chemT = chem.trim();
            if (!overwrite && consumptionExists(date, sec, ln, chemT)) {
                conflicts.add(chemT);
                continue;
            }
            VocConsumption c = new VocConsumption();
            c.setProductionDate(date);
            c.setSection(sec);
            c.setLine(ln);
            c.setChemicalCode(chemT);
            c.setQuantityKg(qty != null ? qty : 0.0);
            c.setReuseKg(reuse != null ? reuse : 0.0);
            saveConsumption(c);
            saved++;
        }
        return new BatchResult(saved, conflicts);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Standard recipe CRUD (the VOC "DB")
    // ════════════════════════════════════════════════════════════════════════

    /** Page size for the recipe list — avoids loading the whole table into memory. */
    public static final int RECIPE_PAGE_SIZE = 25;

    public Optional<VocStandardRate> findRateById(Long id) {
        return rateRepo.findById(id);
    }

    /** Upsert on (articleNo, chemicalCode). When a reciprocal formula is supplied
     *  it drives kgPerPair; otherwise the submitted kgPerPair is kept as-is. */
    public VocStandardRate saveRate(VocStandardRate r) {
        if (r.getSection() == null || r.getSection().isBlank()) r.setSection(DEFAULT_SECTION);
        r.setArticleNo(r.getArticleNo() != null ? r.getArticleNo().trim() : null);
        r.setChemicalCode(r.getChemicalCode() != null ? r.getChemicalCode().trim() : null);
        String formula = normalizeFormula(r.getFormula());
        r.setFormula(formula);
        if (formula != null) {
            r.setKgPerPair(evalFormula(formula));
        } else if (r.getKgPerPair() == null) {
            r.setKgPerPair(0.0);
        }

        if (r.getId() == null) {
            Optional<VocStandardRate> existing =
                    rateRepo.findBySectionAndArticleNoAndChemicalCode(r.getSection(), r.getArticleNo(), r.getChemicalCode());
            if (existing.isPresent()) {
                VocStandardRate e = existing.get();
                e.setKgPerPair(r.getKgPerPair());
                e.setFormula(formula);
                r = e;
            }
        }
        boolean isNew = (r.getId() == null);
        VocStandardRate saved = rateRepo.save(r);
        ensureArticle(saved.getArticleNo());
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

    // ── Wide "DB" matrix (article × chemical) ─────────────────────────────────

    /** One page of recipe articles (the matrix rows), searchable by article,
     *  model code, or model name. */
    public Page<VocRecipeArticle> getRecipeArticlesPage(int page, String q) {
        Pageable pageable = PageRequest.of(Math.max(0, page), RECIPE_PAGE_SIZE, Sort.by("articleNo"));
        if (q != null && !q.isBlank()) {
            String t = q.trim();
            return recipeArticleRepo
                    .findByArticleNoContainingIgnoreCaseOrModelCodeContainingIgnoreCaseOrModelNameContainingIgnoreCase(
                            t, t, t, pageable);
        }
        return recipeArticleRepo.findAll(pageable);
    }

    /** Chemical column order of the workbook "DB"/EFF sheet (adhesives → primers →
     *  hot-melts …); codes absent from that list fall to the end alphabetically. */
    private static final List<String> CHEM_ORDER = List.of(
            "Latex", "H550", "577NT", "577NT3", "710NT1", "94LNT", "98H1", "98NH1",
            "GH-7055", "GH-705A", "GH-708", "72KMN", "5611", "5612B", "733P",
            "GH-705AN", "311A5", "6002VN", "C37", "NR-18", "202A", "7281");

    /** Per-chemical header tiers {manufacturer, base, type} from the workbook "R"/"DB"
     *  sheets — kept static so the header is identical to Excel regardless of seeding. */
    private static final Map<String, String[]> CHEM_META = Map.ofEntries(
            Map.entry("Latex",    new String[]{"Dinh Thinh", "Water Base",   "Adhesive"}),
            Map.entry("H550",     new String[]{"Everend",    "Water Base",   "Hot melt"}),
            Map.entry("577NT",    new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("577NT3",   new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("710NT1",   new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("94LNT",    new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("98H1",     new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("98NH1",    new String[]{"Greco",      "Solvent Base", "Adhesive"}),
            Map.entry("GH-7055",  new String[]{"Green life", "Water Base",   "Hot melt"}),
            Map.entry("GH-705A",  new String[]{"Green life", "Water Base",   "Hot melt"}),
            Map.entry("GH-708",   new String[]{"Green life", "Water Base",   "Hot melt"}),
            Map.entry("72KMN",    new String[]{"Nanpao",     "Solvent Base", "Adhesive"}),
            Map.entry("5611",     new String[]{"Upaco",      "Water Base",   "Adhesive"}),
            Map.entry("5612B",    new String[]{"Upaco",      "Water Base",   "Adhesive"}),
            Map.entry("733P",     new String[]{"Henkel",     "Water Base",   "Adhesive"}),
            Map.entry("GH-705AN", new String[]{"Green life", "Water Base",   "Hot melt"}),
            Map.entry("311A5",    new String[]{"Greco",      "Solvent Base", "Primer"}),
            Map.entry("6002VN",   new String[]{"Greco",      "Water Base",   "Primer"}),
            Map.entry("C37",      new String[]{"Trust-ink",  "Solvent Base", "Primer"}),
            Map.entry("NR-18",    new String[]{"Bang Duc",   "Water Base",   "Hot melt"}),
            Map.entry("202A",     new String[]{"Greco",      "Solvent Base", "Primer"}),
            Map.entry("7281",     new String[]{"Cherng Tay", "Water Base",   "Hot melt"}));

    /** Chemical unit price ($/kg) from the workbook "R" sheet — drives the cost block
     *  (cost/pair = kg/pair × price). C37 has no listed price so it is omitted. */
    private static final Map<String, Double> CHEM_PRICE = Map.ofEntries(
            Map.entry("Latex", 1.383689), Map.entry("H550", 3.685),
            Map.entry("577NT", 3.47),     Map.entry("577NT3", 3.47),
            Map.entry("710NT1", 2.18),    Map.entry("94LNT", 2.78),
            Map.entry("98H1", 2.71),      Map.entry("98NH1", 2.71),
            Map.entry("GH-7055", 3.90),   Map.entry("GH-705A", 3.70),
            Map.entry("GH-708", 4.44),    Map.entry("72KMN", 2.97),
            Map.entry("5611", 1.155),     Map.entry("5612B", 1.167778),
            Map.entry("733P", 0.480585),  Map.entry("GH-705AN", 3.00),
            Map.entry("311A5", 2.73),     Map.entry("6002VN", 6.23),
            Map.entry("NR-18", 2.59),     Map.entry("202A", 2.21),
            Map.entry("7281", 6.965));

    /** The 22 workbook chemicals in DB-sheet order (for the wide import template). */
    public List<String> getRecipeChemicalOrder() { return CHEM_ORDER; }

    /** Header tiers {manufacturer, base, type} for a chemical, or null if unknown. */
    public String[] getChemicalMeta(String code) { return CHEM_META.get(code); }

    /** Columns for one section: SEW leads with the 22 canonical workbook chemicals (Excel
     *  order); every section then appends the codes its own recipe rows actually use. */
    private List<String> orderedChemicalColumns(String section) {
        List<String> cols = new ArrayList<>();
        if (DEFAULT_SECTION.equalsIgnoreCase(section)) cols.addAll(CHEM_ORDER);
        for (String code : rateRepo.findDistinctChemicalCodesBySection(section)) {
            if (!cols.contains(code)) cols.add(code);
        }
        return cols;
    }

    private static String baseLabel(String materialType) {
        if ("WATER".equalsIgnoreCase(materialType)) return "Water Base";
        if ("SOLVENT".equalsIgnoreCase(materialType)) return "Solvent Base";
        return "";
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Build the wide matrix for the given page of articles: chemical codes as the
     *  columns, each cell carrying the evaluated kg/pair and its raw formula. */
    public VocRecipeGridDto buildRecipeGrid(List<VocRecipeArticle> articles, String section) {
        List<String> chemicals = orderedChemicalColumns(section);

        // Upper header tiers (manufacturer → base → type) and unit price per column.
        // The canonical 22 use the static maps (guaranteed to match Excel); any extra
        // chemical falls back to the master ("R") so new columns carry their own header.
        Map<String, VocChemical> master = new HashMap<>();
        for (VocChemical c : chemicalRepo.findAll()) {
            if (c.getCode() != null) master.put(c.getCode().trim().toUpperCase(), c);
        }
        List<String> manu = new ArrayList<>(), base = new ArrayList<>(), type = new ArrayList<>();
        Map<String, Double> priceByChem = new LinkedHashMap<>();
        for (String code : chemicals) {
            String[] m = CHEM_META.get(code);
            VocChemical mc = master.get(code.toUpperCase());
            if (m != null) {
                manu.add(m[0]); base.add(m[1]); type.add(m[2]);
            } else {
                manu.add(mc != null ? nz(mc.getManufacturer()) : "");
                base.add(mc != null ? baseLabel(mc.getMaterialType()) : "");
                type.add(mc != null ? nz(mc.getClassification()) : "");
            }
            Double p = CHEM_PRICE.get(code);
            if (p == null && mc != null) p = mc.getPricePerKg();
            if (p != null) priceByChem.put(code, p);
        }

        List<VocRecipeGridDto.Row> rows = new ArrayList<>();
        if (!articles.isEmpty()) {
            List<String> ids = articles.stream().map(VocRecipeArticle::getArticleNo).toList();
            Map<String, List<VocStandardRate>> byArticle = rateRepo.findByArticleNoIn(ids).stream()
                    .filter(r -> section.equalsIgnoreCase(r.getSection()))
                    .collect(Collectors.groupingBy(VocStandardRate::getArticleNo));
            for (VocRecipeArticle a : articles) {
                Map<String, VocRecipeGridDto.Cell> cells = new HashMap<>();
                Map<String, Double> costs = new HashMap<>();
                double totalCost = 0.0;
                for (VocStandardRate r : byArticle.getOrDefault(a.getArticleNo(), List.of())) {
                    cells.put(r.getChemicalCode(), VocRecipeGridDto.Cell.builder()
                            .id(r.getId()).kgPerPair(r.getKgPerPair()).formula(r.getFormula()).build());
                    Double price = priceByChem.get(r.getChemicalCode());
                    if (price != null && r.getKgPerPair() != null) {
                        double cost = r.getKgPerPair() * price;
                        costs.put(r.getChemicalCode(), cost);
                        totalCost += cost;
                    }
                }
                rows.add(VocRecipeGridDto.Row.builder()
                        .articleNo(a.getArticleNo()).modelCode(a.getModelCode()).modelName(a.getModelName())
                        .baseE(a.getBaseE()).baseF(a.getBaseF())
                        .cells(cells).costs(costs).totalCost(totalCost).build());
            }
        }
        return VocRecipeGridDto.builder()
                .chemicals(chemicals)
                .manufacturers(manu)
                .bases(base)
                .types(type)
                .prices(priceByChem)
                .rows(rows).build();
    }

    /** Save a whole "DB" row in one shot: the article identity plus every chemical
     *  formula. A blank formula removes that chemical's rate; a non-blank one upserts
     *  it (the reciprocal expression drives kg/pair). Mirrors editing a DB-sheet row. */
    @Transactional
    public void saveRecipeModel(String section, String articleNo, String modelCode, String modelName,
                                Double baseE, Double baseF,
                                List<String> codes, List<String> formulas) {
        if (articleNo == null || articleNo.isBlank())
            throw new IllegalArgumentException("Article # is required");
        String art = articleNo.trim();
        String sec = (section == null || section.isBlank()) ? DEFAULT_SECTION : section.trim();
        upsertArticle(VocRecipeArticle.builder()
                .articleNo(art).modelCode(modelCode).modelName(modelName)
                .baseE(baseE).baseF(baseF).build());

        if (codes != null) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i) == null ? null : codes.get(i).trim();
                if (code == null || code.isBlank()) continue;
                String norm = normalizeFormula(formulas != null && i < formulas.size() ? formulas.get(i) : null);
                Optional<VocStandardRate> existing = rateRepo.findBySectionAndArticleNoAndChemicalCode(sec, art, code);
                if (norm == null) {
                    existing.ifPresent(rateRepo::delete);          // cleared → remove the rate
                } else {
                    VocStandardRate r = existing.orElseGet(() -> VocStandardRate.builder()
                            .section(sec).articleNo(art).chemicalCode(code).build());
                    r.setFormula(norm);
                    r.setKgPerPair(evalFormula(norm));
                    rateRepo.save(r);
                }
            }
        }
        systemLogService.logAction("SAVE_VOC_MODEL", art);
    }

    /** Upsert an article identity row (model code/name, E/F bases) — logged. */
    public void saveRecipeArticle(VocRecipeArticle a) {
        VocRecipeArticle saved = upsertArticle(a);
        if (saved != null) systemLogService.logAction("SAVE_VOC_ARTICLE", saved.getArticleNo());
    }

    private VocRecipeArticle upsertArticle(VocRecipeArticle a) {
        if (a.getArticleNo() == null || a.getArticleNo().isBlank()) return null;
        String articleNo = a.getArticleNo().trim();
        VocRecipeArticle e = recipeArticleRepo.findById(articleNo)
                .orElseGet(() -> VocRecipeArticle.builder().articleNo(articleNo).build());
        e.setModelCode(blankToNull(a.getModelCode()));
        e.setModelName(blankToNull(a.getModelName()));
        e.setBaseE(a.getBaseE());
        e.setBaseF(a.getBaseF());
        return recipeArticleRepo.save(e);
    }

    /** Create a bare article row so a freshly added cell shows up as a matrix row. */
    private void ensureArticle(String articleNo) {
        if (articleNo == null || articleNo.isBlank()) return;
        if (!recipeArticleRepo.existsById(articleNo)) {
            recipeArticleRepo.save(VocRecipeArticle.builder().articleNo(articleNo).build());
        }
    }

    // ── Reciprocal-formula helpers ────────────────────────────────────────────

    /** Evaluate a "DB"-sheet reciprocal sum like "1/1300+1/1500+1/1400" → kg/pair.
     *  Plain decimals are accepted; '+'-separated terms, each "a/b" or a number.
     *  Whitespace, parentheses and a leading '=' are ignored; 0 for blank/invalid. */
    public static double evalFormula(String expr) {
        String s = normalizeFormula(expr);
        if (s == null) return 0.0;
        double sum = 0.0;
        for (String term : s.split("\\+")) {
            if (term.isEmpty()) continue;
            try {
                int slash = term.indexOf('/');
                if (slash >= 0) {
                    double den = Double.parseDouble(term.substring(slash + 1));
                    if (den != 0) sum += Double.parseDouble(term.substring(0, slash)) / den;
                } else {
                    sum += Double.parseDouble(term);
                }
            } catch (NumberFormatException ignored) {
                // skip a malformed term rather than failing the whole expression
            }
        }
        return sum;
    }

    /** Strip whitespace, parentheses and '='; null when nothing meaningful remains. */
    private static String normalizeFormula(String expr) {
        if (expr == null) return null;
        String s = expr.replaceAll("[\\s()=]", "");
        return s.isEmpty() ? null : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — standard recipe. Auto-detects two layouts:
    //   WIDE  : the original "DB" sheet (anchor "Article #" in col A; chemical
    //           codes 3 rows below starting at column G; article rows below).
    //   LONG  : the template (Article | Chemical | Kg per pair), data from row 2.
    // Duplicate (article, chemical) pairs are averaged (matches Excel AVERAGEIFS).
    // ════════════════════════════════════════════════════════════════════════

    public WeeklyImportResultDto importRecipe(MultipartFile file) throws IOException {
        return importRecipe(file, null);
    }

    /** Section for these rows: {@code sectionOverride} if given, else the workbook's "Data"
     *  sheet Section column (each VOC file is one section), else SEW. Recipe is section-keyed. */
    @Transactional
    public WeeklyImportResultDto importRecipe(MultipartFile file, String sectionOverride) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            String section = recipeSection(wb, sectionOverride);

            // Detect WIDE layout by finding a header cell containing "article" within the first
            // rows/columns; the chemical-code row then sits 3 rows below that anchor. Generalised
            // across sections (SEW/ASSY/SF) — the recipe key and chemical block are resolved below.
            int anchor = -1;
            outer:
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
                Row r = sheet.getRow(i);
                if (r == null) continue;
                for (int c = 0; c <= 7; c++) {
                    if (headerContains(r.getCell(c), "article")) { anchor = i; break outer; }
                }
            }
            Row headerRow = anchor >= 0 ? sheet.getRow(anchor) : null;
            Row chemRow   = anchor >= 0 ? sheet.getRow(anchor + 3) : null;

            // Chemical columns = the code row's populated cells, de-duplicated to the FIRST
            // occurrence of each code. The "DB" sheet repeats the chemical matrix in a second
            // block with different dosages, but the workbook's own per-chemical sheets read only
            // the first block (their AVERAGEIFS point at DB!G/H…), so block 2 must be ignored.
            Map<Integer, String> chemCols = new LinkedHashMap<>();
            Set<String> seenCodes = new HashSet<>();
            if (chemRow != null) {
                for (int c = 0; c < chemRow.getLastCellNum(); c++) {
                    String name = getString(chemRow.getCell(c));
                    if (name != null && !name.isBlank() && seenCodes.add(name.trim()))
                        chemCols.put(c, name.trim());
                }
            }
            boolean wide = anchor >= 0 && !chemCols.isEmpty();

            // Accumulate sum + count per (article, chemical) for averaging
            Map<String, double[]> agg = new LinkedHashMap<>();   // key -> {sum, count}
            Map<String, String[]> keyParts = new HashMap<>();    // key -> {article, chemical}
            Map<String, String> keyFormula = new HashMap<>();    // key -> raw reciprocal formula
            Map<String, VocRecipeArticle> articleMeta = new LinkedHashMap<>(); // article -> identity row

            if (wide) {
                int chemStart = chemCols.keySet().stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
                // Identity columns resolved by header label so shifted layouts still map correctly.
                int patternCol = headerCol(headerRow, "pattern", 0, chemStart);
                int styleCol   = headerCol(headerRow, "style", 0, chemStart);
                int quotaCol   = headerCol(headerRow, "quota", 0, chemStart);
                int baseFCol   = (quotaCol >= 0 && quotaCol + 1 < chemStart) ? quotaCol + 1 : -1;

                // Register any new chemical column in the master, reading its header tiers
                // (manufacturer = anchor row, base = anchor+1, type = anchor+2).
                Row manuRow = sheet.getRow(anchor);
                Row baseRow = sheet.getRow(anchor + 1);
                Row typeRow = sheet.getRow(anchor + 2);
                for (Map.Entry<Integer, String> e : chemCols.entrySet()) {
                    String code = e.getValue();
                    if (chemicalRepo.findByCodeIgnoreCase(code).isPresent()) continue;
                    String bse = getString(baseRow != null ? baseRow.getCell(e.getKey()) : null);
                    chemicalRepo.save(VocChemical.builder()
                            .code(code)
                            .manufacturer(getString(manuRow != null ? manuRow.getCell(e.getKey()) : null))
                            .materialType(normalizeMaterialType(bse))
                            .classification(getString(typeRow != null ? typeRow.getCell(e.getKey()) : null))
                            .vocFactor(0.0).active(true).build());
                }
                for (int i = anchor + 4; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    // Recipe key = column A. The workbook joins production to the recipe on DB!$A,
                    // a per-section formula ("=B" for SEW, LEFT(B,11)/REF for ASSY/SF) whose result
                    // Excel caches; we read that cached value. Blank rows cache as "0".
                    String article = getString(row.getCell(0));
                    if (article == null || article.isBlank() || article.equals("0")) continue;
                    article = article.trim();
                    // Identity columns (model code / name / base counts) by resolved header label.
                    articleMeta.computeIfAbsent(article, k -> VocRecipeArticle.builder()
                            .articleNo(k)
                            .modelCode(patternCol >= 0 ? getString(row.getCell(patternCol)) : null)
                            .modelName(styleCol >= 0 ? getString(row.getCell(styleCol)) : null)
                            .baseE(quotaCol >= 0 ? getDouble(row.getCell(quotaCol)) : null)
                            .baseF(baseFCol >= 0 ? getDouble(row.getCell(baseFCol)) : null)
                            .build());
                    for (Map.Entry<Integer, String> e : chemCols.entrySet()) {
                        Cell cell = row.getCell(e.getKey());
                        Double v = getDouble(cell);
                        if (v == null || v == 0) continue;
                        accumulate(agg, keyParts, article, e.getValue(), v);
                        String f = getFormula(cell);
                        if (f != null) keyFormula.putIfAbsent(article + "||" + e.getValue(), f);
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
                        rateRepo.findBySectionAndArticleNoAndChemicalCode(section, parts[0], parts[1]);
                VocStandardRate r = existing.orElseGet(VocStandardRate::new);
                r.setSection(section);
                r.setArticleNo(parts[0]);
                r.setChemicalCode(parts[1]);
                r.setKgPerPair(avg);
                r.setFormula(keyFormula.get(e.getKey()));
                rateRepo.save(r);
                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
            // Upsert article identity rows (wide layout) or bare stubs (long layout)
            if (!articleMeta.isEmpty()) {
                articleMeta.values().forEach(this::upsertArticle);
            } else {
                keyParts.values().stream().map(p -> p[0]).distinct().forEach(this::ensureArticle);
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

    /** Section for a recipe import: explicit override, else the workbook's "Data" sheet
     *  Section column (each VOC file is one section), else the default. */
    private String recipeSection(Workbook wb, String override) {
        if (override != null && !override.isBlank()) return override.trim();
        Sheet data = wb.getSheet("Data");
        if (data != null) {
            for (int i = 1; i <= Math.min(data.getLastRowNum(), 50); i++) {
                Row r = data.getRow(i);
                String s = r != null ? getString(r.getCell(1)) : null;   // col B = Section
                if (s != null && !s.isBlank()) return s.trim();
            }
        }
        return DEFAULT_SECTION;
    }

    /** Recipe map key: section + cleaned article, both lower-cased. */
    private static String recipeKey(String section, String cleanedArticle) {
        return (section == null ? DEFAULT_SECTION : section).toLowerCase() + "|" + cleanedArticle.toLowerCase();
    }

    /** True if the cell's text contains {@code keyword} (case-insensitive). */
    private boolean headerContains(Cell cell, String keyword) {
        String s = getString(cell);
        return s != null && s.toLowerCase().contains(keyword);
    }

    /** First column (left of the chemical block, excluding the article column) whose header
     *  contains {@code keyword}; -1 if none. Lets identity columns be found regardless of layout. */
    private int headerCol(Row header, String keyword, int excludeCol, int before) {
        if (header == null) return -1;
        int max = Math.min(before, header.getLastCellNum());
        for (int c = 0; c < max; c++) {
            if (c == excludeCol) continue;
            if (headerContains(header.getCell(c), keyword)) return c;
        }
        return -1;
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

    /** Sections that actually own a recipe (SEW/ASSY/SF) — feeds the recipe-page selector. */
    public List<String> getRecipeSections() {
        List<String> sections = rateRepo.findDistinctSections();
        if (sections.isEmpty()) sections = new ArrayList<>(List.of(DEFAULT_SECTION));
        return sections;
    }

    /** Distinct lines from production data — feeds the entry line suggestions (datalist). */
    public List<String> getLines() {
        return productionRepo.findDistinctLines();
    }

    public VocReportDto getMonthlyReport(VocReportFilter filter) {
        if (filter == null) filter = VocReportFilter.ofMonth(null);
        VocReportDto report = new VocReportDto();
        report.setFilter(filter);
        List<String> months = getAllMonths();
        report.setAllMonths(months);

        String month = filter.month();
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
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // Full month first: dropdown options must survive any active filter.
        List<VocConsumption> monthCons = consumptionRepo
                .findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(monthStart, monthEnd);
        Set<String> optLines = new TreeSet<>(), optSections = new TreeSet<>(), optChems = new TreeSet<>();
        Set<Integer> optWeeks = new TreeSet<>();
        for (VocConsumption c : monthCons) {
            optLines.add(c.getLine());
            optSections.add(c.getSection());
            optChems.add(c.getChemicalCode());
            optWeeks.add(weekOfMonth(c.getProductionDate()));
        }
        report.setAllLines(new ArrayList<>(optLines));
        report.setAllSections(new ArrayList<>(optSections));
        report.setAllChemCodes(new ArrayList<>(optChems));
        for (int w : optWeeks) {
            LocalDate ws = monthStart.plusDays((w - 1) * 7L);
            LocalDate we = ws.plusDays(6).isAfter(monthEnd) ? monthEnd : ws.plusDays(6);
            report.getWeekOptions().put(w, DAY_MONTH.format(ws) + "–" + DAY_MONTH.format(we));
        }

        // Effective range = month ∩ from/to ∩ selected week block (like %!$A$1, every
        // filter narrows the data BEFORE aggregation so all tabs stay consistent).
        LocalDate from = monthStart;
        LocalDate to = monthEnd;
        if (filter.from() != null && !filter.from().isBefore(monthStart) && !filter.from().isAfter(monthEnd)) {
            from = filter.from();
        }
        if (filter.to() != null && !filter.to().isAfter(monthEnd) && !filter.to().isBefore(monthStart)) {
            to = filter.to();
        }
        if (filter.week() != null && filter.week() >= 1) {
            LocalDate ws = monthStart.plusDays((filter.week() - 1) * 7L);
            if (!ws.isAfter(monthEnd)) {
                LocalDate we = ws.plusDays(6).isAfter(monthEnd) ? monthEnd : ws.plusDays(6);
                if (ws.isAfter(from)) from = ws;
                if (we.isBefore(to)) to = we;
            }
        }
        if (to.isBefore(from)) return report;

        Set<String> chemFilter = new HashSet<>();
        if (filter.hasChems()) {
            for (String c : filter.chems()) chemFilter.add(c.toLowerCase());
        }

        List<VocConsumption> consumptions = new ArrayList<>();
        for (VocConsumption c : monthCons) {
            LocalDate d = c.getProductionDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            if (filter.hasSection() && !filter.section().equals(c.getSection())) continue;
            if (filter.hasLine() && !filter.line().equals(c.getLine())) continue;
            if (!chemFilter.isEmpty() && !chemFilter.contains(c.getChemicalCode().toLowerCase())) continue;
            consumptions.add(c);
        }
        if (consumptions.isEmpty()) return report;

        // Chemical master, keyed by lower-case code
        Map<String, VocChemical> chemMap = new HashMap<>();
        for (VocChemical c : chemicalRepo.findAll()) {
            chemMap.put(c.getCode().toLowerCase(), c);
        }

        // Output per (date, section, line) from the VOC "Data" sheet (voc_production),
        // the same source the workbook uses. Article shares within a line sum to the line
        // total, so accumulate as double then round once per (date, section, line).
        List<VocProduction> vocProd = vocProductionRepo.findByProductionDateBetween(from, to);
        Map<String, Double> outputAccum = new HashMap<>();
        for (VocProduction p : vocProd) {
            outputAccum.merge(outputKey(p.getProductionDate(), p.getSection(), p.getLine()),
                    p.getOutput() != null ? p.getOutput() : 0.0, Double::sum);
        }
        Map<String, Integer> outputMap = new HashMap<>();
        outputAccum.forEach((k, v) -> outputMap.put(k, (int) Math.round(v)));

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

        buildReconciliation(report, consumptions, vocProd, chemMap, from, to, filter, chemFilter);
        return report;
    }

    private static String outputKey(LocalDate d, String section, String line) {
        return d + "|" + section + "|" + line;
    }

    /** Workbook week block: 7 calendar days anchored at day 1 (1–7 = 1, 8–14 = 2, …). */
    private static int weekOfMonth(LocalDate d) {
        return (d.getDayOfMonth() - 1) / 7 + 1;
    }

    private static final java.time.format.DateTimeFormatter DAY_MONTH =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM");

    // ════════════════════════════════════════════════════════════════════════
    // Reconciliation pivot — Actual vs Allowance (the "%" sheet)
    //   allowance(date, chem) = Σ_lines Σ_slots slot.output × recipe(slot.article, chem)
    //   ratio(date, chem)     = actualKg / allowanceKg          (% sheet ratio row)
    //   diff(date, chem)      = allowanceKg − actualKg          (% sheet "Difference" row)
    // No buffer: the % sheet's allowance comes from the numbered sheets 1–21, which
    // compute Σ(rate × hourly output) with NO markup — so allowance is weighted by the
    // article running in each time slot, not the day's first article. The "1.1" in the
    // workbook is only the standalone CALCULATOR scratch sheet (nothing reads it) and the
    // ">110%" highlight threshold — neither is part of the displayed allowance/ratio.
    // Aggregated across lines per date; scoped to the sections present in consumption.
    // ════════════════════════════════════════════════════════════════════════

    private void buildReconciliation(VocReportDto report, List<VocConsumption> consumptions,
                                     List<VocProduction> production,
                                     Map<String, VocChemical> chemMap, LocalDate from, LocalDate to,
                                     VocReportFilter filter, Set<String> chemFilter) {
        // Sections that VOC actually tracks this month
        Set<String> consSections = new HashSet<>();
        for (VocConsumption c : consumptions) consSections.add(c.getSection());

        // ── Allowance + output per date, from VOC production (output × recipe, no markup) ──
        // voc_production (the workbook's "Data" sheet) already carries the VOC section
        // (SEW/ASSY/SF) and the per-article output share apportioned from the sheet's slot
        // weights, so allowance is a plain Σ output × recipe — matching the numbered sheets.
        Map<String, Map<String, Double>> recipe = loadRecipeMap();

        Map<LocalDate, Double> outputByDate = new TreeMap<>();
        Map<LocalDate, Map<String, Double>> allowance = new TreeMap<>();
        Map<String, Map<String, Double>> allowanceByLine = new TreeMap<>();   // per-line pivot
        Set<String> chemsPresent = new TreeSet<>();

        for (VocProduction p : production) {
            String sec = p.getSection();
            if (!consSections.contains(sec)) continue;
            if (filter.hasSection() && !filter.section().equals(sec)) continue;
            if (filter.hasLine() && !filter.line().equals(p.getLine())) continue;
            LocalDate d = p.getProductionDate();
            double out = p.getOutput() != null ? p.getOutput() : 0.0;
            outputByDate.merge(d, out, Double::sum);

            // A produced article with no recipe contributes 0 (intended — no silent fallback).
            String a = SectionMetrics.ArticleKey.parse(p.getArticleNo()).cleanedArticle();
            Map<String, Double> rec = a != null ? recipe.get(recipeKey(sec, a)) : null;
            if (rec == null) continue;
            for (Map.Entry<String, Double> e : rec.entrySet()) {
                if (!chemFilter.isEmpty() && !chemFilter.contains(e.getKey().toLowerCase())) continue;
                String chem = canonicalChem(e.getKey(), chemMap);
                double allowKg = out * e.getValue();
                allowance.computeIfAbsent(d, k -> new HashMap<>()).merge(chem, allowKg, Double::sum);
                allowanceByLine.computeIfAbsent(p.getLine(), k -> new HashMap<>()).merge(chem, allowKg, Double::sum);
                chemsPresent.add(chem);
            }
        }

        // ── Actual + net VOC per date, from consumption ──
        Map<LocalDate, Map<String, Double>> actual = new TreeMap<>();
        Map<String, Map<String, Double>> actualByLine = new TreeMap<>();   // EFF per-line pivot
        Map<LocalDate, Double> netVocByDate = new TreeMap<>();
        for (VocConsumption c : consumptions) {
            VocChemical chem = chemMap.get(c.getChemicalCode().toLowerCase());
            double factor = chem != null ? chem.getVocFactor() : 0.0;
            double qty = c.getQuantityKg();
            // Canonical code so "LATEX" (entry) and "Latex" (recipe) merge into one
            // column — Excel's SUMIFS is case-insensitive.
            String code = canonicalChem(c.getChemicalCode(), chemMap);
            actual.computeIfAbsent(c.getProductionDate(), k -> new HashMap<>())
                    .merge(code, qty, Double::sum);
            actualByLine.computeIfAbsent(c.getLine(), k -> new HashMap<>()).merge(code, qty, Double::sum);
            netVocByDate.merge(c.getProductionDate(), (qty - c.getReuseKg()) * factor, Double::sum);
            chemsPresent.add(code);
        }

        // ── Build pivot rows (dates) × columns (chemicals present) ──
        report.setReconcileChemicals(new ArrayList<>(chemsPresent));

        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(allowance.keySet());
        allDates.addAll(actual.keySet());

        // Date rows grouped into the workbook's 7-day blocks (days 1–7, 8–14, …)
        Map<Integer, List<VocReconcileRowDto>> byWeek = new TreeMap<>();
        for (LocalDate d : allDates) {
            VocReconcileRowDto row = new VocReconcileRowDto();
            row.setDate(d);
            row.setOutput((int) Math.round(outputByDate.getOrDefault(d, 0.0)));
            row.setVocGrams(netVocByDate.getOrDefault(d, 0.0) * 1000.0);

            Map<String, Double> dayActual = actual.getOrDefault(d, Map.of());
            Map<String, Double> dayAllow = allowance.getOrDefault(d, Map.of());
            for (String chem : chemsPresent) {
                VocReconcileCellDto cell = buildCell(dayActual.getOrDefault(chem, 0.0), dayAllow.get(chem));
                if (cell != null) row.getCells().put(chem, cell);
            }
            byWeek.computeIfAbsent(weekOfMonth(d), k -> new ArrayList<>()).add(row);
        }

        // Weekly blocks: label (first–last date present, like %!AD2), Total row over the
        // weekly sums (same NC/NA logic — %!C14/C15), HIGH/LOW ranking on those totals.
        List<VocReconcileWeekDto> weeks = new ArrayList<>();
        List<VocReconcileRowDto> allRows = new ArrayList<>();
        for (List<VocReconcileRowDto> weekRows : byWeek.values()) {
            VocReconcileWeekDto week = new VocReconcileWeekDto();
            LocalDate first = weekRows.get(0).getDate();
            LocalDate last = weekRows.get(weekRows.size() - 1).getDate();
            week.setLabel(first.equals(last) ? DAY_MONTH.format(first)
                    : DAY_MONTH.format(first) + "–" + DAY_MONTH.format(last));
            week.setRows(weekRows);
            week.setTotalRow(buildTotalRow(weekRows, chemsPresent));
            rankWeek(week);
            weeks.add(week);
            allRows.addAll(weekRows);
        }
        report.setReconcileWeeks(weeks);

        // Grand Total over the whole filtered range (%!C32/C33 = C52/C70)
        if (!allRows.isEmpty()) {
            report.setReconcileTotal(buildTotalRow(allRows, chemsPresent));
        }

        // EFF sheet per-line pivot (same actual/allowance, regrouped by line)
        buildLinePivot(report, allowanceByLine, actualByLine, chemsPresent);
    }

    /** EFF sheet per-line dashboard: rows = line, a cell per chemical (reusing the
     *  reconcile {@link #buildCell} so ratio/diff/NC/NA/OVER match), a Total row, and
     *  the High/Low consumption ranking over that total (EFF cols Y/Z). Columns reuse
     *  {@code report.reconcileChemicals}. Aggregated over the whole filtered range. */
    private void buildLinePivot(VocReportDto report,
                                Map<String, Map<String, Double>> allowanceByLine,
                                Map<String, Map<String, Double>> actualByLine,
                                Set<String> chemsPresent) {
        Set<String> lines = new TreeSet<>();
        lines.addAll(allowanceByLine.keySet());
        lines.addAll(actualByLine.keySet());
        if (lines.isEmpty()) return;

        List<VocReconcileRowDto> rows = new ArrayList<>();
        for (String line : lines) {
            VocReconcileRowDto row = new VocReconcileRowDto();
            row.setLine(line);
            Map<String, Double> act = actualByLine.getOrDefault(line, Map.of());
            Map<String, Double> allow = allowanceByLine.getOrDefault(line, Map.of());
            for (String chem : chemsPresent) {
                VocReconcileCellDto cell = buildCell(act.getOrDefault(chem, 0.0), allow.get(chem));
                if (cell != null) row.getCells().put(chem, cell);
            }
            rows.add(row);
        }
        report.setByLineRows(rows);

        VocReconcileRowDto total = buildTotalRow(rows, chemsPresent);
        report.setByLineTotal(total);

        // High/Low ranking over the Total row — reuse the week-ranking logic.
        VocReconcileWeekDto tmp = new VocReconcileWeekDto();
        tmp.setTotalRow(total);
        rankWeek(tmp);
        report.setByLineHigh(tmp.getHigh());
        report.setByLineLow(tmp.getLow());
    }

    /** Canonical chemical code from the master (case-insensitive), so entry,
     *  recipe and master spellings land in the same pivot column. */
    private static String canonicalChem(String code, Map<String, VocChemical> chemMap) {
        String aliased = aliasChem(code);
        VocChemical c = chemMap.get(aliased.toLowerCase());
        return c != null ? c.getCode() : aliased;
    }

    /** One pivot cell from raw sums — the % sheet's exact NC/NA/ratio/diff logic.
     *  Returns null when both sides are empty (blank cell; Excel treats 0 as ""). */
    private static VocReconcileCellDto buildCell(double act, Double allow) {
        boolean hasAllow = allow != null && allow > 0;
        if (act == 0 && !hasAllow) return null;

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
        return cell;
    }

    /** Total row over a set of date rows: per-chemical Σactual/Σallowance re-celled
     *  (ratio of sums, NOT average of ratios — %!C14 = C42/C60), output/vocGrams summed. */
    private static VocReconcileRowDto buildTotalRow(List<VocReconcileRowDto> rows, Set<String> chems) {
        VocReconcileRowDto total = new VocReconcileRowDto();
        int output = 0;
        double grams = 0;
        Map<String, double[]> sums = new HashMap<>();   // chem -> {ΣactualKg, ΣallowanceKg}
        for (VocReconcileRowDto row : rows) {
            output += row.getOutput();
            grams += row.getVocGrams();
            for (Map.Entry<String, VocReconcileCellDto> e : row.getCells().entrySet()) {
                double[] s = sums.computeIfAbsent(e.getKey(), k -> new double[2]);
                s[0] += e.getValue().getActualKg();
                s[1] += e.getValue().getAllowanceKg();
            }
        }
        total.setOutput(output);
        total.setVocGrams(grams);
        for (String chem : chems) {
            double[] s = sums.get(chem);
            if (s == null) continue;
            VocReconcileCellDto cell = buildCell(s[0], s[1] > 0 ? s[1] : null);
            if (cell != null) total.getCells().put(chem, cell);
        }
        return total;
    }

    /** Week ranking from its Total-row ratios (%!AC/AF LARGE/SMALL panels):
     *  ≥ 1.1 = over-consumed (HIGH, desc), ≤ 0.9 = under-consumed (LOW, asc).
     *  NC/NA totals have no ratio and are skipped, like LARGE/SMALL skip text. */
    private static void rankWeek(VocReconcileWeekDto week) {
        for (Map.Entry<String, VocReconcileCellDto> e : week.getTotalRow().getCells().entrySet()) {
            Double ratio = e.getValue().getRatio();
            if (ratio == null) continue;
            if (ratio >= 1.1) week.getHigh().add(new VocReportDto.ChemRank(e.getKey(), ratio));
            else if (ratio <= 0.9) week.getLow().add(new VocReportDto.ChemRank(e.getKey(), ratio));
        }
        week.getHigh().sort((a, b) -> Double.compare(b.ratio(), a.ratio()));
        week.getLow().sort((a, b) -> Double.compare(a.ratio(), b.ratio()));
    }

    /** recipe map: cleanedArticle (lower) -> (chemicalCode -> kgPerPair). Recipe table
     *  is small, so load all and match in-memory (avoids a fragile In+IgnoreCase query).
     *  Shared by the reconciliation and the SUBCON report. */
    private Map<String, Map<String, Double>> loadRecipeMap() {
        Map<String, Map<String, Double>> recipe = new HashMap<>();
        for (VocStandardRate r : rateRepo.findAll()) {
            String a = SectionMetrics.ArticleKey.parse(r.getArticleNo()).cleanedArticle();
            if (a == null) continue;
            recipe.computeIfAbsent(recipeKey(r.getSection(), a), k -> new HashMap<>())
                    .put(r.getChemicalCode(), r.getKgPerPair());
        }
        return recipe;
    }

    // ════════════════════════════════════════════════════════════════════════
    // SUBCON (subcontractor) tracking — workbook "CEMENT" / "ACTUAL CEMENT".
    //   standard(chem) = output × recipe(article, chem)   (no markup, like reconcile)
    //   shortage(chem) = standard − actual
    // Header/detail: OUTPUT lives once per (date, subcontractor, article).
    // ════════════════════════════════════════════════════════════════════════

    public List<String> getSubconMonths() { return subconRepo.findDistinctMonths(); }

    public List<String> getSubcontractors() { return subconRepo.findDistinctSubcontractors(); }

    public VocSubconReportDto getSubconReport(String month) {
        VocSubconReportDto report = new VocSubconReportDto();
        List<String> months = subconRepo.findDistinctMonths();
        report.setAllMonths(months);
        if (month == null || month.isBlank()) month = months.isEmpty() ? null : months.get(0);
        report.setSelectedMonth(month);
        if (month == null) return report;

        YearMonth ym;
        try { ym = YearMonth.parse(month); } catch (RuntimeException e) { return report; }
        List<VocSubconEntry> entries = subconRepo.findWithDetailsBetween(ym.atDay(1), ym.atEndOfMonth());
        if (entries.isEmpty()) return report;

        Map<String, Map<String, Double>> recipe = loadRecipeMap();
        Map<String, VocChemical> chemMap = new HashMap<>();
        for (VocChemical c : chemicalRepo.findAll()) chemMap.put(c.getCode().toLowerCase(), c);

        Set<String> chemsPresent = new TreeSet<>();
        List<VocSubconReportDto.Row> rows = new ArrayList<>();
        for (VocSubconEntry e : entries) {
            String art = SectionMetrics.ArticleKey.parse(e.getArticleNo()).cleanedArticle();
            // ponytail: subcon (CEMENT) is SEW's; key its recipe to SEW.
            Map<String, Double> rec = art != null ? recipe.get(recipeKey(DEFAULT_SECTION, art)) : null;
            int out = e.getOutput() != null ? e.getOutput() : 0;

            // actual + reuse per chemical from the detail rows
            Map<String, double[]> byChem = new LinkedHashMap<>();   // chem -> {actual, reuse}
            for (VocSubconDetail d : e.getDetails()) {
                double[] v = byChem.computeIfAbsent(d.getChemicalCode(), k -> new double[2]);
                v[0] += d.getActualKg() != null ? d.getActualKg() : 0.0;
                v[1] += d.getReuseKg() != null ? d.getReuseKg() : 0.0;
            }
            // union of chemicals with an actual and those with a standard rate
            Set<String> chems = new TreeSet<>(byChem.keySet());
            if (rec != null) chems.addAll(rec.keySet());

            VocSubconReportDto.Row row = VocSubconReportDto.Row.builder()
                    .id(e.getId()).date(e.getProductionDate())
                    .subcontractor(e.getSubcontractor()).articleNo(e.getArticleNo())
                    .output(out).build();
            double totStd = 0, totAct = 0, totVoc = 0;
            for (String chem : chems) {
                double[] av = byChem.getOrDefault(chem, new double[2]);
                double act = av[0], reuse = av[1];
                Double rate = rec != null ? rec.get(chem) : null;
                double std = rate != null ? out * rate : 0.0;
                boolean hasStd = std > 0;
                if (act == 0 && !hasStd) continue;

                VocReconcileCellDto cell = new VocReconcileCellDto();
                cell.setActualKg(act);
                cell.setAllowanceKg(std);
                cell.setDiffKg(std - act);          // shortage
                if (hasStd && act > 0) {
                    double ratio = act / std;
                    cell.setRatio(ratio);
                    cell.setStatus(ratio > 1.0 ? "OVER" : "OK");
                } else if (hasStd) {
                    cell.setStatus("NC");
                } else {
                    cell.setStatus("NA");
                }
                row.getCells().put(chem, cell);

                VocChemical ch = chemMap.get(chem.toLowerCase());
                double factor = (ch != null && ch.getVocFactor() != null) ? ch.getVocFactor() : 0.0;
                totStd += std; totAct += act; totVoc += (act - reuse) * factor;
                chemsPresent.add(chem);
            }
            row.setTotalStandardKg(totStd);
            row.setTotalActualKg(totAct);
            row.setTotalShortageKg(totStd - totAct);
            row.setVocKg(totVoc);
            rows.add(row);
        }
        report.setChemicals(new ArrayList<>(chemsPresent));
        report.setRows(rows);
        return report;
    }

    /** Upsert one subcontractor chemical actual: finds/creates the (date, subcontractor,
     *  article) header (setting its output), then upserts the chemical detail under it. */
    @Transactional
    public void saveSubconDetail(LocalDate date, String subcontractor, String articleNo, Integer output,
                                 String chemicalCode, Double actualKg, Double reuseKg) {
        String subcon = subcontractor != null ? subcontractor.trim() : null;
        String article = articleNo != null ? articleNo.trim() : null;
        String chem = chemicalCode != null ? chemicalCode.trim() : null;
        VocSubconEntry entry = subconRepo
                .findByProductionDateAndSubcontractorAndArticleNo(date, subcon, article)
                .orElseGet(() -> VocSubconEntry.builder()
                        .productionDate(date).subcontractor(subcon).articleNo(article).output(0).build());
        if (output != null) entry.setOutput(output);

        VocSubconDetail detail = entry.getDetails().stream()
                .filter(d -> d.getChemicalCode().equalsIgnoreCase(chem))
                .findFirst().orElse(null);
        if (detail == null) {
            entry.getDetails().add(VocSubconDetail.builder()
                    .entry(entry).chemicalCode(chem)
                    .actualKg(actualKg != null ? actualKg : 0.0)
                    .reuseKg(reuseKg != null ? reuseKg : 0.0).build());
        } else {
            detail.setActualKg(actualKg != null ? actualKg : 0.0);
            detail.setReuseKg(reuseKg != null ? reuseKg : 0.0);
        }
        subconRepo.save(entry);
        systemLogService.logAction("SAVE_VOC_SUBCON", date + " | " + subcon + " | " + article + " | " + chem);
    }

    /** Batch upsert (legacy: always overwrites). Returns the number saved. */
    @Transactional
    public int saveSubconBatch(LocalDate date, String subcontractor, String articleNo, Integer output,
                               List<String> chemicalCode, List<Double> actualKg, List<Double> reuseKg) {
        return saveSubconBatch(date, subcontractor, articleNo, output, chemicalCode, actualKg, reuseKg, true).saved();
    }

    /** Batch save with confirm-before-overwrite. A chemical whose detail already exists on the
     *  (date, subcontractor, article) header is skipped + reported as a conflict unless
     *  {@code overwrite}. New chemicals are added. Builds the header once. */
    @Transactional
    public BatchResult saveSubconBatch(LocalDate date, String subcontractor, String articleNo, Integer output,
                               List<String> chemicalCode, List<Double> actualKg, List<Double> reuseKg,
                               boolean overwrite) {
        List<String> conflicts = new ArrayList<>();
        if (date == null || subcontractor == null || subcontractor.isBlank()
                || articleNo == null || articleNo.isBlank() || chemicalCode == null) return new BatchResult(0, conflicts);
        final String subF = subcontractor.trim(), artF = articleNo.trim();
        VocSubconEntry entry = subconRepo
                .findByProductionDateAndSubcontractorAndArticleNo(date, subF, artF)
                .orElseGet(() -> VocSubconEntry.builder()
                        .productionDate(date).subcontractor(subF).articleNo(artF).output(0).build());
        if (output != null) entry.setOutput(output);
        int saved = 0;
        for (int i = 0; i < chemicalCode.size(); i++) {
            String chem = chemicalCode.get(i);
            if (chem == null || chem.isBlank()) continue;
            Double act = (actualKg != null && i < actualKg.size()) ? actualKg.get(i) : null;
            Double reuse = (reuseKg != null && i < reuseKg.size()) ? reuseKg.get(i) : null;
            if ((act == null || act == 0.0) && (reuse == null || reuse == 0.0)) continue;   // blank row
            final String chemF = chem.trim();
            VocSubconDetail detail = entry.getDetails().stream()
                    .filter(d -> d.getChemicalCode().equalsIgnoreCase(chemF))
                    .findFirst().orElse(null);
            if (detail == null) {
                entry.getDetails().add(VocSubconDetail.builder()
                        .entry(entry).chemicalCode(chemF)
                        .actualKg(act != null ? act : 0.0).reuseKg(reuse != null ? reuse : 0.0).build());
                saved++;
            } else if (overwrite) {
                detail.setActualKg(act != null ? act : 0.0);
                detail.setReuseKg(reuse != null ? reuse : 0.0);
                saved++;
            } else {
                conflicts.add(chemF);
            }
        }
        subconRepo.save(entry);
        systemLogService.logAction("SAVE_VOC_SUBCON_BATCH", date + " | " + subF + " | " + artF + " | " + saved + " chem");
        return new BatchResult(saved, conflicts);
    }

    public void deleteSubconEntry(Long id) {
        subconRepo.findById(id).ifPresent(e -> {
            subconRepo.deleteById(id);
            systemLogService.logAction("DELETE_VOC_SUBCON",
                    e.getProductionDate() + " | " + e.getSubcontractor() + " | " + e.getArticleNo());
        });
    }

    // Excel import — SUBCON. Auto-detects two layouts:
    //   WIDE  : the workbook's "ACTUAL CEMENT" sheet (twin Std|Actual blocks, repeating
    //           23-row day-blocks; we read the Actual block). Imported directly.
    //   LONG  : Date | Subcontractor | Article | Output | Chemical | Actual Kg | Reuse Kg
    @Transactional
    public WeeklyImportResultDto importSubconFromExcel(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet wide = findWideSubconSheet(wb);
            if (wide != null) importSubconWide(wide, result);
            else              importSubconLong(wb.getSheetAt(0), result);
        }
        systemLogService.logAction("IMPORT_VOC_SUBCON", result.toFlashMessage());
        return result;
    }

    /** The "ACTUAL CEMENT" sheet (trim-insensitive name) or the first sheet that carries
     *  the wide twin-block signature (a row with "LINE" in col B and again in col AB). */
    private Sheet findWideSubconSheet(Workbook wb) {
        for (int s = 0; s < wb.getNumberOfSheets(); s++) {
            String name = wb.getSheetName(s);
            if (name != null && name.trim().equalsIgnoreCase("ACTUAL CEMENT")) return wb.getSheetAt(s);
        }
        Sheet first = wb.getSheetAt(0);
        for (int i = 0; i <= Math.min(first.getLastRowNum(), 12); i++) {
            Row row = first.getRow(i);
            if (row != null && "LINE".equalsIgnoreCase(getString(row.getCell(1)))
                    && "LINE".equalsIgnoreCase(getString(row.getCell(27)))) return first;
        }
        return null;
    }

    // WIDE "ACTUAL CEMENT": per 23-row day-block — date at headerRow-3 (col B), block
    // header where col B == "LINE"; we read the ACTUAL (right) block: AB=line→subcontractor,
    // AC=article, AD=output, AE..AZ=actual kg per chemical (codes read from the header row).
    private void importSubconWide(Sheet sheet, WeeklyImportResultDto result) {
        final int RB_LINE = 27, RB_ART = 28, RB_OUT = 29, RB_CHEM_FROM = 30, RB_CHEM_TO = 51;
        Map<String, VocSubconEntry> cache = new LinkedHashMap<>();   // (date|line|article) -> header
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row hdr = sheet.getRow(i);
            if (hdr == null || !"LINE".equalsIgnoreCase(getString(hdr.getCell(RB_LINE)))) continue;

            Row dateRow = sheet.getRow(i - 3);
            LocalDate date = dateRow != null ? getDate(dateRow.getCell(1)) : null;
            if (date == null) { result.getErrors().add("Block at row " + (i + 1) + ": missing/invalid date"); continue; }

            Map<Integer, String> chemCols = new LinkedHashMap<>();   // column index -> chemical code
            for (int c = RB_CHEM_FROM; c <= RB_CHEM_TO; c++) {
                String code = getString(hdr.getCell(c));
                if (code != null && !code.isBlank()) chemCols.put(c, aliasChem(code));
            }
            if (chemCols.isEmpty()) continue;

            for (int r = i + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) break;
                String line = getString(row.getCell(RB_LINE));
                if (line == null || line.isBlank()) break;          // end of block
                String lu = line.trim().toUpperCase();
                if (lu.equals("LINE")) break;                       // next block header
                if (lu.startsWith("TOTAL") || lu.startsWith("TỔNG") || lu.startsWith("TONG")) continue;

                String article = getString(row.getCell(RB_ART));
                if (article == null || article.isBlank()) { result.setSkipped(result.getSkipped() + 1); continue; }
                Double output = getDouble(row.getCell(RB_OUT));

                final String subF = line.trim(), artF = article.trim();
                String key = date + "|" + subF + "|" + artF;
                VocSubconEntry entry = cache.computeIfAbsent(key, k -> subconRepo
                        .findByProductionDateAndSubcontractorAndArticleNo(date, subF, artF)
                        .orElseGet(() -> VocSubconEntry.builder()
                                .productionDate(date).subcontractor(subF).articleNo(artF).output(0).build()));
                if (output != null) entry.setOutput(output.intValue());

                for (Map.Entry<Integer, String> ce : chemCols.entrySet()) {
                    Double act = getDouble(row.getCell(ce.getKey()));
                    if (act == null || act == 0.0) continue;
                    final String chemF = ce.getValue();
                    VocSubconDetail detail = entry.getDetails().stream()
                            .filter(d -> d.getChemicalCode().equalsIgnoreCase(chemF))
                            .findFirst().orElse(null);
                    if (detail == null) {
                        entry.getDetails().add(VocSubconDetail.builder()
                                .entry(entry).chemicalCode(chemF).actualKg(act).reuseKg(0.0).build());
                        result.setInserted(result.getInserted() + 1);
                    } else {
                        detail.setActualKg(act);
                        result.setUpdated(result.getUpdated() + 1);
                    }
                }
            }
        }
        cache.values().forEach(subconRepo::save);
    }

    // LONG layout: Date | Subcontractor | Article | Output | Chemical | Actual Kg | Reuse Kg
    private void importSubconLong(Sheet sheet, WeeklyImportResultDto result) {
        Map<String, VocSubconEntry> cache = new LinkedHashMap<>();   // (date|subcon|article) -> header
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

            LocalDate date = getDate(row.getCell(0));
            String subcon = getString(row.getCell(1));
            String article = getString(row.getCell(2));
            Double output = getDouble(row.getCell(3));
            String chem = getString(row.getCell(4));
            Double actual = getDouble(row.getCell(5));
            Double reuse = getDouble(row.getCell(6));

            if (date == null && (subcon == null || subcon.isBlank())
                    && (chem == null || chem.isBlank())) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            if (date == null) { result.getErrors().add("Row " + (i + 1) + ": Date is required/invalid"); continue; }
            if (subcon == null || subcon.isBlank()) { result.getErrors().add("Row " + (i + 1) + ": Subcontractor is required"); continue; }
            if (article == null || article.isBlank()) { result.getErrors().add("Row " + (i + 1) + ": Article is required"); continue; }
            if (chem == null || chem.isBlank()) { result.getErrors().add("Row " + (i + 1) + ": Chemical is required"); continue; }

            final String subF = subcon.trim(), artF = article.trim();
            String key = date + "|" + subF + "|" + artF;
            VocSubconEntry entry = cache.computeIfAbsent(key, k -> subconRepo
                    .findByProductionDateAndSubcontractorAndArticleNo(date, subF, artF)
                    .orElseGet(() -> VocSubconEntry.builder()
                            .productionDate(date).subcontractor(subF).articleNo(artF).output(0).build()));
            if (output != null) entry.setOutput(output.intValue());

            final String chemF = aliasChem(chem);
            VocSubconDetail detail = entry.getDetails().stream()
                    .filter(d -> d.getChemicalCode().equalsIgnoreCase(chemF))
                    .findFirst().orElse(null);
            if (detail == null) {
                entry.getDetails().add(VocSubconDetail.builder()
                        .entry(entry).chemicalCode(chemF)
                        .actualKg(actual != null ? actual : 0.0)
                        .reuseKg(reuse != null ? reuse : 0.0).build());
                result.setInserted(result.getInserted() + 1);
            } else {
                detail.setActualKg(actual != null ? actual : 0.0);
                detail.setReuseKg(reuse != null ? reuse : 0.0);
                result.setUpdated(result.getUpdated() + 1);
            }
        }
        cache.values().forEach(subconRepo::save);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — chemical master. Auto-detects two layouts by header width:
    //   FULL "R" sheet (≥10 cols): Code | MaterialType | Class | Mfr | VOC | UNIT |
    //                              kg | Container | Price | $/KG | Date
    //   LEGACY template (6 cols):  Code | Type | Class | Mfr | VOC Factor | Price/kg
    // pricePerKg comes from $/KG (col 9) on the full sheet, col 5 on the legacy one.
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public WeeklyImportResultDto importChemicalsFromExcel(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            boolean fullR = header != null && header.getLastCellNum() >= 10;
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
                // A chemical code is shared across sections; a section's R sheet may leave VOC/price
                // blank or 0 (ASSY/OS do). Only overwrite when the incoming value is present, so one
                // section's blanks never wipe another section's real factor/price.
                setIfPresent(getString(row.getCell(1)), v -> c.setMaterialType(normalizeMaterialType(v)));
                setIfPresent(getString(row.getCell(2)), c::setClassification);
                setIfPresent(getString(row.getCell(3)), c::setManufacturer);
                if (factor != null && factor > 0) c.setVocFactor(factor);
                else if (c.getVocFactor() == null) c.setVocFactor(0.0);
                if (fullR) {
                    setIfPresent(getString(row.getCell(5)), c::setUnit);
                    setIfPositive(getDouble(row.getCell(6)), c::setContainerSizeKg);
                    setIfPositive(getDouble(row.getCell(8)), c::setContainerPrice);
                    setIfPositive(getDouble(row.getCell(9)), c::setPricePerKg);   // $/KG
                    setIfPresent(getString(row.getCell(10)), c::setPriceRefNote); // "Date" — date or text
                } else {
                    setIfPositive(getDouble(row.getCell(5)), c::setPricePerKg);   // legacy 6-col template
                }
                if (c.getMaterialType() == null) c.setMaterialType("SOLVENT");
                if (c.getActive() == null) c.setActive(true);
                chemicalRepo.save(c);

                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        systemLogService.logAction("IMPORT_VOC_CHEMICALS", result.toFlashMessage());
        return result;
    }

    /** Apply {@code value} via {@code setter} only when it is non-blank (preserves existing data). */
    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) setter.accept(value);
    }

    /** Apply {@code value} via {@code setter} only when it is a positive number. */
    private static void setIfPositive(Double value, java.util.function.Consumer<Double> setter) {
        if (value != null && value > 0) setter.accept(value);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — consumption ("Actual" sheet). Two layouts, auto-detected by width:
    //   FULL "Actual" (≥10 cols): Date|Section|Line|Chemical|Production|Throw|Reuse|MatType|Class|Mfr|VOC
    //   LEGACY template (6 cols): Date|Section|Line|Chemical|Quantity Kg|Reuse Kg
    // Consumed qty = Production/Quantity (col 4); reuse = col 6 (full) or col 5 (legacy).
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public WeeklyImportResultDto importConsumptionFromExcel(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            // Raw VOC workbook keeps actual consumption on the "Actual" sheet; the app's own
            // template (GET /voc/consumption/template) puts it on sheet 0.
            Sheet sheet = wb.getSheet("Actual") != null ? wb.getSheet("Actual") : wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            // 7+ cols = wide "Actual" (Date|Section|Line|Chemical|Production|Throw|Reuse) → reuse col 6;
            // 6-col legacy template (Date|Section|Line|Chemical|Quantity|Reuse) → reuse col 5.
            int reuseCol = (header != null && header.getLastCellNum() >= 7) ? 6 : 5;
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
                Double reuse = getDouble(row.getCell(reuseCol));
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

    // ════════════════════════════════════════════════════════════════════════
    // Excel import — production ("Data" sheet). This is the allowance source the
    // workbook itself uses: allowance = output × recipe. A line that runs several
    // styles in a day is split per article by the sheet's per-slot weights
    //   (AB..AP, cols 27..41) against the slot articles (K..Y, cols 10..24),
    // so we store one row per (date, section, line, article) with output already
    // apportioned: share = H × Σweight(article) / Σweight(line). Reconciliation
    // then sums output × recipe — matching the workbook's weighted formula.
    // Layout (0-based): Date 0 | Section 1 | Line 2 (+ subline 3) | Output H 7.
    // ════════════════════════════════════════════════════════════════════════

    private static final int DATA_SLOT_FIRST = 10;   // K
    private static final int DATA_SLOT_COUNT = 15;   // K..Y
    private static final int DATA_WEIGHT_FIRST = 27; // AB..AP

    @Transactional
    public WeeklyImportResultDto importVocProduction(MultipartFile file) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheet("Data");
            if (sheet == null) {
                result.getErrors().add("Không tìm thấy sheet 'Data' trong file");
                return result;
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

                LocalDate date = getDate(row.getCell(0));
                if (date == null) { result.setSkipped(result.getSkipped() + 1); continue; }   // header/blank rows

                String section = getString(row.getCell(1));
                section = (section == null || section.isBlank()) ? DEFAULT_SECTION : section.trim();
                String line = getString(row.getCell(2));
                if (line == null || line.isBlank()) { result.setSkipped(result.getSkipped() + 1); continue; }
                String sub = getString(row.getCell(3));
                line = (sub != null && !sub.isBlank()) ? line.trim() + sub.trim() : line.trim();

                Double output = getDouble(row.getCell(7));
                if (output == null || output == 0.0) { result.setSkipped(result.getSkipped() + 1); continue; }

                // Sum each article's slot weights (default weight 1 when the sheet leaves it blank).
                Map<String, Double> weightByArticle = new LinkedHashMap<>();
                double totalWeight = 0.0;
                for (int s = 0; s < DATA_SLOT_COUNT; s++) {
                    String art = getString(row.getCell(DATA_SLOT_FIRST + s));
                    if (art == null || art.isBlank()) continue;
                    Double w = getDouble(row.getCell(DATA_WEIGHT_FIRST + s));
                    double weight = (w != null && w > 0) ? w : 1.0;
                    weightByArticle.merge(art.trim(), weight, Double::sum);
                    totalWeight += weight;
                }
                if (weightByArticle.isEmpty() || totalWeight == 0.0) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                for (Map.Entry<String, Double> e : weightByArticle.entrySet()) {
                    double share = output * (e.getValue() / totalWeight);   // apportion H by slot weight
                    Optional<VocProduction> existing = vocProductionRepo
                            .findByProductionDateAndSectionAndLineAndArticleNo(date, section, line, e.getKey());
                    VocProduction p = existing.orElseGet(VocProduction::new);
                    p.setProductionDate(date);
                    p.setSection(section);
                    p.setLine(line);
                    p.setArticleNo(e.getKey());
                    p.setOutput(share);
                    vocProductionRepo.save(p);
                    if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                    else                      result.setInserted(result.getInserted() + 1);
                }
            }
        }
        systemLogService.logAction("IMPORT_VOC_PRODUCTION", result.toFlashMessage());
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

    /** Raw reciprocal expression of a formula cell (e.g. "1/1300+1/1500"); null for
     *  a plain value cell — lets the DB sheet's formulas survive the import. */
    private String getFormula(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.FORMULA) return null;
        return normalizeFormula(cell.getCellFormula());
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
