package com.example.migration.model.dto;

import java.time.LocalDateTime;

/**
 * 作業執行 DTO
 */
public class JobExecutionDTO {

    private Long id;
    private String jobName;
    private String jobParameters;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String exitCode;
    private String exitMessage;
    private Long readCount;
    private Long writeCount;
    private Long skipCount;
    private Long errorCount;
    private Long duration; // 執行時間（毫秒）
    private String createdBy;

    // Constructors
    public JobExecutionDTO() {}

    public JobExecutionDTO(String jobName, String status) {
        this.jobName = jobName;
        this.status = status;
        this.startTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobParameters() { return jobParameters; }
    public void setJobParameters(String jobParameters) { this.jobParameters = jobParameters; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExitCode() { return exitCode; }
    public void setExitCode(String exitCode) { this.exitCode = exitCode; }

    public String getExitMessage() { return exitMessage; }
    public void setExitMessage(String exitMessage) { this.exitMessage = exitMessage; }

    public Long getReadCount() { return readCount; }
    public void setReadCount(Long readCount) { this.readCount = readCount; }

    public Long getWriteCount() { return writeCount; }
    public void setWriteCount(Long writeCount) { this.writeCount = writeCount; }

    public Long getSkipCount() { return skipCount; }
    public void setSkipCount(Long skipCount) { this.skipCount = skipCount; }

    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    // Utility methods
    public boolean isRunning() {
        return "STARTED".equals(status) || "STARTING".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public double getSuccessRate() {
        if (readCount == null || readCount == 0) {
            return 0.0;
        }
        long successCount = writeCount != null ? writeCount : 0;
        return (double) successCount / readCount * 100;
    }
}