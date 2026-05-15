package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SheetDetectorTest {

    private void writeProjectTemplateHeader(Sheet sheet) {
        Row r1 = sheet.createRow(0);
        r1.createCell(0).setCellValue("Date");
        r1.createCell(1).setCellValue("Section");
        r1.createCell(2).setCellValue("Line");
        r1.createCell(3).setCellValue("sub-line");
        r1.createCell(4).setCellValue("sub-section");
        r1.createCell(5).setCellValue("DL");
        r1.createCell(6).setCellValue("DLI");
        r1.createCell(7).setCellValue("IDL");
        r1.createCell(8).setCellValue("Output");
        r1.createCell(9).setCellValue("WT");
        r1.createCell(10).setCellValue("RFT");
        for (int i = 11; i <= 25; i++) {
            r1.createCell(i).setCellValue(String.format("%02d:00-%02d:00", 7+i-11, 8+i-11));
        }
        r1.createCell(26).setCellValue("Article");
        r1.createCell(27).setCellValue("Allowance");
    }

    private void writeFactoryHeader(Sheet sheet) {
        Row r1 = sheet.createRow(0);
        r1.createCell(0).setCellValue("REF 1");
        r1.createCell(1).setCellValue("REF 2");
        r1.createCell(2).setCellValue("Date");
        r1.createCell(4).setCellValue("Line");  // mislabelled — actual section
        r1.createCell(7).setCellValue("DL");
        r1.createCell(8).setCellValue("DLI");
        r1.createCell(9).setCellValue("IDL");
        r1.createCell(10).setCellValue("Output");
        r1.createCell(11).setCellValue("WT");
        r1.createCell(12).setCellValue("RFT");
        r1.createCell(13).setCellValue("Article");
        r1.createCell(28).setCellValue("ARTICLE");
        r1.createCell(29).setCellValue("Allowance");

        Row r2 = sheet.createRow(1);
        for (int i = 0; i < 15; i++) {
            r2.createCell(13 + i).setCellValue(String.format("%02d:00\n%02d:00", 7+i, 8+i));
        }
    }

    private void writeMasterDbHeader(Sheet sheet) {
        // Mimics the factory's "DB" sheet — article master, NOT daily detail.
        Row r1 = sheet.createRow(0);
        r1.createCell(0).setCellValue("REF");
        r1.createCell(1).setCellValue("Article No.");
        r1.createCell(2).setCellValue("Pattern No.");
        r1.createCell(3).setCellValue("Shoe Name");
        r1.createCell(4).setCellValue("OS Code");
    }

    @Test
    void finds_D_sheet_in_factory_workbook() {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("6S");
            writeMasterDbHeader(wb.createSheet("DB"));
            writeFactoryHeader(wb.createSheet("D"));
            wb.createSheet("Sheet1");

            Sheet found = SheetDetector.findDailyDetailSheet(wb);
            assertEquals("D", found.getSheetName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void finds_DB_sheet_when_no_D() {
        try (Workbook wb = new XSSFWorkbook()) {
            writeProjectTemplateHeader(wb.createSheet("DB"));

            Sheet found = SheetDetector.findDailyDetailSheet(wb);
            assertEquals("DB", found.getSheetName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void finds_by_signature_with_arbitrary_sheet_name() {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Cover");
            writeProjectTemplateHeader(wb.createSheet("Daily April"));

            Sheet found = SheetDetector.findDailyDetailSheet(wb);
            assertEquals("Daily April", found.getSheetName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void rejects_factory_DB_sheet_falls_through_to_D() {
        // The factory workbook's "DB" sheet (article master) does NOT have the daily
        // signature; detector must skip it and find "D" instead.
        try (Workbook wb = new XSSFWorkbook()) {
            writeMasterDbHeader(wb.createSheet("DB"));
            writeFactoryHeader(wb.createSheet("D"));

            Sheet found = SheetDetector.findDailyDetailSheet(wb);
            assertEquals("D", found.getSheetName());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void throws_when_no_sheet_matches() {
        try (Workbook wb = new XSSFWorkbook()) {
            writeMasterDbHeader(wb.createSheet("Random"));

            assertThrows(IllegalStateException.class,
                    () -> SheetDetector.findDailyDetailSheet(wb));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void hasDailySignature_true_for_project_template() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("X");
            writeProjectTemplateHeader(s);
            assertTrue(SheetDetector.hasDailySignature(s));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void hasDailySignature_false_for_master_DB() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("DB");
            writeMasterDbHeader(s);
            assertFalse(SheetDetector.hasDailySignature(s));
        } catch (Exception e) {
            fail(e);
        }
    }
}
