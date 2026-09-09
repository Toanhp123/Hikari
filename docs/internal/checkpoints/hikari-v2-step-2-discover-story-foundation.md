# Hikari V2 Step 2 - Discover + Story Detail Foundation

Date: 2026-09-09
Status: **TASKS 0-6 COMPLETED/ACCEPTED; TASK 7 NOT RUN**

## Authority

- Design: `../../superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md`
- Implementation plan: `../../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Accepted predecessor: `hikari-v2-step-1-foundation-clean-boot.md`
- Completed/accepted execution boundary: Tasks 0-6.
- Next canonical execution boundary: Task 7, not started in the Task 6 closure turn.

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

## Later Task Status

Tasks 0-6: **COMPLETED/ACCEPTED**. Tasks 7 through 16: **NOT RUN**.

## Risks / Open Checks

- API 26/API 37 repetition, later device/UI, performance, profile, and plugin-integration gates
  remain owned by later tasks and are `NOT RUN`.

## Exact Resume Boundary

Task 6 is completed/accepted: the fresh focused cone is green, the user-owned four-AAR assemble plus
architecture gate is accepted, direct AAR inspection confirms benchmark/non-minified resolution and
release cleanliness, and the corrected shell gate is accepted as `BUILD SUCCESSFUL`. Resume Step 2
at Task 7 of the owning implementation plan. Task 7 was not executed in the Task 6 closure turn.
