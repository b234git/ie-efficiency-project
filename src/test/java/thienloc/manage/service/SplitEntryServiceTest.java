package thienloc.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.dto.DailyProductionDetailDto;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.entity.SplitEntry;
import thienloc.manage.entity.SplitEntryDetail;
import thienloc.manage.entity.SplitEntryStatus;
import thienloc.manage.entity.User;
import thienloc.manage.mapper.SplitEntryMapper;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.SplitEntryRepository;
import thienloc.manage.testutil.TestDataFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitEntryServiceTest {

    @Mock
    private SplitEntryRepository splitEntryRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProductionService productionService;

    @Mock
    private DailyProductionRepository dailyProductionRepository;

    // Real mapper (dependency-free, field-mapping only) so convertToDto tests
    // exercise actual mapping logic instead of a null mock.
    @Spy
    private SplitEntryMapper splitEntryMapper = new SplitEntryMapper();

    @InjectMocks
    private SplitEntryService splitEntryService;

    private User testUser;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser("admin", "ROLE_ADMIN");
        testDate = LocalDate.of(2026, 3, 15);
    }

    // ─── Page 1: Manpower ────────────────────────────────────────────────────────

    @Test
    void testSaveManpower_NewEntry() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.empty());
        when(splitEntryRepository.save(any(SplitEntry.class)))
                .thenAnswer(inv -> {
                    SplitEntry se = inv.getArgument(0);
                    if (se.getId() == null) se.setId(1L);
                    return se;
                });

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();

        SplitEntry result = splitEntryService.saveManpower(dto, "admin");

        assertNotNull(result);
        assertEquals(30.0, result.getMp(), 0.001);
        assertEquals(25.0, result.getDli(), 0.001);
        assertEquals(5.0, result.getIdl(), 0.001);
        assertEquals(testUser, result.getManpowerFilledBy());
    }

    @Test
    void testSaveManpower_ExistingEntry() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        existing.setMp(20.0);
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setMp(35.0);

        SplitEntry result = splitEntryService.saveManpower(dto, "admin");

        assertEquals(35.0, result.getMp(), 0.001);
    }

    // ─── Page 2: Output ──────────────────────────────────────────────────────────

    @Test
    void testSaveOutput_SetsFields() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setWt(8.0);
        dto.setTotalOutput(1000);
        dto.setRft(95.0);

        SplitEntry result = splitEntryService.saveOutput(dto, "admin");

        assertEquals(8.0, result.getWt(), 0.001);
        assertEquals(1000, result.getTotalOutput());
        assertEquals(95.0, result.getRft(), 0.001);
        assertEquals(testUser, result.getOutputFilledBy());
    }

    // ─── Page 3: Articles & Allowance ────────────────────────────────────────────

    @Test
    void testSaveArticles_AllowanceNormalization_80() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setAllowance(80.0); // >1, should become 0.8

        SplitEntry result = splitEntryService.saveArticles(dto, "admin");

        assertEquals(0.8, result.getAllowance(), 0.001);
    }

    @Test
    void testSaveArticles_NullAllowance_DefaultsTo1() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setAllowance(null);

        SplitEntry result = splitEntryService.saveArticles(dto, "admin");

        assertEquals(1.0, result.getAllowance(), 0.001);
    }

    @Test
    void testSaveArticles_ClearsAndRebuildsDetails() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        // Add old detail
        SplitEntryDetail oldDetail = SplitEntryDetail.builder()
                .splitEntry(existing).timeSlot("07:00-08:00").articleNo("OLD_ART").output(0).build();
        existing.getDetails().add(oldDetail);

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setAllowance(85.0);
        List<DailyProductionDetailDto> details = new ArrayList<>();
        DailyProductionDetailDto d1 = new DailyProductionDetailDto();
        d1.setTimeSlot("08:00-09:00");
        d1.setArticleNo("NEW_ART");
        details.add(d1);
        // Blank article should be skipped
        DailyProductionDetailDto d2 = new DailyProductionDetailDto();
        d2.setTimeSlot("09:00-10:00");
        d2.setArticleNo("");
        details.add(d2);
        dto.setDetails(details);

        SplitEntry result = splitEntryService.saveArticles(dto, "admin");

        assertEquals(1, result.getDetails().size());
        assertEquals("NEW_ART", result.getDetails().get(0).getArticleNo());
        assertEquals(testUser, result.getArticleFilledBy());
    }

    // ─── checkAndSync ────────────────────────────────────────────────────────────

    @Test
    void testCheckAndSync_AllFilled_Synced() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        existing.setMp(30.0);
        existing.setWt(8.0);
        existing.setTotalOutput(1000);
        existing.setManpowerFilledBy(testUser);

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productionService.saveDailyProduction(any(DailyProductionDto.class), eq("admin")))
                .thenReturn(99L);

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setWt(8.0);
        dto.setTotalOutput(1000);
        dto.setRft(95.0);

        SplitEntry result = splitEntryService.saveOutput(dto, "admin");

        assertEquals(SplitEntryStatus.SYNCED, result.getStatus());
        assertEquals(99L, result.getLinkedDailyProductionId());
        verify(productionService).saveDailyProduction(any(DailyProductionDto.class), eq("admin"));
    }

    @Test
    void testCheckAndSync_PartialFill_Partial() {
        when(userService.findByUsername("admin")).thenReturn(testUser);
        // Entry with no WT or totalOutput yet
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.empty());
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();

        SplitEntry result = splitEntryService.saveManpower(dto, "admin");

        assertEquals(SplitEntryStatus.PARTIAL, result.getStatus());
        verify(productionService, never()).saveDailyProduction(any(), anyString());
    }

    @Test
    void testCheckAndSync_UsernamePriority() {
        // manpowerFilledBy > outputFilledBy > articleFilledBy
        User mpUser = TestDataFactory.createUser("mp_user", "ROLE_USER");
        mpUser.setId(2L);
        User outputUser = TestDataFactory.createUser("out_user", "ROLE_MANAGER");
        outputUser.setId(3L);

        when(userService.findByUsername("out_user")).thenReturn(outputUser);

        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        existing.setMp(30.0);
        existing.setManpowerFilledBy(mpUser);
        // When output is saved, all required fields will be set -> sync triggered
        // Username should be from manpowerFilledBy (priority)

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productionService.saveDailyProduction(any(DailyProductionDto.class), eq("mp_user")))
                .thenReturn(100L);

        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setWt(8.0);
        dto.setTotalOutput(1000);
        dto.setRft(95.0);

        splitEntryService.saveOutput(dto, "out_user");

        // Should sync using manpowerFilledBy username, not outputFilledBy
        verify(productionService).saveDailyProduction(any(DailyProductionDto.class), eq("mp_user"));
    }

    @Test
    void testCheckAndSync_AllowanceConvertedToPercent() {
        when(userService.findByUsername("admin")).thenReturn(testUser);

        SplitEntry existing = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        existing.setMp(30.0);
        existing.setWt(8.0);
        existing.setTotalOutput(1000);
        existing.setAllowance(0.85); // Stored as fraction
        existing.setManpowerFilledBy(testUser);

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(existing));
        when(splitEntryRepository.save(any(SplitEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<DailyProductionDto> dtoCaptor = ArgumentCaptor.forClass(DailyProductionDto.class);
        when(productionService.saveDailyProduction(dtoCaptor.capture(), eq("admin")))
                .thenReturn(99L);

        // Trigger sync via saveOutput (all fields already set)
        SplitEntryDto dto = TestDataFactory.createSplitEntryDto();
        dto.setWt(8.0);
        dto.setTotalOutput(1000);
        dto.setRft(95.0);

        splitEntryService.saveOutput(dto, "admin");

        // Allowance 0.85 → 85.0 when passed to ProductionService
        assertEquals(85.0, dtoCaptor.getValue().getAllowance(), 0.001);
    }

    // ─── Query methods ───────────────────────────────────────────────────────────

    @Test
    void testGetByDateSectionLine_Found() {
        SplitEntry entry = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        entry.setMp(30.0);
        entry.setManpowerFilledBy(testUser);
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(entry));

        Optional<SplitEntryDto> result = splitEntryService.getByDateSectionLine(testDate, "SEW", "1A");

        assertTrue(result.isPresent());
        assertEquals("SEW", result.get().getSection());
        assertEquals("1A", result.get().getLine());
    }

    @Test
    void testGetByDateSectionLine_NotFound() {
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.empty());

        Optional<SplitEntryDto> result = splitEntryService.getByDateSectionLine(testDate, "SEW", "1A");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetEntriesForDate() {
        SplitEntry entry = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        when(splitEntryRepository.findByProductionDateOrderBySectionAscLineAsc(testDate))
                .thenReturn(List.of(entry));
        when(splitEntryRepository.findLinkedProductionIdsByDate(testDate))
                .thenReturn(List.of());
        when(dailyProductionRepository.findByProductionDateOrderBySectionAscLineAsc(testDate))
                .thenReturn(List.of());

        List<SplitEntryDto> result = splitEntryService.getEntriesForDate(testDate);

        assertEquals(1, result.size());
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    @Test
    void testDeleteEntry() {
        splitEntryService.deleteEntry(1L);

        verify(splitEntryRepository).deleteById(1L);
    }

    // ─── convertToDto ────────────────────────────────────────────────────────────

    @Test
    void testConvertToDto_AllowanceMultipliedBy100() {
        SplitEntry entry = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        entry.setAllowance(0.85);
        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(entry));

        Optional<SplitEntryDto> result = splitEntryService.getByDateSectionLine(testDate, "SEW", "1A");

        assertTrue(result.isPresent());
        assertEquals(85.0, result.get().getAllowance(), 0.001);
    }

    @Test
    void testConvertToDto_FilledFlags() {
        SplitEntry entry = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        entry.setMp(30.0); // manpower filled
        entry.setTotalOutput(1000); // output filled
        // No details → articlesFilled = false

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(entry));

        Optional<SplitEntryDto> result = splitEntryService.getByDateSectionLine(testDate, "SEW", "1A");

        assertTrue(result.isPresent());
        assertTrue(result.get().isManpowerFilled());
        assertTrue(result.get().isOutputFilled());
        assertFalse(result.get().isArticlesFilled());
    }

    @Test
    void testConvertToDto_FilledByUsernames() {
        User outputUser = TestDataFactory.createUser("output_user", "ROLE_MANAGER");
        outputUser.setId(2L);

        SplitEntry entry = TestDataFactory.createSplitEntry(testDate, "SEW", "1A");
        entry.setManpowerFilledBy(testUser);
        entry.setOutputFilledBy(outputUser);

        when(splitEntryRepository.findByProductionDateAndSectionAndLine(testDate, "SEW", "1A"))
                .thenReturn(Optional.of(entry));

        Optional<SplitEntryDto> result = splitEntryService.getByDateSectionLine(testDate, "SEW", "1A");

        assertTrue(result.isPresent());
        assertEquals("admin", result.get().getManpowerFilledByUsername());
        assertEquals("output_user", result.get().getOutputFilledByUsername());
        assertNull(result.get().getArticleFilledByUsername());
    }
}
