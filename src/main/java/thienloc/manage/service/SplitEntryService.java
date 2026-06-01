package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.SplitEntry;
import thienloc.manage.entity.SplitEntryDetail;
import thienloc.manage.entity.SplitEntryStatus;
import thienloc.manage.entity.User;
import thienloc.manage.mapper.SplitEntryMapper;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.SplitEntryRepository;
import thienloc.manage.util.NormalizationUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SplitEntryService implements ISplitEntryService {

    @Autowired
    private SplitEntryRepository splitEntryRepository;

    @Autowired
    private DailyProductionRepository dailyProductionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private IProductionService productionService;

    @Autowired
    private SplitEntryMapper splitEntryMapper;

    @Autowired
    private LineAssignmentService lineAssignmentService;

    // ─── Page 1: Manpower ─────────────────────────────────────────────────────────

    @Transactional
    public SplitEntry saveManpower(SplitEntryDto dto, String username) {
        User user = userService.findByUsername(username);
        lineAssignmentService.assertCanWrite(username, dto.getSection(), dto.getLine());
        SplitEntry entry = findOrCreate(dto.getProductionDate(), dto.getSection(), dto.getLine());

        entry.setMp(dto.getMp());
        entry.setDli(dto.getDli());
        entry.setIdl(dto.getIdl());
        entry.setManpowerFilledBy(user);

        entry = splitEntryRepository.save(entry);
        checkAndSync(entry);
        return entry;
    }

    // ─── Page 2: Output ───────────────────────────────────────────────────────────

    @Transactional
    public SplitEntry saveOutput(SplitEntryDto dto, String username) {
        User user = userService.findByUsername(username);
        lineAssignmentService.assertCanWrite(username, dto.getSection(), dto.getLine());
        SplitEntry entry = findOrCreate(dto.getProductionDate(), dto.getSection(), dto.getLine());

        entry.setWt(dto.getWt());
        entry.setTotalOutput(dto.getTotalOutput());
        entry.setRft(dto.getRft());
        entry.setOutputFilledBy(user);

        entry = splitEntryRepository.save(entry);
        checkAndSync(entry);
        return entry;
    }

    // ─── Page 3: Allowance & Articles ─────────────────────────────────────────────

    @Transactional
    public SplitEntry saveArticles(SplitEntryDto dto, String username) {
        User user = userService.findByUsername(username);
        lineAssignmentService.assertCanWrite(username, dto.getSection(), dto.getLine());
        SplitEntry entry = findOrCreate(dto.getProductionDate(), dto.getSection(), dto.getLine());

        entry.setAllowance(NormalizationUtil.normalizeAllowance(dto.getAllowance()));

        // Clear and rebuild details
        entry.getDetails().clear();
        if (dto.getDetails() != null) {
            for (DailyProductionDetailDto detailDto : dto.getDetails()) {
                if (detailDto.getArticleNo() != null && !detailDto.getArticleNo().trim().isEmpty()) {
                    SplitEntryDetail detail = SplitEntryDetail.builder()
                            .splitEntry(entry)
                            .timeSlot(detailDto.getTimeSlot())
                            .articleNo(detailDto.getArticleNo())
                            .output(0)
                            .build();
                    entry.getDetails().add(detail);
                }
            }
        }
        entry.setArticleFilledBy(user);

        entry = splitEntryRepository.save(entry);
        checkAndSync(entry);
        return entry;
    }

    // ─── Find or Create ───────────────────────────────────────────────────────────

    private SplitEntry findOrCreate(LocalDate date, String section, String line) {
        section = normalizeSection(section, line);

        Optional<SplitEntry> existing = splitEntryRepository
                .findByProductionDateAndSectionAndLine(date, section, line);

        if (existing.isPresent()) {
            return existing.get();
        }

        return SplitEntry.builder()
                .productionDate(date)
                .section(section)
                .line(line)
                .build();
    }

    /**
     * Auto-map certain section+line combos:
     * ASSEMBLY + line "5" → ASSEMBLY SMALL
     * ASSEMBLY + other lines → ASSEMBLY BIG
     */
    private String normalizeSection(String section, String line) {
        if ("ASSEMBLY".equalsIgnoreCase(section)) {
            return "5".equals(line)
                    ? SectionMetrics.ASSEMBLY_SMALL.getSectionName()
                    : SectionMetrics.ASSEMBLY_BIG.getSectionName();
        } else if ("ASSEMBLY BIG".equalsIgnoreCase(section) && "5".equals(line)) {
            return SectionMetrics.ASSEMBLY_SMALL.getSectionName();
        }
        return section;
    }

    // ─── Auto-sync to DailyProduction ─────────────────────────────────────────────

    private void checkAndSync(SplitEntry entry) {
        boolean ready = entry.getMp() != null
                && entry.getWt() != null
                && entry.getTotalOutput() != null;

        if (ready) {
            syncToDailyProduction(entry);
            entry.setStatus(SplitEntryStatus.SYNCED);
        } else {
            entry.setStatus(SplitEntryStatus.PARTIAL);
        }
        splitEntryRepository.save(entry);
    }

    private void syncToDailyProduction(SplitEntry se) {
        // Build a DailyProductionDto and reuse existing save logic
        DailyProductionDto dto = new DailyProductionDto();
        dto.setId(se.getLinkedDailyProductionId());
        dto.setProductionDate(se.getProductionDate());
        dto.setSection(se.getSection());
        dto.setLine(se.getLine());
        dto.setMp(se.getMp());
        dto.setDli(se.getDli());
        dto.setIdl(se.getIdl());
        dto.setWt(se.getWt());
        dto.setOutput(se.getTotalOutput());
        dto.setRft(se.getRft());

        // Allowance is already normalized (0-1), convert to percentage for ProductionService
        double allowancePercent = se.getAllowance() != null ? se.getAllowance() * 100.0 : 100.0;
        dto.setAllowance(allowancePercent);

        // Copy article details
        if (se.getDetails() != null && !se.getDetails().isEmpty()) {
            List<DailyProductionDetailDto> detailDtos = se.getDetails().stream()
                    .map(d -> DailyProductionDetailDto.builder()
                            .timeSlot(d.getTimeSlot())
                            .articleNo(d.getArticleNo())
                            .output(d.getOutput())
                            .build())
                    .collect(Collectors.toList());
            dto.setDetails(detailDtos);
        }

        // Determine createdBy: prefer manpowerFilledBy, fallback to outputFilledBy, then articleFilledBy
        String syncUsername = null;
        if (se.getManpowerFilledBy() != null) {
            syncUsername = se.getManpowerFilledBy().getUsername();
        } else if (se.getOutputFilledBy() != null) {
            syncUsername = se.getOutputFilledBy().getUsername();
        } else if (se.getArticleFilledBy() != null) {
            syncUsername = se.getArticleFilledBy().getUsername();
        }

        if (syncUsername != null) {
            Long savedId = productionService.saveDailyProduction(dto, syncUsername);
            se.setLinkedDailyProductionId(savedId);
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteEntry(Long id) {
        splitEntryRepository.deleteById(id);
    }

    @Transactional
    public void deleteMultiple(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            splitEntryRepository.deleteAllById(ids);
        }
    }

    /** Returns true if a row was deleted, false if no row with that id exists. Never throws on missing. */
    @Transactional
    public boolean deleteIfPresent(Long id) {
        if (id == null) return false;
        if (!splitEntryRepository.existsById(id)) return false;
        splitEntryRepository.deleteById(id);
        return true;
    }

    // ─── Query methods ────────────────────────────────────────────────────────────

    public Optional<SplitEntryDto> getByDateSectionLine(LocalDate date, String section, String line) {
        section = normalizeSection(section, line);
        return splitEntryRepository.findByProductionDateAndSectionAndLine(date, section, line)
                .map(this::convertToDto);
    }

    public List<SplitEntryDto> getEntriesForDate(LocalDate date) {
        List<SplitEntryDto> splitDtos = splitEntryRepository
                .findByProductionDateOrderBySectionAscLineAsc(date)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        splitDtos.forEach(d -> d.setSource("SPLIT"));

        // Also show direct DailyProduction entries not linked to any SplitEntry
        Set<Long> linkedIds = new HashSet<>(splitEntryRepository.findLinkedProductionIdsByDate(date));
        List<SplitEntryDto> directDtos = dailyProductionRepository
                .findByProductionDateOrderBySectionAscLineAsc(date)
                .stream()
                .filter(dp -> !linkedIds.contains(dp.getId()))
                .map(this::convertDpToSplitDto)
                .collect(Collectors.toList());

        List<SplitEntryDto> merged = new ArrayList<>(splitDtos);
        merged.addAll(directDtos);
        merged.sort(Comparator.comparing(SplitEntryDto::getSection)
                              .thenComparing(SplitEntryDto::getLine));
        return merged;
    }

    public List<SplitEntryDto> getEntriesForMonth(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<SplitEntryDto> splitDtos = splitEntryRepository
                .findByProductionDateBetweenOrderByDateAscSectionAscLineAsc(from, to)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        splitDtos.forEach(d -> d.setSource("SPLIT"));

        Set<Long> linkedIds = new HashSet<>(splitEntryRepository.findLinkedProductionIdsByDateRange(from, to));
        List<SplitEntryDto> directDtos = dailyProductionRepository
                .findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to)
                .stream()
                .filter(dp -> !linkedIds.contains(dp.getId()))
                .map(this::convertDpToSplitDto)
                .collect(Collectors.toList());

        List<SplitEntryDto> merged = new ArrayList<>(splitDtos);
        merged.addAll(directDtos);
        merged.sort(Comparator.comparing(SplitEntryDto::getProductionDate)
                              .thenComparing(SplitEntryDto::getSection)
                              .thenComparing(SplitEntryDto::getLine));
        return merged;
    }

    private SplitEntryDto convertDpToSplitDto(DailyProduction dp) {
        return splitEntryMapper.fromDailyProduction(dp);
    }

    private SplitEntryDto convertToDto(SplitEntry entity) {
        return splitEntryMapper.toDto(entity);
    }
}
