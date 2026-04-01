package thienloc.manage.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import thienloc.manage.service.DataRetentionService;

@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    @Autowired
    private DataRetentionService dataRetentionService;

    /**
     * Runs daily at 2:00 AM: check for expiring data and delete expired records.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyRetentionCheck() {
        log.info("Data retention check started");
        dataRetentionService.checkAndNotify();
        dataRetentionService.deleteExpiredData();
        dataRetentionService.deleteIncompleteSplitEntries();
        log.info("Data retention check completed");
    }
}
