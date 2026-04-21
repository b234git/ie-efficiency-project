package thienloc.manage.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class DataRetentionJob implements Job {

    @Autowired
    private DataRetentionScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        scheduler.dailyRetentionCheck();
    }
}
