# Hikari V2 Step 2 - Discover + Story Detail Foundation

Date: 2026-09-10
Status: **TASKS 0-10 COMPLETED/ACCEPTED; TASK 11 NOT RUN**

## Authority

- Design: `../../superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md`
- Implementation plan: `../../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Accepted predecessor: `hikari-v2-step-1-foundation-clean-boot.md`
- Completed/accepted execution boundary: Tasks 0-10.
- Active canonical execution boundary: Task 11, `NOT RUN`.

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

## Task 3 Delta

- Added the keyed Story Detail Room schema: `story_detail` plus position-keyed `story_author`,
  `story_artist`, and `story_genre` tables. Detail source version/acquisition time remains separate
  from the mutable Discover summary, and ordered child values are reconstructed by persisted
  position.
- Added a `StorySourceRef`-keyed transactional observer. Room's generated implementation performs
  one joined identity/summary/detail query plus exactly one bounded relation query for each child
  table, for four SQL statements per coherent snapshot independent of unrelated Catalog rows.
- Added one atomic `publishStoryDetail` transaction that snapshots mutable lists, repeats complete
  validation at the storage boundary, verifies exact persisted identity, upserts summary/detail,
  replaces all bounded children, preserves monotonic access time, and maps unrelated rollback
  failures to `Storage(PUBLISH_STORY)` without changing materialized Discover cards.
- Added explicit access-touch storage behavior with no observation-driven writes. Touch and later
  detail publication advance an existing orphan candidate's aging timestamp monotonically so
  retention order cannot become stale.
- Added the storage-internal `story_orphan_retention` DAO/index and atomic release cleanup. It uses
  only keyed current-Discover reachability/detail checks plus `LIMIT 65`/`LIMIT 1` indexed orphan
  queries, retains at most 64 candidates after commit, excludes active protected IDs from eviction,
  and deletes unreachable no-detail or evicted Story rows through identity-owned cascades.
- Added instrumentation contracts for summary-before-detail, full detail/child round-trip,
  four-query shape with 250 unrelated rows, atomic rollback, provenance independence, Discover
  isolation, storage-boundary revalidation, explicit access writes, aged 500-row retention query
  shape, the 64-row cap, release cleanup, current reachability, and child-position uniqueness.

## Task 3 Agent-Owned Evidence

- TDD RED: `./gradlew :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` failed on the
  missing Task 3 Story Detail/retention schema and store APIs before production implementation, as
  expected, 2026-09-09.
- Focused GREEN compile: `./gradlew :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` -
  PASS (exit 0), 2026-09-09.
- Focused final gate: `./gradlew :catalog:storage:assembleDebug
  :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS, `BUILD SUCCESSFUL in 24s`,
  36 actionable tasks (2 executed, 34 up-to-date), 2026-09-09.
- User-run RED evidence: the selected connected suite ran 14 tests and failed only
  `oneStorySnapshotUsesFourQueriesRegardlessOfUnrelatedRows`, with `expected:<4> but was:<5>`.
  Generated Room 2.8.4 code confirmed the `@Relation` adapter stepped the parent SELECT once to
  collect relation keys, reset it, and stepped it again to materialize the row before executing
  the three child SELECTs.
- Remediation replaces the generated relation adapter with an invalidation-tracked observer whose
  snapshot read is one explicit Room transaction: one keyed parent DAO query plus one ordered
  authors, artists, and genres query. The same instrumentation assertion remains the RED/GREEN
  behavioral contract.
- The same user-run broad gate passed the architecture checks but Detekt failed on the Task 3
  `evictOneOverflow` helper's three returns. The helper now selects a nullable eviction and has one
  final return without changing the bounded overflow behavior.
- Post-remediation focused gate: `./gradlew :catalog:storage:assembleDebug
  :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS (exit 0), 2026-09-09.
- Generated schema review confirms the five Task 3 tables, detail route uniqueness, child primary
  keys/foreign keys, and `(last_accessed_epoch_ms, story_id)` retention index.
- Post-remediation generated DAO review confirms each of the four Story SELECT methods executes its
  statement once; the storage observer composes them inside one transaction. No production
  retention query contains global `COUNT(*)` or history-wide `ORDER BY story_detail`.

## Task 3 Required User-Owned Gate

Status: **PASS / ACCEPTED**

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.StoryDetailPersistenceInstrumentedTest,app.openstory.catalog.storage.StoryRetentionInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

The first returned results exposed and drove the two remediations recorded above. The user rerun
on the remediated tree reports `BUILD SUCCESSFUL` for both required commands, 2026-09-09. Task 3 is
accepted. Task 13 later repeats Room behavior on API 26 and API 37.

## Task 3 Self-Review

- Story reads are keyed by the full validated route identity and observe only the selected summary,
  optional detail, and its three bounded child tables. The generated query set is fixed at four;
  unrelated row count cannot amplify SQL statement count or result cardinality.
- Complete detail state publishes inside one Room transaction. A forced child insert failure rolls
  back description, authors, artists, genres, provenance, and summary together; detail enrichment
  never writes `discover_card`.
- Detail provenance is owned by `story_detail` and survives later Discover summary/source-version
  publication. Summary-only projections remain valid before enrichment.
- Retention work is driven only by the released Story plus a maximum 65-row indexed candidate read
  and one indexed unprotected eviction lookup. No production global Story/detail enumeration,
  count, or history sort was introduced.
- Observation performs no access write. Explicit touch/detail refresh keeps both detail and an
  existing orphan marker monotonic, avoiding reactive write loops and stale eviction order.
- DAO-shaped retention methods and Room entities remain module-internal. No runtime/app/network,
  startup, V1 quarantine, or new dependency edge was introduced.
- Independent review dispatch was attempted under the review skill but the child runtime returned
  `404 No active credentials for provider: openai`. The root completed the required changed-cone
  review directly; the final connected behavior and broad architecture/Detekt reruns are accepted.

## Task 4 Delta

- Added the runtime-owned immutable `CatalogSourceBinding`, injectable CPU/I/O dispatchers, and
  `CatalogImporter`. Discover/detail payloads are snapshotted and completely validated on the CPU
  dispatcher; provenance comes only from the binding plus host timestamp. Story IDs, semantic
  ordering/caps, normalized cover locators, and stable cover revisions are derived before entering
  the mutation gate or Room.
- Added one coroutine `CatalogMutationGate` shared by Discover publication and active Story pin
  transitions. `ActiveStoryPins` is an in-memory active-demand set capped at exactly two distinct
  refs; registration completes before route exposure can continue, publication snapshots pins
  under the same gate, and release removes the pin then invokes one atomic keyed cleanup with only
  the remaining protected IDs.
- Reworked Discover identity persistence into bounded bulk reads/insertion/revalidation. Identity
  collision remains fail-closed and typed, with no suffix/random/retry repair. Existing summary
  content type cannot be rewritten through another Discover media scope or Story Detail command.
- Extended the atomic Discover transaction with delta-driven retention: remove newly reachable
  orphan markers, classify only removed bounded IDs through keyed any-current-Discover/detail/pin
  checks, retain pinned removed rows as durable candidates, delete unreachable summary-only rows,
  and evict only bounded oldest unprotected candidates until the ledger is at most 64.
- Added post-mutation invariant guards for the two-pin protection snapshot, 57-ID publication
  diagnostic bound, two-ID release diagnostic bound, and 64-row orphan ledger. No publication or
  release query enumerates historical Story/detail rows.
- Added focused runtime tests for host provenance, zero/content publication, deterministic section
  order, remote cover normalization, typed collision propagation without retry, detail authority,
  CPU dispatch, pin cap/release snapshots, exclusive mutation ordering, and both publication/pin
  race orderings.
- Expanded the real-Room Task 4 fixture for atomic rollback, obsolete-generation deletion,
  storage-boundary duplicate rejection, raw collision injection, content-type consistency,
  fixed semantic summary priority, cross-media reachability, pinned/detail/summary-only branches,
  post-unpin deletion, exact 57-touch maximum replacement, 64-row retention, abandoned-pin later
  eviction, and preservation of unrelated aged history.

## Task 4 Agent-Owned Evidence

- TDD RED: `./gradlew :catalog:runtime:testDebugUnitTest --tests '*CatalogImporter*' --tests
  '*CatalogMutationGate*' --no-daemon` failed on the missing Task 4 runtime types and importer APIs,
  as expected, 2026-09-09.
- Focused GREEN: `./gradlew :catalog:runtime:testDebugUnitTest --tests '*CatalogImporter*' --tests
  '*CatalogMutationGate*' --tests '*ActiveStoryPins*' --no-daemon` - PASS (exit 0), 2026-09-09.
- Storage build/connected-test compile: `./gradlew :catalog:storage:assembleDebug
  :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` - PASS (exit 0), 2026-09-09.
- Post-user-failure remediation rerun of the same storage assemble/instrumentation compile command
  - PASS (exit 0), 2026-09-09.
- Final closure gate: focused runtime importer/mutation/pin tests plus storage assemble and
  instrumentation compile in one Gradle invocation - PASS (exit 0), 2026-09-09.
- One attempted verification invocation included a nonexistent
  `:catalog:storage:compileDebugPAndroidTestKotlin` task and failed during command-line task
  selection before compilation. The diagnostic named the valid
  `compileDebugAndroidTestKotlin` candidate; the corrected command above passes.

## Task 4 Required User-Owned Gate

Status: **PASS / COMPLETED / ACCEPTED**

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.DiscoverPublicationRetentionInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

The first returned connected run executed 12 tests on Redmi Note 9S / API 35. Ten passed; two
content-type authority tests failed during setup before reaching storage because their initial
cards retained the helper default `sourceVersion = "discover-v1"` while their publication
provenance used `"manga-v1"`. Both setup cards now explicitly use `"manga-v1"`. The first returned
broad run passed the reported architecture checks and failed Detekt only on one blocking
`MaxLineLength` finding in `RoomCatalogStore.requireConsistentStoredContentTypes`; the condition is
now split without changing behavior. The focused storage assemble/instrumentation compile rerun
passes after both remediations. The user then reported `BUILD SUCCESSFUL` for both required reruns,
so the connected Task 4 behavior and broad architecture/Detekt gates are accepted. API 26/API 37
repetition remains owned by Task 13.

## Task 4 Self-Review

- Slow/untrusted validation, projection, hashing, URI normalization, and cover revision derivation
  complete before the mutation gate. The gate contains only active-pin set transitions and one
  bounded Room mutation; it contains no source/network/image/UI/user wait.
- Publication performs bounded bulk identity/summary/card work, reads only the previous current
  generation, and classifies only removed IDs. Retention uses keyed reachability/detail queries and
  indexed `LIMIT 65`/`LIMIT 1` candidate reads; 1,000 unrelated summary rows in the connected
  fixture are outside the physical work set.
- The same ref may keep section-local cards in multiple semantic sections, while storage chooses
  summary authority by fixed `POPULAR -> LATEST_UPDATES -> TOP_RATED` priority then position.
  Cross-publication content-type disagreement fails before overwriting the summary.
- Caller cancellation propagates through CPU dispatch, mutex acquisition, write-port calls, and
  storage failure translation unchanged. Typed validation/collision/invariant failures are not
  remapped to generic storage failures.
- Removed pinned Stories enter the durable bounded candidate ledger before publication commits;
  normal release reclassifies them under the same gate, and a later bounded mutation can evict an
  abandoned process-death candidate after it is no longer protected.
- Runtime packages preserve the planned leaf/shared DAG and add no app, feature, plugin, network,
  image, startup, or quarantine dependency edge. Room/DAO implementation remains storage-internal.
- Independent review dispatch was attempted with the requested high-risk reviewer profile, but the
  child runtime returned `404 No active credentials for provider: openai`. Per repository model
  routing, the root did not silently retry on an unverified profile and completed the changed-cone
  review directly.

## Task 5 Delta

- Added `CatalogRuntimeFactory` and one activity/capability-owned `CatalogCapabilitySession`.
  Construction is inert; an absent release binding returns typed `SourceUnavailable` without
  opening Room, while an admitted binding opens one storage handle asynchronously on first demand
  and reuses the activation result.
- Added immutable binding-backed `SourceAssetPolicyProvider`; runtime uses only the binding source
  key/version plus the injected host clock when accepting acquisition data. Source payloads cannot
  provide provenance authority.
- Added `CatalogAcquisitionExecutor` with active-only single-flight keys for Discover
  `(source, mediaType)` and Story Detail `StorySourceRef`. Joined work shares one source execution;
  success, failure, and cancellation remove the terminal map entry. Caller/source cancellation is
  rethrown, source failures map to typed acquisition failures, and write failures remain typed
  storage failures.
- Added keyed `DiscoverSession` observation/bootstrap. Only durable `Absent` can claim the one
  automatic bootstrap attempt; `Published(empty)` and `Published(content)` never bootstrap. A
  known binding without an acquisition source exposes `SourceUnavailable`, and media switching
  affects only the selected persistence key.
- Added keyed `StoryDetailSession` ownership. A validated ref is pinned before one semantic access
  touch and before observation; failed touch rolls the pin back under the mutation gate. Missing
  detail starts one keyed acquisition, cached detail does not, explicit retry can rerun after a
  terminal failure, and ref/binding mismatch fails before source execution.
- Discover and Story state collectors share one active persistence observer per session/key via
  `WhileSubscribed`; Story sessions are cached by ref and removed after successful demand release,
  preventing observer/session growth across historical Story visits.

## Task 5 Agent-Owned Evidence

- TDD RED: the unfiltered runtime unit command failed on the missing factory/session/executor APIs,
  as expected, 2026-09-09.
- Additional RED regressions reproduced duplicate observers across collectors/keyed Story demand,
  retained released Story sessions, raw write-failure leakage, pin leakage after failed access
  touch, and acquisition incorrectly starting after a Story read failure.
- Exact focused gate: `./gradlew :catalog:runtime:testDebugUnitTest --tests
  '*CatalogCapabilitySession*' --tests '*DiscoverSession*' --tests '*StoryDetailSession*' --tests
  '*CatalogAcquisitionExecutor*' --no-daemon` - PASS, 24 tests, exit 0, 2026-09-09.
- Widened direct dependency-cone gate adding `CatalogImporter`, `ActiveStoryPins`, and
  `CatalogMutationGate` - PASS, 36 tests, exit 0, 2026-09-09.
- Static changed-cone checks pass `git diff --check`, find no Kotlin line over 120 characters, and
  find no WorkManager, AndroidX Startup, `GlobalScope`, or Main-dispatch runtime owner.

## Task 5 Required User-Owned Gate

Status: **PASS / COMPLETED / ACCEPTED**

```bash
./gradlew :catalog:runtime:testDebugUnitTest verifyArchitecture detekt --no-daemon
```

The user reported `BUILD SUCCESSFUL` for the required unfiltered runtime unit suite plus
`verifyArchitecture detekt` command on 2026-09-09. The returned evidence is accepted, so Task 5 is
completed/accepted.

## Task 5 Self-Review

- Room remains unopened before explicit activation, and no absent binding fabricates a source key
  or development seed. The runtime owns no process/global singleton, startup component, worker,
  scheduler, or Main-dispatch work.
- Source work and storage opening run on the injected I/O dispatcher; validation/projection remains
  in the existing importer CPU owner. The mutation gate is not held across source acquisition,
  observation, or user wait.
- Discover bootstrap authority is persistence-state based, not card-count based. Repeated `Absent`
  emissions and concurrent callers cannot create duplicate work, while both forms of durable
  `Published` state remain terminal for automatic bootstrap.
- Story source identity is carried only by the validated `StorySourceRef`; no mutable current-source
  state exists. The pin/access-touch ordering is explicit, touch failure cannot consume the two-pin
  budget, and one demand session cannot write access metadata on observer emissions.
- Persistence observers and acquisition maps are keyed and active-only. Released Story sessions are
  removed from the capability cache; shared flow collection does not duplicate Room observers.
- Framework/raw source, read, open, and write exceptions do not enter feature-facing state.
  Cancellation remains cancellation and does not become a user issue.

## Task 6 Delta

- Added the feature-owned `CatalogVariantBinding` contract. Debug resolves one deterministic local
  binding/source, `benchmarkRelease` resolves one deterministic benchmark binding/source,
  `nonMinifiedRelease` explicitly reuses the benchmark Kotlin/resources/manifest directories, and
  release resolves a source-free `binding = null`.
- Added typed MANGA and LIGHT_NOVEL fixtures with exact 5/9/5 semantic-section memberships,
  bounded Story Detail payloads, Unicode source IDs, stable logical local-cover IDs/versions, and
  eight real compressed WebP resources. Android resource integers exist only in the variant-local
  logical asset resolver and are never persisted.
- Added explicit runtime Discover acquisition on an activated capability plus Context-owned runtime
  construction so feature composition can drive the existing executor/importer/storage path without
  adding a `:feature:catalog -> :catalog:storage` edge.
- Added public benchmark/profile-source-set-only `BenchmarkCatalogFixture.prepare(context)`. It
  imports both enabled media snapshots through `CatalogAcquisitionExecutor -> CatalogImporter ->
  Room`, waits until matching provenance is durably observable, closes the short-lived session, and
  only then allows `BenchmarkLaunchStateActivity` to expose its ready marker.
- Extended the Step 2 build-surface verifier and shell gate to enforce concrete variant bindings,
  exact benchmark source reuse, no duplicate `nonMinifiedRelease` fixture, compressed fixture
  assets, required variant inputs, and release AAR cleanliness by inspecting actual AAR/class/resource
  entries.

## Task 6 Agent-Owned Evidence

- TDD RED: four build-surface tests failed on missing variant/mapping/release policies; the runtime
  test failed on the missing explicit acquisition API; debug and benchmark fixture tests failed with
  `ClassNotFoundException` while concrete variant bindings were intentionally absent.
- RED/GREEN verifier cycles also proved missing/invalid WebP assets and a concrete binding that does
  not implement `CatalogVariantBinding` are rejected.
- Fresh final focused cone:
  `./gradlew :build-logic:test :catalog:runtime:testDebugUnitTest
  :feature:catalog:testDebugUnitTest :feature:catalog:testBenchmarkReleaseUnitTest
  :feature:catalog:compileDebugKotlin :feature:catalog:compileReleaseKotlin
  :feature:catalog:compileBenchmarkReleaseKotlin
  :feature:catalog:compileNonMinifiedReleaseKotlin :app:compileBenchmarkReleaseKotlin
  :app:compileNonMinifiedReleaseKotlin --no-daemon` - PASS, exit 0, 2026-09-09.
- The fresh test results contain 67 build-logic tests, 37 runtime tests, and one fixture test in each
  of debug and benchmarkRelease, all with zero failures/errors.
- Static changed-cone checks pass `git diff --check`, find no Kotlin line over 120 characters, and
  confirm all eight fixture assets have `RIFF`/`WEBP` headers.

## Task 6 Required User-Owned Gate

Status: **PASS / COMPLETED / ACCEPTED**

```bash
./gradlew :feature:catalog:assembleDebug :feature:catalog:assembleRelease \
  :feature:catalog:assembleBenchmarkRelease :feature:catalog:assembleNonMinifiedRelease \
  verifyArchitecture --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

The user reported `BUILD SUCCESSFUL` for the four-variant assemble plus `verifyArchitecture`
command on 2026-09-09. Direct inspection confirms all four AARs exist, benchmark/non-minified AARs
are byte-identical and contain the benchmark source/fixture, debug contains its local source, and
release contains no seed/fixture/plugin-harness class or resource.

Post-gate inspection exposed one shell-test false negative: AGP writes `drawable-nodpi` inputs as
`res/drawable-nodpi-v4/...` AAR entries, while the script required the unqualified directory name.
The matcher now accepts the canonical optional `-vN` qualifier; Git Bash syntax and the corrected
matcher pass against the assembled debug/benchmark/non-minified AARs. The user then reported
`BUILD SUCCESSFUL` for the corrected shell-gate rerun on 2026-09-09:

```bash
bash scripts/tests/v2-step2-build-surface-test.sh
```

The returned Gradle, artifact, and corrected shell evidence is accepted, so Task 6 is
completed/accepted.

## Task 6 Self-Review

- Release/main contains only the shared interface plus the release-null object; all typed source
  implementations, fixture data, benchmark preparation, and bundled covers stay in non-release
  source sets. `nonMinifiedRelease` owns no duplicate source tree.
- Both media fixtures cross the real validator/projection/importer/write boundary and remain at 19
  memberships each. Repeated Story memberships are identity-consistent, details are bounded, and
  source lookup remains keyed by host-authoritative source identity.
- Benchmark preparation performs no direct DAO writes, waits after each successful import for a
  matching durable publication, and closes the capability session. Room remains lazy until this
  explicit benchmark setup demand.
- The feature gains no direct storage dependency. The runtime addition delegates to the existing
  single-flight executor and preserves typed failure/cancellation behavior.
- Build-surface policy reads actual variant inputs and validates compressed asset signatures. AAR
  review plus the corrected shell gate confirms release cleanliness and benchmark/non-minified
  resolution.

## Task 7 Delta

- Replaced the static app-owned returning `HomeShell` with the public no-argument
  `CatalogEntryPoint`; `:app` now imports only that narrow feature boundary and owns no Catalog
  runtime, storage, source, image, or variant wiring.
- Gated Catalog composition on both resolved `AppLaunchState.Ready` and completion of the first
  application-owned frame. FirstRun still transitions only after
  `markInitialSetupCompleted()` succeeds, and `destination-ready` is not marked before that same
  first-frame boundary.
- Added feature-owned composition that resolves `VariantCatalogBinding`, lazily creates and closes
  one `CatalogCapabilitySession`, activates it asynchronously, maps source-unavailable/failure to
  bounded feature state, and exposes the Task 7 Discover root without pulling Task 8 presentation
  into scope.
- Added Android-free `CatalogTrace`/`CatalogTraceSink` authority under `:catalog:runtime` with all
  seven frozen Step 2 labels. `AndroidCatalogTraceSink` is the feature-only Android adapter, and
  Task 7 emits only `HikariV2:catalog-activation-start`.
- Added debug-only activation/storage/acquisition/image-init diagnostics and the connected handoff
  test for pre-Ready zero work, returning Ready launch, and FirstRun persistence ordering. Updated
  existing startup UI tests and benchmark/profile journeys from the deleted `startup-home` tag to
  the feature-owned `catalog-discover` destination.

## Task 7 Agent-Owned Evidence

- TDD RED: the focused app host suite failed three tests because the entry point/composition did not
  exist and `StartupGate` still selected `HomeShell`.
- Self-review RED: focused app-shell tests failed on the stale benchmark `startup-home` target and
  on `destination-ready` not being gated by `firstFrameReached`; both regressions are remediated.
- Fresh canonical focused gate:
  `./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*'
  --tests '*StartupTraceContractTest*' :app:compileDebugAndroidTestKotlin
  :feature:catalog:compileDebugKotlin --no-daemon` - PASS, exit 0, 2026-09-09.
- Fresh widened changed-cone gate:
  `./gradlew :feature:catalog:testDebugUnitTest :feature:catalog:compileReleaseKotlin
  :feature:catalog:compileBenchmarkReleaseKotlin
  :feature:catalog:compileNonMinifiedReleaseKotlin :benchmark:compileBenchmarkReleaseKotlin
  :benchmark:compileNonMinifiedReleaseKotlin --no-daemon` - PASS, exit 0, 2026-09-09.
- Static changed-cone checks pass `git diff --check`, find no Kotlin line over 120 characters, leave
  exactly one `:app` production Catalog import (`CatalogEntryPoint`), and find no remaining
  production/caller `HomeShell`, `startup-home`, or `HOME_TAG` Kotlin reference; the only
  `HOME_TAG` text is the negative contract assertion.
- User-returned connected gate: `CatalogLaunchHandoffTest` passes 3/3 on Redmi Note 9S/API 35;
  `BUILD SUCCESSFUL`, 2026-09-09.
- The first user-run `:app:verifyFoundation verifyArchitecture` gate failed only at
  `:app:verifyAppStructure` with
  `v2_structure.package_cycle: packages=app.openstory,app.openstory.startup.ui`. Root-cause tracing
  showed that the verifier treated the external
  `app.openstory.catalog.feature.CatalogEntryPoint` import as a dependency on the local ancestor
  package `app.openstory`, creating a false reverse edge to `MainActivity`'s real
  `app.openstory -> app.openstory.startup.ui` edge.
- TDD remediation evidence: the exact external-import/root-package fixture failed before the fix;
  the complete `AppStructuralVerifierTest` class passes after the fix. Fresh
  `./gradlew :app:verifyAppStructure --no-daemon` passes and verifies 11 production files, exit 0,
  2026-09-09.
- User-returned broad rerun: `./gradlew :app:verifyFoundation verifyArchitecture --no-daemon` -
  `BUILD SUCCESSFUL`, 2026-09-09. Both required Task 7 user-owned gates are accepted as PASS.

## Task 7 Required User-Owned Gate

Status: **PASSED / ACCEPTED**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.startup.CatalogLaunchHandoffTest \
  --no-daemon
./gradlew :app:verifyFoundation verifyArchitecture --no-daemon
```

Both returned gate results are reviewed and accepted as PASS. Task 7 is completed/accepted; this
closure turn does not execute Task 8.

## Task 7 Self-Review

- Startup ordering is explicit: resolving Ready cannot compose or trace the Catalog destination
  until the first-frame callback has completed; failed FirstRun persistence retains FirstRun and
  therefore cannot create a session or increment any Catalog work counter.
- Session/runtime construction remains feature-owned and demand-scoped. Release/null binding
  activates to typed source-unavailable without opening Room; debug storage opens only inside
  asynchronous `session.activate()` after the feature entry is composed.
- `:app` has no new dependency edge or implementation import. Trace names are unique, Android-free
  in runtime, and Android tracing stays in the feature adapter.
- Debug diagnostics are absent from release/main and observe real activation/storage/source entry
  points. Task 7 initializes no image loader, so its debug image-init count remains structurally zero
  until the later image task adds real instrumentation.
- Direct deleted-surface callers are migrated, including startup instrumentation and benchmark/
  profile wait targets. Task 8 UI behavior, device execution, and broad foundation/architecture
  acceptance remain deliberately outside agent-owned execution here.
- The structural-verifier root-cause expansion remains build-logic-only: local package edges still
  resolve to the most specific declared owner, including nested declared types, while imports owned
  by another Gradle module no longer fall back to a broad local package ancestor.

## Task 8 Delta

- Replaced the Task 7 static activation status with a bounded Discover presentation state that
  distinguishes `NoContentLoading`, durable `Empty`, retained `Content`, and
  `NoContentFailure`. Published content remains visible during refresh and read/acquisition failure.
- Added one ViewModel-owned Catalog runtime session. Manga is the default for each newly created
  ViewModel, ordinary configuration recreation retains the selected medium through the existing
  ViewModel, and process-death-style ViewModel recreation defaults to Manga without
  `SavedStateHandle`, DataStore, or another durable selection store.
- Added both enabled Manga and Light Novel controls. Selection cancels the prior collector and
  observes only the selected media scope; the feature-facing runtime adapter exposes Discover
  observation/refresh only, so card rendering and media selection cannot issue Story Detail calls.
- Added safe typed issue mapping with only `CatalogIssueKind` plus retryability. Cancellation is
  rethrown, raw exception/source/URL/payload text is not retained, and source/validation/identity/
  acquisition/storage/artwork/internal failures map to bounded feature-owned kinds.
- Added a single-`LazyColumn` Discover surface with semantic Popular, Latest Updates, and Top Rated
  sections, stable section/card keys and tags, partial-section omission, geometry-shaped loading
  skeletons, placeholder/local visual slots, accessible card labels, and no nested vertical owner.
  Presentation defensively caps each section at 5/9/5 and the selected snapshot at 19 memberships.
- Moved `CatalogCapabilitySession` lifetime from composition-local `remember` ownership into the
  ViewModel runtime adapter, preserving the Task 7 post-Ready activation trace/diagnostics while
  closing the session only when the ViewModel is cleared.

## Task 8 Agent-Owned Evidence

- TDD RED: `:feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*'` failed because the
  Task 8 state, ViewModel, runtime adapter, and safe issue mapper did not exist.
- Compose RED: `:feature:catalog:compileDebugAndroidTestKotlin` failed because the Discover screen,
  semantic tags, section models, and loading/content tree did not exist.
- Self-review regression RED: the new unavailable-activation/media-switch test failed because a
  media change replaced the terminal source-unavailable state with loading indefinitely; the
  ViewModel now retains the bounded activation issue across media selection.
- Fresh focused host gate:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*' --no-daemon` - PASS,
  exit 0, 9 tests, 2026-09-10.
- Fresh instrumentation source gate:
  `./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon` - PASS, exit 0,
  2026-09-10.
- Fresh direct app-caller compile:
  `./gradlew :app:compileDebugKotlin --no-daemon` - PASS, exit 0, 2026-09-10.
- User-run connected gate:
  `:feature:catalog:connectedDebugAndroidTest` filtered to `DiscoverScreenInstrumentedTest` - PASS,
  exit 0, 4/4 tests on Redmi Note 9S/API 35, 2026-09-10.
- First user-run broad gate: `verifyArchitecture` completed successfully, but `detekt` failed with
  12 blocking feature findings: eight fixture magic numbers from Task 6, one intentional constant
  diagnostic method from Task 7, and three Task 8 naming/magic-number/generic-catch findings.
- Root-cause remediation reuses `CatalogSectionCaps.cap(...)` for fixture section limits, names the
  rating scale and popular-card aspect ratio, moves `DiscoverTestTags` to its matching file, and
  locally suppresses only the intentional zero-valued pre-image diagnostic method and the
  contract-required unexpected-`Throwable` mapping boundary.
- Fresh post-remediation focused compile:
  `./gradlew :feature:catalog:compileDebugKotlin :feature:catalog:compileBenchmarkReleaseKotlin --no-daemon`
  - PASS, exit 0, 2026-09-10.
- Fresh post-remediation focused Detekt over the six affected production files - PASS, exit 0,
  2026-09-10. This is changed-cone evidence, not acceptance of the required full gate.
- Fresh post-remediation ViewModel regression:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*' --no-daemon` - PASS,
  exit 0, 9 tests, 2026-09-10.
- Static changed-cone review finds no Kotlin line over 120 characters; no `SavedStateHandle`,
  DataStore, Story Detail acquisition, nested vertical list/grid, Search, Chapters, WorkManager,
  AndroidX Startup, or `GlobalScope` was introduced in Task 8 production code.

## Task 8 Required User-Owned Gate

Status: **PASSED / ACCEPTED**

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.discover.DiscoverScreenInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

- Connected gate: PASS, 4/4 tests on Redmi Note 9S/API 35.
- Post-remediation broad gate: user reports `BUILD SUCCESSFUL`; `verifyArchitecture` and `detekt`
  are accepted as PASS.

Task 8 is completed/accepted. The closure turn does not execute Task 9.

## Task 8 Self-Review

- Recomposition state is bounded to one selected medium, four presentation variants, at most three
  sections, and at most 19 lightweight card projections; no rich Story Detail DTO or bitmap is held.
- Each media selection owns exactly one collector job. Replacement cancels the old job, stale
  emissions/results are media-key checked, and ViewModel clearing cancels work then closes runtime
  storage/session ownership idempotently.
- `Published(empty)` remains Empty and never becomes bootstrap authority. Content and Empty retain
  non-blocking refresh issues; a read failure with no usable snapshot becomes a bounded fatal state.
- Popular uses one bounded horizontal `LazyRow`; Latest and Top Rated are finite children of the
  root `LazyColumn`, so there is one vertical scroll owner and no full-feed eager image request.
- Section/card identities derive from semantic kind plus stable source Story identity. Empty
  sections are omitted, both media controls remain enabled, and the UI emits only
  `StorySourceRef` plus optional `CoverAssetKey` on card selection.
- Actual Compose tree/scroll/accessibility execution and the broad architecture/Detekt gate pass.

## Task 9 Delta

- Added a saved Story route whose persisted representation is one bounded primitive string carrying
  only the `StorySourceRef` identity fields plus the optional cover revision. Restore re-derives the
  exact Story identity, validates cover alignment, and fails closed to Discover on malformed or
  mismatched input.
- Added a feature-owned runtime holder shared by Discover and Story ViewModels. A restored Story
  does not construct the Discover ViewModel first; `StoryDetailSession.activate()` completes pin
  registration and access touch before Story content becomes visible.
- Added the keyed Story Detail ViewModel/UI flow with separable summary, metadata, loading, and safe
  typed issue state. Missing detail relies on the existing keyed runtime acquisition, retry remains
  inline, cached summary/detail/cover survive acquisition or read failures, and cancellation never
  becomes issue UI.
- Added explicit destination release on button/system Back. Release and unexpected observer failures
  fail closed without leaking raw payload/error text or leaving the old demand active.
- Moved the Discover `LazyListState` above the route branch and passes the same instance back into
  Discover, preserving exact in-memory scroll continuity across Discover -> Story -> Back.
- Story Detail remains metadata-only. No Chapter, Reader, Library, progress/reconciliation, bitmap,
  entity, or broad DTO navigation state was introduced.

## Task 9 Agent-Owned Evidence

- TDD RED was observed for missing route/ViewModel production types, then focused GREEN passes:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*StoryDetailViewModel*' --tests '*CatalogRoute*' --rerun-tasks --no-daemon`
  - PASS, 11 tests total: 7 Story ViewModel tests and 4 route tests; zero failures/errors.
- `./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS; the Story restoration/pin-order/Back continuity instrumentation source compiles.
- Post-device-failure focused compile:
  `.\gradlew.bat :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS after correcting the scroll-continuity instrumentation fixture; no production source was
    changed by this remediation.
- Post-remediation focused host regression:
  `.\gradlew.bat :feature:catalog:testDebugUnitTest --tests '*StoryDetailViewModel*' --tests '*CatalogRoute*' --tests '*DiscoverViewModel*' --no-daemon`
  - PASS; the route, Story Detail, and Discover state cone remains green.
- Final self-review race regression:
  `.\gradlew.bat :feature:catalog:testDebugUnitTest --tests '*StoryDetailViewModelTest.immediateReopenAfterBackDoesNotReuseTheDemandBeingReleased' --no-daemon`
  - RED before remediation: one assertion failure while the old demand remained active during a
    blocked release.
  - GREEN after remediation: PASS; immediate reopen waits for the captured release and creates a
    fresh active demand.
- Fresh final agent-owned gate after the race remediation:
  - focused Story/route/Discover host tests: PASS.
  - `:feature:catalog:compileDebugAndroidTestKotlin`: PASS.
- Fresh closure rerun after final user evidence:
  - focused Story/route/Discover host tests: PASS.
  - `:feature:catalog:compileDebugAndroidTestKotlin`: PASS.
- Widened changed-cone checks:
  - `./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*' --no-daemon`
    - PASS, 9 tests; shared runtime ownership does not regress the bounded Discover reducer.
  - `./gradlew :app:compileDebugKotlin --no-daemon`
    - PASS; the direct app caller compiles against the evolved feature entry composition.
- `git diff --check` passes; only expected line-ending conversion warnings are reported by Git.

## Task 9 Required User-Owned Gate

Status: **PASS / ACCEPTED**.

```powershell
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.story.StoryRouteRestorationInstrumentedTest' `
  --no-daemon
.\gradlew.bat verifyArchitecture detekt --no-daemon
```

- Broad gate: PASS. The user-returned `verifyArchitecture detekt` run completed with
  `BUILD SUCCESSFUL`; all architecture checks passed and the reported Detekt findings are warnings
  only.
- Connected gate: PASS. The first direct PowerShell invocation stripped the `-Pandroid` prefix,
  so Gradle rejected `.testInstrumentationRunnerArguments...` as an unknown task before executing
  instrumentation. A local `gradlew help` probe confirms that quoting the whole project-property
  argument preserves it correctly. The corrected user run executed 4 tests on Redmi Note 9S/API 35:
  3 passed and `backReturnsToDiscoverWithTheSameScrollStateInstance` failed with `expected:<6> but
  was:<0>`. Root-cause review found that the test began on Story and first measured an empty Discover
  surface with an impossible top-level index 6, which Compose correctly clamped to 0. The test now
  first renders a real three-section Discover surface at valid index 2, transitions to Story, then
  verifies Back retains the same `LazyListState` instance and exact index/offset. The corrected
  user rerun reports `BUILD SUCCESSFUL` for the filtered
  `StoryRouteRestorationInstrumentedTest` class on Redmi Note 9S/API 35.
- Final self-review subsequently exposed and remediated a production Back -> immediate reopen race:
  `closeDestination()` previously left the old demand feature-active until its asynchronous release
  coroutine ran, so reopening the same Story could reuse the demand being released. The ViewModel
  now detaches route/demand state synchronously and makes the next activation await the captured
  release job. A blocking-release regression was observed RED and passes after the fix. Because this
  production change post-dated the earlier evidence, both commands above required one final rerun.
- Final user reruns: both the filtered connected class and `verifyArchitecture detekt` report
  `BUILD SUCCESSFUL` after the race remediation.

Task 9 is completed/accepted. This closure turn commits Task 9 and does not execute Task 10.

## Task 9 Self-Review

- Pin-first ordering is preserved for both card navigation and restored Story routes. Discover is
  not activated first on restored Story, and publication/pruning still shares the runtime mutation
  gate with active pins.
- The feature consumes the existing keyed runtime observer and atomic importer/write boundary; it
  adds no DAO-shaped orchestration and does not widen the <=4-query storage contract.
- Route save data is length-framed and bounded by existing domain identity/revision limits. Restore
  never repairs identity drift or exposes the rejected raw payload to UI.
- One shared runtime/session owner survives configuration recreation; Story demand is reusable after
  Back, and release/observer failure paths clear stale feature state without crashing the route.
- Back detaches the feature's old demand synchronously; an immediate reopen waits for its captured
  release to finish, preventing reuse of a session/pin in the middle of release.
- The changed cone contains no Chapter/Reader/Library/progress fan-in, bitmap navigation, or direct
  storage/app-shell ownership violation.

## Task 10 Delta

- Added a capability-private, lazily created Catalog image session. No Coil loader, decoded cache,
  disk cache, callback registration, or cover work exists before the first composed cover demand;
  final feature-session destruction closes the image session before the runtime session and every
  close/unregister path is idempotent.
- Configured exact initial ceilings: 32 MiB decoded memory, one 128 MiB Coil disk cache, at most
  eight active cover pipelines, and zero manual offscreen prefetch. Android low-memory/pressure
  callbacks clear only decoded memory; `TRIM_MEMORY_UI_HIDDEN` alone preserves warm continuity and
  decoded-memory trims do not clear encoded disk entries.
- Added `CoverRequest`, the local `CoverFetcher`, and `CoverEncodedDiskCache`. Both Coil memory and
  encoded disk identity use only `CoverAssetKey.stableCacheKey`; the explicit disk adapter closes
  snapshots, replaces atomically, aborts incomplete/failed/oversized writes, and rethrows caller
  cancellation after releasing its editor.
- Moved Android drawable resolution behind build-type-owned `VariantLocalCoverAssets`: debug and
  benchmark/profile variants map stable logical asset ID/version pairs to packaged resources;
  release resolves nothing and still packages no deterministic seed artwork. Raw resource integers
  do not enter domain, storage, route, or UI model state.
- Preserved `CoverLocator` beside `CoverAssetKey` through Discover/Story presentation. Discover and
  Story issue the same stable request for unchanged artwork; the route cover key is available to
  Story before metadata arrives, enabling the memory fast path without carrying a bitmap/DTO.
- Replaced placeholder-only cover slots with fixed-geometry asynchronous cover surfaces. Artwork
  failure is scoped to typed `CatalogFailure.Artwork` state while metadata remains rendered, and a
  stable failed request does not automatically restart on unrelated recomposition.
- Converted Latest Updates and Top Rated cards into individual top-level LazyColumn rows while
  retaining Popular as one horizontal carousel. This makes vertical offscreen disposal cancel image
  demand instead of composing all 9/5 vertical cards eagerly.

## Task 10 Agent-Owned Evidence

- TDD RED was observed for the missing image/cache/session API, missing locator projection, route
  cover readiness, scoped artwork failure, composition disposal, viewport-row ownership, and disk
  editor failure cleanup before each production change.
- Final focused feature cone:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*CoverEncodedDiskCacheTest' --tests '*CoverJobLimiterTest' --tests '*CoverArtworkFailureTest' --tests '*DiscoverViewportLayoutTest' --tests '*DiscoverViewModelTest' --tests '*StoryDetailViewModelTest' --tests '*CatalogVariantFixtureTest' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS, 22 focused host tests, debug production compile, and instrumentation-source compile; zero
    failures/errors/warnings.
- Direct app-shell regression:
  `./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*' --no-daemon`
  - PASS with zero warnings.
- Variant cone:
  `./gradlew :feature:catalog:compileReleaseKotlin :feature:catalog:compileBenchmarkReleaseKotlin :feature:catalog:compileNonMinifiedReleaseKotlin :feature:catalog:testBenchmarkReleaseUnitTest --tests '*CatalogVariantFixtureTest*' --no-daemon`
  - PASS for release, benchmarkRelease, and nonMinifiedRelease compilation plus the real benchmark
    logical-asset resolver fixture; zero warnings.
- Post-user-failure focused remediation gate:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*CoverEncodedDiskCacheTest' --tests '*CoverArtworkFailureTest' --tests '*DiscoverViewportLayoutTest' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS after separating visible metadata from the cover placeholder fixture, preserving cache
    cancellation/abort behavior while satisfying source shape, and moving public-in-file state/row
    declarations to matching files.
- Fresh closure gate after user-owned acceptance:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*CoverEncodedDiskCacheTest' --tests '*CoverJobLimiterTest' --tests '*CoverArtworkFailureTest' --tests '*DiscoverViewportLayoutTest' --tests '*DiscoverViewModelTest' --tests '*StoryDetailViewModelTest' --tests '*CatalogVariantFixtureTest' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS, 22/22 focused host tests with zero failures/errors/skips plus debug production and
    instrumentation-source compilation.
- `git diff --check` passes; only expected working-copy line-ending notices are emitted by Git.

## Task 10 Required User-Owned Gate

Status: **ACCEPTED**.

```powershell
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.assets.LocalCoverContinuityInstrumentedTest' `
  --no-daemon
.\gradlew.bat verifyArchitecture detekt --no-daemon
```

- The first connected run executed 9 tests on Redmi Note 9S/API 35 and passed 8/9. The sole failure
  was an invalid test fixture: it asserted `Visible metadata 10` from `CoverArtwork`, whose visual
  placeholder intentionally renders only the title's first character. The fixture now renders an
  independent metadata `Text` beside the failed cover while retaining the stable-failure/no-retry
  assertion; the class is compiled and requires a connected rerun.
- The first broad run passed every architecture verifier and Detekt reported five blocking Task 10
  feature findings: one `MaxLineLength`, two `MatchingDeclarationName`, one `ReturnCount`, and one
  `TooGenericExceptionCaught`. Source-only remediations are present; `verifyArchitecture detekt`
  required a user rerun before acceptance.
- The user reports `BUILD SUCCESSFUL` for both required reruns: the filtered connected
  `LocalCoverContinuityInstrumentedTest` class and `verifyArchitecture detekt`. This accepts the
  connected continuity behavior and the architecture/Detekt remediation evidence.

## Task 10 Self-Review

- Coil and concrete Android resource ownership remain confined to `feature.assets` plus the
  build-type resolver; domain/storage/runtime/app-shell production source gained no image or raw
  resource dependency.
- The loader/cache/callback graph is created only by cover demand. Feature activation and skeleton
  rendering alone cannot initialize it, and final holder teardown closes image ownership before
  runtime/storage ownership.
- Exact stable identity is used for memory and explicit encoded disk keys; UI object identity,
  `toString()`, mutable Story metadata, and raw `R.drawable` values are not cache authority.
- Vertical viewport work is physically bounded by LazyColumn composition; zero manual prefetch is
  present, and the shared limiter prevents more than eight active cover pipelines.
- Memory-hit requests bypass fetcher/resolver/disk work; disk snapshots are attached to the decode
  source and close after consumption. Failed writes retain the prior atomic entry and do not leave
  editors locked.
- Artwork error/cancellation lifetimes are scoped: generic decode failures map to safe typed artwork
  state, caller cancellation propagates, disposal cancels in-flight demand, and metadata/geometry
  remain stable.

## Later Task Status

Tasks 0-10: **COMPLETED/ACCEPTED**. Tasks 11 through 16: **NOT RUN**.

## Risks / Open Checks

- API 26/API 37 repetition, later device/UI, performance, profile, and plugin-integration gates
  remain owned by later tasks and are `NOT RUN`.

## Exact Resume Boundary

Resume Step 2 at Task 11, Step 1: write the RED JVM remote artwork policy/transport tests. Preserve
the Task 10 capability-private image-session/cache ownership and do not add a concrete production
HTTP adapter, OkHttp/Coil network dependency, or main/release `INTERNET` permission. Task 11 has not
started in this Task 10 closure turn.
