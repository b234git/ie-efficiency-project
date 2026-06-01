package thienloc.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EfficiencyCalculatorServiceTest {

    @Mock
    private MasterDbRepository masterDbRepository;

    @InjectMocks
    private EfficiencyCalculatorService calculator;

    private MasterDb sampleMasterDb;

    @BeforeEach
    void setUp() {
        sampleMasterDb = MasterDb.builder()
                .ref("46024SEW1A")
                .articleNo("Y12345")
                .patternNo("P001")
                .shoeName("TestShoe")
                .sewCt(1681.0)
                .sewMp(30.0)
                .sewQuotaDb(450.0)
                .sewPph(72.0)
                .dataMonth("2026-02")
                .build();
    }

    @Test
    void testActualPph() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", 1000, 25.0, 8.0, 30.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        // Actual PPH = 1000 / 25 / 8 = 5.0
        assertNotNull(dto.getActualPph());
        assertEquals(5.0, dto.getActualPph(), 0.001);
    }

    @Test
    void testEffKpi() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", 1000, 25.0, 8.0, 30.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        // EFF KPI = Output / (MP * WT * 3600 * Allowance / CT)
        // Target = 30 * 8 * 3600 * 0.85 / 1681 = 437.24...
        // EFF KPI = 1000 / 437.24 ≈ 2.287...
        assertNotNull(dto.getEffKpi());
        assertTrue(dto.getEffKpi() > 0);
    }

    @Test
    void testNullOutputSkipsCalculation() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", null, 25.0, 8.0, 30.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        assertNull(dto.getEffKpi());
        assertNull(dto.getActualPph());
    }

    @Test
    void testZeroMpSkipsCalculation() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", 1000, 25.0, 8.0, 0.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        assertNull(dto.getEffKpi());
    }

    @Test
    void testMasterDbNotFound() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("UNKNOWN");
        DailyProduction entity = buildEntity("SEW", "UNKNOWN", 1000, 25.0, 8.0, 30.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(Collections.emptyList());

        calculator.populateEfficiencyMetrics(dto, entity);

        assertNull(dto.getPatternNo());
        assertNull(dto.getShoeName());
    }

    @Test
    void testAllowanceNormalization() {
        // 80 → 0.8
        assertEquals(0.8, calculator.normalizeAllowance(80.0), 0.001);
        // 0.85 stays
        assertEquals(0.85, calculator.normalizeAllowance(0.85), 0.001);
        // null → 1.0
        assertEquals(1.0, calculator.normalizeAllowance(null), 0.001);
        // 0 → 1.0
        assertEquals(1.0, calculator.normalizeAllowance(0.0), 0.001);
    }

    @Test
    void testMonthAwareLookupWithFallback() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", 500, 20.0, 8.0, 25.0, 0.85);
        entity.setDli(20.0);
        // Add a detail so the article appears in the batch load
        DailyProductionDetail detail = new DailyProductionDetail();
        detail.setArticleNo("Y12345");
        entity.setDetails(List.of(detail));

        // Month-specific batch lookup returns empty
        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), eq("2026-03")))
                .thenReturn(Collections.emptyList());
        // Fallback batch load returns sampleMasterDb
        when(masterDbRepository.findByArticleNoInOrderByRefAsc(anyList()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        // Should have found MasterDb via fallback batch map
        assertEquals("P001", dto.getPatternNo());
        assertEquals("TestShoe", dto.getShoeName());
    }

    // ─── Per-slot suffix "-2" → 1ST vs 2ND column ──────────────────────────

    @Test
    void testBuffSlotWithSuffixDash2UsesSecondColumn() {
        // Row section = "BUFF 1" (subsec=1), but one slot has article "406203-2".
        // That slot must resolve to BUFF_2ND and look up buff2nd* columns.
        MasterDb md = MasterDb.builder()
                .articleNo("406203")
                .patternNo("P-BUFF").shoeName("BuffShoe")
                .buff1stCt(100.0).buff1stMp(5.0).buff1stQuotaDb(500.0).buff1stPph(36.0)
                .buff2ndCt(200.0).buff2ndMp(4.0).buff2ndQuotaDb(400.0).buff2ndPph(18.0)
                .build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("406203-2");
        DailyProduction entity = buildEntity("BUFF 1", "406203-2", 1000, 20.0, 8.0, 10.0, 1.0);
        entity.setDli(20.0);

        // Detail with "-2" suffix — should resolve to BUFF_2ND.
        DailyProductionDetail d = new DailyProductionDetail();
        d.setArticleNo("406203-2");
        d.setOutput(1000);
        d.setTimeSlot("07:00-08:00");
        entity.setDetails(List.of(d));

        // MasterDb is stored under cleaned article "406203".
        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(md));

        calculator.populateEfficiencyMetrics(dto, entity);

        // stdPph should come from 2ND column = 18.0, not 1ST (36.0)
        assertEquals(18.0, dto.getStdPph(), 0.001);
    }

    @Test
    void testBuffSubsec2NoSuffixFallsBackTo1StWhenNo2ndData() {
        // Row section = "BUFF 2", article has no "-2". MasterDb only has 1ST data.
        // Expected: try 2ND first (null) → fallback to 1ST.
        MasterDb md = MasterDb.builder()
                .articleNo("406203")
                .patternNo("P-BUFF").shoeName("BuffShoe")
                .buff1stCt(100.0).buff1stMp(5.0).buff1stQuotaDb(500.0).buff1stPph(36.0)
                .buff2ndCt(null).buff2ndMp(null).buff2ndQuotaDb(null).buff2ndPph(null)
                .build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("406203");
        DailyProduction entity = buildEntity("BUFF 2", "406203", 500, 20.0, 8.0, 10.0, 1.0);
        entity.setDli(20.0);
        DailyProductionDetail d = new DailyProductionDetail();
        d.setArticleNo("406203");
        d.setOutput(500);
        d.setTimeSlot("07:00-08:00");
        entity.setDetails(List.of(d));

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(md));

        calculator.populateEfficiencyMetrics(dto, entity);

        // stdPph falls back to 1ST column = 36.0
        assertEquals(36.0, dto.getStdPph(), 0.001);
    }

    @Test
    void testBuffSubsec2NoSuffixPrefers2ndWhenAvailable() {
        // Row section = "BUFF 2", article has no "-2". MasterDb has BOTH 1ST and 2ND data.
        // Expected: 2ND preferred (user rule: fallback chỉ khi primary null).
        MasterDb md = MasterDb.builder()
                .articleNo("406203")
                .patternNo("P-BUFF").shoeName("BuffShoe")
                .buff1stCt(100.0).buff1stMp(5.0).buff1stQuotaDb(500.0).buff1stPph(36.0)
                .buff2ndCt(200.0).buff2ndMp(4.0).buff2ndQuotaDb(400.0).buff2ndPph(18.0)
                .build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("406203");
        DailyProduction entity = buildEntity("BUFF 2", "406203", 500, 20.0, 8.0, 10.0, 1.0);
        entity.setDli(20.0);
        DailyProductionDetail d = new DailyProductionDetail();
        d.setArticleNo("406203");
        d.setOutput(500);
        d.setTimeSlot("07:00-08:00");
        entity.setDetails(List.of(d));

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(md));

        calculator.populateEfficiencyMetrics(dto, entity);

        // Prefers 2ND = 18.0 (not fallback to 1ST)
        assertEquals(18.0, dto.getStdPph(), 0.001);
    }

    // ─── Multi-article rows (per-slot weighting) ─────────────────────────────

    @Test
    void testMultiArticleWeightedTargetAndCt() {
        // SEW row: article Y100 over 3 slots, Y200 over 2 slots — both in Master DB.
        MasterDb mdA = MasterDb.builder().articleNo("Y100").patternNo("PA").shoeName("SA")
                .sewCt(1000.0).sewPph(60.0).sewMp(20.0).sewQuotaDb(300.0).build();
        MasterDb mdB = MasterDb.builder().articleNo("Y200")
                .sewCt(2000.0).sewPph(30.0).sewMp(10.0).sewQuotaDb(200.0).build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y100 (+)");
        DailyProduction entity = buildEntity("SEW", "Y100 (+)", 1000, 25.0, 10.0, 40.0, 1.0);
        entity.setDetails(List.of(
                detailOf("Y100", "07:00-08:00"), detailOf("Y100", "08:00-09:00"),
                detailOf("Y100", "09:00-10:00"), detailOf("Y200", "10:00-11:00"),
                detailOf("Y200", "11:00-12:00")));

        stubMasterDb(mdA, mdB);

        calculator.populateEfficiencyMetrics(dto, entity);

        // weightedCt = (1000*3 + 2000*2) / 5 = 1400
        assertEquals(1400.0, dto.getTct(), 0.001);
        // weightedPph = (60*3 + 30*2)/5 = 48 ; target = MP*WT*pph*allowance = 40*10*48 = 19200
        assertEquals(19200.0, dto.getTarget(), 0.001);
        // effKpi = Output * weightedCt / (MP*WT*3600*allowance) = 1000*1400 / 1_440_000
        assertEquals(1_400_000.0 / 1_440_000.0, dto.getEffKpi(), 0.0001);
    }

    @Test
    void testPartialMasterDbMissRenormalizesTarget() {
        // Y100 resolves; Y200 is absent from Master DB. The 2 missing slots must NOT
        // dilute the target. New (renormalized) target = 40*10*60*1.0 = 24000.
        // OLD behaviour divided by totalSlots (5): 40*10*(60*3)/5 = 14400 → EFF% overstated.
        MasterDb mdA = MasterDb.builder().articleNo("Y100").patternNo("PA").shoeName("SA")
                .sewCt(1000.0).sewPph(60.0).sewMp(20.0).sewQuotaDb(300.0).build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y100 (+)");
        DailyProduction entity = buildEntity("SEW", "Y100 (+)", 1000, 25.0, 10.0, 40.0, 1.0);
        entity.setDetails(List.of(
                detailOf("Y100", "07:00-08:00"), detailOf("Y100", "08:00-09:00"),
                detailOf("Y100", "09:00-10:00"), detailOf("Y200", "10:00-11:00"),
                detailOf("Y200", "11:00-12:00")));

        stubMasterDb(mdA); // Y200 not returned

        calculator.populateEfficiencyMetrics(dto, entity);

        assertEquals(24000.0, dto.getTarget(), 0.001);
        // weightedCt over resolved slots only = (1000*3)/3 = 1000
        assertEquals(1000.0, dto.getTct(), 0.001);
    }

    @Test
    void testMultiArticleEffSalary() {
        MasterDb mdA = MasterDb.builder().articleNo("Y100").patternNo("PA").shoeName("SA")
                .sewCt(1000.0).sewPph(60.0).sewMp(20.0).sewQuotaDb(300.0).build();
        MasterDb mdB = MasterDb.builder().articleNo("Y200")
                .sewCt(2000.0).sewPph(30.0).sewMp(10.0).sewQuotaDb(200.0).build();

        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y100 (+)");
        DailyProduction entity = buildEntity("SEW", "Y100 (+)", 1000, 25.0, 10.0, 40.0, 1.0);
        entity.setDetails(List.of(
                detailOf("Y100", "07:00-08:00"), detailOf("Y100", "08:00-09:00"),
                detailOf("Y100", "09:00-10:00"), detailOf("Y200", "10:00-11:00"),
                detailOf("Y200", "11:00-12:00")));

        stubMasterDb(mdA, mdB);

        calculator.populateEfficiencyMetrics(dto, entity);

        // sumQuota = (300/10)*3 + (200/10)*2 = 90 + 40 = 130 ; sumMp = 20*3 + 10*2 = 80 ; slots = 5
        // refTime = 5 (≤10) ; adjustedSumQuota = 130 ; avgMp = 16
        // salaryTarget = (130/16)*DLI*allowance = 8.125*25 = 203.125 ; effSalary = 1000/203.125
        assertEquals(1000.0 / 203.125, dto.getEffSalary(), 0.0001);
    }

    @Test
    void testSingleArticleRowMatchesLegacyTarget() {
        // Equivalence guard: a fully-resolved single-article row (all slots one article)
        // yields target = MP*WT*PPH*allowance, unchanged by the renormalization refactor.
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("Y12345");
        DailyProduction entity = buildEntity("SEW", "Y12345", 1000, 25.0, 8.0, 30.0, 0.85);
        entity.setDetails(List.of(
                detailOf("Y12345", "07:00-08:00"), detailOf("Y12345", "08:00-09:00"),
                detailOf("Y12345", "09:00-10:00")));

        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenReturn(List.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        // target = 30 * 8 * 72 * 0.85 = 14688 ; tct = sewCt = 1681
        assertEquals(30.0 * 8.0 * 72.0 * 0.85, dto.getTarget(), 0.001);
        assertEquals(1681.0, dto.getTct(), 0.001);
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private DailyProductionDetail detailOf(String articleNo, String timeSlot) {
        DailyProductionDetail d = new DailyProductionDetail();
        d.setArticleNo(articleNo);
        d.setTimeSlot(timeSlot);
        d.setOutput(0);
        return d;
    }

    /** Stub the month-aware batch + single lookups, returning only the given MasterDb rows. */
    private void stubMasterDb(MasterDb... rows) {
        when(masterDbRepository.findByArticleNoInAndDataMonthOrderByRefAsc(anyList(), anyString()))
                .thenAnswer(inv -> {
                    List<String> arts = inv.getArgument(0);
                    List<MasterDb> out = new java.util.ArrayList<>();
                    for (MasterDb m : rows) {
                        if (arts.contains(m.getArticleNo())) out.add(m);
                    }
                    return out;
                });
    }

    private DailyProduction buildEntity(String section, String article, Integer output,
                                         Double dli, Double wt, Double mp, Double allowance) {
        DailyProduction e = new DailyProduction();
        e.setSection(section);
        e.setTotalOutput(output);
        e.setDli(dli);
        e.setWt(wt);
        e.setMp(mp);
        e.setAllowance(allowance);
        e.setProductionDate(LocalDate.of(2026, 3, 15));
        return e;
    }
}
