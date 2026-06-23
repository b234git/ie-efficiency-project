package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.ISalaryService;
import thienloc.manage.service.SalaryExcelExportService;
import thienloc.manage.service.WeeklyTrackingService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/incentive")
public class SalaryController {

    @Autowired
    private ISalaryService salaryService;

    @Autowired
    private IProductionService productionService;

    @Autowired
    private WeeklyTrackingService weeklyTrackingService;

    @Autowired
    private SalaryExcelExportService salaryExcelExportService;

    @GetMapping({"", "/"})
    public String index(@RequestParam(required = false) String month,
                        @RequestParam(required = false) String section,
                        @RequestParam(required = false) String line,
                        Model model) {
        // Merge production months + 6S/Reprocess months so the dropdown always
        // reflects actual data even when weekly tracking hasn't been filled in yet.
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

            // Extract distinct sections and lines from all blocks (before filtering)
            if (report != null && report.getBlocks() != null) {
                for (SalaryReportDto.SectionLineBlock block : report.getBlocks()) {
                    if (!allSections.contains(block.getSection())) {
                        allSections.add(block.getSection());
                    }
                    if (!allLines.contains(block.getLine())) {
                        allLines.add(block.getLine());
                    }
                }

                // Filter blocks by section and/or line
                if (section != null && !section.isEmpty()) {
                    report.getBlocks().removeIf(b -> !b.getSection().equals(section));
                }
                if (line != null && !line.isEmpty()) {
                    report.getBlocks().removeIf(b -> !b.getLine().equals(line));
                }
            }
        }

        model.addAttribute("allMonths", allMonths);
        model.addAttribute("allSections", allSections);
        model.addAttribute("allLines", allLines);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedSection", section);
        model.addAttribute("selectedLine", line);
        model.addAttribute("report", report);
        return "salary";
    }

    /** Download the incentive/salary "S" report as .xlsx (same month/section/line filter as the page). */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> export(@RequestParam(required = false) String month,
                                                      @RequestParam(required = false) String section,
                                                      @RequestParam(required = false) String line) throws IOException {
        if (month == null) {
            List<String> allMonths = new ArrayList<>(productionService.getDistinctMonths());
            for (String m : weeklyTrackingService.getAllDistinctMonths()) {
                if (!allMonths.contains(m)) allMonths.add(m);
            }
            allMonths.sort((a, b) -> b.compareTo(a));
            if (!allMonths.isEmpty()) month = allMonths.get(0);
        }

        SalaryReportDto report = (month != null) ? salaryService.buildReport(month) : null;
        if (report != null && report.getBlocks() != null) {
            if (section != null && !section.isEmpty()) {
                report.getBlocks().removeIf(b -> !b.getSection().equals(section));
            }
            if (line != null && !line.isEmpty()) {
                report.getBlocks().removeIf(b -> !b.getLine().equals(line));
            }
        }

        ByteArrayInputStream stream = salaryExcelExportService.export(report);
        String fname = "Incentive_S_" + (month != null ? month : "all") + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fname)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(stream));
    }
}
