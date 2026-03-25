package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import thienloc.manage.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductionService {

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EfficiencyCalculatorService efficiencyCalculator;

    // ─── Save ────────────────────────────────────────────────────────────────────

    public Long saveDailyProduction(DailyProductionDto dto, String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + username);
        }

        // Auto-map ASSEMBLY line 5 → ASSEMBLY SMALL, others → ASSEMBLY BIG
        String section = dto.getSection();
        if ("ASSEMBLY".equalsIgnoreCase(section)) {
            section = "5".equals(dto.getLine())
                    ? SectionMetrics.ASSEMBLY_SMALL.getSectionName()
                    : SectionMetrics.ASSEMBLY_BIG.getSectionName();
            dto.setSection(section);
        }

        double allowanceVal = 1.0;
        if (dto.getAllowance() != null && dto.getAllowance() > 0) {
            allowanceVal = dto.getAllowance() > 1 ? dto.getAllowance() / 100.0 : dto.getAllowance();
        }

        DailyProduction entity;
        if (dto.getId() != null) {
            entity = productionRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
            entity.setProductionDate(dto.getProductionDate());
            entity.setSection(dto.getSection());
            entity.setLine(dto.getLine());
            entity.setMp(dto.getMp());
            entity.setDli(dto.getDli());
            entity.setIdl(dto.getIdl());
            entity.setWt(dto.getWt());
            entity.setRft(normalizeRft(dto.getRft()));
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
                    .rft(normalizeRft(dto.getRft()))
                    .allowance(allowanceVal)
                    .createdBy(user)
                    .build();
        }

        entity.getDetails().clear();
        if (dto.getDetails() != null) {
            for (DailyProductionDetailDto detailDto : dto.getDetails()) {
                if (detailDto.getArticleNo() != null && !detailDto.getArticleNo().trim().isEmpty()) {
                    DailyProductionDetail detail = DailyProductionDetail.builder()
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
        entity = productionRepository.save(entity);
        return entity.getId();
    }

    /** Normalize RFT: if stored as decimal (0-1), convert to percentage (0-100). */
    private Double normalizeRft(Double rft) {
        if (rft != null && rft > 0 && rft <= 1.0) {
            return rft * 100.0;
        }
        return rft;
    }

    // ─── Query methods ───────────────────────────────────────────────────────────

    public List<DailyProductionDto> getDashboardData(LocalDate date) {
        return convertAllToDtoAndCalculateEff(
                productionRepository.findByProductionDateOrderBySectionAscLineAsc(date));
    }

    public List<DailyProductionDto> getDashboardDataRange(LocalDate from, LocalDate to) {
        return convertAllToDtoAndCalculateEff(
                productionRepository.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to));
    }

    public List<DailyProductionDto> getMyDataRange(String username, LocalDate from, LocalDate to) {
        return convertAllToDtoAndCalculateEff(
                productionRepository.findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(username, from, to));
    }

    public Page<DailyProductionDto> getUserEntries(String username, Pageable pageable) {
        Page<DailyProduction> page = productionRepository.findByCreatedBy_UsernameOrderByIdDesc(username, pageable);
        List<DailyProductionDto> dtos = convertAllToDtoAndCalculateEff(page.getContent());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public Page<DailyProductionDto> getAllEntries(Pageable pageable) {
        Page<DailyProduction> page = productionRepository.findAllByOrderByIdDesc(pageable);
        List<DailyProductionDto> dtos = convertAllToDtoAndCalculateEff(page.getContent());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public DailyProductionDto getById(Long id) {
        DailyProduction record = productionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        return convertToDtoAndCalculateEff(record);
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    public void deleteRecord(Long id) {
        productionRepository.deleteById(id);
    }

    public void deleteMultipleRecords(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            productionRepository.deleteAllById(ids);
        }
    }

    // ─── DTO conversion ──────────────────────────────────────────────────────────

    /** Field mapping only — no DB queries for efficiency calculations. */
    private DailyProductionDto convertToDto(DailyProduction entity) {
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
            dto.setCreatedAt(entity.getCreatedAt().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (entity.getCreatedBy() != null) {
            dto.setCreatedBy(entity.getCreatedBy().getUsername());
        }

        // Build display article and detail DTOs
        String displayArticle = "N/A";
        if (entity.getDetails() != null && !entity.getDetails().isEmpty()) {
            List<String> distinctArticles = entity.getDetails().stream()
                    .map(DailyProductionDetail::getArticleNo)
                    .filter(a -> a != null && !a.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (!distinctArticles.isEmpty()) {
                displayArticle = distinctArticles.get(0);
                if (distinctArticles.size() > 1) {
                    displayArticle += " (+)";
                }
            }

            List<DailyProductionDetailDto> detailDtos = entity.getDetails().stream()
                    .map(d -> {
                        DailyProductionDetailDto dDto = new DailyProductionDetailDto();
                        dDto.setTimeSlot(d.getTimeSlot());
                        dDto.setArticleNo(d.getArticleNo());
                        dDto.setOutput(d.getOutput());
                        return dDto;
                    }).collect(Collectors.toList());
            dto.setDetails(detailDtos);

            // Build articlesJson: {"07:00-08:00":"Y1234", ...}
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (DailyProductionDetailDto d : detailDtos) {
                if (d.getTimeSlot() != null && d.getArticleNo() != null && !d.getArticleNo().isEmpty()) {
                    if (!first) json.append(",");
                    json.append("\"").append(d.getTimeSlot()).append("\":\"")
                        .append(d.getArticleNo().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    first = false;
                }
            }
            json.append("}");
            dto.setArticlesJson(json.toString());
        }
        dto.setArticle(displayArticle);
        dto.setOutput(entity.getTotalOutput());

        String ref = (entity.getSection() != null ? entity.getSection() : "") +
                (entity.getLine() != null ? entity.getLine() : "");
        dto.setRef(ref);

        return dto;
    }

    /** Single record: field mapping + efficiency calculation (used by getById). */
    private DailyProductionDto convertToDtoAndCalculateEff(DailyProduction entity) {
        DailyProductionDto dto = convertToDto(entity);
        efficiencyCalculator.populateEfficiencyMetrics(dto, entity);
        return dto;
    }

    /** Batch: field mapping + bulk MasterDb load + efficiency calculation. */
    private List<DailyProductionDto> convertAllToDtoAndCalculateEff(List<DailyProduction> entities) {
        List<DailyProductionDto> dtos = entities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        efficiencyCalculator.populateEfficiencyMetricsBatch(dtos, entities);
        return dtos;
    }

    // ─── Weekly Report ───────────────────────────────────────────────────────────

    public List<WeeklyReportDto> getWeeklyReport(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(7); // Fri→Fri inclusive = 8 days

        List<DailyProduction> allRecords = productionRepository
                .findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(weekStart, weekEnd);

        Map<String, List<DailyProduction>> grouped = new LinkedHashMap<>();
        for (DailyProduction rec : allRecords) {
            String key = rec.getSection() + "|" + rec.getLine();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(rec);
        }

        // Batch load MasterDb — 1 query thay vì N queries (N+1 fix)
        Set<String> allArticleNos = allRecords.stream()
                .filter(r -> r.getDetails() != null && !r.getDetails().isEmpty())
                .map(r -> r.getDetails().get(0).getArticleNo())
                .filter(a -> a != null && !a.trim().isEmpty() && !"N/A".equals(a))
                .map(a -> a.replace(" (+)", ""))
                .collect(Collectors.toSet());

        Map<String, MasterDb> masterDbMap = allArticleNos.isEmpty()
                ? Map.of()
                : masterDbRepository.findByArticleNoInOrderByRefAsc(allArticleNos)
                        .stream()
                        .collect(Collectors.toMap(MasterDb::getArticleNo, m -> m, (a, b) -> a));

        List<WeeklyReportDto> blocks = new ArrayList<>();

        for (Map.Entry<String, List<DailyProduction>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String section = parts[0];
            String line = parts.length > 1 ? parts[1] : "";
            List<DailyProduction> records = entry.getValue();

            WeeklyReportDto block = new WeeklyReportDto();
            block.setSection(section);
            block.setLine(line);

            List<WeeklyReportDto.DailyRow> dailyRows = new ArrayList<>();
            double sumEff = 0, sumActPph = 0, sumStdPph = 0, sumMp = 0, sumWt = 0;
            int sumOutput = 0, effCount = 0, stdCount = 0;

            Optional<SectionMetrics> smOpt = SectionMetrics.fromSection(section);

            for (DailyProduction rec : records) {
                WeeklyReportDto.DailyRow row = new WeeklyReportDto.DailyRow();
                row.setDate(rec.getProductionDate());
                row.setOutput(rec.getTotalOutput());
                row.setMp(rec.getMp());
                row.setWt(rec.getWt());

                String articleNo = "N/A";
                if (rec.getDetails() != null && !rec.getDetails().isEmpty()) {
                    articleNo = rec.getDetails().get(0).getArticleNo();
                }
                row.setArticleNo(articleNo);

                // Map lookup thay vì query DB (đã batch load ở trên)
                Optional<MasterDb> masterOpt = "N/A".equals(articleNo)
                        ? Optional.empty()
                        : Optional.ofNullable(masterDbMap.get(articleNo.replace(" (+)", "")));
                if (masterOpt.isPresent() && smOpt.isPresent()) {
                    MasterDb master = masterOpt.get();
                    row.setPatternNo(master.getPatternNo());
                    row.setShoeName(master.getShoeName());

                    Double stdPph = smOpt.get().getPph(master);
                    row.setStdPph(stdPph);
                    if (stdPph != null) {
                        sumStdPph += stdPph;
                        stdCount++;
                    }
                }

                if (rec.getDli() != null && rec.getWt() != null
                        && rec.getDli() > 0 && rec.getWt() > 0) {
                    double actualPph = (double) rec.getTotalOutput() / rec.getDli() / rec.getWt();
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

            int n = records.size();
            WeeklyReportDto.SummaryRow summary = new WeeklyReportDto.SummaryRow();
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
