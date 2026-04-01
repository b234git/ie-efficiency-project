package thienloc.manage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.SplitEntry;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.SplitEntryRepository;

import java.util.List;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Handles data retention policy:
 * - Notify ADMIN + MANAGER 30 days before data expires (2-year retention)
 * - Delete expired data (MasterDb by dataMonth, DailyProduction by productionDate)
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    private static final int RETENTION_YEARS = 2;
    private static final int GRACE_PERIOD_DAYS = 30;
    private static final int INCOMPLETE_DAYS = 3;

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private SplitEntryRepository splitEntryRepository;

    @Autowired
    private DailyProductionRepository dailyProductionRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * Check for data approaching expiration and notify admin/manager.
     */
    public void checkAndNotify() {
        LocalDate now = LocalDate.now();

        // Data older than (2 years - 30 days) is "approaching expiration"
        LocalDate warningCutoff = now.minusYears(RETENTION_YEARS).plusDays(GRACE_PERIOD_DAYS);
        String warningMonth = warningCutoff.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Check MasterDb
        long masterDbCount = masterDbRepository.countByDataMonthBefore(warningMonth);
        if (masterDbCount > 0) {
            String title = "Dữ liệu MasterDb sắp hết hạn";
            String message = masterDbCount + " bản ghi MasterDb có dữ liệu trước tháng " + warningMonth
                    + " sẽ bị xóa tự động sau 30 ngày theo chính sách lưu trữ " + RETENTION_YEARS + " năm.";
            notificationService.notifyAdminAndManager(title, message, "WARNING");
        }

        // Check DailyProduction
        LocalDate prodWarningDate = warningCutoff;
        long prodCount = dailyProductionRepository.countByProductionDateBefore(prodWarningDate);
        if (prodCount > 0) {
            String title = "Dữ liệu sản xuất sắp hết hạn";
            String message = prodCount + " bản ghi DailyProduction trước ngày " + prodWarningDate
                    + " sẽ bị xóa tự động sau 30 ngày theo chính sách lưu trữ " + RETENTION_YEARS + " năm.";
            notificationService.notifyAdminAndManager(title, message, "WARNING");
        }
    }

    /**
     * Delete data older than the retention period.
     */
    public void deleteExpiredData() {
        LocalDate now = LocalDate.now();
        LocalDate cutoffDate = now.minusYears(RETENTION_YEARS);
        String cutoffMonth = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Delete expired MasterDb records
        int masterDeleted = masterDbRepository.deleteByDataMonthBefore(cutoffMonth);
        if (masterDeleted > 0) {
            log.info("Data retention: deleted {} MasterDb records before {}", masterDeleted, cutoffMonth);
            systemLogService.logAction("DATA_RETENTION",
                    "Auto-deleted " + masterDeleted + " MasterDb records before " + cutoffMonth);
        }

        // Delete expired DailyProduction records
        int prodDeleted = dailyProductionRepository.deleteByProductionDateBefore(cutoffDate);
        if (prodDeleted > 0) {
            log.info("Data retention: deleted {} DailyProduction records before {}", prodDeleted, cutoffDate);
            systemLogService.logAction("DATA_RETENTION",
                    "Auto-deleted " + prodDeleted + " DailyProduction records before " + cutoffDate);
        }
    }

    /**
     * Delete incomplete SplitEntry records (status=PARTIAL) older than INCOMPLETE_DAYS days.
     * Also delete completed SplitEntry records (READY/SYNCED) older than RETENTION_YEARS years.
     */
    public void deleteIncompleteSplitEntries() {
        LocalDate cutoff = LocalDate.now().minusDays(INCOMPLETE_DAYS);
        List<SplitEntry> stale = splitEntryRepository.findByStatusAndProductionDateBefore("PARTIAL", cutoff);
        if (!stale.isEmpty()) {
            splitEntryRepository.deleteAll(stale);
            log.info("Auto-deleted {} incomplete SplitEntry records before {}", stale.size(), cutoff);
            systemLogService.logAction("SPLIT_ENTRY_CLEANUP",
                    "Auto-deleted " + stale.size() + " incomplete SplitEntry records before " + cutoff);
        }

        // Also enforce 2-year retention on completed entries (READY + SYNCED)
        LocalDate retentionCutoff = LocalDate.now().minusYears(RETENTION_YEARS);
        for (String status : List.of("READY", "SYNCED")) {
            List<SplitEntry> expired = splitEntryRepository.findByStatusAndProductionDateBefore(status, retentionCutoff);
            if (!expired.isEmpty()) {
                splitEntryRepository.deleteAll(expired);
                log.info("Data retention: deleted {} SplitEntry[{}] records before {}", expired.size(), status, retentionCutoff);
                systemLogService.logAction("DATA_RETENTION",
                        "Auto-deleted " + expired.size() + " SplitEntry[" + status + "] records before " + retentionCutoff);
            }
        }
    }
}
