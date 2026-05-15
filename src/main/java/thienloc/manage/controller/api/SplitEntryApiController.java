package thienloc.manage.controller.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.dto.SplitEntryDto;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.service.ISplitEntryService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/split-entries")
public class SplitEntryApiController {

    private final ISplitEntryService splitEntryService;

    public SplitEntryApiController(ISplitEntryService splitEntryService) {
        this.splitEntryService = splitEntryService;
    }

    @GetMapping
    public ResponseEntity<List<SplitEntryDto>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String month) {
        if (month != null && !month.isBlank()) {
            return ResponseEntity.ok(splitEntryService.getEntriesForMonth(YearMonth.parse(month)));
        }
        LocalDate d = (date != null) ? date : LocalDate.now();
        return ResponseEntity.ok(splitEntryService.getEntriesForDate(d));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!splitEntryService.deleteIfPresent(id)) {
            throw new ResourceNotFoundException("SplitEntry not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDelete(@RequestBody BulkDeleteRequest body) {
        if (body.ids() != null && !body.ids().isEmpty()) {
            for (Long id : body.ids()) {
                splitEntryService.deleteIfPresent(id);
            }
        }
        return ResponseEntity.noContent().build();
    }

    public record BulkDeleteRequest(List<Long> ids) {}
}
