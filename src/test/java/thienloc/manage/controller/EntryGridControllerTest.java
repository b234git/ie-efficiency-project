package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.security.TestRbacSecurityConfig;
import thienloc.manage.service.IEfficiencyCalculatorService;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.LineAssignmentService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SystemLogService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Render + flow checks for the fast multi-line grid entry page. */
@WebMvcTest(EntryGridController.class)
@Import({SecurityConfig.class, TestRbacSecurityConfig.class})
class EntryGridControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private IProductionService productionService;
    @MockitoBean private IEfficiencyCalculatorService efficiencyCalculator;
    @MockitoBean private DailyProductionRepository productionRepository;
    @MockitoBean private SystemLogService systemLogService;
    @MockitoBean private LineAssignmentService lineAssignmentService;
    @MockitoBean private NotificationService notificationService;

    @Test
    void getGridRendersTable() throws Exception {
        LineAssignmentService.LineScope scope = mock(LineAssignmentService.LineScope.class);
        when(scope.isRestricted()).thenReturn(false);
        when(lineAssignmentService.scopeFor(any())).thenReturn(scope);
        when(productionService.getDashboardData(any())).thenReturn(List.of());
        when(productionRepository.findDistinctLines()).thenReturn(List.of("1A", "2A"));

        mockMvc.perform(get("/entry/grid").param("date", "2026-04-01")
                        .with(user("u").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("entry-grid"))
                .andExpect(model().attributeExists("rows", "lines", "sectionOptions", "date"));
    }

    @Test
    void saveBatchRedirects() throws Exception {
        when(productionService.saveDailyProduction(any(), any(), anyBoolean())).thenReturn(1L);

        mockMvc.perform(post("/entry/grid/save-batch").with(csrf()).with(user("u").roles("MANAGER"))
                        .param("date", "2026-04-01")
                        .param("section", "SEW").param("line", "1A")
                        .param("mp", "30").param("wt", "8").param("output", "1000")
                        .param("article", "ART1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/entry/grid*"));
    }

    @Test
    void previewEffReturnsJson() throws Exception {
        mockMvc.perform(post("/entry/grid/preview-eff").with(csrf()).with(user("u").roles("MANAGER"))
                        .param("date", "2026-04-01").param("section", "SEW")
                        .param("mp", "30").param("wt", "8").param("output", "1000")
                        .param("article", "ART1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
