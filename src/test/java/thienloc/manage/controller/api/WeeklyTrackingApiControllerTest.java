package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.ReprocessRecord;
import thienloc.manage.entity.SixSRecord;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.WeeklyTrackingService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeeklyTrackingApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class WeeklyTrackingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private WeeklyTrackingService service;

    @MockitoBean
    private NotificationService notificationService;

    private String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    // ── 6S CRUD ───────────────────────────────────────────────────────────────

    @Test
    void createSixS_admin_returns201() throws Exception {
        SixSRecord saved = new SixSRecord();
        saved.setId(7L);
        when(service.saveSixS(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/weekly-tracking/sixs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dataMonth", "2026-04", "section", "SEW", "line", "1A", "week1", 50))))
                .andExpect(status().isCreated());

        verify(service).saveSixS(any(SixSRecord.class));
    }

    @Test
    void updateSixS_manager_returns200() throws Exception {
        SixSRecord saved = new SixSRecord();
        saved.setId(11L);
        when(service.saveSixS(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/weekly-tracking/sixs/11")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dataMonth", "2026-04", "section", "SEW", "line", "1A", "week1", 60))))
                .andExpect(status().isOk());

        verify(service).saveSixS(any(SixSRecord.class));
    }

    @Test
    void deleteSixS_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/weekly-tracking/sixs/5")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(service).deleteSixS(5L);
    }

    @Test
    void bulkDeleteSixS_manager_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/weekly-tracking/sixs/bulk-delete")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(1, 2, 3)))))
                .andExpect(status().isNoContent());

        verify(service).deleteSixSByIds(List.of(1L, 2L, 3L));
    }

    // ── Reprocess CRUD ────────────────────────────────────────────────────────

    @Test
    void createReprocess_admin_returns201() throws Exception {
        ReprocessRecord saved = new ReprocessRecord();
        saved.setId(3L);
        when(service.saveReprocess(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/weekly-tracking/reprocess")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dataMonth", "2026-04", "section", "SEW", "line", "1A",
                                "week1", 10, "output", 5000))))
                .andExpect(status().isCreated());

        verify(service).saveReprocess(any(ReprocessRecord.class));
    }

    @Test
    void deleteReprocess_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/weekly-tracking/reprocess/9")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(service).deleteReprocess(9L);
    }

    @Test
    void bulkDeleteReprocess_admin_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/weekly-tracking/reprocess/bulk-delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", List.of(4, 5)))))
                .andExpect(status().isNoContent());

        verify(service).deleteReprocessByIds(List.of(4L, 5L));
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void createSixS_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/weekly-tracking/sixs")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void createSixS_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/weekly-tracking/sixs")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void deleteSixS_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/weekly-tracking/sixs/5")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
