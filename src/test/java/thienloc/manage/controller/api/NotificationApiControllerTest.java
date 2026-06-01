package thienloc.manage.controller.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class NotificationApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void markAsRead_returns204_andCallsService() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/42/read")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(42L);
    }

    @Test
    void markAsRead_unauthenticated_returns401Json() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/42/read")
                        .with(csrf().asHeader()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void markAsRead_userRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/42/read")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    @Test
    void dismissAll_admin_returns204_andCallsServiceWithAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/dismiss-all")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(notificationService).dismissAll("ROLE_ADMIN");
    }

    @Test
    void dismissAll_manager_returns204_andCallsServiceWithManagerRole() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/dismiss-all")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(notificationService).dismissAll("ROLE_MANAGER");
    }

    @Test
    void dismissAll_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/dismiss-all")
                        .with(user("u").roles("USER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    @Test
    void markAsRead_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/42/read")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }
}
