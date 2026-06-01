package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    @Autowired
    private IProductionService productionService;

    @Autowired
    private ISplitEntryService splitEntryService;

    @Autowired
    private SystemLogService systemLogService;

    public ForceDeleteResult forceDeleteByIds(String source, List<Long> ids) {
        boolean isSplit = "SPLIT".equalsIgnoreCase(source);
        List<Long> deleted = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        if (ids != null) {
            for (Long id : ids) {
                if (id == null) continue;
                try {
                    boolean ok = isSplit ? splitEntryService.deleteIfPresent(id)
                                         : productionService.deleteIfPresent(id);
                    if (ok) deleted.add(id); else missing.add(id);
                } catch (Exception ex) {
                    failed.add(id + ": " + ex.getMessage());
                }
            }
        }

        systemLogService.logAction("ADMIN_FORCE_DELETE",
                "source=" + (isSplit ? "SPLIT" : "ENTRY")
                        + " | requested=" + (ids == null ? 0 : ids.size())
                        + " | deleted=" + deleted
                        + " | missing=" + missing
                        + " | failed=" + failed.size());

        return new ForceDeleteResult(isSplit ? "SPLIT" : "ENTRY", deleted, missing, failed);
    }

    public record ForceDeleteResult(String source, List<Long> deleted, List<Long> missing, List<String> failed) {}
}
