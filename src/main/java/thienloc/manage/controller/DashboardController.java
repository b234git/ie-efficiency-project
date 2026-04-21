package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.IProductionService;
import thienloc.manage.util.RecordFilterUtil;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private IProductionService productionService;

    @GetMapping("/")
    public String showDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article,
            Model model) {

        LocalDate today = LocalDate.now();
        List<DailyProductionDto> records;
        LocalDate fromDate;
        LocalDate toDate = today;

        switch (range) {
            case "1M":
                fromDate = today.minusMonths(1);
                records = productionService.getDashboardDataRange(fromDate, toDate);
                model.addAttribute("rangeLabel", "Last 1 month (" + fromDate + " → " + toDate + ")");
                break;
            case "6M":
                fromDate = today.minusMonths(6);
                records = productionService.getDashboardDataRange(fromDate, toDate);
                model.addAttribute("rangeLabel", "Last 6 months (" + fromDate + " → " + toDate + ")");
                break;
            case "ALL":
                fromDate = today.minusMonths(12);
                records = productionService.getDashboardDataRange(fromDate, toDate);
                model.addAttribute("rangeLabel", "Last 12 months (" + fromDate + " → " + toDate + ")");
                break;
            default: // TODAY or explicit date
                if (date == null)
                    date = today;
                records = productionService.getDashboardData(date);
                fromDate = date;
                model.addAttribute("rangeLabel", "");
                break;
        }

        records = RecordFilterUtil.filterByArticle(records, article);

        model.addAttribute("selectedDate", date != null ? date : today);
        model.addAttribute("selectedRange", range);
        model.addAttribute("article", article);
        model.addAttribute("records", records);
        return "dashboard";
    }

    @PostMapping("/edit")
    public String editRecord(@ModelAttribute DailyProductionDto dto, Principal principal,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article) {
        productionService.saveDailyProduction(dto, principal.getName());
        String redirect = "redirect:/dashboard/?date=" + dto.getProductionDate() + "&range=" + range;
        if (article != null && !article.trim().isEmpty()) {
            redirect += "&article=" + article;
        }
        return redirect;
    }

    @PostMapping("/delete")
    public String deleteRecord(@RequestParam Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article) {
        productionService.deleteRecord(id);
        String redirect = "redirect:/dashboard/?date=" + date + "&range=" + range;
        if (article != null && !article.trim().isEmpty()) {
            redirect += "&article=" + article;
        }
        return redirect;
    }

    @PostMapping("/delete-multiple")
    public String deleteMultipleRecords(@RequestParam List<Long> ids,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article) {
        if (ids != null && !ids.isEmpty()) {
            productionService.deleteMultipleRecords(ids);
        }
        String redirect = "redirect:/dashboard/?date=" + (date != null ? date : "") + "&range=" + range;
        if (article != null && !article.trim().isEmpty()) {
            redirect += "&article=" + article;
        }
        return redirect;
    }
}
