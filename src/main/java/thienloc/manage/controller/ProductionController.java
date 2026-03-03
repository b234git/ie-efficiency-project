package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.ProductionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/entry")
public class ProductionController {

    @Autowired
    private ProductionService productionService;

    @Autowired
    private thienloc.manage.service.SystemLogService systemLogService;

    @GetMapping({ "", "/" })
    public String showEntryForm(
            @RequestParam(defaultValue = "0") int page,
            Model model,
            Principal principal) {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setProductionDate(LocalDate.now()); // Default to today

        // Initialize 15 time slots (round hours from 07:00 to 21:00)
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

        // Show history of user's inputs
        Pageable pageable = PageRequest.of(page, 10);
        model.addAttribute("historyPage", productionService.getUserEntries(principal.getName(), pageable));
        return "entry";
    }

    @PostMapping("/save")
    public String saveEntry(DailyProductionDto dto, Principal principal) {
        productionService.saveDailyProduction(dto, principal.getName());
        systemLogService.logAction("ADD_ENTRY",
                "Added production entry for Section: " + dto.getSection() + ", Article: " + dto.getArticle());
        return "redirect:/entry/?success";
    }
}
