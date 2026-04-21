package thienloc.manage.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import thienloc.manage.service.DataRetentionService;

import java.time.Instant;

@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    @Autowired
    private DataRetentionService dataRetentionService;

    private volatile Instant lastRun;

    public Instant getLastRun() { return lastRun; }

    public void dailyRetentionCheck() {
        log.info("Data retention check started");
        dataRetentionService.checkAndNotify();
        dataRetentionService.deleteExpiredData();
        dataRetentionService.deleteIncompleteSplitEntries();
        dataRetentionService.deleteOldSystemLogs();
        lastRun = Instant.now();
        log.info("Data retention check completed");
    }
}
