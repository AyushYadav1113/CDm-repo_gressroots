# Certificate Deployment Manager (CDM) — Core Service

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

## Running the Codebase Locally

### 1. Prerequisites

Ensure you have the following installed on your machine:

- **Java (JDK) 21+**: Verify with `java -version`
- **Maven 3.9+** (or use included `./mvnw`): Verify with `./mvnw -version`
- **Docker & Docker Desktop**: Verify with `docker --version`
- **PostgreSQL** *(only needed if running database natively without Docker)*

---

### 2. Setting Up the Database

You can run PostgreSQL either through **Docker** or as a **local native service**.

#### Option A: Using Docker (Quickest & Recommended)

Start the PostgreSQL 16 container:

```bash
docker compose up -d postgres
```

To stop PostgreSQL later:
```bash
docker compose stop postgres
```

#### Option B: Using Native Local PostgreSQL

If you run PostgreSQL directly on macOS (e.g., Homebrew) or Linux:

1. Connect to PostgreSQL:
   ```bash
   psql -U $(whoami) -d postgres
   ```

2. Execute SQL setup:
   ```sql
   -- Create CDM database
   CREATE DATABASE cdm_db;

   -- Create application user
   CREATE ROLE cdm_user WITH LOGIN PASSWORD 'cdm_password_change_me';

   -- Grant privileges
   GRANT ALL PRIVILEGES ON DATABASE cdm_db TO cdm_user;
   \c cdm_db
   GRANT ALL ON SCHEMA public TO cdm_user;
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cdm_user;
   ```

3. Test connection:
   ```bash
   PGPASSWORD=cdm_password_change_me psql -h localhost -U cdm_user -d cdm_db -c "SELECT current_user, current_database();"
   ```

---

### 3. Local Environment Configuration (`.env`)

The project uses `.env` for local configuration overrides (ignored by Git to keep secrets safe):

```bash
cp .env.example .env
```

Default configuration for local development:
```env
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8085
LOG_LEVEL_CDM=DEBUG

DB_HOST=localhost
DB_PORT=5432
DB_NAME=cdm_db
DB_USERNAME=cdm_user
DB_PASSWORD=cdm_password_change_me

SECURITY_BASIC_USERNAME=cdm_admin
SECURITY_BASIC_PASSWORD=admin_dev_password_change_me
```

> [!NOTE]
> The default local server port is configured to **`8085`** in `application-local.yml` to prevent port collisions with other local services or Docker containers bound to port `8080`.

---

### 4. Starting the Application

#### Method 1: Using Maven Wrapper (Standard Development Mode)

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The application will start in ~3–4 seconds. You will see:
- Flyway automatically applies migration `V1__initial_schema.sql`
- JPA repositories initialized
- Tomcat started on port **`8085`**

#### Method 2: Running via Docker Compose (Full Stack)

To run both PostgreSQL and the containerized Spring Boot application:

```bash
docker compose up --build -d
```

Stream application logs:
```bash
docker compose logs -f cdm-app
```

To stop all services:
```bash
docker compose down
```

#### Method 3: Running Packaged JAR

```bash
./mvnw clean package -DskipTests
java -jar target/cdm-core-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

### 5. Verifying the Running Application

With the server running on port `8085`, verify endpoints using `curl`:

#### A. System Diagnostics & Database Connectivity
```bash
curl -s http://localhost:8085/api/v1/system/status | jq .
```
```json
{
  "success": true,
  "message": "System status retrieved successfully",
  "data": {
    "applicationName": "cdm-core",
    "version": "1.0.0",
    "status": "HEALTHY",
    "serverTime": "2026-09-26T15:47:17.811Z",
    "javaVersion": "24.0.1",
    "components": {
      "database": "UP (PostgreSQL ...)",
      "security": "ENFORCED (Stateless Basic / RBAC)",
      "flyway": "MIGRATIONS_APPLIED",
      "orchestration": "READY"
    }
  },
  "timestamp": "2026-09-26T15:47:17.814647Z"
}
```

#### B. Lightweight Ping Check
```bash
curl -s http://localhost:8085/api/v1/system/ping | jq .
```
```json
{
  "success": true,
  "message": "Ping successful",
  "data": "pong"
}
```

#### C. Spring Boot Actuator Health Probe
```bash
curl -s http://localhost:8085/actuator/health | jq .
```
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

#### D. Interactive Swagger UI & OpenAPI Specification
- **Swagger UI Console**: [http://localhost:8085/swagger-ui/index.html](http://localhost:8085/swagger-ui/index.html)
- **OpenAPI 3.0 JSON Spec**: [http://localhost:8085/v3/api-docs](http://localhost:8085/v3/api-docs)

#### E. Spring Security Verification
1. **Unauthenticated access to protected endpoint (Expect 401)**:
   ```bash
   curl -i http://localhost:8085/api/v1/jobs
   ```
   *Returns HTTP 401 Unauthorized in RFC 7807 error format.*

2. **Authenticated access using Basic Auth credentials**:
   ```bash
   curl -i -u cdm_admin:admin_dev_password_change_me http://localhost:8085/api/v1/system/status
   ```
   *Returns HTTP 200 OK.*

---

## 6. Testing with Postman & Newman

A complete Postman collection and environment are pre-configured in the [`postman/`](file:///Users/ayushyadav/CDm-repo_gressroots/postman) folder:

- Collection: [`postman/CDM_API_Collection.postman_collection.json`](file:///Users/ayushyadav/CDm-repo_gressroots/postman/CDM_API_Collection.postman_collection.json)
- Environment: [`postman/CDM_Local.postman_environment.json`](file:///Users/ayushyadav/CDm-repo_gressroots/postman/CDM_Local.postman_environment.json)
- Raw OpenAPI Spec: [`postman/cdm-openapi.json`](file:///Users/ayushyadav/CDm-repo_gressroots/postman/cdm-openapi.json)

### Importing into Postman:
1. Open Postman and click **Import** (top left).
2. Drag and drop both JSON files from `postman/`.
3. Select **CDM Local Environment** in the top-right environment selector.
4. Click **Run collection** to run all automated test assertions.

### Running via CLI (Newman):
```bash
npx -y newman run postman/CDM_API_Collection.postman_collection.json \
  -e postman/CDM_Local.postman_environment.json
```

---

## 7. Running the Automated Test Suite

CDM uses **Testcontainers** to launch an isolated PostgreSQL container automatically during integration test runs.

Run all unit tests and integration tests:
```bash
./mvnw clean test
```

Expected output:
```text
[INFO] Running com.grassroots.cdm.ActuatorAndOpenApiIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grassroots.cdm.repository.DatabaseMigrationAndRepositoryIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grassroots.cdm.controller.SystemControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grassroots.cdm.service.SystemServiceImplTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grassroots.cdm.exception.GlobalExceptionHandlerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.grassroots.cdm.CdmApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 8. Common Troubleshooting

| Issue | Cause | Resolution |
|---|---|---|
| `Port 8085 already in use` | Another process is using port 8085. | Update `SERVER_PORT=8090` in `.env` or run `SERVER_PORT=8090 ./mvnw spring-boot:run`. |
| `FATAL: password authentication failed for user "cdm_user"` | Local PostgreSQL user password mismatch. | Verify credentials in `.env` match your PostgreSQL instance, or use `docker compose up -d postgres`. |
| `Connection refused: localhost:5432` | PostgreSQL is not started. | Start PostgreSQL with `docker compose up -d postgres` or `brew services start postgresql@16`. |
| `Docker daemon is not running` (during `./mvnw test`) | Docker Desktop is not started. | Open Docker Desktop (`open -a Docker` on macOS). |
