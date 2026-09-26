# Certificate Deployment Manager (CDM) - Core Service

The **Certificate Deployment Manager (CDM)** is an enterprise orchestration platform designed to automate the discovery, renewal matching, credential retrieval, MID Server dispatch, live verification, and auditing of SSL/TLS certificates across heterogeneous infrastructure (Windows IIS, Linux Apache/Nginx, and Java Keystores).

---

## Architecture Principles

- **CDM is the Orchestration & Decision-Making Layer**: CDM tracks certificate lifecycles, correlates discovered certificates to Sectigo renewals, retrieves deployment credentials just-in-time from CyberArk, and schedules deployment operations.
- **MID Server is the Execution Layer**: Deployment operations and scripts are dispatched to ServiceNow MID Servers. **Target servers must never be directly controlled by arbitrary or ad-hoc commands from the CDM backend.**
- **Least-Privilege Security**: Target credentials retrieved from CyberArk CCP/AIM are held transiently in memory and wiped immediately post-dispatch; they are never persisted or logged.
- **Audit-First**: Every state transition, discovery sync, credential acquisition, and verification is recorded into an append-only, tamper-evident audit trail.

---

## Technology Stack

- **Java 21**: Modern language features (Records, Pattern Matching, Sequenced Collections).
- **Spring Boot 3.4.2**: Core framework, Spring MVC, Spring Data JPA, Spring Security, Spring Boot Actuator.
- **PostgreSQL 16**: Relational persistence and JSONB metadata storage.
- **Flyway**: Version-controlled, reproducible database migrations (`db/migration/V1__initial_schema.sql`).
- **Spring Security 6**: Stateless authentication, role-based authorization, RFC 7807 error responses.
- **Springdoc OpenAPI / Swagger UI 3**: Interactive API documentation at `/swagger-ui/index.html`.
- **JUnit 5, Mockito & Testcontainers**: Unit and real-database integration testing.
- **Docker & Docker Compose**: Containerized database and production runtime.

---

## Project Structure & Architecture Packages

```
com.grassroots.cdm
├── CdmApplication.java                  # Main Spring Boot entry point
├── audit                                # Immutable audit trail and event domain
│   ├── AuditAction.java                 # Standard audit event types
│   ├── AuditEvent.java                  # Audit event payload
│   └── AuditService.java                # Audit publishing interface
├── configuration                        # Framework and subsystem configurations
│   ├── CdmSecurityProperties.java       # Security credentials binding
│   ├── JpaAuditingConfig.java           # JPA entity auditing configuration
│   ├── OpenApiConfig.java               # OpenAPI/Swagger 3 specification metadata
│   └── SecurityConfig.java              # Spring Security filter chain & RBAC
├── controller                           # REST API controllers (DTO boundaries)
│   └── SystemController.java            # Diagnostic and health inspection APIs
├── deployment                           # Deployment orchestration domain
│   ├── DeploymentDispatcher.java        # MID Server dispatch orchestrator contract
│   ├── DeploymentInstruction.java       # Parameterized MID server payload specification
│   ├── DeploymentJobStatus.java         # State machine statuses
│   └── TargetType.java                  # Supported targets (IIS, Apache, Nginx, JKS)
├── dto                                  # Data Transfer Objects (Records)
│   ├── ApiResponse.java                 # Standard response wrapper
│   ├── CertificateSummaryDto.java       # Certificate view representation
│   ├── DeploymentJobDto.java            # Job view representation
│   ├── ErrorResponse.java               # RFC-7807 error envelope
│   ├── SystemStatusDto.java             # Diagnostic status payload
│   └── ValidationError.java             # Constraint violation item
├── entity                               # JPA entities
│   ├── AuditLogRecord.java              # audit_logs table entity
│   ├── BaseEntity.java                  # MappedSuperclass (UUID, version, audit timestamps)
│   ├── CertificateRecord.java           # certificates table entity
│   └── DeploymentJob.java               # deployment_jobs table entity
├── exception                            # Exception handling and translations
│   ├── CdmException.java                # Base application exception
│   ├── GlobalExceptionHandler.java      # Centralized REST exception translator
│   ├── IntegrationException.java        # External integration faults
│   ├── ResourceNotFoundException.java   # Missing resource 404
│   └── ValidationException.java         # Business constraint violation 400
├── integration                          # External system contract interfaces
│   ├── CredentialSecret.java            # Transient in-memory secret holder
│   ├── CyberArkVaultClient.java         # CyberArk credential retrieval contract
│   ├── MidServerClient.java             # ServiceNow MID Server queue contract
│   ├── SectigoClient.java               # Sectigo CA renewal contract
│   └── ServiceNowClient.java            # ServiceNow CMDB discovery contract
├── matching                             # Certificate correlation engine
│   ├── CertificateMatcher.java          # Matching strategy contract
│   └── MatchResult.java                 # Match evaluation outcome
├── repository                           # Spring Data JPA repositories
│   ├── AuditLogRecordRepository.java
│   ├── CertificateRecordRepository.java
│   └── DeploymentJobRepository.java
├── security                             # Authentication & authorization handlers
│   ├── CustomAccessDeniedHandler.java   # 403 Forbidden JSON handler
│   └── CustomAuthenticationEntryPoint.java # 401 Unauthorized JSON handler
├── service                              # Business service abstractions
│   ├── SystemService.java               # Platform diagnostics interface
│   └── impl/SystemServiceImpl.java      # System diagnostic implementation
├── verification                         # Verification contracts
│   ├── EndpointVerifier.java            # Live TLS endpoint handshake verification
│   ├── NextDayDiscoveryVerifier.java    # ServiceNow discovery reconciliation
│   └── VerificationResult.java          # Handshake validation result
└── workflow                             # Multi-step state machine engine
    ├── WorkflowContext.java             # Contextual workflow state
    └── WorkflowEngine.java              # Step execution engine contract
```

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (or local PostgreSQL 16)

### Environment Configuration

Copy the template to your environment:
```bash
cp .env.example .env
```

### Running with Docker Compose

To start both PostgreSQL and the CDM application:
```bash
docker compose up -d
```

To run only the PostgreSQL container:
```bash
docker compose up -d postgres
```

### Running Locally with Maven

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

---

## Endpoints

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/v1/system/status` | System diagnostic & DB connectivity status | Public |
| `GET` | `/api/v1/system/ping` | Lightweight ping liveness check | Public |
| `GET` | `/actuator/health` | Spring Boot Actuator liveness & readiness | Public |
| `GET` | `/actuator/info` | Application build & runtime info | Public |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 JSON specification | Public |
| `GET` | `/swagger-ui/index.html` | Interactive Swagger UI documentation | Public |
| `ANY` | `/api/v1/*` | Business orchestration endpoints | Authenticated (`ROLE_ADMIN`, `ROLE_ORCHESTRATOR`) |

---

## Testing

Run unit and integration tests (executes containerized PostgreSQL via Testcontainers):
```bash
mvn clean test
```
