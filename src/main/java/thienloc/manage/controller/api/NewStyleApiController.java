package thienloc.manage.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thienloc.manage.entity.NewStyleEntry;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.service.NewStyleService;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/new-styles")
public class NewStyleApiController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final NewStyleService service;

    public NewStyleApiController(NewStyleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<NewStyleEntry>> list(@RequestParam(required = false) String month) {
        String selected = (month != null && !month.isBlank()) ? month : YearMonth.now().format(MONTH_FMT);
        return ResponseEntity.ok(service.getByMonth(selected));
    }

    @PostMapping
    public ResponseEntity<NewStyleEntry> create(@RequestBody NewStyleEntry body) {
        body.setId(null);
        return ResponseEntity.status(201).body(service.save(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NewStyleEntry> update(@PathVariable Long id, @RequestBody NewStyleEntry body) {
        service.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewStyle not found: " + id));
        body.setId(id);
        return ResponseEntity.ok(service.save(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NewStyle not found: " + id));
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
