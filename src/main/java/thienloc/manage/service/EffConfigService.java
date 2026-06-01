package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import thienloc.manage.config.CacheConfig;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.repository.EffIncentiveRateRepository;
import thienloc.manage.repository.EffMultiplierRepository;

import java.util.List;
import java.util.Optional;

@Service
public class EffConfigService {

    @Autowired
    private EffMultiplierRepository multiplierRepo;

    @Autowired
    private EffIncentiveRateRepository rateRepo;

    @Autowired
    private SystemLogService systemLogService;

    // ── Multiplier ────────────────────────────────────────────────────────────

    @Cacheable(CacheConfig.EFF_MULTIPLIERS)
    public List<EffMultiplier> getAllMultipliers() {
        return multiplierRepo.findAllByOrderBySecAsc();
    }

    public Optional<EffMultiplier> findMultiplierById(Long id) {
        return multiplierRepo.findById(id);
    }

    @CacheEvict(cacheNames = CacheConfig.EFF_MULTIPLIERS, allEntries = true)
    public EffMultiplier saveMultiplier(EffMultiplier entity) {
        boolean isNew = entity.getId() == null;
        EffMultiplier saved = multiplierRepo.save(entity);
        systemLogService.logAction(
                isNew ? "ADD_EFF_MULTIPLIER" : "EDIT_EFF_MULTIPLIER",
                "SEC: " + saved.getSec());
        return saved;
    }

    @CacheEvict(cacheNames = CacheConfig.EFF_MULTIPLIERS, allEntries = true)
    public void deleteMultiplierById(Long id) {
        multiplierRepo.deleteById(id);
        systemLogService.logAction("DELETE_EFF_MULTIPLIER", "Deleted Multiplier ID: " + id);
    }

    // ── Incentive Rate ────────────────────────────────────────────────────────

    @Cacheable(CacheConfig.EFF_RATES)
    public List<EffIncentiveRate> getAllRates() {
        return rateRepo.findAllByOrderBySecAscEffPercentAsc();
    }

    @Cacheable(CacheConfig.EFF_RATE_SECS)
    public List<String> getDistinctRateSecs() {
        return rateRepo.findDistinctSecs();
    }

    public Optional<EffIncentiveRate> findRateById(Long id) {
        return rateRepo.findById(id);
    }

    @CacheEvict(cacheNames = {CacheConfig.EFF_RATES, CacheConfig.EFF_RATE_SECS}, allEntries = true)
    public EffIncentiveRate saveRate(EffIncentiveRate entity) {
        boolean isNew = entity.getId() == null;
        EffIncentiveRate saved = rateRepo.save(entity);
        systemLogService.logAction(
                isNew ? "ADD_EFF_RATE" : "EDIT_EFF_RATE",
                "SEC: " + saved.getSec() + ", EFF%: " + saved.getEffPercent());
        return saved;
    }

    @CacheEvict(cacheNames = {CacheConfig.EFF_RATES, CacheConfig.EFF_RATE_SECS}, allEntries = true)
    public void deleteRateById(Long id) {
        rateRepo.deleteById(id);
        systemLogService.logAction("DELETE_EFF_RATE", "Deleted Rate ID: " + id);
    }
}
