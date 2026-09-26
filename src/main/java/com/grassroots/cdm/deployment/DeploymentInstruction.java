package com.grassroots.cdm.deployment;

import java.util.Map;

/**
 * Structured, parameter-driven instruction payload dispatched to MID Server.
 * Target servers are never directly subjected to arbitrary ad-hoc commands.
 */
public record DeploymentInstruction(
        String jobReference,
        TargetType targetType,
        String targetHost,
        int targetPort,
        String certificateBundleReference,
        Map<String, String> parameters
) {}
