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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductionService productionService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void testShowDashboard_ManagerRole() throws Exception {
        when(productionService.getDashboardData(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("records", "selectedDate", "selectedRange"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testShowDashboard_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/dashboard/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testShowDashboard_1MRange() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/").param("range", "1M"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rangeLabel"));

        verify(productionService).getDashboardDataRange(any(), any());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void testEditRecord() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("manager"))).thenReturn(1L);

        mockMvc.perform(post("/dashboard/edit")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("mp", "30.0")
                        .param("wt", "8.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).saveDailyProduction(any(), eq("manager"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testDeleteRecord() throws Exception {
        mockMvc.perform(post("/dashboard/delete")
                        .param("id", "1")
                        .param("date", "2026-03-15")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).deleteRecord(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteMultiple() throws Exception {
        mockMvc.perform(post("/dashboard/delete-multiple")
                        .param("ids", "1", "2", "3")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).deleteMultipleRecords(List.of(1L, 2L, 3L));
    }
}
