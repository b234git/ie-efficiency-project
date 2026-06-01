package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SystemLogService;
import thienloc.manage.service.UserService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void testAdminDashboard_AdminRole() throws Exception {
        when(userService.findAllUsers()).thenReturn(List.of());
        when(systemLogService.getLogsPage(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists("users", "logsPage"));
    }

    @Test
    void testAdminDashboard_AdminNavbarShowsManagerHubsPlusAdmin() throws Exception {
        when(userService.findAllUsers()).thenReturn(List.of());
        when(systemLogService.getLogsPage(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/entry/\"")))
                .andExpect(content().string(containsString("href=\"/split-entry/\"")))
                .andExpect(content().string(containsString("href=\"/report/\"")))
                .andExpect(content().string(containsString("href=\"/report/weekly\"")))
                .andExpect(content().string(containsString("href=\"/masterdb/\"")))
                .andExpect(content().string(containsString("href=\"/eff-config/\"")))
                .andExpect(content().string(containsString("href=\"/weekly-tracking/\"")))
                .andExpect(content().string(containsString("href=\"/new-style/\"")))
                .andExpect(content().string(containsString("href=\"/salary/\"")))
                .andExpect(content().string(containsString("href=\"/admin/\"")));
    }

    @Test
    void testAdminDashboard_ManagerRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminDashboard_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

}
