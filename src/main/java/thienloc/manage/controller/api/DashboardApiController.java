package thienloc.manage.controller.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.service.IProductionService;
import thienloc.manage.util.RecordFilterUtil;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardApiController {

    private final IProductionService productionService;

    public DashboardApiController(IProductionService productionService) {
        this.productionService = productionService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article) {

        LocalDate today = LocalDate.now();
        List<DailyProductionDto> records;
        String rangeLabel;

        switch (range) {
            case "1M":
                records = productionService.getDashboardDataRange(today.minusMonths(1), today);
                rangeLabel = "Last 1 month (" + today.minusMonths(1) + " → " + today + ")";
                break;
            case "6M":
                records = productionService.getDashboardDataRange(today.minusMonths(6), today);
                rangeLabel = "Last 6 months (" + today.minusMonths(6) + " → " + today + ")";
                break;
            case "ALL":
                records = productionService.getDashboardDataRange(today.minusMonths(12), today);
                rangeLabel = "Last 12 months (" + today.minusMonths(12) + " → " + today + ")";
                break;
            default:
                LocalDate d = (date != null) ? date : today;
                records = productionService.getDashboardData(d);
                rangeLabel = "";
                break;
        }

        records = RecordFilterUtil.filterByArticle(records, article);

        return ResponseEntity.ok(new DashboardResponse(
                records, rangeLabel,
                date != null ? date : today,
                range, article));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DailyProductionDto> update(@PathVariable Long id,
                                                     @RequestBody DailyProductionDto body,
                                                     Authentication auth) {
        body.setId(id);
        productionService.saveDailyProduction(body, auth.getName());
        return ResponseEntity.ok(productionService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productionService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDelete(@RequestBody BulkDeleteRequest body) {
        if (body.ids() != null && !body.ids().isEmpty()) {
            productionService.deleteMultipleRecords(body.ids());
        }
        return ResponseEntity.noContent().build();
    }

    public record DashboardResponse(
            List<DailyProductionDto> records,
            String rangeLabel,
            LocalDate selectedDate,
            String selectedRange,
            String article) {}

    public record BulkDeleteRequest(List<Long> ids) {}
}
