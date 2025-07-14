package com.example.migration.batch.job;

import com.example.migration.batch.listener.JobExecutionListener;
import com.example.migration.batch.listener.StepExecutionListener;
import com.example.migration.batch.processor.DataTransformProcessor;
import com.example.migration.batch.reader.OracleClobReader;
import com.example.migration.batch.writer.MongoDocumentWriter;
import com.example.migration.batch.writer.OracleArchiveWriter;
import com.example.migration.model.document.MigrationDocument;
import com.example.migration.model.entity.OracleEntity;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;

/**
 * 遷移作業配置類
 * 配置 Oracle 到 MongoDB 的遷移作業
 */
@Configuration
public class MigrationJobConfig {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    @Qualifier("oracleTransactionManager")
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("batchTaskExecutor")
    private TaskExecutor taskExecutor;

    @Autowired
    private JobExecutionListener jobExecutionListener;

    @Autowired
    private StepExecutionListener stepExecutionListener;

    @Autowired
    private OracleClobReader oracleClobReader;

    @Autowired
    private DataTransformProcessor dataTransformProcessor;

    @Autowired
    private MongoDocumentWriter mongoDocumentWriter;

    @Autowired
    private OracleArchiveWriter oracleArchiveWriter;

    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    @Value("${batch.skip-limit:10}")
    private int skipLimit;

    @Value("${batch.retry-limit:3}")
    private int retryLimit;

    @Value("${batch.throttle-limit:10}")
    private int throttleLimit;

    @Value("${batch.archive.enabled:false}")
    private boolean archiveEnabled;

    /**
     * 主要遷移作業
     */
    @Bean(name = "migrationJob")
    public Job migrationJob() {
        return new JobBuilder("migrationJob", jobRepository)
                .listener(jobExecutionListener)
                .start(migrationStep())
                .build();
    }

    /**
     * 遷移步驟
     */
    @Bean
    public Step migrationStep() {
        return new StepBuilder("migrationStep", jobRepository)
                .<OracleEntity, MigrationDocument>chunk(chunkSize, transactionManager)
                .reader(itemReader())
                .processor(itemProcessor())
                .writer(itemWriter())
                .listener(stepExecutionListener)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(Exception.class)
                .retryLimit(retryLimit)
                .retry(Exception.class)
                .throttleLimit(throttleLimit)
                .taskExecutor(taskExecutor)
                .build();
    }

    /**
     * 資料讀取器
     */
    @Bean
    public ItemReader<OracleEntity> itemReader() {
        return oracleClobReader;
    }

    /**
     * 資料處理器
     */
    @Bean
    public ItemProcessor<OracleEntity, MigrationDocument> itemProcessor() {
        return dataTransformProcessor;
    }

    /**
     * 資料寫入器
     */
    @Bean
    public ItemWriter<MigrationDocument> itemWriter() {
        if (archiveEnabled) {
            // 使用複合寫入器，同時寫入 MongoDB 和 Oracle 封存表
            CompositeItemWriter<MigrationDocument> compositeWriter = new CompositeItemWriter<>();
            compositeWriter.setDelegates(Arrays.asList(mongoDocumentWriter, oracleArchiveWriter));
            return compositeWriter;
        } else {
            // 只寫入 MongoDB
            return mongoDocumentWriter;
        }
    }

    /**
     * 清理作業 - 清理已處理的資料
     */
    @Bean(name = "cleanupJob")
    public Job cleanupJob() {
        return new JobBuilder("cleanupJob", jobRepository)
                .listener(jobExecutionListener)
                .start(cleanupStep())
                .build();
    }

    /**
     * 清理步驟
     */
    @Bean
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 實現清理邏輯
                    // 例如：刪除已成功遷移的資料
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(stepExecutionListener)
                .build();
    }

    /**
     * 驗證作業 - 驗證遷移結果
     */
    @Bean(name = "validationJob")
    public Job validationJob() {
        return new JobBuilder("validationJob", jobRepository)
                .listener(jobExecutionListener)
                .start(validationStep())
                .build();
    }

    /**
     * 驗證步驟
     */
    @Bean
    public Step validationStep() {
        return new StepBuilder("validationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 實現驗證邏輯
                    // 例如：比較 Oracle 和 MongoDB 中的資料數量
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(stepExecutionListener)
                .build();
    }

    /**
     * 重試作業 - 處理失敗的資料
     */
    @Bean(name = "retryJob")
    public Job retryJob() {
        return new JobBuilder("retryJob", jobRepository)
                .listener(jobExecutionListener)
                .start(retryStep())
                .build();
    }

    /**
     * 重試步驟
     */
    @Bean
    public Step retryStep() {
        return new StepBuilder("retryStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 實現重試邏輯
                    // 例如：重新處理失敗的資料
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(stepExecutionListener)
                .build();
    }
}