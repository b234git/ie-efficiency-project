package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.dto.ImportPreviewDto;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.testutil.TestDataFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDbImportServiceTest {

    @Mock
    private MasterDbRepository masterDbRepository;

    // Persistence was extracted to MasterDbService.saveAll(); the import service
    // still reads existing rows via the repository but delegates the save here.
    @Mock
    private MasterDbService masterDbService;

    // Real registry so the import path's timing instrumentation works.
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private MasterDbImportService importService;

    @Test
    void testImportFromExcel_NewRecords() throws Exception {
        when(masterDbRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{"REF001", "ART001", "P001", "Shoe1", "OS001", 100.0, 30.0, 450.0, 72.0}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        int count = importService.importFromExcel(file);

        assertEquals(1, count);
        verify(masterDbService).saveAll(anyList());
    }

    @Test
    void testImportFromExcel_UpdatesExisting() throws Exception {
        MasterDb existing = MasterDb.builder().ref("REF001").articleNo("OLD_ART").build();
        when(masterDbRepository.findAll()).thenReturn(List.of(existing));

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{"REF001", "ART001", "P001", "Shoe1", "OS001"}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        int count = importService.importFromExcel(file);

        assertEquals(1, count);
        // The existing entity should have been updated
        assertEquals("ART001", existing.getArticleNo());
    }

    @Test
    void testImportFromExcel_WithDataMonth() throws Exception {
        when(masterDbRepository.findByDataMonth("2026-03")).thenReturn(Collections.emptyList());

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{"REF001", "ART001", "P001", "Shoe1", "OS001"}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        importService.importFromExcel(file, "2026-03");

        verify(masterDbRepository).findByDataMonth("2026-03");
    }

    @Test
    void testImportFromExcel_SkipsBlankRef() throws Exception {
        when(masterDbRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{null, "ART001", "P001"},  // blank ref -> skipped
                new Object[]{"REF001", "ART001", "P001"}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        int count = importService.importFromExcel(file);

        assertEquals(1, count);
    }

    @Test
    void testImportFromExcel_SkipsBlankArticle() throws Exception {
        when(masterDbRepository.findAll()).thenReturn(Collections.emptyList());

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{"REF001", null, "P001"},  // blank article -> skipped
                new Object[]{"REF002", "ART002", "P002"}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        int count = importService.importFromExcel(file);

        assertEquals(1, count);
    }

    @Test
    void testPreviewImport_NewVsUpdateStatus() throws Exception {
        MasterDb existing = MasterDb.builder().ref("REF001").articleNo("ART001").build();
        when(masterDbRepository.findAll()).thenReturn(List.of(existing));

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(
                new Object[]{"REF001", "ART001", "P001", "Shoe1"},  // UPDATE
                new Object[]{"REF002", "ART002", "P002", "Shoe2"}   // NEW
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportPreviewDto preview = importService.previewImport(file, null);

        assertEquals(2, preview.getTotalRows());
        assertEquals(1, preview.getNewCount());
        assertEquals(1, preview.getUpdateCount());
        assertEquals("UPDATE", preview.getRows().get(0).getStatus());
        assertEquals("NEW", preview.getRows().get(1).getStatus());
    }

    @Test
    void testCommitImport_BatchSaves() {
        MasterDb e1 = MasterDb.builder().ref("R1").articleNo("A1").build();
        MasterDb e2 = MasterDb.builder().ref("R2").articleNo("A2").build();

        ImportPreviewDto preview = ImportPreviewDto.builder()
                .rows(List.of(
                        ImportPreviewDto.ImportRowPreview.builder().ref("R1").entity(e1).build(),
                        ImportPreviewDto.ImportRowPreview.builder().ref("R2").entity(e2).build()))
                .build();

        int count = importService.commitImport(preview);

        assertEquals(2, count);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MasterDb>> captor = ArgumentCaptor.forClass(List.class);
        verify(masterDbService).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void testImportFromExcel_SectionMetricsMapped() throws Exception {
        when(masterDbRepository.findAll()).thenReturn(Collections.emptyList());

        // REF, Article, Pattern, Shoe, OS, then SEW(CT,MP,Quota,PPH), BUFF1ST(CT,MP,Quota,PPH)...
        Object[] row = new Object[37];
        row[0] = "REF001";
        row[1] = "ART001";
        row[2] = "P001";
        row[3] = "TestShoe";
        row[4] = "OS001";
        row[5] = 1681.0;  // sewCt
        row[6] = 30.0;    // sewMp
        row[7] = 450.0;   // sewQuotaDb
        row[8] = 72.0;    // sewPph
        row[9] = 100.0;   // buff1stCt

        byte[] bytes = TestDataFactory.buildMasterDbExcelBytes(rows(row));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        importService.importFromExcel(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MasterDb>> captor = ArgumentCaptor.forClass(List.class);
        verify(masterDbService).saveAll(captor.capture());
        MasterDb saved = captor.getValue().get(0);
        assertEquals(1681.0, saved.getSewCt(), 0.001);
        assertEquals(30.0, saved.getSewMp(), 0.001);
        assertEquals(450.0, saved.getSewQuotaDb(), 0.001);
        assertEquals(72.0, saved.getSewPph(), 0.001);
        assertEquals(100.0, saved.getBuff1stCt(), 0.001);
    }

    private static List<Object[]> rows(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) {
            list.add(row);
        }
        return list;
    }
}
