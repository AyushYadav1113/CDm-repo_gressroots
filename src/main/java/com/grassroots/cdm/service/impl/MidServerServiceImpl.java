package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.MidServerDto;
import com.grassroots.cdm.dto.MidServerHealthDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.MidServerRepository;
import com.grassroots.cdm.service.MidServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MidServerServiceImpl implements MidServerService {

    private static final Logger log = LoggerFactory.getLogger(MidServerServiceImpl.class);

    private final MidServerRepository midServerRepository;
    private final CdmDtoMapper dtoMapper;

    public MidServerServiceImpl(MidServerRepository midServerRepository, CdmDtoMapper dtoMapper) {
        this.midServerRepository = midServerRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PagedResponse<MidServerDto> getMidServers(MidServerStatus status, Pageable pageable) {
        log.debug("Fetching MID servers: status={}, pageable={}", status, pageable);

        Specification<MidServer> spec = (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), status);
        };

        Page<MidServer> page = midServerRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toMidServerDto);
    }

    @Override
    public MidServerDto getMidServerById(UUID id) {
        log.debug("Fetching MID server by ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("MID Server ID must not be null");
        }
        return midServerRepository.findById(id)
                .map(dtoMapper::toMidServerDto)
                .orElseThrow(() -> new ResourceNotFoundException("MID Server not found with ID: " + id));
    }

    @Override
    public MidServerHealthDto getMidServerHealth(UUID id) {
        log.debug("Checking MID server health for ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("MID Server ID must not be null");
        }
        MidServer midServer = midServerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MID Server not found with ID: " + id));

        return dtoMapper.toMidServerHealthDto(midServer);
    }
}
