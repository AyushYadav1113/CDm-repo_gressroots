package com.grassroots.cdm.metrics;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Service contract for publishing operational and business metrics to Spring Boot Actuator / Micrometer.
 */
public interface CdmMetricsService {

    /**
     * Records count of certificates discovered or synchronized.
     *
     * @param source e.g. "SERVICENOW", "SECTIGO"
     * @param count  number of certificates discovered
     */
    void recordCertificatesDiscovered(String source, int count);

    /**
     * Records count of certificate renewal matches made.
     *
     * @param decision e.g. "AUTO_MATCH", "REVIEW_REQUIRED", "NO_MATCH"
     * @param count    number of matches evaluated
     */
    void recordCertificatesMatched(String decision, int count);

    /**
     * Records creation of a new deployment job.
     *
     * @param technology target technology (e.g. "IIS", "APACHE", "NGINX", "JAVA")
     */
    void recordDeploymentJobCreated(String technology);

    /**
     * Records a successfully completed deployment job.
     *
     * @param technology target technology
     */
    void recordDeploymentSuccessful(String technology);

    /**
     * Records a failed deployment job.
     *
     * @param failureType reason or failure category (e.g. "CREDENTIAL_FAILED", "MID_DISPATCH_FAILED", "VERIFICATION_FAILED")
     * @param technology  target technology
     */
    void recordDeploymentFailed(String failureType, String technology);

    /**
     * Records a deployment verification failure.
     *
     * @param reason failure description or classification
     */
    void recordVerificationFailure(String reason);

    /**
     * Records a MID Server communication or execution failure.
     *
     * @param targetType target host technology or node
     */
    void recordMidServerFailure(String targetType);

    /**
     * Records a deployment retry attempt.
     *
     * @param attemptNumber current attempt index
     */
    void recordRetryAttempt(int attemptNumber);

    /**
     * Records execution duration of a workflow phase or lifecycle stage.
     *
     * @param workflowName workflow identifier (e.g. "discovery", "matching", "planning", "execution", "retry", "next_day")
     * @param duration     duration elapsed
     */
    void recordWorkflowDuration(String workflowName, Duration duration);

    /**
     * Measures and records the execution duration of a runnable workflow task.
     *
     * @param workflowName workflow identifier
     * @param action       task to execute and time
     */
    void recordTimedWorkflow(String workflowName, Runnable action);

    /**
     * Measures and records the execution duration of a supplier workflow task.
     *
     * @param workflowName workflow identifier
     * @param supplier     task to execute and time
     * @param <T>          return type
     * @return task output
     */
    <T> T recordTimedWorkflow(String workflowName, Supplier<T> supplier);
}
