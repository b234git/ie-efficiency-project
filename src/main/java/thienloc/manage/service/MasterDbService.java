package thienloc.manage.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.util.List;
import java.util.Optional;

@Service
public class MasterDbService {

    private static final Logger log = LoggerFactory.getLogger(MasterDbService.class);
    private static final int PAGE_SIZE = 10;

    @Autowired
    private MasterDbRepository masterDbRepository;

    // ─── Migration: backfill dataMonth for existing records ──────────────────────

    @PostConstruct
    public void migrateDataMonth() {
        long nullCount = masterDbRepository.countByDataMonthIsNull();
        if (nullCount > 0) {
            int updated = masterDbRepository.updateNullDataMonth("2026-02");
            log.info("Migration: assigned dataMonth '2026-02' to {} existing records", updated);
        }
    }

    // ─── Read ──────────────────────────────────────────────────────────────────

    public Page<MasterDb> getAll(int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        return masterDbRepository.findAll(pageable);
    }

    public Page<MasterDb> search(String keyword, int page) {
        return search(keyword, null, page);
    }

    public Page<MasterDb> search(String keyword, String dataMonth, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasMonth = dataMonth != null && !dataMonth.trim().isEmpty();

        if (hasKeyword && hasMonth) {
            return masterDbRepository.searchByKeywordAndMonth(keyword, dataMonth, pageable);
        } else if (hasMonth) {
            return masterDbRepository.findByDataMonth(dataMonth, pageable);
        } else if (hasKeyword) {
            return masterDbRepository.findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
                    keyword, keyword, pageable);
        } else {
            return masterDbRepository.findAll(pageable);
        }
    }

    public Optional<MasterDb> findById(Long id) {
        return masterDbRepository.findById(id);
    }

    public List<String> getDistinctMonths() {
        return masterDbRepository.findDistinctDataMonths();
    }

    // ─── Create / Update ───────────────────────────────────────────────────────

    public MasterDb save(MasterDb entity) {
        return masterDbRepository.save(entity);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    public void deleteById(Long id) {
        masterDbRepository.deleteById(id);
    }
}
