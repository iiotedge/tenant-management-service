# Production Readiness Checklist

What's actually verified vs. still open. See `TODO.md` for the reasoning
behind each unchecked item. Mirrors auth-service's checklist structure.

## Authentication & authorization

- [x] JWT validation and RBAC (`@PreAuthorize` + `@tenantSecurity`) come
      from the shared `iiotedge-security-starter`, validating tokens
      auth-service issues
- [x] `jwt.secret` required with no insecure fallback in prod (dev/hProd
      keep a local-only fallback for convenience)
- [x] Tenant-hierarchy rules enforced server-side (`validateHierarchy`),
      not just left to callers to get right
- [ ] Secrets sourced from a vault/secrets manager rather than plain env vars

## Error handling & input validation

- [x] `GlobalExceptionHandler` maps domain errors to correct HTTP status
      codes (404/409/400/403/500) instead of leaking raw 500s
- [x] Bean validation (`@Valid`) on `CreateTenantRequest`
- [x] No stack traces or internal details leaked in the generic 500 response

## Scalability

- [x] `GET /api/v1/tenants` is paginated (was an unbounded `findAll()`)
- [x] `GET /{tenantId}/companies-with-users` fetches each tree level in one
      batched query instead of one query per node (was O(nodes), now O(depth))
- [x] Explicit indexes on `parent_id` and `tenant_type` (hierarchy queries
      filter on both)
- [ ] Load/performance testing under a realistic tenant count and tree depth

## Testing & static analysis

- [x] Mockito unit test suite covers `TenantService` and `TenantController`
      (hierarchy validation, not-found paths, pagination, tree building,
      status-code mapping)
- [x] JaCoCo/SpotBugs quality gates inherited from the shared parent POM
      (same chain auth-service uses) - not yet run against a live gate
      threshold in this environment (see `TODO.md`)
- [ ] Integration tests against a real Spring context / test database
      (current suite is Mockito-based unit tests only, same documented gap
      as auth-service)
- [ ] `TenantManagementServiceApplicationTests.contextLoads` needs a live
      local Postgres to run - not exercised in this pass

## Observability

- [x] `/actuator/health`, `/actuator/info`, `/actuator/prometheus` exposed
      (matches auth-service)
- [x] Dead/commented-out legacy code removed (`TenantCassandraDao`, ~760
      lines of superseded `TenantService` history) - was actively
      obscuring what's live
- [ ] Distributed tracing
- [ ] Alerting rules

## Deployment

- [ ] No CI pipeline wired up yet for this repo (auth-service has one via
      a multi-repo-checkout workaround - see its `ci.yml` for the pattern
      this would need to replicate)
- [ ] Verified running with more than one replica in a real environment
- [ ] Real DB migrations (Flyway/Liquibase) instead of
      `spring.jpa.hibernate.ddl-auto=update` - matches the rest of the
      platform's current convention, not unique to this service
