package thienloc.manage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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

        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc(anyString(), anyString()))
                .thenReturn(Optional.of(sampleMasterDb));

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

        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc(anyString(), anyString()))
                .thenReturn(Optional.of(sampleMasterDb));

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

        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc(anyString(), anyString()))
                .thenReturn(Optional.of(sampleMasterDb));

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

        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc(anyString(), anyString()))
                .thenReturn(Optional.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        assertNull(dto.getEffKpi());
    }

    @Test
    void testMasterDbNotFound() {
        DailyProductionDto dto = new DailyProductionDto();
        dto.setArticle("UNKNOWN");
        DailyProduction entity = buildEntity("SEW", "UNKNOWN", 1000, 25.0, 8.0, 30.0, 0.85);
        entity.setDli(25.0);

        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(masterDbRepository.findFirstByArticleNoOrderByRefAsc(anyString()))
                .thenReturn(Optional.empty());

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

        // Month-specific lookup returns empty
        when(masterDbRepository.findFirstByArticleNoAndDataMonthOrderByRefAsc("Y12345", "2026-03"))
                .thenReturn(Optional.empty());
        // Fallback returns result
        when(masterDbRepository.findFirstByArticleNoOrderByRefAsc("Y12345"))
                .thenReturn(Optional.of(sampleMasterDb));

        calculator.populateEfficiencyMetrics(dto, entity);

        // Should have found MasterDb via fallback
        assertEquals("P001", dto.getPatternNo());
        assertEquals("TestShoe", dto.getShoeName());
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

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
