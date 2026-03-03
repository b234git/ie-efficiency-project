package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.ProductionService;

import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private ProductionService productionService;

    @GetMapping("/")
    public String showDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        model.addAttribute("selectedDate", date);
        model.addAttribute("records", productionService.getDashboardData(date));
        return "dashboard";
    }

    @PostMapping("/edit")
    public String editRecord(@ModelAttribute DailyProductionDto dto, Principal principal) {
        // Output fields mapping from form direct to DTO
        productionService.saveDailyProduction(dto, principal.getName());
        return "redirect:/dashboard/?date=" + dto.getProductionDate();
    }

    @PostMapping("/delete")
    public String deleteRecord(@RequestParam Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        productionService.deleteRecord(id);
        return "redirect:/dashboard/?date=" + date;
    }
}
