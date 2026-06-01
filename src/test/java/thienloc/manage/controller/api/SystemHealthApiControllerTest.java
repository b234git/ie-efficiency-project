package thienloc.manage.controller.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemHealthApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class SystemHealthApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeterRegistry meterRegistry;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private InfoEndpoint infoEndpoint;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void stubMeterRegistry() throws Exception {
        Search search = mock(Search.class);
        when(search.tag(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(search);
        when(search.gauges()).thenReturn(java.util.Collections.emptyList());
        when(search.gauge()).thenReturn(null);
        when(meterRegistry.find(org.mockito.ArgumentMatchers.anyString())).thenReturn(search);

        Connection conn = mock(Connection.class);
        when(conn.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conn);
    }

    @Test
    void get_admin_returns200WithPayload() throws Exception {
        mockMvc.perform(get("/api/v1/system-health")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbStatus").value("UP"))
                .andExpect(jsonPath("$.healthUp").value(true))
                .andExpect(jsonPath("$.healthStatus").value("UP"))
                .andExpect(jsonPath("$.appName").exists());
    }

    @Test
    void get_managerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/system-health")
                        .with(user("mgr").roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/system-health"))
                .andExpect(status().isUnauthorized());
    }
}
