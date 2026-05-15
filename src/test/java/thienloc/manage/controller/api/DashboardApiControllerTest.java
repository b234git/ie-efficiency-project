package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfig.class)
class DashboardApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private IProductionService productionService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void get_admin_returnsPayload() throws Exception {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setSection("SEW");
        dto.setLine("1A");
        when(productionService.getDashboardData(any())).thenReturn(new ArrayList<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedRange").value("TODAY"))
                .andExpect(jsonPath("$.records.length()").value(1));
    }

    @Test
    void update_returns200_andReturnsRefetched() throws Exception {
        DailyProductionDto saved = new DailyProductionDto();
        saved.setId(5L);
        saved.setSection("SEW");
        when(productionService.getById(5L)).thenReturn(saved);

        mockMvc.perform(put("/api/v1/dashboard/5")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("section", "SEW", "line", "1A"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        verify(productionService).saveDailyProduction(any(DailyProductionDto.class), anyString());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/9")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(productionService).deleteRecord(9L);
    }

    @Test
    void bulkDelete_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/bulk-delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ids", List.of(1, 2)))))
                .andExpect(status().isNoContent());

        verify(productionService).deleteMultipleRecords(List.of(1L, 2L));
    }

    @Test
    void get_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productionService);
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
