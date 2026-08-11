# tenant-management-service

Manages tenants (organizations/sub-tenants) and their hierarchy. Security
(JWT validation, `@tenantSecurity` RBAC checks) comes from the shared
`iiotedge-security-starter`, validating tokens issued by `auth-service`.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/tenants` | SUPER_ADMIN, or `SCOPE_INTERNAL` (auth-service signup flow) |
| DELETE | `/api/v1/tenants/internal/{tenantId}` | `SCOPE_INTERNAL` only - auth-service's compensating rollback if signup fails after tenant creation |
| GET | `/api/v1/tenants/{tenantId}` | Members of that tenant |
| GET | `/api/v1/tenants` | SUPER_ADMIN only - platform-wide listing, paginated (`?page=&size=&sort=`, defaults to 20/page sorted by name) |
| GET | `/api/v1/tenants/children/{parentId}` | Members of the parent tenant |
| GET | `/api/v1/tenants/{tenantId}/companies-with-users` | Members of that tenant - full descendant tree, one batched query per tree level |

Errors follow `{"statusCode": ..., "error": "...", "message": "..."}` via
`GlobalExceptionHandler`, mirroring auth-service's shape: 404 for a missing
tenant, 409 for a hierarchy-rule violation, 400 for bean-validation
failures, 403 for `@PreAuthorize` denials, 500 (generic, no stack trace)
for anything unexpected.

`/actuator/health`, `/actuator/info`, `/actuator/prometheus` are exposed,
matching auth-service's observability setup.

## Configuration

Active profile: `${SPRING_PROFILES_ACTIVE:dev}` (`dev`, `hProd`, or `prod`).

- `JWT_SECRET_KEY_BASE64` - same signing key as auth-service, required by
  `iiotedge-security-starter`'s `StatelessJwtFilter`. **Required with no
  fallback in `prod`**; `dev`/`hProd` fall back to the same published dev
  key auth-service's dev profile uses, for local testing only.
- `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` - Postgres, required
  in `prod` (injected by `iiotedge-cli.sh`); `dev`/`hProd` default to a
  local Postgres instance.
- `REDIS_*` - present in `application-prod.yml` under `spring.data.redis.*`
  (current Boot 3.x property prefix) but currently inert: this service has
  no `spring-boot-starter-data-redis` dependency or connection factory bean.
  Left in place, not wired up, in case Redis is added later.
- Schema is managed via `spring.jpa.hibernate.ddl-auto` (Flyway disabled),
  matching the rest of the platform. `src/main/resources/db/manual/` holds
  SQL that isn't run automatically - see that file's header for when to run
  it by hand.
- CORS: only configured for `dev` (`CorsConfig`, `@Profile("dev")`). In
  `hProd`/`prod`, the edge Nginx layer (`iiotedge-cli.sh`) attaches CORS
  headers instead - a second Spring-managed CORS layer would risk duplicate
  `Access-Control-Allow-Origin` headers.
