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
import thienloc.manage.service.ExcelService;
import thienloc.manage.service.NotificationService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExcelController.class)
@Import(SecurityConfig.class)
class ExcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExcelService excelService;

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

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

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
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testConfirmImport() throws Exception {
        Path tempFile = Files.createTempFile("test-import-", ".xlsx");
        Files.write(tempFile, new byte[]{1, 2, 3});

        mockMvc.perform(post("/excel/import/confirm")
                        .sessionAttr("entryImportFile", tempFile.toString())
                        .with(user("user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("success"));

        verify(excelService).importExcel(any(InputStream.class), eq("user"));
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
