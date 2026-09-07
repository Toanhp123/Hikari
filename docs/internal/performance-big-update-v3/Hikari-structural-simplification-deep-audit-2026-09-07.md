# Hikari Big Update — Structural Simplification / Dead-Code / Duplicate-Logic Deep Audit

Date: 2026-09-07  
Audited inputs:

- `Hikari-master.zip`
- `Hikari-performance-big-update-baseline-2026-09-07-v3.zip`

## 1. Executive conclusion

The v3 performance baseline is strong as a **root-cause performance architecture plan**. It has a coherent 33-family closure matrix, bounded-data contracts, explicit wave ordering, migration ownership, risk gates, benchmark dimensions, and a useful final semantic-scope→physical-work revalidation.

It is **not yet a complete “big update / repository cleanup” plan**.

The missing layer is a dedicated pre-freeze pass whose purpose is not another collection of local performance patches, but to reduce the amount of production structure that remains after those patches:

- dead / orphan production code;
- test-only helpers and fakes shipped in `src/main`;
- duplicate semantic mappers and near-duplicate algorithms;
- one-shot operations routed through reactive APIs unnecessarily;
- N+1 work outside the current 33-family census;
- package ownership cycles inside otherwise acyclic Gradle modules;
- oversized authority classes that the repository itself explicitly allowlists as temporary/v1 debt;
- broad public/constructor surfaces that make call paths and ownership harder to reason about;
- compatibility/adapter paths that survive after their caller count reaches zero;
- structural gates that currently detect “large/suspicious” code but do not require the debt budget to shrink.

**Recommendation:** do not replace the existing Waves 0–8. Add one mandatory **Structural Simplification / Repository Hygiene gate after behavior-changing Waves 1–7 have stabilized and before final Wave-8 freeze**. Wave 8 Task 8 should remain responsible for upgrade/compatibility retirement, while the new gate owns repo-wide dead-code, duplicate-logic, package-DAG, test-surface, and authority extraction work.

A second important result is that the 33-family census is not fully closed: this audit found one additional high-confidence performance root cause in Library Content Mapping (proposed **X19** below).

---

## 2. Why the current roadmap misses this layer

The baseline does mention cleanup, but its scope is narrower than a whole-repo simplification pass:

- Master roadmap Wave 8 says “remove obsolete compatibility paths that are proven safe to retire”.
- Wave 8 Task 8 explicitly enumerates compatibility introduced/superseded by Waves 1–6 and old performance paths.
- The per-wave self-review asks for duplicate fallback paths and semantic-scope→physical-work expansion.
- The roadmap also says **“Do not mix unrelated cleanup with performance commits.”**

Those rules are correct, but they create an intentional gap: unrelated-but-real structural debt is excluded from the performance tasks, and no dedicated later owner exists for it.

The existing final source-level audit is primarily a **physical-work scope audit** (Room/files/Keystore/process maps/work queues). It does not define acceptance criteria for:

- product reachability / dead production symbols;
- test-only code in production source sets;
- clone/semantic-mapper drift;
- package dependency SCCs;
- shrinking `source-layout-allowlist.txt`;
- public API surface reduction;
- unnecessary `Flow.first()` one-shot wrappers;
- duplicated domain-neutral normalization/similarity primitives.

Therefore v3 can reach “33/33 closed” while still shipping a repository that is larger and more tangled than necessary.

---

## 3. Repository structural census

Static census of `src/main/**/*.kt` in the supplied source tree:

- **624 Kotlin production-source files**
- **~61,898 production Kotlin LOC**

The repository’s own `scripts/structural-review-report.sh` reports **316 review candidates**. This is a *review census*, not 317 confirmed defects:

| Candidate category | Count |
|---|---:|
| imports | 130 |
| long functions | 81 |
| wide constructors | 32 |
| large files | 31 |
| broad public-method surface | 26 |
| broad/generic names | 10 |
| explicitly allowlisted production large files | 6 |

This is valuable because the repository already knows where its structural pressure points are; the big-update roadmap simply does not currently turn that information into a shrinking-debt acceptance gate.

### 3.1 Explicitly deferred production files

`config/source-layout-allowlist.txt` contains several production exemptions whose reasons explicitly describe temporary debt:

- `feature/catalog/.../DiscoverViewModel.kt` — **658 LOC** — “pending a dedicated extraction plan”.
- `reader/.../ReaderAssetCoordinator.kt` — **1159 LOC** — invariants “intentionally co-located for v1”.
- `reader/.../ReaderRouteCoordinator.kt` — **587 LOC** — route execution + selected-release refresh “share one v1 authority”.
- `reader/.../ReaderRouteSession.kt` — **683 LOC** — commit/refresh/prefetch lifecycle “remain one session authority”.
- `storage/room/.../RoomCatalogRepository.kt` — **510 LOC** — transaction orchestration remains in one owner.
- `storage/room/RoomMigrations.kt` — **534 LOC** — contiguous migration owner.

Not all of these should be split. `RoomMigrations` is a good example of a large file with a coherent chronological owner. The important finding is that at least four exemptions literally encode “temporary/v1/pending extraction” debt and there is no mandatory roadmap task that makes those reasons disappear before freeze.

---

# 4. High-confidence findings

## S1 / proposed X19 — Content Mapping candidate filtering is an N+1 query + repeated identity-resolution path

**Priority:** P1 structural performance defect  
**Confidence:** high

Production path:

`library/.../ContentMappingService.kt:79-102`

- `withoutRejectedOrLinked(storyId)` obtains linked mappings through `repository.observe(storyId).first()`.
- `withoutRejected(storyId)` then iterates every candidate and calls `repository.isRejected(...)` individually.

Room implementation:

`storage/room/.../RoomContentMappingRepository.kt:80-85`

- each `isRejected(...)` first calls `identity.resolve(storyId)`;
- then performs one DAO `SELECT EXISTS`.
- in the current Room resolver, `resolve()` itself materializes the full redirect table; Wave 1/A6 is already intended to replace that global identity lookup with bounded point/frontier logic, but even after A6 the K repeated identity resolutions + K rejection queries remain.

Yet `LibraryDao` already exposes:

- `mappingsForStory(storyId)` at lines 66–67;
- `rejectionsForStory(storyId)` at lines 69–73.

For `K` candidate mappings and `R` historical redirects, current filtering can therefore perform approximately:

```text
1 reactive subscription/snapshot for linked mappings
+ K Story identity resolutions (currently each can materialize R redirects)
+ K rejection EXISTS queries
```

So before A6 lands the physical shape can approach `O(K·R + K SQL)`; after A6 bounds identity resolution, the remaining batch-via-point shape is still roughly `K` identity lookups + `K` rejection queries.

The semantic question is “filter this candidate batch against the current mapping/rejection snapshot for one Story”. The physical work should be one resolved Story identity + one bounded mapping snapshot + one bounded rejection snapshot, followed by in-memory set membership.

**Required contract:**

- add/use a one-shot bounded mapping snapshot for one Story rather than `observe(...).first()` when reactive invalidation is not required;
- resolve the Story identity once;
- bulk-read rejections for that resolved Story once;
- key the snapshot by `(pluginId, sourceStoryId, policyVersion)`;
- candidate filtering becomes O(K) CPU after O(1) bounded DB reads rather than K point DB calls.

**Why this is new:** no `ContentMapping`/rejection path appears in the current audit/roadmap root-cause closure matrix. Taxonomically, there are two valid choices: add this as X19, or deliberately broaden X7 from “Catalog reconciliation batch-via-point” into a cross-domain **batch work through point APIs** family and add Library Mapping as a mandatory X7 call site. The current v3 does neither, so the important issue is missing ownership rather than the exact ID number.

---

## S2 — Confirmed production-source orphans / zero-caller surfaces exist

**Priority:** P1 hygiene  
**Confidence:** high for the listed symbols; framework/reflection false positives excluded

High-confidence zero-reference production symbols in the supplied source tree include:

- `feature/catalog/.../components/StoryShelf.kt:21` — `StoryShelf` declaration only; no production/test caller found.
- `feature/settings/.../SettingsViewModel.kt:118` — `clearErrors()` declaration only.
- `app/.../ChapterSyncCursorCodec.kt:41` — `decodeResult()` declaration only; callers use `decode()` directly.
- `reader/.../ReaderAssetFailure.kt:13` — `AssetNotFound` declared but no source/test reference.
- `reader/engine/.../SourceHealth.kt:241` — `TransportFailure.RateLimited` declared but no source/test reference.
- `reader/engine/.../SourceHealth.kt:272` — `ContentFailure.CorruptDocument` declared but no source/test reference.
- `storage/room/.../CanonicalCatalogDao.kt:283` — `upsertMergeReversalEvent()` declaration only; `insertMergeReversalEvent()` is the live adjacent write path.

These are small individually, but they are precisely the class of debt a final “root causes closed” audit will not detect.

**Required contract:** every orphan is either removed or gets a written runtime/API-extension reason. No “maybe useful later” production surface remains by default.

---

## S3 — Entire classes live in `src/main` but are test-only in the current product call graph

**Priority:** P1 hygiene/API surface  
**Confidence:** high for current source reachability; product-feature intent still needs a final owner decision before deletion

### `downloads/cache/CacheService.kt`

- Production class has no production caller.
- Only `CacheServiceTest` constructs it.
- Older RICC planning documentation explicitly said the old `CacheService` bypass should be retired from automatic document writes.

This is stronger than “unused helper”: it looks like a superseded runtime service that survived a previous architecture cutover.

### `catalog/home/CatalogHomeQuery.kt`

- Production class has no production caller.
- Only `CatalogHomeQueryTest` constructs it.
- Live Discover ranking uses `AggregateRanking` through `DiscoverSemanticContent` instead.

### `plugins/runtime/update/PluginRollbackService.kt`

- No production wiring/caller found.
- Only `PluginRollbackServiceTest` invokes it.

Rollback may be a legitimate future capability, so unlike `CacheService` this should be classified as **“unreachable product capability: wire it or remove it”**, not blindly deleted.

**Required contract:** no implementation class remains in `src/main` solely to keep a unit test alive. If a capability is intentionally public/future-facing, record that explicitly and test its API contract; otherwise retire it.

---

## S4 — Test infrastructure is shipped as production API

**Priority:** P1 source-set hygiene  
**Confidence:** high

`core/common/src/main/.../Clock.kt` contains:

- `FakeClock`
- `FakeMonotonicClock`
- `advanceBy`
- `advanceByNanos`

The fakes are heavily used by tests (well over 100 references for `FakeClock`) and have no runtime consumer.

Other test-oriented production helpers include:

- `reader/.../ReaderExecutionScheduler.forTest()`
- `feature/catalog/.../SearchViewModel.createForTest()`
- `catalog/.../CanonicalBootstrapUseCase.prewarm()` — current references are tests only
- `reader/.../ReaderAssetWorkingSetPolicy.memoryPrewarmBehind()` — current references are tests only

Not every test seam should disappear: internal constructors and small interfaces are often the cleanest way to test production state machines. The problem is specifically **test implementation or test-only convenience behavior in the production source set**.

**Required contract:** move reusable fakes/builders to Gradle `testFixtures` (or the project’s dedicated testing support location); keep production interfaces/seams, not fake implementations.

Additional confirmed test-only/trivial production seams found by production-vs-test reference census:

- `app/.../CanonicalEngineWorker.kt` — `runCanonicalEngineWork()` is exercised by tests but the shipping worker bypasses it; `canonicalRetryWakeWorkName()` is also a test-only one-hop wrapper.
- `app/.../LibraryMappingWorker.kt` — `uniqueWorkName()` exists only for tests and delegates directly to `WorkNames.libraryMapping(...)`.
- `feature/catalog/.../DiscoverSemanticContent.kt` — `projectSemanticDiscoverContent()` and `discoverCanonicalBootstrapStoryIds()` are production-source helpers with no production caller; only tests retain them.
- `reader/.../ReaderAssetCoordinator.kt` — `sessionSnapshot()` is a test-introspection surface only.
- `reader/.../ReaderRouteSession.kt` — the no-argument `hardInvalidate()` overload is test-only; production uses the current-context guarded form.

These do **not** all require deletion. State-machine introspection may be better replaced by black-box test observation, while one-hop worker naming helpers can usually disappear outright. The finding is that the current hygiene gate has no systematic way to distinguish these from real runtime API.

---

## S5 — Semantic entity↔domain mapping logic is duplicated across Room merge and normal repository paths

**Priority:** P1 correctness-maintainability  
**Confidence:** high

Exact/near-exact mapping logic is repeated in independent production files, including:

- `StoryCanonicalStateEntity.toPreference()`
  - `RoomCanonicalCatalogRepository.kt:368-380`
  - `RoomStoryMergeReaders.kt:90-102`

- `ChapterReleaseEntity.toModel()`
  - `RoomChapterRepository.kt:289-300`
  - `RoomStoryMergeReaders.kt:153-164`

- `ChapterSyncStateEntity.toModel()`
  - `RoomChapterRepository.kt:340-349`
  - `RoomStoryMergeReaders.kt:172-181`

- parsed Chapter-label reconstruction is repeated in the same two paths.

- `ContentMappingEntity` construction/translation is spread between the normal mapping repository and merge/reversal writers.

The risk is not primarily LOC. Merge/reversal code is a correctness-sensitive second path. When a schema/domain field changes, one mapper can drift while another continues to compile.

**Required contract:** define package-internal, domain-specific Room mapping functions with one ownership location per entity family. Do **not** create a generic reflection/mapper framework. The goal is semantic single ownership, not abstraction for its own sake.

---

## S6 — Known “god-ish” authorities are explicitly deferred but the big-update plan does not close the deferral

**Priority:** P1 architecture/hygiene  
**Confidence:** high as structural debt; extraction shape must be conservative

### ReaderAssetCoordinator — 1159 LOC

Repository structural report flags multiple >50-line methods. The class currently owns a broad combination of session state, viewport/protection planning, delivery/publication, invalidation and cache-facing coordination.

A useful extraction is by **mutable-state ownership / lifecycle**, not by arbitrary line count. For example, candidate units are session registry/state, viewport/protection planning, and publication/invalidation arbitration, while a thin facade can remain if it is the necessary single authority.

### ReaderRouteSession — 683 LOC

Owns semantic commit/refresh, graph/session state, prefetch lifecycle and asset-session integration. Any extraction must preserve a single clear state-machine/lock authority; splitting one lock-protected invariant across several independently mutable services would be a regression.

### ReaderRouteCoordinator — 587 LOC

Current allowlist reason says route execution and selected-release refresh share a v1 authority. After performance waves stabilize routing contracts, selected-release refresh is a candidate for extraction if it can own no duplicated route state.

### DiscoverViewModel — 658 LOC

The allowlist itself says “pending a dedicated extraction plan”. This is the strongest repo-authored evidence that a dedicated post-behavior extraction is already expected.

### AutomaticCacheBudgetCoordinator — 496 LOC / 27 public methods

This file will be changed heavily by X8–X10 work. Splitting it before those invariants settle would create churn. After Wave 5, re-evaluate separation between accounting ledger, write admission/reservation, and pressure/maintenance execution.

### RoomCatalogRepository — 510 LOC

Wave 2 will already reshape Catalog persistence. Post-wave extraction should be limited to stable transaction/read/mapping responsibilities, preserving one transactional authority.

**Required contract:** the final allowlist must not keep explanations like “pending extraction” or “intentionally co-located for v1”. A large cohesive owner may remain, but its final reason must describe a durable invariant, not temporary debt.

---

## S7 — Gradle module DAG is clean, but several same-module package dependency cycles reveal ownership erosion

**Priority:** P1 architecture  
**Confidence:** high for SCC existence; only selected SCCs are promoted as defects

A package-import SCC scan finds eight same-module cycles. Raw SCC count alone is **not** a bug; several are expected artifacts of parent-package facades, Room database ownership, framework composition, or `engine` facade/internal layout.

The strongest actionable cycles are:

### Catalog — 9-package SCC

Packages include canonical/details/diagnostics/fusion/home/metadata/orchestration/reconciliation/repository.

Concrete ownership inversion:

- `CatalogMetadataCoordinator` imports `CatalogDetailsLoader` / details result types;
- `CatalogDetailsLoader` imports metadata failure/key/level types;
- `CatalogFullMetadataFallbackService` in `details` depends back on `CatalogMetadataAccess` / `CatalogMetadataCoordinator`.

Another bidirectional seam exists between orchestration and reconciliation:

- orchestration imports reconciliation runners/cases;
- reconciliation imports `CanonicalEngineWorkRepository`, `CanonicalEngineWorkType`, and event-sink concepts from orchestration.

This means package names no longer describe a one-way dependency direction.

### Reader — `reader.assets ↔ reader.routing`

Assets import routing-owned `ReaderSessionId`, `ReaderNetworkFactsPort`, `ReaderNetworkState`; routing imports asset arbiter/failure/manifest/session types. This is a strong ownership-placement smell because session/network facts are cross-cutting runtime contracts, not naturally owned by one side of a cycle.

Candidate repair: move the truly shared session/network/fetch contracts to a neutral reader runtime/session contract package while keeping assets and routing as one-way consumers. Do not merge both packages into one giant “reader manager”.

### Downloads — 4-package SCC

`downloads.assets` depends on cache/reconciliation concepts, while cache/reconciliation depend back on asset blob/metadata types. After Wave 5, distinguish physical asset storage primitives from cache policy/admission and reconciliation policy so direction becomes explicit.

### Plugin runtime — 9-package SCC

Runtime root, capabilities, execution, install, persistence, and update participate in one SCC. Some root-package result types explain part of this, but install↔update and runtime-root↔execution/capability edges deserve a post-Wave-4 ownership pass.

### Self-review exclusions

Do not promote these merely because SCC detection sees them:

- `storage.room` root ↔ DAO subpackages: `OpenStoryDatabase` naturally imports DAOs while repositories import the database owner.
- `reader.engine` ↔ `reader.engine.internal`: likely deliberate facade/internal implementation pattern.
- app root/DI/framework packages: parent/root composition frequently creates import SCC artifacts.
- plugin API `manifest ↔ protocol`: review, but do not break unless semantic ownership is actually unclear.

**Required contract:** selected domain package cycles have an explicit target DAG. No “shared” package may become a dumping ground; only move concepts that are genuinely neutral contracts.

---

## S8 — One-shot service operations unnecessarily route through reactive `Flow.first()` APIs

**Priority:** P1/P2 depending path frequency  
**Confidence:** high as unnecessary API shape; performance severity varies

Confirmed examples:

- `ContentMappingService.withoutRejectedOrLinked()` → `repository.observe(storyId).first()`.
- `ChapterSyncService.syncMappings()` → `mappings.observe(storyId).first()`.
- `CatalogStoryProjectionRepository.find()` default → `observe().first().firstOrNull` (already addressed by the v3 bounded production override plan, so this is **not** a new root cause).

For a one-shot command, `observe(...).first()` creates a reactive subscription solely to emulate a snapshot read. Besides allocation/subscription overhead, it makes it easier for a default implementation to accidentally become global and obscures the storage complexity contract.

**Required contract:** where the semantic operation is a command-time snapshot, expose a bounded suspend snapshot API. Reactive APIs remain for actual ongoing observation.

Do not ban all `Flow.first()` globally: DataStore settings reads and intentional “await first available state” pipelines can be correct.

---

## S9 — Duplicate text normalization/Jaccard similarity algorithms exist in Catalog and Library matching

**Priority:** P2 maintainability / future performance consistency  
**Confidence:** high duplication, medium consolidation recommendation

`catalog/engine/.../TitleNormalizer.kt` and `library/.../ContentStoryMatcher.kt` both implement:

- NFKC normalization;
- `Locale.ROOT` lowercasing;
- non-letter/number replacement;
- whitespace collapse;
- token-set Jaccard similarity.

They are currently nearly the same concept implemented separately.

Blind DRY is unsafe because Catalog reconciliation and Library content mapping may intentionally evolve different matching semantics. Two acceptable outcomes exist:

1. extract a tiny domain-neutral normalized-text similarity primitive **only if characterization tests prove both domains intend identical normalization/math**; or
2. keep separate implementations but explicitly document/domain-test the intended divergence.

The current accidental near-copy is the bad state because future fixes can land in only one matcher.

---

## S10 — Small duplicated UI observation helpers indicate missing local UI-state primitive ownership

**Priority:** P2  
**Confidence:** high duplication; low runtime importance

`ObservationState.availableValueOrNull()` appears in both Downloads and Updates ViewModels, with related `issueOrUnavailable` helpers duplicated across feature screens.

This should not become a large generic UI framework. A small package-local extension file in the existing catalog UI state-contract package is enough **if** semantics are truly identical.

This is cleanup, not a performance root cause.

---

## S11 — Low-level AtomicFile blob-store code has exact duplicated file-sync/write mechanics

**Priority:** P2  
**Confidence:** medium

`AtomicFileReaderAssetBlobStore` and `AtomicFileChapterBlobStore` contain identical/near-identical low-level output synchronization sections.

Consolidation is only useful if both stores have exactly the same atomicity/fsync/error-cleanup contract. If Reader asset and Chapter blob durability semantics differ, keep the duplicated-looking code and test each contract separately.

Do not generalize into a broad storage framework merely to remove 10–15 lines.

---

## S12 — Structural quality checks report breadth, but current acceptance is “report/allowlist”, not “debt budget shrinks”

**Priority:** P1 process/gate  
**Confidence:** high

Current tooling already detects:

- long files/functions;
- wide constructors;
- broad public method surfaces;
- import-heavy files;
- broad names;
- allowlisted large files.

What is missing from the big-update acceptance contract is a **delta rule**.

A big structural update should not need “zero findings”, but it should prove:

- no new temporary/v1 allowlist exemption;
- temporary existing exemptions shrink or acquire a durable justification;
- no new production-only orphan introduced;
- no new test fake/helper in `src/main`;
- selected package SCC count does not regress;
- duplicate semantic mapper inventory does not grow;
- broad APIs touched by a wave become narrower or are explicitly justified.

---

## S13 — `source-hygiene-policy-test.sh` can pass falsely when its target file is missing

**Priority:** P1 governance/test defect  
**Confidence:** reproduced

The current source-hygiene script assigns paths for retired Catalog matching files that no longer exist, then uses negative checks such as:

```bash
! grep -q 'CatalogMatchExplanation' "$catalog_match_result" || fail ...
```

For a missing file, `grep` exits non-zero, so the leading `!` converts **“file not found”** into success. Running the supplied script reproduces:

```text
status=0
Source hygiene policy verified.
grep: .../catalog/matching/MatchResult.kt: No such file or directory
grep: .../catalog/matching/StoryMatcher.kt: No such file or directory
...
```

This is not cosmetic. A stale negative assertion can silently stop checking the architecture while CI remains green. It also explains how newer test-only wrappers/orphans can appear despite a historical policy whose stated purpose is to prevent exactly that class of regression.

**Required contract:**

- every file-targeted hygiene assertion first proves the target exists, unless file absence is itself the intended contract;
- use a helper such as `require_file` + `forbid_pattern` so missing-path semantics are explicit;
- add a negative fixture test proving the hygiene script fails when an expected policy target disappears;
- convert symbol-specific retired-code checks into a small systematic reachability/source-set policy where practical, rather than accumulating stale one-off greps forever.

---

## S14 — The current source-layout gate is already red on the supplied tree

**Priority:** P1 governance debt  
**Confidence:** reproduced

Running `scripts/verify-source-layout.sh` against the supplied repository reports all current >300-line production candidates and then fails on:

```text
Test Kotlin source exceeds 750 lines:
catalog/src/test/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewServiceTest.kt (751)
```

So the repository is not merely carrying reported structural debt; the current tree already exceeds one of its own hard layout ceilings. This does not affect runtime performance directly, but it matters for the big update because a freeze plan that assumes architecture/source gates are green would begin from a false baseline.

**Required contract:** baseline Wave/Gate S0 must first make structural verification deterministically green, then ratchet from that known-good state. Do not add a new allowlist row solely to silence a one-line overage; either extract the test fixture coherently or justify a durable test-suite exception with a removal criterion.

---

# 5. Findings that are already covered by v3 and must NOT be double-counted

Several apparent structural problems are real but already have an owner in the current performance plan:

- Search/Refresh duplicate `ingestContext()` / global-ish reconciliation setup → A1/A2 / Wave 2.
- `CatalogStoryProjectionRepository.find()` default global observation → A5 / Wave 1 production point override + architecture guard.
- bounded `observeForStories()` defaults implemented through global `observeAll()` → A7/A8/X2 depending repository; v3 already requires production bounded overrides/fail-closed guards.
- Story membership/progress via global Library/progress observers → A8 / Wave 7.
- automatic-cache global candidate/accounting paths → X8–X10 / Wave 5.
- canonical batch work through point APIs → X7 / Wave 3.
- plugin all-policy/script payload materialization → X16 / Wave 4.

The structural cleanup wave should remove residual superseded paths **after** these owners land, not create parallel fixes beforehand.

---

# 6. Self-review: false positives / abstractions that should be kept unless stronger evidence appears

The audit intentionally rejects “fewer classes is always cleaner”. The following patterns looked like wrappers at first glance but have valid architectural roles.

## Keep Reader execution delegates/test seams

`ReaderRouteExecutionDelegate`, `ReaderSelectedReleaseRefreshDelegate`, and related fun interfaces let `ReaderRouteSession` be tested without constructing the entire coordinator graph. They are not automatically useless wrappers.

## Keep port projections on a shared concrete store

`ReaderDocumentStore` and `ReaderCacheFactsPort` can be separate views implemented by `DownloadAwareReaderDocumentStore`. This is a legitimate dependency-inversion surface and prevents consumers from depending on the full storage object.

## Do not split `RoomMigrations` merely for LOC

One contiguous migration registration owner is often safer than distributing schema history across arbitrary files. Its allowlist reason is durable/cohesive, unlike “pending extraction” and “v1” reasons.

## Do not count Hilt/Room/framework reachability as dead code

Zero direct textual references are expected for:

- Hilt modules/provider methods/entry points;
- Room converter/DAO generated usage patterns;
- Android application/activity/worker callbacks;
- macrobenchmark/JUnit framework entry points;
- build-logic Gradle plugin/task classes.

A dead-code gate must understand or explicitly allow these mechanisms.

## Do not DRY tiny UI/layout similarities by default

Ten lines of similar Compose layout are cheaper than a generic component whose parameters hide visual intent. Consolidate only repeated semantics, not merely repeated syntax.

## Do not weaken security/integrity boundaries for “less code”

Reader checksum/security invalidation, plugin capability/auth validation, one secure session snapshot after X18, and explicit upgrade/migration paths remain correctness boundaries even when they add types or calls.

---

# 7. Recommended mandatory pre-freeze structural wave

Recommended placement:

```text
Waves 0–7 behavior/performance architecture
        ↓
Structural Simplification / Repository Hygiene Gate
        ↓
Wave 8 risk acceptance + upgrade compatibility retirement + final freeze
```

This ordering prevents cleanup from fighting active performance redesigns and gives dead/superseded-path analysis the final post-migration call graph.

## Task S0 — Freeze a structural baseline and reachability policy

- capture structural-review candidate inventory;
- define framework/reflection/codegen exclusions;
- record current selected package SCCs;
- record current orphan/test-only/clone inventory;
- fail new temporary source-layout allowlist entries.

## Task S1 — Close proposed X19 Content Mapping N+1

- add one-shot bounded mapping/rejection snapshot contract;
- resolve Story once;
- bulk filter candidate set;
- add query-count/scaling regression test for `K` candidates.

## Task S2 — Retire dead and unreachable runtime paths

Start with high-confidence candidates:

- `StoryShelf`;
- `SettingsViewModel.clearErrors`;
- `ChapterSyncCursorCodec.decodeResult`;
- unused Reader/source-health failure variants after checking serialized/API compatibility;
- unused DAO `upsertMergeReversalEvent`;
- `CacheService` if final automatic-cache call graph confirms zero caller;
- `CatalogHomeQuery` if Discover remains the ranking owner;
- `PluginRollbackService`: either wire a real product owner or remove it.

Every deletion requires source search + affected tests. Do not remove migration/schema/API compatibility solely because current runtime caller count is zero.

## Task S3 — Move test-only production surfaces into test fixtures

- `FakeClock` / `FakeMonotonicClock`;
- `forTest()` / `createForTest()` conveniences where a fixture/builder can own them;
- test-only behavior methods (`prewarm`, `memoryPrewarmBehind`) if no production contract exists after final waves.

Preserve small production interfaces/constructors that are valid test seams.

## Task S4 — Canonicalize semantic mappers and selected duplicate pure algorithms

- centralize Room entity/domain mappers by entity family;
- characterize Catalog vs Library text normalization similarity before deciding share-vs-explicit-fork;
- centralize tiny ObservationState semantics only within the feature boundary;
- leave low-value UI syntax clones alone.

## Task S5 — Repair selected package ownership cycles

Priority order:

1. Reader `assets ↔ routing` shared session/network contracts;
2. Catalog metadata/details and orchestration/reconciliation direction;
3. Downloads asset/cache/reconcile layering after Wave 5;
4. Plugin runtime install/update/execution ownership after Wave 4.

Do not target zero SCCs globally; target only cycles that cross semantic ownership.

## Task S6 — Extract temporary/v1 oversized authorities after their behavior stabilizes

- DiscoverViewModel;
- ReaderAssetCoordinator;
- ReaderRouteSession;
- ReaderRouteCoordinator;
- AutomaticCacheBudgetCoordinator;
- re-evaluate RoomCatalogRepository after Wave 2.

Extraction acceptance is based on state ownership and dependency direction, not file length alone.

## Task S7 — Add permanent structural gates

Suggested checks:

- production-orphan/reachability census with framework allowlist;
- no `Fake*` test implementation in production source sets unless explicitly runtime-facing;
- no `forTest`/`createForTest` production API without an allowlisted reason;
- semantic mapper duplicate baseline;
- selected package SCC/layer-direction check;
- one-shot-via-reactive API review (`observe(...).first()` in services/commands);
- source-layout allowlist cannot gain `pending`, `temporary`, or `v1` debt at final freeze;
- file-targeted hygiene rules fail closed when an expected file disappears;
- structural-review candidate count is tracked as a budget/trend rather than ignored after reporting.

---

# 8. Proposed acceptance gate for the “big update”

The performance big update should not be called structurally frozen until all of the following are true:

1. Existing 33 root-cause criteria are closed **plus X19 is either added/closed or disproved with query-count evidence**.
2. High-confidence orphan production symbols have zero unexplained entries.
3. Test implementations/fakes are not shipped in `src/main` merely for tests.
4. Superseded runtime services have zero production caller and are retired; intentionally dormant product capabilities have an explicit owner/reason.
5. Room merge/reversal and normal repository paths share one semantic mapper owner per entity family.
6. Selected Catalog/Reader/Downloads/Plugin package ownership cycles have an explicit one-way target or an explicit documented reason to remain.
7. Temporary/v1 `source-layout-allowlist` reasons have been removed; durable cohesive exceptions may remain.
8. No service command uses a reactive/global convenience path when an equivalent bounded one-shot query is required by its semantics.
9. Structural-review metrics do not regress from the pre-wave baseline, and touched hotspots show a measurable reduction in breadth/debt rather than merely moving code between files.
10. Source-hygiene/layout gates fail closed: missing policy targets cannot pass a negative grep, and the structural baseline is green before ratcheting.
11. Final Wave-8 source-level performance sweep runs *after* this cleanup, so benchmarks/profile generation measure the architecture that will actually ship.

---

# 9. Priority order

## Must add to the big-update plan

1. **X19 Content Mapping N+1**.
2. Dead/unreachable runtime code retirement.
3. Test-only code removal from production source sets.
4. Semantic Room mapper consolidation.
5. Temporary/v1 allowlist debt closure.
6. Selected package-cycle repair.
7. Structural regression gates.

## Should do, but only after characterization

- Catalog/Library normalization primitive sharing;
- low-level AtomicFile helper consolidation;
- small ObservationState helper consolidation;
- broad constructor/public API reduction where it actually improves ownership.

## Explicitly do not optimize just for aesthetics

- RoomMigrations line count;
- Reader/test seam fun interfaces;
- DI ports implemented by one shared concrete store;
- tiny Compose syntax duplication;
- security/integrity/migration boundaries;
- framework-generated/referenced entry points.

---

# 10. Final assessment

The supplied v3 is **not wrong**. It is a strong performance/root-cause program. The problem is scope: it can finish with the app faster while leaving a substantial amount of structural debt untouched.

The strongest evidence is internal to the repository itself:

- 316 structural-review candidates;
- six production large-file allowlist entries, several explicitly marked “pending extraction” or “v1”;
- confirmed zero-caller/test-only production surfaces;
- duplicated correctness-sensitive Room mappers;
- selected package dependency cycles despite a clean Gradle module DAG;
- and a newly confirmed Content Mapping N+1 path outside the current root-cause matrix.

For a true “big update”, performance repair and structural simplification should be treated as two different passes with different acceptance criteria. First stabilize behavior/data-scope/locking via Waves 0–7; then simplify the resulting architecture; only then do risk acceptance, compatibility retirement, final benchmarks, baseline profile regeneration, and freeze.
