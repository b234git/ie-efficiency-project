package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.ProductionService;
import thienloc.manage.service.SystemLogService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductionController.class)
@Import(SecurityConfig.class)
class ProductionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductionService productionService;

    @MockitoBean
    private SystemLogService systemLogService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testShowEntryForm_UserRole() throws Exception {
        when(productionService.getMyDataRange(eq("user"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/entry/"))
                .andExpect(status().isOk())
                .andExpect(view().name("entry"))
                .andExpect(model().attributeExists("production", "entries"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testShowEntryForm_RangeFilter() throws Exception {
        when(productionService.getMyDataRange(eq("user"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/entry/").param("range", "1M"))
                .andExpect(status().isOk());

        verify(productionService).getMyDataRange(eq("user"), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testSaveEntry() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("user"))).thenReturn(1L);

        mockMvc.perform(post("/entry/save")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("mp", "30.0")
                        .param("wt", "8.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?success"));

        verify(productionService).saveDailyProduction(any(), eq("user"));
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void testEditEntry_ManagerRole() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("manager"))).thenReturn(1L);

        mockMvc.perform(post("/entry/edit")
                        .param("id", "1")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("mp", "30.0")
                        .param("wt", "8.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?edited"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testEditEntry_UserRole_Forbidden() throws Exception {
        mockMvc.perform(post("/entry/edit")
                        .param("id", "1")
                        .param("productionDate", "2026-03-15")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminDeleteEntry_AdminRole() throws Exception {
        mockMvc.perform(post("/entry/admin-delete")
                        .param("id", "1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?deleted"));

        verify(productionService).deleteRecord(1L);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testRequestEdit_UserRole() throws Exception {
        DailyProductionDto record = new DailyProductionDto();
        record.setProductionDate(LocalDate.of(2026, 3, 15));
        record.setSection("SEW");
        record.setLine("1A");
        record.setOutput(1000);
        when(productionService.getById(1L)).thenReturn(record);

        mockMvc.perform(post("/entry/request-edit")
                        .param("id", "1")
                        .param("reason", "Wrong data")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?requestSent"));

        verify(notificationService).notifyAdminAndManager(anyString(), anyString(), eq("INFO"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testBulkDelete_ManagerRole() throws Exception {
        mockMvc.perform(post("/entry/bulk-delete")
                        .param("ids", "1", "2")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?deleted"));

        verify(productionService, times(2)).deleteRecord(anyLong());
    }
}
