package thienloc.manage.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.util.NormalizationUtil;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extracted from ProductionService.convertToDtoAndCalculateEff().
 * Handles all efficiency/KPI/PPH calculations for a DailyProduction record.
 *
 * Formulas:
 *   EFF KPI     = Output * CT / (MP * WT * 3600 * Allowance)
 *   EFF Salary  = Output / (SUM(PPH*MP) / AVG(MP) * DLI * Allowance)
 *   Actual PPH  = Output / DLI / WT
 *   Target      = MP * WT * PPH * Allowance (weighted by article time-slots)
 *
 * MasterDb lookup mirrors Excel AVERAGEIFS: when multiple rows share the same
 * articleNo (e.g. variants by ref), CT/MP/Quota/PPH are averaged across them.
 */
@Service
public class EfficiencyCalculatorService implements IEfficiencyCalculatorService {

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Populate efficiency metrics on a DTO based on the production entity and MasterDb lookup.
     * Sets: patternNo, shoeName, stdPph, actualPph, effKpi, effSalary, target, eff, gap, tct, pph.
     */
    public void populateEfficiencyMetrics(DailyProductionDto dto, DailyProduction entity) {
        String section = entity.getSection();
        Optional<SectionMetrics> sectionOpt = SectionMetrics.fromSection(section);

        // Extract productionMonth once (reused for batch load and single lookup)
        String productionMonth = (entity.getProductionDate() != null)
                ? entity.getProductionDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : null;

        // Batch-load MasterDb for all articles in detail slots (month-aware).
        // Articles may carry "-2" suffix; strip it for MasterDb key lookup.
        List<DailyProductionDetail> validDetails = getValidDetails(entity);
        Map<String, List<MasterDb>> masterDbMap = batchLoadMasterDb(
                validDetails.stream()
                        .map(d -> SectionMetrics.ArticleKey.parse(d.getArticleNo()).cleanedArticle())
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList()),
                productionMonth);

        // Determine the lookup article from the DTO (already set by caller).
        // Strip "-2" suffix since MasterDb.articleNo is always stored without it.
        String rawLookupArticle = dto.getArticle() != null ? dto.getArticle().replace(" (+)", "") : "N/A";
        String lookupArticle = "N/A".equals(rawLookupArticle)
                ? "N/A"
                : SectionMetrics.ArticleKey.parse(rawLookupArticle).cleanedArticle();

        // Resolve per-slot primary/fallback for the DTO's primary article
        SectionMetrics.ResolvedSection primaryRes = SectionMetrics.resolveSlot(section, rawLookupArticle);
        SectionMetrics primarySm = (primaryRes.primary() != null) ? primaryRes.primary() : sectionOpt.orElse(null);

        // Lookup MasterDb list (month-aware with fallback to batch cache)
        List<MasterDb> masterList = Collections.emptyList();
        MasterDb masterData = null;
        if (!"N/A".equals(lookupArticle)) {
            // Try month-specific lookup first
            if (productionMonth != null) {
                masterList = masterDbRepository
                        .findByArticleNoInAndDataMonthOrderByRefAsc(
                                Collections.singletonList(lookupArticle), productionMonth);
            }

            // Fallback: use batch cache (no extra query)
            if (masterList.isEmpty()) {
                List<MasterDb> fromCache = masterDbMap.get(lookupArticle);
                if (fromCache != null) masterList = fromCache;
            }

            if (!masterList.isEmpty()) {
                masterData = masterList.get(0);
                dto.setPatternNo(masterData.getPatternNo());
                dto.setShoeName(masterData.getShoeName());
                if (primarySm != null) {
                    final SectionMetrics sm = primarySm;
                    final SectionMetrics fb = primaryRes.fallback();
                    dto.setStdPph(avgMetric(masterList, m -> sm.getPphOrFallback(m, fb)));
                }
            }

            // Set masterDbReason for Pattern/Style warning in report
            if (masterData == null) {
                dto.setMasterDbReason("Article '" + lookupArticle + "' not found in Master DB");
            } else if (masterData.getPatternNo() == null || masterData.getShoeName() == null) {
                dto.setMasterDbReason("Pattern/Style not set in Master DB for article '" + lookupArticle + "'");
            }
        }

        // Actual PPH = Output / DLI / WT
        calculateActualPph(dto, entity);

        // Allowance normalization
        double allowance = normalizeAllowance(entity.getAllowance());

        if (sectionOpt.isPresent()) {
            SectionMetrics effPrimary = (primarySm != null) ? primarySm : sectionOpt.get();
            final SectionMetrics fb = primaryRes.fallback();

            // Averaged primary-article metrics (matches Excel AVERAGEIFS behavior).
            Double singleCt = avgMetric(masterList, m -> effPrimary.getCtOrFallback(m, fb));
            Double dbMp = avgMetric(masterList, m -> effPrimary.getMpOrFallback(m, fb));
            Double dbQuota = avgMetric(masterList, m -> effPrimary.getQuotaOrFallback(m, fb));
            Double displayPph = avgMetric(masterList, m -> effPrimary.getPphOrFallback(m, fb));

            // Weighted CT across all articles (proportional to time slots) — per-slot resolution.
            Double weightedCt = calculateWeightedCt(section, singleCt, validDetails, masterDbMap);

            // 1) EFF KPI (use weighted CT for multi-article accuracy)
            calculateEffKpi(dto, entity, weightedCt, allowance);

            // 2) EFF Salary (per-slot resolution internally)
            calculateEffSalary(dto, entity, section, dbQuota, dbMp, allowance, validDetails, masterDbMap);

            // 3) Target + EFF% + Gap (per-slot resolution internally)
            calculateTarget(dto, entity, masterData != null, section, displayPph,
                    weightedCt, allowance, validDetails, masterDbMap);

            // ── Set reasons when EFF values could not be calculated ──
            if (dto.getEffKpi() == null) {
                if (masterData == null && !"N/A".equals(lookupArticle)) {
                    dto.setEffKpiReason("Article '" + lookupArticle + "' not found in Master DB");
                } else if (weightedCt == null || weightedCt <= 0) {
                    dto.setEffKpiReason("No CT data for " + section + " in Master DB");
                } else if (entity.getMp() == null || entity.getMp() <= 0) {
                    dto.setEffKpiReason("MP is missing or zero");
                } else if (entity.getWt() == null || entity.getWt() <= 0) {
                    dto.setEffKpiReason("WT is missing or zero");
                } else if (entity.getTotalOutput() == null) {
                    dto.setEffKpiReason("Output is missing");
                }
            }
            if (dto.getEffSalary() == null) {
                if (entity.getDli() == null || entity.getDli() <= 0) {
                    dto.setEffSalaryReason("DLI is missing or zero");
                } else if (masterData == null && !"N/A".equals(lookupArticle)) {
                    dto.setEffSalaryReason("Article '" + lookupArticle + "' not found in Master DB");
                } else {
                    dto.setEffSalaryReason("No Quota data for " + section + " in Master DB");
                }
            }
        } else {
            String reason = "Section '" + section + "' not configured";
            dto.setEffKpiReason(reason);
            dto.setEffSalaryReason(reason);
        }
    }

    /**
     * Batch populate: pre-loads all MasterDb data in bulk, then calculates metrics.
     * Reduces N * (2-3) queries to (uniqueMonths + 1) queries total.
     */
    public void populateEfficiencyMetricsBatch(List<DailyProductionDto> dtos,
                                                List<DailyProduction> entities) {
        if (dtos.isEmpty()) return;
        long t0 = System.nanoTime();

        // 1. Collect all unique articles and production months
        Set<String> allArticleNos = new HashSet<>();
        Set<String> allMonths = new HashSet<>();
        for (DailyProduction entity : entities) {
            String month = getProductionMonth(entity);
            if (month != null) allMonths.add(month);
            for (DailyProductionDetail d : getValidDetails(entity)) {
                String cleaned = SectionMetrics.ArticleKey.parse(d.getArticleNo()).cleanedArticle();
                if (cleaned != null) allArticleNos.add(cleaned);
            }
        }

        if (allArticleNos.isEmpty()) {
            for (int i = 0; i < dtos.size(); i++) {
                populateFromCache(dtos.get(i), entities.get(i),
                        new HashMap<>(), new HashMap<>());
            }
            meterRegistry.timer("eff.calc.batch").record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            return;
        }

        // 2. Batch load: per-month maps + fallback (no month filter)
        List<String> articleList = new ArrayList<>(allArticleNos);
        Map<String, Map<String, List<MasterDb>>> monthMaps = new HashMap<>();
        for (String month : allMonths) {
            Map<String, List<MasterDb>> map = buildMap(
                    masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(articleList, month));
            if (!map.isEmpty()) monthMaps.put(month, map);
        }
        Map<String, List<MasterDb>> fallbackMap = buildMap(
                masterDbRepository.findByArticleNoInOrderByRefAsc(articleList));

        // 3. Process each record using cached data
        for (int i = 0; i < dtos.size(); i++) {
            populateFromCache(dtos.get(i), entities.get(i), monthMaps, fallbackMap);
        }
        meterRegistry.timer("eff.calc.batch").record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
    }

    /**
     * Populate metrics for a single record using pre-loaded MasterDb cache.
     */
    private void populateFromCache(DailyProductionDto dto, DailyProduction entity,
                                    Map<String, Map<String, List<MasterDb>>> monthMaps,
                                    Map<String, List<MasterDb>> fallbackMap) {
        String section = entity.getSection();
        Optional<SectionMetrics> sectionOpt = SectionMetrics.fromSection(section);
        String productionMonth = getProductionMonth(entity);

        // Resolve masterDbMap: month-specific → fallback (matches batchLoadMasterDb logic)
        Map<String, List<MasterDb>> masterDbMap;
        if (productionMonth != null && monthMaps.containsKey(productionMonth)) {
            masterDbMap = monthMaps.get(productionMonth);
        } else {
            masterDbMap = fallbackMap;
        }

        List<DailyProductionDetail> validDetails = getValidDetails(entity);
        String rawLookupArticle = dto.getArticle() != null ? dto.getArticle().replace(" (+)", "") : "N/A";
        String lookupArticle = "N/A".equals(rawLookupArticle)
                ? "N/A"
                : SectionMetrics.ArticleKey.parse(rawLookupArticle).cleanedArticle();

        // Lookup primary article list: month-specific first, then fallback
        List<MasterDb> masterList = Collections.emptyList();
        MasterDb masterData = null;
        SectionMetrics.ResolvedSection primaryRes = SectionMetrics.resolveSlot(section, rawLookupArticle);
        SectionMetrics primarySm = (primaryRes.primary() != null) ? primaryRes.primary() : sectionOpt.orElse(null);
        if (!"N/A".equals(lookupArticle) && lookupArticle != null) {
            if (productionMonth != null && monthMaps.containsKey(productionMonth)) {
                List<MasterDb> list = monthMaps.get(productionMonth).get(lookupArticle);
                if (list != null) masterList = list;
            }
            if (masterList.isEmpty()) {
                List<MasterDb> list = fallbackMap.get(lookupArticle);
                if (list != null) masterList = list;
            }
            if (!masterList.isEmpty()) {
                masterData = masterList.get(0);
                dto.setPatternNo(masterData.getPatternNo());
                dto.setShoeName(masterData.getShoeName());
                if (primarySm != null) {
                    final SectionMetrics smStd = primarySm;
                    final SectionMetrics fbStd = primaryRes.fallback();
                    dto.setStdPph(avgMetric(masterList, m -> smStd.getPphOrFallback(m, fbStd)));
                }
            }

            // Set masterDbReason for Pattern/Style warning in report
            if (masterData == null) {
                dto.setMasterDbReason("Article '" + lookupArticle + "' not found in Master DB");
            } else if (masterData.getPatternNo() == null || masterData.getShoeName() == null) {
                dto.setMasterDbReason("Pattern/Style not set in Master DB for article '" + lookupArticle + "'");
            }
        }

        calculateActualPph(dto, entity);
        double allowance = normalizeAllowance(entity.getAllowance());

        if (sectionOpt.isPresent()) {
            SectionMetrics sm = sectionOpt.get();
            final SectionMetrics effPrimary = (primarySm != null) ? primarySm : sm;
            final SectionMetrics fb = primaryRes.fallback();
            Double singleCt = avgMetric(masterList, m -> effPrimary.getCtOrFallback(m, fb));
            Double dbMp = avgMetric(masterList, m -> effPrimary.getMpOrFallback(m, fb));
            Double dbQuota = avgMetric(masterList, m -> effPrimary.getQuotaOrFallback(m, fb));
            Double displayPph = avgMetric(masterList, m -> effPrimary.getPphOrFallback(m, fb));
            Double weightedCt = calculateWeightedCt(section, singleCt, validDetails, masterDbMap);

            calculateEffKpi(dto, entity, weightedCt, allowance);
            calculateEffSalary(dto, entity, section, dbQuota, dbMp, allowance, validDetails, masterDbMap);
            calculateTarget(dto, entity, masterData != null, section, displayPph,
                    weightedCt, allowance, validDetails, masterDbMap);

            if (dto.getEffKpi() == null) {
                if (masterData == null && !"N/A".equals(lookupArticle)) {
                    dto.setEffKpiReason("Article '" + lookupArticle + "' not found in Master DB");
                } else if (weightedCt == null || weightedCt <= 0) {
                    dto.setEffKpiReason("No CT data for " + section + " in Master DB");
                } else if (entity.getMp() == null || entity.getMp() <= 0) {
                    dto.setEffKpiReason("MP is missing or zero");
                } else if (entity.getWt() == null || entity.getWt() <= 0) {
                    dto.setEffKpiReason("WT is missing or zero");
                } else if (entity.getTotalOutput() == null) {
                    dto.setEffKpiReason("Output is missing");
                }
            }
            if (dto.getEffSalary() == null) {
                if (entity.getDli() == null || entity.getDli() <= 0) {
                    dto.setEffSalaryReason("DLI is missing or zero");
                } else if (masterData == null && !"N/A".equals(lookupArticle)) {
                    dto.setEffSalaryReason("Article '" + lookupArticle + "' not found in Master DB");
                } else {
                    dto.setEffSalaryReason("No Quota data for " + section + " in Master DB");
                }
            }
        } else {
            String reason = "Section '" + section + "' not configured";
            dto.setEffKpiReason(reason);
            dto.setEffSalaryReason(reason);
        }
    }

    private String getProductionMonth(DailyProduction entity) {
        return (entity.getProductionDate() != null)
                ? entity.getProductionDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : null;
    }

    /**
     * Group MasterDb records by articleNo. Multiple records per article are kept
     * so averaging can reproduce Excel's AVERAGEIFS behavior.
     */
    private Map<String, List<MasterDb>> buildMap(List<MasterDb> list) {
        return list.stream()
                .filter(m -> m.getArticleNo() != null)
                .collect(Collectors.groupingBy(MasterDb::getArticleNo));
    }

    /**
     * Average of non-null, positive values from the extractor across records.
     * Mirrors Excel AVERAGEIFS: multiple MasterDb rows for one articleNo are averaged.
     */
    private Double avgMetric(List<MasterDb> records, Function<MasterDb, Double> extractor) {
        if (records == null || records.isEmpty()) return null;
        double sum = 0.0;
        int n = 0;
        for (MasterDb m : records) {
            Double v = extractor.apply(m);
            if (v != null && v > 0) { sum += v; n++; }
        }
        return n > 0 ? sum / n : null;
    }

    /**
     * Actual PPH = Output / DLI / WT (uses Direct Labor Involved, not total MP).
     */
    private void calculateActualPph(DailyProductionDto dto, DailyProduction entity) {
        if (entity.getDli() != null && entity.getWt() != null
                && entity.getDli() > 0 && entity.getWt() > 0
                && entity.getTotalOutput() != null) {
            dto.setActualPph((double) entity.getTotalOutput() / entity.getDli() / entity.getWt());
        }
    }

    /**
     * EFF KPI = Output / TargetOutput, where TargetOutput = MP * WT * 3600 * Allowance / CT.
     */
    private void calculateEffKpi(DailyProductionDto dto, DailyProduction entity,
                                 Double ct, double allowance) {
        if (entity.getTotalOutput() != null && entity.getMp() != null && entity.getWt() != null
                && entity.getMp() > 0 && entity.getWt() > 0 && ct != null && ct > 0) {
            double targetOutput = (entity.getMp() * entity.getWt() * 3600.0 * allowance) / ct;
            double effKpi = (targetOutput > 0) ? (entity.getTotalOutput() / targetOutput) : 0.0;
            dto.setEffKpi(effKpi);
        }
    }

    /**
     * EFF Salary = Output / (SUM(PPH*MP) / AVG(MP) * DLI * Allowance).
     * Uses pre-loaded masterDbMap and resolves the 1ST/2ND column per slot
     * based on each article's "-2" suffix.
     */
    private void calculateEffSalary(DailyProductionDto dto, DailyProduction entity,
                                    String rowSection, Double dbQuota, Double dbMp,
                                    double allowance,
                                    List<DailyProductionDetail> salaryDetails,
                                    Map<String, List<MasterDb>> masterDbMap) {
        if (entity.getTotalOutput() == null || entity.getDli() == null || entity.getDli() <= 0) {
            return;
        }

        double sumQuota = 0.0;
        double sumMp = 0.0;
        int slotCount = 0;

        if (!salaryDetails.isEmpty()) {
            for (DailyProductionDetail detail : salaryDetails) {
                String raw = detail.getArticleNo();
                SectionMetrics.ArticleKey key = SectionMetrics.ArticleKey.parse(raw);
                List<MasterDb> list = masterDbMap.get(key.cleanedArticle());
                if (list == null || list.isEmpty()) continue;
                SectionMetrics.ResolvedSection res = SectionMetrics.resolveSlot(rowSection, raw);
                if (res.primary() == null) continue;
                final SectionMetrics pSm = res.primary();
                final SectionMetrics fbSm = res.fallback();
                Double slotQuotaDb = avgMetric(list, m -> pSm.getQuotaOrFallback(m, fbSm));
                Double slotMp = avgMetric(list, m -> pSm.getMpOrFallback(m, fbSm));
                if (slotQuotaDb != null && slotMp != null && slotMp > 0) {
                    sumQuota += slotQuotaDb / 10.0;
                    sumMp += slotMp;
                    slotCount++;
                }
            }
        }

        if (slotCount > 0 && sumQuota > 0) {
            // REF TIME adjustment: when >10 slots, last slot counts as half
            double refTime = (slotCount > 10) ? slotCount - 0.5 : slotCount;
            double adjustedSumQuota = sumQuota * refTime / slotCount;
            double avgMp = sumMp / slotCount;
            double salaryTarget = (adjustedSumQuota / avgMp) * entity.getDli() * allowance;
            double effSalary = (salaryTarget > 0) ? (entity.getTotalOutput() / salaryTarget) : 0.0;
            dto.setEffSalary(effSalary);
        } else if (dbQuota != null && dbQuota > 0 && dbMp != null && dbMp > 0) {
            // Fallback: use single MasterDb Quota (already averaged by caller)
            double slotQuota = dbQuota / 10.0;
            double salaryTarget = slotQuota / dbMp * entity.getDli() * allowance;
            double effSalary = (salaryTarget > 0) ? (entity.getTotalOutput() / salaryTarget) : 0.0;
            dto.setEffSalary(effSalary);
        }
    }

    /**
     * Calculate Target (multi-article weighted), EFF%, and Gap.
     * Uses pre-loaded masterDbMap to avoid N+1 queries.
     */
    private void calculateTarget(DailyProductionDto dto, DailyProduction entity,
                                 boolean hasMasterData, String rowSection,
                                 Double displayPph,
                                 Double ct, double allowance,
                                 List<DailyProductionDetail> validDetails,
                                 Map<String, List<MasterDb>> masterDbMap) {
        if (validDetails.isEmpty() || entity.getMp() == null || entity.getWt() == null
                || entity.getMp() <= 0 || entity.getWt() <= 0) {
            return;
        }

        int totalSlots = validDetails.size();
        // Group by raw article (keep "-2" so slot-level resolution works)
        Map<String, Long> countByArticle = validDetails.stream()
                .collect(Collectors.groupingBy(d -> d.getArticleNo().trim(), Collectors.counting()));

        double totalTarget = 0.0;
        boolean hasValidPph = false;

        for (Map.Entry<String, Long> entry : countByArticle.entrySet()) {
            String rawArt = entry.getKey();
            long slotCount = entry.getValue();

            SectionMetrics.ArticleKey key = SectionMetrics.ArticleKey.parse(rawArt);
            List<MasterDb> list = masterDbMap.get(key.cleanedArticle());
            if (list == null || list.isEmpty()) continue;
            SectionMetrics.ResolvedSection res = SectionMetrics.resolveSlot(rowSection, rawArt);
            if (res.primary() == null) continue;
            final SectionMetrics pSm = res.primary();
            final SectionMetrics fbSm = res.fallback();
            Double artPph = avgMetric(list, m -> pSm.getPphOrFallback(m, fbSm));
            if (artPph != null && artPph > 0) {
                hasValidPph = true;
                double fractionWt = entity.getWt() * ((double) slotCount / totalSlots);
                totalTarget += (entity.getMp() * fractionWt * artPph * allowance);
            }
        }

        if (hasValidPph && totalTarget > 0) {
            dto.setTarget(totalTarget);
            if (hasMasterData) {
                dto.setTct(ct);
                dto.setPph(displayPph);
            }
            if (entity.getTotalOutput() != null) {
                dto.setEff(((double) entity.getTotalOutput() / totalTarget) * 100.0);
                dto.setGap(entity.getTotalOutput() - totalTarget);
            }
        } else if (ct != null && ct > 0) {
            // Fallback: CT-based target
            dto.setTct(ct);
            double targetTct = (entity.getWt() * entity.getMp() * 3600.0 * allowance) / ct;
            dto.setTarget(targetTct);
            if (entity.getTotalOutput() != null && targetTct > 0) {
                dto.setEff(((double) entity.getTotalOutput() / targetTct) * 100.0);
                dto.setGap(entity.getTotalOutput() - targetTct);
            }
        }
    }

    /**
     * Calculate weighted-average CT based on time-slot proportions.
     * weightedCt = SUM(CT_i * slots_i) / totalSlots
     * Falls back to single-article CT if no valid details.
     */
    private Double calculateWeightedCt(String rowSection, Double singleArticleCt,
                                        List<DailyProductionDetail> validDetails,
                                        Map<String, List<MasterDb>> masterDbMap) {
        if (validDetails.isEmpty()) {
            return singleArticleCt;
        }

        Map<String, Long> countByArticle = validDetails.stream()
                .collect(Collectors.groupingBy(d -> d.getArticleNo().trim(), Collectors.counting()));

        double weightedCtSum = 0.0;
        long slotsWithValidCt = 0;

        for (Map.Entry<String, Long> entry : countByArticle.entrySet()) {
            String rawArt = entry.getKey();
            long slotCount = entry.getValue();

            SectionMetrics.ArticleKey key = SectionMetrics.ArticleKey.parse(rawArt);
            List<MasterDb> list = masterDbMap.get(key.cleanedArticle());
            if (list == null || list.isEmpty()) continue;
            SectionMetrics.ResolvedSection res = SectionMetrics.resolveSlot(rowSection, rawArt);
            if (res.primary() == null) continue;
            final SectionMetrics pSm = res.primary();
            final SectionMetrics fbSm = res.fallback();
            Double artCt = avgMetric(list, m -> pSm.getCtOrFallback(m, fbSm));
            if (artCt != null && artCt > 0) {
                weightedCtSum += artCt * slotCount;
                slotsWithValidCt += slotCount;
            }
        }

        if (slotsWithValidCt > 0) {
            return weightedCtSum / slotsWithValidCt;
        }
        return singleArticleCt;
    }

    /**
     * Batch load MasterDb for a list of articleNos — one query instead of N queries.
     * Month-aware with fallback to any-month.
     * Returns a map keyed by articleNo → list of all matching records (for averaging).
     */
    private Map<String, List<MasterDb>> batchLoadMasterDb(Collection<String> articleNos, String dataMonth) {
        if (articleNos == null || articleNos.isEmpty()) return new HashMap<>();
        List<String> distinct = articleNos.stream().distinct().collect(Collectors.toList());

        List<MasterDb> results;
        if (dataMonth != null) {
            results = masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(distinct, dataMonth);
            if (results.isEmpty()) {
                results = masterDbRepository.findByArticleNoInOrderByRefAsc(distinct);
            }
        } else {
            results = masterDbRepository.findByArticleNoInOrderByRefAsc(distinct);
        }

        return buildMap(results);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    double normalizeAllowance(Double allowance) {
        return NormalizationUtil.normalizeAllowance(allowance);
    }

    /**
     * Get valid (non-blank article) details from a production entity.
     */
    private List<DailyProductionDetail> getValidDetails(DailyProduction entity) {
        if (entity.getDetails() == null) return new ArrayList<>();
        return entity.getDetails().stream()
                .filter(d -> d.getArticleNo() != null && !d.getArticleNo().trim().isEmpty())
                .collect(Collectors.toList());
    }
}
