package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testRoot_NotAuthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testRoot_Authenticated_RedirectsToHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testHome_AdminRole() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void testHome_ManagerRole() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testHome_UserRole() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry/"));
    }
}
