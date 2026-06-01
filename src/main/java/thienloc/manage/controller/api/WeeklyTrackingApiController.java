package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.entity.ReprocessRecord;
import thienloc.manage.entity.SixSRecord;
import thienloc.manage.service.WeeklyTrackingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/weekly-tracking")
public class WeeklyTrackingApiController {

    private final WeeklyTrackingService service;

    public WeeklyTrackingApiController(WeeklyTrackingService service) {
        this.service = service;
    }

    // ── 6S Records ────────────────────────────────────────────────────────────

    @PostMapping("/sixs")
    public ResponseEntity<SixSRecord> createSixS(@RequestBody SixSRecord body) {
        body.setId(null);
        return ResponseEntity.status(201).body(service.saveSixS(body));
    }

    @PutMapping("/sixs/{id}")
    public ResponseEntity<SixSRecord> updateSixS(@PathVariable Long id, @RequestBody SixSRecord body) {
        body.setId(id);
        return ResponseEntity.ok(service.saveSixS(body));
    }

    @DeleteMapping("/sixs/{id}")
    public ResponseEntity<Void> deleteSixS(@PathVariable Long id) {
        service.deleteSixS(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sixs/bulk-delete")
    public ResponseEntity<Void> bulkDeleteSixS(@RequestBody BulkDeleteRequest body) {
        if (body.ids() != null && !body.ids().isEmpty()) {
            service.deleteSixSByIds(body.ids());
        }
        return ResponseEntity.noContent().build();
    }

    // ── Reprocess Records ─────────────────────────────────────────────────────

    @PostMapping("/reprocess")
    public ResponseEntity<ReprocessRecord> createReprocess(@RequestBody ReprocessRecord body) {
        body.setId(null);
        return ResponseEntity.status(201).body(service.saveReprocess(body));
    }

    @PutMapping("/reprocess/{id}")
    public ResponseEntity<ReprocessRecord> updateReprocess(@PathVariable Long id, @RequestBody ReprocessRecord body) {
        body.setId(id);
        return ResponseEntity.ok(service.saveReprocess(body));
    }

    @DeleteMapping("/reprocess/{id}")
    public ResponseEntity<Void> deleteReprocess(@PathVariable Long id) {
        service.deleteReprocess(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reprocess/bulk-delete")
    public ResponseEntity<Void> bulkDeleteReprocess(@RequestBody BulkDeleteRequest body) {
        if (body.ids() != null && !body.ids().isEmpty()) {
            service.deleteReprocessByIds(body.ids());
        }
        return ResponseEntity.noContent().build();
    }

    public record BulkDeleteRequest(List<Long> ids) {}
}
