package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.MidServerExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MidServerExecutionRepository extends JpaRepository<MidServerExecution, UUID> {

    Optional<MidServerExecution> findByTaskId(String taskId);

    Optional<MidServerExecution> findByIdempotencyKey(String idempotencyKey);

    List<MidServerExecution> findByJobIdOrderByDispatchedAtDesc(UUID jobId);
}
