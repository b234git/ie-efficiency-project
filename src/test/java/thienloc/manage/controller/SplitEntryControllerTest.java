package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.LineSummaryImportService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SplitEntryService;
import thienloc.manage.service.SystemLogService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SplitEntryController.class)
@Import(SecurityConfig.class)
class SplitEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SplitEntryService splitEntryService;

    @MockitoBean
    private SystemLogService systemLogService;

    @MockitoBean
    private LineSummaryImportService lineSummaryImportService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "USER")
    void testShowLanding_UserRole() throws Exception {
        when(splitEntryService.getEntriesForDate(any())).thenReturn(List.of());

        mockMvc.perform(get("/split-entry/"))
                .andExpect(status().isOk())
                .andExpect(view().name("split-entry"))
                .andExpect(model().attributeExists("entries", "selectedDate"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testShowManpowerForm() throws Exception {
        mockMvc.perform(get("/split-entry/manpower"))
                .andExpect(status().isOk())
                .andExpect(view().name("split-entry-manpower"))
                .andExpect(model().attributeExists("splitEntry", "sections"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testShowManpowerForm_PreFill() throws Exception {
        SplitEntryDto existing = new SplitEntryDto();
        existing.setSection("SEW");
        existing.setLine("1A");
        existing.setMp(30.0);
        when(splitEntryService.getByDateSectionLine(any(), eq("SEW"), eq("1A")))
                .thenReturn(Optional.of(existing));

        mockMvc.perform(get("/split-entry/manpower")
                        .param("date", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("splitEntry"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testSaveManpower() throws Exception {
        mockMvc.perform(post("/split-entry/manpower")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("mp", "30.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(splitEntryService).saveManpower(any(), eq("user"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testShowOutputForm_ManagerRole() throws Exception {
        mockMvc.perform(get("/split-entry/output"))
                .andExpect(status().isOk())
                .andExpect(view().name("split-entry-output"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testShowOutputForm_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/split-entry/output"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void testSaveOutput() throws Exception {
        mockMvc.perform(post("/split-entry/output")
                        .param("productionDate", "2026-03-15")
                        .param("section", "SEW")
                        .param("line", "1A")
                        .param("wt", "8.0")
                        .param("totalOutput", "1000")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(splitEntryService).saveOutput(any(), eq("manager"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testShowArticlesForm_ManagerRole() throws Exception {
        mockMvc.perform(get("/split-entry/articles"))
                .andExpect(status().isOk())
                .andExpect(view().name("split-entry-articles"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testShowArticlesForm_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/split-entry/articles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testDeleteEntry_ManagerRole() throws Exception {
        mockMvc.perform(post("/split-entry/delete/1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(splitEntryService).deleteEntry(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void testDeleteEntry_UserRole_Forbidden() throws Exception {
        mockMvc.perform(post("/split-entry/delete/1")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testImportCancel() throws Exception {
        mockMvc.perform(post("/split-entry/import/cancel")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/split-entry/manpower"));
    }
}
