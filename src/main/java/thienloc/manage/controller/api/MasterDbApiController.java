package thienloc.manage.controller.api;

import org.springframework.data.domain.Page;
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
import thienloc.manage.dto.response.PageResponse;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.exception.ResourceNotFoundException;
import thienloc.manage.service.MasterDbService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/masterdb")
public class MasterDbApiController {

    private final MasterDbService masterDbService;

    public MasterDbApiController(MasterDbService masterDbService) {
        this.masterDbService = masterDbService;
    }

    @GetMapping
    public ResponseEntity<MasterDbListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dataMonth) {
        Page<MasterDb> result = masterDbService.search(keyword, dataMonth, page);
        return ResponseEntity.ok(new MasterDbListResponse(
                PageResponse.from(result),
                masterDbService.getDistinctMonths()));
    }

    @PostMapping
    public ResponseEntity<MasterDb> create(@RequestBody MasterDb body) {
        body.setId(null);
        return ResponseEntity.status(201).body(masterDbService.save(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasterDb> update(@PathVariable Long id, @RequestBody MasterDb body) {
        masterDbService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MasterDb not found: " + id));
        body.setId(id);
        return ResponseEntity.ok(masterDbService.save(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        masterDbService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MasterDb not found: " + id));
        masterDbService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record MasterDbListResponse(PageResponse<MasterDb> records, List<String> availableMonths) {}
}
