package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.IProductionService;
import thienloc.manage.service.NotificationService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(ProductionApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class ProductionApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private IProductionService productionService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void list_returnsPagedFilteredResults() throws Exception {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setId(1L);
        dto.setSection("SEW");
        dto.setArticle("AR-1");
        Page<DailyProductionDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 25), 1);
        when(productionService.getMyDataRangeWithSplitEntriesPaged(
                anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/entries")
                        .param("range", "1M")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].section").value("SEW"));
    }

    @Test
    void create_returns201_andReturnsRefetched() throws Exception {
        when(productionService.saveDailyProduction(any(), anyString())).thenReturn(42L);
        DailyProductionDto saved = new DailyProductionDto();
        saved.setId(42L);
        saved.setSection("SEW");
        when(productionService.getById(42L)).thenReturn(saved);

        mockMvc.perform(post("/api/v1/entries")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("section", "SEW", "line", "1A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void update_returns200_withRefetchedRecord() throws Exception {
        when(productionService.saveDailyProduction(any(), anyString())).thenReturn(7L);
        DailyProductionDto saved = new DailyProductionDto();
        saved.setId(7L);
        saved.setOutput(500);
        when(productionService.getById(7L)).thenReturn(saved);

        mockMvc.perform(put("/api/v1/entries/7")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("output", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(500));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/entries/9")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(productionService).deleteRecord(9L);
    }

    @Test
    void delete_returns404_whenServiceThrowsResourceNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("not found"))
                .when(productionService).deleteRecord(99L);

        mockMvc.perform(delete("/api/v1/entries/99")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void bulkDelete_returns204_andCallsDeleteIfPresent() throws Exception {
        mockMvc.perform(post("/api/v1/entries/bulk-delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ids", List.of(1, 2, 3)))))
                .andExpect(status().isNoContent());

        verify(productionService).deleteIfPresent(1L);
        verify(productionService).deleteIfPresent(2L);
        verify(productionService).deleteIfPresent(3L);
    }

    @Test
    void adminDeletableIds_returnsList() throws Exception {
        when(productionService.getFilteredIds(anyString(), any(), any(), eq(""), eq("")))
                .thenReturn(List.of(11L, 12L));

        mockMvc.perform(get("/api/v1/entries/admin-deletable-ids")
                        .param("range", "1M")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value(11));
    }

    @Test
    void list_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/entries")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productionService);
    }

    @Test
    void delete_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/entries/5")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productionService);
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/entries"))
                .andExpect(status().isUnauthorized());
    }
}
