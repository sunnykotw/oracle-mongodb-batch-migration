package com.example.migration.repository.oracle;

import com.example.migration.model.entity.JobExecutionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobExecutionHistoryRepository extends JpaRepository<JobExecutionHistory, Long> {
    Optional<JobExecutionHistory> findByExecutionId(Long executionId);

    Page<JobExecutionHistory> findByJobName(String jobName, Pageable pageable);

    Long countByStatus(String status);

    List<JobExecutionHistory> findTop10ByOrderByStartTimeDesc();

	long countByStatusAndStartTimeAfter(String string, LocalDateTime cutoff);

	Long countByStartTimeAfter(LocalDateTime cutoff);
}