package thienloc.manage.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the EFF JUNE import bug: the D-sheet Date column is a fill-down
 * formula (=C3+1) and Output is a VLOOKUP formula. parseDateCell / getInteger must
 * read the cached formula result instead of treating FORMULA cells as unparseable.
 */
class ExcelCellUtilTest {

    private Cell formulaCellWithNumber(Sheet sheet, int row, String formula, double cached) {
        Cell c = sheet.createRow(row).createCell(0);
        c.setCellFormula(formula);
        c.setCellValue(cached);   // POI stores this as the cached <v> result, keeps the formula
        return c;
    }

    @Test
    void parseDateCell_resolvesFormulaDate() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet();
            double serial = DateUtil.getExcelDate(new GregorianCalendar(2026, 5, 2).getTime()); // 2026-06-02
            Cell formulaDate = formulaCellWithNumber(s, 0, "A1+1", serial);
            assertEquals(LocalDate.of(2026, 6, 2), ExcelCellUtil.parseDateCell(formulaDate));

            // literal numeric date still works
            Cell literal = s.createRow(1).createCell(0);
            literal.setCellValue(new GregorianCalendar(2026, 5, 1).getTime());
            assertEquals(LocalDate.of(2026, 6, 1), ExcelCellUtil.parseDateCell(literal));
        }
    }

    @Test
    void getInteger_resolvesFormulaOutput() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet();
            Cell formulaOutput = formulaCellWithNumber(s, 0, "VLOOKUP(1,B:B,1,0)", 600);
            assertEquals(600, ExcelCellUtil.getInteger(formulaOutput));

            // string-result formula degrades to null, not an exception
            Cell strFormula = s.createRow(1).createCell(0);
            strFormula.setCellFormula("IFERROR(1/0,\"\")");
            strFormula.setCellValue("");
            assertNull(ExcelCellUtil.getInteger(strFormula));
        }
    }
}
