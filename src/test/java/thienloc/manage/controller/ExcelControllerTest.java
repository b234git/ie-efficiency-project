package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.entity.ImportJob;
import thienloc.manage.service.ExcelService;
import thienloc.manage.service.ImportJobService;
import thienloc.manage.service.NotificationService;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcelController.class)
@Import({SecurityConfig.class, thienloc.manage.security.TestRbacSecurityConfig.class})
class ExcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExcelService excelService;

    @MockitoBean
    private ImportJobService importJobService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testDownloadTemplate() throws Exception {
        when(excelService.generateTemplate())
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/excel/template").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=Production_Template.xlsx"));
    }

    @Test
    void testPreviewImport_ValidFile() throws Exception {
        EntryImportPreviewDto preview = new EntryImportPreviewDto();
        preview.setTotalRows(1);
        preview.setValidRows(1);
        preview.setErrorRows(0);
        preview.setRows(List.of());
        when(excelService.parseForPreview(any())).thenReturn(preview);

        // Header must carry the real XLSX (PK\x03\x04) magic bytes — ExcelFileValidator
        // now rejects files that are not genuine Excel before parsing.
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0});

        mockMvc.perform(multipart("/excel/preview").file(file)
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("entry-import-confirm"))
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    void testPreviewImport_ParseError() throws Exception {
        when(excelService.parseForPreview(any())).thenThrow(new RuntimeException("Bad file"));

        MockMultipartFile file = new MockMultipartFile("file", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/excel/preview").file(file)
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("importError"));
    }

    @Test
    void testConfirmImport() throws Exception {
        Path tempFile = Files.createTempFile("test-import-", ".xlsx");
        Files.write(tempFile, new byte[]{1, 2, 3});
        when(importJobService.createJob("ENTRY_IMPORT", "user"))
                .thenReturn(ImportJob.builder().id(5L).build());

        // Confirm now kicks off an async import job and redirects to its status page.
        mockMvc.perform(post("/excel/import/confirm")
                        .sessionAttr("entryImportFile", tempFile.toString())
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/excel/import/status/5"));

        verify(importJobService).runEntryImport(5L, tempFile.toString(), "user");
    }

    @Test
    void testConfirmImport_NoSession() throws Exception {
        mockMvc.perform(post("/excel/import/confirm")
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("error"));

        verify(excelService, never()).importExcel(any(byte[].class), anyString());
    }

    @Test
    void testCancelImport() throws Exception {
        mockMvc.perform(post("/excel/import/cancel")
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"));
    }
}
