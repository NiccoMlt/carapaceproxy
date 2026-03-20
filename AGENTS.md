# AGENTS.md

Agent guide for `carapaceproxy`, focused on **server configuration**.

## Scope and structure
- Root project is Maven multi-module (`pom.xml`).
- Backend module: `carapace-server` (Java 21, JAR).
- UI module: `carapace-ui` (Vue 2 WAR), not the primary source of server config semantics.
- Prefer Maven wrapper `./mvnw`.
- Reference docs in `docs/`: `dynamic-configuration-reference.md`, `static-configuration-reference.md`, `jvm-system-properties-reference.md`, `secrets-keystores-certificates-storage.md`, `herddb-configuration-store-schema.md`, `zookeeper-stored-data-reference.md`.
- Lombok (`@Data`, `@Builder`, `@Getter`/`@Setter`) is used across config and model classes. Java records are used for `NetworkListenerConfiguration`, `BackendConfiguration`, and `EndpointKey` — do not add conventional constructors or setters to these.

## Configuration files that matter most
- Static boot config template:
  - `carapace-server/src/main/resources/conf/server.properties`
- Cluster boot config template:
  - `carapace-server/src/main/resources/conf/cluster.properties`
- Dynamic runtime config template:
  - `carapace-server/src/main/resources/conf/server.dynamic.properties`
- User realm file example:
  - `carapace-server/src/main/resources/conf/user.properties`
- Main server bootstrap entrypoint:
  - `carapace-server/src/main/java/org/carapaceproxy/launcher/ServerMain.java`
- Main server config application flow:
  - `carapace-server/src/main/java/org/carapaceproxy/core/HttpProxyServer.java`
  - `carapace-server/src/main/java/org/carapaceproxy/core/RuntimeServerConfiguration.java`
  - `carapace-server/src/main/java/org/carapaceproxy/server/mapper/StandardEndpointMapper.java`

## Server configuration model (important)
Carapace server config is split into two logical layers:

- **Static/boot configuration** (requires restart):
  - Read by `ServerMain` from `conf/server.properties` by default (or custom path argument).
  - Applied by `HttpProxyServer.configureAtBoot(...)` via `applyStaticConfiguration(...)`.
  - Controls mode, admin interface, user realm class, AWS creds, clustering, etc.

- **Dynamic configuration** (reloadable):
  - Source depends on `config.type`:
    - `file` -> same properties source
    - `database` -> `HerdDBConfigurationStore`
  - Applied by `applyDynamicConfiguration(...)` after validation.
  - Contains listeners, certificates, filters, backends, directors, routes, cache/logging, and many runtime knobs.

## Key boot/static properties
- `mode=standalone|cluster`
- `config.type=file|database`
- Admin interface:
  - `http.admin.enabled`
  - `http.admin.host`
  - `http.admin.port`
  - `https.admin.port`
  - `https.admin.sslcertfile`
  - `https.admin.sslcertfilepassword`
  - `admin.advertised.host`
  - `admin.accesslog.*`
- User realm:
  - `userrealm.class`
  - `userrealm.path` (when using file realm implementation)
- Cluster and ZK (when `mode=cluster`):
  - `peer.id`
  - `zkAddress`
  - `zkSecure`
  - `zkTimeout`
  - `zookeeper.*` (forwarded as additional ZK client properties)
- AWS credentials (used by ACME/Route53 integration):
  - `aws.accesskey`
  - `aws.secretkey`
- HerdDB (when `config.type=database`):
  - `db.*` (forwarded to embedded HerdDB)
  - `replication.factor`
- Cluster/testing offset:
  - `listener.offset.port` (shifts all listener ports; used in cluster tests)

## Key dynamic properties families
- Listener definitions: `listener.<n>.*`
  - Core: `host`, `port`, `ssl`, `enabled`, `defaultcertificate`
  - SSL: `sslciphers`, `sslprotocols` (default: `TLSv1.2,TLSv1.3`)
  - HTTP protocols: `protocol` (values: `HTTP11`, `H2`, `H2C`; default: `HTTP11,H2` for SSL, `HTTP11,H2C` for plain)
  - Forwarded strategy: `forwarded` (values: `preserve` [default], `rewrite`, `drop`, `if-trusted`); `trustedips` (used with `if-trusted`)
  - Keep-alive: `keepalive`, `keepaliveidle`, `keepaliveinterval`, `keepalivecount`, `maxkeepaliverequests`
  - Network: `sobacklog`
- Certificate definitions: `certificate.<n>.*`
  - `hostname`, `san`, `file`, `password`, `mode` (`static`|`acme`|`manual`), `daysbeforerenewal`
- Filter definitions: `filter.<n>.*`
  - `type` must be one of: `add-x-forwarded-for` (deprecated, use forwarded strategy on listener), `add-x-tls-cipher`, `add-x-tls-protocol`, `add-server-header`, `match-user-regexp`, `match-session-regexp`
  - Optional `match` expression (default `all`); type-specific `param`, `regexp`, `value`
- Backend definitions: `backend.<n>.*`
  - Core: `id`, `host`, `port`, `enabled`, `probePath`, `safeCapacity`
  - SSL: `ssl`, `cacertificate`, `cacertificatepassword`
  - Health probe scheme: `probescheme` (`http` [default] or `https`)
- Director definitions: `director.<n>.*`
- Route definitions: `route.<n>.*`
  - Core: `id`, `action`, `enabled`, `match`
  - Per-route fallbacks: `erroraction`, `maintenanceaction` (must reference a defined `static`-type action)
- Custom header definitions: `header.<n>.*`
  - `id`, `name`, `value`, `mode` (`add`|`set`|`remove`)
  - Headers are referenced by id in `action.<n>.headers`
- Action definitions: `action.<n>.*`
  - Core: `id`, `enabled`, `type` (`proxy`|`cache`|`static`|`redirect`|`acme-challenge`), `director`, `file`, `code`, `headers` (comma-separated header ids)
  - Redirect-specific: `redirect.location` OR `redirect.proto`, `redirect.host`, `redirect.port`, `redirect.path`
- Connection pool definitions: `connectionpool.<n>.*`
  - `id`, `domain`, `enabled`, `maxconnectionsperendpoint`, `borrowtimeout`, `connecttimeout`, `stuckrequesttimeout`, `idletimeout`, `maxlifetime`, `disposetimeout`, `keepaliveidle`, `keepaliveinterval`, `keepalivecount`, `keepalive`
  - Domain-scoped pools override global `connectionsmanager.*` defaults for matching backends
- Defaults/mapper behavior:
  - `default.action.notfound`
  - `default.action.internalerror`
  - `default.action.maintenance`
  - `default.action.badrequest`
  - `default.action.serviceunavailable`
  - `mapper.class`, `mapper.forcedirector.parameter`, `mapper.forcebackend.parameter`
  - `mapper.debug` (adds `X-Proxy-Path` routing debug header), `mapper.debug.name`
- Runtime subsystems:
  - `connectionsmanager.*`
  - `cache.*` (includes `cache.cachealways` for always-cached extensions)
  - `healthmanager.*` (includes `healthmanager.warmupperiod`, `healthmanager.tolerant`, `healthmanager.connecttimeout`)
  - `dynamiccertificatesmanager.*` (includes `domainschecker.ipaddresses`, `localcertificates.store.path`, `localcertificates.peers.ids`, `errors.maxattempts`)
  - `ocspstaplingmanager.*`
  - `ocsp.enabled`
  - `accesslog.*` (includes `accesslog.advanced.enabled`, `accesslog.advanced.body.size`, `accesslog.maxsize`)
  - `truststore.*`
  - `carapace.*` (includes `carapace.maxheadersize`, `carapace.maintenancemode.enabled`, `carapace.http10backwardcompatibility.enabled`)
  - `clients.idle.timeout`
  - `response.compression.threshold`, `request.compression.enabled`

## Practical startup and runtime behavior
- Service script starts `org.carapaceproxy.launcher.ServerMain`.
- Boot flow:
  1) read static properties
  2) read cluster mode details
  3) choose dynamic store (`file`/`database`)
  4) apply static config
  5) initialize group membership
  6) validate and apply dynamic config
  7) `server.startMetrics()` — registers Prometheus/Micrometer metrics endpoint
  8) `server.startAdminInterface()` — starts Jetty admin/API/UI server
- In cluster mode, config changes are propagated through group membership events.
- Dynamic maintenance toggle writes `carapace.maintenancemode.enabled` and reapplies dynamic config.
- HTTP/2 is supported per listener: `H2` (TLS only) and `H2C` (cleartext); defaults to `HTTP11+H2` (SSL) or `HTTP11+H2C` (plain).
- Request smuggling protection (CL.TE and CL.0 attacks) is enforced in `StandardEndpointMapper` for HTTP/1.x requests.

## Build commands
Run from repository root.

- Build all modules, skip tests:
  - `./mvnw -B package -DskipTests`
- Build all modules with tests:
  - `./mvnw -B test`
- Install all modules:
  - `./mvnw -B install`
- Production package (includes UI asset build):
  - `./mvnw clean install -DskipTests -Pproduction`
- Backend module only:
  - `./mvnw -pl carapace-server -am package`
- Quick local dev startup (server + UI with sample config):
  - `./run.sh`
- Docker image build (requires production package first):
  - `./mvnw clean install -DskipTests -Pproduction && docker/build.sh`
- Docker run (admin UI on port 8001):
  - `docker run -p 8001:8001 carapace/carapace-server:latest`
  - Pass static properties via env vars with prefix `CARAPACE_` (e.g., `-e CARAPACE_mode=cluster`)
- Single-node cluster via Docker Compose:
  - `docker compose up -d` (from `docker/`)

## Test commands (including single test)
Java tests are JUnit 4 via Surefire.

- All tests:
  - `./mvnw test`
- Backend tests only:
  - `./mvnw -pl carapace-server test`
- Single test class:
  - `./mvnw -pl carapace-server -Dtest=ForwardedStrategyTest test`
- Single test method:
  - `./mvnw -pl carapace-server -Dtest=ForwardedStrategyTest#testRewriteStrategy test`
- Pattern selection:
  - `./mvnw -pl carapace-server -Dtest='*Configuration*Test' test`

Notes:
- Prefer module-scoped tests while iterating on config code.
- Do not bypass Maven test runner; Surefire adds required JVM opens.

## Lint and static analysis
- SpotBugs (backend):
  - `./mvnw -pl carapace-server spotbugs:check`
- Maven Enforcer runs in normal build lifecycle; keep it enabled.
- UI lint (only when UI is touched):
  - from `carapace-ui/src/main/webapp`: `yarn lint`

## Code style guidelines (server-focused)

### Java style
- Keep Apache 2.0 license header in Java files.
- Naming:
  - package names lower-case
  - classes/interfaces PascalCase
  - methods/fields camelCase
  - constants UPPER_SNAKE_CASE
- Imports:
  - no wildcard imports
  - static imports first, then non-static imports
  - preserve existing grouping/order in touched file
- Formatting:
  - 4-space indentation
  - preserve current brace/wrapping style
  - avoid mass reformatting unrelated code

### Types and configuration APIs
- Use existing configuration abstractions (`ConfigurationStore`, runtime config classes).
- Preserve checked exception contracts (e.g., `ConfigurationNotValidException`).
- Prefer extending existing property families over introducing ad-hoc keys.
- Validate new/changed property values close to parsing time.

### Error handling and logging
- Fail fast for invalid configuration values.
- Include property key and invalid value in error messages when possible.
- Do not swallow exceptions silently.
- Use SLF4J (`Logger`/`LoggerFactory`), not stdout/stderr for server logic.

### Config-change safety rules for agents
- Distinguish static vs dynamic keys before implementing changes.
- If a key is static, do not claim hot-reload behavior.
- Keep backward compatibility for existing property names/defaults.
- When adding keys, update sample config templates in `src/main/resources/conf/`.
- For cluster-sensitive changes, verify both standalone and cluster paths.

## Minimal validation workflow for config-related changes
- Run one targeted test class first.
- Run broader backend tests if config parsing/reload behavior changed.
- Optionally smoke-run local startup using packaged service scripts.

## Cursor and Copilot rules
- `.cursor/rules/`: not present.
- `.cursorrules`: not present.
- `.github/copilot-instructions.md`: not present.

If those files are added, update this document and align agent behavior.
