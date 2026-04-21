package thienloc.manage.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
// import thienloc.manage.config.SampleDataInitializer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean
    // private SampleDataInitializer sampleDataInitializer;

    // ─── CSRF verification ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testPostWithoutCsrf_Forbidden() throws Exception {
        mockMvc.perform(post("/entry/save")
                .param("section", "SEW"))
                .andExpect(status().isForbidden());
    }

    // ─── Public endpoints ────────────────────────────────────────────────────────

    @Test
    void testPublicEndpoints_Login() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void testPublicEndpoints_Register() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    void testProtectedEndpoint_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/entry/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ─── /admin/** ───────────────────────────────────────────────────────────────

    @Test
    void testAdmin_AdminRole_Allowed() throws Exception {
        mockMvc.perform(get("/admin/").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void testAdmin_ManagerRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdmin_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ─── /dashboard/** ──────────────────────────────────────────────────────────

    @Test
    void testDashboard_AdminAllowed() throws Exception {
        mockMvc.perform(get("/dashboard/").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void testDashboard_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/dashboard/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testDashboard_UserForbidden() throws Exception {
        mockMvc.perform(get("/dashboard/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ─── /entry/** ──────────────────────────────────────────────────────────────

    @Test
    void testEntry_UserForbidden() throws Exception {
        mockMvc.perform(get("/entry/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEntry_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/entry/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testSplitEntry_UserAllowed() throws Exception {
        mockMvc.perform(get("/split-entry/").with(user("user").roles("USER")))
                .andExpect(status().isOk());
    }

    // ─── /split-entry/output ─────────────────────────────────────────────────────

    @Test
    void testSplitEntryOutput_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/split-entry/output").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testSplitEntryOutput_UserForbidden() throws Exception {
        mockMvc.perform(get("/split-entry/output").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ─── /notifications/** ──────────────────────────────────────────────────────

    @Test
    void testNotifications_AdminAllowed() throws Exception {
        mockMvc.perform(get("/notifications").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void testNotifications_UserForbidden() throws Exception {
        mockMvc.perform(get("/notifications").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ─── /masterdb/** ───────────────────────────────────────────────────────────

    @Test
    void testMasterDb_UserForbidden() throws Exception {
        mockMvc.perform(get("/masterdb/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testMasterDb_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/masterdb/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testWeeklyTracking_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/weekly-tracking/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testWeeklyTracking_UserForbidden() throws Exception {
        mockMvc.perform(get("/weekly-tracking/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSalary_UserForbidden() throws Exception {
        mockMvc.perform(get("/salary/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ─── /report/** ─────────────────────────────────────────────────────────────

    @Test
    void testReport_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/report/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void testReport_UserForbidden() throws Exception {
        mockMvc.perform(get("/report/").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
