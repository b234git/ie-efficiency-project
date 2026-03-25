package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MasterDbTemplateServiceTest {

    private final MasterDbTemplateService templateService = new MasterDbTemplateService();

    @Test
    void testGenerateTemplate_CreatesValidXlsx() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // Verify it can be parsed back
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(wb);
        }
    }

    @Test
    void testGenerateTemplate_HasDbSheet() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("DB");
            assertNotNull(sheet, "Sheet 'DB' should exist");
        }
    }

    @Test
    void testGenerateTemplate_Row0HasSectionHeaders() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row0 = wb.getSheet("DB").getRow(0);
            // First 5 cols are ID columns
            assertEquals("REF", row0.getCell(0).getStringCellValue());
            assertEquals("Article No.", row0.getCell(1).getStringCellValue());
            assertEquals("Pattern No.", row0.getCell(2).getStringCellValue());
            assertEquals("Shoe Name", row0.getCell(3).getStringCellValue());
            assertEquals("OS Code", row0.getCell(4).getStringCellValue());
            // Col 5 starts section headers
            assertEquals("SEW", row0.getCell(5).getStringCellValue());
            assertEquals("BUFFING 1ST", row0.getCell(9).getStringCellValue());
        }
    }

    @Test
    void testGenerateTemplate_Row1HasSubHeaders() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row1 = wb.getSheet("DB").getRow(1);
            assertEquals("CT", row1.getCell(5).getStringCellValue());
            assertEquals("MP", row1.getCell(6).getStringCellValue());
            assertEquals("QUOTA", row1.getCell(7).getStringCellValue());
            assertEquals("PPH", row1.getCell(8).getStringCellValue());
            // Repeat for next section
            assertEquals("CT", row1.getCell(9).getStringCellValue());
        }
    }

    @Test
    void testGenerateTemplate_HasSampleDataRow() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row2 = wb.getSheet("DB").getRow(2);
            assertEquals("Y01748P7402H0038", row2.getCell(0).getStringCellValue());
            assertEquals("Y01748P7402", row2.getCell(1).getStringCellValue());
            assertEquals(1681.0, row2.getCell(5).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void testGenerateTemplate_FrozenPanes() throws IOException {
        byte[] bytes = templateService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("DB");
            // PaneInformation should show freeze at row 2
            assertNotNull(sheet.getPaneInformation());
            assertEquals(2, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }
}
