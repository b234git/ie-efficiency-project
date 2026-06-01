package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.ISplitEntryService;
import thienloc.manage.service.NotificationService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SplitEntryApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class SplitEntryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private ISplitEntryService splitEntryService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void list_byDate_returnsEntries() throws Exception {
        SplitEntryDto dto = new SplitEntryDto();
        dto.setId(1L);
        dto.setSection("SEW");
        when(splitEntryService.getEntriesForDate(LocalDate.parse("2026-04-15")))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/split-entries")
                        .param("date", "2026-04-15")
                        .with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].section").value("SEW"));
    }

    @Test
    void list_byMonth_returnsEntries() throws Exception {
        when(splitEntryService.getEntriesForMonth(YearMonth.parse("2026-04")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/split-entries")
                        .param("month", "2026-04")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(splitEntryService).getEntriesForMonth(YearMonth.parse("2026-04"));
    }

    @Test
    void delete_returns204_whenExists() throws Exception {
        when(splitEntryService.deleteIfPresent(5L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/split-entries/5")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(splitEntryService).deleteIfPresent(5L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        when(splitEntryService.deleteIfPresent(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/split-entries/99")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_userRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/split-entries/5")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(splitEntryService);
    }

    @Test
    void bulkDelete_returns204() throws Exception {
        when(splitEntryService.deleteIfPresent(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/split-entries/bulk-delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ids", List.of(1, 2)))))
                .andExpect(status().isNoContent());

        verify(splitEntryService).deleteIfPresent(1L);
        verify(splitEntryService).deleteIfPresent(2L);
    }

    @Test
    void bulkDelete_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/split-entries/bulk-delete")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(splitEntryService);
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/split-entries"))
                .andExpect(status().isUnauthorized());
    }
}
