package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SystemLogService;
import thienloc.manage.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SystemLogService systemLogService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminDashboard_AdminRole() throws Exception {
        when(userService.findAllUsers()).thenReturn(List.of());
        when(systemLogService.getLogsPage(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists("users", "logsPage"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testAdminDashboard_ManagerRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testAdminDashboard_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateRole() throws Exception {
        mockMvc.perform(post("/admin/update-role")
                        .param("userId", "1")
                        .param("newRole", "ROLE_MANAGER")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/?success"));

        verify(userService).updateRole(1L, "ROLE_MANAGER");
        verify(systemLogService).logAction(eq("UPDATE_ROLE"), anyString());
    }
}
