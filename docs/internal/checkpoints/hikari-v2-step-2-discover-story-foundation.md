# Hikari V2 Step 2 - Discover + Story Detail Foundation

Date: 2026-09-09
Status: **TASKS 0-2 COMPLETED/ACCEPTED; TASK 3 READY TO START**

## Authority

- Design: `../../superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md`
- Implementation plan: `../../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Accepted predecessor: `hikari-v2-step-1-foundation-clean-boot.md`
- Completed/accepted execution boundary: Tasks 0-2.
- Next execution boundary: Task 3, not started.

Reviewed artifact SHA-256:

- design: `a00b2721a831f33e700323c27ea705110d1fb6b163bf91156b4c369c0eea2074`
- plan: `945a13e374306a779c6265b1fe3c7da7e713f21c81025c77cd0a7b6936d1a0ff`

## Task 0 Delta

- Admitted exactly `:catalog:domain`, `:catalog:storage`, `:catalog:runtime`, and
  `:feature:catalog` with the approved production and test dependency graph.
- Added the minimal Android-library convention and explicit `debug`, `release`,
  `benchmarkRelease`, and `nonMinifiedRelease` library variants.
- Re-admitted only the reviewed KSP, Room, Lifecycle, and Coil aliases; Room/KSP are
  owned by storage, Coil is owned by the feature, and no HTTP client/network module is admitted.
- Removed Room's unused multi-process invalidation service from the storage library manifest so
  all app variants preserve the Step 1 no-background-service startup ratchet.
- Added exact Step 2 build-surface and production package-SCC verifiers and wired them
  into `verifyArchitecture` without weakening the Step 1 app startup/manifest gates.
- Replaced the Step 1 blanket Catalog/project-edge bans with exact graph and narrow
  app import authority; the only permitted app-facing Catalog symbol is
  `app.openstory.catalog.feature.CatalogEntryPoint`.
- Updated product authority so Manga and Light Novel are enabled from Step 2 while
  Search remains later scope.
- Persisted the reviewed design and plan at their canonical paths.

No production Catalog behavior, seed source, plugin harness, remote transport, Room schema,
or UI implementation is introduced by Task 0.

## Agent-Owned Evidence

- `./gradlew :build-logic:test --no-daemon` - PASS, 60 tests, 2026-09-09.
- `./gradlew :catalog:domain:test :catalog:storage:compileDebugKotlin :catalog:runtime:compileDebugKotlin :feature:catalog:compileDebugKotlin --no-daemon` - PASS, 2026-09-09.
- `./gradlew :app:tasks --all --no-daemon` - PASS; all four merged-manifest startup
  verification tasks are registered, 2026-09-09.
- `./gradlew verifyProductionPackageStructure --configuration-cache --no-daemon`
  run twice - PASS; first run stored and second run reused configuration cache, 2026-09-09.
- `bash -n scripts/tests/v2-step2-build-surface-test.sh` under Git Bash - PASS, 2026-09-09.
- `./gradlew :app:verifyDebugMergedManifestStartup :app:verifyReleaseMergedManifestStartup
  :app:verifyBenchmarkReleaseMergedManifestStartup
  :app:verifyNonMinifiedReleaseMergedManifestStartup --no-daemon` - PASS for all four variants
  after removing Room's unused `MultiInstanceInvalidationService`, 2026-09-09.

## Required User-Owned Gate

Status: **PASS**

The first user attempt failed because `VerifyProductionPackageStructureTask` accessed
`Task.project` during execution, and the requested Step 2 script path was absent. Both issues
are remediated: the task now consumes an injected root directory property, the legacy Step 1
script is restored unchanged, and the Step 2 script exists at the requested path.

The second user attempt produced mixed evidence: the Step 2 shell gate passed, while the broad
Gradle gate exposed Room 2.8.4's transitive `MultiInstanceInvalidationService` in all four app
merged manifests. The storage manifest now removes that unused multi-process service, and the
four focused merged-manifest verification tasks pass.

```bash
./gradlew verifyArchitecture :app:verifyFoundation --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

The user returned concise `BUILD SUCCESSFUL` evidence for the requested final rerun on
2026-09-09. Combined with the previously returned complete shell-gate output, both required
Task 0 commands are accepted as PASS.

## Task 0 Self-Review

- The active policy and settings contain exactly the approved 11-module graph; production and
  test edges match the Task 0 contract, including `:app <-> :benchmark` test-only ownership.
- Android library variants are explicit for `debug`, `release`, `benchmarkRelease`, and
  `nonMinifiedRelease`, with release fallbacks and no framework plugin hidden in the convention.
- Room/KSP remain confined to `:catalog:storage`; Coil remains confined to `:feature:catalog`;
  no OkHttp, concrete HTTP transport, release seed, or plugin harness is admitted.
- App ownership remains one narrow `:feature:catalog` edge and the only permitted Catalog import
  is `app.openstory.catalog.feature.CatalogEntryPoint`.
- All four app merged-manifest verification tasks remain active. Room's unused multi-process
  invalidation service is removed without weakening the no-service verifier or allowing another
  provider, initializer, receiver, or forbidden permission.
- Package-SCC and build-surface tasks declare configuration-cache-safe inputs and remain wired
  into `verifyArchitecture` with the Step 1 identity/foundation gates.
- Task 0 adds no Catalog Kotlin/Java production behavior; the only production source artifact is
  the storage manifest override required to preserve the accepted startup boundary.

Result: **PASS; no unresolved in-scope defect found.**

## Task 1 Delta

- Added the pure-JVM Catalog domain contract for source-stable identity, strict UTF-8/scalar
  limits, typed failures, host-owned provenance, semantic section models, acquisition/read/write
  ports, publication commands, and bounded mutation diagnostics.
- Froze `SourceStoryIdV1` and cover-revision domain-separated SHA-256 framing with full lowercase
  256-bit vectors; malformed UTF-16 is rejected before encoding and opaque source identity is not
  trimmed, normalized, case-folded, or derived from mutable metadata.
- Added typed remote HTTPS locator normalization with IDNA/STD3 host canonicalization, default-443
  removal, exact raw path/query preservation, relative redirect revalidation, and rejection of
  userinfo, fragments, non-HTTPS/non-DNS authorities, controls, backslashes, and oversized input.
- After the first broad gate exposed the module policy's intentional `java.net` ban, replaced the
  initial JDK URI/IDN adapter with domain-owned HTTPS parsing, RFC-style relative dot-segment
  resolution, conservative IDNA/STD3 validation, and RFC 3492 Punycode encoding. The architecture
  policy remains unchanged and no network implementation was admitted.
- Added acquisition/publication validators for the frozen identifier, text, collection, rating,
  timestamp, semantic-section, provenance, cover-alignment, and Story Detail bounds. Public
  validation failures cross the boundary as `CatalogFailure.Validation`; cancellation cannot be
  wrapped as a Catalog failure.
- Added deterministic Popular/Latest Updates/Top Rated projection and exact 5/9/5 caps while
  preserving original rating values/scales and excluding missing or invalid eligibility signals.
- Kept `:catalog:domain` Android/Room/Compose/Coil/HTTP-client/plugin DTO-free with one-way package
  ownership matching the reviewed Task 1 file map.

## Task 1 Agent-Owned Evidence

- TDD RED: `./gradlew :catalog:domain:test --no-daemon` failed at compile time on the missing Task 1
  domain symbols, as expected, before production implementation.
- Focused final tree: `./gradlew :catalog:domain:test :catalog:storage:compileDebugKotlin
  :catalog:runtime:compileDebugKotlin :feature:catalog:compileDebugKotlin --no-daemon` - PASS,
  41 domain tests, 0 failures, 0 errors, 0 skipped after broad-gate remediation, 2026-09-09.
- Post-`ReturnCount` remediation: `./gradlew :catalog:domain:test --no-daemon` - PASS,
  `BUILD SUCCESSFUL`, 6 actionable tasks, 2026-09-09.
- `git diff --check` - PASS, 2026-09-09.
- Focused forbidden-framework scan of `catalog/domain/src` and its build file found no
  Android/Room/Compose/Coil/OkHttp/plugin/quarantined-Catalog imports, 2026-09-09.
- Focused remediation scans found zero `java.net` imports and zero production lines above the
  Detekt 120-character threshold, 2026-09-09.

## Task 1 User-Owned Gate Evidence

Final result on 2026-09-09: **PASS; Task 1 accepted.**

```bash
./gradlew verifyArchitecture detekt --no-daemon
```

- `verifyApplicationIdentity`, app structure/boot boundaries, all four merged-manifest startup
  checks, Step 2 production package structure, and Step 2 build surface passed.
- `verifyModuleBoundaries` rejected exactly three Task 1 imports: `java.net.IDN`, `java.net.URI`,
  and `java.net.URISyntaxException` from `RemoteHttpsUriV1`.
- Detekt reported 14 Task 1 errors: seven magic-number findings and seven maximum-line-length
  findings. Existing quarantined Catalog/Reader long-method/complexity diagnostics were warnings,
  not the Task 1 failure cause.
- Remediation removed all `java.net` usage without weakening module policy, named the flagged hash
  shift/mask and semantic-cap constants, wrapped all flagged long lines, and expanded domain tests
  with independent Punycode/redirect vectors.
- Second run passed `verifyApplicationIdentity`, both app source-structure checks, all four merged-
  manifest startup checks, Step 2 package/build-surface checks, and `verifyModuleBoundaries` for 11
  modules. Detekt then failed on one remaining Task 1 issue: `DnsHostCanonicalizer.encodeCodePoint`
  had three returns versus the configured limit of two. Existing Catalog/Reader long-method,
  large-class, and complexity diagnostics remained warnings and did not fail the task.
- The Punycode encoder now preserves the same three branches through one expression return; the
  focused domain vectors remain green.
- Final user-owned rerun: `./gradlew verifyArchitecture detekt --no-daemon` - PASS,
  `BUILD SUCCESSFUL`, 2026-09-09. The pre-existing Catalog/Reader complexity diagnostics remain
  non-blocking warnings under the repository Detekt policy.

## Task 1 Self-Review

- Golden IDs/revisions use exact domain prefixes, NUL separator, unsigned big-endian length
  framing, full SHA-256 output, and strict UTF-8; cover URI revision uses the frozen 4,096-character
  locator ceiling rather than incorrectly treating it as a byte ceiling.
- Validation distinguishes malformed input, over-limit input, authority mismatch, and invariant
  violations; acquisition payloads contain no source key/version/timestamp or trusted revision
  authority that could impersonate the host binding.
- Publication commands revalidate scalar/collection shape, contiguous section positions,
  duplicate positions/Stories, source/media/version alignment, and cover locator/key alignment
  before the future storage boundary.
- Semantic projection has deterministic ordering and bounded output; no V1 canonical/fusion type,
  framework exception, network implementation, persistence implementation, or UI concern entered
  the domain module.
- The custom locator parser preserves absolute raw path/query identity, normalizes dot segments
  only while resolving relative redirects, canonicalizes IDNA dot/case forms, rejects leading
  combining marks and unsafe authorities, and is covered by independent `bücher`, `mañana`,
  Japanese, mixed-hyphen, and sharp-s vectors.
- No unresolved in-scope defect was found in the Task 1 dependency cone.

## Task 2 Delta

- Added the new `hikari-v2-catalog.db` Room schema baseline at version 1 with exactly the four
  Task 2 product tables: `catalog_source_state`, `story_source_identity`,
  `story_source_summary`, and `discover_card`; no V1 entity or migration chain is imported.
- Added database-backed uniqueness for exact `(source_key, source_story_id)`, `story_id`,
  Discover section positions, and per-section Story membership, with indexed current-generation
  lookup and foreign-key ownership.
- Added one atomic Discover publication transaction that revalidates the bounded command at the
  storage boundary, verifies exact source identity fail-closed, upserts identity/summary data,
  writes one bounded materialized generation, advances durable source state, and removes the
  obsolete generation. First publication is generation 1; `Long.MAX_VALUE` fails closed rather
  than wrapping.
- Added one coherent Room Flow query whose left side is `catalog_source_state` and whose only
  observed join is the bounded `discover_card` generation. It preserves `Absent` versus durable
  `Published(empty)` and orders `POPULAR -> LATEST_UPDATES -> TOP_RATED`, then item position,
  without observing Story summary/detail tables.
- Added lazy `CatalogStorageFactory.open()` ownership, open/read/write failure translation,
  cancellation preservation, and idempotent `RoomCatalogStore.close()`.
- Added real Room instrumentation contracts for fresh/empty/reopen state, materialized field
  round-trip, deterministic ordering, one coherent SQL query per invalidation snapshot, absence
  of detail/child-table read requirements, database uniqueness, typed identity collision,
  storage-boundary position/cap rejection, and generation overflow preservation.

## Task 2 Agent-Owned Evidence

- TDD RED: `./gradlew :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` failed at
  compile time on the missing Task 2 DB/store/factory symbols before production implementation,
  as expected, 2026-09-09.
- Focused GREEN: `./gradlew :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS,
  `BUILD SUCCESSFUL`, 20 actionable tasks, 2026-09-09.
- Focused final gate: `./gradlew :catalog:storage:assembleDebug
  :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS, `BUILD SUCCESSFUL`,
  36 actionable tasks on the final source tree, 2026-09-09.
- Post-Detekt remediation compile: `./gradlew :catalog:storage:assembleDebug
  :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS, `BUILD SUCCESSFUL` in 23s,
  36 actionable tasks, 2026-09-09.
- Generated schema review confirms version 1 contains only the four Task 2 product tables, both
  identity uniqueness authorities, both Discover uniqueness authorities, and the intended
  foreign keys/indices.
- Generated DAO review confirms the observer tracks only `catalog_source_state` and
  `discover_card` and executes one `LEFT JOIN discover_card` query for each invalidation read.
- Focused source scan found no forbidden Compose/Coil/OkHttp/`java.net`/quarantined Catalog
  imports, no production or test Kotlin line over 120 characters, and no trailing whitespace.

## Task 2 Required User-Owned Gate

Status: **PASS**

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.DiscoverPersistenceInstrumentedTest,app.openstory.catalog.storage.CatalogIdentityIntegrityInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

The focused connected Room command is accepted as user-reported PASS on one available Android
target. The first broad command verified every architecture check, then Detekt failed on four
Task 2 findings: two generic catch boundaries, a three-throw mapping helper, and the intentionally
deferred retention parameter. The source preserves the same failure/cancellation semantics
through value-based failure mapping, removes both generic catches, and narrowly documents the
Task 4-owned unused parameter. The user reran `./gradlew verifyArchitecture detekt --no-daemon` on
the remediated tree and reported `BUILD SUCCESSFUL`; Task 2 is accepted. Task 13 later repeats Room
behavior on API 26 and 37.

## Task 2 Self-Review

- The hot-path observer is one state-left-join-card SQL shape; summary/detail changes cannot
  invalidate it, an inner join cannot collapse an empty publication, and no broad JSON/blob or
  per-card read contract exists.
- Publication validation occurs before the transaction and is repeated from an immutable card
  snapshot at the store boundary; all DB mutation then commits or rolls back as one Room
  transaction. Typed validation/identity failures remain specific, while unrelated framework
  failures map to the matching storage operation and caller cancellation is rethrown.
- Identity has both exact source-key and derived Story-ID uniqueness. A mismatched persisted
  triple fails as `IdentityCollision` without suffix, random, or insertion-order repair.
- Primary/index order covers the bounded current-generation query and both duplicate membership
  invariants. Section/card cap rejection is enforced through the only product write boundary,
  not an invented DAO bypass or unsupported Room check annotation.
- The exported schema starts at version 1 and contains no V1 migration history, detail children,
  retention table, startup initializer, service, provider, network dependency, or app-owned DB
  access. Factory construction performs no Room work; opening is explicit and final close is
  idempotent.
- An independent review dispatch was attempted under the review skill but the child runtime had
  no active provider credentials. The root therefore completed the required changed-cone review
  directly; no unresolved in-scope source defect was found before the open device/broad gates.

## Later Task Status

Tasks 0-2: **COMPLETED/ACCEPTED**. Task 3: **READY TO START**.
Tasks 4 through 16: **NOT RUN**.

## Risks / Open Checks

- Task 2 has no remaining open gate or unresolved in-scope risk.
- API 26/API 37 repetition, later device/UI, performance, profile, and plugin-integration gates
  remain owned by later tasks and are `NOT RUN`.

## Exact Resume Boundary

Start Step 2 Task 3 at plan Step 1: write RED keyed Story Detail persistence and bounded
orphan-retention tests. Re-read the plan global constraints and Task 3 section before changing
code. Do not reopen Tasks 0-2 without a regression, contradiction, or dependency trail.
