package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.NewStyleEntry;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.NewStyleService;
import thienloc.manage.service.NotificationService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewStyleApiController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class NewStyleApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private NewStyleService service;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void list_byMonth_returnsEntries() throws Exception {
        NewStyleEntry e = new NewStyleEntry();
        e.setId(1L);
        e.setStyle("NV-A");
        when(service.getByMonth("2026-04")).thenReturn(List.of(e));

        mockMvc.perform(get("/api/v1/new-styles")
                        .param("month", "2026-04")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].style").value("NV-A"));
    }

    @Test
    void list_defaultsToCurrentMonth_whenMonthBlank() throws Exception {
        when(service.getByMonth(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/new-styles")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(service).getByMonth(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void create_returns201() throws Exception {
        NewStyleEntry saved = new NewStyleEntry();
        saved.setId(99L);
        saved.setStyle("NV-A");
        when(service.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/new-styles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "dataMonth", "2026-04",
                                "section", "SEW",
                                "line", "1A",
                                "style", "NV-A",
                                "quantity", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99));
    }

    @Test
    void update_returns200_whenExists() throws Exception {
        when(service.findById(5L)).thenReturn(Optional.of(new NewStyleEntry()));
        NewStyleEntry saved = new NewStyleEntry();
        saved.setId(5L);
        saved.setStyle("NV-NEW");
        when(service.save(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/new-styles/5")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "dataMonth", "2026-04",
                                "section", "SEW",
                                "line", "1A",
                                "style", "NV-NEW",
                                "quantity", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").value("NV-NEW"));
    }

    @Test
    void delete_returns204_whenExists() throws Exception {
        when(service.findById(7L)).thenReturn(Optional.of(new NewStyleEntry()));

        mockMvc.perform(delete("/api/v1/new-styles/7")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(service).deleteById(7L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        when(service.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/new-styles/99")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());

        verify(service).findById(99L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void list_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/new-styles")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
