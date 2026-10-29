# AGENTS.md — std-dive-gas-tracker (backend module)

Rules for any agent (or human) working in this module. Read fully before the first edit. The
domain spec, REST contract and task plan live in the frontend repo:

- `/Users/sebas/git/std-dive-gas-tracker-web/docs/DOMAIN.md` — what to compute (§ numbers)
- `/Users/sebas/git/std-dive-gas-tracker-web/docs/API.md` — request/response shapes
- `/Users/sebas/git/std-dive-gas-tracker-web/docs/PLAN.md` — tasks, DoD, subagent protocol
- `/Users/sebas/git/std-dive-gas-tracker-web/docs/DECISIONS.md` — decision log (append, never rewrite)

The surrounding dive-logger reactor has its own (gitignored) notes in
`/Users/sebas/IdeaProjects/std-dive-logger-backend/.claude/AGENTS.md`. Its general conventions
(NullAway, Spotless, comment style, Hibernate pitfalls) apply here too; its dive-specific content
does not.

## What this module is

A separate Spring Boot app (`ch.sthomas.stddivegastracker`, port 8090) that tracks shared
blending-gas inventory, fills and cost allocation for teams of divers. Own Postgres database
(`t_*` tables, Flyway). Users authenticate with **dive-logger** access JWTs (no passwords here).

## Keep it minimal (the human's standing instruction)

As simple and small as possible while providing the abstractions the spec needs:

- Build exactly what DOMAIN/API specify; BACKLOG sections stay unbuilt. No speculative fields,
  states, settings, endpoints, feature flags, builders, mappers, factories or "for later" hooks.
- **An interface only where there are ≥ 2 real implementations** (today:
  `calc.gas.CompressibilityModel` → `IdealGas`, `VirialFit`). Otherwise a concrete class; in
  `calc`, records + static functions. Loss policies are the `LossPolicy` enum handled inside the
  engine, not a strategy plugin.
- Prefer fewer, larger-but-cohesive types over many tiny ones; don't add a wrapper type for a
  single field or a helper class for a single call site.
- A reviewer deletes anything no DOMAIN/API section asks for.

## Java code style (the human's standard — enforced by Checkstyle + ErrorProne, task Q1)

Modern Java 25, written the way the human's own edit of `calc.allocation.AllocationEngine` reads:

```java
for (final var draw : draws) {
    if (draw.beneficiary() instanceof ToVessel(final var vesselId)) {
        drawsInto.computeIfAbsent(vesselId, _ -> new ArrayList<>()).add(draw);
    }
}
final var drawsFrom = new HashMap<Long, List<Draw>>();
```

- **`final` everywhere it can be**: every parameter, local variable, enhanced-for variable,
  catch parameter and field that isn't reassigned. Classes `final` unless designed for extension
  (JPA entities and Spring `@Configuration` classes are the exceptions).
- **`final var`** for local variables — the type goes on the right (`new HashMap<Long,
  List<Draw>>()`, a factory or method call). Explicit types only where inference can't work or
  would change the type: lambdas/method-reference targets and numeric literals that must be
  `double`/`long` (`final double litres = 0;`). A rare exception gets
  `// checkstyle: explicit-type <reason>`.
- **Records** for every data carrier (DTOs, calc inputs/outputs, value types), validation in the
  compact constructor, related records nested in the type that owns them. **Sealed interfaces**
  for closed hierarchies.
- **Pattern matching**: `instanceof` with type or **record patterns** (`instanceof
  ToVessel(final var vesselId)`), `switch` **expressions** with arrow cases, record
  deconstruction in `case`, exhaustive over sealed types/enums **without** `default` (so a new
  case fails compilation). No `if`/`else` chains on types, no casts after `instanceof`.
- **try-with-resources** for every `AutoCloseable` (`Files.lines/list/walk` streams, readers,
  `InputStream`s, `HttpClient`, JDBC objects); never a manual `close()` in `finally`.
- **Unnamed variables `_`** for unused lambda, catch and pattern variables.
- Immutable collections: `List.of`/`Map.of`/`Set.copyOf`, `Stream.toList()`; records copy their
  list components (`List.copyOf`) in the compact constructor. `Optional` only as a return type.
- Text blocks (`"""`) for multi-line SQL/JSON in code and tests.
- Constructor injection only (no field injection, no Lombok), `@Nullable` from jspecify.
- Enhanced `for` / streams over index loops unless the index is needed; a plain loop beats a
  clever stream when it reads better.

## Interfaces and contract tests

Every common interface (or enum-dispatched family) is tested **once as a contract, against every
implementation**, plus each implementation's own specifics:

- `CompressibilityModel` → `calc.gas.CompressibilityModelContractTest` (abstract): each model's
  test class extends it (`IdealGasTest`, `VirialFitTest`). A new model is not done until its test
  extends the contract and passes.
- `LossPolicy` `TRUE_UP` / `POOLED` → `calc.allocation.AllocationPolicyContractTest` runs the DOMAIN
  §7.5 invariant suite with all-TRUE_UP, all-POOLED and mixed scenarios.
- When you introduce another interface (with a DECISIONS entry), add its contract test in the same
  change. Never test an implementation only through a mock of its interface.

## Isolation (hard rules)

- **Never** import from `ch.sthomas.stddivelogger.*` and never add a dependency on another
  `std-dive-logger-*` module. `ArchitectureTest.noDiveLoggerCode` enforces the import side.
- **Never** edit other modules or the parent `pom.xml`, except where a PLAN task explicitly says
  so (B0, P1). A helper worth sharing gets a DECISIONS entry and a separate, reviewed change.
- The app scans only `ch.sthomas.stddivegastracker`; keep it that way (no `scanBasePackages`).

## Package layering (enforced by `ArchitectureTest`)

```
calc     pure domain math (DOMAIN §4–§9): java.* + jspecify only. No Spring/JPA/I/O, no logging.
model    API records/enums (model.<area>) and JPA entities (model.entity). No logic beyond
         validation in compact constructors and entity.toRecord().
data     Spring Data repositories (data.repository) + one *DataService per aggregate
         (data.service). Transactions live here. No authorization.
service  business logic + authorization (TeamAccessService). Calls data + calc.
ws       controllers (ws.controller), security (ws.auth, ws.config), exception mapping
         (ws.advice). Controllers are thin: one service call, no logic, no entities, no repos.
```

Direction is strictly downward: `ws → service → data → model → calc`. `GET/PUT /v1/me`
(`MeController` → `UserService` → `UserDataService` → `UserEntity`) is the reference slice — copy
its shape for new endpoints.

## Commands (from the reactor root)

Reactor root: `/Users/sebas/IdeaProjects/std-dive-logger-backend/std-dive-logger-backend`.
The default `java` is JDK 27, which crashes Spotless — always prefix Maven with JDK 26:

```
export JAVA_HOME=/Users/sebas/Library/Java/JavaVirtualMachines/openjdk-26.0.2.1/Contents/Home
mvn -pl std-dive-gas-tracker spotless:apply          # format (never hand-format)
mvn -pl std-dive-gas-tracker verify                  # compile + NullAway + all tests
mvn -pl std-dive-gas-tracker checkstyle:check        # fast style loop (also runs in `validate`); rare exception: `// checkstyle: explicit-type <reason>` above the line
mvn -pl std-dive-gas-tracker test -Dtest=FillSolverTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl std-dive-gas-tracker spotless:check
```

No `-am` needed (the module depends on no reactor sibling). Docker must be running (tests use
Testcontainers `postgres:18-alpine`). Add **`-o`** (offline) once dependencies are downloaded: a
transitive repository (maven.java.net, expired certificate) otherwise stalls every build for
~13 minutes on metadata checks; drop `-o` only when you add or bump a dependency.

Run locally (from **this module directory**, not the reactor root):

```
cd /Users/sebas/IdeaProjects/std-dive-logger-backend/std-dive-logger-backend/std-dive-gas-tracker
JAVA_HOME=…/openjdk-26.0.2.1/Contents/Home mvn spring-boot:run
```

Spring Boot's docker-compose support starts `compose.yaml` (Postgres on **:5460**, kept running).
Profile `dev-login` is on by default locally: `POST http://localhost:8090/api/dev/login
{"email":"you@example.test"}` → `{accessToken}`; then `Authorization: Bearer <token>`. Reset the
local DB: `docker compose down -v` in this directory. **Never** point anything at the dive
logger's dev DB on :5450 (the human's real data).

## Verification discipline

Do not report a task done unless, in this session, `spotless:apply` + `verify` ran green and the
new tests assert the spec's numbers. If something could not be run, say exactly what. A failing
check is reported with its output, never rounded up to "done".

## Conventions

- **NullAway** (`org.jspecify.annotations.Nullable`): every package needs a `package-info.java`
  with `@NullMarked` (main **and** test). NullAway does not understand AssertJ's `isNotNull()` —
  use `Objects.requireNonNull(...)` on response bodies in tests.
- **Units in names**: `…PressureBar` (gauge), `waterVolumeLitres`, `…Litres` (surface litres),
  fractions `o2`/`he` in 0..1. No unit-less `pressure`/`volume` fields.
- **Money**: `BigDecimal` only (never `double`/`float`), DB `numeric(12,2)`, engine unrounded
  (`MathContext.DECIMAL128`), round `HALF_EVEN` to 2 dp only at the API boundary. Every
  `BigDecimal` is serialised as a plain string by `JacksonConfig` — so use `BigDecimal` for money
  and nothing else.
- **Records validate inputs, not just derived values** (compact constructors; e.g. `GasMix`
  checks each fraction, not only their sum — see DOMAIN §3).
- **Errors**: throw, don't translate. `NoSuchElementException` → 404, `IllegalArgumentException`
  → 400, `IllegalStateException` → 409, `AccessDeniedException` → 403
  (`ws.advice.ExceptionHandling`). Messages are user-facing: short, specific, no SQL/class names.
- **Authorization**: every team-scoped service method starts with
  `teamAccess.requireMember(teamId, user)` / `requireAdmin(...)` (task D2). Non-members get 404,
  never 403. Never rely on a check made elsewhere in the call chain.
- **Controllers** take `@AuthenticationPrincipal GasUser user`, validate bodies with `@Valid`,
  return records from `model.*` — never entities.
- **Entities**: public no-arg constructor, `@SuppressWarnings("NullAway.Init")`, public getters
  (Hibernate proxies can't intercept private methods), explicit `@Column(name = …)`, `toRecord()`.
  `ddl-auto=validate` catches entity/migration drift in tests.
- **Hibernate pitfalls** (from the dive logger): a directly-constructed entity's collection is a
  plain list until reloaded — `flush(); clear();` before re-reading in the same transaction;
  don't trust a `mappedBy` side's in-memory reference right after inserting the owning row.
- **Read-heavy aggregation** (balances, reports): load flat rows (JPQL projections or
  `NamedParameterJdbcTemplate`) into calc inputs; don't hydrate deep entity graphs.
- **Comments**: 2–3 lines max, fragments over prose, reference `DOMAIN §x` instead of restating
  the spec. No snake_case Java identifiers.
- **No new dependencies** without a DECISIONS entry (versions not managed by the parent need an
  explicit `<version>` property in this pom).

## Migrations

`src/main/resources/db/migration/postgresql/`, `V1_0_<n>__snake_case.sql`. **Until the first
deployment (task P1), `V1_0_0__init.sql` is the only migration and is edited in place**; after
editing it, reset the local DB (`docker compose down -v`). After P1, never edit an applied
migration — add the next version. Naming: `t_<entity>`, `pk_<entity>_id BIGINT GENERATED ALWAYS
AS IDENTITY`, `fk_<entity>_id`, **an index on every FK column** (Postgres creates none),
`created_at/updated_at TIMESTAMPTZ`, enums as `VARCHAR` + `CHECK`, money `NUMERIC(12,2)`.

## Tests

- `calc`: plain JUnit 5 + AssertJ, no Spring. Golden values from DOMAIN §11 must appear
  literally in tests. Property tests use a seeded `java.util.Random` loop (no new library),
  print the seed on failure.
- Spring integration tests **extend `support.AbstractIntegrationTest`** (singleton Postgres
  container, one cached context, `RestTestClient`, `bearer(email)` helper; tokens via
  `support.TestJwts`, which signs with a test-only Ed25519 key under `src/test/resources/jwt`). Don't add per-class property overrides without need — each variant costs a
  new Spring context. Don't tag tests `slow`: this module's suite runs in the default build.
- Every endpoint: happy path, non-member → 404, non-admin → 403 where DOMAIN §10 restricts it,
  and a validation → 400 case.

## Auth (summary — see API.md "Auth")

Tokens are the dive logger's **Ed25519 (EdDSA)** access JWTs; this module holds only **public**
keys (`…auth.public-keys`, PEM, real or literal `\n` newlines; several during a rotation) and the
required `iss` (`…auth.issuer`). `ws.auth.DiveLoggerJwtDecoder` picks the key by header `kid`
(first 16 chars of base64url(SHA-256(X.509 DER)); no `kid` only works with exactly one key and no
dev key), requires `exp`, an email `sub`, a numeric `uid` and in production `email_verified: true`
(`…auth.require-email-verified-claim`), and fails startup without any key. **Identity is the
dive-logger `uid`** (`t_user.pk_user_id`); the email is a copy refreshed on every request (a stale
holder of it loses it), and invitations (PENDING members) bind to the user on their first
*verified* sign-in (`UserDataService.syncAuthenticated`, one transaction). Under profile
`dev-login` **and** `…auth.dev-login-enabled=true` an ephemeral Ed25519 key (`DevLoginKey`, new on
every start) signs `POST /api/dev/login` tokens with a stable *negative* `uid`; only that key may
carry negative ids. Never weaken any of this without a reviewed DECISIONS entry.

## Git

Never commit, push, rebase or amend. The human commits work in progress themselves, sometimes
mid-session — a clean `git status` after your edits is normal.
