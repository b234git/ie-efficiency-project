package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    void testShowDashboard_ManagerRole() throws Exception {
        when(productionService.getDashboardData(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/").with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("records", "selectedDate", "selectedRange"));
    }

    @Test
    void testShowDashboard_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/dashboard/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testShowDashboard_1MRange() throws Exception {
        when(productionService.getDashboardDataRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/").param("range", "1M").with(user("user").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rangeLabel"));

        verify(productionService).getDashboardDataRange(any(), any());
    }

    @Test
    void testEditRecord() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("manager"))).thenReturn(1L);

        mockMvc.perform(post("/dashboard/edit")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("mp", "30.0")
                        .param("wt", "8.0")
                        .with(user("manager").roles("MANAGER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).saveDailyProduction(any(), eq("manager"));
    }

    @Test
    void testDeleteRecord() throws Exception {
        mockMvc.perform(post("/dashboard/delete")
                        .param("id", "1")
                        .param("date", "2026-03-15")
                        .with(user("user").roles("MANAGER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).deleteRecord(1L);
    }

    @Test
    void testDeleteMultiple() throws Exception {
        mockMvc.perform(post("/dashboard/delete-multiple")
                        .param("ids", "1", "2", "3")
                        .with(user("user").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).deleteMultipleRecords(List.of(1L, 2L, 3L));
    }
}
