package thienloc.manage.controller.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.ISalaryService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.WeeklyTrackingService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalaryApiController.class)
@Import(SecurityConfig.class)
class SalaryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ISalaryService salaryService;

    @MockitoBean
    private IProductionService productionService;

    @MockitoBean
    private WeeklyTrackingService weeklyTrackingService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void get_admin_returnsReportAndDropdowns() throws Exception {
        when(productionService.getDistinctMonths()).thenReturn(List.of("2026-04", "2026-03"));
        when(weeklyTrackingService.getAllDistinctMonths()).thenReturn(List.of("2026-04"));

        SalaryReportDto report = new SalaryReportDto();
        report.setMonth("2026-04");
        SalaryReportDto.SectionLineBlock blockSew = new SalaryReportDto.SectionLineBlock();
        blockSew.setSection("SEW");
        blockSew.setLine("1A");
        SalaryReportDto.SectionLineBlock blockBuff = new SalaryReportDto.SectionLineBlock();
        blockBuff.setSection("BUFF");
        blockBuff.setLine("2B");
        report.setBlocks(new ArrayList<>(List.of(blockSew, blockBuff)));
        when(salaryService.buildReport("2026-04")).thenReturn(report);

        mockMvc.perform(get("/api/v1/salary")
                        .param("month", "2026-04")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedMonth").value("2026-04"))
                .andExpect(jsonPath("$.allMonths.length()").value(2))
                .andExpect(jsonPath("$.allSections", org.hamcrest.Matchers.contains("SEW", "BUFF")))
                .andExpect(jsonPath("$.report.blocks.length()").value(2));
    }

    @Test
    void get_filtersBlocksBySectionAndLine() throws Exception {
        when(productionService.getDistinctMonths()).thenReturn(List.of("2026-04"));
        when(weeklyTrackingService.getAllDistinctMonths()).thenReturn(List.of());

        SalaryReportDto report = new SalaryReportDto();
        report.setMonth("2026-04");
        SalaryReportDto.SectionLineBlock keep = new SalaryReportDto.SectionLineBlock();
        keep.setSection("SEW");
        keep.setLine("1A");
        SalaryReportDto.SectionLineBlock drop = new SalaryReportDto.SectionLineBlock();
        drop.setSection("BUFF");
        drop.setLine("2B");
        report.setBlocks(new ArrayList<>(List.of(keep, drop)));
        when(salaryService.buildReport("2026-04")).thenReturn(report);

        mockMvc.perform(get("/api/v1/salary")
                        .param("month", "2026-04")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .with(user("mgr").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.blocks.length()").value(1))
                .andExpect(jsonPath("$.report.blocks[0].section").value("SEW"));
    }

    @Test
    void get_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/salary")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/salary"))
                .andExpect(status().isUnauthorized());
    }
}
