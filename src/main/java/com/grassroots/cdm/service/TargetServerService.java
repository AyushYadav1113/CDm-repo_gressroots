package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.dto.TargetServerDto;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying managed target server inventory.
 */
public interface TargetServerService {

    /**
     * Retrieves paginated target server records matching optional filter criteria.
     */
    PagedResponse<TargetServerDto> getServers(
            EnvironmentType environment,
            ServerTechnology technology,
            ServerStatus status,
            String hostname,
            Pageable pageable
    );

    /**
     * Retrieves a single target server by its unique UUID identifier.
     *
     * @param id target server UUID
     * @return target server DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no server exists with the ID
     */
    TargetServerDto getServerById(UUID id);
}
