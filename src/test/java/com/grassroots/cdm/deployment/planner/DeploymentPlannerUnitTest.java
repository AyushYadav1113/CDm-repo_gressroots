package com.grassroots.cdm.deployment.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.planner.config.DeploymentPlannerProperties;
import com.grassroots.cdm.deployment.planner.exception.AmbiguousMatchException;
import com.grassroots.cdm.deployment.planner.exception.DeploymentAlreadyCompletedException;
import com.grassroots.cdm.deployment.planner.exception.DuplicateDeploymentJobException;
import com.grassroots.cdm.deployment.planner.exception.MissingMidServerException;
import com.grassroots.cdm.deployment.planner.exception.MissingServerException;
import com.grassroots.cdm.deployment.planner.exception.NoMatchException;
import com.grassroots.cdm.deployment.planner.exception.UnsupportedTechnologyException;
import com.grassroots.cdm.deployment.planner.generator.IdempotencyKeyGenerator;
import com.grassroots.cdm.deployment.planner.impl.DefaultDeploymentPlanner;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
import com.grassroots.cdm.deployment.planner.rules.DeploymentTypeResolver;
import com.grassroots.cdm.deployment.planner.rules.MidServerValidator;
import com.grassroots.cdm.deployment.planner.rules.PriorityCalculator;
import com.grassroots.cdm.deployment.planner.rules.TargetServerValidator;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.JobPriority;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentPlanner Unit Tests")
class DeploymentPlannerUnitTest {

    @Mock
    private CertificateReplacementRepository replacementRepository;

    @Mock
    private CertificateInstallationRepository installationRepository;

    @Mock
    private DeploymentJobRepository deploymentJobRepository;

    @Mock
    private AuditService auditService;

    private DeploymentPlannerProperties properties;
    private DeploymentTypeResolver deploymentTypeResolver;
    private PriorityCalculator priorityCalculator;
    private TargetServerValidator targetServerValidator;
    private MidServerValidator midServerValidator;
    private ObjectMapper objectMapper;

    private DefaultDeploymentPlanner planner;

    @BeforeEach
    void setUp() {
        properties = new DeploymentPlannerProperties();
        deploymentTypeResolver = new DeploymentTypeResolver();
        priorityCalculator = new PriorityCalculator(properties);
        targetServerValidator = new TargetServerValidator();
        midServerValidator = new MidServerValidator(properties);
        objectMapper = new ObjectMapper();

        planner = new DefaultDeploymentPlanner(
                replacementRepository,
                installationRepository,
                deploymentJobRepository,
                auditService,
                deploymentTypeResolver,
                priorityCalculator,
                targetServerValidator,
                midServerValidator,
                properties,
                objectMapper
        );
    }

    // =========================================================================
    // HELPER BUILDERS
    // =========================================================================

    private CertificateRecord buildCertificate(String cn, String thumbprint, Instant validTo) {
        CertificateRecord cert = new CertificateRecord();
        cert.setId(UUID.randomUUID());
        cert.setCommonName(cn);
        cert.setThumbprint(thumbprint);
        cert.setFingerprintSha256(thumbprint);
        cert.setValidFrom(Instant.now().minus(300, ChronoUnit.DAYS));
        cert.setValidTo(validTo);
        return cert;
    }

    private MidServer buildMidServer(String name, MidServerStatus status) {
        MidServer midServer = new MidServer();
        midServer.setId(UUID.randomUUID());
        midServer.setName(name);
        midServer.setEndpoint("https://" + name + ".internal:443/api");
        midServer.setStatus(status);
        return midServer;
    }

    private TargetServer buildServer(String hostname, ServerTechnology tech, ServerOperatingSystem os,
                                    EnvironmentType env, ServerStatus status, MidServer midServer) {
        TargetServer server = new TargetServer();
        server.setId(UUID.randomUUID());
        server.setHostname(hostname);
        server.setTechnology(tech);
        server.setOperatingSystem(os);
        server.setEnvironment(env);
        server.setStatus(status);
        server.setMidServer(midServer);
        return server;
    }

    private CertificateInstallation buildInstallation(CertificateRecord cert, TargetServer server,
                                                     ServerTechnology tech, String binding, int port) {
        CertificateInstallation inst = new CertificateInstallation();
        inst.setId(UUID.randomUUID());
        inst.setCertificate(cert);
        inst.setServer(server);
        inst.setTechnology(tech);
        inst.setBindingInfo(binding);
        inst.setPort(port);
        inst.setStatus(InstallationStatus.INSTALLED);
        return inst;
    }

    private CertificateReplacement buildReplacement(CertificateRecord oldCert, CertificateRecord newCert,
                                                    MatchStatus matchStatus, double score) {
        CertificateReplacement repl = new CertificateReplacement();
        repl.setId(UUID.randomUUID());
        repl.setOldCertificate(oldCert);
        repl.setNewCertificate(newCert);
        repl.setMatchStatus(matchStatus);
        repl.setMatchingScore(score);
        repl.setMatchingReasons("[]");
        return repl;
    }

    // =========================================================================
    // 1. IIS / WINDOWS
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Plan deployment for Windows / IIS creates job with DeploymentType.IIS")
    void testIisDeploymentPlanning() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB-IIS", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB-IIS", Instant.now().plus(375, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-CORP-WIN-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-iis-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "Default Web Site", 443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.98);

        when(deploymentJobRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(deploymentJobRepository.findByInstallationIdAndNewCertificateId(any(), any())).thenReturn(Optional.empty());
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        DeploymentJob job = planner.planDeploymentForInstallation(replacement, installation);

        assertThat(job).isNotNull();
        assertThat(job.getDeploymentType()).isEqualTo(DeploymentType.IIS);
        assertThat(job.getTargetType()).isEqualTo("IIS");
        assertThat(job.getTargetHost()).isEqualTo("web-iis-01.grassroots.internal");
        assertThat(job.getTargetPort()).isEqualTo(443);
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.PENDING);
        assertThat(job.getOldCertificate()).isEqualTo(oldCert);
        assertThat(job.getNewCertificate()).isEqualTo(newCert);
        assertThat(job.getIdempotencyKey()).startsWith("DEP:web-iis-01.grassroots.internal:443:");
        assertThat(job.getJobReference()).startsWith("JOB-");
        assertThat(job.getCreationReason()).contains("Automated renewal deployment planned for installation");
        assertThat(job.getCreationReason()).contains("IIS");
        assertThat(job.getPriority()).isEqualTo(JobPriority.HIGH); // 10 days remaining <= 15 days

        verify(deploymentJobRepository).saveAndFlush(any(DeploymentJob.class));
        verify(auditService).recordAudit(any());
    }

    // =========================================================================
    // 2. APACHE / LINUX
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Plan deployment for Linux / Apache creates job with DeploymentType.APACHE")
    void testApacheDeploymentPlanning() {
        CertificateRecord oldCert = buildCertificate("secure.grassroots.internal", "OLD-THUMB-APA", Instant.now().plus(20, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("secure.grassroots.internal", "NEW-THUMB-APA", Instant.now().plus(380, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-CORP-LNX-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-apache-01.grassroots.internal", ServerTechnology.APACHE,
                ServerOperatingSystem.LINUX_RHEL, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.APACHE, "/etc/httpd/conf.d/ssl.conf", 443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        when(deploymentJobRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(deploymentJobRepository.findByInstallationIdAndNewCertificateId(any(), any())).thenReturn(Optional.empty());
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        DeploymentJob job = planner.planDeploymentForInstallation(replacement, installation);

        assertThat(job).isNotNull();
        assertThat(job.getDeploymentType()).isEqualTo(DeploymentType.APACHE);
        assertThat(job.getTargetType()).isEqualTo("APACHE");
        assertThat(job.getTargetHost()).isEqualTo("web-apache-01.grassroots.internal");
        assertThat(job.getPriority()).isEqualTo(JobPriority.HIGH); // Production tier
    }

    // =========================================================================
    // 3. NGINX / LINUX
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Plan deployment for Linux / Nginx creates job with DeploymentType.NGINX")
    void testNginxDeploymentPlanning() {
        CertificateRecord oldCert = buildCertificate("gateway.grassroots.internal", "OLD-THUMB-NGX", Instant.now().plus(5, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("gateway.grassroots.internal", "NEW-THUMB-NGX", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-CORP-LNX-02", MidServerStatus.UP);
        TargetServer server = buildServer("gw-nginx-01.grassroots.internal", ServerTechnology.NGINX,
                ServerOperatingSystem.LINUX_UBUNTU, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.NGINX, "/etc/nginx/sites-available/default", 8443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 1.0);

        when(deploymentJobRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(deploymentJobRepository.findByInstallationIdAndNewCertificateId(any(), any())).thenReturn(Optional.empty());
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        DeploymentJob job = planner.planDeploymentForInstallation(replacement, installation);

        assertThat(job).isNotNull();
        assertThat(job.getDeploymentType()).isEqualTo(DeploymentType.NGINX);
        assertThat(job.getTargetType()).isEqualTo("NGINX");
        assertThat(job.getTargetPort()).isEqualTo(8443);
        assertThat(job.getPriority()).isEqualTo(JobPriority.CRITICAL); // 5 days remaining <= 7 days
    }

    // =========================================================================
    // 4. JAVA / KEYSTORE
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Plan deployment for Java Keystore/Tomcat creates job with DeploymentType.JAVA")
    void testJavaDeploymentPlanning() {
        CertificateRecord oldCert = buildCertificate("auth.grassroots.internal", "OLD-THUMB-JAVA", Instant.now().plus(40, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("auth.grassroots.internal", "NEW-THUMB-JAVA", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-CORP-LNX-01", MidServerStatus.UP);
        TargetServer server = buildServer("auth-app-01.grassroots.internal", ServerTechnology.JAVA_KEYSTORE,
                ServerOperatingSystem.LINUX_RHEL, EnvironmentType.STAGING, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.JAVA_KEYSTORE, "app-alias", 8443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.92);

        when(deploymentJobRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(deploymentJobRepository.findByInstallationIdAndNewCertificateId(any(), any())).thenReturn(Optional.empty());
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        DeploymentJob job = planner.planDeploymentForInstallation(replacement, installation);

        assertThat(job).isNotNull();
        assertThat(job.getDeploymentType()).isEqualTo(DeploymentType.JAVA);
        assertThat(job.getTargetType()).isEqualTo("JAVA");
        assertThat(job.getPriority()).isEqualTo(JobPriority.NORMAL); // Staging environment
    }

    // =========================================================================
    // 5. MISSING SERVER
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: Target server is missing or decommissioned throws MissingServerException")
    void testMissingServerThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        // Subcase A: Null server
        CertificateInstallation installWithNullServer = new CertificateInstallation();
        installWithNullServer.setId(UUID.randomUUID());
        installWithNullServer.setCertificate(oldCert);
        installWithNullServer.setServer(null);

        assertThrows(MissingServerException.class, () ->
                planner.planDeploymentForInstallation(replacement, installWithNullServer)
        );

        // Subcase B: Decommissioned server
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer decommissionedServer = buildServer("decom-01.grassroots.internal", ServerTechnology.APACHE,
                ServerOperatingSystem.LINUX_RHEL, EnvironmentType.PRODUCTION, ServerStatus.DECOMMISSIONED, midServer);
        CertificateInstallation installWithDecomServer = buildInstallation(oldCert, decommissionedServer, ServerTechnology.APACHE, "default", 443);

        MissingServerException ex = assertThrows(MissingServerException.class, () ->
                planner.planDeploymentForInstallation(replacement, installWithDecomServer)
        );
        assertThat(ex.getMessage()).contains("DECOMMISSIONED");
    }

    // =========================================================================
    // 6. MISSING MID SERVER
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Missing or non-operational MID Server throws MissingMidServerException")
    void testMissingMidServerThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        // Subcase A: Null MID server
        TargetServer serverWithoutMid = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, null);
        CertificateInstallation installNoMid = buildInstallation(oldCert, serverWithoutMid, ServerTechnology.IIS, "default", 443);

        MissingMidServerException ex1 = assertThrows(MissingMidServerException.class, () ->
                planner.planDeploymentForInstallation(replacement, installNoMid)
        );
        assertThat(ex1.getMessage()).contains("has no assigned MID Server");

        // Subcase B: MID Server is DOWN
        MidServer downMidServer = buildMidServer("MID-DOWN-01", MidServerStatus.DOWN);
        TargetServer serverWithDownMid = buildServer("web-02.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, downMidServer);
        CertificateInstallation installDownMid = buildInstallation(oldCert, serverWithDownMid, ServerTechnology.IIS, "default", 443);

        MissingMidServerException ex2 = assertThrows(MissingMidServerException.class, () ->
                planner.planDeploymentForInstallation(replacement, installDownMid)
        );
        assertThat(ex2.getMessage()).contains("is not operational");
    }

    // =========================================================================
    // 7. DUPLICATE JOB
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Duplicate pending/active deployment job throws DuplicateDeploymentJobException")
    void testDuplicateJobThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "default", 443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        String idempotencyKey = IdempotencyKeyGenerator.generateKey(oldCert, newCert, installation);

        DeploymentJob existingJob = new DeploymentJob();
        existingJob.setId(UUID.randomUUID());
        existingJob.setJobReference("JOB-20260927-EXISTING");
        existingJob.setIdempotencyKey(idempotencyKey);
        existingJob.setStatus(DeploymentJobStatus.PENDING);

        when(deploymentJobRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingJob));

        DuplicateDeploymentJobException ex = assertThrows(DuplicateDeploymentJobException.class, () ->
                planner.planDeploymentForInstallation(replacement, installation)
        );
        assertThat(ex.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(ex.getExistingJobId()).isEqualTo(existingJob.getId());
        verify(deploymentJobRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // 8. AMBIGUOUS CERTIFICATE MATCH
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Ambiguous match (PENDING_REVIEW) is prevented from auto-deploying")
    void testAmbiguousCertificateMatchThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "default", 443);

        // REVIEW_REQUIRED match status
        CertificateReplacement ambiguousReplacement = buildReplacement(oldCert, newCert, MatchStatus.PENDING_REVIEW, 0.75);

        AmbiguousMatchException ex = assertThrows(AmbiguousMatchException.class, () ->
                planner.planDeploymentForInstallation(ambiguousReplacement, installation)
        );
        assertThat(ex.getReplacementId()).isEqualTo(ambiguousReplacement.getId());
        assertThat(ex.getMessage()).contains("PENDING_REVIEW");

        verify(deploymentJobRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // 9. ALREADY COMPLETED DEPLOYMENT
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Deployment job already completed throws DeploymentAlreadyCompletedException")
    void testAlreadyCompletedDeploymentThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "default", 443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        String idempotencyKey = IdempotencyKeyGenerator.generateKey(oldCert, newCert, installation);

        DeploymentJob completedJob = new DeploymentJob();
        completedJob.setId(UUID.randomUUID());
        completedJob.setJobReference("JOB-20260927-DONE");
        completedJob.setIdempotencyKey(idempotencyKey);
        completedJob.setStatus(DeploymentJobStatus.COMPLETED);

        when(deploymentJobRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(completedJob));

        DeploymentAlreadyCompletedException ex = assertThrows(DeploymentAlreadyCompletedException.class, () ->
                planner.planDeploymentForInstallation(replacement, installation)
        );
        assertThat(ex.getExistingJobId()).isEqualTo(completedJob.getId());
        assertThat(ex.getIdempotencyKey()).isEqualTo(idempotencyKey);
        verify(deploymentJobRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // 10. UNSUPPORTED TECHNOLOGY
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Unsupported target technology throws UnsupportedTechnologyException")
    void testUnsupportedTechnologyThrowsException() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", null,
                ServerOperatingSystem.OTHER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, null, "default", 443);
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        UnsupportedTechnologyException ex = assertThrows(UnsupportedTechnologyException.class, () ->
                planner.planDeploymentForInstallation(replacement, installation)
        );
        assertThat(ex.getMessage()).contains("Target technology is null and cannot be resolved");
        verify(deploymentJobRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // 11. NO_MATCH REJECTION
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: NO_MATCH (null, rejected, superseded) refuses job creation")
    void testNoMatchRefusesJobCreation() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "default", 443);

        // Null replacement
        assertThrows(NoMatchException.class, () -> planner.planDeploymentForInstallation(null, installation));

        // REJECTED status
        CertificateReplacement rejected = buildReplacement(oldCert, newCert, MatchStatus.REJECTED, 0.20);
        assertThrows(NoMatchException.class, () -> planner.planDeploymentForInstallation(rejected, installation));

        // SUPERSEDED status
        CertificateReplacement superseded = buildReplacement(oldCert, newCert, MatchStatus.SUPERSEDED, 0.90);
        assertThrows(NoMatchException.class, () -> planner.planDeploymentForInstallation(superseded, installation));
    }

    // =========================================================================
    // 12. MULTI-INSTALLATION PLANNING & REJECTIONS COLLECTION
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: planDeployments evaluates all installations and aggregates planned jobs and rejections")
    void testPlanDeploymentsCollectsJobsAndRejections() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        CertificateReplacement replacement = buildReplacement(oldCert, newCert, MatchStatus.AUTO_MATCHED, 0.95);

        // Installation 1: Valid IIS server with UP MID server
        MidServer midServer1 = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server1 = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer1);
        CertificateInstallation install1 = buildInstallation(oldCert, server1, ServerTechnology.IIS, "Default Web Site", 443);

        // Installation 2: Apache server with DOWN MID server (will be rejected)
        MidServer midServer2 = buildMidServer("MID-DOWN", MidServerStatus.DOWN);
        TargetServer server2 = buildServer("web-02.grassroots.internal", ServerTechnology.APACHE,
                ServerOperatingSystem.LINUX_RHEL, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer2);
        CertificateInstallation install2 = buildInstallation(oldCert, server2, ServerTechnology.APACHE, "/etc/httpd/conf.d/ssl.conf", 443);

        when(installationRepository.findByCertificateId(oldCert.getId())).thenReturn(List.of(install1, install2));
        when(deploymentJobRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(deploymentJobRepository.findByInstallationIdAndNewCertificateId(any(), any())).thenReturn(Optional.empty());
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(UUID.randomUUID());
            }
            return j;
        });

        DeploymentPlanResult result = planner.planDeployments(replacement);

        assertThat(result).isNotNull();
        assertThat(result.totalInstallationsEvaluated()).isEqualTo(2);
        assertThat(result.plannedJobs()).hasSize(1);
        assertThat(result.plannedJobs().get(0).getTargetHost()).isEqualTo("web-01.grassroots.internal");
        assertThat(result.rejections()).hasSize(1);
        assertThat(result.rejections().get(0).serverHostname()).isEqualTo("web-02.grassroots.internal");
        assertThat(result.rejections().get(0).errorType()).isEqualTo("MissingMidServerException");
    }

    // =========================================================================
    // 13. DETERMINISTIC IDEMPOTENCY KEY REPEATABILITY
    // =========================================================================
    @Test
    @DisplayName("Scenario 13: IdempotencyKeyGenerator is 100% deterministic and collision-resistant")
    void testIdempotencyKeyRepeatability() {
        CertificateRecord oldCert = buildCertificate("api.grassroots.internal", "OLD-THUMB", Instant.now().plus(10, ChronoUnit.DAYS));
        CertificateRecord newCert = buildCertificate("api.grassroots.internal", "NEW-THUMB", Instant.now().plus(365, ChronoUnit.DAYS));
        MidServer midServer = buildMidServer("MID-01", MidServerStatus.UP);
        TargetServer server = buildServer("web-01.grassroots.internal", ServerTechnology.IIS,
                ServerOperatingSystem.WINDOWS_SERVER, EnvironmentType.PRODUCTION, ServerStatus.ACTIVE, midServer);
        CertificateInstallation installation = buildInstallation(oldCert, server, ServerTechnology.IIS, "Default Web Site", 443);

        String key1 = IdempotencyKeyGenerator.generateKey(oldCert, newCert, installation);
        String key2 = IdempotencyKeyGenerator.generateKey(oldCert, newCert, installation);

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.length()).isLessThanOrEqualTo(128);

        // Different installation port yields different key
        CertificateInstallation diffPortInstall = buildInstallation(oldCert, server, ServerTechnology.IIS, "Default Web Site", 8443);
        String diffKey = IdempotencyKeyGenerator.generateKey(oldCert, newCert, diffPortInstall);
        assertThat(diffKey).isNotEqualTo(key1);
    }
}
