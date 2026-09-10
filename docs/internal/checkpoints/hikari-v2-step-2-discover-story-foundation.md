# Hikari V2 Step 2 - Discover + Story Detail Foundation

Date: 2026-09-10
Status: **TASKS 0-13 COMPLETED/ACCEPTED; TASK 14 NOT RUN**

## Authority

- Design: `../../superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.4.md`
- Implementation plan: `../../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Accepted predecessor: `hikari-v2-step-1-foundation-clean-boot.md`
- Completed/accepted execution boundary: Tasks 0-13.
- Next canonical execution boundary: Task 14, `NOT RUN`.

Reviewed artifact SHA-256:

- design: `2f7ffe3ec7693c6f4b3219c795bcd08f0260f4804c8eff78d38626c6603cdb6f`
- plan: `41f51a26bf860fc8e3ba89ce9d30e36010774520d05e27bc2aa1494f506506d2`

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

## Task 11 Delta

- Added the feature-private `RemoteCoverTransport` seam with only normalized HTTPS URI, finite
  timeout values, status/redirect/media metadata, and an encoded body stream; no HTTP-library type
  or concrete production transport is present.
- Added source-scoped `RemoteCoverPolicy`: initial and redirected URI text reuses the Task 1 strict
  parser/canonicalizer, validates every hop against the current host-owned
  `SourceAssetPolicyProvider`, follows only 301/302/303/307/308, rejects loops or more than five
  redirects, preserves raw path/query identity, and applies 10s connect plus 20s read/call policy.
- Operation-owned call deadlines map only to `Artwork(TIMEOUT)`. Caller/session cancellation is
  preserved as the same exception instance across the timeout scope; every returned response/body
  closes once, and close/cancellation/failure paths delete transient encoded files.
- Remote bodies spool through an 8 KiB buffer into a temporary file with an 8 MiB declared and
  streaming hard cap. Content type is normalized case-insensitively with parameters stripped and
  only JPEG/PNG/WebP are admitted before Android decoder preflight.
- Added Android bounds-only preflight for source width/height <=8,192 and overflow-safe pixel
  surface <=32,000,000. The remote path rejects malformed/type-mismatched containers, animated
  WebP/APNG, and original-size requests; it computes target-size power-of-two sampling before the
  unchanged Coil decode path.
- Extended `CoverFetcher` so a remote disk hit first recovers and validates the current host-owned
  source policy. A miss follows policy -> bounded spool -> preflight -> atomic encoded-cache commit;
  preflight/fetch failure cannot commit a partial entry, and a later disk hit performs no transport
  request.
- Wired image policy lookup through the demand-owned runtime activation. Persisted locator/source
  state does not carry a remembered policy object; rebuilding against the current binding succeeds
  only when that binding still supplies the matching policy and otherwise fails `POLICY_REJECTED`.
- Extended the Step 2 build-surface verifier to reject any concrete `RemoteCoverTransport`
  implementation under main/release, independent of known client imports. Existing dependency and
  source gates continue to reject OkHttp, Coil network, `HttpURLConnection`, and `java.net.URL`.

## Task 11 Agent-Owned Evidence

- TDD RED was observed for the missing remote policy/transport API, Android preflight API, concrete
  production transport verifier, source-policy-before-transport ordering, and response-close
  transient-file cleanup before each production change.
- JVM remote policy/transport gate:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' --no-daemon`
  - PASS, 11/11 tests with zero failures/errors/skips.
- Fresh focused closure cone:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' :catalog:domain:test --tests '*CoverAssetContractsTest*' :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS: 11 remote-policy tests, 12 domain cover/URI tests, 16 build-surface verifier tests, debug
    production compile, and instrumentation-source compile. The only emitted warnings were two
    pre-existing Compose-test API deprecations outside the Task 11 changed cone; the Task 11 WebP
    fixture deprecation is locally scoped in test source.
- Release dependency proof:
  `./gradlew :feature:catalog:dependencies --configuration releaseRuntimeClasspath --no-daemon`
  - PASS; dependency output contains Coil Compose/Core only and no `okhttp`,
    `coil-network-okhttp`, or `coil-network-core` artifact.
- Direct source/manifest review finds no `android.permission.INTERNET` in app/feature manifests and
  no concrete production `RemoteCoverTransport`, OkHttp, Coil-network, `HttpURLConnection`, or
  `java.net.URL` source/import.
- Reviewed the 2026-09-10 returned user-owned gate failures. The connected test reached all eight
  cases but the oversized-header case returned `DECODE_FAILED`; the broad gate also exposed a
  verifier false positive for constructor parameters typed as `RemoteCoverTransport` plus the new
  Task 11 Detekt findings.
- Added a verifier regression that distinguishes transport consumers from concrete implementations;
  RED was observed for the false positive, then the complete `Step2BuildSurfaceVerifierTest` suite
  passed with 17 tests. `verifyStep2BuildSurface` now passes against the real working tree.
- Preflight now reads JPEG/PNG/WebP container dimensions before invoking `BitmapFactory`, applies
  the individual-dimension and overflow-safe pixel limits to those encoded bounds, then requires the
  Android bounds probe to succeed and agree before admitting the image. This preserves fail-closed
  decoder validation while classifying encoded image bombs as `DIMENSIONS_TOO_LARGE` even when the
  platform decoder refuses their patched headers.
- Fresh focused closure after the fixes:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' :catalog:domain:test --tests '*CoverAssetContractsTest*' :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS; 55 actionable tasks, with the affected test/compile outputs current.
- Final combined agent-owned rerun included focused Detekt source selection for
  `CoverImagePreflight.kt` and `RemoteCoverPolicy.kt`, the same closure tests/compiles, and
  `verifyStep2BuildSurface`.
  - PASS; 57 actionable tasks, 6 executed and 51 up-to-date. The temporary Detekt init script was
    removed after the run and is not part of the repository delta.
- Reviewed the 2026-09-10 user re-verification. The broad
  `:app:verifyFoundation verifyArchitecture detekt` gate passed (`BUILD SUCCESSFUL` in 21s; 47
  actionable tasks). The filtered connected class ran 8/8 tests but retained one failure in
  `sourceWidthHeightAndPixelSurfaceAreRejectedBeforeFullDecode`: expected
  `DIMENSIONS_TOO_LARGE`, received `DECODE_FAILED`.
- Root-cause inspection of that connected failure found that an extended static WebP can expose a
  `VP8X` canvas before its `VP8`/`VP8L` image bitstream. Returning the first bounds chunk admitted a
  small canvas without checking the later image-bitstream dimensions, leaving `BitmapFactory` to
  reject the inconsistent patched fixture. The parser now scans through `VP8X`, applies the source
  limits to both canvas and image bounds, requires a real static image bitstream, and fails closed
  when the two dimensions disagree. The existing failing connected fixture is the regression RED.
- Fresh affected closure after the WebP parser fix:
  `./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' :catalog:domain:test --tests '*CoverAssetContractsTest*' :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS; `BUILD SUCCESSFUL` in 12s, 51 actionable tasks (5 executed, 46 up-to-date).
- User returned `BUILD SUCCESSFUL` for both required final reruns after the WebP fix: the filtered
  connected `CoverImagePreflightInstrumentedTest` and
  `:app:verifyFoundation verifyArchitecture detekt` gates.
- Final fresh agent-owned closure rerun of the same focused test/compile cone passed:
  `BUILD SUCCESSFUL` in 11s, 51 actionable tasks (4 executed, 47 up-to-date).

## Task 11 Required User-Owned Gate

Status: **ACCEPTED**.

```powershell
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.assets.CoverImagePreflightInstrumentedTest' `
  --no-daemon
.\gradlew.bat :app:verifyFoundation verifyArchitecture detekt --no-daemon
```

- The returned connected result covers all local JPEG/PNG/WebP bounds, malformed/animated-container,
  target sampling, controlled remote miss/disk hit, preflight-no-commit, and recreated-policy cases.
- Both required user-owned gates and the final fresh agent-owned closure are accepted.

## Task 11 Self-Review

- Redirect count, loop detection, relative resolution, canonical DNS-host comparison, and policy
  lookup happen before issuing each hop. Cached remote bytes are not trusted after their source
  policy disappears.
- Response lifetime is per-hop and close-exactly-once; success payloads, partial bodies, cancellation,
  typed rejection, unexpected I/O, and response-close failure all remove transient files.
- Remote bytes are never materialized as an additional full 8 MiB array in production. The only
  full arrays are bounded deterministic test fixtures; production spools then commits by stream.
- Bounds probing uses `BitmapFactory.Options.inJustDecodeBounds`, validates individual dimensions
  before overflow-safe `Long` pixel multiplication, and does not admit animated or original-size
  remote decoding.
- Ownership remains acyclic: domain owns strict URI/policy types, runtime owns the current source
  binding/provider, and only `feature.assets` owns transport policy, preflight, cache sequencing,
  and Coil integration. App/discover/story packages gain no HTTP/image-policy authority.

## Task 12 Delta

- Added session-owned manual Discover refresh with one single-flight owner per source/media scope,
  retained `Published(empty/content)` snapshots on typed refresh failure, caller-cancellation
  propagation, and synchronous terminal work-entry removal.
- Added runtime quiescence for Discover and Story demand. Quiescing cancels active acquisition,
  stops observation demand, releases active Story pins, and leaves the capability-owned Room handle
  open; terminal capability close remains idempotent and closes the owned store exactly once.
- Added lifecycle-aware feature ownership: STOP quiesces active ViewModel demand and removes the
  image loader from composition so composed cover requests dispose; START resumes only the current
  route against persisted state and serializes restart after pending quiescence.
- Manual retry remains runtime-owned and double taps join one active refresh. Lifecycle STOP does
  not create a scheduler, retry owner, or automatic continuation, and terminal bootstrap
  success/failure is not converted into a lifecycle retry.
- Added debug-only lifecycle diagnostics for active Discover collectors, composed cover demand,
  image-session lifetime, runtime-session lifetime, and close ordering. Release/main behavior uses
  the existing no-op diagnostic implementation.
- Added `CatalogLifecycleInstrumentedTest` with a test-only Activity/manifest. It exercises the real
  debug Catalog composition, Room/runtime session, local source, and cover path across
  `STARTED -> CREATED -> STARTED` plus terminal Activity destruction.

## Task 12 Agent-Owned Evidence

- TDD RED was observed for the missing `DiscoverSession.refresh()`/quiescence APIs, active
  work/pin diagnostics, duplicate ViewModel retry ownership, Story acquisition surviving release,
  STOP/START resubscription before quiescence completion, and terminal bootstrap restarting after a
  lifecycle cycle.
- Fresh focused host closure:
  `.\gradlew.bat :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest --no-daemon`
  - PASS; `BUILD SUCCESSFUL` in 6s, 87 tests, zero failures/errors.
- Fresh Android behavior compile:
  `.\gradlew.bat :feature:catalog:compileDebugAndroidTestKotlin --no-daemon`
  - PASS; `BUILD SUCCESSFUL` in 6s with no warnings in the final-tree rerun. An earlier compile
    emitted only the pre-existing deprecated Compose test
    rule in `StoryRouteRestorationInstrumentedTest.kt`, outside the Task 12 changed test.
- Required static owner scan found no `WorkManager`, `androidx.work`, or `GlobalScope` use in the
  Catalog runtime/feature production cone. Its only match is the pre-existing app manifest
  `androidx.startup.InitializationProvider` used to remove lifecycle/emoji initializers; Task 12
  adds no initializer or background owner.
- `git diff --check` reports no whitespace errors; Git emits only the repository's existing
  LF-to-CRLF working-copy notices.

## Task 12 Required User-Owned Gate

Status: **ACCEPTED**.

```powershell
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.lifecycle.CatalogLifecycleInstrumentedTest' `
  --no-daemon
.\gradlew.bat :app:verifyFoundation verifyArchitecture detekt --no-daemon
```

- User-reported filtered connected lifecycle gate:
  `.\gradlew.bat :feature:catalog:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.lifecycle.CatalogLifecycleInstrumentedTest' --no-daemon`
  - PASS; user reports `BUILD SUCCESSFUL` on 2026-09-10.
- User-reported broad policy gate:
  `.\gradlew.bat :app:verifyFoundation verifyArchitecture detekt --no-daemon`
  - PASS; user reports `BUILD SUCCESSFUL` on 2026-09-10.
- Both required returned results were reviewed and accepted. Task 12 is completed/accepted.

## Task 12 Self-Review

- Discover refresh failure cannot replace a durable empty/content snapshot with `Absent`; safe UI
  issues remain scoped to the selected media and raw source/storage details do not escape.
- Single-flight cleanup is synchronous at terminal completion, while cancellation remains
  structured under the capability session and is never mapped into a user failure.
- STOP/route transitions cancel the visible observer/acquisition/image demand without closing Room
  or image caches. START waits for quiescence before reacquiring; terminal Activity destruction
  closes image session before runtime/store, each once.
- Story STOP releases its active pin and session; resume creates a fresh keyed demand against the
  same persisted store. Back/rejection remains the only path that clears the saved route UI state.
- No new module edge, app import, startup initializer, WorkManager owner, process scope, or remote
  source/network surface is introduced.

## Task 13 Delta

- Synchronized the user-reviewed R2.4 design and implementation plan into their canonical repository
  paths, updated the checkpoint authority hashes, and corrected the resume/task-routing split so API
  26/API 37 correctness is owned by Task 15 rather than the new Task 13 presentation foundation.
- Admitted `:core:designsystem` as an exact zero-project-dependency Android library. The final app
  graph has one Catalog product edge plus one separately classified presentation edge:
  `:app -> :feature:catalog, :core:designsystem` and
  `:feature:catalog -> :catalog:domain, :catalog:runtime, :core:designsystem`.
- Added exact graph/import/dependency/package-SCC verification. App code may import only
  `CatalogEntryPoint` from Catalog and `theme.HikariTheme` from the Design System; the shared module
  rejects Catalog/plugin/runtime/storage/image/network/work dependencies and imports.
- Replaced the temporary `HikariBootTheme` fork with one root `HikariTheme` in `HikariStartupApp`.
  Light/dark root backgrounds remain exact white/black and the accepted first-frame/Ready ordering is
  unchanged.
- Added the frozen work-free Step 2 presentation vocabulary: stable palette, typography, Material 3
  shapes, allocation-free `HikariSpacing`, bounded segmented control, section header, static skeleton,
  caller-sized empty/error/inline feedback, and enabled-only Material 3 pull refresh.
- Migrated only shared Catalog chrome. Discover retains one vertical `LazyColumn`, stable section/card
  keys and 5/9/5 caps; it emits sections directly without `DiscoverViewportRow`/flattened render lists,
  keeps durable content visible while refreshing, and removes duplicate progress/manual Refresh chrome.
  Story Detail gains shared static metadata skeleton/inline feedback and remains non-pull-refreshable.
- Exposed distinct Discover `refresh()` and failure-recovery `retry()` intents while preserving one
  `refreshJob`/runtime acquisition owner. The R2.4 amendment exposed missing Task 12 behavior in the
  supplied tree; focused regressions now prove retryable activation failure re-activates once,
  storage-read Retry restarts only the selected observer, refresh+Retry join one acquisition, and
  resume restarts activation cancelled by quiescence.
- Rewrote `docs/ui/design-system.md` as the active scoped V2 policy, classified the V1 Design System
  as `REDESIGN | REFERENCE` in the salvage ledger, and added the fail-closed exact source/API/caller
  and hidden-work gate `scripts/tests/v2-step2-designsystem-slice-test.sh`.

No V1 implementation module was transplanted. The exact references adapted were the R2.4 frozen
palette/typography/shape/API contract, the prior active V1-derived `docs/ui/design-system.md` policy,
and `docs/internal/v2/v1-salvage-ledger.md`; artwork/network/backdrop/full-component ownership remains
reference-only.

## Task 13 Agent-Owned Evidence

- TDD RED was observed for the missing module/edges/root theme, Design System dependency/import
  ratchets, missing shared Compose API, distinct Discover refresh intent, activation/read-failure
  recovery, and resume-after-cancel behavior.
- Graph admission gate:
  `./gradlew :build-logic:test verifyArchitecture :core:designsystem:assembleDebug --no-daemon`
  - PASS; `BUILD SUCCESSFUL` in 1m 5s after correcting the plan's nonexistent Foundation alias to
    the repository's existing Material3-provided Foundation dependency pattern.
- Fresh focused final-tree closure:
  `./gradlew :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' --tests '*ModuleGraphTest*' :core:designsystem:assembleDebug :feature:catalog:testDebugUnitTest :app:testDebugUnitTest :core:designsystem:compileDebugAndroidTestKotlin :feature:catalog:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon`
  - PASS; fresh final-tree rerun after checkpoint/roadmap synchronization reports
    `BUILD SUCCESSFUL` in 12s with 136 tasks up-to-date. Current reports contain 25 focused
    build-logic tests, 43 feature unit tests, and 16 app unit tests with zero failures/errors; all
    three instrumentation source sets compile.
- `bash scripts/tests/v2-step2-designsystem-slice-test.sh`
  - PASS; exact source/API budget, caller map, app import authority, disabled refresh branch,
    no hidden work/state/scroll/effects, and stale V1 policy exclusions are green.
- `git diff --check` reports no whitespace errors; Git emits only repository LF-to-CRLF notices.

## Task 13 Big Update Regression Matrix

| Audit family | Status | Task 13 evidence / reason |
|---|---|---|
| A1 | PROTECTED | Pull refresh delegates to the existing bounded Task 12 acquisition path; no reconciliation index is added. |
| A2 | N/A | No request/provider ingest-session fork or index rebuild is added. |
| A3 | N/A | No Story-ID allocation or all-Story scan is added. |
| A5 | PROTECTED | Shared UI performs no projection lookup/data read; keyed/bounded repositories remain authoritative. |
| A6 | N/A | No redirect or identity resolution is added. |
| A7 | PROTECTED | Design System has zero Story observers or reactive identity resolution. |
| A8 | OWNED | Module/import/hidden-work gates reject repositories and Flow collectors; feature observations remain bounded. |
| L1 | OWNED | Exact slice gate rejects coroutine/effect/CPU/I-O ownership in shared UI. |
| L3 | PROTECTED | UI routes pull to `refresh()` and failures to `retry()`; focused tests prove both join one acquisition owner. |
| L4 | N/A | No Search or canonical settlement path exists. |
| L5 | N/A | No canonical fusion path exists. |
| L6 | N/A | No canonical hydration/hash/currentness path exists. |
| L7 | N/A | No DAO write loop or per-entry commit path is introduced. |
| L8 | PROTECTED | Shared primitives create no subscription demand; feature retains selected-media/keyed observation scope. |
| D1 | OWNED | Design System owns no persistence, history, cache, registry, or corpus-sized lifetime. |
| X1 | PROTECTED | Root theme and controls add no storage observer or invalidation source. |
| X2 | N/A | No application-scope progress/cache-policy history scan exists. |
| X3 | OWNED | APIs accept narrow presentation values; static media options leave UiState and flattened viewport rows are removed. |
| X4 | PROTECTED | No storage trigger, read-combine observation, or snapshot reconstruction is added. |
| X5 | OWNED | Design System starts no foreground or durable work and cannot compete for an existing work item. |
| X6 | N/A | No durable recovery backlog, outbox, or worker path exists. |
| X7 | N/A | No reconciliation point/batch API is introduced. |
| X8 | N/A | Reader automatic-cache ledger/planning is untouched. |
| X9 | N/A | Reader eviction/detach paths are untouched. |
| X10 | N/A | Reader state/publication lock ownership is untouched. |
| X11 | PROTECTED | Design System receives no encoded image/page payload and owns no payload copies. |
| X12 | PROTECTED | Design System owns no operational memoization or metadata-suppression cache. |
| X13 | N/A | No Chapter aggregation path is added. |
| X14 | N/A | No Chapter Room commit/notification path is added. |
| X15 | N/A | No Chapter scheduling/pagination path is added. |
| X16 | PROTECTED | Design System has no plugin manifest/package/executable discovery dependency. |
| X17 | PROTECTED | Design System has no plugin provisioning/package outcome/state ownership. |
| X18 | PROTECTED | Design System has no credential/session/Keystore access. |
| RISK-A4 | N/A | No candidate lookup/search-index path is added. |
| RISK-BIND | N/A | No Story-ID bounded-set data API is added. |
| RISK-LIFECYCLE | OWNED | Root theme has zero collectors/effects/work; startup tests retain Unknown/FirstRun without Catalog composition. |
| RISK-GLOBAL-RESOURCE | OWNED | Gates reject image/network/CPU arbiter, cache, service, registry, or process-resource ownership. |
| RISK-PLUGIN-ISOLATE | N/A | No JavaScript isolate/runtime path is added. |
| RISK-PLUGIN-AUTH-CACHE | N/A | No credential cache/session/auth path is added. |
| RISK-STARTUP-CONTENTION | OWNED | Root theme has no initializer, I/O, DB/network/image access, runtime font load, job, or mutable registry. |
| baseline L2 | OWNED via RISK-LIFECYCLE | Root theming has zero upstream semantic demand and introduces no hidden destination collection. |
| baseline RISK-PROVIDER | PROTECTED | UI adds no provider launch/fan-out; both acquisition-capable intents use the existing guarded owner. |

Amplifier checks are also closed in the focused/static cone: one vertical scroll owner; stable lazy
keys; bounded section/card counts; no hidden destination collector; no image/cache ownership move;
no application-lifetime shared UI owner; no independent loading/publication stream; and no cosmetic
`distinctUntilChanged()` placed after expensive work.

## Task 13 Required User-Owned Gate

Status: **COMPLETED/ACCEPTED**.

Run the four focused connected classes on one intended Android target:

```powershell
.\gradlew.bat :core:designsystem:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariDesignSystemContractTest' `
  --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.startup.StartupSurfaceTest' `
  --no-daemon
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.discover.DiscoverScreenInstrumentedTest' `
  --no-daemon
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.story.StoryDetailScreenInstrumentedTest' `
  --no-daemon
```

Then run the broad/release/architecture gates:

```powershell
.\gradlew.bat :feature:catalog:assembleDebug :feature:catalog:assembleRelease `
  :feature:catalog:assembleBenchmarkRelease :feature:catalog:assembleNonMinifiedRelease `
  :app:assembleDebug :app:assembleRelease :app:verifyFoundation verifyArchitecture detekt `
  --no-daemon
& 'C:\Program Files\Git\bin\bash.exe' scripts/tests/v2-step2-build-surface-test.sh
& 'C:\Program Files\Git\bin\bash.exe' scripts/tests/v2-step2-designsystem-slice-test.sh
```

Returned user evidence on 2026-09-10:

- `StartupSurfaceTest`: PASS, 2 tests on Redmi Note 9S / API 35.
- `StoryDetailScreenInstrumentedTest`: PASS, 2 tests on Redmi Note 9S / API 35.
- `HikariDesignSystemContractTest`: FAIL, 3 of 7 tests. Two failures were invalid test
  assumptions: a disabled Material3 button retains an `OnClick` semantics action while exposing the
  disabled state, and Compose 1.11 permits only one test-rule `setContent` call per test. The remaining
  no-hierarchy failure was isolated to the segmented-control test and followed no production assertion;
  its rerun remains required after the deterministic test-contract repairs.
- `DiscoverScreenInstrumentedTest`: FAIL, 1 of 6 tests because one test called the rule's `setContent`
  twice. The two states are now separate tests with one composition each.
- Broad/release/architecture invocation: FAIL only at Detekt's two Task 13 errors. The segmented-control
  bounds now use named constants, and `DiscoverViewModel.retry()` now expresses the same guarded branch
  behavior without four early returns. Reported architecture/foundation checks remained green; warning-
  severity pre-existing Detekt findings are unchanged.
- `v2-step2-build-surface-test.sh`: PASS.
- `v2-step2-designsystem-slice-test.sh`: no terminal result was included in the returned transcript, so
  this result remains open rather than inferred.

Focused post-repair agent-owned evidence:

- `:core:designsystem:compileDebugAndroidTestKotlin`,
  `:feature:catalog:compileDebugAndroidTestKotlin`, and
  `:feature:catalog:testDebugUnitTest`: PASS; `BUILD SUCCESSFUL` in 40s, 66 actionable tasks.
- `git diff --check`: no whitespace errors; Git reports only the repository's existing LF-to-CRLF
  notices.

Final returned user evidence on 2026-09-10 confirms every remaining gate passes after repair:

- `HikariDesignSystemContractTest`: PASS.
- `DiscoverScreenInstrumentedTest`: PASS.
- broad Debug/Release/BenchmarkRelease/NonMinifiedRelease assembly plus foundation, architecture, and
  Detekt gate: `BUILD SUCCESSFUL`.
- `v2-step2-designsystem-slice-test.sh`: PASS.

Together with the previously accepted `StartupSurfaceTest`, `StoryDetailScreenInstrumentedTest`, and
`v2-step2-build-surface-test.sh` results, this closes the complete Task 13 user-owned gate. The tracked
generated baseline-profile files still contain historical `HikariBootTheme` entries; Task 16 owns
profile regeneration and no Task 13 acceptance claim relies on those stale profile bytes.

## Task 13 Self-Review

- Root theming changes presentation only; it adds no Catalog composition before the accepted
  `Ready && firstFrameReached` gate and preserves exact window-background continuity.
- Shared code knows no Catalog/Story/image/failure/runtime type and owns no effect, collector,
  coroutine, cache, registry, navigation, or semantic mutable state. Material3 transient pull state
  exists only in the enabled branch.
- Discover pull refresh is available only for durable Empty/Content; Absent/fatal states retain the
  explicit loading/Retry route. Refresh and Retry are distinct UI intents but cannot create parallel
  source acquisition.
- Cover artwork, cache/security/decode, route identity, Story pin/release, retention, Room, and
  benchmark-threshold ownership are unchanged. Story Detail has no pull refresh or new acquisition.
- Every admitted public Design System symbol has a production caller and a documented semantic
  reason; no generic layout/text/card/artwork/navigation wrapper or future-only API remains.
- Task 14 remains `NOT RUN` and owns all visual restoration/polish.

## Later Task Status

Tasks 0-13: **COMPLETED/ACCEPTED**.
Tasks 14 through 18: **NOT RUN**.

## Risks / Open Checks

- No Task 13 gate remains open. Task 14 visual restoration, Task 15 API 26/API 37 and
  screenshot/correctness evidence, Task 16 performance/profile evidence, Task 17 plugin integration,
  and Task 18 final acceptance remain `NOT RUN`.

## Exact Resume Boundary

Task 13 is completed/accepted. Stop at this boundary. On a new explicit instruction to begin Task 14,
resume from this checkpoint and read only the owning plan's Task 14 section plus its materially relevant
global constraints and affected code. Do not begin Task 15 or reopen Task 13 ownership while executing
Task 14 unless focused evidence proves a Task 13 regression.
