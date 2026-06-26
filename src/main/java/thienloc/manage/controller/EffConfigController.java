package thienloc.manage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.service.EffConfigService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/eff-config")
@RequiredArgsConstructor
public class EffConfigController {

    private static final Map<String, List<String>> SECTION_GRADE_LABELS;
    static {
        SECTION_GRADE_LABELS = new LinkedHashMap<>();
        SECTION_GRADE_LABELS.put("ASSY", Arrays.asList("A","B","C","D","LL1","LL2","LL3","SV1","SV2"));
        SECTION_GRADE_LABELS.put("SEW",  Arrays.asList("AA","A","B","C","D","E","LL1","LL2","LL3"));
        SECTION_GRADE_LABELS.put("SF",   Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
        SECTION_GRADE_LABELS.put("BUFF", Arrays.asList("A","B","C","LL1","LL2","LL3","CB4","CB5","CB6"));
    }

    private final EffConfigService effConfigService;

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
}
