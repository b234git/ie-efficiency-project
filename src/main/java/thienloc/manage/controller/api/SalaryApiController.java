package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.ISalaryService;
import thienloc.manage.service.WeeklyTrackingService;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incentive")
public class SalaryApiController {

    private final ISalaryService salaryService;
    private final IProductionService productionService;
    private final WeeklyTrackingService weeklyTrackingService;

    public SalaryApiController(ISalaryService salaryService,
                               IProductionService productionService,
                               WeeklyTrackingService weeklyTrackingService) {
        this.salaryService = salaryService;
        this.productionService = productionService;
        this.weeklyTrackingService = weeklyTrackingService;
    }

    @GetMapping
    public ResponseEntity<SalaryResponse> get(@RequestParam(required = false) String month,
                                              @RequestParam(required = false) String section,
                                              @RequestParam(required = false) String line) {
        List<String> allMonths = new ArrayList<>(productionService.getDistinctMonths());
        for (String m : weeklyTrackingService.getAllDistinctMonths()) {
            if (!allMonths.contains(m)) allMonths.add(m);
        }
        allMonths.sort((a, b) -> b.compareTo(a));

        if (month == null && !allMonths.isEmpty()) {
            month = allMonths.get(0);
        }

        SalaryReportDto report = null;
        List<String> allSections = new ArrayList<>();
        List<String> allLines = new ArrayList<>();

        if (month != null) {
            report = salaryService.buildReport(month);
            if (report != null && report.getBlocks() != null) {
                for (SalaryReportDto.SectionLineBlock block : report.getBlocks()) {
                    if (!allSections.contains(block.getSection())) allSections.add(block.getSection());
                    if (!allLines.contains(block.getLine())) allLines.add(block.getLine());
                }
                if (section != null && !section.isEmpty()) {
                    report.getBlocks().removeIf(b -> !b.getSection().equals(section));
                }
                if (line != null && !line.isEmpty()) {
                    report.getBlocks().removeIf(b -> !b.getLine().equals(line));
                }
            }
        }

        return ResponseEntity.ok(new SalaryResponse(report, allMonths, allSections, allLines, month, section, line));
    }

    public record SalaryResponse(
            SalaryReportDto report,
            List<String> allMonths,
            List<String> allSections,
            List<String> allLines,
            String selectedMonth,
            String selectedSection,
            String selectedLine) {}
}
