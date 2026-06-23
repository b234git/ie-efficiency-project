package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import thienloc.manage.dto.VocChemicalSummaryDto;
import thienloc.manage.dto.VocReconcileCellDto;
import thienloc.manage.dto.VocReportDto;
import thienloc.manage.dto.VocSubconReportDto;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke tests: the new VOC exports produce a valid workbook with the expected sheets. */
class VocExcelExportServiceTest {

    private final VocExcelExportService svc = new VocExcelExportService();

    @Test
    void monthlyReportHasRollupAndReconcileSheets() throws Exception {
        VocReportDto r = new VocReportDto();
        r.setSelectedMonth("2026-04");
        r.setTotalOutput(1000);
        r.setTotalVocKg(12.5);
        VocChemicalSummaryDto c = new VocChemicalSummaryDto();
        c.setCode("577NT3");
        c.setMaterialType("SOLVENT");
        c.setQuantityKg(20.0);
        c.setVocKg(8.0);
        r.setChemicals(List.of(c));
        r.setReconcileChemicals(List.of("577NT3"));

        byte[] bytes = svc.exportMonthlyReport(r).readAllBytes();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(wb.getSheet("VOC Index (SF-SP)"), "SF-SP rollup sheet");
            assertNotNull(wb.getSheet("%"), "reconcile sheet");
        }
    }

    @Test
    void subconExportHasSheet() throws Exception {
        VocSubconReportDto r = new VocSubconReportDto();
        r.setSelectedMonth("2026-04");
        r.setChemicals(List.of("577NT3"));
        VocReconcileCellDto cell = new VocReconcileCellDto();
        cell.setAllowanceKg(5.0);
        cell.setActualKg(4.6);
        cell.setDiffKg(0.4);
        VocSubconReportDto.Row row = VocSubconReportDto.Row.builder()
                .id(1L).date(java.time.LocalDate.of(2026, 4, 1))
                .subcontractor("TH2").articleNo("ART1").output(600)
                .totalStandardKg(5.0).totalActualKg(4.6).totalShortageKg(0.4).vocKg(2.0)
                .build();
        row.getCells().put("577NT3", cell);
        r.setRows(List.of(row));

        byte[] bytes = svc.exportSubcon(r).readAllBytes();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(wb.getSheet("SUBCON"));
        }
    }
}
