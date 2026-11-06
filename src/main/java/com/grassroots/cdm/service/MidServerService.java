package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.MidServerDto;
import com.grassroots.cdm.dto.MidServerHealthDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying MID Server nodes and health diagnostics.
 */
public interface MidServerService {

    /**
     * Retrieves paginated MID Server nodes matching optional filter criteria.
     */
    PagedResponse<MidServerDto> getMidServers(MidServerStatus status, Pageable pageable);

    /**
     * Retrieves a single MID Server node by its unique UUID identifier.
     *
     * @param id MID Server UUID
     * @return MID Server DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no node exists with the ID
     */
    MidServerDto getMidServerById(UUID id);

    /**
     * Performs a health and connectivity check on a specific MID Server node.
     *
     * @param id MID Server UUID
     * @return MID Server health DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no node exists with the ID
     */
    MidServerHealthDto getMidServerHealth(UUID id);
}
