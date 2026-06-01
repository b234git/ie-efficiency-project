package thienloc.manage.controller.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.IExcelService;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.NotificationService;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class ReportApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IProductionService productionService;

    @MockitoBean
    private IExcelService excelService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void get_returnsReportPayload() throws Exception {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setSection("SEW");
        dto.setLine("1A");
        dto.setOutput(100);
        dto.setEff(0.85);
        when(productionService.getDashboardDataRange(any(), any()))
                .thenReturn(new ArrayList<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/reports")
                        .param("range", "1M")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedRange").value("1M"))
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.totalOutput").value(100));
    }

    @Test
    void weekly_returnsBlocks() throws Exception {
        WeeklyReportDto block = WeeklyReportDto.builder()
                .section("SEW")
                .line("1A")
                .dailyRows(new ArrayList<>())
                .build();
        when(productionService.getWeeklyReport(any())).thenReturn(new ArrayList<>(List.of(block)));

        mockMvc.perform(get("/api/v1/reports/weekly")
                        .param("weekStart", "2026-04-03")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-04-03"))
                .andExpect(jsonPath("$.canViewSunday").value(true));
    }

    @Test
    void export_returnsXlsxWithContentDisposition() throws Exception {
        when(productionService.getDashboardDataRange(any(), any()))
                .thenReturn(new ArrayList<>());
        when(excelService.exportDailyReport(any(), any(), any()))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/v1/reports/export")
                        .param("range", "1M")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Daily_Report_")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void weeklyExport_returnsXlsxBytes() throws Exception {
        when(productionService.getWeeklyReport(any())).thenReturn(new ArrayList<>());
        when(excelService.exportWeeklyReport(any(), any()))
                .thenReturn(new ByteArrayInputStream(new byte[]{9, 9}));

        mockMvc.perform(get("/api/v1/reports/weekly/export")
                        .param("weekStart", "2026-04-03")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Weekly_Report_2026-04-03")));
    }

    @Test
    void get_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }
}
