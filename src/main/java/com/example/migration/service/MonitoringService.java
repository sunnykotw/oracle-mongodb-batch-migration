package com.example.migration.service;

import com.example.migration.model.dto.MigrationStatusDTO;
import com.example.migration.model.entity.JobExecutionHistory;
import com.example.migration.repository.oracle.JobExecutionHistoryRepository;
import com.example.migration.repository.oracle.OracleRepository;
import com.example.migration.repository.mongodb.MigrationDocumentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class MonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    @Autowired
    private JobExecutionHistoryRepository jobExecutionHistoryRepository;

    @Autowired
    private OracleRepository oracleRepository;

    @Autowired
    private MigrationDocumentRepository migrationDocumentRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    // 監控指標
    private Counter jobExecutionCounter;
    private Counter jobSuccessCounter;
    private Counter jobFailureCounter;
    private Timer jobExecutionTimer;
    private AtomicLong activeJobsGauge;
    private AtomicLong totalRecordsProcessedGauge;
    private AtomicLong errorRecordsGauge;

    // 記憶體快取
    private final Map<String, MigrationStatusDTO> statusCache = new ConcurrentHashMap<>();
    private final Map<String, Long> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<String>> alertHistory = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("Initializing MonitoringService");
        initializeMetrics();
        loadInitialData();
    }

    /**
     * 初始化監控指標
     */
    private void initializeMetrics() {
        jobExecutionCounter = Counter.builder("migration.job.execution.total")
                .description("Total number of job executions")
                .register(meterRegistry);

        jobSuccessCounter = Counter.builder("migration.job.success.total")
                .description("Total number of successful job executions")
                .register(meterRegistry);

        jobFailureCounter = Counter.builder("migration.job.failure.total")
                .description("Total number of failed job executions")
                .register(meterRegistry);

        jobExecutionTimer = Timer.builder("migration.job.execution.duration")
                .description("Job execution duration")
                .register(meterRegistry);

        activeJobsGauge = new AtomicLong(0);
        Gauge.builder("migration.job.active")
                .description("Number of currently active jobs")
                .register(meterRegistry, activeJobsGauge, AtomicLong::get);

        totalRecordsProcessedGauge = new AtomicLong(0);
        Gauge.builder("migration.records.processed.total")
                .description("Total number of records processed")
                .register(meterRegistry, totalRecordsProcessedGauge, AtomicLong::get);

        errorRecordsGauge = new AtomicLong(0);
        Gauge.builder("migration.records.error.total")
                .description("Total number of error records")
                .register(meterRegistry, errorRecordsGauge, AtomicLong::get);

        logger.info("Monitoring metrics initialized");
    }

    /**
     * 載入初始數據
     */
    private void loadInitialData() {
        try {
            // 載入活躍作業數
            Long activeJobs = jobExecutionHistoryRepository.countByStatus("STARTED");
            activeJobsGauge.set(activeJobs);

            // 載入總處理記錄數
            Long totalProcessed = migrationDocumentRepository.count();
            totalRecordsProcessedGauge.set(totalProcessed);

            logger.info("Initial monitoring data loaded - Active jobs: {}, Total processed: {}", 
                activeJobs, totalProcessed);

        } catch (Exception e) {
            logger.error("Error loading initial monitoring data", e);
        }
    }

    /**
     * 記錄作業執行開始
     */
    public void recordJobStart(String jobName, Long executionId) {
        logger.debug("Recording job start: {} with execution id: {}", jobName, executionId);
        
        jobExecutionCounter.increment();
        activeJobsGauge.incrementAndGet();
        
        // 記錄開始時間
        performanceMetrics.put(jobName + "_" + executionId + "_start", System.currentTimeMillis());
        
        // 更新狀態快取
        MigrationStatusDTO status = new MigrationStatusDTO();
        status.setJobName(jobName);
        status.setExecutionId(executionId);
        status.setStatus("STARTED");
        status.setStartTime(LocalDateTime.now());
        status.setRecordsProcessed(0L);
        status.setErrorCount(0L);
        statusCache.put(jobName + "_" + executionId, status);
    }

    /**
     * 記錄作業執行完成
     */
    public void recordJobCompletion(String jobName, Long executionId, String status, Long recordsProcessed, Long errorCount) {
        logger.debug("Recording job completion: {} with status: {}", jobName, status);
        
        activeJobsGauge.decrementAndGet();
        
        if ("COMPLETED".equals(status)) {
            jobSuccessCounter.increment();
        } else if ("FAILED".equals(status)) {
            jobFailureCounter.increment();
        }
        
        // 記錄執行時間
        String startKey = jobName + "_" + executionId + "_start";
        Long startTime = performanceMetrics.get(startKey);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            jobExecutionTimer.record(duration, TimeUnit.MILLISECONDS);
            performanceMetrics.remove(startKey);
        }
        
        // 更新總處理記錄數
        if (recordsProcessed != null) {
            totalRecordsProcessedGauge.addAndGet(recordsProcessed);
        }
        
        // 更新錯誤記錄數
        if (errorCount != null) {
            errorRecordsGauge.addAndGet(errorCount);
        }
        
        // 更新狀態快取
        String statusKey = jobName + "_" + executionId;
        MigrationStatusDTO statusDTO = statusCache.get(statusKey);
        if (statusDTO != null) {
            statusDTO.setStatus(status);
            statusDTO.setEndTime(LocalDateTime.now());
            statusDTO.setRecordsProcessed(recordsProcessed != null ? recordsProcessed : 0L);
            statusDTO.setErrorCount(errorCount != null ? errorCount : 0L);
            
            // 計算執行時間
            if (statusDTO.getStartTime() != null) {
                long duration = ChronoUnit.SECONDS.between(statusDTO.getStartTime(), statusDTO.getEndTime());
                statusDTO.setDuration(duration);
            }
        }
        
        // 檢查是否需要觸發告警
        checkAlerts(jobName, status, errorCount);
    }

    /**
     * 更新作業進度
     */
    public void updateJobProgress(String jobName, Long executionId, Long recordsProcessed, Long errorCount) {
        logger.debug("Updating job progress: {} - Records: {}, Errors: {}", jobName, recordsProcessed, errorCount);
        
        String statusKey = jobName + "_" + executionId;
        MigrationStatusDTO statusDTO = statusCache.get(statusKey);
        if (statusDTO != null) {
            statusDTO.setRecordsProcessed(recordsProcessed != null ? recordsProcessed : 0L);
            statusDTO.setErrorCount(errorCount != null ? errorCount : 0L);
            statusDTO.setLastUpdateTime(LocalDateTime.now());
        }
    }

    /**
     * 獲取作業狀態
     */
    public MigrationStatusDTO getJobStatus(String jobName, Long executionId) {
        String statusKey = jobName + "_" + executionId;
        MigrationStatusDTO status = statusCache.get(statusKey);
        
        if (status == null) {
            // 從資料庫載入
            Optional<JobExecutionHistory> historyOpt = jobExecutionHistoryRepository.findByExecutionId(executionId);
            if (historyOpt.isPresent()) {
                JobExecutionHistory history = historyOpt.get();
                status = convertToStatusDTO(history);
                statusCache.put(statusKey, status);
            }
        }
        
        return status;
    }

    /**
     * 獲取所有活躍作業狀態
     */
    public List<MigrationStatusDTO> getActiveJobStatuses() {
        return statusCache.values().stream()
                .filter(status -> "STARTED".equals(status.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 獲取系統健康狀態
     */
    public Map<String, Object> getSystemHealth() {
        logger.debug("Getting system health status");
        
        Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            // 資料庫連接狀態
            healthStatus.put("database.oracle.status", checkOracleHealth());
            healthStatus.put("database.mongodb.status", checkMongoDBHealth());
            
            // 作業執行狀態
            healthStatus.put("jobs.active", activeJobsGauge.get());
            healthStatus.put("jobs.total_executions", jobExecutionCounter.count());
            healthStatus.put("jobs.success_rate", calculateSuccessRate());
            
            // 記憶體使用情況
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            healthStatus.put("memory.total", totalMemory);
            healthStatus.put("memory.used", usedMemory);
            healthStatus.put("memory.free", freeMemory);
            healthStatus.put("memory.usage_percentage", (double) usedMemory / totalMemory * 100);
            
            // 最近錯誤數量
            healthStatus.put("recent_errors", getRecentErrorCount());
            
            // 整體狀態
            healthStatus.put("overall_status", determineOverallHealth(healthStatus));
            
        } catch (Exception e) {
            logger.error("Error getting system health", e);
            healthStatus.put("overall_status", "ERROR");
            healthStatus.put("error_message", e.getMessage());
        }
        
        return healthStatus;
    }

    /**
     * 獲取效能統計
     */
    public Map<String, Object> getPerformanceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 執行時間統計
            stats.put("average_execution_time", jobExecutionTimer.mean(TimeUnit.SECONDS));
            stats.put("max_execution_time", jobExecutionTimer.max(TimeUnit.SECONDS));
            stats.put("min_execution_time", jobExecutionTimer.totalTime(TimeUnit.SECONDS) / jobExecutionTimer.count());
            
            // 處理記錄統計
            stats.put("total_records_processed", totalRecordsProcessedGauge.get());
            stats.put("total_error_records", errorRecordsGauge.get());
            
            // 成功率統計
            double successRate = calculateSuccessRate();
            stats.put("success_rate", successRate);
            
            // 處理速度統計
            double avgProcessingSpeed = calculateAverageProcessingSpeed();
            stats.put("average_processing_speed", avgProcessingSpeed);
            
            // 最近24小時統計
            stats.put("last_24h_statistics", getLast24HourStatistics());
            
        } catch (Exception e) {
            logger.error("Error getting performance statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 獲取告警資訊
     */
    public Map<String, Object> getAlertInformation() {
        Map<String, Object> alertInfo = new HashMap<>();
        
        try {
            // 活躍告警
            List<String> activeAlerts = new ArrayList<>();
            
            // 檢查活躍作業過多
            if (activeJobsGauge.get() > 10) {
                activeAlerts.add("High number of active jobs: " + activeJobsGauge.get());
            }
            
            // 檢查成功率過低
            double successRate = calculateSuccessRate();
            if (successRate < 80.0) {
                activeAlerts.add("Low success rate: " + String.format("%.2f%%", successRate));
            }
            
            // 檢查記憶體使用過高
            Runtime runtime = Runtime.getRuntime();
            double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.totalMemory() * 100;
            if (memoryUsage > 85.0) {
                activeAlerts.add("High memory usage: " + String.format("%.2f%%", memoryUsage));
            }
            
            // 檢查最近錯誤數量
            long recentErrors = getRecentErrorCount();
            if (recentErrors > 100) {
                activeAlerts.add("High number of recent errors: " + recentErrors);
            }
            
            alertInfo.put("active_alerts", activeAlerts);
            alertInfo.put("alert_count", activeAlerts.size());
            alertInfo.put("alert_history", getAlertHistory());
            
        } catch (Exception e) {
            logger.error("Error getting alert information", e);
            alertInfo.put("error", e.getMessage());
        }
        
        return alertInfo;
    }

    /**
     * 定期清理快取
     */
    @Scheduled(fixedDelay = 300000) // 5分鐘
    public void cleanupCache() {
        logger.debug("Starting cache cleanup");
        
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            
            Iterator<Map.Entry<String, MigrationStatusDTO>> iterator = statusCache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, MigrationStatusDTO> entry = iterator.next();
                MigrationStatusDTO status = entry.getValue();
                
                // 清理24小時前的已完成作業
                if (status.getEndTime() != null && status.getEndTime().isBefore(cutoff)) {
                    iterator.remove();
                }
            }
            
            logger.debug("Cache cleanup completed. Current cache size: {}", statusCache.size());
            
        } catch (Exception e) {
            logger.error("Error during cache cleanup", e);
        }
    }

    /**
     * 定期更新監控數據
     */
    @Scheduled(fixedDelay = 60000) // 1分鐘
    public void updateMonitoringData() {
        logger.debug("Updating monitoring data");
        
        try {
            // 更新活躍作業數
            Long activeJobs = jobExecutionHistoryRepository.countByStatus("STARTED");
            activeJobsGauge.set(activeJobs);
            
            // 更新總處理記錄數
            Long totalProcessed = migrationDocumentRepository.count();
            totalRecordsProcessedGauge.set(totalProcessed);
            
        } catch (Exception e) {
            logger.error("Error updating monitoring data", e);
        }
    }

    /**
     * 檢查Oracle資料庫健康狀態
     */
    private String checkOracleHealth() {
        try {
            // 執行簡單的查詢來檢查連接
            oracleRepository.findTop1ByOrderByIdAsc();
            return "HEALTHY";
        } catch (Exception e) {
            logger.error("Oracle health check failed", e);
            return "UNHEALTHY";
        }
    }

    /**
     * 檢查MongoDB健康狀態
     */
    private String checkMongoDBHealth() {
        try {
            // 執行簡單的查詢來檢查連接
            migrationDocumentRepository.count();
            return "HEALTHY";
        } catch (Exception e) {
            logger.error("MongoDB health check failed", e);
            return "UNHEALTHY";
        }
    }

    /**
     * 計算成功率
     */
    private double calculateSuccessRate() {
        double totalExecutions = jobExecutionCounter.count();
        double successfulExecutions = jobSuccessCounter.count();
        
        if (totalExecutions == 0) {
            return 100.0;
        }
        
        return (successfulExecutions / totalExecutions) * 100.0;
    }

    /**
     * 計算平均處理速度
     */
    private double calculateAverageProcessingSpeed() {
        try {
            double totalTime = jobExecutionTimer.totalTime(TimeUnit.SECONDS);
            long totalRecords = totalRecordsProcessedGauge.get();
            
            if (totalTime == 0) {
                return 0.0;
            }
            
            return totalRecords / totalTime; // 記錄數/秒
            
        } catch (Exception e) {
            logger.error("Error calculating average processing speed", e);
            return 0.0;
        }
    }

    /**
     * 獲取最近錯誤數量
     */
    private long getRecentErrorCount() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            return jobExecutionHistoryRepository.countByStatusAndStartTimeAfter("FAILED", cutoff);
        } catch (Exception e) {
            logger.error("Error getting recent error count", e);
            return 0;
        }
    }

    /**
     * 獲取最近24小時統計
     */
    private Map<String, Object> getLast24HourStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            
            Long totalExecutions = jobExecutionHistoryRepository.countByStartTimeAfter(cutoff);
            Long successfulExecutions = jobExecutionHistoryRepository.countByStatusAndStartTimeAfter("COMPLETED", cutoff);
            Long failedExecutions = jobExecutionHistoryRepository.countByStatusAndStartTimeAfter("FAILED", cutoff);
            
            stats.put("total_executions", totalExecutions);
            stats.put("successful_executions", successfulExecutions);
            stats.put("failed_executions", failedExecutions);
            
            if (totalExecutions > 0) {
                stats.put("success_rate", (double) successfulExecutions / totalExecutions * 100);
            } else {
                stats.put("success_rate", 0.0);
            }
            
        } catch (Exception e) {
            logger.error("Error getting last 24 hour statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 檢查告警
     */
    private void checkAlerts(String jobName, String status, Long errorCount) {
        List<String> jobAlerts = alertHistory.computeIfAbsent(jobName, k -> new ArrayList<>());
        
        if ("FAILED".equals(status)) {
            String alert = "Job " + jobName + " failed at " + LocalDateTime.now();
            jobAlerts.add(alert);
            logger.warn("ALERT: {}", alert);
        }
        
        if (errorCount != null && errorCount > 1000) {
            String alert = "Job " + jobName + " has high error count: " + errorCount;
            jobAlerts.add(alert);
            logger.warn("ALERT: {}", alert);
        }
        
        // 保持告警歷史記錄在合理大小
        if (jobAlerts.size() > 100) {
            jobAlerts.subList(0, jobAlerts.size() - 100).clear();
        }
    }

    /**
     * 獲取告警歷史
     */
    private Map<String, List<String>> getAlertHistory() {
        Map<String, List<String>> recentAlerts = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : alertHistory.entrySet()) {
            List<String> alerts = entry.getValue();
            if (!alerts.isEmpty()) {
                // 只返回最近10條告警
                int startIndex = Math.max(0, alerts.size() - 10);
                recentAlerts.put(entry.getKey(), new ArrayList<>(alerts.subList(startIndex, alerts.size())));
            }
        }
        
        return recentAlerts;
    }

    /**
     * 判斷整體健康狀態
     */
    private String determineOverallHealth(Map<String, Object> healthStatus) {
        String oracleStatus = (String) healthStatus.get("database.oracle.status");
        String mongoStatus = (String) healthStatus.get("database.mongodb.status");
        double successRate = (Double) healthStatus.get("jobs.success_rate");
        double memoryUsage = (Double) healthStatus.get("memory.usage_percentage");
        
        if (!"HEALTHY".equals(oracleStatus) || !"HEALTHY".equals(mongoStatus)) {
            return "CRITICAL";
        }
        
        if (successRate < 80.0 || memoryUsage > 90.0) {
            return "WARNING";
        }
        
        return "HEALTHY";
    }

    /**
     * 轉換為狀態DTO
     */
    private MigrationStatusDTO convertToStatusDTO(JobExecutionHistory history) {
        MigrationStatusDTO status = new MigrationStatusDTO();
        status.setJobName(history.getJobName());
        status.setExecutionId(history.getExecutionId());
        status.setStatus(history.getStatus());
        status.setStartTime(history.getStartTime());
        status.setEndTime(history.getEndTime());
        
        // 從退出訊息中解析記錄數（如果有的話）
        if (history.getExitMessage() != null) {
            // 這裡可以根據實際的退出訊息格式來解析
            // 暫時設置為0
            status.setRecordsProcessed(0L);
            status.setErrorCount(0L);
        }
        
        return status;
    }
}