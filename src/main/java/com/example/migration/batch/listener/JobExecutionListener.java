package com.example.migration.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * 作業執行監聽器
 * 監控批次作業的執行狀態和統計資訊
 */
@Component
public class JobExecutionListener implements org.springframework.batch.core.JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(JobExecutionListener.class);
	
    @Value("${batch.notification.enabled:false}")
    private boolean notificationEnabled;

    @Value("${batch.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 作業開始前執行
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long jobId = jobExecution.getJobId();
        LocalDateTime startTime = LocalDateTime.now();
        
        log.info("========================================");
        log.info("作業開始執行");
        log.info("作業名稱: {}", jobName);
        log.info("作業ID: {}", jobId);
        log.info("開始時間: {}", startTime.format(FORMATTER));
        log.info("作業參數: {}", jobExecution.getJobParameters());
        log.info("========================================");

        // 設置作業開始時間到執行上下文
        jobExecution.getExecutionContext().putString("startTime", startTime.format(FORMATTER));
        
        // 初始化統計資訊
        initializeStatistics(jobExecution);
        
        if (monitoringEnabled) {
            recordJobStart(jobExecution);
        }
    }

    /**
     * 作業完成後執行
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long jobId = jobExecution.getJobId();
        BatchStatus status = jobExecution.getStatus();
        LocalDateTime endTime = LocalDateTime.now();
        
        // 計算執行時間
        Duration duration = Duration.between(
            jobExecution.getStartTime().toInstant(null),
            jobExecution.getEndTime().toInstant(null)
        );
        
        log.info("========================================");
        log.info("作業執行完成");
        log.info("作業名稱: {}", jobName);
        log.info("作業ID: {}", jobId);
        log.info("完成時間: {}", endTime.format(FORMATTER));
        log.info("執行狀態: {}", status);
        log.info("執行時間: {} 秒", duration.getSeconds());
        
        // 記錄統計資訊
        logJobStatistics(jobExecution);
        
        // 處理作業結果
        handleJobResult(jobExecution);
        
        log.info("========================================");
        
        if (monitoringEnabled) {
            recordJobEnd(jobExecution);
        }
        
        if (notificationEnabled) {
            sendNotification(jobExecution);
        }
    }

    /**
     * 初始化統計資訊
     */
    private void initializeStatistics(JobExecution jobExecution) {
        jobExecution.getExecutionContext().putLong("totalProcessedCount", 0L);
        jobExecution.getExecutionContext().putLong("totalSkipCount", 0L);
        jobExecution.getExecutionContext().putLong("totalErrorCount", 0L);
    }

    /**
     * 記錄作業統計資訊
     */
    private void logJobStatistics(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        
        long totalReadCount = 0;
        long totalWriteCount = 0;
        long totalSkipCount = 0;
        long totalProcessCount = 0;
        
        for (StepExecution stepExecution : stepExecutions) {
            totalReadCount += stepExecution.getReadCount();
            totalWriteCount += stepExecution.getWriteCount();
            totalSkipCount += stepExecution.getSkipCount();
            totalProcessCount += stepExecution.getProcessSkipCount();
            
            log.info("步驟 [{}] 統計:", stepExecution.getStepName());
            log.info("  讀取筆數: {}", stepExecution.getReadCount());
            log.info("  寫入筆數: {}", stepExecution.getWriteCount());
            log.info("  跳過筆數: {}", stepExecution.getSkipCount());
            log.info("  處理跳過筆數: {}", stepExecution.getProcessSkipCount());
            log.info("  回滾筆數: {}", stepExecution.getRollbackCount());
            log.info("  提交筆數: {}", stepExecution.getCommitCount());
            
            // 記錄錯誤資訊
            if (!stepExecution.getFailureExceptions().isEmpty()) {
                log.error("步驟 [{}] 執行錯誤:", stepExecution.getStepName());
                stepExecution.getFailureExceptions().forEach(exception -> 
                    log.error("  錯誤: {}", exception.getMessage(), exception)
                );
            }
        }
        
        log.info("作業總計統計:");
        log.info("  總讀取筆數: {}", totalReadCount);
        log.info("  總寫入筆數: {}", totalWriteCount);
        log.info("  總跳過筆數: {}", totalSkipCount);
        log.info("  總處理跳過筆數: {}", totalProcessCount);
        
        // 更新執行上下文
        jobExecution.getExecutionContext().putLong("totalReadCount", totalReadCount);
        jobExecution.getExecutionContext().putLong("totalWriteCount", totalWriteCount);
        jobExecution.getExecutionContext().putLong("totalSkipCount", totalSkipCount);
    }

    /**
     * 處理作業結果
     */
    private void handleJobResult(JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();
        
        switch (status) {
            case COMPLETED:
                log.info("作業成功完成");
                handleSuccessfulJob(jobExecution);
                break;
            case FAILED:
                log.error("作業執行失敗");
                handleFailedJob(jobExecution);
                break;
            case STOPPED:
                log.warn("作業被停止");
                handleStoppedJob(jobExecution);
                break;
            case ABANDONED:
                log.warn("作業被放棄");
                handleAbandonedJob(jobExecution);
                break;
            default:
                log.info("作業狀態: {}", status);
        }
    }

    /**
     * 處理成功的作業
     */
    private void handleSuccessfulJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        
        // 根據不同作業類型執行相應的後續處理
        switch (jobName) {
            case "migrationJob":
                log.info("遷移作業成功完成，可以執行驗證作業");
                break;
            case "cleanupJob":
                log.info("清理作業成功完成");
                break;
            case "validationJob":
                log.info("驗證作業成功完成");
                break;
            case "retryJob":
                log.info("重試作業成功完成");
                break;
        }
    }

    /**
     * 處理失敗的作業
     */
    private void handleFailedJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        
        log.error("作業 [{}] 執行失敗", jobName);
        
        // 記錄失敗原因
        jobExecution.getAllFailureExceptions().forEach(exception -> 
            log.error("失敗原因: {}", exception.getMessage(), exception)
        );
        
        // 根據作業類型決定後續處理
        switch (jobName) {
            case "migrationJob":
                log.error("遷移作業失敗，建議執行重試作業");
                break;
            case "cleanupJob":
                log.error("清理作業失敗，需要手動檢查");
                break;
            case "validationJob":
                log.error("驗證作業失敗，資料可能有問題");
                break;
            case "retryJob":
                log.error("重試作業失敗，需要人工介入");
                break;
        }
    }

    /**
     * 處理被停止的作業
     */
    private void handleStoppedJob(JobExecution jobExecution) {
        log.warn("作業被手動停止，可以稍後重新啟動");
    }

    /**
     * 處理被放棄的作業
     */
    private void handleAbandonedJob(JobExecution jobExecution) {
        log.warn("作業被放棄，需要檢查原因");
    }

    /**
     * 記錄作業開始
     */
    private void recordJobStart(JobExecution jobExecution) {
        // 這裡可以實現監控系統的記錄邏輯
        // 例如：發送監控指標、記錄到數據庫等
        log.debug("記錄作業開始監控資訊");
    }

    /**
     * 記錄作業結束
     */
    private void recordJobEnd(JobExecution jobExecution) {
        // 這裡可以實現監控系統的記錄邏輯
        // 例如：更新監控指標、記錄到數據庫等
        log.debug("記錄作業結束監控資訊");
    }

    /**
     * 發送通知
     */
    private void sendNotification(JobExecution jobExecution) {
        // 這裡可以實現通知邏輯
        // 例如：發送郵件、Slack通知、企業微信等
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        
        log.info("發送作業通知: 作業[{}] 狀態[{}]", jobName, status);
        
        // 實際的通知實現可以依據需求添加
        // notificationService.sendJobNotification(jobExecution);
    }
}