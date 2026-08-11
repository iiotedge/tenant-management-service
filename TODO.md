# TODO

Known gaps, prioritized. See `CHECKLIST.md` for the full done/open list.

## Resolved (kept here briefly for context, not action items)

- ~~No global exception handling~~ - `GlobalExceptionHandler` added,
  mirroring auth-service's response shape.
- ~~No input validation~~ - `@Valid`/bean validation added to
  `CreateTenantRequest`.
- ~~Unbounded tenant listing~~ - `GET /api/v1/tenants` is now paginated.
- ~~N+1 query in the descendant-tree endpoint~~ - now one batched query
  per tree level instead of one per node.
- ~~No observability~~ - actuator/prometheus exposed, matching auth-service.
- ~~jwt.secret had an insecure fallback usable in prod~~ - fixed in an
  earlier pass (see git history), same pattern as auth-service.
- ~~~760 lines of dead, commented-out Cassandra-era code~~ - removed.

## Architecture

- [ ] **CI pipeline.** This repo has none yet. auth-service's
      `.github/workflows/ci.yml` solves the same "local relative-path
      parent POM + unpublished shared modules" problem via a multi-repo
      checkout - that pattern would need to be replicated here (update
      the checked-out-repo path and reactor-trimming list for
      `tenant-management-service` instead of `auth-service`).
- [ ] **Publish the parent POM chain + `common` modules to GitHub
      Packages.** Same platform-wide gap noted in auth-service's `TODO.md` -
      not fixable from within this service alone.
- [ ] Real DB migrations (Flyway/Liquibase) instead of `ddl-auto: update`.
      `src/main/resources/db/manual/` already holds one migration that
      isn't run automatically for exactly this reason - worth revisiting
      platform-wide, not just here.
- [ ] The `RestTemplateConfig` bean and `services.dms.url` config exist but
      are currently unused - no code calls DMS yet. Left in place rather
      than removed since they look like groundwork for a real integration,
      but worth confirming intent before adding more scaffolding on top.
      If it is wired up, give it an explicit connect/read timeout - an
      unconfigured `RestTemplate` can hang indefinitely on a slow/dead peer.

## Testing

- [ ] Integration tests against a real Spring context / Postgres (current
      suite is Mockito-based unit tests only, matching auth-service's own
      documented gap).
- [ ] `TenantManagementServiceApplicationTests.contextLoads` needs a live
      local Postgres - couldn't be exercised in this environment.
- [ ] Confirm the JaCoCo/SpotBugs gates inherited from the shared parent
      POM actually pass at their configured thresholds here - inherited
      via the same parent chain as auth-service, but not run to a gate
      verdict in this environment (no local Postgres for the
      DB-dependent test in the reactor).

## Operational

- [ ] Validate this service under more than one replica in a real
      environment.
- [ ] Secrets management - `jwt.secret`/DB password are plain environment
      variables today, same as auth-service's own open item.
