package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.security.SecurityConfig;
import thienloc.manage.service.ExcelService;
import thienloc.manage.service.NotificationService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
    @WithMockUser(roles = "USER")
    void testDownloadTemplate() throws Exception {
        when(excelService.generateTemplate())
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/excel/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=Production_Template.xlsx"));
    }

    @Test
    @WithMockUser(roles = "USER")
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

        mockMvc.perform(multipart("/excel/preview").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("entry-import-confirm"))
                .andExpect(model().attributeExists("preview"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testPreviewImport_ParseError() throws Exception {
        when(excelService.parseForPreview(any())).thenThrow(new RuntimeException("Bad file"));

        MockMultipartFile file = new MockMultipartFile("file", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/excel/preview").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testConfirmImport() throws Exception {
        mockMvc.perform(post("/excel/import/confirm")
                        .sessionAttr("entryImportFile", new byte[]{1, 2, 3})
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("success"));

        verify(excelService).importExcel(any(byte[].class), eq("user"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testConfirmImport_NoSession() throws Exception {
        mockMvc.perform(post("/excel/import/confirm")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"))
                .andExpect(flash().attributeExists("error"));

        verify(excelService, never()).importExcel(any(byte[].class), anyString());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCancelImport() throws Exception {
        mockMvc.perform(post("/excel/import/cancel")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/entry"));
    }
}
