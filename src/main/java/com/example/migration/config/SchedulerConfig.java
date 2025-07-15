package com.example.migration.config;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.CronScheduleBuilder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 排程配置類
 * 配置 Quartz 排程器和相關任務
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Value("${scheduler.thread-count:10}")
    private int threadCount;

    @Value("${scheduler.thread-priority:5}")
    private int threadPriority;

    @Value("${scheduler.misfire-threshold:60000}")
    private int misfireThreshold;

    @Autowired
    @Qualifier("batchDataSource")
    private DataSource dataSource;

    /**
     * 配置 Quartz 排程器工廠
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setQuartzProperties(quartzProperties());
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setOverwriteExistingJobs(true);
        factory.setStartupDelay(30); // 延遲30秒啟動
        
        return factory;
    }

    /**
     * Quartz 屬性配置
     */
    private Properties quartzProperties() {
        Properties props = new Properties();
        
        // 基本設定
        props.put("org.quartz.scheduler.instanceName", "MigrationScheduler");
        props.put("org.quartz.scheduler.instanceId", "AUTO");
        props.put("org.quartz.scheduler.skipUpdateCheck", "true");
        props.put("org.quartz.scheduler.batchTriggerAcquisitionMaxCount", "3");
        props.put("org.quartz.scheduler.batchTriggerAcquisitionFireAheadTimeWindow", "1000");
        
        // 線程池設定
        props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.put("org.quartz.threadPool.threadCount", String.valueOf(threadCount));
        props.put("org.quartz.threadPool.threadPriority", String.valueOf(threadPriority));
        props.put("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");
        
        // JobStore 設定
        props.put("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.oracle.OracleDelegate");
        props.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.put("org.quartz.jobStore.isClustered", "true");
        props.put("org.quartz.jobStore.clusterCheckinInterval", "20000");
        props.put("org.quartz.jobStore.misfireThreshold", String.valueOf(misfireThreshold));
        props.put("org.quartz.jobStore.selectWithLockSQL", "SELECT * FROM {0}LOCKS WHERE SCHED_NAME = {1} AND LOCK_NAME = ? FOR UPDATE");
        
        // 插件設定
        props.put("org.quartz.plugin.shutdownhook.class", "org.quartz.plugins.management.ShutdownHookPlugin");
        props.put("org.quartz.plugin.shutdownhook.cleanShutdown", "true");
        
        return props;
    }

    /**
     * 配置排程器
     */
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) {
        return schedulerFactoryBean.getScheduler();
    }

    /**
     * 批次作業 Quartz Job
     */
    public static class BatchJob implements org.quartz.Job {
        
        @Autowired
        private JobLauncher jobLauncher;
        
        @Autowired
        @Qualifier("migrationJob")
        private Job migrationJob;
        
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                JobParameters params = new JobParametersBuilder()
                        .addLong("time", System.currentTimeMillis())
                        .addString("jobName", context.getJobDetail().getKey().getName())
                        .addString("triggerName", context.getTrigger().getKey().getName())
                        .toJobParameters();
                
                jobLauncher.run(migrationJob, params);
                
            } catch (Exception e) {
                throw new JobExecutionException("批次作業執行失敗", e);
            }
        }
    }

    /**
     * 創建預設遷移作業的 JobDetail
     */
    @Bean
    public JobDetail migrationJobDetail() {
        return JobBuilder.newJob(BatchJob.class)
                .withIdentity("migrationJob", "migration")
                .withDescription("Oracle to MongoDB 遷移作業")
                .storeDurably()
                .build();
    }

    /**
     * 創建預設遷移作業的觸發器
     */
    @Bean
    public Trigger migrationJobTrigger() {
        // 預設每天凌晨 2 點執行
        return TriggerBuilder.newTrigger()
                .forJob(migrationJobDetail())
                .withIdentity("migrationTrigger", "migration")
                .withDescription("Oracle to MongoDB 遷移作業觸發器")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }

    /**
     * 手動觸發作業的簡單觸發器
     */
    @Bean
    public Trigger manualTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(migrationJobDetail())
                .withIdentity("manualTrigger", "migration")
                .withDescription("手動觸發遷移作業")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionFireNow())
                .build();
    }
}