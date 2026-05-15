package thienloc.manage.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.exception.ServiceUnavailableException;
import thienloc.manage.mapper.ProductionMapper;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.SplitEntryRepository;
import thienloc.manage.util.NormalizationUtil;

@Service
public class ProductionService implements IProductionService {

    private static final Logger log = LoggerFactory.getLogger(ProductionService.class);

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private SplitEntryRepository splitEntryRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private IEfficiencyCalculatorService efficiencyCalculator;

    @Autowired
    private ProductionMapper productionMapper;

    // ─── Save ────────────────────────────────────────────────────────────────────

    @Transactional
    public Long saveDailyProduction(DailyProductionDto dto, String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + username);
        }

        // Normalize section abbreviations; for ambiguous assembly inputs route by line
        String section = dto.getSection();
        if ("ASSEMBLY".equalsIgnoreCase(section) || "ASSY".equalsIgnoreCase(section)) {
            section = "5".equals(dto.getLine())
                    ? SectionMetrics.ASSEMBLY_SMALL.getSectionName()
                    : SectionMetrics.ASSEMBLY_BIG.getSectionName();
        } else if ("ASSEMBLY BIG".equalsIgnoreCase(section) && "5".equals(dto.getLine())) {
            section = SectionMetrics.ASSEMBLY_SMALL.getSectionName();
        } else {
            section = SectionMetrics.normalize(section);
        }
        dto.setSection(section);

        double allowanceVal = NormalizationUtil.normalizeAllowance(dto.getAllowance());

        DailyProduction entity;
        boolean isNew = dto.getId() == null;
        if (!isNew) {
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
        if (!isNew) {
            // Force orphan-removal DELETEs to run before the new INSERTs,
            // otherwise the (daily_production_id, time_slot) unique constraint fires.
            productionRepository.flush();
        }
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
        if (isNew) {
            entity = productionRepository.save(entity);
        }
        return entity.getId();
    }

    private Double normalizeRft(Double rft) {
        return NormalizationUtil.normalizeRft(rft);
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
                productionRepository
                        .findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(username,
                                from, to));
    }

    public List<DailyProductionDto> getMyDataRangeWithSplitEntries(String username, LocalDate from, LocalDate to) {
        List<DailyProductionDto> entries = getMyDataRange(username, from, to);
        entries.forEach(e -> e.setSource("ENTRY"));

        List<DailyProductionDto> splitRows = buildSplitDtos(from, to);

        List<DailyProductionDto> merged = new ArrayList<>(entries);
        merged.addAll(splitRows);
        return merged;
    }

    /** DB-level paginated version — section/line lọc tại DB, article/errorsOnly/source lọc in-memory trên trang. */
    public Page<DailyProductionDto> getMyDataRangeWithSplitEntriesPaged(
            String username, LocalDate from, LocalDate to,
            String section, String line, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        String sectionFilter = (section == null) ? "" : section;
        String lineFilter   = (line == null)    ? "" : line;

        // Pass 1: lấy IDs có LIMIT/OFFSET, section/line lọc tại DB
        Page<Long> idPage = productionRepository.findIdsByUsernameAndDateRange(
                username, from, to, sectionFilter, lineFilter, pageable);

        // Pass 2: load đúng entities + details bằng IDs (tránh N+1)
        List<DailyProductionDto> dtos = new ArrayList<>();
        if (!idPage.isEmpty()) {
            List<DailyProduction> entities = productionRepository.findByIdsWithDetails(idPage.getContent());
            dtos = convertAllToDtoAndCalculateEff(entities);
            dtos.forEach(e -> e.setSource("ENTRY"));
        }

        long totalElements = idPage.getTotalElements();

        // Page 0: prepend PARTIAL split entries (luôn ít, không cần phân trang)
        if (page == 0) {
            List<DailyProductionDto> splitRows = buildSplitDtos(from, to);

            // Áp dụng cùng filter section/line cho splits
            if (!sectionFilter.isEmpty()) {
                splitRows = splitRows.stream()
                        .filter(s -> sectionFilter.equals(s.getSection()))
                        .collect(Collectors.toList());
            }
            if (!lineFilter.isEmpty()) {
                String lowerLine = lineFilter.toLowerCase();
                splitRows = splitRows.stream()
                        .filter(s -> s.getLine() != null && s.getLine().toLowerCase().contains(lowerLine))
                        .collect(Collectors.toList());
            }

            List<DailyProductionDto> merged = new ArrayList<>(splitRows);
            merged.addAll(dtos);
            totalElements += splitRows.size();
            return new PageImpl<>(merged, pageable, totalElements);
        }

        return new PageImpl<>(dtos, pageable, totalElements);
    }

    /** Extract split entry rows thành PARTIAL DTOs (dùng chung cho cả 2 phương thức trên). */
    private List<DailyProductionDto> buildSplitDtos(LocalDate from, LocalDate to) {
        return splitEntryRepository.findPartialByDateRange(from, to)
                .stream()
                .map(se -> {
                    DailyProductionDto dto = new DailyProductionDto();
                    dto.setId(se.getId());
                    dto.setProductionDate(se.getProductionDate());
                    dto.setSection(se.getSection());
                    dto.setLine(se.getLine());
                    dto.setMp(se.getMp());
                    dto.setDli(se.getDli());
                    dto.setIdl(se.getIdl());
                    dto.setWt(se.getWt());
                    dto.setOutput(se.getTotalOutput() != null ? se.getTotalOutput() : 0);
                    dto.setRft(se.getRft());
                    dto.setAllowance(se.getAllowance() != null ? se.getAllowance() : 1.0);
                    dto.setSource("SPLIT");
                    dto.setCreatedBy(se.getManpowerFilledBy() != null
                            ? se.getManpowerFilledBy().getUsername() : "-");
                    dto.setArticle("N/A");
                    return dto;
                })
                .collect(Collectors.toList());
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

    @Transactional
    public void deleteRecord(Long id) {
        productionRepository.deleteById(id);
    }

    @Transactional
    public void deleteMultipleRecords(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            productionRepository.deleteAllById(ids);
        }
    }

    /** Returns true if a row was deleted, false if no row with that id exists. Never throws on missing. */
    @Transactional
    public boolean deleteIfPresent(Long id) {
        if (id == null) return false;
        if (!productionRepository.existsById(id)) return false;
        productionRepository.deleteById(id);
        return true;
    }

    /**
     * Delete a record only if it belongs to the given user.
     * Used by /entry/delete (USER role) to prevent IDOR attacks.
     * 
     * @throws ResourceNotFoundException if record doesn't exist or doesn't belong
     *                                   to user
     */
    public void deleteOwnRecord(Long id, String username) {
        DailyProduction record = productionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));
        if (record.getCreatedBy() == null || !record.getCreatedBy().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Record not found");
        }
        productionRepository.deleteById(id);
    }

    // ─── DTO conversion ──────────────────────────────────────────────────────────

    /** Field mapping only — no DB queries for efficiency calculations. */
    private DailyProductionDto convertToDto(DailyProduction entity) {
        return productionMapper.toDto(entity);
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

    @CircuitBreaker(name = "weeklyReport", fallbackMethod = "getWeeklyReportFallback")
    @Transactional(readOnly = true, timeout = 10)
    public List<WeeklyReportDto> getWeeklyReport(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(7); // Fri→Fri inclusive = 8 days

        List<DailyProduction> allRecords = productionRepository
                .findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(weekStart, weekEnd);

        // Reuse the same DTO conversion + efficiency calculation as the daily report
        List<DailyProductionDto> allDtos = convertAllToDtoAndCalculateEff(allRecords);

        // Group DTOs by section|line (with ASSEMBLY BIG line 5 → ASSEMBLY SMALL override)
        Map<String, List<DailyProductionDto>> grouped = new LinkedHashMap<>();
        for (DailyProductionDto dto : allDtos) {
            String section = dto.getSection();
            if ("ASSEMBLY BIG".equalsIgnoreCase(section) && "5".equals(dto.getLine())) {
                section = SectionMetrics.ASSEMBLY_SMALL.getSectionName();
            }
            String key = section + "|" + dto.getLine();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
        }

        List<WeeklyReportDto> blocks = new ArrayList<>();

        for (Map.Entry<String, List<DailyProductionDto>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String section = parts[0];
            String line = parts.length > 1 ? parts[1] : "";
            List<DailyProductionDto> dtos = entry.getValue();

            WeeklyReportDto block = new WeeklyReportDto();
            block.setSection(section);
            block.setLine(line);

            List<WeeklyReportDto.DailyRow> dailyRows = new ArrayList<>();
            double sumEff = 0, sumActPph = 0, sumStdPph = 0, sumMp = 0, sumWt = 0, sumDli = 0;
            int sumOutput = 0, effCount = 0, stdCount = 0, sumTargetOutput = 0, targetCount = 0;

            for (DailyProductionDto dto : dtos) {
                WeeklyReportDto.DailyRow row = new WeeklyReportDto.DailyRow();
                row.setDate(dto.getProductionDate());
                row.setOutput(dto.getOutput());
                row.setMp(dto.getMp());
                row.setWt(dto.getWt());
                row.setDli(dto.getDli());
                row.setArticleNo(dto.getArticle());
                row.setPatternNo(dto.getPatternNo());
                row.setShoeName(dto.getShoeName());
                row.setStdPph(dto.getStdPph());
                row.setActualPph(dto.getActualPph());

                // Use EFF KPI from daily report's EfficiencyCalculatorService
                if (dto.getEffKpi() != null) {
                    row.setEff(dto.getEffKpi());
                    sumEff += dto.getEffKpi();
                    effCount++;
                }

                // Use Target from daily report's calculation
                if (dto.getTarget() != null) {
                    row.setTargetOutput((int) Math.round(dto.getTarget()));
                    sumTargetOutput += row.getTargetOutput();
                    targetCount++;
                }

                if (dto.getStdPph() != null) { sumStdPph += dto.getStdPph(); stdCount++; }
                if (dto.getActualPph() != null) sumActPph += dto.getActualPph();
                sumOutput += (dto.getOutput() != null ? dto.getOutput() : 0);
                sumMp += (dto.getMp() != null ? dto.getMp() : 0);
                sumWt += (dto.getWt() != null ? dto.getWt() : 0);
                sumDli += (dto.getDli() != null ? dto.getDli() : 0);

                dailyRows.add(row);
            }

            block.setDailyRows(dailyRows);

            int n = dtos.size();
            WeeklyReportDto.SummaryRow summary = new WeeklyReportDto.SummaryRow();
            summary.setTotalOutput(sumOutput);
            summary.setDayCount(n);
            summary.setAvgMp(n > 0 ? sumMp / n : 0);
            summary.setAvgWt(n > 0 ? sumWt / n : 0);
            summary.setAvgEff(effCount > 0 ? sumEff / effCount : null);
            summary.setAvgActualPph(n > 0 ? sumActPph / n : null);
            summary.setAvgStdPph(stdCount > 0 ? sumStdPph / stdCount : null);
            summary.setAvgDli(n > 0 ? sumDli / n : 0);
            summary.setTotalTargetOutput(targetCount > 0 ? sumTargetOutput : null);

            block.setTotal(summary);
            blocks.add(block);
        }

        return blocks;
    }

    List<WeeklyReportDto> getWeeklyReportFallback(LocalDate weekStart, Throwable t) {
        log.warn("Circuit open for weeklyReport, weekStart={}: {}", weekStart, t.getMessage());
        throw new ServiceUnavailableException("Báo cáo tuần tạm thời không khả dụng. Vui lòng thử lại sau.");
    }

    public List<String> getDistinctMonths() {
        return productionRepository.findDistinctMonths();
    }

    public List<Long> getFilteredIds(String username, LocalDate from, LocalDate to, String section, String line) {
        String s = (section == null) ? "" : section;
        String l = (line == null) ? "" : line;
        return productionRepository.findAllIdsByUsernameAndDateRange(username, from, to, s, l);
    }
}
