package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDbServiceTest {

    @Mock
    private MasterDbRepository masterDbRepository;

    @InjectMocks
    private MasterDbService masterDbService;

    @Test
    void testMigrateDataMonth_HasNullRecords() {
        when(masterDbRepository.countByDataMonthIsNull()).thenReturn(10L);
        when(masterDbRepository.updateNullDataMonth("2026-02")).thenReturn(10);

        masterDbService.migrateDataMonth();

        verify(masterDbRepository).updateNullDataMonth("2026-02");
    }

    @Test
    void testMigrateDataMonth_NoNullRecords() {
        when(masterDbRepository.countByDataMonthIsNull()).thenReturn(0L);

        masterDbService.migrateDataMonth();

        verify(masterDbRepository, never()).updateNullDataMonth(anyString());
    }

    @Test
    void testGetAll() {
        Page<MasterDb> page = new PageImpl<>(List.of(MasterDb.builder().ref("R1").build()));
        when(masterDbRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<MasterDb> result = masterDbService.getAll(0);

        assertEquals(1, result.getTotalElements());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(masterDbRepository).findAll(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void testSearch_KeywordOnly() {
        Page<MasterDb> page = new PageImpl<>(List.of());
        when(masterDbRepository.findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
                eq("Y123"), eq("Y123"), any(Pageable.class))).thenReturn(page);

        masterDbService.search("Y123", null, 0);

        verify(masterDbRepository).findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
                eq("Y123"), eq("Y123"), any(Pageable.class));
    }

    @Test
    void testSearch_MonthOnly() {
        Page<MasterDb> page = new PageImpl<>(List.of());
        when(masterDbRepository.findByDataMonth(eq("2026-02"), any(Pageable.class))).thenReturn(page);

        masterDbService.search(null, "2026-02", 0);

        verify(masterDbRepository).findByDataMonth(eq("2026-02"), any(Pageable.class));
    }

    @Test
    void testSearch_KeywordAndMonth() {
        Page<MasterDb> page = new PageImpl<>(List.of());
        when(masterDbRepository.searchByKeywordAndMonth(eq("Y123"), eq("2026-02"), any(Pageable.class)))
                .thenReturn(page);

        masterDbService.search("Y123", "2026-02", 0);

        verify(masterDbRepository).searchByKeywordAndMonth(eq("Y123"), eq("2026-02"), any(Pageable.class));
    }

    @Test
    void testSearch_NeitherKeywordNorMonth() {
        Page<MasterDb> page = new PageImpl<>(List.of());
        when(masterDbRepository.findAll(any(Pageable.class))).thenReturn(page);

        masterDbService.search(null, null, 0);

        verify(masterDbRepository).findAll(any(Pageable.class));
    }

    @Test
    void testSearch_BlankKeyword() {
        Page<MasterDb> page = new PageImpl<>(List.of());
        when(masterDbRepository.findAll(any(Pageable.class))).thenReturn(page);

        masterDbService.search("   ", null, 0);

        verify(masterDbRepository).findAll(any(Pageable.class));
    }

    @Test
    void testFindById() {
        MasterDb m = MasterDb.builder().id(1L).ref("R1").build();
        when(masterDbRepository.findById(1L)).thenReturn(Optional.of(m));

        Optional<MasterDb> result = masterDbService.findById(1L);

        assertTrue(result.isPresent());
    }

    @Test
    void testSave() {
        MasterDb m = MasterDb.builder().ref("R1").build();
        when(masterDbRepository.save(m)).thenReturn(m);

        MasterDb result = masterDbService.save(m);

        assertNotNull(result);
        verify(masterDbRepository).save(m);
    }

    @Test
    void testDeleteById() {
        masterDbService.deleteById(1L);

        verify(masterDbRepository).deleteById(1L);
    }

    @Test
    void testGetDistinctMonths() {
        when(masterDbRepository.findDistinctDataMonths()).thenReturn(List.of("2026-03", "2026-02"));

        List<String> result = masterDbService.getDistinctMonths();

        assertEquals(2, result.size());
    }
}
