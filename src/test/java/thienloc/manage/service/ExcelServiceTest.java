package thienloc.manage.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.testutil.TestDataFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelServiceTest {

    @Mock
    private DailyProductionRepository productionRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ExcelService excelService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser("admin", "ROLE_ADMIN");
    }

    @Test
    void testGenerateTemplate_CorrectHeaders() throws Exception {
        ByteArrayInputStream bis = excelService.generateTemplate();

        try (XSSFWorkbook wb = new XSSFWorkbook(bis)) {
            Sheet sheet = wb.getSheet("DB");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Date", header.getCell(0).getStringCellValue());
            assertEquals("Section", header.getCell(1).getStringCellValue());
            assertEquals("Line", header.getCell(2).getStringCellValue());
            assertEquals("MP (DL)", header.getCell(3).getStringCellValue());
            // 10 base cols + 15 time slot cols = 25 total
            assertEquals("07:00-08:00 (Article)", header.getCell(10).getStringCellValue());
            assertEquals("21:00-22:00 (Article)", header.getCell(24).getStringCellValue());
        }
    }

    @Test
    void testImportExcel_ValidFile() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 25.0, 5.0, 8.0, 1000, 95.0, 100.0}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        excelService.importExcel(file, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyProduction>> captor = ArgumentCaptor.forClass(List.class);
        verify(productionRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        DailyProduction saved = captor.getValue().get(0);
        assertEquals("SEW", saved.getSection());
        assertEquals("1A", saved.getLine());
        assertEquals(30.0, saved.getMp(), 0.001);
    }

    @Test
    void testImportExcel_PercentageNormalization() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        // RFT=0.87 should become 87.0; Allowance=0.85 should become 85.0
        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 25.0, 5.0, 8.0, 1000, 0.87, 0.85}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        excelService.importExcel(file, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyProduction>> captor = ArgumentCaptor.forClass(List.class);
        verify(productionRepository).saveAll(captor.capture());
        DailyProduction saved = captor.getValue().get(0);
        assertEquals(87.0, saved.getRft(), 0.001);
        assertEquals(85.0, saved.getAllowance(), 0.001);
    }

    @Test
    void testImportExcel_DefaultAllowance() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        // Allowance column is null -> should default to 100.0
        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 25.0, 5.0, 8.0, 1000, 95.0, null}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        excelService.importExcel(file, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyProduction>> captor = ArgumentCaptor.forClass(List.class);
        verify(productionRepository).saveAll(captor.capture());
        assertEquals(100.0, captor.getValue().get(0).getAllowance(), 0.001);
    }

    @Test
    void testImportExcel_SkipsMissingRequiredFields() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", null, "1A", 30.0, 0, 0, 8.0},   // missing section -> skip
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 0, 0, 8.0}   // valid
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        excelService.importExcel(file, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyProduction>> captor = ArgumentCaptor.forClass(List.class);
        verify(productionRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void testImportExcel_TimeSlotArticles() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        // Col 10 = 07:00-08:00 article, col 11 = 08:00-09:00 article
        Object[] row = new Object[12];
        row[0] = "2026-03-15"; row[1] = "SEW"; row[2] = "1A";
        row[3] = 30.0; row[4] = 25.0; row[5] = 5.0; row[6] = 8.0;
        row[7] = 1000; row[8] = 95.0; row[9] = 100.0;
        row[10] = "Y12345"; row[11] = "Y12345";
        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(row));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        excelService.importExcel(file, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyProduction>> captor = ArgumentCaptor.forClass(List.class);
        verify(productionRepository).saveAll(captor.capture());
        DailyProduction saved = captor.getValue().get(0);
        assertEquals(2, saved.getDetails().size());
        assertEquals("07:00-08:00", saved.getDetails().get(0).getTimeSlot());
        assertEquals("Y12345", saved.getDetails().get(0).getArticleNo());
    }

    @Test
    void testImportExcel_UserNotFound_Throws() throws Exception {
        when(userService.findByUsername("unknown")).thenReturn(null);

        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 0, 0, 8.0}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        assertThrows(RuntimeException.class, () -> excelService.importExcel(file, "unknown"));
    }

    @Test
    void testParseForPreview_ValidFile() throws Exception {
        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 25.0, 5.0, 8.0, 1000, 95.0, 100.0}));
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        EntryImportPreviewDto preview = excelService.parseForPreview(file);

        assertEquals(1, preview.getTotalRows());
        assertEquals(1, preview.getValidRows());
        assertEquals(0, preview.getErrorRows());
    }

    @Test
    void testParseForPreview_MissingFieldsMarksInvalid() throws Exception {
        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", null, null, null}));  // missing section, line, mp
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        EntryImportPreviewDto preview = excelService.parseForPreview(file);

        assertEquals(1, preview.getTotalRows());
        assertEquals(0, preview.getValidRows());
        assertEquals(1, preview.getErrorRows());
        assertFalse(preview.getRows().get(0).isValid());
    }

    @Test
    void testImportExcel_FromByteArray() throws Exception {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        byte[] bytes = TestDataFactory.buildProductionExcelBytes(rows(
                new Object[]{"2026-03-15", "SEW", "1A", 30.0, 25.0, 5.0, 8.0, 1000, 95.0, 100.0}));

        excelService.importExcel(bytes, "admin");

        verify(productionRepository).saveAll(anyList());
    }

    private static List<Object[]> rows(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) {
            list.add(row);
        }
        return list;
    }
}
