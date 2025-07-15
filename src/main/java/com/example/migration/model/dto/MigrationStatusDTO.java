package com.example.migration.model.dto;

import java.time.LocalDateTime;

/**
 * 遷移狀態 DTO
 */
public class MigrationStatusDTO {

    private String jobName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalRecords;
    private Long processedRecords;
    private Long successRecords;
    private Long failedRecords;
    private Double progressPercentage;
    private String currentStep;
    private String errorMessage;
    private Long estimatedRemainingTime; // 預估剩餘時間（毫秒）

    // Constructors
    public MigrationStatusDTO() {}

    public MigrationStatusDTO(String jobName, String status) {
        this.jobName = jobName;
        this.status = status;
    }

    // Getters and Setters
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords)	{ this.totalRecords = totalRecords; }

	public Long getProcessedRecords() { return processedRecords; }
	public void setProcessedRecords(Long processedRecords) { this.processedRecords = processedRecords; }

	public Long getSuccessRecords() { return successRecords; }
	public void setSuccessRecords(Long successRecords) { this.successRecords = successRecords; }

	public Long getFailedRecords() { return failedRecords; }
	public void setFailedRecords(Long failedRecords) { this.failedRecords = failedRecords; }

	public Double getProgressPercentage() { return progressPercentage; }
	public void setProgressPercentage(Double progressPercentage) { this.progressPercentage = progressPercentage; }

	public String getCurrentStep() { return currentStep; }
	public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }	

	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

	public Long getEstimatedRemainingTime() { return estimatedRemainingTime; }
	public void setEstimatedRemainingTime(Long estimatedRemainingTime) { this.estimatedRemainingTime = estimatedRemainingTime; }


}
