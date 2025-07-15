package com.example.migration.service;

import com.example.migration.model.dto.JobConfigDTO;
import com.example.migration.model.dto.JobExecutionDTO;
import com.example.migration.model.entity.JobExecutionHistory;
import com.example.migration.repository.oracle.JobExecutionHistoryRepository;
import com.example.migration.exception.custom.MigrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobManagementService {

    private static final Logger logger = LoggerFactory.getLogger(JobManagementService.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRegistry jobRegistry;

    @Autowired
    private JobExecutionHistoryRepository jobExecutionHistoryRepository;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private MonitoringService monitoringService;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 啟動批次作業
     */
    @Async
    public CompletableFuture<JobExecutionDTO> startJob(String jobName, Map<String, Object> parameters) {
        logger.info("Starting job: {} with parameters: {}", jobName, parameters);
        
        try {
            // 獲取Job配置
            JobConfigDTO jobConfig = configurationService.getJobConfig(jobName);
            if (jobConfig == null) {
                throw new MigrationException("Job configuration not found: " + jobName);
            }

            // 驗證Job配置
            validateJobConfig(jobConfig);

            // 從註冊表獲取Job
            Job job = jobRegistry.getJob(jobName);
            
            // 構建Job參數
            JobParameters jobParameters = buildJobParameters(parameters);
            
            // 執行Job
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);
            
            // 記錄執行歷史
            saveJobExecutionHistory(jobExecution, jobConfig);
            
            // 轉換為DTO
            JobExecutionDTO executionDTO = convertToJobExecutionDTO(jobExecution);
            
            logger.info("Job {} started successfully with execution id: {}", jobName, jobExecution.getId());
            return CompletableFuture.completedFuture(executionDTO);
            
        } catch (NoSuchJobException e) {
            logger.error("Job not found: {}", jobName, e);
            throw new MigrationException("Job not found: " + jobName, e);
        } catch (JobExecutionAlreadyRunningException e) {
            logger.error("Job {} is already running", jobName, e);
            throw new MigrationException("Job is already running: " + jobName, e);
        } catch (JobRestartException e) {
            logger.error("Job {} restart failed", jobName, e);
            throw new MigrationException("Job restart failed: " + jobName, e);
        } catch (JobInstanceAlreadyCompleteException e) {
            logger.error("Job {} instance already complete", jobName, e);
            throw new MigrationException("Job instance already complete: " + jobName, e);
        } catch (JobParametersInvalidException e) {
            logger.error("Invalid job parameters for job: {}", jobName, e);
            throw new MigrationException("Invalid job parameters: " + jobName, e);
        } catch (Exception e) {
            logger.error("Error starting job: {}", jobName, e);
            throw new MigrationException("Error starting job: " + jobName, e);
        }
    }

    /**
     * 停止執行中的作業
     */
    public boolean stopJob(Long executionId) {
        logger.info("Stopping job execution: {}", executionId);
        
        try {
            // 在實際應用中，需要實現停止邏輯
            // 這裡可以通過JobOperator或自定義的停止機制
            
            // 更新執行歷史狀態
            Optional<JobExecutionHistory> historyOpt = jobExecutionHistoryRepository.findByExecutionId(executionId);
            if (historyOpt.isPresent()) {
                JobExecutionHistory history = historyOpt.get();
                history.setStatus("STOPPED");
                history.setEndTime(LocalDateTime.now());
                jobExecutionHistoryRepository.save(history);
                
                logger.info("Job execution {} stopped successfully", executionId);
                return true;
            }
            
            logger.warn("Job execution {} not found", executionId);
            return false;
            
        } catch (Exception e) {
            logger.error("Error stopping job execution: {}", executionId, e);
            throw new MigrationException("Error stopping job execution: " + executionId, e);
        }
    }

    /**
     * 獲取作業執行狀態
     */
    public JobExecutionDTO getJobExecutionStatus(Long executionId) {
        logger.debug("Getting job execution status: {}", executionId);
        
        try {
            Optional<JobExecutionHistory> historyOpt = jobExecutionHistoryRepository.findByExecutionId(executionId);
            if (historyOpt.isPresent()) {
                return convertToJobExecutionDTO(historyOpt.get());
            }
            
            logger.warn("Job execution {} not found", executionId);
            return null;
            
        } catch (Exception e) {
            logger.error("Error getting job execution status: {}", executionId, e);
            throw new MigrationException("Error getting job execution status: " + executionId, e);
        }
    }

    /**
     * 獲取作業執行歷史
     */
    public Page<JobExecutionDTO> getJobExecutionHistory(String jobName, int page, int size) {
        logger.debug("Getting job execution history for job: {}, page: {}, size: {}", jobName, page, size);
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
            
            Page<JobExecutionHistory> historyPage;
            if (jobName != null && !jobName.isEmpty()) {
                historyPage = jobExecutionHistoryRepository.findByJobName(jobName, pageable);
            } else {
                historyPage = jobExecutionHistoryRepository.findAll(pageable);
            }
            
            return historyPage.map(this::convertToJobExecutionDTO);
            
        } catch (Exception e) {
            logger.error("Error getting job execution history", e);
            throw new MigrationException("Error getting job execution history", e);
        }
    }

    /**
     * 獲取所有可用的作業
     */
    public List<String> getAvailableJobs() {
        logger.debug("Getting available jobs");
        
        try {
            Collection<String> jobNames = jobRegistry.getJobNames();
            return new ArrayList<>(jobNames);
            
        } catch (Exception e) {
            logger.error("Error getting available jobs", e);
            throw new MigrationException("Error getting available jobs", e);
        }
    }

    /**
     * 重新啟動失敗的作業
     */
    @Async
    public CompletableFuture<JobExecutionDTO> restartJob(Long executionId) {
        logger.info("Restarting job execution: {}", executionId);
        
        try {
            // 獲取原始執行記錄
            Optional<JobExecutionHistory> historyOpt = jobExecutionHistoryRepository.findByExecutionId(executionId);
            if (!historyOpt.isPresent()) {
                throw new MigrationException("Job execution not found: " + executionId);
            }
            
            JobExecutionHistory history = historyOpt.get();
            
            // 只有失敗的作業才能重新啟動
            if (!"FAILED".equals(history.getStatus())) {
                throw new MigrationException("Only failed jobs can be restarted. Current status: " + history.getStatus());
            }
            
            // 重新啟動作業
            Map<String, Object> originalParameters = parseJobParameters(history.getJobParameters());
            return startJob(history.getJobName(), originalParameters);
            
        } catch (Exception e) {
            logger.error("Error restarting job execution: {}", executionId, e);
            throw new MigrationException("Error restarting job execution: " + executionId, e);
        }
    }

    /**
     * 獲取作業統計資訊
     */
    public Map<String, Object> getJobStatistics() {
        logger.debug("Getting job statistics");
        
        try {
            Map<String, Object> statistics = new HashMap<>();
            
            // 總執行次數
            Long totalExecutions = jobExecutionHistoryRepository.count();
            statistics.put("totalExecutions", totalExecutions);
            
            // 成功執行次數
            Long successfulExecutions = jobExecutionHistoryRepository.countByStatus("COMPLETED");
            statistics.put("successfulExecutions", successfulExecutions);
            
            // 失敗執行次數
            Long failedExecutions = jobExecutionHistoryRepository.countByStatus("FAILED");
            statistics.put("failedExecutions", failedExecutions);
            
            // 執行中的作業數
            Long runningExecutions = jobExecutionHistoryRepository.countByStatus("STARTED");
            statistics.put("runningExecutions", runningExecutions);
            
            // 成功率
            double successRate = totalExecutions > 0 ? (double) successfulExecutions / totalExecutions * 100 : 0;
            statistics.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            // 最近執行記錄
            List<JobExecutionHistory> recentExecutions = jobExecutionHistoryRepository
                .findTop10ByOrderByStartTimeDesc();
            statistics.put("recentExecutions", recentExecutions.stream()
                .map(this::convertToJobExecutionDTO)
                .collect(Collectors.toList()));
            
            return statistics;
            
        } catch (Exception e) {
            logger.error("Error getting job statistics", e);
            throw new MigrationException("Error getting job statistics", e);
        }
    }

    /**
     * 驗證Job配置
     */
    private void validateJobConfig(JobConfigDTO jobConfig) {
        if (jobConfig.getName() == null || jobConfig.getName().isEmpty()) {
            throw new MigrationException("Job name is required");
        }
        
        if (jobConfig.getSource() == null) {
            throw new MigrationException("Job source configuration is required");
        }
        
        if (jobConfig.getTarget() == null) {
            throw new MigrationException("Job target configuration is required");
        }
        
        // 添加更多驗證邏輯
    }

    /**
     * 構建Job參數
     */
    private JobParameters buildJobParameters(Map<String, Object> parameters) {
        JobParametersBuilder builder = new JobParametersBuilder();
        
        // 添加時間戳確保每次執行的唯一性
        builder.addLong("timestamp", System.currentTimeMillis());
        
        // 添加自定義參數
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    builder.addString(entry.getKey(), (String) value);
                } else if (value instanceof Long) {
                    builder.addLong(entry.getKey(), (Long) value);
                } else if (value instanceof Double) {
                    builder.addDouble(entry.getKey(), (Double) value);
                } else if (value instanceof Date) {
                    builder.addDate(entry.getKey(), (Date) value);
                } else {
                    builder.addString(entry.getKey(), String.valueOf(value));
                }
            }
        }
        
        return builder.toJobParameters();
    }

    /**
     * 保存作業執行歷史
     */
    private void saveJobExecutionHistory(JobExecution jobExecution, JobConfigDTO jobConfig) {
        JobExecutionHistory history = new JobExecutionHistory();
        history.setId(jobExecution.getId());
        history.setJobName(jobExecution.getJobInstance().getJobName());
        history.setStatus(jobExecution.getStatus().name());
        history.setStartTime(jobExecution.getStartTime() != null ? 
        		 jobExecution.getStartTime() :LocalDateTime.now());
        history.setEndTime(jobExecution.getEndTime() != null ? 
            jobExecution.getEndTime() : null);
        history.setJobParameters(jobExecution.getJobParameters().toString());
        history.setExitCode(jobExecution.getExitStatus().getExitCode());
        history.setExitMessage(jobExecution.getExitStatus().getExitDescription());
        
        jobExecutionHistoryRepository.save(history);
    }

    /**
     * 轉換為JobExecutionDTO
     */
    private JobExecutionDTO convertToJobExecutionDTO(JobExecution jobExecution) {
        JobExecutionDTO dto = new JobExecutionDTO();
        dto.setId(jobExecution.getId());
        dto.setJobName(jobExecution.getJobInstance().getJobName());
        dto.setStatus(jobExecution.getStatus().name());
        dto.setStartTime(jobExecution.getStartTime() != null ? 
            jobExecution.getStartTime() : null);
        dto.setEndTime(jobExecution.getEndTime() != null ? 
            jobExecution.getEndTime() : null);
        dto.setExitCode(jobExecution.getExitStatus().getExitCode());
        dto.setExitMessage(jobExecution.getExitStatus().getExitDescription());
        
     // 使用 Duration 計算秒數
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            long durationSeconds = java.time.Duration.between(dto.getStartTime(), dto.getEndTime()).getSeconds();
            dto.setDuration(durationSeconds);
        }
        
        return dto;
    }

    /**
     * 轉換為JobExecutionDTO (從JobExecutionHistory)
     */
    private JobExecutionDTO convertToJobExecutionDTO(JobExecutionHistory history) {
        JobExecutionDTO dto = new JobExecutionDTO();
        dto.setId(history.getId());
        dto.setJobName(history.getJobName());
        dto.setStatus(history.getStatus());
        dto.setStartTime(history.getStartTime());
        dto.setEndTime(history.getEndTime());
        dto.setExitCode(history.getExitCode());
        dto.setExitMessage(history.getExitMessage());
        
        // 計算執行時間
        if (history.getStartTime() != null && history.getEndTime() != null) {
            long durationSeconds = java.time.Duration.between(history.getStartTime(), history.getEndTime()).getSeconds();
            dto.setDuration(durationSeconds);
        }
        
        return dto;
    }

    /**
     * 解析Job參數
     */
    private Map<String, Object> parseJobParameters(String jobParametersString) {
        Map<String, Object> parameters = new HashMap<>();
        
        if (jobParametersString != null && !jobParametersString.isEmpty()) {
            // 簡單的參數解析邏輯
            String[] pairs = jobParametersString.split(",");
            for (String pair : pairs) {
                if (pair.contains("=")) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        parameters.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
        }
        
        return parameters;
    }
}