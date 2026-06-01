package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.dto.EntryImportPreviewDto;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExcelService is a thin facade — it delegates template generation to ExcelTemplateService
 * and all import/preview work to EntryExcelImportService. These tests pin that delegation;
 * the underlying logic is covered by ExcelTemplateServiceTest and EntryExcelImportServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class ExcelServiceTest {

    @Mock
    private ExcelTemplateService templateService;

    @Mock
    private EntryExcelImportService importService;

    @InjectMocks
    private ExcelService excelService;

    @Test
    void testGenerateTemplate_DelegatesToTemplateService() throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(templateService.generateTemplate()).thenReturn(stream);

        assertSame(stream, excelService.generateTemplate());
        verify(templateService).generateTemplate();
    }

    @Test
    void testImportExcel_File_DelegatesToImportService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1, 2, 3});

        excelService.importExcel(file, "admin");

        verify(importService).importExcel(file, "admin");
    }

    @Test
    void testImportExcel_Bytes_DelegatesToImportService() throws Exception {
        byte[] bytes = {1, 2, 3};

        excelService.importExcel(bytes, "admin");

        verify(importService).importExcel(bytes, "admin");
    }

    @Test
    void testParseForPreview_DelegatesToImportService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1, 2, 3});
        EntryImportPreviewDto preview = new EntryImportPreviewDto();
        when(importService.parseForPreview(file)).thenReturn(preview);

        assertSame(preview, excelService.parseForPreview(file));
        verify(importService).parseForPreview(file);
    }
}
