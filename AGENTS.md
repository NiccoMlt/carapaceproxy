# AGENTS.md

Operational guide for coding agents in `carapaceproxy`.

## Project layout
- Maven multi-module repository.
- Modules:
  - `carapace-server` (Java 21, JAR, proxy runtime).
  - `carapace-ui` (WAR; Vue 2 app in `src/main/webapp`).
- Root `pom.xml` builds both modules.
- Use Maven wrapper (`./mvnw`) by default.

## Toolchain
- JDK: 21 (Temurin toolchain configured in Maven).
- Maven wrapper: 3.9.11 (`.mvn/wrapper/maven-wrapper.properties`).
- UI production profile uses Node `v20.11.1` and Yarn `v1.22.22`.

## Build commands
Run from repo root unless specified.

- Build all modules (skip tests):
  - `./mvnw -B package -DskipTests`
- Build all modules with tests:
  - `./mvnw -B test`
- Install all modules locally:
  - `./mvnw -B install`
- Production bundle (UI build + server zip):
  - `./mvnw clean install -DskipTests -Pproduction`
- Build only backend:
  - `./mvnw -pl carapace-server -am package`
- Build only UI WAR:
  - `./mvnw -pl carapace-ui -am package`

## Test commands (especially single-test)
Java tests are JUnit 4 through Surefire.

- All tests:
  - `./mvnw test`
- Backend module tests only:
  - `./mvnw -pl carapace-server test`
- Single class:
  - `./mvnw -pl carapace-server -Dtest=ForwardedStrategyTest test`
- Single method:
  - `./mvnw -pl carapace-server -Dtest=ForwardedStrategyTest#testRewriteStrategy test`
- Pattern:
  - `./mvnw -pl carapace-server -Dtest='*Cache*Test' test`

Notes:
- Prefer `-pl carapace-server` for faster feedback.
- Surefire JVM args include required `--add-opens`; use Maven tests, not ad-hoc runners.

## Lint and static analysis

### Java
- SpotBugs is configured via Maven plugin.
- Run SpotBugs check:
  - `./mvnw -pl carapace-server spotbugs:check`
- Maven Enforcer runs in normal builds; do not bypass dependency/plugin constraints.

### UI (Vue)
Run from `carapace-ui/src/main/webapp`:

- Install deps: `yarn install`
- Lint: `yarn lint`
- Build: `yarn build`
- Dev server: `yarn serve`

Focused lint example:
- `npx eslint src/components/Configuration.vue`

Note:
- `package.json` has no frontend test script; treat lint + build as UI validation baseline.

## Developer helper scripts
- Start full local stack: `./run.sh`
- Redeploy UI quickly: `./carapace-ui/deploy.sh`

## CI parity (useful defaults)
- PR validation uses JDK 21 and Maven wrapper.
- CI sequence is effectively package (skip tests) then `test`.
- Test reports come from `carapace-*/target/surefire-reports/*.xml`.

## Code style guidelines
Follow nearby code; avoid unrelated reformatting.

### Java (`carapace-server`)
- Keep Apache 2.0 license header on Java source files.
- Naming:
  - packages lower-case (`org.carapaceproxy...`)
  - classes/interfaces PascalCase
  - methods/fields camelCase
  - constants `static final` + UPPER_SNAKE_CASE
- Imports:
  - no wildcard imports
  - static imports first, then regular imports
  - keep import order consistent with surrounding files
- Formatting:
  - 4-space indentation, no tabs
  - preserve existing wrapping/brace style
  - do not mass-format untouched code
- Types and APIs:
  - prefer existing domain types/exceptions (e.g., `ConfigurationNotValidException`)
  - use checked exceptions when the existing API contract uses them
  - avoid introducing new frameworks/libraries without clear need
- Error handling:
  - fail fast on invalid configuration/state
  - wrap low-level exceptions with contextual messages at boundaries
  - never swallow exceptions silently
- Logging:
  - use SLF4J (`Logger`/`LoggerFactory`)
  - avoid `System.out`/`System.err` in production code

### Java tests
- JUnit 4 patterns (`@Test`, `@Rule`, `@Before`, `@RunWith`).
- Test classes end with `Test`.
- Prefer deterministic tests and explicit assertions.
- Reuse shared test utilities in `org.carapaceproxy.utils`.

### Vue/JavaScript (`carapace-ui/src/main/webapp`)
- Vue 2 Options API style; keep SFC structure: template/script/style.
- Keep component names PascalCase in `name` field.
- Reuse API helpers from `src/serverapi.js`.
- Keep side effects in lifecycle hooks/methods, not computed properties.
- Respect current ESLint setup (`eslint:recommended`, `plugin:vue/essential`).
- Preserve per-file quote/semicolon style; do not normalize whole files unless needed.

## Imports, dependencies, and formatting guardrails
- Prefer existing utilities over adding dependencies.
- Do not change dependency versions opportunistically.
- Keep Maven dependency exclusions/convergence choices intact.
- Keep edits minimal and scoped to the task.

## Suggested agent workflow
- Identify impacted module first (`carapace-server` vs `carapace-ui`).
- Run narrow checks first (single test class/method or file lint).
- After backend changes, run at least nearest backend tests.
- After UI changes, run `yarn lint`; run `yarn build` for non-trivial changes.
- Before handoff, verify commands are run from the correct directory.

## Cursor and Copilot rules
- `.cursor/rules/`: not present.
- `.cursorrules`: not present.
- `.github/copilot-instructions.md`: not present.

If these files are added later, update this AGENTS.md section to mirror them.
