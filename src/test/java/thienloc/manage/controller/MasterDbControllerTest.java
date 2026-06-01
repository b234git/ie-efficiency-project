package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.ImportPreviewDto;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.MasterDbImportService;
import thienloc.manage.service.MasterDbService;
import thienloc.manage.service.MasterDbTemplateService;
import thienloc.manage.service.NotificationService;
import thienloc.manage.service.SystemLogService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MasterDbController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class MasterDbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MasterDbService masterDbService;

    @MockitoBean
    private MasterDbImportService masterDbImportService;

    @MockitoBean
    private MasterDbTemplateService masterDbTemplateService;

    @MockitoBean
    private SystemLogService systemLogService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testList_WithPagination() throws Exception {
        when(masterDbService.search(isNull(), isNull(), eq(0)))
                .thenReturn(new PageImpl<>(List.of()));
        when(masterDbService.getDistinctMonths()).thenReturn(List.of());

        mockMvc.perform(get("/masterdb/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("masterdb"))
                .andExpect(model().attributeExists("records", "currentPage", "totalPages"));
    }

    @Test
    void testList_WithSearchKeyword() throws Exception {
        when(masterDbService.search(eq("Y123"), isNull(), eq(0)))
                .thenReturn(new PageImpl<>(List.of()));
        when(masterDbService.getDistinctMonths()).thenReturn(List.of());

        mockMvc.perform(get("/masterdb/").param("keyword", "Y123").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());

        verify(masterDbService).search(eq("Y123"), isNull(), eq(0));
    }

    @Test
    void testSave_NewRecord() throws Exception {
        when(masterDbService.save(any(MasterDb.class))).thenReturn(new MasterDb());

        mockMvc.perform(post("/masterdb/save")
                        .param("ref", "REF001")
                        .param("articleNo", "ART001")
                        .with(user("user").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        verify(masterDbService).save(any(MasterDb.class));
        verify(systemLogService).logAction(eq("ADD_MASTERDB"), anyString(), any());
    }

    @Test
    void testDelete() throws Exception {
        mockMvc.perform(post("/masterdb/delete").param("id", "1")
                        .with(user("user").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        verify(masterDbService).deleteById(1L);
    }

    @Test
    void testImportCommit() throws Exception {
        ImportPreviewDto preview = ImportPreviewDto.builder()
                .rows(List.of())
                .newCount(1)
                .updateCount(0)
                .dataMonth("2026-03")
                .build();
        when(masterDbImportService.commitImport(any())).thenReturn(1);

        mockMvc.perform(post("/masterdb/import/commit")
                        .sessionAttr("importPreview", preview)
                        .with(user("user").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/masterdb/"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testImportCancel() throws Exception {
        mockMvc.perform(post("/masterdb/import/cancel")
                        .with(user("user").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/masterdb/"));
    }

    @Test
    void testDownloadTemplate() throws Exception {
        when(masterDbTemplateService.generateTemplate()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/masterdb/template").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=MasterDb_Template.xlsx"));
    }
}
