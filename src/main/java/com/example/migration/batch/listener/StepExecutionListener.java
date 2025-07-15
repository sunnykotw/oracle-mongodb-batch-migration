package com.example.migration.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 步驟執行監聽器
 * 監控批次步驟的執行狀態和統計資訊
 */
@Component
public class StepExecutionListener implements org.springframework.batch.core.StepExecutionListener {
	
	private static final Logger log = LoggerFactory.getLogger(StepExecutionListener.class);
	
    @Value("${batch.step.monitoring.enabled:true}")
    private boolean stepMonitoringEnabled;

    @Value("${batch.step.performance.threshold:1000}")
    private long performanceThreshold;

    @Value("${batch.step.error.threshold:100}")
    private long errorThreshold;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong STEP_COUNTER = new AtomicLong(0);

    /**
     * 步驟開始前執行
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        String stepName = stepExecution.getStepName();
        Long stepId = stepExecution.getId();
        LocalDateTime startTime = LocalDateTime.now();
        
        log.info("----------------------------------------");
        log.info("步驟開始執行");
        log.info("步驟名稱: {}", stepName);
        log.info("步驟ID: {}", stepId);
        log.info("開始時間: {}", startTime.format(FORMATTER));
        log.info("作業參數: {}", stepExecution.getJobExecution().getJobParameters());
        log.info("----------------------------------------");

        // 設置步驟開始時間到執行上下文
        stepExecution.getExecutionContext().putString("stepStartTime", startTime.format(FORMATTER));
        stepExecution.getExecutionContext().putLong("stepSequence", STEP_COUNTER.incrementAndGet());
        
        // 初始化步驟統計資訊
        initializeStepStatistics(stepExecution);
        
        if (stepMonitoringEnabled) {
            recordStepStart(stepExecution);
        }
    }

    /**
     * 步驟完成後執行
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String stepName = stepExecution.getStepName();
        Long stepId = stepExecution.getId();
        ExitStatus exitStatus = stepExecution.getExitStatus();
        LocalDateTime endTime = LocalDateTime.now();
        
        // 計算執行時間
        Duration duration = Duration.between(
            stepExecution.getStartTime().toInstant(null),
            stepExecution.getEndTime().toInstant(null)
        );
        
        log.info("----------------------------------------");
        log.info("步驟執行完成");
        log.info("步驟名稱: {}", stepName);
        log.info("步驟ID: {}", stepId);
        log.info("完成時間: {}", endTime.format(FORMATTER));
        log.info("執行狀態: {}", exitStatus.getExitCode());
        log.info("執行時間: {} 秒", duration.getSeconds());
        
        // 記錄詳細統計資訊
        logStepStatistics(stepExecution, duration);
        
        // 性能分析
        analyzePerformance(stepExecution, duration);
        
        // 錯誤分析
        analyzeErrors(stepExecution);
        
        // 處理步驟結果
        ExitStatus finalExitStatus = handleStepResult(stepExecution, exitStatus);
        
        log.info("----------------------------------------");
        
        if (stepMonitoringEnabled) {
            recordStepEnd(stepExecution);
        }
        
        return finalExitStatus;
    }

    /**
     * 初始化步驟統計資訊
     */
    private void initializeStepStatistics(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("customReadCount", 0L);
        stepExecution.getExecutionContext().putLong("customWriteCount", 0L);
        stepExecution.getExecutionContext().putLong("customSkipCount", 0L);
        stepExecution.getExecutionContext().putLong("customErrorCount", 0L);
        stepExecution.getExecutionContext().putString("stepStatus", "RUNNING");
    }

    /**
     * 記錄步驟統計資訊
     */
    private void logStepStatistics(StepExecution stepExecution, Duration duration) {
        log.info("步驟統計資訊:");
        log.info("  讀取筆數: {}", stepExecution.getReadCount());
        log.info("  寫入筆數: {}", stepExecution.getWriteCount());
        log.info("  跳過筆數: {}", stepExecution.getSkipCount());
        log.info("  處理跳過筆數: {}", stepExecution.getProcessSkipCount());
        log.info("  過濾筆數: {}", stepExecution.getFilterCount());
        log.info("  回滾筆數: {}", stepExecution.getRollbackCount());
        log.info("  提交筆數: {}", stepExecution.getCommitCount());
        log.info("  讀取跳過筆數: {}", stepExecution.getReadSkipCount());
        log.info("  寫入跳過筆數: {}", stepExecution.getWriteSkipCount());
        
        // 計算處理效率
        if (duration.getSeconds() > 0) {
            long itemsPerSecond = stepExecution.getWriteCount() / duration.getSeconds();
            log.info("  處理效率: {} 筆/秒", itemsPerSecond);
        }
        
        // 記錄錯誤資訊
        if (!stepExecution.getFailureExceptions().isEmpty()) {
            log.error("步驟執行錯誤:");
            stepExecution.getFailureExceptions().forEach(exception -> 
                log.error("  錯誤: {}", exception.getMessage(), exception)
            );
        }
    }

    /**
     * 性能分析
     */
    private void analyzePerformance(StepExecution stepExecution, Duration duration) {
        long processedItems = stepExecution.getWriteCount();
        long durationSeconds = duration.getSeconds();
        
        if (durationSeconds > 0) {
            long itemsPerSecond = processedItems / durationSeconds;
            
            log.info("性能分析:");
            log.info("  處理效率: {} 筆/秒", itemsPerSecond);
            log.info("  平均處理時間: {} 毫秒/筆", (duration.toMillis() / Math.max(processedItems, 1)));
            
            // 性能警告
            if (itemsPerSecond < performanceThreshold) {
                log.warn("性能警告: 處理效率低於閾值 {} 筆/秒", performanceThreshold);
                stepExecution.getExecutionContext().putString("performanceWarning", "LOW_THROUGHPUT");
            }
            
            // 長時間運行警告
            if (durationSeconds > 3600) { // 超過1小時
                log.warn("性能警告: 步驟執行時間過長 {} 秒", durationSeconds);
                stepExecution.getExecutionContext().putString("performanceWarning", "LONG_RUNNING");
            }
        }
    }

    /**
     * 錯誤分析
     */
    private void analyzeErrors(StepExecution stepExecution) {
        long totalErrors = stepExecution.getSkipCount() + stepExecution.getProcessSkipCount() + 
                          stepExecution.getReadSkipCount() + stepExecution.getWriteSkipCount();
        
        if (totalErrors > 0) {
            log.warn("錯誤分析:");
            log.warn("  總錯誤數: {}", totalErrors);
            log.warn("  讀取錯誤: {}", stepExecution.getReadSkipCount());
            log.warn("  處理錯誤: {}", stepExecution.getProcessSkipCount());
            log.warn("  寫入錯誤: {}", stepExecution.getWriteSkipCount());
            log.warn("  其他跳過: {}", stepExecution.getSkipCount());
            
            // 計算錯誤率
            long totalItems = stepExecution.getReadCount();
            if (totalItems > 0) {
                double errorRate = (double) totalErrors / totalItems * 100;
                log.warn("  錯誤率: {:.2f}%", errorRate);
                
                // 錯誤率警告
                if (totalErrors > errorThreshold) {
                    log.error("錯誤警告: 錯誤數量超過閾值 {}", errorThreshold);
                    stepExecution.getExecutionContext().putString("errorWarning", "HIGH_ERROR_COUNT");
                }
                
                if (errorRate > 5.0) { // 錯誤率超過5%
                    log.error("錯誤警告: 錯誤率過高 {:.2f}%", errorRate);
                    stepExecution.getExecutionContext().putString("errorWarning", "HIGH_ERROR_RATE");
                }
            }
        }
    }

    /**
     * 處理步驟結果
     */
    private ExitStatus handleStepResult(StepExecution stepExecution, ExitStatus exitStatus) {
        String stepName = stepExecution.getStepName();
        String exitCode = exitStatus.getExitCode();
        
        log.info("處理步驟結果:");
        log.info("  步驟名稱: {}", stepName);
        log.info("  退出狀態: {}", exitCode);
        
        // 更新執行上下文
        stepExecution.getExecutionContext().putString("stepStatus", exitCode);
        
        switch (exitCode) {
            case "COMPLETED":
                return handleCompletedStep(stepExecution, exitStatus);
            case "FAILED":
                return handleFailedStep(stepExecution, exitStatus);
            case "STOPPED":
                return handleStoppedStep(stepExecution, exitStatus);
            default:
                return exitStatus;
        }
    }

    /**
     * 處理完成的步驟
     */
    private ExitStatus handleCompletedStep(StepExecution stepExecution, ExitStatus exitStatus) {
        log.info("步驟成功完成");
        
        // 根據步驟類型執行相應的後續處理
        String stepName = stepExecution.getStepName();
        
        switch (stepName) {
            case "migrationStep":
                log.info("遷移步驟完成，資料已成功遷移");
                break;
            case "cleanupStep":
                log.info("清理步驟完成，舊資料已清理");
                break;
            case "validationStep":
                log.info("驗證步驟完成，資料驗證通過");
                break;
            case "retryStep":
                log.info("重試步驟完成，失敗資料已重新處理");
                break;
        }
        
        // 檢查是否有警告
        if (stepExecution.getExecutionContext().containsKey("performanceWarning") ||
            stepExecution.getExecutionContext().containsKey("errorWarning")) {
            return new ExitStatus("COMPLETED_WITH_WARNINGS");
        }
        
        return exitStatus;
    }

    /**
     * 處理失敗的步驟
     */
    private ExitStatus handleFailedStep(StepExecution stepExecution, ExitStatus exitStatus) {
        log.error("步驟執行失敗");
        
        // 記錄失敗原因
        stepExecution.getFailureExceptions().forEach(exception -> 
            log.error("失敗原因: {}", exception.getMessage(), exception)
        );
        
        // 根據失敗類型決定後續處理
        String stepName = stepExecution.getStepName();
        
        switch (stepName) {
            case "migrationStep":
                log.error("遷移步驟失敗，建議檢查資料源和目標");
                return new ExitStatus("FAILED_MIGRATION");
            case "cleanupStep":
                log.error("清理步驟失敗，可能需要手動清理");
                return new ExitStatus("FAILED_CLEANUP");
            case "validationStep":
                log.error("驗證步驟失敗，資料可能有問題");
                return new ExitStatus("FAILED_VALIDATION");
            case "retryStep":
                log.error("重試步驟失敗，需要人工介入");
                return new ExitStatus("FAILED_RETRY");
        }
        
        return exitStatus;
    }

    /**
     * 處理停止的步驟
     */
    private ExitStatus handleStoppedStep(StepExecution stepExecution, ExitStatus exitStatus) {
        log.warn("步驟被停止");
        return exitStatus;
    }

    /**
     * 記錄步驟開始
     */
    private void recordStepStart(StepExecution stepExecution) {
        // 這裡可以實現監控系統的記錄邏輯
        log.debug("記錄步驟開始監控資訊");
    }

    /**
     * 記錄步驟結束
     */
    private void recordStepEnd(StepExecution stepExecution) {
        // 這裡可以實現監控系統的記錄邏輯
        log.debug("記錄步驟結束監控資訊");
    }
}