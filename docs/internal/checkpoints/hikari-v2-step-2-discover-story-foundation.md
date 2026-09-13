# Hikari V2 Step 2 - Discover + Story Detail Foundation

Date: 2026-09-13
Status: **TASKS 0-17 COMPLETED/ACCEPTED WITH RECORDED TASK 16 PERFORMANCE DEBT; TASK 18 READY TO START**

## Authority

- Design: `../../superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.8.md`
- Task 16 amendment: the user-supplied R2.10 amendment is integrated into the tracked owning plan
  and this checkpoint; its standalone untracked file is intentionally excluded from the commit.
- Implementation plan: `../../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Accepted predecessor: `hikari-v2-step-1-foundation-clean-boot.md`
- Completed/accepted execution boundary: Tasks 0-17.
- Next execution boundary: Task 18 is ready to start in a new turn. Task 18 remains `NOT RUN` in
  this turn.
- 2026-09-11 authority correction: R2.8 supersedes only the Task 14 visual/IA/token plan on top of accepted Tasks 0-13; no Task 14 production work is recorded by this docs patch.

Reviewed artifact SHA-256:

- design R2.8: `cdb6e6b7a2db65acd54685fd5a1d5f78c97976d1f5c0ae53e3d64e2894828552`
- plan: `694ad52f03546208efbe1bb82debb957e33f3a23915470497e05c443f1c3248b`

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

| Audit family            | Status                   | Task 13 evidence / reason                                                                                           |
| ----------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| A1                      | PROTECTED                | Pull refresh delegates to the existing bounded Task 12 acquisition path; no reconciliation index is added.          |
| A2                      | N/A                      | No request/provider ingest-session fork or index rebuild is added.                                                  |
| A3                      | N/A                      | No Story-ID allocation or all-Story scan is added.                                                                  |
| A5                      | PROTECTED                | Shared UI performs no projection lookup/data read; keyed/bounded repositories remain authoritative.                 |
| A6                      | N/A                      | No redirect or identity resolution is added.                                                                        |
| A7                      | PROTECTED                | Design System has zero Story observers or reactive identity resolution.                                             |
| A8                      | OWNED                    | Module/import/hidden-work gates reject repositories and Flow collectors; feature observations remain bounded.       |
| L1                      | OWNED                    | Exact slice gate rejects coroutine/effect/CPU/I-O ownership in shared UI.                                           |
| L3                      | PROTECTED                | UI routes pull to `refresh()` and failures to `retry()`; focused tests prove both join one acquisition owner.       |
| L4                      | N/A                      | No Search or canonical settlement path exists.                                                                      |
| L5                      | N/A                      | No canonical fusion path exists.                                                                                    |
| L6                      | N/A                      | No canonical hydration/hash/currentness path exists.                                                                |
| L7                      | N/A                      | No DAO write loop or per-entry commit path is introduced.                                                           |
| L8                      | PROTECTED                | Shared primitives create no subscription demand; feature retains selected-media/keyed observation scope.            |
| D1                      | OWNED                    | Design System owns no persistence, history, cache, registry, or corpus-sized lifetime.                              |
| X1                      | PROTECTED                | Root theme and controls add no storage observer or invalidation source.                                             |
| X2                      | N/A                      | No application-scope progress/cache-policy history scan exists.                                                     |
| X3                      | OWNED                    | APIs accept narrow presentation values; static media options leave UiState and flattened viewport rows are removed. |
| X4                      | PROTECTED                | No storage trigger, read-combine observation, or snapshot reconstruction is added.                                  |
| X5                      | OWNED                    | Design System starts no foreground or durable work and cannot compete for an existing work item.                    |
| X6                      | N/A                      | No durable recovery backlog, outbox, or worker path exists.                                                         |
| X7                      | N/A                      | No reconciliation point/batch API is introduced.                                                                    |
| X8                      | N/A                      | Reader automatic-cache ledger/planning is untouched.                                                                |
| X9                      | N/A                      | Reader eviction/detach paths are untouched.                                                                         |
| X10                     | N/A                      | Reader state/publication lock ownership is untouched.                                                               |
| X11                     | PROTECTED                | Design System receives no encoded image/page payload and owns no payload copies.                                    |
| X12                     | PROTECTED                | Design System owns no operational memoization or metadata-suppression cache.                                        |
| X13                     | N/A                      | No Chapter aggregation path is added.                                                                               |
| X14                     | N/A                      | No Chapter Room commit/notification path is added.                                                                  |
| X15                     | N/A                      | No Chapter scheduling/pagination path is added.                                                                     |
| X16                     | PROTECTED                | Design System has no plugin manifest/package/executable discovery dependency.                                       |
| X17                     | PROTECTED                | Design System has no plugin provisioning/package outcome/state ownership.                                           |
| X18                     | PROTECTED                | Design System has no credential/session/Keystore access.                                                            |
| RISK-A4                 | N/A                      | No candidate lookup/search-index path is added.                                                                     |
| RISK-BIND               | N/A                      | No Story-ID bounded-set data API is added.                                                                          |
| RISK-LIFECYCLE          | OWNED                    | Root theme has zero collectors/effects/work; startup tests retain Unknown/FirstRun without Catalog composition.     |
| RISK-GLOBAL-RESOURCE    | OWNED                    | Gates reject image/network/CPU arbiter, cache, service, registry, or process-resource ownership.                    |
| RISK-PLUGIN-ISOLATE     | N/A                      | No JavaScript isolate/runtime path is added.                                                                        |
| RISK-PLUGIN-AUTH-CACHE  | N/A                      | No credential cache/session/auth path is added.                                                                     |
| RISK-STARTUP-CONTENTION | OWNED                    | Root theme has no initializer, I/O, DB/network/image access, runtime font load, job, or mutable registry.           |
| baseline L2             | OWNED via RISK-LIFECYCLE | Root theming has zero upstream semantic demand and introduces no hidden destination collection.                     |
| baseline RISK-PROVIDER  | PROTECTED                | UI adds no provider launch/fan-out; both acquisition-capable intents use the existing guarded owner.                |

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
- At the Task 13 acceptance boundary, Task 14 remained `NOT RUN` and owned all visual restoration/polish.

## Task 14 Delta

- Migrated the root Material 3 palette and typography to the exact R2.8 values while preserving
  white/black root backgrounds, the accepted spacing/shape scales, platform Serif/Sans ownership,
  and the single root `HikariTheme` architecture.
- Preserved shared state/action ownership while applying the reviewed restrained body treatment to
  `HikariErrorState` and `HikariInlineFeedback`.
- Replaced the migration-only segmented media selector with feature-local floating Manga/Light
  Novel destination navigation, including selected-tab semantics, selected no-op behavior, explicit
  media-switch scroll reset, safe system insets, and final-content bottom clearance.
- Rebuilt Discover with one vertical owner and distinct bounded Popular hero, Latest poster-rail,
  and Top Rated rank-list silhouettes plus final-geometry static loading reservations.
- Rebuilt Story Detail around compact/wide portrait identity geometry and semantic About/Authors/
  Artists/Genres/Status/Language groups; existing projection-owned media type and latest-update
  timestamp now map to user-safe UI labels while source/provenance/route authority remains hidden.
- Retired `HikariSegmentedControl` and `HikariSegmentedOption` atomically with their tests, caller
  checks, source-budget entries, and active Design System policy.
- Added `docs/internal/v2/step-2-visual-acceptance-2026-09-11.md` as the single Task 14 visual and
  correctness ledger. Bundled reference hashes match; the written blueprint differs at raw-byte
  level only because this Windows worktree uses CRLF, and its LF-normalized hash matches authority.
- Repaired the returned user-gate failures without changing Task 14 behavior: media accessibility
  assertions now use stable destination tags, Latest loading asserts the approved `92x138.dp`
  geometry, the restoration fixture again has enough vertical content to preserve a non-zero list
  position, shared media labels live in a leaf presentation package, and Detekt literals are named.
- Applied User Visual Direction 3 enhancements and feedback refinements (2026-09-11):
    - `:core:designsystem`: Refined palette tokens to warm paper surfaces (`#FFF9F7`/`#F6F0EC`), deep charcoal dark (`#131318`/`#181820`), coral rose primary (`#F4515B`), and teal secondary (`#2A9D8F`).
    - `:feature:catalog` Discover: Implemented Direction 3 "Magazine Style Grid" with full-width Editorial Hero banner ("Stories for a Brighter You") and page dots, "Trending Now" and "Recommended for You" horizontal poster rails with "See All" action stubs, Editorial Quote card ("A good story stays with you."), Top Rated list with clean single-digit ranks (fixed 2-digit wrapping bug), circular `SearchIcon` action in header, and removed placeholder bottom navigation bar.
    - `:feature:catalog` Floating Nav: Refined floating pill navbar with 3 tabs: `Manga`, `Home` (centered), and `Light Novel` with elevated `CircleShape` styling, subtle border, primary active indicator, and smooth scroll-to-top on Home tap.
    - `:feature:catalog` Story Detail: Implemented Direction 3 layout ("Whispers in the Rain" style) with 280dp cover artwork hero, vertical gradient scrim fade, star rating row (★ 9.5), circular `BackArrowIcon` and `HeartIcon` top bar buttons, primary CTA "Read from Chapter 1", OutlinedButton with `BookmarkIcon`, circular vector action buttons (`HeartIcon`, `ShareIcon`, `MoreIcon`), 2-column metadata grid, genre chips, tabs, expandable synopsis, and "You May Also Like" shelf.
    - Explicit bypass policy: All non-Step-2 actions (Search, See All, Read from Chapter 1, Add to Library, Chapters/Similar tabs) are explicitly bypassed as disabled stubs (`enabled = false`) to honor Step 2 restrictions without side effects.
- Refactored the rejected Direction 3 implementation without changing its intended appearance:
  Discover navigation is stateless and Home can no longer replace the selected media destination;
  editorial/header/quote composition is split from screen state routing; adaptive insets flow into
  every section; section caps reuse `CatalogSectionCaps`; dead metric aliases and ineffective Story
  cover-width plumbing are removed; future-only Story chrome is isolated from projection-backed
  metadata; and the bespoke Canvas icon code is replaced by immutable feature-local vectors.

## Task 14 Agent-Owned Evidence

- Focused RED/GREEN Story mapping gate: the test first failed because media type/latest-update were
  absent, then failed behaviorally with the wished-for API present but unmapped, and finally passed
  after UTC/English mapping was implemented.
- Final focused Gradle closure: `BUILD SUCCESSFUL` in 12s, 62 actionable tasks, covering Design
  System production/androidTest compilation, feature production/androidTest compilation, focused
  Discover refresh/ViewModel tests, Story Detail ViewModel tests, and cover-failure regression.
- User Direction 3 and Feedback Refinements compilation and tests: `./gradlew :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin :feature:catalog:testDebugUnitTest :catalog:domain:test` - PASS.
- Architecture Package Check: `./gradlew verifyProductionPackageStructure` - PASS.
- Static Analysis: `./gradlew detekt --no-daemon` - PASS (0 errors/warnings in modified code).
- Design System Slice Script: `scripts/tests/v2-step2-designsystem-slice-test.sh` via Git Bash - PASS.
- Whitespace Check: `git diff --check` - PASS (0 whitespace errors).
- Follow-up refactor RED/GREEN: `CatalogMediaDestinationNavTest` first failed on the missing
  stateless media-selection contract, then passed after Home/selected-media behavior was made pure.
- Follow-up focused closure: Design System production/androidTest compile, Catalog production/
  androidTest compile, focused Discover/Story/Nav unit tests, and
  `verifyProductionPackageStructure` passed; final post-review `BUILD SUCCESSFUL` in 10s,
  63 actionable tasks.
- Follow-up Design System slice via Git Bash: PASS; `git diff --check`: PASS.
- First user rerun after the cleanup: broad `:app:verifyFoundation verifyArchitecture detekt`
  passed in 14s (51 tasks). Connected Catalog executed 23 tests with 3 failures: two Story assertions
  queried lazy content before scrolling it into composition, and the route regression still targeted
  the removed `Back` text instead of the icon control.
- Returned-failure repair: Story now exposes a stable root tag and accessible Back description;
  tests use a real detail fixture, scroll to projection-backed metadata/preview actions before
  asserting, and the route regression clicks the real Back control. The three changed-cone Detekt
  `LongMethod` warnings in `DiscoverEditorial.kt`, `DiscoverEditorialQuote.kt`, and
  `DiscoverSections.kt` are addressed by extracting composition helpers rather than suppressing
  findings; the user-owned broad rerun remains their final evidence.
- Fresh repair cone: Catalog production/androidTest compile, focused Discover/Story/Nav unit tests,
  and `verifyProductionPackageStructure` passed; final `BUILD SUCCESSFUL` in 9s,
  63 actionable tasks.
- User-returned Design System connected contract: PASS on Redmi Note 9S / API 35, 7 tests,
  `BUILD SUCCESSFUL` in 27s.
- Pre-follow-up user-returned Catalog connected contract: PASS; this evidence was superseded when
  the Direction 3 cleanup changed Catalog production UI.
- Pre-follow-up user-returned `:app:verifyFoundation verifyArchitecture detekt`: PASS; the affected
  broad gate was rerun after cleanup and passed, then production changed again during failure repair.
- Final user-returned Catalog connected rerun after the failure repair: PASS, reported on
  2026-09-11.
- Final user-returned `:app:verifyFoundation verifyArchitecture detekt` rerun after the failure
  repair: PASS, reported on 2026-09-11.
- Returned-failure repair cone: `:feature:catalog` production/androidTest compile,
  `CatalogRouteTest`, and `verifyProductionPackageStructure` passed in 22s, 61 actionable tasks.
- Final acceptance-tree focused gate: Design System production/androidTest compile, Catalog
  production/androidTest compile and unit tests, Catalog domain tests, and
  `verifyProductionPackageStructure` passed; `BUILD SUCCESSFUL` in 21s, 69 actionable tasks. The
  Design System slice script and `git diff --check` also pass on the final acceptance tree.
- Independent reviewer dispatch was attempted twice but unavailable because the configured provider
  returned `404 No active credentials`; root self-review therefore remains the available review
  evidence for this handoff.

## Task 14 User-Owned Visual Acceptance

The earlier Design System connected evidence remains applicable because this repair did not change
`:core:designsystem`. The user reports PASS for both final affected reruns: connected Catalog and
broad architecture/Detekt. No correctness gate remains open for Task 14. On 2026-09-11, the user
explicitly accepted every item in the Direction 3 visual checklist. Task 14 is
`COMPLETED/ACCEPTED`.

## Task 15 Delta

- Added `CatalogScreenshotEvidenceTest` with one deterministic 15-PNG Compose-owned surface matrix for
  Discover loading, Manga and Light Novel Popular/Latest/Top Rated sections, durable empty,
  retained refreshing content, retained content with a retryable refresh issue, Story metadata
  loading, complete Story metadata, and Story metadata failure with the cover/summary retained.
- Added `ScreenshotEvidence`, which captures the full Compose root with standard Compose/Android
  APIs and writes non-empty PNG files with deterministic `compact-*` or `wide-*` names. It prefers
  the test package external-media directory for reliable `adb pull`, then falls back to its
  external-files or cache directory; no golden-diff framework or Roborazzi dependency is introduced.
- Evolved `scripts/verify-fast.sh` and `scripts/verify.sh` so both cover the admitted Step 2 modules:
  domain/runtime/feature host tests plus storage and Design System debug assemblies. Existing
  retained/quarantine checks remain present.
- Strengthened `scripts/tests/v2-verification-entrypoints-test.sh` first, observed its expected RED
  against the missing Step 2 tasks, then updated both entrypoints to satisfy the contract.
- Replaced the retention DAO's SQLite 3.24+ `ON CONFLICT ... DO UPDATE` statement after the
  returned API 26 run proved that Android 8.0's SQLite 3.18 rejects it. `touchOrphan` now performs
  the same monotonic `UPDATE MAX(...)` followed by an insert only when absent inside one Room
  transaction. No dependency graph, schema, runtime ownership, public API, or feature scope changed.

## Task 15 Agent-Owned Evidence

- Verification-entrypoint RED: Git Bash test failed with
  `scripts/verify.sh is missing V2 Gradle task: :core:designsystem:assembleDebug` before the script
  changes.
- Verification-entrypoint GREEN:
  `C:\Program Files\Git\bin\bash.exe scripts/tests/v2-verification-entrypoints-test.sh` - PASS,
  `V2 verification entrypoints verified.`
- Screenshot harness RED: `:feature:catalog:compileDebugAndroidTestKotlin` failed only on the absent
  `ScreenshotEvidence` symbol after test-source corrections; GREEN passed after the minimal helper
  was added.
- Focused host gate:
  `.\gradlew.bat :catalog:domain:test :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:testDebugUnitTest --no-daemon`
  - `BUILD SUCCESSFUL` in 21s, 114 actionable tasks.
- Focused instrumentation-source gate:
  `.\gradlew.bat :catalog:storage:compileDebugAndroidTestKotlin :feature:catalog:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon`
  - `BUILD SUCCESSFUL` in 21s, 98 actionable tasks.
- Fresh final combined closure on the completed local tree: the four focused host test tasks plus
  all three instrumentation-source compile tasks above - `BUILD SUCCESSFUL` in 9s,
  129 actionable tasks (1 executed, 128 up-to-date).
- Returned API 26 connected run on `emulator-5554`, Pixel AVD Android 8.0: feature 43/43 PASS and
  app 10/10 PASS; storage FAILED 7/40. Every storage failure converged on
  `StoryRetentionDao.touchOrphan` with `SQLiteException: near "ON": syntax error`, proving the
  direct UPSERT statement was incompatible with the minimum-supported platform SQLite version.
- Post-repair focused instrumentation-source gate:
  `.\gradlew.bat :catalog:storage:compileDebugAndroidTestKotlin --no-daemon` -
  `BUILD SUCCESSFUL` in 41s, 24 actionable tasks (5 executed, 19 up-to-date).
- Returned `scripts/verify-fast.sh` stopped in `scripts/tests/v2-build-surface-test.sh` because the
  retained Step 1 gate still classified the now-required `AndroidLibraryConventionPlugin.kt`, Room
  aliases, and Coil alias as inactive. The Task 0/Step 2 plan and current module build files prove
  those surfaces are active and ownership-scoped. The base gate no longer rejects them, while the
  dedicated Step 2 build-surface gate retains their configuration/artifact ownership checks; the
  base gate continues to reject Hilt, WorkManager, OkHttp, Navigation 3, JavaScriptEngine, backdrop,
  Roborazzi, and generic Room/Hilt convention plugins.
- Post-repair focused shell checks:
  `bash -n scripts/tests/v2-build-surface-test.sh`,
  `bash scripts/tests/v2-build-surface-test.sh`, and
  `bash scripts/tests/v2-verification-entrypoints-test.sh` - PASS.
- The next returned `scripts/verify-fast.sh` run passed the prior gates, then stopped in
  `scripts/tests/v2-retired-runtime-absence-test.sh` because its Step 1 path list still rejected
  the Task 13-admitted `core/designsystem` module. Removing only that stale entry produced the
  expected second RED on the Task 0-admitted `feature` root. The repaired gate now requires
  `core/designsystem` and `feature/catalog`, permits no other direct child below `feature`, and
  continues to reject all other retired V1 runtime/integration paths.
- Retired-path gate RED/GREEN: after the first minimal correction it failed with
  `Retired V1 runtime path still exists: feature`; after adding the exact `feature/catalog`
  allowlist, `bash -n scripts/tests/v2-retired-runtime-absence-test.sh` and
  `bash scripts/tests/v2-retired-runtime-absence-test.sh` both PASS.
- The third returned `scripts/verify-fast.sh` run passed every preceding static/Step 2 gate, then
  stopped in `scripts/verify-source-layout.sh` because its blanket generation-label rule rejected
  the plan-frozen `RemoteHttpsUriV1.kt`. A bounded audit of all active source filenames found only
  that contract, `SourceStoryIdV1.kt`, and its matching test. The verifier now permits exactly those
  three reviewed semantic-version contract paths and continues to reject any other `V1`, `V2`,
  `Legacy`, or `Compat` filename.
- Source-layout gate RED/GREEN: the new fixture containing all three approved paths failed first on
  `RemoteHttpsUriV1.kt`; after the exact-path exception it passes, while an injected
  `LegacySample.kt` remains rejected. `bash scripts/verify-source-layout.sh` also PASSes with no
  line-limit violation; its six `>300`-line messages are advisory structural-review candidates.
- Full bounded static-gate audit through `run_repository_static_gates` reached
  `Structural hard policies verified.` after all nine `scripts/tests/*.sh` checks, structural
  suppression verification, source-layout verification, and the structural review. Its embedded
  Step 2 Gradle surface gate was `BUILD SUCCESSFUL` in 11s with 3/3 tasks executed.
- `git diff --check` reports no whitespace errors; output is limited to Windows LF-to-CRLF
  working-copy warnings for changed files.
- On 2026-09-11, the user reported that every Task 15 command had been run and pulled the
  screenshot artifacts into `task15-evidence/`. Artifact inspection found 15 non-empty PNGs under
  each of `api26/catalog-screenshot-evidence/` and `api37/catalog-screenshot-evidence/`; the two
  matrices visually contain the expected Discover and Story evidence surfaces.
- The pulled API 26 matrix is 1080 x 1731 and the API 37 matrix is 1080 x 2400, but every artifact
  in both matrices is named `compact-*`. Device inspection shows `emulator-5556` is API 37 at
  1080 x 2400 and density 420, which yields about 411dp available width. This is below the
  screenshot helper's frozen 600dp `wide` threshold, so the API 37 run is a second compact run and
  cannot satisfy the required wider-configuration evidence gate.
- The currently retained API 37 connected XML reports show storage 40/40 PASS, app 10/10 PASS,
  and the later filtered screenshot class 1/1 PASS, all with zero failures/errors and exit code 0.
  The later runs overwrote the local full-feature and API 26 connected reports, and the user has
  not yet supplied an explicit PASS summary for those results or the four broad commands.
- The user then reran the screenshot class on a >=600dp configuration and pulled
  `task15-evidence/wide/`. Review confirms exactly 15 non-empty `wide-*` PNGs at 1080 x 2400,
  covering the complete deterministic Discover and Story matrix. Contact-sheet inspection found no
  missing, blank, or incorrectly classified surface. The compact/wide screenshot artifact gate is
  therefore complete; this is correctness/evidence review and does not reopen Task 14 visual design.
- The user explicitly confirms PASS for both complete API 26/API 37 connected commands and all four
  broad commands: architecture/foundation/Detekt, the Step 2 build-surface script,
  `scripts/verify-fast.sh`, and `scripts/verify.sh`. Together with the reviewed compact/wide
  artifacts and retained API 37 XML results, every required Task 15 user-owned gate is accepted.

## Task 15 R2.8 Local Acceptance Map

This map covers every R2.8 criterion without claiming the user-owned device/full gates,
Task 16 performance/profile work, or Task 17 plugin work as complete.

| R2.8 criteria | Current evidence / owner |
|---|---|
| 1-4 | Protected by the accepted Task 0/7 product, startup, and launch-handoff evidence plus `app/src/androidTest/.../startup/`; final cross-device rerun is part of the Task 15 API matrix. |
| 5-8 | Protected by Task 2-6 Room/importer/variant evidence, `CatalogVariantFixtureTest`, and `v2-step2-build-surface-test.sh`; Task 15 entrypoints now retain these module checks. |
| 9-15 | Protected by `module-boundaries.json`, build-logic architecture tests, the Task 13 Design System slice, accepted Task 14 visual evidence, and the accepted Task 15 broad gates. |
| 16-19 | Covered by `DiscoverPersistenceInstrumentedTest`, `DiscoverPublicationRetentionInstrumentedTest`, runtime Discover tests, and Discover UI tests; the Task 15 API 26/API 37 matrix is accepted. |
| 20-24 | Covered by domain identity tests, `CatalogRouteTest`, `StoryRouteRestorationInstrumentedTest`, and Story screen/ViewModel tests. |
| 25-38 | Covered by the five storage connected classes plus importer, mutation-gate, pin, retention, rollback, and non-loop tests from Tasks 2-4; the Task 15 API 26/API 37 matrix is accepted. |
| 39-48 | Covered by deterministic fixture assets, `LocalCoverContinuityInstrumentedTest`, `CoverImagePreflightInstrumentedTest`, remote-policy host tests, and the reviewed compact/wide Task 15 screenshot matrix. |
| 49-55 | Covered by Discover refresh, runtime single-flight/quiescence, lifecycle, cache-hit, startup, foundation, and thread-ownership tests recorded under Tasks 5, 7, 10, 12, and 13. |
| 56-60 | **OPEN - OWNED BY TASK 16** performance, profile regeneration, startup delta, and frame/jank evidence. |
| 61 | Structural/lifecycle ownership is protected by Task 12 runtime/feature tests; quantitative aged/repeated-growth evidence remains **OPEN - OWNED BY TASK 16**. |
| 62 | Task 15 local non-performance correctness is accepted; the performance portion remains **OPEN - OWNED BY TASK 16**. |
| 63-66 | **OPEN - OWNED BY TASK 17** deterministic MangaUpdates plugin integration and optional live smoke scope. |
| 67 | Local retention-on-failure is covered by storage/runtime/feature tests; the plugin-acquisition branch remains **OPEN - OWNED BY TASK 17**. |
| 68 | Protected by the reviewed R2.8 plan and accepted Task 14 freeze; Task 15 introduces no placeholder, new ownership rule, or deferred design decision. |

## Task 15 Required User-Owned Gates

Use explicit serial selection; replace placeholders with real values. The API commands intentionally
run the complete storage, feature, and app connected suites on each target.

```powershell
$env:ANDROID_SERIAL = '<api26-serial>'
.\gradlew.bat :catalog:storage:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon

$env:ANDROID_SERIAL = '<api37-serial>'
.\gradlew.bat :catalog:storage:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon
```

Run the screenshot class once on an accepted compact target and once on a configuration with at
least 600dp available width. Pull each device's evidence before reusing that target/package data.

```powershell
$env:ANDROID_SERIAL = '<compact-serial>'
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.evidence.CatalogScreenshotEvidenceTest' --no-daemon
adb -s '<compact-serial>' pull /sdcard/Android/media/app.openstory.catalog.feature.test/catalog-screenshot-evidence '<host-output-dir>\compact'

$env:ANDROID_SERIAL = '<wide-serial>'
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.evidence.CatalogScreenshotEvidenceTest' --no-daemon
adb -s '<wide-serial>' pull /sdcard/Android/media/app.openstory.catalog.feature.test/catalog-screenshot-evidence '<host-output-dir>\wide'

Remove-Item Env:ANDROID_SERIAL
```

Record device, API, resolution/available-width dp class, command result, and pulled artifact path.
Then run the broad gates from this Windows workspace with Git Bash:

```powershell
.\gradlew.bat verifyArchitecture :app:verifyFoundation detekt --no-daemon
& 'C:\Program Files\Git\bin\bash.exe' scripts/tests/v2-step2-build-surface-test.sh
& 'C:\Program Files\Git\bin\bash.exe' scripts/verify-fast.sh
& 'C:\Program Files\Git\bin\bash.exe' scripts/verify.sh
```

The user confirms every command above PASS, and the compact/wide screenshot artifacts are reviewed.
The Task 15 user-owned gate is accepted.

## Task 16 Implementation Delta

Task 16 agent-owned implementation is present; device verification reached Step 8 after the first
scroll performance gate failed:

- Added the frozen Catalog runtime/UI trace milestones and async latency spans without renaming
  the Task 7 trace authority or adding startup work.
- Added deterministic normal, persisted-empty, repeated-refresh, disk-hit, aged-storage, and
  oversized-detail preparation through the real importer/Room boundary, with direct SQLite setup
  limited to 5,000 unrelated aged rows. The aged setup performs a real importer refresh after
  direct seeding and persists its bounded preparation evidence across force-stop.
- Added the nine required Macrobenchmark journeys and final returning Discover -> Story -> Back
  Baseline Profile journey. `openStoryDiskHit` now waits for settled Discover decode work, trims
  decoded memory before the measured Story click, and requires zero transport requests plus a new
  bounded off-main decode.
- Added benchmark-only live diagnostics for SQL observation counts, acquisition/transport,
  collectors, cover demands/jobs, runtime work, Story pins, decoded/disk ownership, requested
  decode dimensions/thread, and bounded mutation touched counts. Source and transport fixtures
  assert non-main execution on Android.
- Added build-surface ratchets for benchmark helpers and removed the obsolete diagnostics Activity
  mode now that read-only diagnostics use the benchmark-only provider.
- Remediated the final benchmark-source self-review finding without suppression: Catalog fixture
  orchestration, retention/race preparation, cover preparation, and the pin/prune barrier source
  now have separate benchmark-only owners. `BenchmarkCatalogFixture.kt` is 112 lines and
  `BenchmarkCatalogSource.kt` is 192 lines; every split owner remains below the 200-line ratchet.
- Performance evidence owner:
  `docs/internal/v2/catalog-step2-performance-baseline-2026-09-08.md`.

Agent-owned evidence on 2026-09-12:

- `:catalog:runtime:testBenchmarkReleaseUnitTest :feature:catalog:testBenchmarkReleaseUnitTest
  :app:compileBenchmarkReleaseKotlin --no-daemon`: PASS.
- Fresh combined runtime/feature debug + benchmark-release tests and benchmark assemble with
  `--rerun-tasks`: PASS (`BUILD SUCCESSFUL in 1m 26s`, 172/172 tasks executed).
- `:build-logic:test --tests app.openstory.build.architecture.Step2BuildSurfaceVerifierTest
  --no-daemon`: PASS.
- Post-refactor `:feature:catalog:testBenchmarkReleaseUnitTest
  :app:compileBenchmarkReleaseKotlin --no-daemon`: PASS (`BUILD SUCCESSFUL in 16s`, 56 actionable
  tasks).
- Post-refactor full `Step2BuildSurfaceVerifierTest`: PASS (`BUILD SUCCESSFUL in 16s`, 3 actionable
  tasks); the accepted benchmark surface now requires the retention, cover, and barrier owners.
- Focused runtime/feature diagnostics and benchmark compile/assemble checks: PASS.
- Exact Step 6 gate PASS: `BUILD SUCCESSFUL in 12s`, 121 actionable tasks.

The first user-owned Task 16 journey returned on 2026-09-12:

- `coldFreshInstall`: PASS (`BUILD SUCCESSFUL in 1m 48s`, 1 test, 5 iterations) on Redmi Note 9S / API
  35. All frozen fresh-path zero-work assertions passed.
- TTID median: `484.353177 ms`. This preliminary stale-profile result is above the eventual fresh
  review trigger, but is not a valid final Step 1 comparison until Task 16 regenerates the profile.
  The retained JSON and five Perfetto paths plus trace-processor diagnostic are recorded in
  `docs/internal/v2/catalog-step2-performance-baseline-2026-09-08.md`.
- The first `coldReturningDiscover` command also passed at the instrumentation level and returned a
  preliminary TTID median of `483.546927 ms`, storage-ready `146.249896 ms`, first-snapshot
  `186.938281 ms`, content-ready `260.764011 ms`, and first-cover `263.222396 ms`. Review found the
  journey proved one Discover query but omitted the separate frozen `acquisition == 0` assertion.
  That assertion is now added; `:benchmark:assemble --no-daemon` passes (`BUILD SUCCESSFUL in 25s`,
  70 actionable tasks), and `coldReturningDiscover` requires one corrected rerun before acceptance.

No corrected returning-path Macrobenchmark evidence, remaining journey, device decode assertion,
regenerated profile, final startup comparison, broad architecture/Detekt/shell suite, or
physical-device acceptance is claimed here.

## Task Status At Initial Task 16 Handoff

Tasks 0-15: **COMPLETED/ACCEPTED**.
Task 16: **IN PROGRESS at this historical handoff; superseded by the final closure below**.
Tasks 17 through 18: **NOT RUN**.

## Historical Risks / Open Checks

This section records the execution history and is superseded by the final Task 16 closure below.

- Task 15 remains accepted. Task 16 device Macrobenchmark/profile execution and review are open and
  user-owned. The preliminary `coldFreshInstall` result and incomplete first
  `coldReturningDiscover` result are retained. The corrected `coldReturningDiscover` rerun now
  passes all five iterations with `discoverQueries == 1` and `acquisition == 0`; its JSON, five
  Perfetto traces, benchmark message, and Gradle log are retained under
  `benchmark/build/task16-retained/2026-09-12/coldReturningDiscover-corrected/`.
  `multiSectionDiscoverScroll` then passed its journey assertions and completed five iterations
  (`BUILD SUCCESSFUL in 2m 34s`) but failed every frozen frame trigger: CPU P95 `30.597154 ms`, CPU
  P99 `88.974780 ms`, and overrun P95 `32.106 ms`. All five per-run P95 values also exceed the
  16.67 ms CPU/overrun thresholds. Raw JSON, five Perfetto traces, benchmark message, and Gradle log
  are retained under `benchmark/build/task16-retained/2026-09-12/multiSectionDiscoverScroll/`; the
  JSON SHA-256 is `174cadd9ecd9f0ae91d7ab11268041766ed41176cd1155bf6f6f9b53bd8695dd`.
  Trace diagnosis localizes the dominant cost to RenderThread/GPU command flush, shader
  compilation/cache misses, buffer stuffing, and missed app deadlines; the trace also contains
  EdgeEffect overscroll work. Step 8 corrected the benchmark driver to stop when selectors are
  visible, use V1-compatible deterministic 20-step swipe geometry, and clear package data only for
  the first frame-fixture preparation while later iterations force-stop and idempotently reseed.
  The focused 3-test driver gate passes (`BUILD SUCCESSFUL in 46s`). The final corrected scroll run
  passes (`BUILD SUCCESSFUL in 1m 55s`); median-of-five per-iteration percentiles are CPU P95
  `13.637 ms`, CPU P99 `18.922 ms`, and overrun P95 `7.961 ms`, all below their frozen triggers.
  Final raw evidence is retained under
  `benchmark/build/task16-retained/2026-09-12/multiSectionDiscoverScroll-warm-cache-policy/`; JSON
  SHA-256 is `fc165d33a795a0e20cce09aa2e178c7325679aa4b47bf0026a0f6547d110ef88`.
  `openStoryMemoryHit` then passed its transport/decode/query assertions and completed five
  iterations (`BUILD SUCCESSFUL in 2m 38s`) but failed the frozen Story-open frame gate: median
  per-iteration CPU P95 `40.166 ms` and overrun P95 `41.294 ms`, both above `33.33 ms`. Story
  content-ready median is `57.775625 ms`. Raw JSON, five Perfetto traces, benchmark message, and
  Gradle log are retained under `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit/`;
  JSON SHA-256 is `537462e8d393f82e1d72269caa234ab4ff07b6eeac34d818ab866a02bf3f4c03`.
  Three evidence-driven Story-layout corrections were then measured and rejected: separate lazy
  metadata items (`43.685 ms` CPU P95 / `38.222 ms` overrun P95), first runtime state before route
  exposure (`51.303 ms` / `58.401 ms`), and a one-frame metadata deferral (`39.047 ms` /
  `32.157 ms`). The latter two raw runs are retained under
  `openStoryMemoryHit-first-state-before-route/` and `openStoryMemoryHit-deferred-metadata-frame/`
  with JSON SHA-256 values `6e6bd5a1b1e6ac10885ad09587487a61f5c543a73382ba2a96e955e65dfd28f4`
  and `bf22b499507e36925c53f0157ce79841d55b4bf6923741860b27f0a7e2df02c6`. All three experiments
  were removed after measurement. Trace evidence now identifies the load-bearing problem as Story
  summary/detail publication and text-heavy layout partitioning; a fourth tactical tweak is not
  authorized without architectural review and a new RED characterization.
  The five later Step 7 journeys and the post-regeneration final startup comparison remain open. The current generated
  profile files are stale relative to the uncommitted Task 16 code and must not be used for final
  comparison. The first corrected returning rerun attempt on 2026-09-12 was blocked before
  measurement because the Redmi Note 9S secure PIN keyguard was showing; fixture reproduction
  completed normally once isolated, so no code/performance failure is claimed and no replacement
  JSON was produced. That failed connected task cleaned the earlier benchmarkRelease JSON/Perfetto
  outputs, so the preliminary numeric record remains but raw Task 16 device artifacts must be
  regenerated. Task 17 plugin integration and Task 18 final acceptance remain `NOT RUN`.

## Task 16 R2.10 Stage 0 Delta

```text
Stage 0 - COMPLETE / CLOSED
Hypothesis: stale/underrepresentative profile materially explains Story-open tail
Result: REJECTED as material root contributor for the next correction
Next: Stage A root characterization
Stage B production correction remains BLOCKED
```

- The user-supplied R2.10 amendment, integrated into the tracked owning plan and checkpoint rather
  than retained as a standalone repository file, confirms that Tasks 0-15 remain
  completed/accepted and changes only the Task 16 Step 8 resume order. Compilation/profile
  isolation now precedes Stage A architecture characterization or any fourth production correction.
- `openStoryMemoryHitFullCompilationDiagnostic` reuses the accepted Story memory-hit fixture,
  counters, trace metric, five iterations, and UI shape under `CompilationMode.Full`. The existing
  `openStoryMemoryHit` acceptance journey remains `CompilationMode.Partial` with
  `BaselineProfileMode.Require`.
- TDD RED: `:benchmark:compileBenchmarkReleaseKotlin --no-daemon` failed only on the missing
  `storyOpenFullCompilationMode` symbol.
- Agent-owned GREEN: `:benchmark:assemble --no-daemon` completed `BUILD SUCCESSFUL in 17s`, with 66
  actionable tasks. No production UI/runtime behavior changed.
- The Stage 0 Redmi Note 9S diagnostic passed its hard counters and five iterations (`BUILD
  SUCCESSFUL in 2m 37s`) but remained frame-red. Median-of-five per-iteration CPU P95 is
  `47.354 ms`; overrun P95 is `47.799 ms`, respectively `+7.188 ms` and `+6.505 ms` versus the
  retained Partial+Require baseline. Story content-ready median improved to `36.675 ms`, showing a
  compilation contribution to data-ready latency but not to the unresolved frame tail.
- Raw evidence is retained under
  `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit-full-compilation/`; JSON SHA-256 is
  `1d20d6985b629a6d96e21b8b721724f83bea52870ca17c9259786911b95d0395`.
- The compilation/profile explanation for the frame failure is rejected. This does not identify
  Full compilation as a regression cause; the Full result is used only to show that profile
  omission is not a material explanation for the next correction.
- The diagnostic-only Full benchmark entrypoint/configuration was removed after evidence closure.
  The normal acceptance journey remains `CompilationMode.Partial(BaselineProfileMode.Require)`.
- Presentation/composition topology remains the dominant unresolved root. Stage A follows below;
  Stage B, later Task 16 journeys, profile regeneration, and Task 17 remain blocked.

## Task 16 R2.10 Stage A Characterization

Stage A is **COMPLETE / CHARACTERIZED / USER VERIFIED**. No Stage B production correction was
implemented.

### 1. Proven facts

- Durable/runtime ownership remains unchanged. `CatalogCapabilityActivation.Available` still uses
  one keyed `storySessions.getOrPut(ref)`, and two callers receive the same `StoryDetailSession`.
  Multiple state collectors still share one `StoryDetailReadPort.observe(ref)` subscription. No
  repository, observer, flow, query, selected-Story cache, retained DTO, or presentation owner was
  added.
- Process restoration remains exactly the validated `StorySourceRef` plus optional
  `CoverAssetKey`; no Story summary/detail payload was added to saved route state.
- The focused ViewModel characterization records the cache-hit publication shape as:

```text
StateFlow initial null
-> route active: summary=null, detail=null, route cover key retained
-> one coherent persisted projection received
-> one UI publication with summary=full and detail=full
```

  The current memory-hit path therefore does jump from the empty Story route state to a broad
  summary+detail publication rather than publishing a naturally progressive summary-only state.
- `detail != null` is the single current gate for `StoryMetadataSections`. That subtree contains
  persisted facts/body and also `StoryPreviewActions`, `StoryPreviewTabs`, and
  `StoryRecommendationPreview`; the compiled UI characterization records that the Read/Library
  shell, tabs, and recommendation shell appear with the rich-detail transition rather than owning
  independent readiness.
- `StoryHero` still accepts the broad `StoryDetailUiState`, although its reads are limited to
  summary identity plus top-level cover locator/key. A detail-only state change leaves all fields
  read by Hero equal while still changing the state object delivered through
  `CatalogScreen -> StoryDetailScreen -> StoryHero`.
- Artwork has duplicate presentation authority: `StorySummaryUi` contains `coverAssetKey` and
  `coverLocator`, while `StoryDetailUiState` repeats both. Route continuity initially supplies only
  the top-level `CoverAssetKey`; the persisted projection later supplies the locator and duplicates
  both values at the two levels. With the same asset key, locator arrival changes `CoverRequest`
  equality/model input while preserving the same stable memory/disk cache key.
- `CatalogSessionContent` collects the full Story state before route rendering and passes it through
  `CatalogScreen`. This collection scope is source-proven; no Stage A evidence proves that it is a
  dominant invalidation amplifier.
- Lightweight deterministic markers were added to the existing trace authority:
  `story-projection-received`, `story-ui-published`, `story-hero-materialization`, and
  `story-body-materialization`. They annotate existing boundaries only. The existing
  `story-detail-content-ready` marker remains runtime/data-ready, not rendered-ready.

### 2. Candidates

- Root-level Story collection may amplify invalidation, but remains a structural smell until a
  runtime trace attributes repeated or widened composition work to that collector.
- Locator arrival for an unchanged asset key changes the request/model object. Actual painter
  restart/request churn is not proven; stable cache-key continuity is proven, so no artwork change
  is authorized from Stage A alone.
- Hero participation after a broad destination-state publication is now traceable and structurally
  expected, but its isolated share of the frame tail is not measured in Stage A.
- Body/facts text measurement remains the strongest physical-work candidate from retained Perfetto
  evidence. Stage A does not claim a one-to-one mapping from source `Text` calls to trace slices.

### 3. Exact publication/materialization sequence

```text
Story route requested
-> one keyed StoryDetailSession activated and pinned
-> initial Story UI state published: summary=null, detail=null, route cover key only
-> route becomes active
-> Room/runtime coherent Story projection received
-> HikariV2:story-projection-received
-> HikariV2:story-detail-content-ready when rich detail is present (data-ready only)
-> StoryDetailViewModel publishes one broad summary+detail StoryDetailUiState
-> HikariV2:story-ui-published
-> CatalogSessionContent root collector invalidates the Story destination path
-> Story Hero item participates / HikariV2:story-hero-materialization
-> detail-gated body plus action/tab/recommendation shells participate /
   HikariV2:story-body-materialization
-> Compose measure/layout tail (retained trace evidence; no new device run in Stage A)
```

### 4. Stage B recommendation

If Stage B is explicitly authorized, keep Room/runtime unchanged and make one tightly bounded
presentation ownership correction in this order: narrow Hero input to the identity/artwork subset;
establish one artwork presentation owner while preserving route-cover continuity; move the static
action/tab/recommendation blueprint shell out of rich-detail readiness; project rich detail into a
bounded body/facts input. Do not add a stream, query, cache, retained DTO, preview, delay, yield,
frame shift, route-order change, or reduced blueprint. Rerun only focused owning tests and then the
single `openStoryMemoryHit` journey after an accepted Stage B correction.

### 5. Stage A evidence and self-review

- RED: the focused runtime/feature test command failed on the missing four trace boundaries and
  missing ViewModel publication callback, as intended.
- GREEN: `:catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest` filtered to
  `CatalogTraceTest`, `StoryDetailSessionTest`, `CatalogUiTraceTest`, and
  `StoryDetailViewModelTest`: PASS (`BUILD SUCCESSFUL in 28s`, 55 actionable tasks).
- Compile/contract gate: `:feature:catalog:compileDebugAndroidTestKotlin
  :app:testDebugUnitTest --tests '*StartupTraceContractTest' :benchmark:assemble`: PASS
  (`BUILD SUCCESSFUL in 54s`, 174 actionable tasks).
- User-owned connected characterization on Redmi Note 9S / API 35:
  `:feature:catalog:connectedDebugAndroidTest` filtered to
  `StoryDetailScreenInstrumentedTest#richDetailCurrentlyUnlocksBodyAndUnrelatedBlueprintShellsTogether`:
  PASS (`1 test`, `BUILD SUCCESSFUL in 43s`, 114 actionable tasks). This closes the Stage A device
  characterization gate without running another Task 16 journey.
- Final-tree verification split the filtered JVM tests from non-test tasks so Gradle applies
  `--tests` only to test tasks: focused runtime/feature/app tests PASS (`BUILD SUCCESSFUL in 52s`,
  112 actionable tasks), then Android-test compile plus benchmark assemble PASS
  (`BUILD SUCCESSFUL in 34s`, 113 actionable tasks).
- No owner/stream/query/schema/DAO was added; no production state shape, route timing, scheduling,
  UI presence, benchmark threshold, or benchmark workload changed. The Full diagnostic-only
  benchmark code was removed while its artifacts/checksum/docs were retained.
- The Task 14/15 blueprint remains intact. No V1 multi-stream/dual-truth mechanism, benchmark
  gaming, or Stage B refactor was introduced. `StoryHero` and `StoryMetadataSections` production
  inputs remain unchanged; trace `SideEffect`s sit at their existing screen item boundaries.

## Task 16 R2.10 Stage B1 Delta

Stage B1 is **COMPLETE / B1-B — ARCHITECTURAL IMPROVEMENT, PERFORMANCE NEUTRAL**. Story-open
remains frame-red, so no later Stage B correction is authorized in this turn.

### 1. Ownership correction

- Added one small immutable `StoryHeroUi` projection containing only Hero-owned identity fields
  (`title`, media type, rating, publication/status summary, latest-update label) plus the artwork
  locator/key required for route-cover continuity. `StoryDetailUiState.toHeroUi()` derives this
  projection without adding stored state, a Flow, collector, query, repository, cache, or owner.
- `StoryHero` no longer accepts `StoryDetailUiState`; its production signature accepts only
  `StoryHeroUi`, layout spacing, modifier, and the existing lightweight materialization callback.
  Rich detail/body, authors/artists/genres/description, detail loading, issue/retry state,
  destination activity, tabs, recommendation state, and future capability state cannot enter the
  Hero dependency contract.
- The Hero materialization marker moved from the parent Lazy item into `StoryHero`, so it now marks
  actual Hero participation rather than any parent item recomposition. Body tracing remains at the
  existing detail item boundary.
- Existing artwork duplication between `StorySummaryUi` and top-level `StoryDetailUiState` remains
  intentionally unchanged; consolidating that ownership is the next separate Stage B hypothesis,
  not part of B1. Room/runtime/session/acquisition/route timing and blueprint composition are
  unchanged.

### 2. RED -> GREEN evidence

- RED: `:feature:catalog:testDebugUnitTest --tests '*StoryHeroUiTest' --no-daemon` failed at
  `compileDebugUnitTestKotlin` only because `toHeroUi()` did not exist.
- GREEN: the same focused command passed after the projection/signature correction (`2 tests`,
  `BUILD SUCCESSFUL in 27s`, 50 actionable tasks).
- Focused ViewModel/Hero unit tests plus androidTest compilation passed (`BUILD SUCCESSFUL in 34s`,
  60 actionable tasks). The Stage A runtime/feature characterization set also passed
  (`BUILD SUCCESSFUL in 28s`, 59 actionable tasks).
- `StoryDetailScreenInstrumentedTest` passed all 7 tests on Redmi Note 9S/API 35. The first isolated
  test attempt lost its Compose hierarchy only after the physical screen timed out and the host
  Activity was destroyed; device log showed no production exception. A rerun with a temporary
  guarded screen-timeout override passed, and the original device setting was restored.
- `CatalogScreenshotEvidenceTest` passed on the same device and produced the existing 15-PNG
  deterministic surface matrix. The Story blueprint/geometry/semantics remained intact.
- Focused `Step2BuildSurfaceVerifierTest` and `ProductionPackageStructureVerifierTest` passed.
  `git diff --check` passed.
- Final-tree rerun after checkpoint reconciliation: focused `StoryHeroUiTest`,
  `StoryDetailViewModelTest`, `CatalogUiTraceTest`, and Android-test compilation passed with
  `--rerun-tasks` (`BUILD SUCCESSFUL in 1m 41s`, 60 actionable tasks). The two focused architecture
  verifiers also passed with `--rerun-tasks` (`BUILD SUCCESSFUL in 28s`, 3 actionable tasks), and
  `git diff --check` remained clean.

### 3. `openStoryMemoryHit` result

The only rerun journey was the normal frozen acceptance path:

```text
CompilationMode.Partial(
    baselineProfileMode = BaselineProfileMode.Require
)
5 iterations, unchanged memory-hit fixture and hard counters
Redmi Note 9S / API 35
BUILD SUCCESSFUL in 3m 7s
```

Median-of-five per-iteration P95 values:

| Metric | B1 runs (ms) | B1 median | Frozen baseline | Delta |
|---|---|---:|---:|---:|
| CPU P95 | `41.626, 41.782, 52.289, 36.114, 60.039` | `41.782 ms` | `40.166 ms` | `+1.616 ms` / `+4.02%` |
| Overrun P95 | `33.808, 31.537, 44.659, 37.577, 55.564` | `37.577 ms` | `41.294 ms` | `-3.717 ms` / `-9.00%` |

- Both medians remain above the frozen `33.33 ms` review trigger. Neither metric deteriorated by
  more than the separate 10% material-regression rule, so this is B1-B rather than B1-C.
- Story data-ready median was `57.276 ms`, approximately unchanged from the retained `57.776 ms`
  baseline. B1 does not claim broad Hero state was the complete physical root.
- Perfetto SQL inspection found the expected order in all five iterations:
  `story-projection-received -> story-ui-published -> story-hero-materialization ->
  story-body-materialization`.
- Raw JSON, five Perfetto traces, benchmark message, and Gradle log are retained at
  `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit-stage-b1-hero-boundary/`.
  The benchmark JSON SHA-256 is
  `7291fce5b5e9292d21f8adbcb4e7fab6557177a18c97af44099e383daf23a3b20`.

### 4. B1 self-review and next hypothesis

- No second truth, observer, Flow, collector, query, repository, coroutine owner, retained DTO,
  presentation map, preview, delay/yield/frame deferral, route-order change, or benchmark branch
  was added.
- `StoryHeroUi` is a value projection of the existing publication, not a stored wrapper around the
  broad state. Tests prove destination-wide detail/loading/issue/activity changes leave it equal,
  while Hero identity and artwork changes alter it.
- Route-cover continuity and the existing duplicate artwork representation are unchanged. The
  latter remains explicit debt rather than being silently folded into B1.
- Body/actions/tabs/recommendation gating and every Task 14/15 visual shell remain unchanged; no
  work was removed, deferred, or shifted outside the measured window.
- Tests were strengthened from the Stage A characterization: rich-detail publication still admits
  the body and blueprint shell, but no longer rematerializes Hero when Hero-owned input is equal.
- Next single evidence-driven correction, if explicitly authorized, is Stage B2: establish one
  artwork presentation owner and remove the duplicate cover fallback/authority while preserving
  the route cover key and first persisted locator transition. Do not begin shell/detail decoupling,
  body/facts slicing, preview, or layout tuning in the same correction.

## Exact Resume Boundary

### Final-profile Story-open confirmation - 2026-09-13

- The Stage B2 artwork-owner correction is present in the working tree: `StoryArtworkUi` is the
  single route/persisted artwork presentation value, `StorySummaryUi` no longer duplicates the
  cover key/locator, and Hero still consumes the bounded immutable projection introduced by B1.
  This does not add a Flow, query, repository, cache, retained DTO, or runtime owner.
- The user-reported final fixture regeneration produced `20,874` rules and differed by only nine
  rules from the immediately preceding regeneration (`99.96%` retained). The checked-in generated
  Baseline and Startup Profile files are byte-identical at `20,874` rules with SHA-256
  `797b58730c732777698f3aba24dc6ea11302cd8bfa4b17e75c351568f4ac54eb`. The source/runtime base
  commit is `1cdbee50c708e1407b8c2b9a8aa148d1a44c4f98` plus the uncommitted Task 16 delta.
- Per the explicit resume boundary, only the normal `openStoryMemoryHit` journey was rerun on the
  connected Redmi Note 9S / API 35 under
  `CompilationMode.Partial(BaselineProfileMode.Require)`. The command completed one test and five
  measured iterations with `BUILD SUCCESSFUL in 2m 11s`.
- All frozen journey assertions passed: transport reads remained zero, successful decode count did
  not increase, and Story observation queries remained within `1..4`. Story content-ready runs
  were `20.588`, `30.518`, `34.198`, `31.124`, and `29.324 ms`; median `30.518 ms`.
- The final-profile frame gate remains red. Per-iteration CPU P95 was `47.175`, `47.742`, `49.406`,
  `37.434`, and `39.585 ms`; median `47.175 ms > 33.33 ms`. Per-iteration overrun P95 was `36.551`,
  `41.649`, `53.266`, `40.432`, and `31.999 ms`; median `40.432 ms > 33.33 ms`.
- Versus the first correctness-green frozen baseline `40.166 / 41.294 ms`, CPU P95 deteriorated by
  `+7.009 ms / +17.45%`, crossing the separate `>10%` review rule; overrun P95 improved by
  `-0.862 ms / -2.09%`. Profile regeneration therefore does not close or explain away the
  Story-open frame failure.
- Fresh raw evidence is retained under
  `benchmark/build/task16-retained/2026-09-13/openStoryMemoryHit-final-profile-confirmation/`.
  Benchmark JSON SHA-256 is
  `724cee9919a54d20145a4d26fdb6080d878590d9d023edd8ebbf5d83692082e5`; the directory contains the
  five Perfetto traces, benchmark message, and Gradle log.
- No other journey was rerun: the fixture repair/profile regeneration did not change their
  production, layout, query, or image shape, and the direct user boundary requested this single
  final-profile Story-open authority run.

### Accepted Story Detail performance debt

- On 2026-09-13 the user explicitly accepted the remaining Story Detail frame tail as deferred
  performance debt so Task 16 can proceed without weakening or relabeling the frozen thresholds.
  The debt covers the final-profile Story transitions whose hard correctness/resource gates pass
  but whose frame review triggers remain red:
  - `openStoryMemoryHit`: CPU P95 `47.175 ms`, overrun P95 `40.432 ms`;
  - `openStoryDiskHit`: CPU P95 `41.906 ms`, overrun P95 `41.678 ms`;
  - `storyBackToDiscover`: CPU P95 `66.212 ms`, overrun P95 `59.902 ms`.
- This is an explicit deferral, not a performance PASS, threshold change, baseline reset, benchmark
  waiver, or permission to weaken the Story blueprint. Future performance hardening resumes from
  the retained final-profile traces and must preserve the accepted Room/runtime/session, query,
  transport/decode, cache, route-continuity, and Task 14/15 UI contracts.
- Final agent-owned Task 16 checks remain green on the accepted tree: runtime/feature unit tests
  plus benchmark assemble passed (`BUILD SUCCESSFUL in 46s`, 125 actionable tasks); the focused
  Step 2 build-surface test, startup trace contract, and Catalog androidTest compilation passed
  (`BUILD SUCCESSFUL in 33s`, 111 actionable tasks).

### Accepted startup performance debt and Task 16 closure

- On 2026-09-13 the user explicitly accepted the remaining startup review-trigger deviations as
  deferred performance debt: fresh TTID median `507.138 ms > 433.632 ms` and returning TTID median
  `467.346 ms > 453.803 ms`. Against the Step 1 reference medians, these are respectively
  `+28.65%` and `+13.28%`.
- The startup runs used the immediately preceding generated profile. The fixture-fixed final
  regeneration kept `20,874` rules and differed by only nine rules (`99.96%` retained), while no
  production/layout/query/image shape changed. The user explicitly accepts the no-rerun decision
  as a profile-equivalence exception and deferred performance debt; this is not represented as
  strict Step 10 ordering or a startup performance PASS.
- Retained startup evidence remains under
  `benchmark/build/task16-retained/2026-09-13/coldFreshInstall-final-profile/` and
  `benchmark/build/task16-retained/2026-09-13/coldReturningDiscover-final-profile/`; benchmark JSON
  SHA-256 values are respectively
  `57fc7ea1e8e8531c722c98f07500c4b1455a2808c2a9334fa191bbda03735429` and
  `66ca39c2d11c94c066e01ca78c1953a2bfb71605a25be31df9a1e9fcdd7764d0`.
- All Task 16 correctness, query/work, transport/decode, cache/resource, terminal ownership,
  profile-generation, and focused compile/test gates are accepted. The remaining Story Detail and
  startup threshold deviations are documented debt rather than hidden PASS results.

At Task 16 closure, Task 17 became `READY TO START` in a new turn while Task 18 remained `NOT RUN`.
Task 17 execution is recorded below. Do not reopen Task 16 performance work unless explicitly
authorized as a separate follow-up or required by a later production/layout/query/image-shape
change.

## Task 17 Implementation Delta

- The deterministic MangaUpdates reference harness is confined to
  `feature/catalog/src/androidTest`. Production/main/release code and manifests are unchanged.
- The reference `manifest.json` and actual `main.js` were copied byte-for-byte from remote branch
  `perf/whole-app-big-update-v3` commit `685a1f8c674efb09eb07c9b2e6c9797ee5298f64`. Their SHA-256
  values match the owning-plan anchors exactly:
  - manifest: `777d257590ca8d1b1791956bed135c5029c62e244807f155a63911db627a2cd5`;
  - main script: `b144ef4fd6ab3c0c319c6f9c92c787bb7796f07559ebaf06ce85d6e11f0e3202`.
  The original archive container was not present locally, so its expected
  `c4107742c1c06848aa48fc4e494192d4b9166fe5d364b4cb131c2b3dca2b5b5c` hash was not
  independently recomputed; the copied member bytes are runtime hash-guarded by the harness.
- `:feature:catalog.testDependencies` is now exactly `[":plugins:api"]`. The four Task 17
  dependencies are exact `androidTestImplementation` entries: `:plugins:api`, AndroidX
  JavaScriptEngine `1.1.0`, serialization JSON, and coroutines core. The Step 2 verifier rejects
  moving plugin API or JavaScriptEngine to another module/configuration and requires the complete
  harness dependency set.
- `ReferencePluginExecutor` is test-only and preserves the bounded V1 execution shape without a
  `:plugins:runtime` edge: owned 15,000 ms `withTimeoutOrNull` deadline, optional 16 MiB isolate
  heap, 256 KiB request/response bridge messages, 2 MiB final output, cancellation propagation,
  message-port closure, isolate closure, and sandbox discard when cancellation cannot terminate an
  isolate.
- `ReferencePluginBridge` loads the real asset, validates the real manifest/protocol, owns only the
  `host.http` bridge, enforces HTTPS + manifest-host policy, and maps unchanged protocol DTOs to
  Step 2 acquisitions. Host binding remains authoritative for source key, source version,
  acquisition clock, and asset policy. Explicit `WEB_NOVEL`/`ANIME` and unknown raw provider kinds
  are ineligible rather than coerced; the raw-type guard admits only the reviewed MangaUpdates
  Manga-like vocabulary so the reference script's broad fallback cannot silently rename an
  unsupported provider kind to Manga.
- The canonical focused instrumentation class covers hard JavaScriptSandbox support, three
  semantic Home kinds, Manga/Light-Novel filtering, unsupported-kind rejection, exact detail route
  identity, host-owned provenance, stricter Step 2 source/title/cover/authors/genres/description
  bounds, failure retention, unchanged Room reads, real Discover/Story composables, disabled reader
  action, executor deadline/byte ceilings/cancellation, and real-locator artwork policy,
  redirect/count, media-type, streaming-size, dimension-preflight, identity, and disk-hit behavior.
- The Step 2 shell gate now explicitly requires the four androidTest dependencies and rejects
  production plugin/JavaScriptEngine dependency scope, production INTERNET permission, and plugin
  harness/reference content in the release AAR.

## Task 17 Agent-Owned Evidence

- RED: focused build-logic tests failed because the accepted fixture carried the new test edge while
  the verifier still expected an empty edge, and because production-scoped plugin/JavaScriptEngine
  dependencies were not yet rejected.
- GREEN build surface: `./gradlew :build-logic:test --tests
  app.openstory.build.architecture.Step2BuildSurfaceVerifierTest --tests
  app.openstory.build.ModuleGraphTest verifyStep2BuildSurface --no-daemon` ->
  `BUILD SUCCESSFUL in 15s` (8 actionable tasks); the actual verifier prints
  `Step 2 build surface verified.`
- GREEN feature cone: `./gradlew :feature:catalog:compileDebugAndroidTestKotlin
  :feature:catalog:testDebugUnitTest --no-daemon` -> `BUILD SUCCESSFUL in 16s` (62 actionable
  tasks).
- AndroidTest dependency inspection confirms `project :plugins:api`,
  `androidx.javascriptengine:javascriptengine:1.1.0`, serialization JSON, and coroutines core on
  `debugAndroidTestCompileClasspath`.
- Release dependency inspection confirms `releaseRuntimeClasspath` contains neither
  `:plugins:api` nor `androidx.javascriptengine`.
- `bash -n scripts/tests/v2-step2-build-surface-test.sh` through Git Bash -> syntax PASS.
- Fresh copied-asset SHA-256 checks match the two reviewed member hashes above.
- Content-type guard TDD: the pre-fix bytecode probe mapped raw `Audio Drama` to `MANGA`, and the
  first fail-closed classifier pass exposed that an unrecognized raw type was still treated as
  missing evidence and accepted. The final guard records declared unknown types as ineligible,
  admits the reviewed Manga/Manhwa/Manhua/OEL, Novel/Light Novel, Web Novel, and Anime vocabulary,
  and rejects both broad Manga fallback (`Audio Drama`) and broad Novel substring (`Visual Novel`).
  The final classifier/eligibility probe reports `UNKNOWN_RAW_ACCEPTED=false` and
  `CONTENT_TYPE_GUARD=PASS`; the affected feature cone rerun completed with `BUILD SUCCESSFUL in
  9s` (58 actionable tasks; 1 executed, 57 up-to-date).

## Task 17 Accepted User-Owned Evidence

The user reported the final Task 17 matrix PASS on 2026-09-13 on the same explicitly selected
JavaScriptSandbox-supported device:

- serial: `adb-91f68893-N7oZEX._adb-tls-connect._tcp`;
- model: `Redmi Note 9S`;
- API: 35 (Android 15).

The focused real-JS integration class completed successfully, including its hard
`JavaScriptSandbox.isSupported()` assertion:

```bash
ANDROID_SERIAL=adb-91f68893-N7oZEX._adb-tls-connect._tcp \
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.plugin.MangaUpdatesCatalogIntegrationTest \
  --no-daemon
```

The full feature connected suite then completed successfully on the same device:

```bash
ANDROID_SERIAL=adb-91f68893-N7oZEX._adb-tls-connect._tcp \
./gradlew :feature:catalog:connectedDebugAndroidTest --no-daemon
```

The release-cleanliness/architecture Gradle gate and Step 2 shell gate also completed successfully:

```bash
./gradlew :build-logic:test :feature:catalog:assembleRelease :app:assembleRelease \
  verifyArchitecture :app:verifyFoundation --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

The final verified tree retains the fixes exposed during user verification: the real Discover UI
assertions scroll to stable semantic section tags, merged-manifest startup verification admits only
`BenchmarkDiagnosticsProvider` and only for `benchmarkRelease`/`nonMinifiedRelease`, and the shell
gate covers all release-like app variants without weakening production provider policy. Task 17 is
completed/accepted; the optional live MangaUpdates smoke was not requested and is not acceptance
evidence.

## Task 17 Exact Resume Boundary

Task 17 is completed/accepted. Stop after its checkpoint/roadmap update and commit. Task 18 remains
`NOT RUN`; it is ready to start only in a new explicitly authorized turn.
