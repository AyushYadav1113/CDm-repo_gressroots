package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.DeploymentJobDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.service.DeploymentService;
import com.grassroots.cdm.workflow.WorkflowEngine;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeploymentServiceImpl implements DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentServiceImpl.class);

    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentWorkflowService workflowService;
    private final WorkflowEngine workflowEngine;
    private final AuditService auditService;
    private final CdmDtoMapper dtoMapper;

    public DeploymentServiceImpl(
            DeploymentJobRepository deploymentJobRepository,
            DeploymentWorkflowService workflowService,
            WorkflowEngine workflowEngine,
            AuditService auditService,
            CdmDtoMapper dtoMapper
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
        this.auditService = auditService;
        this.dtoMapper = dtoMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DeploymentJobDto> getDeployments(
            DeploymentJobStatus status,
            String targetHost,
            DeploymentType deploymentType,
            Pageable pageable
    ) {
        log.debug("Fetching deployments: status={}, targetHost={}, deploymentType={}, pageable={}",
                status, targetHost, deploymentType, pageable);

        Specification<DeploymentJob> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (targetHost != null && !targetHost.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("targetHost")), "%" + targetHost.toLowerCase().trim() + "%"));
            }
            if (deploymentType != null) {
                predicates.add(cb.equal(root.get("deploymentType"), deploymentType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DeploymentJob> page = deploymentJobRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toDeploymentJobDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DeploymentJobDto getDeploymentById(UUID id) {
        log.debug("Fetching deployment job by ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("Deployment job ID must not be null");
        }
        return deploymentJobRepository.findById(id)
                .map(dtoMapper::toDeploymentJobDto)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment job not found with ID: " + id));
    }

    @Override
    @Transactional
    public DeploymentJobDto retryDeployment(UUID id, String correlationId) {
        String cid = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("[{}] Manual retry requested for deployment job {}", cid, id);

        DeploymentJob job = deploymentJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment job not found with ID: " + id));

        auditService.recordAudit(AuditEvent.create(
                AuditAction.JOB_RETRIED,
                "DeploymentJob",
                id.toString(),
                "REST_API",
                "TRIGGERED",
                "Manual deployment retry triggered via REST API",
                "127.0.0.1"
        ));

        // Execute retry through workflow engine
        workflowEngine.retryJobWorkflow(id);

        DeploymentJob updatedJob = deploymentJobRepository.findById(id).orElse(job);
        return dtoMapper.toDeploymentJobDto(updatedJob);
    }

    @Override
    @Transactional
    public DeploymentJobDto verifyDeployment(UUID id, String correlationId) {
        String cid = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("[{}] Manual verification requested for deployment job {}", cid, id);

        if (!deploymentJobRepository.existsById(id)) {
            throw new ResourceNotFoundException("Deployment job not found with ID: " + id);
        }

        DeploymentJob updatedJob = workflowService.verifyDeployment(id, cid);
        return dtoMapper.toDeploymentJobDto(updatedJob);
    }
}
