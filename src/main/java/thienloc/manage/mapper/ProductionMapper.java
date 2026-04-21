package thienloc.manage.mapper;

import org.springframework.stereotype.Component;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts between {@link DailyProduction} entities and {@link DailyProductionDto}.
 * Extracted from ProductionService.convertToDto() to keep the service focused on
 * business logic and repository orchestration.
 *
 * Field mapping only — no DB queries or efficiency calculations.
 * Efficiency population is still handled by EfficiencyCalculatorService.
 */
@Component
public class ProductionMapper {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Map a {@link DailyProduction} entity to a {@link DailyProductionDto}.
     * Builds the display article string (with "(+)" suffix for multi-article rows),
     * the detail DTOs, and the articlesJson map for client-side editing.
     */
    public DailyProductionDto toDto(DailyProduction entity) {
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
            dto.setCreatedAt(entity.getCreatedAt().format(TIMESTAMP_FMT));
        }
        if (entity.getCreatedBy() != null) {
            dto.setCreatedBy(entity.getCreatedBy().getUsername());
        }

        mapDetails(entity, dto);

        String ref = (entity.getSection() != null ? entity.getSection() : "") +
                     (entity.getLine()    != null ? entity.getLine()    : "");
        dto.setRef(ref);
        dto.setOutput(entity.getTotalOutput());

        return dto;
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void mapDetails(DailyProduction entity, DailyProductionDto dto) {
        if (entity.getDetails() == null || entity.getDetails().isEmpty()) {
            dto.setArticle("N/A");
            return;
        }

        List<String> distinctArticles = entity.getDetails().stream()
                .map(DailyProductionDetail::getArticleNo)
                .filter(a -> a != null && !a.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        String displayArticle = "N/A";
        if (!distinctArticles.isEmpty()) {
            displayArticle = distinctArticles.get(0);
            if (distinctArticles.size() > 1) {
                displayArticle += " (+)";
                dto.setArticleTooltip(String.join(", ", distinctArticles));
            }
        }
        dto.setArticle(displayArticle);

        List<DailyProductionDetailDto> detailDtos = entity.getDetails().stream()
                .map(d -> {
                    DailyProductionDetailDto dDto = new DailyProductionDetailDto();
                    dDto.setTimeSlot(d.getTimeSlot());
                    dDto.setArticleNo(d.getArticleNo());
                    dDto.setOutput(d.getOutput());
                    return dDto;
                })
                .collect(Collectors.toList());
        dto.setDetails(detailDtos);
        dto.setArticlesJson(buildArticlesJson(detailDtos));
    }

    private String buildArticlesJson(List<DailyProductionDetailDto> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (DailyProductionDetailDto d : details) {
            if (d.getTimeSlot() != null && d.getArticleNo() != null && !d.getArticleNo().isEmpty()) {
                if (!first) json.append(",");
                json.append("\"").append(d.getTimeSlot()).append("\":\"")
                        .append(d.getArticleNo().replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"");
                first = false;
            }
        }
        json.append("}");
        return json.toString();
    }
}
