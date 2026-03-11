package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.ProductionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/entry")
public class ProductionController {

    @Autowired
    private ProductionService productionService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    /* ── Entry Form ───────────────────────────────────────────── */
    @GetMapping({ "", "/" })
    public String showEntryForm(
            @RequestParam(defaultValue = "0") int page,
            Model model,
            Principal principal) {

        DailyProductionDto dto = new DailyProductionDto();
        dto.setProductionDate(LocalDate.now());

        java.util.List<String> timeSlots = java.util.Arrays.asList(
                "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
                "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
                "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
                "19:00-20:00", "20:00-21:00", "21:00-22:00");
        for (String slot : timeSlots) {
            thienloc.manage.dto.DailyProductionDetailDto detail = new thienloc.manage.dto.DailyProductionDetailDto();
            detail.setTimeSlot(slot);
            dto.getDetails().add(detail);
        }

        model.addAttribute("production", dto);
        Pageable pageable = PageRequest.of(page, 10);
        model.addAttribute("historyPage", productionService.getUserEntries(principal.getName(), pageable));
        return "entry";
    }

    /* ── Save Entry ───────────────────────────────────────────── */
    @PostMapping("/save")
    public String saveEntry(DailyProductionDto dto, Principal principal) {
        productionService.saveDailyProduction(dto, principal.getName());
        systemLogService.logAction("ADD_ENTRY",
                "Added production entry for Section: " + dto.getSection() + ", Article: " + dto.getArticle());
        return "redirect:/entry/?success";
    }

    /* ── My Data View ─────────────────────────────────────────── */
    @GetMapping("/mydata")
    public String myData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article,
            Model model,
            Principal principal) {

        String username = principal.getName();
        LocalDate today = LocalDate.now();
        List<DailyProductionDto> records;
        String rangeLabel;

        switch (range) {
            case "1M":
                records = productionService.getMyDataRange(username, today.minusMonths(1), today);
                rangeLabel = "Last 1 month (" + today.minusMonths(1) + " → " + today + ")";
                break;
            case "6M":
                records = productionService.getMyDataRange(username, today.minusMonths(6), today);
                rangeLabel = "Last 6 months (" + today.minusMonths(6) + " → " + today + ")";
                break;
            case "ALL":
                records = productionService.getMyDataAllTime(username);
                rangeLabel = "All time";
                break;
            default:
                if (date == null)
                    date = today;
                records = productionService.getMyDataRange(username, date, date);
                rangeLabel = "";
                break;
        }

        // Filter by article if provided
        if (article != null && !article.trim().isEmpty()) {
            String lowerArticle = article.toLowerCase().trim();
            records = records.stream()
                    .filter(r -> (r.getArticle() != null && r.getArticle().toLowerCase().contains(lowerArticle)) ||
                            (r.getDetails() != null && r.getDetails().stream()
                                    .anyMatch(d -> d.getArticleNo() != null
                                            && d.getArticleNo().toLowerCase().contains(lowerArticle))))
                    .collect(java.util.stream.Collectors.toList());
        }

        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("selectedRange", range);
        model.addAttribute("rangeLabel", rangeLabel);
        model.addAttribute("article", article);
        model.addAttribute("records", records);

        // Pre-compute stats (Thymeleaf SpEL does NOT support lambdas)
        int totalOutput = records.stream().mapToInt(r -> r.getOutput() != null ? r.getOutput() : 0).sum();
        double avgEff = records.stream().filter(r -> r.getEff() != null)
                .mapToDouble(r -> r.getEff()).average().orElse(0);
        long effCount = records.stream().filter(r -> r.getEff() != null).count();
        model.addAttribute("totalOutput", totalOutput);
        model.addAttribute("avgEff", effCount > 0 ? avgEff : null);

        return "mydata";
    }

    /* ── Delete own entry ─────────────────────────────────────── */
    @PostMapping("/delete")
    public String deleteMyEntry(@RequestParam Long id,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String article) {
        productionService.deleteRecord(id);
        String redirect = "redirect:/entry/mydata?range=" + range;
        if (date != null)
            redirect += "&date=" + date;
        if (article != null && !article.trim().isEmpty())
            redirect += "&article=" + article;
        return redirect;
    }
}
