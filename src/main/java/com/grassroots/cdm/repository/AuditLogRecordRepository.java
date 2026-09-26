package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.AuditLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for immutable AuditLogRecord entities.
 */
@Repository
public interface AuditLogRecordRepository extends JpaRepository<AuditLogRecord, UUID> {

    List<AuditLogRecord> findByEntityNameAndEntityId(String entityName, String entityId);

    List<AuditLogRecord> findByAction(String action);

    List<AuditLogRecord> findByActor(String actor);
}
