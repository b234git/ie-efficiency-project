package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import thienloc.manage.entity.MasterDb;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SectionMetricsTest {

    @Test
    void testLookupSew() {
        Optional<SectionMetrics> opt = SectionMetrics.fromSection("SEW");
        assertTrue(opt.isPresent());
        assertEquals(SectionMetrics.SEW, opt.get());
    }

    @Test
    void testLookupCaseInsensitive() {
        assertTrue(SectionMetrics.fromSection("sew").isPresent());
        assertTrue(SectionMetrics.fromSection("Sew").isPresent());
    }

    @Test
    void testLookupBuffing1st() {
        Optional<SectionMetrics> opt = SectionMetrics.fromSection("BUFFING 1ST");
        assertTrue(opt.isPresent());
        assertEquals(SectionMetrics.BUFF_1ST, opt.get());
    }

    @Test
    void testLookupAssemblyAliases() {
        // "ASSY" -> ASSEMBLY_BIG
        Optional<SectionMetrics> assy = SectionMetrics.fromSection("ASSY");
        assertTrue(assy.isPresent());
        assertEquals(SectionMetrics.ASSEMBLY_BIG, assy.get());

        // "ASSEMBLY" -> ASSEMBLY_BIG
        Optional<SectionMetrics> assembly = SectionMetrics.fromSection("ASSEMBLY");
        assertTrue(assembly.isPresent());
        assertEquals(SectionMetrics.ASSEMBLY_BIG, assembly.get());
    }

    @Test
    void testUnknownSectionReturnsEmpty() {
        Optional<SectionMetrics> opt = SectionMetrics.fromSection("UNKNOWN");
        assertFalse(opt.isPresent());
    }

    @Test
    void testNullSectionReturnsEmpty() {
        assertFalse(SectionMetrics.fromSection(null).isPresent());
    }

    @Test
    void testGetCtFromMasterDb() {
        MasterDb m = MasterDb.builder().sewCt(50.0).build();
        SectionMetrics sew = SectionMetrics.SEW;
        assertEquals(50.0, sew.getCt(m));
    }

    @Test
    void testGetMpFromMasterDb() {
        MasterDb m = MasterDb.builder().sewMp(30.0).build();
        assertEquals(30.0, SectionMetrics.SEW.getMp(m));
    }

    @Test
    void testGetQuotaFromMasterDb() {
        MasterDb m = MasterDb.builder().sewQuotaDb(450.0).build();
        assertEquals(450.0, SectionMetrics.SEW.getQuota(m));
    }

    @Test
    void testGetPphWithValue() {
        MasterDb m = MasterDb.builder().sewPph(72.0).build();
        assertEquals(72.0, SectionMetrics.SEW.getPph(m));
    }

    @Test
    void testPphFallbackToCtWhenPphNull() {
        // PPH is null, CT = 180 → fallback PPH = 3600/180 = 20.0
        MasterDb m = MasterDb.builder().sewPph(null).sewCt(180.0).build();
        assertEquals(20.0, SectionMetrics.SEW.getPph(m));
    }

    @Test
    void testPphFallbackToCtWhenPphZero() {
        MasterDb m = MasterDb.builder().sewPph(0.0).sewCt(180.0).build();
        assertEquals(20.0, SectionMetrics.SEW.getPph(m));
    }

    @Test
    void testPphReturnsNullWhenBothNull() {
        MasterDb m = MasterDb.builder().sewPph(null).sewCt(null).build();
        assertNull(SectionMetrics.SEW.getPph(m));
    }

    @Test
    void testAssemblyBigFallbackToSmall() {
        // Assembly BIG CT is null, Small CT has value → should fallback
        MasterDb m = MasterDb.builder()
                .assemBigCt(null).assemBigMp(null).assemBigQuotaDb(null).assemBigPph(null)
                .assemSmallCt(100.0).assemSmallMp(20.0).assemSmallQuotaDb(300.0).assemSmallPph(36.0)
                .build();

        SectionMetrics big = SectionMetrics.ASSEMBLY_BIG;
        assertEquals(100.0, big.getCt(m));
        assertEquals(20.0, big.getMp(m));
        assertEquals(300.0, big.getQuota(m));
        assertEquals(36.0, big.getPph(m));
    }

    // ─── ArticleKey parsing ─────────────────────────────────────────────────

    @Test
    void testArticleKeyStripsDash2() {
        SectionMetrics.ArticleKey k = SectionMetrics.ArticleKey.parse("406203-2");
        assertEquals("406203", k.cleanedArticle());
        assertTrue(k.isSecondSubsection());
    }

    @Test
    void testArticleKeyPreservesDash02() {
        // "-02" is NOT the subsection marker; keep intact.
        SectionMetrics.ArticleKey k = SectionMetrics.ArticleKey.parse("406203-02");
        assertEquals("406203-02", k.cleanedArticle());
        assertFalse(k.isSecondSubsection());
    }

    @Test
    void testArticleKeyNoSuffix() {
        SectionMetrics.ArticleKey k = SectionMetrics.ArticleKey.parse("406203");
        assertEquals("406203", k.cleanedArticle());
        assertFalse(k.isSecondSubsection());
    }

    @Test
    void testArticleKeyNullAndBlank() {
        assertNull(SectionMetrics.ArticleKey.parse(null).cleanedArticle());
        assertNull(SectionMetrics.ArticleKey.parse("  ").cleanedArticle());
    }

    // ─── Per-slot resolveSlot rules ──────────────────────────────────────────

    @Test
    void testResolveSlotBuffSuffixWinsOverRowSubsec1() {
        // row BUFF 1, article "-2" → primary = BUFF_2ND, no fallback.
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("BUFF 1", "406203-2");
        assertEquals(SectionMetrics.BUFF_2ND, r.primary());
        assertNull(r.fallback());
    }

    @Test
    void testResolveSlotBuffSubsec2NoSuffixFallsBack() {
        // row BUFF 2, article without -2 → primary = BUFF_2ND, fallback = BUFF_1ST.
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("BUFF 2", "406203");
        assertEquals(SectionMetrics.BUFF_2ND, r.primary());
        assertEquals(SectionMetrics.BUFF_1ST, r.fallback());
    }

    @Test
    void testResolveSlotBuff1stNoSuffix() {
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("BUFF 1", "406203");
        assertEquals(SectionMetrics.BUFF_1ST, r.primary());
        assertNull(r.fallback());
    }

    @Test
    void testResolveSlotStockfitSuffixWins() {
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("SF 1", "406203-2");
        assertEquals(SectionMetrics.STOCKFIT_2ND, r.primary());
        assertNull(r.fallback());
    }

    @Test
    void testResolveSlotStockfitSubsec2NoSuffixFallsBack() {
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("SF 2", "406203");
        assertEquals(SectionMetrics.STOCKFIT_2ND, r.primary());
        assertEquals(SectionMetrics.STOCKFIT_1ST, r.fallback());
    }

    @Test
    void testResolveSlotStockfitUvIgnoresSuffix() {
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("SF UV", "406203-2");
        assertEquals(SectionMetrics.STOCKFIT_UV, r.primary());
        assertNull(r.fallback());
    }

    @Test
    void testResolveSlotSewIgnoresSuffix() {
        // Non-BUFF/SF sections pass through, suffix has no effect.
        SectionMetrics.ResolvedSection r = SectionMetrics.resolveSlot("SEW", "406203-2");
        assertEquals(SectionMetrics.SEW, r.primary());
        assertNull(r.fallback());
    }

    // ─── fallback getters ───────────────────────────────────────────────────

    @Test
    void testGetCtOrFallbackPrefersPrimary() {
        MasterDb m = MasterDb.builder().buff2ndCt(50.0).buff1stCt(99.0).build();
        assertEquals(50.0, SectionMetrics.BUFF_2ND.getCtOrFallback(m, SectionMetrics.BUFF_1ST));
    }

    @Test
    void testGetCtOrFallbackUsesFallbackWhenPrimaryNull() {
        MasterDb m = MasterDb.builder().buff2ndCt(null).buff1stCt(99.0).build();
        assertEquals(99.0, SectionMetrics.BUFF_2ND.getCtOrFallback(m, SectionMetrics.BUFF_1ST));
    }

    @Test
    void testGetPphOrFallbackUsesFallback() {
        MasterDb m = MasterDb.builder().buff2ndPph(null).buff2ndCt(null)
                .buff1stPph(36.0).build();
        assertEquals(36.0, SectionMetrics.BUFF_2ND.getPphOrFallback(m, SectionMetrics.BUFF_1ST));
    }

    @Test
    void testAllSectionsExist() {
        String[] sections = {"SEW", "BUFFING 1ST", "BUFFING 2ND",
                "STOCKFIT UV", "STOCKFIT 1ST", "STOCKFIT 2ND",
                "ASSEMBLY BIG", "ASSEMBLY SMALL"};
        for (String s : sections) {
            assertTrue(SectionMetrics.fromSection(s).isPresent(),
                    "Section '" + s + "' should be found");
        }
    }
}
