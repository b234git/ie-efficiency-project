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
import java.util.List;
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

        // if editing an existing record
        DailyProduction entity;
        if (dto.getId() != null) {
            entity = productionRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Record not found"));
            // User modifying must be manager, or we override it.
            // For now, we just update fields
            entity.setProductionDate(dto.getProductionDate());
            entity.setSection(dto.getSection());
            entity.setLine(dto.getLine());
            entity.setMp(dto.getMp());
            entity.setWt(dto.getWt());
        } else {
            entity = DailyProduction.builder()
                    .productionDate(dto.getProductionDate())
                    .section(dto.getSection())
                    .line(dto.getLine())
                    .mp(dto.getMp())
                    .wt(dto.getWt())
                    .createdBy(user)
                    .build();
        }

        // Process details
        entity.getDetails().clear();
        if (dto.getDetails() != null) {
            for (thienloc.manage.dto.DailyProductionDetailDto detailDto : dto.getDetails()) {
                if (detailDto.getArticleNo() != null && !detailDto.getArticleNo().trim().isEmpty()) {
                    thienloc.manage.entity.DailyProductionDetail detail = thienloc.manage.entity.DailyProductionDetail
                            .builder()
                            .dailyProduction(entity)
                            .timeSlot(detailDto.getTimeSlot())
                            .articleNo(detailDto.getArticleNo())
                            .output(0) // Logic changed: Output hourly is ignored
                            .build();
                    entity.getDetails().add(detail);
                }
            }
        }
        // totalOutput is now from dto directly per the new phase 3 rules
        entity.setTotalOutput(dto.getOutput() != null ? dto.getOutput() : 0);

        productionRepository.save(entity);
    }

    public List<DailyProductionDto> getDashboardData(LocalDate date) {
        List<DailyProduction> records = productionRepository.findByProductionDateOrderBySectionAscLineAsc(date);
        return records.stream().map(this::convertToDtoAndCalculateEff).collect(Collectors.toList());
    }

    public Page<DailyProductionDto> getUserEntries(String username, Pageable pageable) {
        Page<DailyProduction> records = productionRepository
                .findByCreatedBy_UsernameOrderByProductionDateDesc(username, pageable);
        return records.map(this::convertToDtoAndCalculateEff);
    }

    public DailyProductionDto getById(Long id) {
        DailyProduction record = productionRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        return convertToDtoAndCalculateEff(record);
    }

    public void deleteRecord(Long id) {
        productionRepository.deleteById(id);
    }

    private DailyProductionDto convertToDtoAndCalculateEff(DailyProduction entity) {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setId(entity.getId());
        dto.setProductionDate(entity.getProductionDate());
        dto.setSection(entity.getSection());
        dto.setLine(entity.getLine());
        if (entity.getCreatedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            dto.setCreatedAt(entity.getCreatedAt().format(formatter));
        }

        // At the dashboard level, we might have multiple articles. For simplicity, we
        // just say "Multiple" if there's more than 1
        String displayArticle = "N/A";
        if (entity.getDetails() != null && !entity.getDetails().isEmpty()) {
            displayArticle = entity.getDetails().get(0).getArticleNo();
            if (entity.getDetails().size() > 1) {
                displayArticle += " (+)";
            }

            // Map details to DTO
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
        dto.setMp(entity.getMp());
        dto.setWt(entity.getWt());

        // Derived field ref = e.g., SEW1A or ASSY3B
        // The excel has "Section" and "Line", e.g. section='SEW', line='1B'
        // Let's assume the DB master ref is just string concatenation.
        String ref = (entity.getSection() != null ? entity.getSection() : "") +
                (entity.getLine() != null ? entity.getLine() : "");
        dto.setRef(ref);

        MasterDb masterData = null;
        // Search masterDB primarily by ArticleNo
        List<MasterDb> masterList = masterDbRepository.findByArticleNo(displayArticle.replace(" (+)", ""));
        if (masterList != null && !masterList.isEmpty()) {
            masterData = masterList.get(0);
        }

        if (masterData != null) {
            dto.setTct(masterData.getTct());

            Double dynamicPph = getDynamicPphBySection(masterData, entity.getSection());
            dto.setPph(dynamicPph); // Get new dynamic PPH field based on Section

            // Calculate Eff = (Total Output / MP / WT) / PPH
            if (entity.getMp() != null && entity.getWt() != null && entity.getMp() > 0 && entity.getWt() > 0
                    && dynamicPph != null && dynamicPph > 0) {

                double eff = ((double) entity.getTotalOutput() / entity.getMp() / entity.getWt()) / dynamicPph;
                dto.setEff(eff);
            }

            // Calculate Target. With new PPH logic, Target = MP * WT * PPH
            if (dynamicPph != null && dynamicPph > 0 && entity.getMp() != null
                    && entity.getWt() != null) {
                double target = entity.getMp() * entity.getWt() * dynamicPph;
                dto.setTarget(target);
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
            case "ASSEMBLY BIG":
                return masterDb.getAssemBigPph();
            case "ASSEMBLY SMALL":
                return masterDb.getAssemSmallPph();
            default:
                return null;
        }
    }
}
