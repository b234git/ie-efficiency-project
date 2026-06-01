package thienloc.manage.controller.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import thienloc.manage.dto.response.PageResponse;
import thienloc.manage.service.IProductionService;
import thienloc.manage.util.RecordFilterUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/entries")
public class ProductionApiController {

    private final IProductionService productionService;

    public ProductionApiController(IProductionService productionService) {
        this.productionService = productionService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DailyProductionDto>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String article,
            @RequestParam(required = false, defaultValue = "false") boolean errorsOnly,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "") String section,
            @RequestParam(required = false, defaultValue = "") String line,
            @RequestParam(required = false, defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            Authentication auth) {

        LocalDate[] dr = resolveRange(range, date, month);
        int normalizedPageSize = normalizePageSize(pageSize);

        Page<DailyProductionDto> recordPage = productionService.getMyDataRangeWithSplitEntriesPaged(
                auth.getName(), dr[0], dr[1], section, line, page, normalizedPageSize);

        List<DailyProductionDto> filtered = new ArrayList<>(recordPage.getContent());
        filtered = RecordFilterUtil.filterByArticle(filtered, article);
        if (errorsOnly) filtered = RecordFilterUtil.filterErrorsOnly(filtered);
        filtered = RecordFilterUtil.filterBySource(filtered, source);

        Page<DailyProductionDto> rebuilt = new PageImpl<>(
                filtered,
                PageRequest.of(recordPage.getNumber(), recordPage.getSize()),
                recordPage.getTotalElements());
        return ResponseEntity.ok(PageResponse.from(rebuilt));
    }

    @PostMapping
    public ResponseEntity<DailyProductionDto> create(@RequestBody DailyProductionDto body, Authentication auth) {
        body.setId(null);
        Long id = productionService.saveDailyProduction(body, auth.getName());
        return ResponseEntity.status(201).body(productionService.getById(id));
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
        if (body.ids() != null) {
            for (Long id : body.ids()) {
                productionService.deleteIfPresent(id);
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin-deletable-ids")
    public ResponseEntity<List<Long>> adminDeletableIds(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TODAY") String range,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "") String section,
            @RequestParam(required = false, defaultValue = "") String line,
            Authentication auth) {
        LocalDate[] dr = resolveRange(range, date, month);
        return ResponseEntity.ok(productionService.getFilteredIds(auth.getName(), dr[0], dr[1], section, line));
    }

    private LocalDate[] resolveRange(String range, LocalDate date, String month) {
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.from(today);
        if ("MONTH".equals(range) && month != null && !month.isBlank()) {
            try {
                ym = YearMonth.parse(month);
            } catch (Exception ignored) { /* fall through to current month */ }
        }
        LocalDate effectiveDate = (date != null) ? date : today;
        return switch (range) {
            case "1M"    -> new LocalDate[]{ today.minusMonths(1), today };
            case "6M"    -> new LocalDate[]{ today.minusMonths(6), today };
            case "ALL"   -> new LocalDate[]{ today.minusMonths(12), today };
            case "MONTH" -> new LocalDate[]{ ym.atDay(1), ym.atEndOfMonth() };
            default      -> new LocalDate[]{ effectiveDate, effectiveDate };
        };
    }

    private int normalizePageSize(int pageSize) {
        int[] allowed = {25, 50, 100, 150, 1000};
        for (int v : allowed) if (v == pageSize) return v;
        return 25;
    }

    public record BulkDeleteRequest(List<Long> ids) {}
}
