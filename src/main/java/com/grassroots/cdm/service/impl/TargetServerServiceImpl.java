package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.dto.TargetServerDto;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.TargetServerRepository;
import com.grassroots.cdm.service.TargetServerService;
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
@Transactional(readOnly = true)
public class TargetServerServiceImpl implements TargetServerService {

    private static final Logger log = LoggerFactory.getLogger(TargetServerServiceImpl.class);

    private final TargetServerRepository serverRepository;
    private final CdmDtoMapper dtoMapper;

    public TargetServerServiceImpl(TargetServerRepository serverRepository, CdmDtoMapper dtoMapper) {
        this.serverRepository = serverRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PagedResponse<TargetServerDto> getServers(
            EnvironmentType environment,
            ServerTechnology technology,
            ServerStatus status,
            String hostname,
            Pageable pageable
    ) {
        log.debug("Fetching servers: environment={}, technology={}, status={}, hostname={}, pageable={}",
                environment, technology, status, hostname, pageable);

        Specification<TargetServer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (environment != null) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            if (technology != null) {
                predicates.add(cb.equal(root.get("technology"), technology));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hostname != null && !hostname.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("hostname")), "%" + hostname.toLowerCase().trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TargetServer> page = serverRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toTargetServerDto);
    }

    @Override
    public TargetServerDto getServerById(UUID id) {
        log.debug("Fetching server by ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("Server ID must not be null");
        }
        return serverRepository.findById(id)
                .map(dtoMapper::toTargetServerDto)
                .orElseThrow(() -> new ResourceNotFoundException("Target server not found with ID: " + id));
    }
}
