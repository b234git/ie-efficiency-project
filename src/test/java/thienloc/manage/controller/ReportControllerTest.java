package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.ExcelService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.ProductionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductionService productionService;

    @MockitoBean
    private ExcelService excelService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testReport_ManagerRole() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/report/").with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attributeExists("records", "selectedRange"));
    }

    @Test
    void testReport_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/report/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testReport_TodayRange() throws Exception {
        when(productionService.getDashboardData(any())).thenReturn(List.of());

        mockMvc.perform(get("/report/").param("range", "TODAY").with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedRange", "TODAY"));
    }

    @Test
    void testReport_MultiFilter() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/report/")
                        .param("range", "1M")
                        .param("article", "Y123")
                        .param("section", "SEW")
                        .with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("article", "Y123"))
                .andExpect(model().attribute("selectedSection", "SEW"));
    }

    @Test
    void testWeeklyReport_ManagerCanViewSunday() throws Exception {
        when(productionService.getWeeklyReport(any())).thenReturn(List.of());

        mockMvc.perform(get("/report/weekly").with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("weekly-report"))
                .andExpect(model().attribute("canViewSunday", true));
    }

    @Test
    void testWeeklyReport_UserForbidden() throws Exception {
        mockMvc.perform(get("/report/weekly").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
