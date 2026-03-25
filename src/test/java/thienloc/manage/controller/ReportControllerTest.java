package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.ProductionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void testReport_ManagerRole() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/report/"))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attributeExists("records", "selectedRange"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testReport_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/report/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testReport_TodayRange() throws Exception {
        when(productionService.getDashboardData(any())).thenReturn(List.of());

        mockMvc.perform(get("/report/").param("range", "TODAY"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedRange", "TODAY"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testReport_MultiFilter() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/report/")
                        .param("range", "1M")
                        .param("article", "Y123")
                        .param("filterSection", "SEW"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("article", "Y123"))
                .andExpect(model().attribute("filterSection", "SEW"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testWeeklyReport_ManagerCanViewSunday() throws Exception {
        when(productionService.getWeeklyReport(any())).thenReturn(List.of());

        mockMvc.perform(get("/report/weekly"))
                .andExpect(status().isOk())
                .andExpect(view().name("weekly-report"))
                .andExpect(model().attribute("canViewSunday", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testWeeklyReport_UserForbidden() throws Exception {
        mockMvc.perform(get("/report/weekly"))
                .andExpect(status().isForbidden());
    }
}
