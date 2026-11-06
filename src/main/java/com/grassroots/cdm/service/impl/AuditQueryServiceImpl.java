package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.AuditLogDto;
import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.service.AuditQueryService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryServiceImpl.class);

    private final AuditLogRecordRepository auditRepository;
    private final CdmDtoMapper dtoMapper;

    public AuditQueryServiceImpl(AuditLogRecordRepository auditRepository, CdmDtoMapper dtoMapper) {
        this.auditRepository = auditRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PagedResponse<AuditLogDto> getAuditEvents(
            String correlationId,
            String eventType,
            String action,
            String actor,
            String entityName,
            String entityId,
            String outcome,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        log.debug("Fetching audit events: correlationId={}, eventType={}, action={}, actor={}, entityName={}, outcome={}, pageable={}",
                correlationId, eventType, action, actor, entityName, outcome, pageable);

        Specification<AuditLogRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (correlationId != null && !correlationId.isBlank()) {
                predicates.add(cb.equal(root.get("correlationId"), correlationId.trim()));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.equal(root.get("actor"), actor.trim()));
            }
            if (entityName != null && !entityName.isBlank()) {
                predicates.add(cb.equal(root.get("entityName"), entityName.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (outcome != null && !outcome.isBlank()) {
                predicates.add(cb.equal(root.get("outcome"), outcome.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLogRecord> page = auditRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toAuditLogDto);
    }
}
