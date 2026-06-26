package thienloc.manage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.SplitEntry;
import thienloc.manage.entity.SplitEntryStatus;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.SplitEntryRepository;
import thienloc.manage.repository.SystemLogRepository;
import thienloc.manage.repository.VocConsumptionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    private static final int RETENTION_YEARS = 2;
    private static final int GRACE_PERIOD_DAYS = 30;
    private static final int INCOMPLETE_DAYS = 3;

    private final MasterDbRepository masterDbRepository;

    private final SplitEntryRepository splitEntryRepository;

    private final DailyProductionRepository dailyProductionRepository;

    private final VocConsumptionRepository vocConsumptionRepository;

    private final SystemLogRepository systemLogRepository;

    private final NotificationService notificationService;

    private final SystemLogService systemLogService;

    public void checkAndNotify() {
        LocalDate now = LocalDate.now();
        LocalDate warningCutoff = now.minusYears(RETENTION_YEARS).plusDays(GRACE_PERIOD_DAYS);
        String warningMonth = warningCutoff.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        long masterDbCount = masterDbRepository.countByDataMonthBefore(warningMonth);
        if (masterDbCount > 0) {
            notificationService.notifyAdminAndManager(
                    "Dữ liệu MasterDb sắp hết hạn",
                    masterDbCount + " bản ghi MasterDb trước tháng " + warningMonth
                            + " sẽ bị xóa sau 30 ngày theo chính sách lưu trữ " + RETENTION_YEARS + " năm.",
                    "WARNING");
        }

        long prodCount = dailyProductionRepository.countByProductionDateBefore(warningCutoff);
        if (prodCount > 0) {
            notificationService.notifyAdminAndManager(
                    "Dữ liệu sản xuất sắp hết hạn",
                    prodCount + " bản ghi DailyProduction trước ngày " + warningCutoff
                            + " sẽ bị xóa sau 30 ngày theo chính sách lưu trữ " + RETENTION_YEARS + " năm.",
                    "WARNING");
        }
    }

    public void deleteExpiredData() {
        LocalDate now = LocalDate.now();
        LocalDate cutoffDate = now.minusYears(RETENTION_YEARS);
        String cutoffMonth = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int masterDeleted = masterDbRepository.deleteByDataMonthBefore(cutoffMonth);
        if (masterDeleted > 0) {
            log.info("Data retention: deleted {} MasterDb records before {}", masterDeleted, cutoffMonth);
            systemLogService.logAction("DATA_RETENTION",
                    "Auto-deleted " + masterDeleted + " MasterDb records before " + cutoffMonth);
        }

        int prodDeleted = dailyProductionRepository.deleteByProductionDateBefore(cutoffDate);
        if (prodDeleted > 0) {
            log.info("Data retention: deleted {} DailyProduction records before {}", prodDeleted, cutoffDate);
            systemLogService.logAction("DATA_RETENTION",
                    "Auto-deleted " + prodDeleted + " DailyProduction records before " + cutoffDate);
        }

        int vocDeleted = vocConsumptionRepository.deleteByProductionDateBefore(cutoffDate);
        if (vocDeleted > 0) {
            log.info("Data retention: deleted {} VocConsumption records before {}", vocDeleted, cutoffDate);
            systemLogService.logAction("DATA_RETENTION",
                    "Auto-deleted " + vocDeleted + " VocConsumption records before " + cutoffDate);
        }
    }

    public void deleteIncompleteSplitEntries() {
        LocalDate cutoff = LocalDate.now().minusDays(INCOMPLETE_DAYS);
        List<SplitEntry> stale = splitEntryRepository.findByStatusAndProductionDateBefore(SplitEntryStatus.PARTIAL, cutoff);
        if (!stale.isEmpty()) {
            splitEntryRepository.deleteAll(stale);
            log.info("Auto-deleted {} incomplete SplitEntry records before {}", stale.size(), cutoff);
            systemLogService.logAction("SPLIT_ENTRY_CLEANUP",
                    "Auto-deleted " + stale.size() + " incomplete SplitEntry records before " + cutoff);
        }

        LocalDate retentionCutoff = LocalDate.now().minusYears(RETENTION_YEARS);
        for (SplitEntryStatus status : List.of(SplitEntryStatus.READY, SplitEntryStatus.SYNCED)) {
            List<SplitEntry> expired = splitEntryRepository.findByStatusAndProductionDateBefore(status, retentionCutoff);
            if (!expired.isEmpty()) {
                splitEntryRepository.deleteAll(expired);
                log.info("Data retention: deleted {} SplitEntry[{}] records before {}", expired.size(), status, retentionCutoff);
                systemLogService.logAction("DATA_RETENTION",
                        "Auto-deleted " + expired.size() + " SplitEntry[" + status + "] records before " + retentionCutoff);
            }
        }
    }

    public void deleteOldSystemLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(RETENTION_YEARS);
        int deleted = systemLogRepository.deleteByTimestampBefore(cutoff);
        if (deleted > 0) {
            log.info("Data retention: deleted {} SystemLog records before {}", deleted, cutoff);
        }
    }
}
