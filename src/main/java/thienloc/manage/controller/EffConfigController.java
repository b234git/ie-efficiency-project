package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.service.EffConfigService;
import thienloc.manage.service.SystemLogService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/eff-config")
public class EffConfigController {

    private static final Map<String, List<String>> SECTION_GRADE_LABELS;
    static {
        SECTION_GRADE_LABELS = new LinkedHashMap<>();
        SECTION_GRADE_LABELS.put("ASSY", Arrays.asList("A","B","C","D","LL1","LL2","LL3","SV1","SV2"));
        SECTION_GRADE_LABELS.put("SEW",  Arrays.asList("AA","A","B","C","D","E","LL1","LL2","LL3"));
        SECTION_GRADE_LABELS.put("SF",   Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
        SECTION_GRADE_LABELS.put("BUFF", Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
    }

    @Autowired
    private EffConfigService effConfigService;

    @Autowired
    private SystemLogService systemLogService;

    // ── GET: render full page ─────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String index(Model model) {
        List<EffMultiplier> allMultipliers = effConfigService.getAllMultipliers();

        Map<String, List<EffMultiplier>> multipliersBySection = new LinkedHashMap<>();
        for (String section : SECTION_GRADE_LABELS.keySet()) {
            List<EffMultiplier> group = allMultipliers.stream()
                    .filter(m -> section.equals(m.getSection()))
                    .collect(Collectors.toList());
            if (!group.isEmpty()) {
                multipliersBySection.put(section, group);
            }
        }
        for (EffMultiplier m : allMultipliers) {
            if (!SECTION_GRADE_LABELS.containsKey(m.getSection())) {
                multipliersBySection
                    .computeIfAbsent(m.getSection(), k -> new java.util.ArrayList<>())
                    .add(m);
            }
        }

        model.addAttribute("multipliers", allMultipliers);
        model.addAttribute("multipliersBySection", multipliersBySection);
        model.addAttribute("gradeLabels", SECTION_GRADE_LABELS);
        model.addAttribute("allRates", effConfigService.getAllRates());
        model.addAttribute("rateSecs", effConfigService.getDistinctRateSecs());
        return "eff-config";
    }

    // ── Multiplier: Save ──────────────────────────────────────────────────────

    @PostMapping("/multiplier/save")
    public String saveMultiplier(@ModelAttribute EffMultiplier entity,
                                 RedirectAttributes ra) {
        try {
            boolean isNew = (entity.getId() == null);
            effConfigService.saveMultiplier(entity);
            systemLogService.logAction(
                    isNew ? "ADD_EFF_MULTIPLIER" : "EDIT_EFF_MULTIPLIER",
                    "SEC: " + entity.getSec());
            ra.addFlashAttribute("success",
                    isNew ? "Multiplier added successfully!" : "Updated successfully!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "multiplier");
        return "redirect:/eff-config/";
    }

    // ── Multiplier: Delete ────────────────────────────────────────────────────

    @PostMapping("/multiplier/delete")
    public String deleteMultiplier(@RequestParam Long id, RedirectAttributes ra) {
        try {
            effConfigService.deleteMultiplierById(id);
            systemLogService.logAction("DELETE_EFF_MULTIPLIER", "Deleted Multiplier ID: " + id);
            ra.addFlashAttribute("success", "Multiplier deleted!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Delete error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "multiplier");
        return "redirect:/eff-config/";
    }

    // ── Rate: Save ────────────────────────────────────────────────────────────

    @PostMapping("/rate/save")
    public String saveRate(@ModelAttribute EffIncentiveRate entity,
                           RedirectAttributes ra) {
        try {
            boolean isNew = (entity.getId() == null);
            effConfigService.saveRate(entity);
            systemLogService.logAction(
                    isNew ? "ADD_EFF_RATE" : "EDIT_EFF_RATE",
                    "SEC: " + entity.getSec() + ", EFF%: " + entity.getEffPercent());
            ra.addFlashAttribute("success",
                    isNew ? "Incentive rate added successfully!" : "Updated successfully!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "rate");
        return "redirect:/eff-config/";
    }

    // ── Rate: Delete ──────────────────────────────────────────────────────────

    @PostMapping("/rate/delete")
    public String deleteRate(@RequestParam Long id, RedirectAttributes ra) {
        try {
            effConfigService.deleteRateById(id);
            systemLogService.logAction("DELETE_EFF_RATE", "Deleted Rate ID: " + id);
            ra.addFlashAttribute("success", "Incentive rate deleted!");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Delete error: " + e.getMessage());
        }
        ra.addFlashAttribute("activeTab", "rate");
        return "redirect:/eff-config/";
    }
}
