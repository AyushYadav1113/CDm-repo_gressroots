# Postman Collection for Certificate Deployment Manager (CDM)

This folder contains the complete Postman collection and environment definitions for testing and interacting with the CDM platform.

---

## Files Included

1. **[`CDM_API_Collection.postman_collection.json`](file:///Users/ayushyadav/CDm-repo_gressroots/postman/CDM_API_Collection.postman_collection.json)**
   - Schema: Postman Collection v2.1.0
   - Built-in test assertions (status codes, JSON schemas, health indicators, response times).
   - Configured with Basic Authentication inheritance.
2. **[`CDM_Local.postman_environment.json`](file:///Users/ayushyadav/CDm-repo_gressroots/postman/CDM_Local.postman_environment.json)**
   - Environment variables for local development (`baseUrl`, `adminUsername`, `adminPassword`).

---

## Included Requests

### 1. System Diagnostics
- `GET /api/v1/system/status`: Inspects real-time system health, application version, and PostgreSQL connection.
- `GET /api/v1/system/ping`: Lightweight liveness probe returning `"pong"`.

### 2. Spring Boot Actuator
- `GET /actuator/health`: Composite health check (Liveness, Readiness, PostgreSQL).
- `GET /actuator/info`: Application runtime info.
- `GET /actuator/metrics`: Micrometer metrics list.
- `GET /actuator/metrics/jvm.memory.used`: JVM memory usage details.

### 3. OpenAPI & Swagger Documentation
- `GET /v3/api-docs`: OpenAPI 3.0 specification JSON.
- `GET /swagger-ui/index.html`: Interactive Swagger UI web console.

### 4. Security & Authorization Verification
- `GET /api/v1/jobs` (No Auth): Verifies 401 Unauthorized response in RFC 7807 problem details format.
- `GET /api/v1/system/status` (Basic Auth): Verifies authenticated access with configured credentials.

---

## How to Import into Postman

1. Open Postman.
2. Click **Import** (top left).
3. Drag and drop both:
   - `CDM_API_Collection.postman_collection.json`
   - `CDM_Local.postman_environment.json`
4. Select the environment **CDM Local Environment** from the environment dropdown (top right).
5. Start the CDM application (`SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run` on port `8085`).
6. Run individual requests or use the **Collection Runner** to execute all automated test scripts.

---

## Running with Newman (CLI)

You can also run the collection directly from the command line using Newman:

```bash
# Install newman if not already installed
npm install -g newman

# Run the collection with the local environment
newman run postman/CDM_API_Collection.postman_collection.json \
  -e postman/CDM_Local.postman_environment.json
```
