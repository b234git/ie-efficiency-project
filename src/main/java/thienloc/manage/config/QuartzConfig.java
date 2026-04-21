package thienloc.manage.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import thienloc.manage.scheduler.DataRetentionJob;

@Configuration
public class QuartzConfig {

    @Bean
    JobDetail dataRetentionJobDetail() {
        return JobBuilder.newJob(DataRetentionJob.class)
                .withIdentity("dataRetentionJob")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger dataRetentionTrigger(JobDetail dataRetentionJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(dataRetentionJobDetail)
                .withIdentity("dataRetentionTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }
}
