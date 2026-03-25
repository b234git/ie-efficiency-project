package thienloc.manage.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

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
    void testEntry_AllRoles_UserAllowed() throws Exception {
        mockMvc.perform(get("/entry/").with(user("user").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void testEntry_AllRoles_ManagerAllowed() throws Exception {
        mockMvc.perform(get("/entry/").with(user("manager").roles("MANAGER")))
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
    void testMasterDb_AllRoles() throws Exception {
        mockMvc.perform(get("/masterdb/").with(user("user").roles("USER")))
                .andExpect(status().isOk());
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
