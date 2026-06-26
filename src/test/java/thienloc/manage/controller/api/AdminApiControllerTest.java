package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.User;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.AdminService;
import thienloc.manage.service.LineAssignmentService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.UserService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class AdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private LineAssignmentService lineAssignmentService;

    @MockitoBean
    private NotificationService notificationService;

    private String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    // ── Update role ───────────────────────────────────────────────────────────

    @Test
    void updateRole_admin_returns200() throws Exception {
        User saved = new User();
        saved.setId(1L);
        saved.setUsername("alice");
        saved.setRole("ROLE_MANAGER");
        when(userService.updateRole(eq(1L), eq("ROLE_MANAGER"))).thenReturn(saved);

        mockMvc.perform(put("/api/v1/admin/users/1/role")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ROLE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_MANAGER"));

        verify(userService).updateRole(1L, "ROLE_MANAGER");
    }

    @Test
    void updateRole_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/1/role")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ROLE_MANAGER"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void updateRole_managerRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/1/role")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ROLE_MANAGER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void updateRole_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/1/role")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ROLE_MANAGER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    // ── Force delete ──────────────────────────────────────────────────────────

    @Test
    void forceDelete_admin_returns200() throws Exception {
        AdminService.ForceDeleteResult result = new AdminService.ForceDeleteResult(
                "ENTRY", List.of(1L, 2L), List.of(3L), List.of());
        when(adminService.forceDeleteByIds(eq("ENTRY"), eq(List.of(1L, 2L, 3L))))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/admin/force-delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("source", "ENTRY", "ids", List.of(1, 2, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ENTRY"))
                .andExpect(jsonPath("$.deleted.length()").value(2))
                .andExpect(jsonPath("$.missing.length()").value(1));

        verify(adminService).forceDeleteByIds("ENTRY", List.of(1L, 2L, 3L));
    }

    @Test
    void forceDelete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/force-delete")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("source", "ENTRY", "ids", List.of(1)))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminService);
    }

    @Test
    void forceDelete_managerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/force-delete")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("source", "ENTRY", "ids", List.of(1)))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminService);
    }

    @Test
    void forceDelete_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/force-delete")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("source", "ENTRY", "ids", List.of(1)))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminService);
    }
}
