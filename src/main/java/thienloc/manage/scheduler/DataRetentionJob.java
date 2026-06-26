package thienloc.manage.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class DataRetentionJob implements Job {

    // Field injection (not constructor): Quartz instantiates Job classes with a no-arg
    // constructor and Spring autowires the fields afterward, so a constructor param can't be used.
    @Autowired
    private DataRetentionScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        scheduler.dailyRetentionCheck();
    }
}
