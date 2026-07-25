# Progress Log — 2026-07-22 / 2026-07-25

## 2026-07-25 — Phase 7 (Audit Service)

### What we accomplished

- Asked the user directly which of three options Phase 7 should be (Audit Service / Frontend / CI-CD) since `progress.md` had left it open; confirmed Audit Service — finishes the backend microservices work before the frontend or CI/CD.
- Planned Phase 7 via plan mode. Explored `audit-service`'s stub (same pom.xml/Dockerfile-only starting point pipeline-service and notification-service had), the unused `postgres-audit` container, and `RabbitMQConstants.AUDIT_QUEUE` (reserved since Phase 3, never consumed). Recognized this phase reuses Phase 6's mirror-DTO/`DefaultJackson2JavaTypeMapper` mechanism wholesale rather than re-solving the `__TypeId__` cross-service deserialization problem.
- Built the real `audit-service` module: `AuditLog`/`AuditEventType` entity + a `search(...)` repository query mirroring `TicketRepository.search`'s established `(:param IS NULL OR ...)` pattern, the same `RabbitMQConfig` shape as notification-service (queue + 7 bindings + type-mapped converter, independent copy of the mirror DTOs — fuller than notification-service's since full traceability, not just recipient resolution, is the point), `AuditListener` + `AuditServiceImpl` (much simpler than `NotificationServiceImpl`: no recipient fan-out, exactly one immutable row per event, including `user.synced` itself this time), and `/api/audit-logs` (list with filters + get-by-id, both `hasRole('ADMIN')`, no mutation endpoints at all — immutability by simply not exposing PATCH/DELETE).
- Added the `api-gateway` route for `/api/audit-logs/**` (no security carve-out needed, same as notification-service's route).
- Added `audit-service` to `docker-compose.yml` (port 8085, `postgres-audit`, no `GH_*` vars).
- Wrote the full test suite: 20 tests in `audit-service` (extraction-rule correctness per event type, repository filter combinations, ADMIN-only controller access). Full reactor build (`mvnw test`, all 9 real modules) is green.
- Wrote `explication_phase_7.md`, focused on what differs from Phase 6 rather than re-explaining the shared mechanism.
- **Unlike every prior phase, no changes were needed to any already-built service** — Phase 6's event enrichment already provided every field this phase needed. Pure new-module + gateway route + docker-compose addition.

#### Files modified/created

**api-gateway**
- `application.yml` (new `audit-service` route, `/api/audit-logs/**` → `lb://audit-service`)

**audit-service** (new module, filled in from the pom.xml/Dockerfile-only stub)
- `pom.xml` (added `h2` test dep, `spring-boot-maven-plugin`)
- `src/main/resources/{application.yml,.env.properties}`, `src/test/resources/application.yml`
- `src/main/java/com/pipedevliv/audit/**` — `AuditServiceApplication`; `entity/` (`AuditLog`, `AuditEventType`); `repository/AuditLogRepository`; `config/RabbitMQConfig` (queue + 7 bindings + type-mapped converter); `service/` (`AuditListener`, `AuditService`, `AuditServiceImpl`); `controller/AuditLogController`; `dto/` (6 DTOs incl. the 4 cross-service mirror payloads)
- `src/test/java/com/pipedevliv/audit/**` — full mirrored test suite (service/repository/controller/listener layers)

**docker-compose.yml** (added `audit-service`)

**explication_phase_7.md** (new)

#### Decisions made

- **Reused Phase 6's mirror-DTO + `setIdClassMapping` mechanism as-is** — no new architectural decision needed, just an independent copy of the pattern (each consumer owns its own view of upstream shapes).
- **No recipient logic at all** — an audit trail records "this happened," not "who should know." One `AuditLog` row per event, unconditionally, including `user.synced` (which notification-service consumed only for its local directory, never surfaced).
- **Fuller mirror DTOs than notification-service's** — traceability is the whole point here, so each mirror captures (nearly) the full upstream payload shape, serialized verbatim into a `details` column.
- **Immutability via absent endpoints, not application logic** — no `updatedAt` field, no `PATCH`/`DELETE` routes exist at all.
- **`ADMIN`-only read access** — an audit trail is an administrative concern, unlike notifications' personal/ownership-scoped model; matches the existing `TicketController.getStats()` precedent for "sensitive aggregate view" endpoints.

#### Remaining tasks

- Five stacked, unmerged branches now: `feature/phase3-user-service` → `feature/phase4-ticket-service` → `feature/phase5-pipeline-service` → `feature/phase6-notification-service` → `feature/phase7-audit-service`. Merge-order decision still not made.
- No live multi-service smoke test of `audit-service` through the running Gateway was done this session (same gap as Phases 5-6) — worth doing interactively next session: perform a few ticket/pipeline actions, confirm `GET /api/audit-logs` as an ADMIN shows the right rows.

#### Next steps for tomorrow

1. Decide merge order for the five stacked branches (or start merging Phase 3 → develop now that it's stable).
2. Do a live multi-service smoke test of the new Audit Service endpoints (manual, through the running Gateway, as an ADMIN user).
3. Move on to the Frontend or CI/CD (Phase 9, `deploy.yml`) — the backend microservices are now feature-complete for the originally sketched scope (user/ticket/pipeline/notification/audit).

---

## 2026-07-25 — Phase 6 (Notification Service)

### What we accomplished

- Planned Phase 6 via plan mode (explored notification-service's stub pom comment "Email, Websockets etc à rajouter plus tard", the pre-existing unused `postgres-notifications` container, `RabbitMQConstants.NOTIFICATION_QUEUE`, and the fact that no service consumed any published event yet), confirmed scope with the user (in-app notifications only — no email/WebSocket, matching the pom's own comment and the total absence of SMTP config anywhere in `.env`), then implemented the full plan.
- Built the real `notification-service` module end-to-end: `Notification`/`LocalUser` entities+repositories, a `RabbitMQConfig` that declares the queue/bindings for all 7 relevant routing keys plus a `DefaultJackson2JavaTypeMapper` mapping each producer's FQCN to a local mirror DTO (solves the `__TypeId__` cross-service deserialization problem without sharing DTO classes via `common-lib`), a `NotificationListener` + `NotificationServiceImpl` implementing the recipient-resolution rules per event type, and the `/api/notifications` REST API (list/unread-count/mark-read/mark-all-read).
- Added two small additive fields to already-built (unmerged) services so recipients could actually be resolved: `TicketEvent.createdByUserId`/`assignedToUserId` (ticket-service) and `PipelineEvent.triggeredByUserId` (pipeline-service) — both non-breaking, no existing consumer before this phase.
- Added the `api-gateway` route for `/api/notifications/**` — no security carve-out needed this time (unlike the Phase 5 webhook), every call here goes through the normal JWT flow.
- Added `notification-service` to `docker-compose.yml` (port 8084, `postgres-notifications`, no `GH_*` vars needed).
- Wrote the full test suite: 31 tests in `notification-service` (service-layer recipient-rule correctness per event type is the important coverage — role-based fan-out excluding the creator, owner+assignee excluding the actor, self-approval exclusion, pipeline events targeting the triggering user, ownership-checked mark-read). Full reactor build (`mvnw test`, all 9 real modules + the empty `audit-service` stub) is green.
- Wrote `explication_phase_6.md`, matching the existing documentation style.

#### Files modified/created

**ticket-service**
- `messaging/TicketEvent.java` (added `createdByUserId`, `assignedToUserId`)
- `messaging/TicketEventPublisher.java` (populates the two new fields in `toEvent`)

**pipeline-service**
- `messaging/PipelineEvent.java` (added `triggeredByUserId`)
- `messaging/PipelineEventPublisher.java` (populates the new field in `toEvent`)

**api-gateway**
- `application.yml` (new `notification-service` route, `/api/notifications/**` → `lb://notification-service`)

**notification-service** (new module, filled in from the pom.xml/Dockerfile-only stub)
- `pom.xml` (added `h2` test dep, `spring-boot-maven-plugin`)
- `src/main/resources/{application.yml,.env.properties}`, `src/test/resources/application.yml`
- `src/main/java/com/pipedevliv/notification/**` — `NotificationServiceApplication`; `entity/` (`Notification`, `NotificationType`, `LocalUser`); `repository/` (2 repos); `config/RabbitMQConfig` (queue + 7 bindings + type-mapped converter); `service/` (`NotificationListener`, `NotificationService`, `NotificationServiceImpl`); `controller/NotificationController`; `dto/` (6 DTOs incl. the 4 cross-service mirror payloads)
- `src/test/java/com/pipedevliv/notification/**` — full mirrored test suite (service/repository/controller/listener layers)

**docker-compose.yml** (added `notification-service`)

**explication_phase_6.md** (new)

#### Decisions made

- **In-app notifications only** — confirmed with the user given the pom's own "add later" comment and no pre-reserved SMTP vars (unlike GitHub's vars before Phase 5). Email/WebSocket explicitly deferred, not stubbed.
- **Mirror DTOs + `setIdClassMapping`, not shared event classes in `common-lib`** — keeps the coupling one-directional (consumer knows producer shapes; producers stay unaware of notification-service), avoids adding message-schema classes to the shared library.
- **Two additive event fields** (`TicketEvent.createdByUserId`/`assignedToUserId`, `PipelineEvent.triggeredByUserId`) rather than a synchronous Feign lookup back into ticket-service/pipeline-service — keeps notification-service purely event-driven, no outbound calls at all.
- **Local `LocalUser` read-model via `user.synced`**, not a Feign call to user-service — same "stay purely event-driven" reasoning; accepted limitation that a never-logged-in user won't receive role-based notifications yet.
- **Ownership-checked REST API**: every endpoint only requires `hasRole('VIEWER')` (any authenticated user); the service layer enforces that a user can only read/mark-read their own notifications — same pattern as ticket ownership checks in Phase 4.

#### Remaining tasks

- Four stacked, unmerged branches now: `feature/phase3-user-service` → `feature/phase4-ticket-service` → `feature/phase5-pipeline-service` → `feature/phase6-notification-service`. Merge-order decision still not made.
- No live multi-service smoke test of `notification-service` through the running Gateway was done this session (same gap as Phase 5's pipeline-service) — worth doing interactively next session: log in as two different users, create/approve/deploy a ticket, confirm `GET /api/notifications` shows the right rows for the right users.

#### Next steps for tomorrow

1. Decide merge order for the four stacked branches (or start merging Phase 3 → develop now that it's stable).
2. Do a live multi-service smoke test of the new Notification Service endpoints (manual, through the running Gateway, two different logged-in users) — same style as the Phase 3 `/api/users/me` verification.
3. Start Phase 7/8 or Phase 9 (CI/CD, `deploy.yml`) — Phase 9 would unblock a real GitHub Actions test of everything built in Phase 5, which would also flow through the notification pipeline built this phase.

---

## 2026-07-24 — Phase 5 (Pipeline Service)

### What we accomplished

- Planned Phase 5 via plan mode (explored the existing speculative Feign contract, api-gateway routing, docker-compose, and the pre-reserved `GH_*` env vars first), confirmed two design decisions with the user (`RestClient` over `WebClient`; full `PipelineStage`/job tracking, not a stub), then implemented the full plan.
- Built the real `pipeline-service` module end-to-end: entities/repositories (`PipelineExecution`, `PipelineStage`), a real GitHub Actions REST API client (`GitHubActionsClient`, trigger/find-run-id/get-run/get-jobs), HMAC webhook signature verification (`WebhookSignatureVerifier`), the business logic tying it together (`PipelineServiceImpl`), and the REST + webhook controllers.
- Added the `X-Hub-Signature-256` webhook security carve-out at two levels: `api-gateway` (`permitAll` for `/api/webhooks/**`, new route) and a second, path-scoped `SecurityFilterChain` local to `pipeline-service` (`@Order(1)`) — `common-lib` itself needed no changes.
- Added a new internal endpoint on Ticket Service, `PATCH /api/tickets/{id}/pipeline-status`, guarded by a new `ROLE_SYSTEM` authority (deliberately outside the human `RoleHierarchy`) so only Pipeline Service's own Feign calls (asserting a fixed system identity, no real authenticated user exists in the webhook path) can reach it.
- Added `pipeline-service` to `docker-compose.yml` (port 8083, `postgres-pipelines`, the four pre-reserved `GH_*` vars).
- Wrote the full test suite: 38 tests in `pipeline-service` (incl. `GitHubActionsClientTest` via `MockRestServiceServer`, and `WebhookControllerTest` — the one controller test in the whole project that runs *with* the real filter chain enabled, to prove the permitAll carve-out and the manual signature check both work for real) + additions to `TicketServiceImplTest`/`TicketControllerTest` for the new callback endpoint. Full reactor build (`mvnw test`, all 9 modules) is green.
- Committed and pushed to new branch `feature/phase5-pipeline-service` (based on `feature/phase4-ticket-service`, which is itself still unmerged, continuing the stacked-branch pattern).
- Wrote `explication_phase_5.md`, matching the existing documentation style.

#### Files modified/created

**api-gateway**
- `application.yml` (new `pipeline-webhooks` route, `/api/webhooks/**` → `lb://pipeline-service`)
- `config/GatewaySecurityConfig.java` (added `permitAll` matcher for `/api/webhooks/**`)

**ticket-service**
- `dto/PipelineStatusUpdateDTO.java` (new)
- `service/TicketService.java` / `TicketServiceImpl.java` (new `updatePipelineStatus` method)
- `controller/TicketController.java` (new `PATCH /{id}/pipeline-status`, `hasRole('SYSTEM')`)
- Test additions in `TicketServiceImplTest` and `TicketControllerTest`

**pipeline-service** (new module, filled in from the pom.xml/Dockerfile-only stub)
- `pom.xml` (added `spring-cloud-starter-openfeign`, `h2` test dep, `spring-boot-maven-plugin`)
- `src/main/resources/{application.yml,.env.properties}`, `src/test/resources/application.yml`
- `src/main/java/com/pipedevliv/pipeline/**` — `PipelineServiceApplication`; `entity/` (`PipelineExecution`, `PipelineStage`, `PipelineStatus`); `repository/` (2 repos); `config/` (`RabbitMQConfig`, `GitHubConfig`, `FeignConfig`, `SecurityConfig`); `service/` (`PipelineService`, `PipelineServiceImpl`, `GitHubActionsClient`, `WebhookSignatureVerifier`); `controller/` (`PipelineController`, `WebhookController`); `dto/` (9 DTOs incl. GitHub API response shapes); `feign/TicketServiceClient`; `messaging/` (`PipelineEvent`, `PipelineEventPublisher`); `exception/GitHubApiException`
- `src/test/java/com/pipedevliv/pipeline/**` — full mirrored test suite (service/repository/controller layers + `GitHubActionsClientTest` + `WebhookSignatureVerifierTest` + `WebhookControllerTest`)

**docker-compose.yml** (added `pipeline-service`)

**explication_phase_5.md** (new)

#### Decisions made

- **`RestClient`, not `WebClient`**, for the GitHub API client — confirmed with the user; matches the fully-synchronous style used everywhere else in the codebase (no reactive stack exists anywhere), avoids introducing `.block()` calls.
- **Full `PipelineStage` job-tracking implemented now**, not stubbed — confirmed with the user, consistent with this project's stated "no half-finished implementations" principle.
- **Run-ID correlation**: best-effort poll-after-dispatch (list runs, take newest) since `workflow_dispatch` doesn't return a run ID synchronously; non-fatal if it fails (execution stays `QUEUED` with `githubRunId = null`).
- **`ROLE_SYSTEM`** for the Pipeline→Ticket callback: a standalone authority, deliberately *not* added to the shared `RoleHierarchy`, so no real Keycloak-authenticated user (however privileged) can ever reach the internal endpoint.
- **Webhook auth**: HMAC `X-Hub-Signature-256` verified manually in the controller, via a second, path-scoped `SecurityFilterChain` (`@Order(1)`) local to `pipeline-service` — chosen over modifying `common-lib`'s shared chain, keeping the carve-out narrow and out of every other service's blast radius.
- **Logs endpoint simplified**: `GET .../logs` returns GitHub's `html_url` for the run rather than downloading/parsing the raw zipped log archive — avoids real complexity for little local benefit.
- **Live E2E testing deferred**: `.github/workflows/deploy.yml` doesn't exist yet (Phase 9's deliverable) and a GitHub-hosted runner can't reach `localhost` without a tunnel — verified instead via the automated test suite + a hand-signed manual webhook curl call.

#### Remaining tasks

- Three stacked, unmerged branches now: `feature/phase3-user-service` → `feature/phase4-ticket-service` → `feature/phase5-pipeline-service`. Merge-order decision still not made.
- No live multi-service smoke test of `pipeline-service` through the running Gateway was done this session (unlike `user-service` in Phase 3) — the stack (Keycloak, Postgres, RabbitMQ, all 5 Spring Boot processes) wasn't started; worth doing interactively next session, the same way the Phase 3/4 manual curl verification was done together.
- Real GitHub Actions round-trip is blocked on Phase 9's `deploy.yml` + a reachable webhook URL.

#### Next steps for tomorrow

1. Decide merge order for the three stacked branches (or start merging Phase 3 → develop now that it's stable).
2. Do a live multi-service smoke test of the new Pipeline Service endpoints + webhook (manual, HMAC-signed curl, through the running Gateway) — same style as the Phase 3 `/api/users/me` verification.
3. Start Phase 6 (Notification Service) or Phase 9 (CI/CD, `deploy.yml`) — Phase 9 first would unblock a real GitHub Actions test of everything built in Phase 5.

---

## 2026-07-22 / 2026-07-23

### What we accomplished

- **Pushed Phase 3 (User Service) to completion**: added the missing test suite (service/repository/controller layers), a `RabbitMQConfig` + `user.synced` event publication, and a `user-service` entry in `docker-compose.yml`. Committed and pushed to `feature/phase3-user-service`.
- **Diagnosed and fixed local dev environment issues** blocking anyone from actually running the stack:
  - `.env` wasn't being read by Spring Boot at all (only `docker compose` reads it automatically) — added `spring-dotenv` plus a `workingDirectory` override on `spring-boot-maven-plugin` so both `mvnw spring-boot:run` and IntelliJ resolve the repo-root `.env` consistently.
  - Port 8080 conflict with a pre-existing `OracleXETNSListener` Windows service on the dev machine — identified and stopped it.
  - Port 8081 conflict between the Dockerized `user-service` container and the same service run locally in IntelliJ — clarified the "run it in one place at a time" rule.
  - Fixed `curl` vs `curl.exe` / PowerShell alias gotchas encountered while manually testing the Keycloak token flow and the API endpoints end-to-end (confirmed `/api/users/me`, `/api/users`, and the anti-bypass 403 all work correctly through the real running stack).
- **Planned and implemented Phase 4 (Ticket Service)** end-to-end via plan mode:
  - Full 14-status ticket lifecycle with a pure `TicketStateMachine`, role-gated REST API, ownership rules, RabbitMQ event publishing, and Feign clients to User Service and the not-yet-built Pipeline Service (with a fallback).
  - Added a shared `RoleHierarchy` (`ADMIN > RELEASE_MANAGER > TECH_LEAD > DEVELOPER > VIEWER`) and an `AccessDeniedException` handler to `common-lib`, benefiting every current/future service.
  - Wrote and debugged the full three-layer test suite (43 tests), including tracking down a real `@WebMvcTest` gotcha (custom `@AutoConfiguration` classes aren't loaded by default in that test slice, silently disabling `@PreAuthorize`) and a stale local Maven repo artifact that was masking the real behavior during debugging.
  - Added `ticket-service` to `docker-compose.yml`.
  - Committed and pushed to new branch `feature/phase4-ticket-service` (based on `feature/phase3-user-service`, which is not yet merged).
- **Wrote `explication_phase_4.md`**, matching the existing `explication_phase_*.md` documentation style, covering the state machine, the RoleHierarchy mechanism, the Feign/Gateway-bypass problem, RabbitMQ events, the REST API table, the test-slice gotcha, and open items for Phase 5.

### Files modified/created

**Phase 3 follow-up (`feature/phase3-user-service`)**
- `backend/common-lib/src/main/java/com/pipedevliv/common/event/RabbitMQConstants.java` (added `USER_SYNCED`)
- `backend/user-service/pom.xml`, `application.yml`, new `config/RabbitMQConfig.java`
- `backend/user-service/src/main/java/com/pipedevliv/user/service/UserServiceImpl.java` (publishes `user.synced`)
- `backend/user-service/src/test/**` (new: `UserServiceImplTest`, `UserProfileRepositoryTest`, `UserControllerTest`, test `application.yml`)
- `docker-compose.yml` (added `user-service`)

**.env / local-run fix (`feature/phase3-user-service`)**
- `backend/pom.xml` (added `spring-dotenv` dependency + `spring-boot-maven-plugin` `workingDirectory` override in `pluginManagement`)
- `backend/{discovery-server,api-gateway,user-service}/src/main/resources/.env.properties` (new, documentation-only)

**Phase 4 (`feature/phase4-ticket-service`)**
- `backend/common-lib/.../security/SecurityConfig.java` (added static `RoleHierarchy` + `MethodSecurityExpressionHandler` beans)
- `backend/common-lib/.../exception/GlobalExceptionHandler.java` (added `AccessDeniedException` handler)
- `backend/ticket-service/pom.xml` (added `spring-cloud-starter-openfeign`, `h2` test dep, `spring-boot-maven-plugin`)
- `backend/ticket-service/src/main/java/com/pipedevliv/ticket/**` — full new module: `TicketServiceApplication`, `entity/` (`Ticket`, `TicketStatus`, `TicketPriority`, `TicketComment`, `TicketHistory`), `repository/` (3 repos), `service/` (`TicketService`, `TicketServiceImpl`, `TicketStateMachine`), `controller/TicketController`, `dto/` (12 DTOs), `feign/` (`UserServiceClient`, `PipelineServiceClient`, `PipelineServiceFallback`), `messaging/` (`TicketEvent`, `TicketEventPublisher`), `config/` (`RabbitMQConfig`, `FeignConfig`), `exception/InvalidTransitionException`
- `backend/ticket-service/src/main/resources/{application.yml,.env.properties}`
- `backend/ticket-service/src/test/**` — `TicketStateMachineTest`, `TicketServiceImplTest`, `TicketRepositoryTest`, `TicketHistoryRepositoryTest`, `TicketCommentRepositoryTest`, `TicketControllerTest`, test `application.yml`
- `docker-compose.yml` (added `ticket-service`)
- `explication_phase_4.md` (new)

### Decisions made

- **14 ticket statuses**: the plan doc's 13-status diagram + `CANCELLED` (terminal, reachable from DRAFT/SUBMITTED).
- **Pipeline Service Feign client**: build the full client now against a speculative contract, with a fallback (`BusinessException("Pipeline Service indisponible")`) until Phase 5 exists, rather than deferring the whole deploy/approve-to-PROD flow.
- **Role hierarchy**: centralized once in `common-lib` (`ADMIN > RELEASE_MANAGER > TECH_LEAD > DEVELOPER > VIEWER`) rather than duplicated per service.
- **`PATCH /status` authorization**: TECH_LEAD+ *or* the ticket's own DEVELOPER-owner, but a mere owner may only request `SUBMITTED` or `CANCELLED` — enforced in the service layer, not in the `@PreAuthorize` SpEL.
- **`TicketStateMachine` design**: deliberately knows nothing about roles/users — pure transition-legality check, separate from authorization. Deviates intentionally from the original plan doc's pseudocode (which conflated both concerns).
- **No MapStruct**: kept manual `toDTO` mapping for consistency with User Service's existing pattern.
- **`.env` loading**: standardized on `spring-dotenv` + a forced `workingDirectory` (repo root) for `spring-boot-maven-plugin`, rather than per-terminal manual env loading or an IntelliJ-only plugin — works identically for CLI and IDE.
- **Docker container placement**: `user-service`/`ticket-service` containers reach the not-yet-containerized `discovery-server`/`api-gateway` via `host.docker.internal`, since those two haven't been containerized yet.
- **Test-slice security wiring**: `TicketControllerTest` explicitly `@Import`s `common-lib`'s `SecurityConfig`/`GlobalExceptionHandler` rather than relying on `@WebMvcTest`'s default auto-configuration — documented as the pattern to reuse for any future service's controller tests that use `@PreAuthorize`.

### Remaining tasks

- Open the PR for `feature/phase3-user-service` → `develop` (link was generated but never opened/merged).
- Open the PR for `feature/phase4-ticket-service` — note it's currently based on the *unmerged* `feature/phase3-user-service`, not `develop`; decide whether to merge Phase 3 first or stack the PRs.
- `explication_phase_4.md` is written but not yet committed/pushed (was mid-flow when this progress log was requested).
- Phase 4's Feign contract with Pipeline Service (`PipelineTriggerDTO`/`PipelineExecutionDTO`) is speculative and will need reconciling once Phase 5 actually defines Pipeline Service's real API.
- No real end-to-end manual test of ticket-service through the Gateway yet (unlike user-service, which was verified live with a real Keycloak token) — worth doing once Phase 5 or at least a manual Postman pass is convenient.

### Next steps for tomorrow

1. Decide merge order for the two open branches (Phase 3 → develop, then Phase 4 → develop, or stack them).
2. Commit + push `explication_phase_4.md`.
3. Start Phase 5 (Pipeline Service): will need a GitHub Actions client, a webhook receiver, and the real `/api/pipelines/trigger` + `/api/pipelines/executions/{id}` endpoints that `PipelineServiceClient` is already calling speculatively from ticket-service.
4. Once Pipeline Service exists, do a live end-to-end test of the full DEV deploy flow (create ticket → submit → approve → deploy DEV) through the running stack.
