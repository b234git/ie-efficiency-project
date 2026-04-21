package thienloc.manage.mapper;

import org.springframework.stereotype.Component;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.SplitEntry;
import thienloc.manage.entity.SplitEntryDetail;
import thienloc.manage.entity.SplitEntryStatus;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts between {@link SplitEntry} / {@link DailyProduction} entities and
 * {@link SplitEntryDto}.
 * Extracted from the two near-identical conversion methods that existed in
 * SplitEntryService (convertToDto and convertDpToSplitDto).
 */
@Component
public class SplitEntryMapper {

    /**
     * Map a {@link SplitEntry} entity to {@link SplitEntryDto}.
     */
    public SplitEntryDto toDto(SplitEntry entity) {
        SplitEntryDto dto = new SplitEntryDto();
        dto.setId(entity.getId());
        dto.setProductionDate(entity.getProductionDate());
        dto.setSection(entity.getSection());
        dto.setLine(entity.getLine());
        dto.setMp(entity.getMp());
        dto.setDli(entity.getDli());
        dto.setIdl(entity.getIdl());
        dto.setWt(entity.getWt());
        dto.setTotalOutput(entity.getTotalOutput());
        dto.setRft(entity.getRft());
        dto.setAllowance(entity.getAllowance() != null ? entity.getAllowance() * 100.0 : 100.0);
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setManpowerFilled(entity.getMp() != null);
        dto.setOutputFilled(entity.getTotalOutput() != null);
        dto.setArticlesFilled(entity.getDetails() != null && !entity.getDetails().isEmpty());

        if (entity.getManpowerFilledBy() != null)
            dto.setManpowerFilledByUsername(entity.getManpowerFilledBy().getUsername());
        if (entity.getOutputFilledBy() != null)
            dto.setOutputFilledByUsername(entity.getOutputFilledBy().getUsername());
        if (entity.getArticleFilledBy() != null)
            dto.setArticleFilledByUsername(entity.getArticleFilledBy().getUsername());

        List<SplitEntryDetail> rawDetails = entity.getDetails() != null
                ? entity.getDetails() : Collections.emptyList();
        dto.setDetails(mapSplitDetails(rawDetails));

        return dto;
    }

    /**
     * Map a {@link DailyProduction} entity to a {@link SplitEntryDto} so that
     * direct-entry records can be shown in the split-entry list view alongside
     * true SplitEntry records.
     */
    public SplitEntryDto fromDailyProduction(DailyProduction dp) {
        SplitEntryDto dto = new SplitEntryDto();
        dto.setId(dp.getId());
        dto.setProductionDate(dp.getProductionDate());
        dto.setSection(dp.getSection());
        dto.setLine(dp.getLine());
        dto.setMp(dp.getMp());
        dto.setDli(dp.getDli());
        dto.setIdl(dp.getIdl());
        dto.setWt(dp.getWt());
        dto.setTotalOutput(dp.getTotalOutput());
        dto.setRft(dp.getRft());
        dto.setAllowance(dp.getAllowance() != null ? dp.getAllowance() * 100.0 : 100.0);
        dto.setStatus(SplitEntryStatus.SYNCED.name());
        dto.setManpowerFilled(true);
        dto.setOutputFilled(true);
        dto.setArticlesFilled(dp.getDetails() != null && !dp.getDetails().isEmpty());
        dto.setSource("DIRECT");

        if (dp.getCreatedBy() != null)
            dto.setManpowerFilledByUsername(dp.getCreatedBy().getUsername());

        if (dp.getDetails() != null) {
            dto.setDetails(dp.getDetails().stream()
                    .map(d -> DailyProductionDetailDto.builder()
                            .timeSlot(d.getTimeSlot())
                            .articleNo(d.getArticleNo())
                            .output(d.getOutput())
                            .build())
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private List<DailyProductionDetailDto> mapSplitDetails(List<SplitEntryDetail> details) {
        return details.stream()
                .map(d -> DailyProductionDetailDto.builder()
                        .timeSlot(d.getTimeSlot())
                        .articleNo(d.getArticleNo())
                        .output(d.getOutput())
                        .build())
                .collect(Collectors.toList());
    }
}
