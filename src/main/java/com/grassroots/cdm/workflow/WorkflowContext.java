package com.grassroots.cdm.workflow;

import java.util.Map;
import java.util.UUID;

/**
 * Contextual state passed across execution steps of the certificate deployment workflow.
 */
public record WorkflowContext(
        UUID deploymentJobId,
        String jobReference,
        Map<String, Object> attributes
) {}
