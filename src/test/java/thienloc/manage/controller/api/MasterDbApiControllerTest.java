package thienloc.manage.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.MasterDbService;
import thienloc.manage.service.NotificationService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(MasterDbApiController.class)
@Import(SecurityConfig.class)
class MasterDbApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    private MasterDbService masterDbService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void list_returnsPageAndAvailableMonths() throws Exception {
        MasterDb one = new MasterDb();
        one.setId(1L);
        one.setRef("R1");
        Page<MasterDb> page = new PageImpl<>(List.of(one), PageRequest.of(0, 10), 1);
        when(masterDbService.search(any(), any(), anyInt())).thenReturn(page);
        when(masterDbService.getDistinctMonths()).thenReturn(List.of("2026-04", "2026-03"));

        mockMvc.perform(get("/api/v1/masterdb")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.totalElements").value(1))
                .andExpect(jsonPath("$.records.content[0].ref").value("R1"))
                .andExpect(jsonPath("$.availableMonths.length()").value(2));
    }

    @Test
    void create_returns201() throws Exception {
        MasterDb saved = new MasterDb();
        saved.setId(99L);
        when(masterDbService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/masterdb")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ref", "R1", "articleNo", "A1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99));

        verify(masterDbService).save(any(MasterDb.class));
    }

    @Test
    void update_returns200_whenExists() throws Exception {
        MasterDb existing = new MasterDb();
        existing.setId(5L);
        when(masterDbService.findById(5L)).thenReturn(Optional.of(existing));

        MasterDb saved = new MasterDb();
        saved.setId(5L);
        saved.setRef("R-NEW");
        when(masterDbService.save(any())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/masterdb/5")
                        .with(user("mgr").roles("MANAGER"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ref", "R-NEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ref").value("R-NEW"));
    }

    @Test
    void update_returns404_whenMissing() throws Exception {
        when(masterDbService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/masterdb/999")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verify(masterDbService).findById(999L);
        verifyNoMoreInteractions(masterDbService);
    }

    @Test
    void delete_returns204_whenExists() throws Exception {
        when(masterDbService.findById(7L)).thenReturn(Optional.of(new MasterDb()));

        mockMvc.perform(delete("/api/v1/masterdb/7")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());

        verify(masterDbService).deleteById(7L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        when(masterDbService.findById(eq(8L))).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/masterdb/8")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/masterdb")
                        .with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/masterdb"))
                .andExpect(status().isUnauthorized());
    }
}
