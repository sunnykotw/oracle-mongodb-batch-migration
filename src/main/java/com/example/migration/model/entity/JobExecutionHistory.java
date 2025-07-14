package com.example.migration.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 作業執行歷史實體
 */
@Entity
@Table(name = "JOB_EXECUTION_HISTORY")
public class JobExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "JOB_NAME", nullable = false)
    private String jobName;

    @Column(name = "JOB_PARAMETERS", columnDefinition = "CLOB")
    private String jobParameters;

    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "EXIT_CODE")
    private String exitCode;

    @Column(name = "EXIT_MESSAGE", columnDefinition = "CLOB")
    private String exitMessage;

    @Column(name = "READ_COUNT")
    private Long readCount;

    @Column(name = "WRITE_COUNT")
    private Long writeCount;

    @Column(name = "SKIP_COUNT")
    private Long skipCount;

    @Column(name = "ERROR_COUNT")
    private Long errorCount;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_TIME")
    private LocalDateTime createdTime;

    // Constructors
    public JobExecutionHistory() {}

    public JobExecutionHistory(String jobName, String jobParameters) {
        this.jobName = jobName;
        this.jobParameters = jobParameters;
        this.createdTime = LocalDateTime.now();
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

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}