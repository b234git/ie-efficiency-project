package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testList_AdminRole() throws Exception {
        when(notificationService.getAll("ROLE_ADMIN")).thenReturn(List.of());

        mockMvc.perform(get("/notifications").with(user("user").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications"))
                .andExpect(model().attributeExists("notifications"));
    }

    @Test
    void testList_UserRole_Forbidden() throws Exception {
        mockMvc.perform(get("/notifications").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
