package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    void testShowEntryForm_UserRole() throws Exception {
        when(productionService.getMyDataRange(eq("user"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/entry/").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("entry"))
                .andExpect(model().attributeExists("production", "entries"));
    }

    @Test
    void testShowEntryForm_RangeFilter() throws Exception {
        when(productionService.getMyDataRange(eq("user"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/entry/").param("range", "1M").with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(productionService).getMyDataRange(eq("user"), any(), any());
    }

    @Test
    void testSaveEntry() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("user"))).thenReturn(1L);

        mockMvc.perform(post("/entry/save")
                .param("productionDate", "2026-03-15")
                .param("section", "SEW")
                .param("line", "1A")
                .param("mp", "30.0")
                .param("wt", "8.0")
                .with(user("user").roles("USER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?success"));

        verify(productionService).saveDailyProduction(any(), eq("user"));
    }

    @Test
    void testEditEntry_ManagerRole() throws Exception {
        when(productionService.saveDailyProduction(any(), eq("manager"))).thenReturn(1L);

        mockMvc.perform(post("/entry/edit")
                .param("id", "1")
                .param("productionDate", "2026-03-15")
                .param("section", "SEW")
                .param("line", "1A")
                .param("mp", "30.0")
                .param("wt", "8.0")
                .with(user("manager").roles("MANAGER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?edited"));
    }

    @Test
    void testEditEntry_UserRole_Forbidden() throws Exception {
        mockMvc.perform(post("/entry/edit")
                .param("id", "1")
                .param("productionDate", "2026-03-15")
                .with(user("user").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminDeleteEntry_AdminRole() throws Exception {
        mockMvc.perform(post("/entry/admin-delete")
                .param("id", "1")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?deleted"));

        verify(productionService).deleteRecord(1L);
    }

    @Test
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
                .with(user("user").roles("USER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?requestSent"));

        verify(notificationService).notifyAdminAndManager(anyString(), anyString(), eq("INFO"));
    }

    @Test
    void testBulkDelete_ManagerRole() throws Exception {
        mockMvc.perform(post("/entry/bulk-delete")
                .param("ids", "1", "2")
                .with(user("manager").roles("MANAGER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/?deleted"));

        verify(productionService, times(2)).deleteRecord(anyLong());
    }

    @Test
    void testDeleteMyEntry_UsesOwnershipCheck() throws Exception {
        mockMvc.perform(post("/entry/delete")
                .param("id", "1")
                .with(user("user").roles("MANAGER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(productionService).deleteOwnRecord(eq(1L), eq("user"));
        verify(productionService, never()).deleteRecord(1L);
    }
}
