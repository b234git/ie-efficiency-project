package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Tests the import-template generation (extracted from ExcelService into ExcelTemplateService). */
class ExcelTemplateServiceTest {

    private final ExcelTemplateService templateService = new ExcelTemplateService();

    @Test
    void testGenerateTemplate_CorrectHeaders() throws Exception {
        ByteArrayInputStream bis = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(bis)) {
            Sheet sheet = wb.getSheet("DB");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Date", header.getCell(0).getStringCellValue());
            assertEquals("Section", header.getCell(1).getStringCellValue());
            assertEquals("Line", header.getCell(2).getStringCellValue());
            assertEquals("sub-line", header.getCell(3).getStringCellValue());
            // Cols 11-25: merged "Article" header; sub-headers in row 1
            assertEquals("Article", header.getCell(11).getStringCellValue());
            Row slotRow = sheet.getRow(1);
            assertEquals("07:00\n08:00", slotRow.getCell(11).getStringCellValue());
            assertEquals("21:00\n22:00", slotRow.getCell(25).getStringCellValue());
        }
    }
}
