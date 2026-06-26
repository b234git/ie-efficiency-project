package thienloc.manage.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.exception.ServiceUnavailableException;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.dto.SalaryReportDto.DayRow;
import thienloc.manage.dto.SalaryReportDto.SectionLineBlock;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.entity.NewStyleEntry;
import thienloc.manage.entity.ReprocessRecord;
import thienloc.manage.entity.SixSRecord;
import thienloc.manage.repository.EffIncentiveRateRepository;
import thienloc.manage.repository.EffMultiplierRepository;
import thienloc.manage.repository.NewStyleEntryRepository;
import thienloc.manage.util.NormalizationUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryService implements ISalaryService {

    private static final Logger log = LoggerFactory.getLogger(SalaryService.class);

    // Grade labels per section (matches EffConfigController.SECTION_GRADE_LABELS)
    private static final Map<String, List<String>> SECTION_GRADE_LABELS;
    static {
        SECTION_GRADE_LABELS = new LinkedHashMap<>();
        SECTION_GRADE_LABELS.put("ASSY", Arrays.asList("A","B","C","D","LL1","LL2","LL3","SV1","SV2"));
        SECTION_GRADE_LABELS.put("SEW",  Arrays.asList("AA","A","B","C","D","E","LL1","LL2","LL3"));
        SECTION_GRADE_LABELS.put("SF",   Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
        SECTION_GRADE_LABELS.put("BUFF", Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
    }

    private final MeterRegistry meterRegistry;

    private final IProductionService productionService;
    private final WeeklyTrackingService weeklyTrackingService;
    private final NewStyleEntryRepository newStyleRepo;
    private final EffIncentiveRateRepository rateRepo;
    private final EffMultiplierRepository multiplierRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "salaryReport", fallbackMethod = "buildReportFallback")
    @Transactional(readOnly = true, timeout = 10)
    public SalaryReportDto buildReport(String month) {
        long t0 = System.nanoTime();
        YearMonth ym = YearMonth.parse(month);
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        // 1. Get all production DTOs for the month (effSalary already populated)
        List<DailyProductionDto> dtos = productionService.getDashboardDataRange(from, to);

        // 2. Build lookup maps for 6S and Reprocess (keyed by "section|line")
        Map<String, SixSRecord> sixsMap = weeklyTrackingService.getSixSByMonth(month)
                .stream().collect(Collectors.toMap(
                        r -> r.getSection() + "|" + r.getLine(),
                        r -> r,
                        (a, b) -> a));

        Map<String, ReprocessRecord> reproMap = weeklyTrackingService.getReprocessByMonth(month)
                .stream().collect(Collectors.toMap(
                        r -> r.getSection() + "|" + r.getLine(),
                        r -> r,
                        (a, b) -> a));

        // 3. Build lookup map for NewStyleEntry filtered by month (keyed by "section|line")
        Map<String, List<NewStyleEntry>> stylesMap = newStyleRepo.findByDataMonthOrderBySectionAscLineAscStyleAsc(month)
                .stream().collect(Collectors.groupingBy(e ->
                        WeeklyTrackingService.normalizeSection(e.getSection()) + "|" + e.getLine()));

        // 4. Pre-load all multipliers and rates
        Map<String, EffMultiplier> multiplierMap = multiplierRepo.findAllByOrderBySecAsc()
                .stream().collect(Collectors.toMap(EffMultiplier::getSec, m -> m));

        Map<String, List<EffIncentiveRate>> ratesMap = new HashMap<>();
        for (String sec : rateRepo.findDistinctSecs()) {
            ratesMap.put(sec, rateRepo.findBySecOrderByEffPercentAsc(sec));
        }

        // 5. Group dtos by section tag|line (preserve insertion order)
        Map<String, List<DailyProductionDto>> grouped = new LinkedHashMap<>();
        for (DailyProductionDto dto : dtos) {
            String tag = WeeklyTrackingService.normalizeSection(dto.getSection());
            String key = tag + "|" + dto.getLine();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
        }

        // 6. Build blocks
        List<SectionLineBlock> blocks = new ArrayList<>();
        for (Map.Entry<String, List<DailyProductionDto>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String section = parts[0];
            String line    = parts.length > 1 ? parts[1] : "";

            blocks.add(buildBlock(section, line, entry.getValue(),
                    sixsMap, reproMap, stylesMap, multiplierMap, ratesMap));
        }

        SalaryReportDto report = new SalaryReportDto();
        report.setMonth(month);
        report.setBlocks(blocks);
        meterRegistry.timer("salary.report.build").record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
        return report;
    }

    SalaryReportDto buildReportFallback(String month, Throwable t) {
        log.warn("Circuit open for salaryReport, month={}: {}", month, t.getMessage());
        throw new ServiceUnavailableException("Báo cáo lương tạm thời không khả dụng. Vui lòng thử lại sau.");
    }

    // ── Block builder ─────────────────────────────────────────────────────────

    private SectionLineBlock buildBlock(
            String section,
            String line,
            List<DailyProductionDto> dtos,
            Map<String, SixSRecord> sixsMap,
            Map<String, ReprocessRecord> reproMap,
            Map<String, List<NewStyleEntry>> stylesMap,
            Map<String, EffMultiplier> multiplierMap,
            Map<String, List<EffIncentiveRate>> ratesMap) {

        SectionLineBlock block = new SectionLineBlock();
        block.setSection(section);
        block.setLine(line);

        // 6S and Reprocess — use normalized section to match WeeklyTrackingService storage
        String key = section + "|" + line;
        String trackingKey = WeeklyTrackingService.normalizeSection(section) + "|" + line;
        SixSRecord sixs = sixsMap.get(trackingKey);
        ReprocessRecord repro = reproMap.get(trackingKey);
        // No tracking record = perfect by default: 6S 100% (no violations), 0% reprocess defect.
        double sixsPct     = (sixs  != null) ? sixs.getTotalPercent()  : 100.0;
        double reproDefect = (repro != null) ? repro.getTotalPercent() : 0.0;
        // Excel sheet S: effective factor = 6S × (1 − reprocess), multiplicative (G1 × (1−G3)),
        // NOT (6S − reprocess). The two only agree when reprocess = 0.
        double effectiveFraction = Math.max(0.0, (sixsPct / 100.0) * (1.0 - reproDefect / 100.0));

        block.setSixSPercent(sixsPct);
        // Show reprocess as a pass rate (100% − defect) to match the Weekly Tracking page.
        block.setReprocessPercent(100.0 - reproDefect);
        block.setEffectivePct(effectiveFraction * 100.0);

        // New Style — incentive = SUM(quantity) × 30,000 VND
        List<NewStyleEntry> styles = stylesMap.getOrDefault(key, List.of());
        int totalQty = styles.stream().mapToInt(NewStyleEntry::getQuantity).sum();
        block.setNewStyleCount(totalQty);
        block.setNewStyleIncentive((long) totalQty * 30_000L);

        // Grade labels (based on base section)
        String baseSec = normalizeBaseSection(section);
        List<String> gradeLabels = SECTION_GRADE_LABELS.getOrDefault(baseSec,
                Arrays.asList("G1","G2","G3","G4","G5","G6","G7","G8","G9"));
        block.setGradeLabels(gradeLabels);

        // Sort daily rows by date
        dtos.sort(Comparator.comparing(DailyProductionDto::getProductionDate));

        // Excel folds the line's New Style incentive into every daily grade cell, distributed
        // evenly across working days (K1 / COUNT(B6:B36)) before the per-day round-up.
        long workingDays = dtos.stream()
                .filter(d -> d.getTarget() != null && d.getTarget() > 0).count();
        double perDayNewStyle = workingDays > 0
                ? (double) block.getNewStyleIncentive() / workingDays : 0.0;

        long[] gradeTotals = new long[9];
        List<DayRow> dayRows = new ArrayList<>();

        for (DailyProductionDto dto : dtos) {
            DayRow row = new DayRow();
            row.setDate(dto.getProductionDate());
            // MP (Actual) = DLI (effective direct labor), matching Excel sheet S col F (=D!I)
            // and the EFF-Salary denominator — NOT dto.getMp() (DL, the gross head-count).
            row.setMp(dto.getDli() != null ? dto.getDli() : 0.0);
            row.setWt(dto.getWt() != null ? dto.getWt() : 0.0);
            // Excel sheet S "Quota" (target) = the WT-based standard quota, consistent with the
            // EFF column (EFF = output / ((quota/MP)·DLI·allowance)). Not dto.getTarget(), which is
            // the EFF-KPI target output used by the dashboards.
            row.setTargetQuota(dto.getStdQuota() != null ? dto.getStdQuota() : 0.0);
            row.setOutput(dto.getOutput() != null ? dto.getOutput().doubleValue() : 0.0);

            // Target MP = standard MP resolved by the EFF calculator (same robust article
            // resolution as EFF), so it is present whenever EFF is. Excel sheet S column C.
            row.setTargetMp(dto.getStdMp() != null ? dto.getStdMp() : 0.0);

            // Determine SEC code (e.g. SEW10, ASSY8)
            int normWt = normalizeWT(dto.getWt());
            String secCode = baseSec + normWt;
            row.setSec(secCode);

            double effSalary = (dto.getEffSalary() != null) ? dto.getEffSalary() * 100.0 : 0.0;
            row.setEffSalary(effSalary);

            // For BUFF: use rateAlias for rate lookup
            String rateSec = secCode;
            EffMultiplier multiplier = multiplierMap.get(secCode);
            if (multiplier != null && multiplier.getRateAlias() != null && !multiplier.getRateAlias().isBlank()) {
                rateSec = multiplier.getRateAlias();
            }

            // Floor-lookup base rate
            List<EffIncentiveRate> rates = ratesMap.getOrDefault(rateSec, List.of());
            double baseRate = floorRateLookup(rates, effSalary);
            row.setBaseRate(baseRate);

            // Grade amounts — Excel: ROUNDUP(base × mult × 6S×(1−reprocess) + newStyle/days, −2)
            long[] gradeAmounts = new long[9];
            if (multiplier != null) {
                double[] mults = getMultiplierArray(multiplier);
                double dayNewStyle = (dto.getTarget() != null && dto.getTarget() > 0)
                        ? perDayNewStyle : 0.0;
                for (int i = 0; i < 9; i++) {
                    gradeAmounts[i] = (long) Math.ceil(
                            (baseRate * mults[i] * effectiveFraction + dayNewStyle) / 100.0) * 100L;
                    gradeTotals[i] += gradeAmounts[i];
                }
            }
            row.setGradeAmounts(gradeAmounts);
            dayRows.add(row);
        }

        block.setDailyRows(dayRows);
        block.setGradeTotals(gradeTotals);
        return block;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String normalizeBaseSection(String section) {
        return NormalizationUtil.normalizeBaseSection(section);
    }

    private int normalizeWT(Double wt) {
        return NormalizationUtil.normalizeWT(wt);
    }

    /**
     * Floor lookup: find the largest effPercent ≤ effSalary in a sorted-asc rates list.
     * Returns 0 if no entry qualifies.
     */
    private double floorRateLookup(List<EffIncentiveRate> rates, double effSalary) {
        double rate = 0;
        for (EffIncentiveRate r : rates) {
            if (r.getEffPercent() <= effSalary) rate = r.getRate();
            else break;
        }
        return rate;
    }

    /** Extracts grade multipliers G1–G9 as a double array. */
    private double[] getMultiplierArray(EffMultiplier m) {
        return new double[]{
                m.getGrade1() != null ? m.getGrade1() : 1.0,
                m.getGrade2() != null ? m.getGrade2() : 1.0,
                m.getGrade3() != null ? m.getGrade3() : 1.0,
                m.getGrade4() != null ? m.getGrade4() : 1.0,
                m.getGrade5() != null ? m.getGrade5() : 1.0,
                m.getGrade6() != null ? m.getGrade6() : 1.0,
                m.getGrade7() != null ? m.getGrade7() : 1.0,
                m.getGrade8() != null ? m.getGrade8() : 1.0,
                m.getGrade9() != null ? m.getGrade9() : 1.0
        };
    }
}
