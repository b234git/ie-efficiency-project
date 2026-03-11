package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductionService {

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private UserService userService;

    public void saveDailyProduction(DailyProductionDto dto, String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found: " + username);
        }

        // Normalize allowance: form sends 0-100, store as 0.0-1.0
        double allowanceVal = 1.0;
        if (dto.getAllowance() != null && dto.getAllowance() > 0) {
            // If user entered > 1 assume it's a percentage (e.g. 80 -> 0.80)
            allowanceVal = dto.getAllowance() > 1 ? dto.getAllowance() / 100.0 : dto.getAllowance();
        }

        DailyProduction entity;
        if (dto.getId() != null) {
            entity = productionRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Record not found"));
            entity.setProductionDate(dto.getProductionDate());
            entity.setSection(dto.getSection());
            entity.setLine(dto.getLine());
            entity.setMp(dto.getMp());
            entity.setDli(dto.getDli());
            entity.setIdl(dto.getIdl());
            entity.setWt(dto.getWt());
            entity.setRft(dto.getRft());
            entity.setAllowance(allowanceVal);
        } else {
            entity = DailyProduction.builder()
                    .productionDate(dto.getProductionDate())
                    .section(dto.getSection())
                    .line(dto.getLine())
                    .mp(dto.getMp())
                    .dli(dto.getDli())
                    .idl(dto.getIdl())
                    .wt(dto.getWt())
                    .rft(dto.getRft())
                    .allowance(allowanceVal)
                    .createdBy(user)
                    .build();
        }

        // Process time-slot details
        entity.getDetails().clear();
        if (dto.getDetails() != null) {
            for (thienloc.manage.dto.DailyProductionDetailDto detailDto : dto.getDetails()) {
                if (detailDto.getArticleNo() != null && !detailDto.getArticleNo().trim().isEmpty()) {
                    thienloc.manage.entity.DailyProductionDetail detail = thienloc.manage.entity.DailyProductionDetail
                            .builder()
                            .dailyProduction(entity)
                            .timeSlot(detailDto.getTimeSlot())
                            .articleNo(detailDto.getArticleNo())
                            .output(0)
                            .build();
                    entity.getDetails().add(detail);
                }
            }
        }
        entity.setTotalOutput(dto.getOutput() != null ? dto.getOutput() : 0);
        productionRepository.save(entity);
    }

    public List<DailyProductionDto> getDashboardData(LocalDate date) {
        List<DailyProduction> records = productionRepository.findByProductionDateOrderBySectionAscLineAsc(date);
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public List<DailyProductionDto> getDashboardDataRange(LocalDate from, LocalDate to) {
        List<DailyProduction> records = productionRepository
                .findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to);
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public List<DailyProductionDto> getMyDataRange(String username, LocalDate from, LocalDate to) {
        List<DailyProduction> records = productionRepository
                .findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(username, from,
                        to);
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public List<DailyProductionDto> getMyDataAllTime(String username) {
        List<DailyProduction> records = productionRepository
                .findByCreatedBy_UsernameOrderByProductionDateDescSectionAsc(username);
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public List<DailyProductionDto> getDashboardDataAllTime() {
        List<DailyProduction> records = productionRepository
                .findAllByOrderByProductionDateDescSectionAscLineAsc();
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public Page<DailyProductionDto> getUserEntries(String username, Pageable pageable) {
        Page<DailyProduction> records = productionRepository
                .findByCreatedBy_UsernameOrderByIdDesc(username, pageable);
        return records.map(this::convertToDtoAndCalculateEff);
    }

    public DailyProductionDto getById(Long id) {
        DailyProduction record = productionRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        return convertToDtoAndCalculateEff(record);
    }

    public void deleteRecord(Long id) {
        productionRepository.deleteById(id);
    }

    public void deleteMultipleRecords(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            productionRepository.deleteAllById(ids);
        }
    }

    private DailyProductionDto convertToDtoAndCalculateEff(DailyProduction entity) {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setId(entity.getId());
        dto.setProductionDate(entity.getProductionDate());
        dto.setSection(entity.getSection());
        dto.setLine(entity.getLine());
        dto.setMp(entity.getMp());
        dto.setDli(entity.getDli());
        dto.setIdl(entity.getIdl());
        dto.setWt(entity.getWt());
        dto.setRft(entity.getRft());
        dto.setAllowance(entity.getAllowance());
        if (entity.getCreatedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setCreatedAt(entity.getCreatedAt().format(formatter));
        }

        String displayArticle = "N/A";
        List<String> distinctArticles = new ArrayList<>();
        if (entity.getDetails() != null && !entity.getDetails().isEmpty()) {
            distinctArticles = entity.getDetails().stream()
                    .map(thienloc.manage.entity.DailyProductionDetail::getArticleNo)
                    .filter(a -> a != null && !a.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (!distinctArticles.isEmpty()) {
                displayArticle = distinctArticles.get(0);
                if (distinctArticles.size() > 1) {
                    displayArticle += " (+)";
                }
            }

            java.util.List<thienloc.manage.dto.DailyProductionDetailDto> detailDtos = entity.getDetails().stream()
                    .map(d -> {
                        thienloc.manage.dto.DailyProductionDetailDto dDto = new thienloc.manage.dto.DailyProductionDetailDto();
                        dDto.setTimeSlot(d.getTimeSlot());
                        dDto.setArticleNo(d.getArticleNo());
                        dDto.setOutput(d.getOutput());
                        return dDto;
                    }).collect(Collectors.toList());
            dto.setDetails(detailDtos);
        }
        dto.setArticle(displayArticle);
        dto.setOutput(entity.getTotalOutput());

        String ref = (entity.getSection() != null ? entity.getSection() : "") +
                (entity.getLine() != null ? entity.getLine() : "");
        dto.setRef(ref);

        MasterDb masterData = null;
        List<MasterDb> masterList = masterDbRepository.findByArticleNo(displayArticle.replace(" (+)", ""));
        if (masterList != null && !masterList.isEmpty()) {
            masterData = masterList.get(0);
            dto.setPatternNo(masterData.getPatternNo());
            dto.setShoeName(masterData.getShoeName());
            dto.setStdPph(getDynamicPphBySection(masterData, entity.getSection()));
        }

        if (entity.getMp() != null && entity.getWt() != null && entity.getMp() > 0 && entity.getWt() > 0
                && entity.getTotalOutput() != null) {
            dto.setActualPph((double) entity.getTotalOutput() / entity.getMp() / entity.getWt());
        }

        double allowance = (entity.getAllowance() != null && entity.getAllowance() > 0)
                ? (entity.getAllowance() > 1.0 ? entity.getAllowance() / 100.0 : entity.getAllowance())
                : 1.0;

        // --- Sheet D KPI Calculations ---
        Double stdPphObj = (masterData != null) ? getDynamicPphBySection(masterData, entity.getSection()) : null;
        double stdPph = (stdPphObj != null) ? stdPphObj : 0.0;
        Double ct = (masterData != null) ? getDynamicCtBySection(masterData, entity.getSection()) : null;
        Double dbMp = (masterData != null) ? getDynamicMpBySection(masterData, entity.getSection()) : null;
        Double dbQuota = (masterData != null) ? getDynamicQuotaBySection(masterData, entity.getSection()) : null;

        // 1) EFF KPI: mathematically reduces to actualPph / stdPph / Allowance, or
        // Output * CT / (MP * WT * 3600 * Allowance)
        if (entity.getTotalOutput() != null && entity.getMp() != null && entity.getWt() != null
                && entity.getMp() > 0 && entity.getWt() > 0 && ct != null && ct > 0) {
            double targetOutput = (entity.getMp() * entity.getWt() * 3600.0 * allowance) / ct;
            double effKpi = (targetOutput > 0) ? (entity.getTotalOutput() / targetOutput) : 0.0;
            dto.setEffKpi(effKpi);
        }

        // 2) EFF Salary: Output / (TotalQuota / AvgMP * DLI * Allowance)
        // Excel: AZ(per-slot quota) = PPH * MP, BO = SUM(AZ), CE = AVG(MP)
        // So: EFF Salary = Output / (SUM(PPH*MP) / AVG(MP) * DLI * Allowance)
        if (entity.getTotalOutput() != null && entity.getDli() != null && entity.getDli() > 0) {
            double sumQuota = 0.0;
            double sumMp = 0.0;
            int slotCount = 0;

            // Calculate TotalQuota and AvgMP from time-slot details
            List<thienloc.manage.entity.DailyProductionDetail> salaryDetails = (entity.getDetails() != null)
                    ? entity.getDetails().stream()
                            .filter(d -> d.getArticleNo() != null && !d.getArticleNo().trim().isEmpty())
                            .collect(Collectors.toList())
                    : new ArrayList<>();

            if (!salaryDetails.isEmpty()) {
                for (thienloc.manage.entity.DailyProductionDetail detail : salaryDetails) {
                    List<MasterDb> mList = masterDbRepository.findByArticleNo(detail.getArticleNo().trim());
                    if (mList != null && !mList.isEmpty()) {
                        MasterDb m = mList.get(0);
                        Double slotQuotaDb = getDynamicQuotaBySection(m, entity.getSection());
                        Double slotMp = getDynamicMpBySection(m, entity.getSection());
                        if (slotQuotaDb != null && slotMp != null && slotMp > 0) {
                            sumQuota += slotQuotaDb / 10.0; // per-slot quota = Quota / 10
                            sumMp += slotMp;
                            slotCount++;
                        }
                    }
                }
            }

            if (slotCount > 0 && sumQuota > 0) {
                // REF TIME adjustment: Excel CF = IF(COUNT>10, COUNT-0.5, COUNT)
                // When >10 slots, last slot counts as half (e.g., 11 slots → refTime=10.5)
                double refTime = (slotCount > 10) ? slotCount - 0.5 : slotCount;
                double adjustedSumQuota = sumQuota * refTime / slotCount;

                double avgMp = sumMp / slotCount;
                double salaryTarget = (adjustedSumQuota / avgMp) * entity.getDli() * allowance;
                double effSalary = (salaryTarget > 0) ? (entity.getTotalOutput() / salaryTarget) : 0.0;
                dto.setEffSalary(effSalary);
            } else if (dbQuota != null && dbQuota > 0 && dbMp != null && dbMp > 0) {
                // Fallback: use single MasterDb Quota when no details available
                double slotQuota = dbQuota / 10.0;
                double salaryTarget = slotQuota / dbMp * entity.getDli() * allowance;
                double effSalary = (salaryTarget > 0) ? (entity.getTotalOutput() / salaryTarget) : 0.0;
                dto.setEffSalary(effSalary);
            }
        }
        // ---------------------------------

        List<thienloc.manage.entity.DailyProductionDetail> validDetails = null;
        if (entity.getDetails() != null) {
            validDetails = entity.getDetails().stream()
                    .filter(d -> d.getArticleNo() != null && !d.getArticleNo().trim().isEmpty())
                    .collect(Collectors.toList());
        }

        if (validDetails != null && !validDetails.isEmpty() && entity.getMp() != null && entity.getWt() != null
                && entity.getMp() > 0 && entity.getWt() > 0) {
            int totalSlots = validDetails.size();
            Map<String, Long> countByArticle = validDetails.stream()
                    .collect(Collectors.groupingBy(d -> d.getArticleNo().trim(), Collectors.counting()));

            double totalTarget = 0.0;
            boolean hasValidPph = false;

            for (Map.Entry<String, Long> entry : countByArticle.entrySet()) {
                String art = entry.getKey();
                long slotCount = entry.getValue();

                List<MasterDb> mList = masterDbRepository.findByArticleNo(art);
                if (mList != null && !mList.isEmpty()) {
                    MasterDb m = mList.get(0);
                    Double artPph = getDynamicPphBySection(m, entity.getSection());
                    if (artPph != null && artPph > 0) {
                        hasValidPph = true;
                        // Tỷ trọng thời gian cho mã này
                        double fractionWt = entity.getWt() * ((double) slotCount / totalSlots);
                        totalTarget += (entity.getMp() * fractionWt * artPph * allowance);
                    }
                }
            }

            if (hasValidPph && totalTarget > 0) {
                dto.setTarget(totalTarget);
                if (masterData != null) {
                    dto.setTct(ct);
                    dto.setPph(getDynamicPphBySection(masterData, entity.getSection()));
                }

                if (entity.getTotalOutput() != null) {
                    dto.setEff(((double) entity.getTotalOutput() / totalTarget) * 100.0);
                    dto.setGap(entity.getTotalOutput() - totalTarget);
                }
            } else if (ct != null && ct > 0) {
                // FALLBACK TCT
                dto.setTct(ct);
                double targetTct = (entity.getWt() * entity.getMp() * 3600.0 * allowance) / ct;
                dto.setTarget(targetTct);
                if (entity.getTotalOutput() != null && targetTct > 0) {
                    dto.setEff(((double) entity.getTotalOutput() / targetTct) * 100.0);
                    dto.setGap(entity.getTotalOutput() - targetTct);
                }
            }
        }
        return dto;
    }

    private Double getDynamicPphBySection(MasterDb masterDb, String section) {
        if (section == null || masterDb == null)
            return null;
        String s = section.toUpperCase().trim();
        switch (s) {
            case "SEW":
                return masterDb.getSewPph();
            case "BUFFING 1ST":
                return masterDb.getBuff1stPph();
            case "BUFFING 2ND":
                return masterDb.getBuff2ndPph();
            case "STOCKFIT UV":
                return masterDb.getStockfitUvPph();
            case "STOCKFIT 1ST":
                return masterDb.getStockfit1stPph();
            case "STOCKFIT 2ND":
                return masterDb.getStockfit2ndPph();
            case "ASSY":
            case "ASSEMBLY":
            case "ASSEMBLY BIG":
                return masterDb.getAssemBigPph() != null ? masterDb.getAssemBigPph() : masterDb.getAssemSmallPph();
            case "ASSEMBLY SMALL":
                return masterDb.getAssemSmallPph();
            default:
                return null;
        }
    }

    private Double getDynamicCtBySection(MasterDb masterDb, String section) {
        if (section == null || masterDb == null)
            return null;
        String s = section.toUpperCase().trim();
        switch (s) {
            case "SEW":
                return masterDb.getSewCt();
            case "BUFFING 1ST":
                return masterDb.getBuff1stCt();
            case "BUFFING 2ND":
                return masterDb.getBuff2ndCt();
            case "STOCKFIT UV":
                return masterDb.getStockfitUvCt();
            case "STOCKFIT 1ST":
                return masterDb.getStockfit1stCt();
            case "STOCKFIT 2ND":
                return masterDb.getStockfit2ndCt();
            case "ASSY":
            case "ASSEMBLY":
            case "ASSEMBLY BIG":
                return masterDb.getAssemBigCt() != null ? masterDb.getAssemBigCt() : masterDb.getAssemSmallCt();
            case "ASSEMBLY SMALL":
                return masterDb.getAssemSmallCt();
            default:
                return null;
        }
    }

    private Double getDynamicMpBySection(MasterDb masterDb, String section) {
        if (section == null || masterDb == null)
            return null;
        String s = section.toUpperCase().trim();
        switch (s) {
            case "SEW":
                return masterDb.getSewMp();
            case "BUFFING 1ST":
                return masterDb.getBuff1stMp();
            case "BUFFING 2ND":
                return masterDb.getBuff2ndMp();
            case "STOCKFIT UV":
                return masterDb.getStockfitUvMp();
            case "STOCKFIT 1ST":
                return masterDb.getStockfit1stMp();
            case "STOCKFIT 2ND":
                return masterDb.getStockfit2ndMp();
            case "ASSY":
            case "ASSEMBLY":
            case "ASSEMBLY BIG":
                return masterDb.getAssemBigMp() != null ? masterDb.getAssemBigMp() : masterDb.getAssemSmallMp();
            case "ASSEMBLY SMALL":
                return masterDb.getAssemSmallMp();
            default:
                return null;
        }
    }

    private Double getDynamicQuotaBySection(MasterDb masterDb, String section) {
        if (section == null || masterDb == null)
            return null;
        String s = section.toUpperCase().trim();
        switch (s) {
            case "SEW":
                return masterDb.getSewQuotaDb();
            case "BUFFING 1ST":
                return masterDb.getBuff1stQuotaDb();
            case "BUFFING 2ND":
                return masterDb.getBuff2ndQuotaDb();
            case "STOCKFIT UV":
                return masterDb.getStockfitUvQuotaDb();
            case "STOCKFIT 1ST":
                return masterDb.getStockfit1stQuotaDb();
            case "STOCKFIT 2ND":
                return masterDb.getStockfit2ndQuotaDb();
            case "ASSY":
            case "ASSEMBLY":
            case "ASSEMBLY BIG":
                return masterDb.getAssemBigQuotaDb() != null ? masterDb.getAssemBigQuotaDb()
                        : masterDb.getAssemSmallQuotaDb();
            case "ASSEMBLY SMALL":
                return masterDb.getAssemSmallQuotaDb();
            default:
                return null;
        }
    }

    /**
     * Weekly EFF Report — groups daily production by Section+Line for a week,
     * calculates ACTUAL PPH vs STD PPH (replicates Sheet "%" logic).
     */
    public List<thienloc.manage.dto.WeeklyReportDto> getWeeklyReport(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);

        // Get all production records for the week
        List<DailyProduction> allRecords = productionRepository
                .findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(weekStart, weekEnd);

        // Group by section+line
        Map<String, List<DailyProduction>> grouped = new LinkedHashMap<>();
        for (DailyProduction rec : allRecords) {
            String key = rec.getSection() + "|" + rec.getLine();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(rec);
        }

        List<thienloc.manage.dto.WeeklyReportDto> blocks = new ArrayList<>();

        for (Map.Entry<String, List<DailyProduction>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String section = parts[0];
            String line = parts.length > 1 ? parts[1] : "";
            List<DailyProduction> records = entry.getValue();

            thienloc.manage.dto.WeeklyReportDto block = new thienloc.manage.dto.WeeklyReportDto();
            block.setSection(section);
            block.setLine(line);

            List<thienloc.manage.dto.WeeklyReportDto.DailyRow> dailyRows = new ArrayList<>();
            double sumEff = 0, sumActPph = 0, sumStdPph = 0, sumMp = 0, sumWt = 0;
            int sumOutput = 0, effCount = 0, stdCount = 0;

            for (DailyProduction rec : records) {
                thienloc.manage.dto.WeeklyReportDto.DailyRow row = new thienloc.manage.dto.WeeklyReportDto.DailyRow();
                row.setDate(rec.getProductionDate());
                row.setOutput(rec.getTotalOutput());
                row.setMp(rec.getMp());
                row.setWt(rec.getWt());

                // Get article from details
                String articleNo = "N/A";
                if (rec.getDetails() != null && !rec.getDetails().isEmpty()) {
                    articleNo = rec.getDetails().get(0).getArticleNo();
                }
                row.setArticleNo(articleNo);

                // Lookup MasterDb for pattern, shoe name, and STD PPH
                List<MasterDb> masterList = masterDbRepository.findByArticleNo(
                        articleNo.replace(" (+)", ""));
                if (masterList != null && !masterList.isEmpty()) {
                    MasterDb master = masterList.get(0);
                    row.setPatternNo(master.getPatternNo());
                    row.setShoeName(master.getShoeName());

                    Double stdPph = getDynamicPphBySection(master, section);
                    row.setStdPph(stdPph);
                    if (stdPph != null) {
                        sumStdPph += stdPph;
                        stdCount++;
                    }
                }

                // Calculate ACTUAL PPH and EFF
                if (rec.getMp() != null && rec.getWt() != null
                        && rec.getMp() > 0 && rec.getWt() > 0) {
                    double actualPph = (double) rec.getTotalOutput() / rec.getMp() / rec.getWt();
                    row.setActualPph(actualPph);
                    sumActPph += actualPph;

                    if (row.getStdPph() != null && row.getStdPph() > 0) {
                        double eff = actualPph / row.getStdPph();
                        row.setEff(eff);
                        sumEff += eff;
                        effCount++;
                    }
                }

                sumOutput += (rec.getTotalOutput() != null ? rec.getTotalOutput() : 0);
                sumMp += (rec.getMp() != null ? rec.getMp() : 0);
                sumWt += (rec.getWt() != null ? rec.getWt() : 0);

                dailyRows.add(row);
            }

            block.setDailyRows(dailyRows);

            // Summary row
            int n = records.size();
            thienloc.manage.dto.WeeklyReportDto.SummaryRow summary = new thienloc.manage.dto.WeeklyReportDto.SummaryRow();
            summary.setTotalOutput(sumOutput);
            summary.setDayCount(n);
            summary.setAvgMp(n > 0 ? sumMp / n : 0);
            summary.setAvgWt(n > 0 ? sumWt / n : 0);
            summary.setAvgEff(effCount > 0 ? sumEff / effCount : null);
            summary.setAvgActualPph(n > 0 ? sumActPph / n : null);
            summary.setAvgStdPph(stdCount > 0 ? sumStdPph / stdCount : null);

            block.setTotal(summary);
            blocks.add(block);
        }

        return blocks;
    }
}
