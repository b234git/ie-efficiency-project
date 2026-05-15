package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.service.EffConfigService;

@RestController
@RequestMapping("/api/v1/eff-config")
public class EffConfigApiController {

    private final EffConfigService effConfigService;

    public EffConfigApiController(EffConfigService effConfigService) {
        this.effConfigService = effConfigService;
    }

    // ── Multipliers ───────────────────────────────────────────────────────────

    @PostMapping("/multipliers")
    public ResponseEntity<EffMultiplier> createMultiplier(@RequestBody EffMultiplier body) {
        body.setId(null);
        EffMultiplier saved = effConfigService.saveMultiplier(body);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/multipliers/{id}")
    public ResponseEntity<EffMultiplier> updateMultiplier(@PathVariable Long id,
                                                          @RequestBody EffMultiplier body) {
        body.setId(id);
        return ResponseEntity.ok(effConfigService.saveMultiplier(body));
    }

    @DeleteMapping("/multipliers/{id}")
    public ResponseEntity<Void> deleteMultiplier(@PathVariable Long id) {
        effConfigService.deleteMultiplierById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Incentive Rates ───────────────────────────────────────────────────────

    @PostMapping("/rates")
    public ResponseEntity<EffIncentiveRate> createRate(@RequestBody EffIncentiveRate body) {
        body.setId(null);
        EffIncentiveRate saved = effConfigService.saveRate(body);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/rates/{id}")
    public ResponseEntity<EffIncentiveRate> updateRate(@PathVariable Long id,
                                                       @RequestBody EffIncentiveRate body) {
        body.setId(id);
        return ResponseEntity.ok(effConfigService.saveRate(body));
    }

    @DeleteMapping("/rates/{id}")
    public ResponseEntity<Void> deleteRate(@PathVariable Long id) {
        effConfigService.deleteRateById(id);
        return ResponseEntity.noContent().build();
    }
}
