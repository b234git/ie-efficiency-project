package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 */
@Service
public class EfficiencyCalculatorService {

    @Autowired
    private MasterDbRepository masterDbRepository;

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

        // Batch-load MasterDb for all articles in detail slots (month-aware)
        List<DailyProductionDetail> validDetails = getValidDetails(entity);
        Map<String, MasterDb> masterDbMap = batchLoadMasterDb(
                validDetails.stream().map(d -> d.getArticleNo().trim()).collect(Collectors.toList()),
                productionMonth);

        // Determine the lookup article from the DTO (already set by caller)
        String lookupArticle = dto.getArticle() != null ? dto.getArticle().replace(" (+)", "") : "N/A";

        // Lookup MasterDb (month-aware with fallback to batch cache)
        MasterDb masterData = null;
        if (!"N/A".equals(lookupArticle)) {
            Optional<MasterDb> masterOpt = Optional.empty();

            // Try month-specific lookup first
            if (productionMonth != null) {
                masterOpt = masterDbRepository
                        .findFirstByArticleNoAndDataMonthOrderByRefAsc(lookupArticle, productionMonth);
            }

            // Fallback: use batch cache (no extra query)
            if (masterOpt.isPresent()) {
                masterData = masterOpt.get();
            } else {
                masterData = masterDbMap.get(lookupArticle);
            }

            if (masterData != null) {
                dto.setPatternNo(masterData.getPatternNo());
                dto.setShoeName(masterData.getShoeName());
                if (sectionOpt.isPresent()) {
                    dto.setStdPph(sectionOpt.get().getPph(masterData));
                }
            }
        }

        // Actual PPH = Output / DLI / WT
        calculateActualPph(dto, entity);

        // Allowance normalization
        double allowance = normalizeAllowance(entity.getAllowance());

        if (sectionOpt.isPresent()) {
            SectionMetrics sm = sectionOpt.get();

            // Extract section-specific values from MasterDb
            Double singleCt = (masterData != null) ? sm.getCt(masterData) : null;
            Double dbMp = (masterData != null) ? sm.getMp(masterData) : null;
            Double dbQuota = (masterData != null) ? sm.getQuota(masterData) : null;

            // Weighted CT across all articles (proportional to time slots)
            Double weightedCt = calculateWeightedCt(sm, singleCt, validDetails, masterDbMap);

            // 1) EFF KPI (use weighted CT for multi-article accuracy)
            calculateEffKpi(dto, entity, weightedCt, allowance);

            // 2) EFF Salary (use pre-loaded map)
            calculateEffSalary(dto, entity, sm, dbQuota, dbMp, allowance, validDetails, masterDbMap);

            // 3) Target + EFF% + Gap (use pre-loaded map, weighted CT for fallback)
            calculateTarget(dto, entity, masterData, sm, weightedCt, allowance, validDetails, masterDbMap);

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

        // 1. Collect all unique articles and production months
        Set<String> allArticleNos = new HashSet<>();
        Set<String> allMonths = new HashSet<>();
        for (DailyProduction entity : entities) {
            String month = getProductionMonth(entity);
            if (month != null) allMonths.add(month);
            for (DailyProductionDetail d : getValidDetails(entity)) {
                allArticleNos.add(d.getArticleNo().trim());
            }
        }

        if (allArticleNos.isEmpty()) {
            for (int i = 0; i < dtos.size(); i++) {
                populateFromCache(dtos.get(i), entities.get(i),
                        new HashMap<>(), new HashMap<>());
            }
            return;
        }

        // 2. Batch load: per-month maps + fallback (no month filter)
        List<String> articleList = new ArrayList<>(allArticleNos);
        Map<String, Map<String, MasterDb>> monthMaps = new HashMap<>();
        for (String month : allMonths) {
            Map<String, MasterDb> map = buildMap(
                    masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(articleList, month));
            if (!map.isEmpty()) monthMaps.put(month, map);
        }
        Map<String, MasterDb> fallbackMap = buildMap(
                masterDbRepository.findByArticleNoInOrderByRefAsc(articleList));

        // 3. Process each record using cached data
        for (int i = 0; i < dtos.size(); i++) {
            populateFromCache(dtos.get(i), entities.get(i), monthMaps, fallbackMap);
        }
    }

    /**
     * Populate metrics for a single record using pre-loaded MasterDb cache.
     */
    private void populateFromCache(DailyProductionDto dto, DailyProduction entity,
                                    Map<String, Map<String, MasterDb>> monthMaps,
                                    Map<String, MasterDb> fallbackMap) {
        String section = entity.getSection();
        Optional<SectionMetrics> sectionOpt = SectionMetrics.fromSection(section);
        String productionMonth = getProductionMonth(entity);

        // Resolve masterDbMap: month-specific → fallback (matches batchLoadMasterDb logic)
        Map<String, MasterDb> masterDbMap;
        if (productionMonth != null && monthMaps.containsKey(productionMonth)) {
            masterDbMap = monthMaps.get(productionMonth);
        } else {
            masterDbMap = fallbackMap;
        }

        List<DailyProductionDetail> validDetails = getValidDetails(entity);
        String lookupArticle = dto.getArticle() != null ? dto.getArticle().replace(" (+)", "") : "N/A";

        // Lookup primary article: month-specific first, then fallback
        MasterDb masterData = null;
        if (!"N/A".equals(lookupArticle)) {
            if (productionMonth != null && monthMaps.containsKey(productionMonth)) {
                masterData = monthMaps.get(productionMonth).get(lookupArticle);
            }
            if (masterData == null) {
                masterData = fallbackMap.get(lookupArticle);
            }
            if (masterData != null) {
                dto.setPatternNo(masterData.getPatternNo());
                dto.setShoeName(masterData.getShoeName());
                if (sectionOpt.isPresent()) {
                    dto.setStdPph(sectionOpt.get().getPph(masterData));
                }
            }
        }

        calculateActualPph(dto, entity);
        double allowance = normalizeAllowance(entity.getAllowance());

        if (sectionOpt.isPresent()) {
            SectionMetrics sm = sectionOpt.get();
            Double singleCt = (masterData != null) ? sm.getCt(masterData) : null;
            Double dbMp = (masterData != null) ? sm.getMp(masterData) : null;
            Double dbQuota = (masterData != null) ? sm.getQuota(masterData) : null;
            Double weightedCt = calculateWeightedCt(sm, singleCt, validDetails, masterDbMap);

            calculateEffKpi(dto, entity, weightedCt, allowance);
            calculateEffSalary(dto, entity, sm, dbQuota, dbMp, allowance, validDetails, masterDbMap);
            calculateTarget(dto, entity, masterData, sm, weightedCt, allowance, validDetails, masterDbMap);

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

    private Map<String, MasterDb> buildMap(List<MasterDb> list) {
        Map<String, MasterDb> map = new HashMap<>();
        for (MasterDb m : list) map.putIfAbsent(m.getArticleNo(), m);
        return map;
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
     * Uses pre-loaded masterDbMap to avoid N+1 queries.
     */
    private void calculateEffSalary(DailyProductionDto dto, DailyProduction entity,
                                    SectionMetrics sm, Double dbQuota, Double dbMp,
                                    double allowance,
                                    List<DailyProductionDetail> salaryDetails,
                                    Map<String, MasterDb> masterDbMap) {
        if (entity.getTotalOutput() == null || entity.getDli() == null || entity.getDli() <= 0) {
            return;
        }

        double sumQuota = 0.0;
        double sumMp = 0.0;
        int slotCount = 0;

        if (!salaryDetails.isEmpty()) {
            for (DailyProductionDetail detail : salaryDetails) {
                MasterDb m = masterDbMap.get(detail.getArticleNo().trim());
                if (m != null) {
                    Double slotQuotaDb = sm.getQuota(m);
                    Double slotMp = sm.getMp(m);
                    if (slotQuotaDb != null && slotMp != null && slotMp > 0) {
                        sumQuota += slotQuotaDb / 10.0;
                        sumMp += slotMp;
                        slotCount++;
                    }
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
            // Fallback: use single MasterDb Quota
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
                                 MasterDb masterData, SectionMetrics sm,
                                 Double ct, double allowance,
                                 List<DailyProductionDetail> validDetails,
                                 Map<String, MasterDb> masterDbMap) {
        if (validDetails.isEmpty() || entity.getMp() == null || entity.getWt() == null
                || entity.getMp() <= 0 || entity.getWt() <= 0) {
            return;
        }

        int totalSlots = validDetails.size();
        Map<String, Long> countByArticle = validDetails.stream()
                .collect(Collectors.groupingBy(d -> d.getArticleNo().trim(), Collectors.counting()));

        double totalTarget = 0.0;
        boolean hasValidPph = false;

        for (Map.Entry<String, Long> entry : countByArticle.entrySet()) {
            String art = entry.getKey();
            long slotCount = entry.getValue();

            MasterDb m = masterDbMap.get(art);
            if (m != null) {
                Double artPph = sm.getPph(m);
                if (artPph != null && artPph > 0) {
                    hasValidPph = true;
                    double fractionWt = entity.getWt() * ((double) slotCount / totalSlots);
                    totalTarget += (entity.getMp() * fractionWt * artPph * allowance);
                }
            }
        }

        if (hasValidPph && totalTarget > 0) {
            dto.setTarget(totalTarget);
            if (masterData != null) {
                dto.setTct(ct);
                dto.setPph(sm.getPph(masterData));
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
    private Double calculateWeightedCt(SectionMetrics sm, Double singleArticleCt,
                                        List<DailyProductionDetail> validDetails,
                                        Map<String, MasterDb> masterDbMap) {
        if (validDetails.isEmpty()) {
            return singleArticleCt;
        }

        Map<String, Long> countByArticle = validDetails.stream()
                .collect(Collectors.groupingBy(d -> d.getArticleNo().trim(), Collectors.counting()));

        double weightedCtSum = 0.0;
        long slotsWithValidCt = 0;

        for (Map.Entry<String, Long> entry : countByArticle.entrySet()) {
            String art = entry.getKey();
            long slotCount = entry.getValue();

            MasterDb m = masterDbMap.get(art);
            if (m != null) {
                Double artCt = sm.getCt(m);
                if (artCt != null && artCt > 0) {
                    weightedCtSum += artCt * slotCount;
                    slotsWithValidCt += slotCount;
                }
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
     * Returns a map keyed by articleNo (first record by ref for each).
     */
    private Map<String, MasterDb> batchLoadMasterDb(Collection<String> articleNos, String dataMonth) {
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

        Map<String, MasterDb> map = new HashMap<>();
        for (MasterDb m : results) {
            map.putIfAbsent(m.getArticleNo(), m);
        }
        return map;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Normalize allowance: form sends 0-100 (e.g. 80), store/use as 0.0-1.0 (0.8).
     */
    double normalizeAllowance(Double allowance) {
        if (allowance == null || allowance <= 0) return 1.0;
        return allowance > 1.0 ? allowance / 100.0 : allowance;
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
