package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import thienloc.manage.dto.SalaryReportDto;
import thienloc.manage.dto.SalaryReportDto.DayRow;
import thienloc.manage.dto.SalaryReportDto.SectionLineBlock;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke test: the incentive "S" export produces a valid workbook with a block + totals. */
class SalaryExcelExportServiceTest {

    private final SalaryExcelExportService svc = new SalaryExcelExportService();

    @Test
    void exportBuildsSSheet() throws Exception {
        SalaryReportDto r = new SalaryReportDto();
        r.setMonth("2026-04");
        SectionLineBlock b = new SectionLineBlock();
        b.setSection("SEW");
        b.setLine("1");
        b.setSixSPercent(93.5);
        b.setReprocessPercent(100.0);
        b.setNewStyleCount(2);
        b.setNewStyleIncentive(60000);
        b.setGradeLabels(List.of("A", "B"));
        b.setGradeTotals(new long[]{100, 200});
        DayRow d = new DayRow();
        d.setDate(LocalDate.of(2026, 4, 1));
        d.setMp(20);
        d.setWt(8.0);
        d.setOutput(1000);
        d.setSec("SEW10");
        d.setEffSalary(105.0);
        d.setBaseRate(50000);
        d.setGradeAmounts(new long[]{10, 20});
        b.setDailyRows(List.of(d));
        r.setBlocks(List.of(b));

        byte[] bytes = svc.export(r).readAllBytes();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheet("S");
            assertNotNull(s);
            assertTrue(s.getLastRowNum() >= 3, "title + header + day + total rows");
        }
    }
}
