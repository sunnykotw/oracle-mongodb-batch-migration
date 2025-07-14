package com.example.migration.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Spring Batch 配置類
 * 配置批次處理的核心組件
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Value("${batch.executor.core-pool-size:5}")
    private int corePoolSize;

    @Value("${batch.executor.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${batch.executor.queue-capacity:100}")
    private int queueCapacity;

    @Value("${batch.executor.thread-name-prefix:batch-task-}")
    private String threadNamePrefix;

    /**
     * 配置批次任務執行器
     */
    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 配置批次事務管理器
     */
    @Bean(name = "batchTransactionManager")
    public PlatformTransactionManager batchTransactionManager() {
        return new ResourcelessTransactionManager();
    }

    /**
     * 配置 Job Repository
     */
    @Bean
    public JobRepository jobRepository(
            @Qualifier("batchDataSource") DataSource dataSource,
            @Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager) throws Exception {
        
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);
        factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
        factory.setTablePrefix("BATCH_");
        factory.setMaxVarCharLength(1000);
        factory.setValidateTransactionState(false);
        factory.afterPropertiesSet();
        
        return factory.getObject();
    }

    /**
     * 配置 Job Launcher
     */
    @Bean
    public JobLauncher jobLauncher(
            JobRepository jobRepository,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor) throws Exception {
        
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(taskExecutor);
        launcher.afterPropertiesSet();
        
        return launcher;
    }

    /**
     * 配置 JdbcTemplate for batch operations
     */
    @Bean(name = "batchJdbcTemplate")
    public JdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
