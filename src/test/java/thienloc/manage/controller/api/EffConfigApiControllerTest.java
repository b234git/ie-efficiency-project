package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.EffConfigService;
import thienloc.manage.service.NotificationService;

import java.math.BigDecimal;
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

@WebMvcTest(EffConfigApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class EffConfigApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private EffConfigService effConfigService;

    @MockitoBean
    private NotificationService notificationService;

    private String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    // ── Multipliers ───────────────────────────────────────────────────────────

    @Test
    void createMultiplier_admin_returns201() throws Exception {
        EffMultiplier saved = new EffMultiplier();
        saved.setId(7L);
        when(effConfigService.saveMultiplier(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/eff-config/multipliers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sec", "SEW8", "section", "SEW", "wt", 8))))
                .andExpect(status().isCreated());

        verify(effConfigService).saveMultiplier(any(EffMultiplier.class));
    }

    @Test
    void updateMultiplier_manager_returns200_andSetsIdFromPath() throws Exception {
        EffMultiplier saved = new EffMultiplier();
        saved.setId(11L);
        when(effConfigService.saveMultiplier(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/eff-config/multipliers/11")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sec", "SEW8", "section", "SEW", "wt", 8))))
                .andExpect(status().isOk());

        verify(effConfigService).saveMultiplier(any(EffMultiplier.class));
    }

    @Test
    void deleteMultiplier_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/eff-config/multipliers/5")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(effConfigService).deleteMultiplierById(5L);
    }

    @Test
    void createMultiplier_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/eff-config/multipliers")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(effConfigService);
    }

    @Test
    void createMultiplier_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/eff-config/multipliers")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(effConfigService);
    }

    @Test
    void deleteMultiplier_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/eff-config/multipliers/5")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(effConfigService);
    }

    // ── Rates ─────────────────────────────────────────────────────────────────

    @Test
    void createRate_manager_returns201() throws Exception {
        EffIncentiveRate saved = new EffIncentiveRate();
        saved.setId(3L);
        when(effConfigService.saveRate(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/eff-config/rates")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sec", "SEW8", "section", "SEW", "wt", 8,
                                "effPercent", new BigDecimal("79.5"), "rate", new BigDecimal("8121")))))
                .andExpect(status().isCreated());

        verify(effConfigService).saveRate(any(EffIncentiveRate.class));
    }

    @Test
    void updateRate_admin_returns200() throws Exception {
        EffIncentiveRate saved = new EffIncentiveRate();
        saved.setId(9L);
        when(effConfigService.saveRate(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/eff-config/rates/9")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sec", "SEW8", "section", "SEW", "wt", 8,
                                "effPercent", new BigDecimal("80"), "rate", new BigDecimal("9000")))))
                .andExpect(status().isOk());

        verify(effConfigService).saveRate(any(EffIncentiveRate.class));
    }

    @Test
    void deleteRate_manager_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/eff-config/rates/9")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(effConfigService).deleteRateById(9L);
    }

    @Test
    void deleteRate_userRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/eff-config/rates/9")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(effConfigService);
    }
}
