package thienloc.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.WeeklyReportDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.testutil.TestDataFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionServiceTest {

    @Mock
    private DailyProductionRepository productionRepository;

    @Mock
    private MasterDbRepository masterDbRepository;

    @Mock
    private UserService userService;

    @Mock
    private EfficiencyCalculatorService efficiencyCalculator;

    @InjectMocks
    private ProductionService productionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser("admin", "ROLE_ADMIN");
    }

    // ─── saveDailyProduction ─────────────────────────────────────────────────────

    @Test
    void testSaveDailyProduction_NewRecord() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);

        when(productionRepository.save(any(DailyProduction.class)))
                .thenAnswer(inv -> {
                    DailyProduction dp = inv.getArgument(0);
                    dp.setId(1L);
                    return dp;
                });

        Long id = productionService.saveDailyProduction(dto, "admin");

        assertNotNull(id);
        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        DailyProduction saved = captor.getValue();
        assertEquals("SEW", saved.getSection());
        assertEquals("1A", saved.getLine());
        assertEquals(30.0, saved.getMp(), 0.001);
        assertEquals(testUser, saved.getCreatedBy());
    }

    @Test
    void testSaveDailyProduction_UpdateExisting() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        DailyProduction existing = TestDataFactory.createDailyProduction("SEW", "1A", 500, 20.0, 7.0);
        when(productionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(1L);
        dto.setMp(35.0);

        productionService.saveDailyProduction(dto, "admin");

        verify(productionRepository).findById(1L);
        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(35.0, captor.getValue().getMp(), 0.001);
    }

    @Test
    void testSaveDailyProduction_AllowanceNormalization_80() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        dto.setAllowance(80.0); // >1, should become 0.8

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(0.8, captor.getValue().getAllowance(), 0.001);
    }

    @Test
    void testSaveDailyProduction_AllowanceNormalization_085() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        dto.setAllowance(0.85); // <=1, stays as is

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(0.85, captor.getValue().getAllowance(), 0.001);
    }

    @Test
    void testSaveDailyProduction_NullAllowance_DefaultsTo1() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        dto.setAllowance(null);

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(1.0, captor.getValue().getAllowance(), 0.001);
    }

    @Test
    void testSaveDailyProduction_UserNotFound_Throws() {
        when(userService.findByUsername("unknown")).thenReturn(null);
        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();

        assertThrows(RuntimeException.class,
                () -> productionService.saveDailyProduction(dto, "unknown"));
    }

    @Test
    void testSaveDailyProduction_DetailsRebuilt() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        List<DailyProductionDetailDto> details = new ArrayList<>();
        DailyProductionDetailDto d1 = new DailyProductionDetailDto();
        d1.setTimeSlot("07:00-08:00");
        d1.setArticleNo("Y12345");
        details.add(d1);
        dto.setDetails(details);

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getDetails().size());
        assertEquals("07:00-08:00", captor.getValue().getDetails().get(0).getTimeSlot());
        assertEquals("Y12345", captor.getValue().getDetails().get(0).getArticleNo());
    }

    @Test
    void testSaveDailyProduction_EmptyArticleSkipped() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        List<DailyProductionDetailDto> details = new ArrayList<>();
        DailyProductionDetailDto d1 = new DailyProductionDetailDto();
        d1.setTimeSlot("07:00-08:00");
        d1.setArticleNo(""); // blank -> should be skipped
        details.add(d1);
        DailyProductionDetailDto d2 = new DailyProductionDetailDto();
        d2.setTimeSlot("08:00-09:00");
        d2.setArticleNo("Y12345"); // valid
        details.add(d2);
        dto.setDetails(details);

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getDetails().size());
        assertEquals("Y12345", captor.getValue().getDetails().get(0).getArticleNo());
    }

    @Test
    void testSaveDailyProduction_RftNormalization() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(productionRepository.save(any(DailyProduction.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyProductionDto dto = TestDataFactory.createDailyProductionDto();
        dto.setId(null);
        dto.setRft(0.87); // decimal -> should become 87.0

        productionService.saveDailyProduction(dto, "admin");

        ArgumentCaptor<DailyProduction> captor = ArgumentCaptor.forClass(DailyProduction.class);
        verify(productionRepository).save(captor.capture());
        assertEquals(87.0, captor.getValue().getRft(), 0.001);
    }

    // ─── Query methods ───────────────────────────────────────────────────────────

    @Test
    void testGetDashboardData() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);
        when(productionRepository.findByProductionDateOrderBySectionAscLineAsc(date))
                .thenReturn(List.of(dp));

        List<DailyProductionDto> result = productionService.getDashboardData(date);

        assertEquals(1, result.size());
        verify(efficiencyCalculator).populateEfficiencyMetricsBatch(anyList(), anyList());
    }

    @Test
    void testGetDashboardDataRange() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(productionRepository.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to))
                .thenReturn(List.of());

        List<DailyProductionDto> result = productionService.getDashboardDataRange(from, to);

        assertEquals(0, result.size());
        verify(productionRepository).findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(from, to);
    }

    @Test
    void testGetMyDataRange() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(productionRepository.findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(
                "admin", from, to)).thenReturn(List.of());

        List<DailyProductionDto> result = productionService.getMyDataRange("admin", from, to);

        assertEquals(0, result.size());
    }

    @Test
    void testGetById_Found() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);
        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertNotNull(result);
        assertEquals("SEW", result.getSection());
        verify(efficiencyCalculator).populateEfficiencyMetrics(any(DailyProductionDto.class), eq(dp));
    }

    @Test
    void testGetById_NotFound_Throws() {
        when(productionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productionService.getById(99L));
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    @Test
    void testDeleteRecord() {
        productionService.deleteRecord(1L);

        verify(productionRepository).deleteById(1L);
    }

    @Test
    void testDeleteMultipleRecords() {
        List<Long> ids = List.of(1L, 2L, 3L);

        productionService.deleteMultipleRecords(ids);

        verify(productionRepository).deleteAllById(ids);
    }

    @Test
    void testDeleteMultipleRecords_EmptyList() {
        productionService.deleteMultipleRecords(List.of());

        verify(productionRepository, never()).deleteAllById(anyList());
    }

    @Test
    void testDeleteMultipleRecords_NullList() {
        productionService.deleteMultipleRecords(null);

        verify(productionRepository, never()).deleteAllById(anyList());
    }

    // ─── DTO conversion ──────────────────────────────────────────────────────────

    @Test
    void testConvertToDto_MultipleArticles_DisplayArticle() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);
        DailyProductionDetail d1 = TestDataFactory.createDailyProductionDetail("07:00-08:00", "Y12345");
        DailyProductionDetail d2 = TestDataFactory.createDailyProductionDetail("08:00-09:00", "Y67890");
        d1.setDailyProduction(dp);
        d2.setDailyProduction(dp);
        dp.getDetails().add(d1);
        dp.getDetails().add(d2);

        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertEquals("Y12345 (+)", result.getArticle());
    }

    @Test
    void testConvertToDto_SingleArticle_DisplayArticle() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);
        DailyProductionDetail d1 = TestDataFactory.createDailyProductionDetail("07:00-08:00", "Y12345");
        d1.setDailyProduction(dp);
        dp.getDetails().add(d1);

        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertEquals("Y12345", result.getArticle());
    }

    @Test
    void testConvertToDto_NoDetails_DisplayArticleNA() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);

        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertEquals("N/A", result.getArticle());
    }

    @Test
    void testConvertToDto_ArticlesJson() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);
        DailyProductionDetail d1 = TestDataFactory.createDailyProductionDetail("07:00-08:00", "Y12345");
        d1.setDailyProduction(dp);
        dp.getDetails().add(d1);

        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertTrue(result.getArticlesJson().contains("07:00-08:00"));
        assertTrue(result.getArticlesJson().contains("Y12345"));
    }

    @Test
    void testConvertToDto_RefField() {
        DailyProduction dp = TestDataFactory.createDailyProduction("SEW", "1A", 1000, 30.0, 8.0);

        when(productionRepository.findById(1L)).thenReturn(Optional.of(dp));

        DailyProductionDto result = productionService.getById(1L);

        assertEquals("SEW1A", result.getRef());
    }

    // ─── Weekly Report ───────────────────────────────────────────────────────────

    @Test
    void testGetWeeklyReport_GroupsBySectionLine() {
        LocalDate weekStart = LocalDate.of(2026, 3, 13); // Friday
        DailyProduction dp1 = TestDataFactory.createDailyProduction("SEW", "1A", 500, 30.0, 8.0);
        dp1.setProductionDate(LocalDate.of(2026, 3, 14));
        DailyProduction dp2 = TestDataFactory.createDailyProduction("SEW", "1A", 600, 30.0, 8.0);
        dp2.setProductionDate(LocalDate.of(2026, 3, 15));

        when(productionRepository.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(
                weekStart, weekStart.plusDays(7)))
                .thenReturn(List.of(dp1, dp2));

        List<WeeklyReportDto> result = productionService.getWeeklyReport(weekStart);

        assertEquals(1, result.size()); // same section|line → 1 block
        assertEquals("SEW", result.get(0).getSection());
        assertEquals("1A", result.get(0).getLine());
        assertEquals(2, result.get(0).getDailyRows().size());
    }

    @Test
    void testGetWeeklyReport_SummaryCalculation() {
        LocalDate weekStart = LocalDate.of(2026, 3, 13);
        DailyProduction dp1 = TestDataFactory.createDailyProduction("SEW", "1A", 500, 30.0, 8.0);
        dp1.setProductionDate(LocalDate.of(2026, 3, 14));
        DailyProduction dp2 = TestDataFactory.createDailyProduction("SEW", "1A", 600, 28.0, 7.5);
        dp2.setProductionDate(LocalDate.of(2026, 3, 15));

        when(productionRepository.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(
                weekStart, weekStart.plusDays(7)))
                .thenReturn(List.of(dp1, dp2));

        List<WeeklyReportDto> result = productionService.getWeeklyReport(weekStart);

        WeeklyReportDto.SummaryRow summary = result.get(0).getTotal();
        assertEquals(1100, summary.getTotalOutput());
        assertEquals(2, summary.getDayCount());
        assertEquals(29.0, summary.getAvgMp(), 0.001); // (30+28)/2
        assertEquals(7.75, summary.getAvgWt(), 0.001); // (8+7.5)/2
    }

    @Test
    void testGetWeeklyReport_NoRecords() {
        LocalDate weekStart = LocalDate.of(2026, 3, 13);
        when(productionRepository.findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(
                weekStart, weekStart.plusDays(7)))
                .thenReturn(List.of());

        List<WeeklyReportDto> result = productionService.getWeeklyReport(weekStart);

        assertTrue(result.isEmpty());
    }
}
