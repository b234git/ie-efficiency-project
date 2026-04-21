package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.dto.WeeklyReportDto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Facade over the three Excel subsystems (template, import, export).
 * Preserves the {@link IExcelService} contract used by controllers while
 * delegating to single-purpose services.
 */
@Service
public class ExcelService implements IExcelService {

    @Autowired
    private ExcelTemplateService templateService;

    @Autowired
    private EntryExcelImportService importService;

    @Autowired
    private EntryExcelExportService exportService;

    @Override
    public ByteArrayInputStream generateTemplate() throws IOException {
        return templateService.generateTemplate();
    }

    @Override
    public void importExcel(MultipartFile file, String username) throws IOException {
        importService.importExcel(file, username);
    }

    @Override
    public void importExcel(byte[] fileBytes, String username) throws IOException {
        importService.importExcel(fileBytes, username);
    }

    @Override
    public void importExcel(InputStream inputStream, String username) throws IOException {
        importService.importExcel(inputStream, username);
    }

    @Override
    public EntryImportPreviewDto parseForPreview(MultipartFile file) throws IOException {
        return importService.parseForPreview(file);
    }

    @Override
    public ByteArrayInputStream exportWeeklyReport(List<WeeklyReportDto> blocks, LocalDate weekStart) throws IOException {
        return exportService.exportWeeklyReport(blocks, weekStart);
    }

    @Override
    public ByteArrayInputStream exportDailyReport(List<DailyProductionDto> records,
                                                   LocalDate from, LocalDate to) throws IOException {
        return exportService.exportDailyReport(records, from, to);
    }
}
