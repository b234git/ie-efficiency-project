package thienloc.manage.service;

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
 * Contract for Excel import/export operations.
 */
public interface IExcelService {

    ByteArrayInputStream generateTemplate() throws IOException;

    void importExcel(MultipartFile file, String username) throws IOException;

    void importExcel(byte[] fileBytes, String username) throws IOException;

    void importExcel(InputStream inputStream, String username) throws IOException;

    EntryImportPreviewDto parseForPreview(MultipartFile file) throws IOException;

    ByteArrayInputStream exportWeeklyReport(List<WeeklyReportDto> blocks, LocalDate weekStart) throws IOException;

    ByteArrayInputStream exportDailyReport(List<DailyProductionDto> records, LocalDate from, LocalDate to) throws IOException;
}
