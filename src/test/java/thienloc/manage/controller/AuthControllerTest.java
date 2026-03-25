package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.User;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SystemLogService;
import thienloc.manage.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SystemLogService systemLogService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void testRegisterPage() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    void testRegisterUser_Success() throws Exception {
        when(userService.findByUsername("newuser")).thenReturn(null);

        mockMvc.perform(post("/register")
                        .param("username", "newuser")
                        .param("password", "pass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?success"));

        verify(userService).registerUser(any(User.class));
        verify(systemLogService).logAction(eq("REGISTER"), anyString());
    }

    @Test
    void testRegisterUser_DuplicateUsername() throws Exception {
        when(userService.findByUsername("existing")).thenReturn(
                User.builder().username("existing").build());

        mockMvc.perform(post("/register")
                        .param("username", "existing")
                        .param("password", "pass123")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(userService, never()).registerUser(any());
    }
}
