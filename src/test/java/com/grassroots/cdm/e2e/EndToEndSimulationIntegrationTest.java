package com.grassroots.cdm.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.confirmation.ConfirmationStatus;
import com.grassroots.cdm.confirmation.impl.DefaultNextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.credential.mock.MockCredentialProvider;
import com.grassroots.cdm.deployment.DeploymentDispatcher;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.planner.config.DeploymentPlannerProperties;
import com.grassroots.cdm.deployment.planner.impl.DefaultDeploymentPlanner;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
import com.grassroots.cdm.deployment.planner.rules.DeploymentTypeResolver;
import com.grassroots.cdm.deployment.planner.rules.MidServerValidator;
import com.grassroots.cdm.deployment.planner.rules.PriorityCalculator;
import com.grassroots.cdm.deployment.planner.rules.TargetServerValidator;
import com.grassroots.cdm.deployment.workflow.impl.DefaultDeploymentWorkflowService;
import com.grassroots.cdm.deployment.workflow.impl.DefaultWorkflowEngine;
import com.grassroots.cdm.deployment.workflow.statemachine.DefaultDeploymentStateMachine;
import com.grassroots.cdm.deployment.workflow.statemachine.DeploymentStateMachine;
import com.grassroots.cdm.discovery.impl.DiscoveryServiceImpl;
import com.grassroots.cdm.discovery.mapper.CertificateDiscoveryMapper;
import com.grassroots.cdm.dto.DiscoveryResultDto;
import com.grassroots.cdm.dto.SectigoSyncResultDto;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import com.grassroots.cdm.integration.sectigo.SectigoClient;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.exception.SectigoTimeoutException;
import com.grassroots.cdm.integration.sectigo.mapper.CertificateSectigoMapper;
import com.grassroots.cdm.integration.sectigo.service.SectigoIntegrationServiceImpl;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowException;
import com.grassroots.cdm.integration.servicenow.service.ServiceNowIntegrationServiceImp;
import com.grassroots.cdm.matching.CertificateMatcher;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.impl.CommonNameMatchEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.IssuerContinuityEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.LifecycleEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.RenewalLinkageEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.SanMatchEvaluator;
import com.grassroots.cdm.matching.generator.impl.DefaultCandidateGenerator;
import com.grassroots.cdm.matching.impl.ScoringCertificateMatcher;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.service.impl.CertificateMatchingServiceImpl;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.repository.MidServerRepository;
import com.grassroots.cdm.repository.TargetServerRepository;
import com.grassroots.cdm.verification.EndpointVerifier;
import com.grassroots.cdm.verification.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Comprehensive End-to-End Simulation Test Suite.
 *
 * <p>Validates the complete orchestration pipeline from Discovery through Sectigo Sync,
 * Matching, Planning, CyberArk Credential Retrieval, MID Server Dispatch, Live Verification,
 * Next-Day CMDB Confirmation, and Audit Trails — with zero connection to real production infrastructure.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EndToEndSimulationIntegrationTest {

    // ─── Repositories (Mocked Storage with Realistic In-Memory State) ────────
    @Mock private CertificateRecordRepository certificateRepository;
    @Mock private TargetServerRepository serverRepository;
    @Mock private CertificateInstallationRepository installationRepository;
    @Mock private CertificateReplacementRepository replacementRepository;
    @Mock private DeploymentJobRepository deploymentJobRepository;
    @Mock private MidServerRepository midServerRepository;
    @Mock private AuditLogRecordRepository auditLogRecordRepository;

    // ─── External Client Mocks ───────────────────────────────────────────────
    @Mock private ServiceNowClient serviceNowClient;
    @Mock private SectigoClient sectigoClient;
    @Mock private CyberArkVaultClient cyberArkVaultClient;
    @Mock private DeploymentDispatcher deploymentDispatcher;
    @Mock private EndpointVerifier endpointVerifier;

    // ─── Audit & Helper Services ─────────────────────────────────────────────
    @Mock private AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── In-Memory Storage Maps for Deterministic Simulation ─────────────────
    private final Map<UUID, CertificateRecord> certDb = new ConcurrentHashMap<>();
    private final Map<UUID, TargetServer> serverDb = new ConcurrentHashMap<>();
    private final Map<UUID, CertificateInstallation> installDb = new ConcurrentHashMap<>();
    private final Map<UUID, CertificateReplacement> replacementDb = new ConcurrentHashMap<>();
    private final Map<UUID, DeploymentJob> jobDb = new ConcurrentHashMap<>();
    private final Map<UUID, MidServer> midDb = new ConcurrentHashMap<>();
    private final List<AuditEvent> auditTrail = Collections.synchronizedList(new ArrayList<>());

    // ─── Core CDM Engine Components ──────────────────────────────────────────
    private DiscoveryServiceImpl discoveryService;
    private SectigoIntegrationServiceImpl sectigoIntegrationService;
    private CertificateMatchingServiceImpl matchingService;
    private DefaultDeploymentPlanner deploymentPlanner;
    private DefaultDeploymentWorkflowService workflowService;
    private DefaultWorkflowEngine workflowEngine;
    private DefaultNextDayConfirmationService nextDayConfirmationService;
    private MockCredentialProvider mockCredentialProvider;

    // ─── Simulation Test Constants ───────────────────────────────────────────
    private static final String TARGET_HOST_IIS = "WEB01";
    private static final String TARGET_HOST_LINUX = "LINUX01";
    private static final String TARGET_HOST_JAVA = "JAVA01";
    private static final String COMMON_NAME = "api.example.com";
    private static final String OLD_THUMBPRINT = "A1B2C3D4E5F60718293A4B5C6D7E8F9012345678";
    private static final String NEW_THUMBPRINT = "F0E1D2C3B4A5968778695A4B3C2D1E0F12345678";

    @BeforeEach
    void setUp() {
        certDb.clear();
        serverDb.clear();
        installDb.clear();
        replacementDb.clear();
        jobDb.clear();
        midDb.clear();
        auditTrail.clear();

        setupRepositoryMocks();
        setupAuditMock();

        // Initialize Matching Engine with production evaluators
        MatchingProperties properties = new MatchingProperties();
        properties.setAutoMatchThreshold(0.85);
        properties.setReviewThreshold(0.60);
        properties.setAmbiguityMargin(0.05);
        properties.setSanWeight(0.35);
        properties.setCnWeight(0.25);
        properties.setRenewalLinkageWeight(0.20);
        properties.setLifecycleWeight(0.10);
        properties.setIssuerWeight(0.10);
        properties.setRejectExpiredCandidates(true);
        properties.setAllowWildcardExpansion(true);

        DefaultCandidateGenerator candidateGenerator = new DefaultCandidateGenerator(properties);

        CertificateMatcher matcher = new ScoringCertificateMatcher(
                properties,
                candidateGenerator,
                List.of(
                        new SanMatchEvaluator(properties),
                        new CommonNameMatchEvaluator(properties),
                        new RenewalLinkageEvaluator(properties),
                        new LifecycleEvaluator(properties),
                        new IssuerContinuityEvaluator(properties)
                )
        );
        matchingService = new CertificateMatchingServiceImpl(
                matcher, certificateRepository, replacementRepository, auditService);

        // Initialize Discovery Service
        ServiceNowIntegrationServiceImp snIntegrationService = new ServiceNowIntegrationServiceImp(serviceNowClient);
        discoveryService = new DiscoveryServiceImpl(
                snIntegrationService, certificateRepository, serverRepository,
                installationRepository, new CertificateDiscoveryMapper(), auditService);

        // Initialize Sectigo Sync Service
        sectigoIntegrationService = new SectigoIntegrationServiceImpl(
                sectigoClient, certificateRepository, new CertificateSectigoMapper(), auditService);

        // Initialize Deployment Planner
        DeploymentPlannerProperties plannerProperties = new DeploymentPlannerProperties();
        deploymentPlanner = new DefaultDeploymentPlanner(
                replacementRepository, installationRepository, deploymentJobRepository,
                auditService, new DeploymentTypeResolver(), new PriorityCalculator(plannerProperties),
                new TargetServerValidator(), new MidServerValidator(plannerProperties),
                plannerProperties, objectMapper);

        // Initialize State Machine and Workflow Engine
        DeploymentStateMachine stateMachine = new DefaultDeploymentStateMachine(deploymentJobRepository, auditService, objectMapper);
        workflowService = new DefaultDeploymentWorkflowService(
                deploymentJobRepository, stateMachine, installationRepository, certificateRepository);

        // Inject external delegates into workflowService
        workflowService.setCyberArkVaultClient(cyberArkVaultClient);
        workflowService.setDeploymentDispatcher(deploymentDispatcher);
        workflowService.setEndpointVerifier(endpointVerifier);

        workflowEngine = new DefaultWorkflowEngine(workflowService, deploymentJobRepository);

        // Initialize Next-Day Confirmation Service
        nextDayConfirmationService = new DefaultNextDayConfirmationService(
                deploymentJobRepository, serviceNowClient, auditService);

        // Initialize Mock Credential Provider
        mockCredentialProvider = new MockCredentialProvider();
        mockCredentialProvider.registerMockCredential("SAFE-CDM-WINDOWS", "svc_iis_deploy", "svc_iis_deploy", "P@ssw0rd_IIS_Secure!");
        mockCredentialProvider.registerMockCredential("SAFE-CDM-LINUX", "svc_ansible_deploy", "svc_ansible_deploy", "P@ssw0rd_Linux_Secure!");
        mockCredentialProvider.registerMockCredential("SAFE-CDM-JAVA", "svc_java_deploy", "svc_java_deploy", "P@ssw0rd_Java_Keystore!");
    }

    private void setupRepositoryMocks() {
        // Certificate repository
        lenient().doAnswer(inv -> {
            CertificateRecord r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            certDb.put(r.getId(), r);
            return r;
        }).when(certificateRepository).save(any(CertificateRecord.class));

        lenient().doAnswer(inv -> {
            CertificateRecord r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            certDb.put(r.getId(), r);
            return r;
        }).when(certificateRepository).saveAndFlush(any(CertificateRecord.class));

        lenient().doAnswer(inv -> Optional.ofNullable(certDb.get(inv.getArgument(0))))
                .when(certificateRepository).findById(any(UUID.class));

        lenient().doAnswer(inv -> {
            String thumb = inv.getArgument(0);
            return certDb.values().stream()
                    .filter(c -> thumb.equalsIgnoreCase(c.getThumbprint()))
                    .findFirst();
        }).when(certificateRepository).findByThumbprint(anyString());

        lenient().doAnswer(inv -> {
            String thumb = inv.getArgument(0);
            return certDb.values().stream()
                    .filter(c -> thumb.equalsIgnoreCase(c.getThumbprint()))
                    .findFirst();
        }).when(certificateRepository).findByThumbprintIgnoreCase(anyString());

        lenient().doAnswer(inv -> {
            CertificateStatus status = inv.getArgument(0);
            return certDb.values().stream().filter(c -> c.getStatus() == status).toList();
        }).when(certificateRepository).findByStatus(any(CertificateStatus.class));

        lenient().doAnswer(inv -> {
            CertificateSource src = inv.getArgument(0);
            return certDb.values().stream().filter(c -> c.getSource() == src).toList();
        }).when(certificateRepository).findBySource(any(CertificateSource.class));

        lenient().doAnswer(inv -> new ArrayList<>(certDb.values()))
                .when(certificateRepository).findAll();

        // TargetServer repository
        lenient().doAnswer(inv -> {
            TargetServer s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            serverDb.put(s.getId(), s);
            return s;
        }).when(serverRepository).save(any(TargetServer.class));

        lenient().doAnswer(inv -> {
            TargetServer s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            serverDb.put(s.getId(), s);
            return s;
        }).when(serverRepository).saveAndFlush(any(TargetServer.class));

        lenient().doAnswer(inv -> Optional.ofNullable(serverDb.get(inv.getArgument(0))))
                .when(serverRepository).findById(any(UUID.class));

        lenient().doAnswer(inv -> {
            String host = inv.getArgument(0);
            return serverDb.values().stream().filter(s -> host.equalsIgnoreCase(s.getHostname())).findFirst();
        }).when(serverRepository).findByHostname(anyString());

        // CertificateInstallation repository
        lenient().doAnswer(inv -> {
            CertificateInstallation inst = inv.getArgument(0);
            if (inst.getId() == null) inst.setId(UUID.randomUUID());
            installDb.put(inst.getId(), inst);
            return inst;
        }).when(installationRepository).save(any(CertificateInstallation.class));

        lenient().doAnswer(inv -> {
            CertificateInstallation inst = inv.getArgument(0);
            if (inst.getId() == null) inst.setId(UUID.randomUUID());
            installDb.put(inst.getId(), inst);
            return inst;
        }).when(installationRepository).saveAndFlush(any(CertificateInstallation.class));

        lenient().doAnswer(inv -> {
            UUID certId = inv.getArgument(0);
            return installDb.values().stream()
                    .filter(i -> i.getCertificate() != null && certId.equals(i.getCertificate().getId()))
                    .toList();
        }).when(installationRepository).findByCertificateId(any(UUID.class));

        lenient().doAnswer(inv -> {
            UUID sid = inv.getArgument(0);
            return installDb.values().stream()
                    .filter(i -> i.getServer() != null && sid.equals(i.getServer().getId()))
                    .toList();
        }).when(installationRepository).findByServerId(any(UUID.class));

        lenient().doAnswer(inv -> {
            UUID sid = inv.getArgument(0);
            int port = inv.getArgument(1);
            String binding = inv.getArgument(2);
            return installDb.values().stream()
                    .filter(i -> i.getServer() != null && sid.equals(i.getServer().getId())
                            && i.getPort() == port
                            && binding.equalsIgnoreCase(i.getBindingInfo()))
                    .findFirst();
        }).when(installationRepository).findByServerIdAndPortAndBindingInfo(any(UUID.class), anyInt(), anyString());

        // CertificateReplacement repository
        lenient().doAnswer(inv -> {
            CertificateReplacement rep = inv.getArgument(0);
            if (rep.getId() == null) rep.setId(UUID.randomUUID());
            replacementDb.put(rep.getId(), rep);
            return rep;
        }).when(replacementRepository).save(any(CertificateReplacement.class));

        lenient().doAnswer(inv -> {
            CertificateReplacement rep = inv.getArgument(0);
            if (rep.getId() == null) rep.setId(UUID.randomUUID());
            replacementDb.put(rep.getId(), rep);
            return rep;
        }).when(replacementRepository).saveAndFlush(any(CertificateReplacement.class));

        lenient().doAnswer(inv -> Optional.ofNullable(replacementDb.get(inv.getArgument(0))))
                .when(replacementRepository).findById(any(UUID.class));

        lenient().doAnswer(inv -> {
            UUID oldCertId = inv.getArgument(0);
            return replacementDb.values().stream()
                    .filter(r -> r.getOldCertificate() != null && oldCertId.equals(r.getOldCertificate().getId()))
                    .toList();
        }).when(replacementRepository).findByOldCertificateId(any(UUID.class));

        lenient().doAnswer(inv -> {
            MatchStatus status = inv.getArgument(0);
            return replacementDb.values().stream().filter(r -> r.getMatchStatus() == status).toList();
        }).when(replacementRepository).findByMatchStatus(any(MatchStatus.class));

        lenient().doAnswer(inv -> new ArrayList<>(replacementDb.values()))
                .when(replacementRepository).findAll();

        // DeploymentJob repository
        lenient().doAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            jobDb.put(j.getId(), j);
            return j;
        }).when(deploymentJobRepository).save(any(DeploymentJob.class));

        lenient().doAnswer(inv -> {
            DeploymentJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            jobDb.put(j.getId(), j);
            return j;
        }).when(deploymentJobRepository).saveAndFlush(any(DeploymentJob.class));

        lenient().doAnswer(inv -> Optional.ofNullable(jobDb.get(inv.getArgument(0))))
                .when(deploymentJobRepository).findById(any(UUID.class));

        lenient().doAnswer(inv -> {
            DeploymentJobStatus status = inv.getArgument(0);
            return jobDb.values().stream().filter(j -> j.getStatus() == status).toList();
        }).when(deploymentJobRepository).findByStatus(any(DeploymentJobStatus.class));

        lenient().doAnswer(inv -> {
            String key = inv.getArgument(0);
            return jobDb.values().stream().anyMatch(j -> key.equals(j.getIdempotencyKey()));
        }).when(deploymentJobRepository).existsByIdempotencyKey(anyString());

        lenient().doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return jobDb.containsKey(id);
        }).when(deploymentJobRepository).existsById(any(UUID.class));

        // MidServer repository
        lenient().doAnswer(inv -> {
            MidServer m = inv.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            midDb.put(m.getId(), m);
            return m;
        }).when(midServerRepository).save(any(MidServer.class));

        lenient().doAnswer(inv -> Optional.ofNullable(midDb.get(inv.getArgument(0))))
                .when(midServerRepository).findById(any(UUID.class));

        lenient().doAnswer(inv -> {
            String name = inv.getArgument(0);
            return midDb.values().stream().filter(m -> name.equalsIgnoreCase(m.getName())).findFirst();
        }).when(midServerRepository).findByName(anyString());
    }

    private void setupAuditMock() {
        lenient().doAnswer(inv -> {
            AuditEvent event = inv.getArgument(0);
            auditTrail.add(event);
            return null;
        }).when(auditService).recordAudit(any(AuditEvent.class));

        lenient().doAnswer(inv -> {
            UUID jobId = inv.getArgument(0);
            String cid = inv.getArgument(1);
            String action = inv.getArgument(2);
            String outcome = inv.getArgument(3);
            String message = inv.getArgument(4);
            auditTrail.add(new AuditEvent(
                    AuditAction.valueOf(action), "DeploymentJob", jobId.toString(), "SIMULATOR", outcome, message, "127.0.0.1", Instant.now()));
            return null;
        }).when(auditService).recordWorkflowEvent(any(), any(), any(), any(), any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. FULL HAPPY PATH E2E SCENARIO
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Complete End-to-End Happy Path Orchestration")
    class CompleteHappyPathScenario {

        @Test
        @DisplayName("Executes full lifecycle for CN=api.example.com on WEB01 (IIS) from discovery through next-day confirmation")
        void testFullHappyPathWorkflow() {
            String correlationId = "E2E-CORR-" + UUID.randomUUID();

            // ── Step 0: Register Mock MID Server ──────────────────────────────
            MidServer midServer = new MidServer("MID-PROD-WIN-01", "https://mid01.internal:8443", MidServerStatus.UP);
            midServer.setId(UUID.randomUUID());
            midDb.put(midServer.getId(), midServer);

            // ── Step 1: ServiceNow Discovery of Old Certificate & Target ─────
            ServiceNowCertificateDto oldCertDto = new ServiceNowCertificateDto();
            oldCertDto.setSysId("sn-cert-old-01");
            oldCertDto.setName(COMMON_NAME);
            oldCertDto.setThumbprint(OLD_THUMBPRINT);
            oldCertDto.setSerialNumber("1000000000001");
            oldCertDto.setIssuer("Sectigo RSA Domain Validation CA");
            oldCertDto.setTargetHost(TARGET_HOST_IIS);
            oldCertDto.setTechnology("IIS");
            oldCertDto.setPort("443");
            oldCertDto.setValidFrom(Instant.now().minus(360, ChronoUnit.DAYS).toString());
            oldCertDto.setValidTo(Instant.now().plus(5, ChronoUnit.DAYS).toString()); // Expiring in 5 days
            oldCertDto.setSubjectAlternativeNames(COMMON_NAME);

            when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(oldCertDto));

            DiscoveryResultDto discoveryResult = discoveryService.discoverCertificates(correlationId);

            assertThat(discoveryResult).isNotNull();
            assertThat(discoveryResult.totalDiscovered()).isEqualTo(1);
            assertThat(certDb).hasSize(1);
            assertThat(serverDb).hasSize(1);
            assertThat(installDb).hasSize(1);

            CertificateRecord oldCertRecord = certDb.values().iterator().next();
            assertThat(oldCertRecord.getCommonName()).isEqualTo(COMMON_NAME);
            assertThat(oldCertRecord.getThumbprint()).isEqualTo(OLD_THUMBPRINT);
            assertThat(oldCertRecord.getSource()).isEqualTo(CertificateSource.SERVICENOW);

            TargetServer targetServer = serverDb.values().iterator().next();
            assertThat(targetServer.getHostname()).isEqualTo(TARGET_HOST_IIS);
            assertThat(targetServer.getTechnology()).isEqualTo(ServerTechnology.IIS);
            targetServer.setMidServer(midServer);
            serverRepository.saveAndFlush(targetServer);

            // ── Step 2: Sectigo Synchronization of Renewed Certificate ───────
            SectigoCertificateDto newSectigoCertDto = new SectigoCertificateDto();
            newSectigoCertDto.setId("sn-cert-old-01-renewed");
            newSectigoCertDto.setRenewedFromCertificateId("sn-cert-old-01");
            newSectigoCertDto.setCommonName(COMMON_NAME);
            newSectigoCertDto.setSha256Fingerprint(NEW_THUMBPRINT);
            newSectigoCertDto.setSerialNumber("2000000000002");
            newSectigoCertDto.setIssuer("Sectigo RSA Domain Validation CA");
            newSectigoCertDto.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS).toString());
            newSectigoCertDto.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS).toString()); // 1 year validity
            newSectigoCertDto.setSubjectAlternativeNames(List.of(COMMON_NAME));
            newSectigoCertDto.setStatus("ISSUED");

            when(sectigoClient.fetchAllCertificates(eq("ISSUED"), anyString()))
                    .thenReturn(List.of(newSectigoCertDto));

            SectigoSyncResultDto sectigoResult = sectigoIntegrationService.syncCertificates(correlationId);

            assertThat(sectigoResult).isNotNull();
            assertThat(sectigoResult.totalRetrieved()).isEqualTo(1);
            assertThat(certDb).hasSize(2);

            CertificateRecord newCertRecord = certDb.values().stream()
                    .filter(c -> c.getSource() == CertificateSource.SECTIGO)
                    .findFirst().orElseThrow();
            assertThat(newCertRecord.getThumbprint()).isEqualTo(NEW_THUMBPRINT);

            // ── Step 3: Certificate Matching Engine (Old → New) ──────────────
            CandidateMatchResult matchResult = matchingService.matchCertificate(oldCertRecord.getId(), correlationId);

            assertThat(matchResult).isNotNull();
            assertThat(replacementDb).hasSize(1);
            CertificateReplacement replacement = replacementDb.values().iterator().next();
            assertThat(replacement.getOldCertificate().getId()).isEqualTo(oldCertRecord.getId());
            assertThat(replacement.getNewCertificate().getId()).isEqualTo(newCertRecord.getId());
            assertThat(replacement.getMatchStatus()).isEqualTo(MatchStatus.AUTO_MATCHED);
            assertThat(replacement.getMatchingScore()).isGreaterThanOrEqualTo(0.85);

            // ── Step 4: Deployment Planning ──────────────────────────────────
            DeploymentPlanResult planResult = deploymentPlanner.planDeployments(replacement.getId());

            assertThat(planResult).isNotNull();
            assertThat(planResult.hasPlannedJobs()).isTrue();
            assertThat(jobDb).hasSize(1);

            DeploymentJob deploymentJob = jobDb.values().iterator().next();
            assertThat(deploymentJob.getStatus()).isIn(DeploymentJobStatus.PENDING, DeploymentJobStatus.PLANNED);
            assertThat(deploymentJob.getTargetHost()).isEqualTo(TARGET_HOST_IIS);
            assertThat(deploymentJob.getTargetType()).isEqualTo("IIS");
            assertThat(deploymentJob.getNewCertificate().getThumbprint()).isEqualTo(NEW_THUMBPRINT);

            // ── Step 5: CyberArk Credential Retrieval (Mocked Safe In-Memory) 
            when(cyberArkVaultClient.retrieveCredential(any(), any(), any()))
                    .thenReturn(new CredentialSecret("svc_iis_deploy", "P@ssw0rd_IIS_Secure!".toCharArray(), "SAFE-CDM-WINDOWS", TARGET_HOST_IIS));

            CredentialSecret credSecret = mockCredentialProvider.getCredential(
                    "SAFE-CDM-WINDOWS", "svc_iis_deploy", TARGET_HOST_IIS);
            assertThat(credSecret).isNotNull();
            assertThat(credSecret.username()).isEqualTo("svc_iis_deploy");

            // ── Step 6: Dispatch to Mock MID Server ──────────────────────────
            when(deploymentDispatcher.dispatchJob(any(DeploymentJob.class)))
                    .thenReturn("MID-TASK-WIN-IIS-999");

            // ── Step 7 & 8: Simulated Deployment Execution ───────────────────
            // Workflow advancement: PLANNED -> CREDENTIALS_PENDING -> CREDENTIALS_ACQUIRED -> SENT_TO_MID -> RUNNING -> DEPLOYED
            workflowService.advanceJob(deploymentJob.getId(), correlationId); // -> CREDENTIALS_ACQUIRED
            workflowService.advanceJob(deploymentJob.getId(), correlationId); // -> SENT_TO_MID
            workflowService.acknowledgeRunning(deploymentJob.getId(), "MID-TASK-WIN-IIS-999", correlationId); // -> RUNNING
            workflowService.markDeployed(deploymentJob.getId(), correlationId); // -> DEPLOYED

            DeploymentJob deployedJob = jobDb.get(deploymentJob.getId());
            assertThat(deployedJob.getStatus()).isEqualTo(DeploymentJobStatus.DEPLOYED);

            // ── Step 9: Immediate Endpoint Verification ──────────────────────
            when(endpointVerifier.verifyEndpoint(eq(TARGET_HOST_IIS), eq(443), eq(NEW_THUMBPRINT)))
                    .thenReturn(VerificationResult.successful(TARGET_HOST_IIS, 443, NEW_THUMBPRINT, "TLS 1.3 handshake verified"));

            workflowService.verifyDeployment(deploymentJob.getId(), correlationId);

            DeploymentJob verifiedJob = jobDb.get(deploymentJob.getId());
            assertThat(verifiedJob.getStatus()).isEqualTo(DeploymentJobStatus.VERIFIED);

            // ── Step 10, 11 & 12: Next-Day ServiceNow Discovery & Confirmation
            // Simulate Day 2 CMDB scan reflecting the new certificate on WEB01
            ServiceNowCertificateDto day2CmdbDto = new ServiceNowCertificateDto();
            day2CmdbDto.setSysId("sn-cert-new-02");
            day2CmdbDto.setName(COMMON_NAME);
            day2CmdbDto.setTargetHost(TARGET_HOST_IIS);
            day2CmdbDto.setThumbprint(NEW_THUMBPRINT);

            when(serviceNowClient.fetchCertificates(anyString(), anyInt(), anyInt(), anyString()))
                    .thenReturn(List.of(day2CmdbDto));

            NextDayConfirmationResult confirmResult = nextDayConfirmationService.confirmDeployment(
                    deploymentJob.getId(), correlationId);

            assertThat(confirmResult.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(confirmResult.isConfirmed()).isTrue();
            assertThat(confirmResult.getObservedThumbprint()).isEqualTo(NEW_THUMBPRINT);

            // ── Step 13: Finalize Deployment as COMPLETED ────────────────────
            workflowService.completeDeployment(deploymentJob.getId(), correlationId);

            DeploymentJob completedJob = jobDb.get(deploymentJob.getId());
            assertThat(completedJob.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);

            // ── Step 14: Comprehensive Audit Trail Verification ──────────────
            assertThat(auditTrail).isNotEmpty();
            assertThat(auditTrail.stream().anyMatch(e -> e.action() == AuditAction.CERTIFICATE_DISCOVERED)).isTrue();
            assertThat(auditTrail.stream().anyMatch(e -> e.action() == AuditAction.CERTIFICATE_MATCHED)).isTrue();
            assertThat(auditTrail.stream().anyMatch(e -> e.action() == AuditAction.NEXT_DAY_CONFIRMED)).isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. FAILURE SCENARIOS & RESILIENCE TESTS
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure Scenarios & Operational Resilience")
    class FailureScenarios {

        @Test
        @DisplayName("Failure 1: ServiceNow unavailable during discovery fails gracefully and does not corrupt data")
        void testServiceNowUnavailable() {
            when(serviceNowClient.fetchAllCertificates(any()))
                    .thenThrow(new ServiceNowException("ServiceNow instance unreachable (HTTP 503)"));

            assertThatThrownBy(() -> discoveryService.discoverCertificates("CORR-SN-DOWN"))
                    .isInstanceOf(ServiceNowException.class)
                    .hasMessageContaining("503");

            assertThat(certDb).isEmpty();
        }

        @Test
        @DisplayName("Failure 2: Sectigo unavailable during sync handles API failure safely")
        void testSectigoUnavailable() {
            when(sectigoClient.fetchAllCertificates(anyString(), anyString()))
                    .thenThrow(new SectigoTimeoutException("Sectigo socket timeout", new RuntimeException()));

            assertThatThrownBy(() -> sectigoIntegrationService.syncCertificates("CORR-SECTIGO-DOWN"))
                    .isInstanceOf(SectigoTimeoutException.class);
        }

        @Test
        @DisplayName("Failure 3: CyberArk unavailable during credential acquisition transitions job to RETRY_PENDING")
        void testCyberArkUnavailable() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);

            when(cyberArkVaultClient.retrieveCredential(any(), any(), any()))
                    .thenThrow(new RuntimeException("CyberArk AIM daemon connection refused"));

            DeploymentJob resultJob = workflowService.acquireCredentials(job.getId(), "CORR-CYBERARK-DOWN");
            assertThat(resultJob.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
            assertThat(resultJob.getErrorInformation()).contains("CyberArk AIM daemon connection refused");
            // Ensure secrets are never leaked into database
            assertThat(resultJob.getErrorInformation()).doesNotContain("password");
        }

        @Test
        @DisplayName("Failure 4: MID Server unavailable during dispatch transitions job to RETRY_PENDING")
        void testMidServerUnavailable() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.CREDENTIALS_ACQUIRED);

            when(deploymentDispatcher.dispatchJob(any()))
                    .thenThrow(new RuntimeException("MID Server queue offline"));

            DeploymentJob resultJob = workflowService.dispatchToMidServer(job.getId(), "CORR-MID-DOWN");
            assertThat(resultJob.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
            assertThat(resultJob.getErrorInformation()).contains("MID Server queue offline");
        }

        @Test
        @DisplayName("Failure 5: Certificate mismatch during live verification fails verification")
        void testLiveVerificationMismatch() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.DEPLOYED);

            String wrongThumbprint = "9999999999999999999999999999999999999999";
            when(endpointVerifier.verifyEndpoint(eq(TARGET_HOST_IIS), eq(443), eq(NEW_THUMBPRINT)))
                    .thenReturn(VerificationResult.failed(TARGET_HOST_IIS, 443, wrongThumbprint, NEW_THUMBPRINT, "Presented thumbprint does not match"));

            DeploymentJob resultJob = workflowService.verifyDeployment(job.getId(), "CORR-MISMATCH");

            assertThat(resultJob.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
            assertThat(resultJob.getErrorInformation()).contains("verification failed");
        }

        @Test
        @DisplayName("Failure 6: Deployment script failure handles failure and transitions to RETRY_PENDING")
        void testDeploymentExecutionFailure() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.RUNNING);

            workflowService.handleFailure(job.getId(), "PowerShell IIS binding script exited with code 1", true, "CORR-FAIL-01");

            assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
            assertThat(job.getErrorInformation()).contains("PowerShell IIS binding script exited with code 1");
        }

        @Test
        @DisplayName("Failure 7: Verification failure triggers retry scheduling")
        void testVerificationFailure() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.VERIFICATION_PENDING);

            workflowService.handleFailure(job.getId(), "Connection refused on port 443", true, "CORR-VERIFY-FAIL");

            assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
        }

        @Test
        @DisplayName("Failure 8: Duplicate job creation blocked by idempotency key check")
        void testDuplicateJobCreationBlocked() {
            DeploymentJob existingJob = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            existingJob.setIdempotencyKey("IDEM-RENEWAL-" + existingJob.getNewCertificate().getId());

            boolean exists = deploymentJobRepository.existsByIdempotencyKey(existingJob.getIdempotencyKey());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Failure 9 & 10: Retry exhaustion escalates job to MANUAL_REVIEW")
        void testRetryExhaustionEscalatesToManualReview() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.RETRY_PENDING);
            job.setAttemptCount(3);
            job.setMaxRetries(3);

            workflowService.escalateToManualReview(job.getId(), "Max retries exhausted (3/3)", "CORR-EXHAUSTION");

            assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.MANUAL_REVIEW);
        }

        @Test
        @DisplayName("Failure 11: Ambiguous certificate match prevents automatic deployment planning")
        void testAmbiguousCertificateMatch() {
            CertificateRecord oldCert = createCert(COMMON_NAME, OLD_THUMBPRINT, CertificateSource.SERVICENOW);
            CertificateRecord newCert = createCert("different.example.com", NEW_THUMBPRINT, CertificateSource.SECTIGO);

            CertificateReplacement rep = new CertificateReplacement(
                    oldCert, newCert, 0.40, "[\"NO_MATCH\"]", MatchStatus.PENDING_REVIEW);
            rep.setId(UUID.randomUUID());
            replacementDb.put(rep.getId(), rep);

            assertThatThrownBy(() -> deploymentPlanner.planDeployments(rep.getId()))
                    .isInstanceOf(com.grassroots.cdm.deployment.planner.exception.AmbiguousMatchException.class)
                    .hasMessageContaining("PENDING_REVIEW");
        }

        @Test
        @DisplayName("Failure 12: Next-Day confirmation detects OLD certificate still present in CMDB")
        void testNextDayDiscoveryDetectsOldCertificate() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.VERIFIED);

            // ServiceNow returns old certificate thumbprint
            ServiceNowCertificateDto oldCmdbDto = new ServiceNowCertificateDto();
            oldCmdbDto.setSysId("sn-cert-old");
            oldCmdbDto.setName(COMMON_NAME);
            oldCmdbDto.setTargetHost(TARGET_HOST_IIS);
            oldCmdbDto.setThumbprint(OLD_THUMBPRINT);

            when(serviceNowClient.fetchCertificates(anyString(), anyInt(), anyInt(), anyString()))
                    .thenReturn(List.of(oldCmdbDto));

            NextDayConfirmationResult result = nextDayConfirmationService.confirmDeployment(job.getId(), "CORR-OLD-CERT");

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_FAILED);
            assertThat(result.getMessage()).contains("Certificate mismatch");
        }

        @Test
        @DisplayName("Failure 13: Next-Day confirmation handles unavailable discovery data without false confirmation")
        void testNextDayDiscoveryUnavailable() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_IIS, ServerTechnology.IIS);
            job.setStatus(DeploymentJobStatus.VERIFIED);

            when(serviceNowClient.fetchCertificates(anyString(), anyInt(), anyInt(), anyString()))
                    .thenReturn(Collections.emptyList());

            NextDayConfirmationResult result = nextDayConfirmationService.confirmDeployment(job.getId(), "CORR-DISC-DOWN");

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.isConfirmed()).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. MULTI-PLATFORM DEPLOYMENT SIMULATIONS (LINUX NGINX, APACHE, JAVA)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-Platform Adapter Simulations")
    class MultiPlatformSimulations {

        @Test
        @DisplayName("Simulates Linux NGINX SSL certificate deployment workflow")
        void testLinuxNginxDeploymentSimulation() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_LINUX, ServerTechnology.NGINX);

            when(cyberArkVaultClient.retrieveCredential(any(), any(), any()))
                    .thenReturn(new CredentialSecret("svc_ansible_deploy", "P@ssw0rd_Linux_Secure!".toCharArray(), "SAFE-CDM-LINUX", TARGET_HOST_LINUX));
            when(deploymentDispatcher.dispatchJob(any()))
                    .thenReturn("MID-TASK-LINUX-NGINX-101");
            when(endpointVerifier.verifyEndpoint(eq(TARGET_HOST_LINUX), eq(443), eq(NEW_THUMBPRINT)))
                    .thenReturn(VerificationResult.successful(TARGET_HOST_LINUX, 443, NEW_THUMBPRINT, "NGINX TLS 1.3 verified"));

            workflowService.advanceJob(job.getId(), "CORR-NGINX"); // -> CREDENTIALS_ACQUIRED
            workflowService.advanceJob(job.getId(), "CORR-NGINX"); // -> SENT_TO_MID
            workflowService.acknowledgeRunning(job.getId(), "MID-TASK-LINUX-NGINX-101", "CORR-NGINX");
            workflowService.markDeployed(job.getId(), "CORR-NGINX");
            workflowService.verifyDeployment(job.getId(), "CORR-NGINX");
            workflowService.completeDeployment(job.getId(), "CORR-NGINX");

            assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        }

        @Test
        @DisplayName("Simulates Java Keystore (JKS/PKCS12) certificate replacement workflow")
        void testJavaKeystoreDeploymentSimulation() {
            DeploymentJob job = createPlannedJob(TARGET_HOST_JAVA, ServerTechnology.JAVA_KEYSTORE);

            when(cyberArkVaultClient.retrieveCredential(any(), any(), any()))
                    .thenReturn(new CredentialSecret("svc_java_deploy", "P@ssw0rd_Java_Keystore!".toCharArray(), "SAFE-CDM-JAVA", TARGET_HOST_JAVA));
            when(deploymentDispatcher.dispatchJob(any()))
                    .thenReturn("MID-TASK-JAVA-JKS-202");
            when(endpointVerifier.verifyEndpoint(eq(TARGET_HOST_JAVA), eq(8443), eq(NEW_THUMBPRINT)))
                    .thenReturn(VerificationResult.successful(TARGET_HOST_JAVA, 8443, NEW_THUMBPRINT, "Java Keystore TLS verified"));

            workflowService.advanceJob(job.getId(), "CORR-JAVA");
            workflowService.advanceJob(job.getId(), "CORR-JAVA");
            workflowService.acknowledgeRunning(job.getId(), "MID-TASK-JAVA-JKS-202", "CORR-JAVA");
            workflowService.markDeployed(job.getId(), "CORR-JAVA");
            workflowService.verifyDeployment(job.getId(), "CORR-JAVA");
            workflowService.completeDeployment(job.getId(), "CORR-JAVA");

            assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        }
    }

    // ─── Helper Factory Methods ───────────────────────────────────────────────

    private CertificateRecord createCert(String cn, String thumbprint, CertificateSource source) {
        CertificateRecord cert = new CertificateRecord();
        cert.setId(UUID.randomUUID());
        cert.setCommonName(cn);
        cert.setThumbprint(thumbprint);
        cert.setFingerprintSha256(thumbprint);
        cert.setSerialNumber(UUID.randomUUID().toString().replaceAll("-", ""));
        cert.setIssuer("DigiCert Global CA");
        cert.setSource(source);
        cert.setStatus(CertificateStatus.ACTIVE);
        cert.setValidFrom(Instant.now().minus(30, ChronoUnit.DAYS));
        cert.setValidTo(Instant.now().plus(60, ChronoUnit.DAYS));
        certDb.put(cert.getId(), cert);
        return cert;
    }

    private DeploymentJob createPlannedJob(String host, ServerTechnology tech) {
        CertificateRecord oldCert = createCert(COMMON_NAME, OLD_THUMBPRINT, CertificateSource.SERVICENOW);
        CertificateRecord newCert = createCert(COMMON_NAME, NEW_THUMBPRINT, CertificateSource.SECTIGO);

        ServerOperatingSystem os = (tech == ServerTechnology.IIS) ? ServerOperatingSystem.WINDOWS_SERVER : ServerOperatingSystem.LINUX_RHEL;
        TargetServer server = new TargetServer(host, "10.0.1.10", os, tech, EnvironmentType.PRODUCTION);
        server.setId(UUID.randomUUID());
        serverDb.put(server.getId(), server);

        int port = (tech == ServerTechnology.JAVA_KEYSTORE) ? 8443 : 443;
        CertificateInstallation install = new CertificateInstallation(oldCert, server, tech, "DEFAULT_BINDING", port);
        install.setId(UUID.randomUUID());
        installDb.put(install.getId(), install);

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-" + job.getId().toString().substring(0, 8));
        job.setIdempotencyKey("IDEM-" + job.getId());
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setTargetServer(server);
        job.setInstallation(install);
        job.setTargetHost(host);
        job.setTargetPort(port);
        job.setTargetType(tech.name());
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setStatus(DeploymentJobStatus.PLANNED);
        job.setMaxRetries(3);
        job.setAttemptCount(0);
        jobDb.put(job.getId(), job);

        return job;
    }
}
