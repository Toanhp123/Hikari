# Hikari V2 Step 2 — Discover + Story Detail Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Repository `AGENTS.md` is stricter than generic skill defaults: execute **one canonical Task N only**, update checkpoint/roadmap, then stop and hand control back. Do not continue into Task N+1 without a new explicit user instruction.

**Goal:** Admit Hikari V2's first real product capability so a returning launch reaches a bounded, persistence-backed multi-section Discover surface for Manga and Light Novel, a Story opens a keyed metadata-only Story Detail surface, covers retain bounded visual continuity, and the Step 1 startup/ownership constitution remains mechanically enforced.

**Architecture:** Keep `:app` as a thin launch-state/destination shell and add exactly four production modules: pure-JVM `:catalog:domain`, Room-owned `:catalog:storage`, demand-owned `:catalog:runtime`, and Compose-owned `:feature:catalog`. Discover is a bounded materialized read model with durable `Absent` versus `Published(empty)` semantics; Story Detail is a keyed bounded read/write model; acquisition is single-owner foreground work; image loading is a capability-private bounded subsystem; deterministic debug/benchmark sources and the final MangaUpdates harness remain non-release. The quarantined V1 `:catalog:model` and `:catalog:engine` stay outside the Step 2 production graph.

**Tech Stack:** Existing Step 1 baseline: Kotlin 2.4.10, JDK 17, AGP 9.3.0, Android minSdk 26 / targetSdk 37, Compose BOM 2026.06.00, coroutines 1.11.0, AndroidX Macrobenchmark/Baseline Profile 1.5.0-beta01. Re-admit only the dependency versions required by the Step 2 production shape and already proven in the supplied V1 tree: KSP 2.3.9, Room 2.8.4, Coil 3.5.0, Lifecycle 2.11.0; JavaScriptEngine 1.1.0 is `androidTest`-only for the final plugin gate. The V1 tree's OkHttp 5.3.0 is reference evidence only and is **not** admitted to Step 2 production/test dependencies.

**Spec:** `docs/superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md`

**Baseline repository:** branch `v2/foundation-clean-boot`, accepted Step 1 runtime/source SHA `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`.

---

## Repository audit basis

This plan was derived by reading the R2.1 design and comparing it against both supplied repositories.

### Step 1 facts that constrain implementation

- `settings.gradle.kts` currently includes exactly `:app`, `:core:common`, `:catalog:model`, `:catalog:engine`, `:reader:engine`, `:plugins:api`, and `:benchmark`; `:catalog:model` and `:catalog:engine` are quarantine/reference modules, not admitted runtime.
- `config/architecture/module-boundaries.json` currently gives `:app` **zero** production project dependencies and fail-closes exact project edges/imports.
- `config/architecture/v2-foundation-policy.json` currently rejects Room, Coil, OkHttp, Catalog packages, `implementation(project(...))`, network permissions, and hidden startup surfaces from the app shell.
- `FoundationConventionPlugin` currently verifies only `:app`, so Step 2 must extend structural/package-SCC verification to all newly admitted production modules rather than merely loosening the app policy.
- `MainActivity` currently calls `HikariStartupApp()` only; `StartupGate` owns `Unknown -> FirstRun/Ready`; `HomeShell` is the exact static destination to replace with a narrow `:feature:catalog` entry point for `Ready`.
- Step 1 benchmark state preparation writes only `initial_setup_completed=true`; Step 2 benchmark setup must additionally materialize deterministic Catalog state through the real importer, not direct DAO inserts.
- Step 1 has **no Robolectric dependency or JVM Android runtime**. Real Room transactions/DAO invalidation, Compose UI rendering, Coil/Android cache behavior, and Android bitmap preflight therefore belong to `androidTest`/connected evidence unless Step 2 explicitly admits a new host-test framework (this plan does not).
- Step 1 accepted startup medians on Redmi Note 9S/API 35 are `394.210469 ms` fresh and `412.547813 ms` returning under `CompilationMode.Partial(BaselineProfileMode.Require)`.

### V1 evidence to preserve or reject

Preserve as behavior/evidence:

- Discover semantic order and caps: Popular 5, Latest Updates 9, Top Rated 5.
- Source-owned Popular order, timestamp-based Latest, score-scale-preserving Top Rated.
- Retained-content-on-refresh-failure semantics.
- Stable Story identity/list keys and source-preserving metadata.
- One vertical scroll owner and geometry-shaped loading.
- Story Detail enrichment does not mutate Home/Discover membership.
- Deterministic benchmark artwork/profile workflows and source-scoped plugin host/security ideas.

Reject as migration targets:

- V1 `DiscoverViewModel`'s multi-stream `homeObservation + bootstrap + settlement + projection` readiness graph and canonical settlement on the Discover critical path.
- V1 Story ViewModel's canonical + Library + progress + reconciliation dependency fan-in for a metadata screen.
- Global/broad Catalog reads, per-card/point pseudo-batch writes, canonical fusion/reconciliation, global history maintenance, duplicate refresh owners, WorkManager continuation, Hilt/global composition, and V1 Room schema/migrations.
- V1 `RoomConventionPlugin`; Step 2 applies Room/KSP directly only in `:catalog:storage`.
- V1 production plugin runtime. The final gate copies/adapts only the minimum executor/bridge support into `:feature:catalog/src/androidTest`.

### Negative performance/structural evidence converted into permanent gates

The supplied whole-app and structural audits specifically identified A1-A8, L1/L3-L8, D1, X1-X7 and package-SCC debt, including a 658-line V1 `DiscoverViewModel`. Step 2 therefore treats the following as architecture tests, not conventions by prose:

- no global canonical read/reconciliation/fusion path reachable from Discover/Story;
- no per-card DAO/detail calls on Discover;
- no independently settling publication/card streams;
- no history-wide retention scan/sort/count on refresh;
- no application-lifetime Catalog observer/work owner;
- no package SCC in any Step 2 production module;
- no seed/plugin-harness leakage into release;
- no pre-demand Room/image/network initialization;
- no `okhttp3`, raw `java.net` HTTP ownership, or concrete network-client imports from `feature.catalog.discover`, `feature.catalog.story`, or the feature root UI/router packages; only `feature.assets` may own the concrete remote transport.


### Package-DAG constraint for the four new modules

The zero-package-SCC ratchet is not left for implementation to discover accidentally. Keep the production package direction acyclic:

```text
catalog.domain.failure + catalog.domain.limits are lowest-level leaf packages and import no higher domain package
catalog.domain.identity + catalog.domain.model -> failure/limits only as needed
catalog.domain.asset -> failure/limits/identity/model only
catalog.domain.source -> failure/limits/identity/model/asset
catalog.domain.read -> failure/limits/identity/model/asset/source
catalog.domain.validation -> failure/limits/identity/model/asset/source/read; it never imports write
catalog.domain.write -> failure/limits/identity/model/asset/source/read/validation as required by bulk commands
(all domain packages may use reviewed :core:common primitives; none imports runtime/storage/feature)

catalog.storage (root facade/factory/database)
    -> storage.discover, storage.story, storage.retention
storage.discover/story/retention
    -> domain types only; never import catalog.storage root facade/factory

catalog.runtime (root capability facade/factory)
    -> runtime.discover, runtime.story, runtime.acquisition, runtime.retention,
       runtime.source, runtime.execution, runtime.concurrency, runtime.trace
runtime.discover/story/acquisition/retention
    -> runtime.source/execution/concurrency as needed + domain/storage ports;
       never import catalog.runtime root facade/factory

catalog.feature (root composition/router)
    -> feature.discover, feature.story, feature.assets
feature.discover/story
    -> runtime/domain + feature.state + feature.assets where needed; never import feature root composition/router
feature.state
    -> domain failure types only; never import discover/story/assets/root composition
feature.assets
    -> domain/runtime policy ports; never import discover/story/state/root UI packages
```

In particular, shared runtime types such as `CatalogSourceBinding` and `CatalogExecutionDispatchers` live in leaf/shared packages (`runtime.source`, `runtime.execution`) rather than the runtime root, because putting them in the root would create `runtime <-> runtime.acquisition/discover/story` package cycles once the root facade imports those children.

---

## Global constraints

- Release identity remains `app.openstory`; JDK is exactly 17; minSdk 26; targetSdk 37.
- Step 2 explicitly enables both `MANGA` and `LIGHT_NOVEL`. Update the authoritative product-design amendment in the same admission change so no active text still says Light Novel is disabled.
- Step 2 is a production-shaped internal/product vertical slice, **not** a ship-ready production remote Catalog source.
- Add exactly four production modules: `:catalog:domain`, `:catalog:storage`, `:catalog:runtime`, `:feature:catalog`.
- Production graph is exact:

```text
:app -> :feature:catalog
:feature:catalog -> :catalog:domain, :catalog:runtime
:catalog:runtime -> :catalog:domain, :catalog:storage
:catalog:storage -> :catalog:domain, :core:common
:catalog:domain -> :core:common
```

- `:catalog:model` and `:catalog:engine` stay quarantine/reference and gain no Step 2 production edge.
- `:plugins:api` is allowed only as `androidTestImplementation` of `:feature:catalog` for Task 15; it is not a Step 2 production dependency.
- Preserve the existing exact test-edge authority while adding Step 2: `:app` keeps test dependency `:benchmark`; `:benchmark` keeps test dependency `:app`; new Step 2 modules start with zero project test dependencies, and only Task 15 changes `:feature:catalog` test dependencies to `[":plugins:api"]`.
- `:catalog:domain` remains pure JVM and Android/Room/Compose/Coil/OkHttp-free. Because its public API exposes `StoryId` from `:core:common`, it applies `java-library` in addition to `openstory.kotlin.jvm` and exposes `api(project(":core:common"))`; because public read ports expose `Flow`, it also uses `api(libs.kotlinx.coroutines.core)` rather than hiding Coroutines behind `implementation`. Later modules must not add a second direct `:core:common` edge merely to make transitive compilation happen.
- Room runtime/compiler/KSP exist only in `:catalog:storage`; do not restore `openstory.room` or a generic Room convention plugin.
- Coil concrete image implementation exists only in `:feature:catalog`. Step 2 production ships **no concrete HTTP/network transport at all**: release/main has no remote Catalog source, no `INTERNET`, no OkHttp dependency, and no Coil network module. `RemoteCoverTransport` is a narrow injected port exercised by deterministic test/integration transports; a concrete production HTTP adapter is admitted only with a later production remote-source capability.
- `:app` imports only the narrow feature entry point, never runtime/storage/image/network implementation packages.
- Main/release manifest does not add `INTERNET` solely for Step 2. Any test permission belongs to `androidTest`/integration wiring.
- No Catalog/Room/image/acquisition work begins from `Application.onCreate`, `MainActivity.onCreate`, AndroidX Startup, provider/service/receiver, or process-wide locator.
- Catalog activation starts only after `AppLaunchState.Ready` selects the Catalog destination. First application-owned frame remains independent of Catalog initialization.
- Discover persistence is `Absent | Published(generation, provenance, cards)`; `Published(empty)` is durable data and never automatic-bootstrap authority.
- Only `Absent + admitted source` may auto-bootstrap, with exactly one active foreground single-flight per `(CatalogSourceKey, mediaType)`.
- Discover selected-media cardinality is at most 19 memberships (5/9/5); both current persisted scopes together at most 38 memberships under the initial policy.
- Story identity is exactly `source-story:v1:<64 lowercase hex>` from the R2.1 domain-separated, length-prefixed exact UTF-8 SHA-256 derivation. Java/Kotlin malformed UTF-16 (for example an unpaired surrogate) is rejected before encoding; no replacement-character UTF-8 encoding is allowed. No metadata normalization, digest truncation, suffix repair, random fallback, or insertion-order resolver.
- `StorySourceRef` is self-consistent: `storyId` must equal `SourceStoryIdV1.derive(SourceStoryKey(catalogSourceKey, sourceStoryId))` at construction/restore/import boundaries. A mismatched triple fails closed instead of choosing one field as authority.
- Failure authority is typed and framework-free. `:catalog:domain` owns `CatalogFailure`; storage/runtime/image boundaries may transport it through `CatalogFailureException`, but Room/SQLite/Coil/JavaScript/HTTP exception classes never escape into feature state. **Caller/session cancellation** is always rethrown unchanged before any failure mapping. Operation-owned deadlines must not rely on catching `TimeoutCancellationException` as a generic `CancellationException`: implement them with `withTimeoutOrNull`/an equivalent dedicated deadline result so a controlled timeout maps to the typed operation failure while external cancellation still propagates. UI maps typed failures to a small `CatalogIssueUi` kind/retryability pair and never renders raw exception messages, raw URLs, host-policy internals, or plugin payload text as an error message.
- Input ceilings include every exact R2.1 maximum: source key 128 UTF-8 bytes; sourceStoryId 512 UTF-8 bytes; title 1,024 Unicode scalar values; description 64 KiB UTF-8; authors 32 x 512 scalars; artists 32 x 512; genres 64 x 256; locator text 4,096 chars; section caps 5/9/5. The plan additionally freezes bounded scalar/control fields that R2.1 leaves implementation-shaped: sourceVersion <=256 UTF-8 bytes; publication-status summary/detail <=512 Unicode scalars each; language <=128 Unicode scalars; local logical asset ID <=512 UTF-8 bytes; local asset version <=128 UTF-8 bytes; reviewed stable artwork token <=512 UTF-8 bytes; acquisition sections <=3, one per semantic kind, and acquisition items are already bounded to the section's 5/9/5 cap before they cross the generic `CatalogAcquisitionSource` boundary.
- Discover publication and Story Detail enrichment are atomic Room transactions.
- Retention foreground work is delta-driven and may scale only with bounded previous/current snapshot + removed bounded delta + bounded orphan overflow. `story_orphan_retention` is <=64 after every successful mutation.
- Active Story pin transitions and publication/pruning share one short mutation gate; the gate is never held across source/network/image/UI/user wait. A Story removed while actively pinned is entered into the same <=64 bounded orphan-retention **candidate ledger** before publication commits and is protected from eviction while pinned; this makes process death crash-safe without a global sweep. A no-detail candidate is deleted on normal unpin, or may remain only as a bounded ledger entry until a later bounded mutation evicts it after an abnormal process death.
- One Discover observer is keyed by selected media type; one Story observer is keyed by `StorySourceRef`; no application-lifetime Catalog observer.
- Discover persistence observation is exactly one coherent SQL/Room query per invalidation snapshot. Story Detail observation is capped at **4 SQL statements per coherent snapshot**: one summary/detail projection plus at most one bounded query each for authors, artists, and genres; this upper bound is independent of total Catalog rows.
- Initial image configuration is explicit, not framework-default: decoded memory cache = **32 MiB**, disk cache = **128 MiB**, foreground cover job ceiling = **8**, and **manual offscreen prefetch = 0** for the first implementation. Task 14 may lower cache/concurrency or add at most one viewport of manual prefetch only if benchmark evidence justifies it. If prefetch is introduced, it is lower-priority/cancellable and **may not occupy all image capacity while visible/selected-Story requests wait**; the implementation must reserve/bypass capacity for visible demand and add a starvation regression test. Encoded remote response <=8 MiB, source width/height <=8,192 px, source pixel surface <=32,000,000 pixels.
- Remote artwork is HTTPS-only, source-host allowlisted, redirect-revalidated, finite-timeout, accepted-media-type-only, and bounded before dangerous decode allocation.
- Debug may wire deterministic local source; `benchmarkRelease` and `nonMinifiedRelease` must wire deterministic benchmark source; release/main wires no seed and no remote source; `androidTest` owns the plugin harness.
- Search, Chapters, Reader, Library, Downloads, Mapping, multi-source fusion, production plugin runtime, background refresh/work, Hilt, Navigation 3, and V1 Room migration are out of scope.
- Every task is TDD where behavior/policy is introduced, includes focused self-review, updates the Step 2 checkpoint/current roadmap when its canonical task closes, commits independently, and **stops** before the next task.
- Verification ownership follows repository `AGENTS.md`: the agent runs focused unit/compile checks it can block on; unfiltered module/full regression, `verifyArchitecture`, Detekt/lint, shell verification suites, connected/device tests, Macrobenchmark/profile generation, and physical-device acceptance are **user-owned by default** unless the user explicitly delegates them back. A task with a required user-owned gate stays on the same Task N as `READY FOR USER VERIFICATION`; it is not committed/advanced as accepted until the returned evidence is reviewed. No polling loop is permitted.

---

## Planned file / ownership map

Exact filenames may be adjusted only when an existing repository naming rule makes a name invalid; ownership and one-way package direction may not change silently.

### Build and architecture

- `settings.gradle.kts` — admit exactly four new production modules.
- `gradle/libs.versions.toml` — re-admit reviewed Room/KSP/Coil/Lifecycle/test aliases; no Step 2 HTTP client alias is added.
- `build-logic/src/main/kotlin/app/openstory/build/AndroidLibraryConventionPlugin.kt` — minimal Android library convention, including explicit Step 2 benchmark/profile build types.
- `build-logic/src/main/kotlin/app/openstory/build/FoundationConventionPlugin.kt` — retain app-shell startup verification; aggregate Step 2 structural gates without turning capability modules into startup owners.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/ProductionPackageStructureVerifier.kt` — package-SCC verification for all admitted production modules.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/Step2BuildSurfaceVerifier.kt` — exact framework/module/source-set/variant ownership checks.
- `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyProductionPackageStructureTask.kt`
- `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyStep2BuildSurfaceTask.kt`
- `config/architecture/module-boundaries.json` — exact Step 2 graph and forbidden imports.
- `config/architecture/v2-foundation-policy.json` — app-shell policy evolved from blanket zero-capability bans to exact app-specific bans while keeping startup permission/initializer ratchets.
- `scripts/tests/v2-step2-build-surface-test.sh` — release/benchmark/source-set artifact checks.

### `:catalog:domain`

- `catalog/domain/build.gradle.kts`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/identity/CatalogSourceKey.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/identity/SourceStoryKey.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/identity/SourceStoryIdV1.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/identity/CatalogIdentifierRules.kt` — strict source-key/source-story-id construction/hash-boundary validation; kept in `identity` so `identity` never imports the higher-level `validation` package.
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/failure/CatalogFailure.kt` — framework-free failure taxonomy plus the narrow `CatalogFailureException` transport wrapper; cancellation is never wrapped.
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/model/CatalogMediaType.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/model/CatalogSectionKind.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/model/CatalogSectionCaps.kt` — pure 5/9/5/max-19 constants only; never imports acquisition/read types.
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/model/CatalogRating.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/AcquisitionProvenance.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/CatalogAcquisitionSource.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/DiscoverAcquisition.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/StoryDetailAcquisition.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/DiscoverModels.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/StoryDetailModels.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/DiscoverReadPort.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/StoryDetailReadPort.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/write/CatalogWritePort.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/asset/CoverAssetContracts.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/asset/RemoteHttpsUriV1.kt` — pure-JVM validated/minimally normalized HTTPS locator representation; scheme/host/default port canonicalization is frozen while path/query bytes remain semantically intact.
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/limits/CatalogInputLimits.kt` — low-level constants shared by identity/asset/validation without creating an upward package dependency.
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/validation/CatalogAcquisitionValidator.kt`
- `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/validation/CatalogPublicationValidator.kt` — validates bounded `DiscoverCard` **and Story Detail projection** publication shape at the cross-module mutation boundary; never imports write commands.
- focused unit tests under matching `catalog/domain/src/test/...` packages.

### `:catalog:storage`

- `catalog/storage/build.gradle.kts`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/CatalogDatabase.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/CatalogStorageFactory.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/discover/DiscoverEntities.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/discover/DiscoverDao.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/story/StoryEntities.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/story/StoryDetailDao.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/retention/StoryRetentionEntity.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/retention/StoryRetentionDao.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/RoomCatalogStore.kt`
- `catalog/storage/schemas/...` — new V2 Catalog schema baseline only.
- pure mapper/helper tests may live under `catalog/storage/src/test`; real Room schema/DAO/transaction/invalidation/reopen/query-count behavior lives under `catalog/storage/src/androidTest` because Step 1 has no Robolectric.

### `:catalog:runtime`

- `catalog/runtime/build.gradle.kts`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogCapabilitySession.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogRuntimeFactory.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/execution/CatalogExecutionDispatchers.kt` — injectable CPU/I/O dispatchers; importer validation/hash/projection uses CPU dispatcher and storage/source I/O uses I/O-owned suspending paths, never Main.
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/source/CatalogSourceBinding.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/acquisition/CatalogAcquisitionExecutor.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/acquisition/CatalogImporter.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/concurrency/CatalogMutationGate.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/retention/ActiveStoryPins.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/discover/DiscoverSession.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/story/StoryDetailSession.kt`
- focused unit tests under matching `src/test` packages.

### `:feature:catalog`

- `feature/catalog/build.gradle.kts`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogEntryPoint.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogComposition.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogRoute.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogScreen.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/trace/CatalogTrace.kt` — Android-free trace names + `CatalogTraceSink` contract; Task 14 extends usage/evidence rather than creating a second authority.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/trace/AndroidCatalogTraceSink.kt` — the only Step 2 production adapter that calls `android.os.Trace`.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/state/CatalogIssueUi.kt` — safe UI issue mapping from typed domain failures; no raw exception/payload/URL text.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverUiState.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverViewModel.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverScreen.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverSections.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailUiState.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailViewModel.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailScreen.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CatalogImageLoader.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverFetcher.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverEncodedDiskCache.kt` — capability-private explicit encoded-byte cache keyed only by `CoverAssetKey.stableCacheKey`, backed by the one Coil `DiskCache` instance capped at 128 MiB; remote custom fetches read/write it explicitly rather than assuming Coil will persist arbitrary custom-fetcher bytes.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/RemoteCoverPolicy.kt`
- non-release source adapters under `src/debug`, `src/benchmarkRelease`, and `src/nonMinifiedRelease` as defined in Task 6.
- plugin harness/support under `src/androidTest` only as defined in Task 15.

### App shell and benchmark

- `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt` — `Ready` renders only the feature entry point.
- delete/retire `app/src/main/kotlin/app/openstory/startup/ui/HomeShell.kt` after the feature entry is available and tests are migrated.
- `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt`
- benchmark fixture state must enter Catalog through the importer/source boundary.

### Documentation/evidence

- `docs/superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md` — canonical checked-in copy of the reviewed R2.1 design before Task 0 closes.
- `docs/superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md` — this reviewed plan, checked in with Task 0 so every later thread can resume from repository authority rather than chat/file-upload history.
- `docs/project/approved-product-design.md` — Step 2 Light Novel amendment.
- `docs/project/current-state.md` — update only as tasks are actually accepted.
- `docs/implementation/current-roadmap.md` — exact next task pointer; never used to auto-start the next task.
- `docs/internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md` — cumulative Step 2 evidence/open gates.
- `docs/internal/v2/startup-baseline-2026-09-07.md` remains immutable historical evidence.
- final Step 2 performance evidence gets a new file, e.g. `docs/internal/v2/step-2-performance-baseline-2026-09-08.md` with actual measurement date when executed.

---

## Core interface contract frozen for the plan

These names/signatures are the cross-task handoff. Implementers may add private helpers but must not silently substitute broad V1 models.

```kotlin
enum class CatalogValidationReason { MALFORMED, OVER_LIMIT, AUTHORITY_MISMATCH, INVARIANT_VIOLATION }
enum class CatalogOperation { DISCOVER, STORY_DETAIL }
enum class CatalogStorageOperation {
    OPEN, READ_DISCOVER, READ_STORY, PUBLISH_DISCOVER, PUBLISH_STORY, RETENTION
}
enum class CatalogArtworkFailureReason {
    INVALID_LOCATOR,
    POLICY_REJECTED,
    REDIRECT_REJECTED,
    TIMEOUT,
    MEDIA_TYPE_REJECTED,
    ENCODED_TOO_LARGE,
    DIMENSIONS_TOO_LARGE,
    DECODE_FAILED,
    IO_FAILED,
}

sealed interface CatalogFailure {
    data class Validation(
        val field: String,
        val reason: CatalogValidationReason,
    ) : CatalogFailure

    data object SourceUnavailable : CatalogFailure
    data class Acquisition(val operation: CatalogOperation) : CatalogFailure
    data class IdentityCollision(val storyId: String) : CatalogFailure
    data class Storage(val operation: CatalogStorageOperation) : CatalogFailure
    data class Artwork(val reason: CatalogArtworkFailureReason) : CatalogFailure
    data class InternalInvariant(val code: String) : CatalogFailure
}

class CatalogFailureException(
    val failure: CatalogFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.toString(), cause)

internal object CatalogIdentifierRules {
    fun requireValidSourceKey(value: String)
    fun requireValidSourceStoryId(value: String)
}

@JvmInline
value class CatalogSourceKey(val value: String) {
    init {
        CatalogIdentifierRules.requireValidSourceKey(value)
    }
}

data class SourceStoryKey(
    val catalogSourceKey: CatalogSourceKey,
    val sourceStoryId: String,
)

object SourceStoryIdV1 {
    fun derive(source: SourceStoryKey): StoryId
}

data class StorySourceRef(
    val storyId: StoryId,
    val catalogSourceKey: CatalogSourceKey,
    val sourceStoryId: String,
) {
    init {
        require(storyId == SourceStoryIdV1.derive(SourceStoryKey(catalogSourceKey, sourceStoryId)))
    }
}

enum class CatalogMediaType { MANGA, LIGHT_NOVEL }
enum class CatalogSectionKind { POPULAR, LATEST_UPDATES, TOP_RATED }

object CatalogSectionCaps {
    const val MAX_DISCOVER_MEMBERSHIPS = 19
    const val MAX_SECTIONS = 3
    fun cap(kind: CatalogSectionKind): Int = when (kind) {
        CatalogSectionKind.POPULAR -> 5
        CatalogSectionKind.LATEST_UPDATES -> 9
        CatalogSectionKind.TOP_RATED -> 5
    }
}

object CatalogAcquisitionValidator {
    fun requireValidDiscover(acquisition: DiscoverAcquisition)
    fun requireValidStoryDetail(ref: StorySourceRef, acquisition: StoryDetailAcquisition)
}

object CatalogPublicationValidator {
    fun requireValidPublishedCards(cards: List<DiscoverCard>)
    fun requireValidStoryDetail(
        summary: StorySummaryProjection,
        detail: StoryRichDetailProjection,
    )
}

data class AcquisitionProvenance(
    val catalogSourceKey: CatalogSourceKey,
    val sourceVersion: String,
    val acquiredAtEpochMs: Long,
)

data class CatalogRating(
    val value: Double,
    val scale: Double,
)

sealed interface AcquisitionCoverInput {
    data class TrustedLocal(
        val logicalAssetId: String,
        val assetVersion: String,
    ) : AcquisitionCoverInput

    data class RemoteHttps(
        val rawUri: String,
        val reviewedStableArtworkToken: String? = null,
    ) : AcquisitionCoverInput
}

data class DiscoverAcquisitionItem(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val cover: AcquisitionCoverInput?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

data class DiscoverAcquisitionSection(
    val kind: CatalogSectionKind,
    val items: List<DiscoverAcquisitionItem>, // list index is source section ordinal
)

data class DiscoverAcquisition(
    val sections: List<DiscoverAcquisitionSection>,
)

data class StoryDetailAcquisition(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val cover: AcquisitionCoverInput?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)

data class DiscoverCard(
    val ref: StorySourceRef,
    val sectionKind: CatalogSectionKind,
    val itemPosition: Int,
    val title: String,
    val contentType: CatalogMediaType,
    val sourceVersion: String,
    val coverLocator: CoverLocator?,
    val coverAssetKey: CoverAssetKey?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

data class StorySummaryProjection(
    val ref: StorySourceRef,
    val title: String,
    val contentType: CatalogMediaType,
    val sourceVersion: String,
    val coverLocator: CoverLocator?,
    val coverAssetKey: CoverAssetKey?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

data class StoryRichDetailProjection(
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)

sealed interface DiscoverPersistenceState {
    data object Absent : DiscoverPersistenceState
    data class Published(
        val generation: Long,
        val provenance: AcquisitionProvenance,
        val cards: List<DiscoverCard>,
    ) : DiscoverPersistenceState
}

data class StoryDetailProjection(
    val ref: StorySourceRef,
    val summary: StorySummaryProjection,
    val detail: StoryRichDetailProjection?,
    val detailProvenance: AcquisitionProvenance?,
) {
    init {
        require(summary.ref == ref)
        require((detail == null) == (detailProvenance == null))
        detailProvenance?.let { require(it.catalogSourceKey == ref.catalogSourceKey) }
    }
}

@JvmInline
value class RemoteHttpsUriV1 private constructor(val value: String) {
    companion object {
        fun parseAndNormalize(raw: String): RemoteHttpsUriV1
    }
}

private val COVER_REVISION_PATTERN = Regex("^cover:v1:[0-9a-f]{64}$")

@JvmInline
value class CoverRevision(val value: String) {
    init {
        require(COVER_REVISION_PATTERN.matches(value))
    }
}

object CoverRevisionV1 {
    fun local(logicalAssetId: String, assetVersion: String): CoverRevision
    fun remoteUri(normalizedUri: RemoteHttpsUriV1): CoverRevision
    fun remoteStableToken(token: String): CoverRevision
}

data class CoverAssetKey(
    val storyId: StoryId,
    val coverRevision: CoverRevision,
) {
    val stableCacheKey: String
        get() = "hikari:v2:cover-asset:v1:${storyId.value}:${coverRevision.value}"
}

sealed interface CoverLocator {
    data class TrustedLocalResource(
        val logicalAssetId: String,
        val assetVersion: String,
    ) : CoverLocator

    data class RemoteHttps(
        val catalogSourceKey: CatalogSourceKey,
        val normalizedUri: RemoteHttpsUriV1,
        val revision: CoverRevision,
    ) : CoverLocator
}

fun requireAlignedCover(
    ref: StorySourceRef,
    locator: CoverLocator?,
    key: CoverAssetKey?,
) {
    require((locator == null) == (key == null))
    if (locator == null || key == null) return
    require(key.storyId == ref.storyId)
    when (locator) {
        is CoverLocator.TrustedLocalResource ->
            require(key.coverRevision == CoverRevisionV1.local(locator.logicalAssetId, locator.assetVersion))
        is CoverLocator.RemoteHttps -> {
            require(locator.catalogSourceKey == ref.catalogSourceKey)
            require(key.coverRevision == locator.revision)
        }
    }
}

data class SourceAssetPolicy(
    val catalogSourceKey: CatalogSourceKey,
    val allowedHttpsHosts: Set<String>,
)

fun interface SourceAssetPolicyProvider {
    fun policyFor(catalogSourceKey: CatalogSourceKey): SourceAssetPolicy?
}

interface DiscoverReadPort {
    fun observe(
        catalogSourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
    ): Flow<DiscoverPersistenceState>
}

interface StoryDetailReadPort {
    fun observe(ref: StorySourceRef): Flow<StoryDetailProjection?>
}

interface CatalogAcquisitionSource {
    suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition
    suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition
}

data class DiscoverPublicationCommand(
    val catalogSourceKey: CatalogSourceKey,
    val mediaType: CatalogMediaType,
    val provenance: AcquisitionProvenance,
    val cards: List<DiscoverCard>, // already validated/capped and section/item-positioned
) {
    init {
        require(provenance.catalogSourceKey == catalogSourceKey)
        CatalogPublicationValidator.requireValidPublishedCards(cards)
        require(cards.all { it.ref.catalogSourceKey == catalogSourceKey })
        require(cards.all { it.contentType == mediaType })
        require(cards.all { it.sourceVersion == provenance.sourceVersion })
        cards.forEach { requireAlignedCover(it.ref, it.coverLocator, it.coverAssetKey) }
    }
}

data class StoryDetailPublicationCommand(
    val ref: StorySourceRef,
    val provenance: AcquisitionProvenance,
    val summary: StorySummaryProjection,
    val detail: StoryRichDetailProjection,
) {
    init {
        require(provenance.catalogSourceKey == ref.catalogSourceKey)
        require(summary.ref == ref)
        require(summary.sourceVersion == provenance.sourceVersion)
        CatalogPublicationValidator.requireValidStoryDetail(summary, detail)
        requireAlignedCover(summary.ref, summary.coverLocator, summary.coverAssetKey)
    }
}

object CatalogMutationBounds {
    // One Discover publication can touch <=19 previous + <=19 new + <=19 overflow evictions.
    const val MAX_DISCOVER_TOUCHED_STORY_IDS = 57
    // Releasing one pin can classify that Story and evict at most one bounded orphan overflow.
    const val MAX_RELEASE_TOUCHED_STORY_IDS = 2
}

data class CatalogMutationDiagnostics(
    val touchedStoryIds: Set<StoryId>, // bounded diagnostic set; never historical enumeration
)

interface CatalogWritePort {
    suspend fun publishDiscover(
        command: DiscoverPublicationCommand,
        retentionProtectedStoryIds: Set<StoryId>,
    ): CatalogMutationDiagnostics

    suspend fun publishStoryDetail(command: StoryDetailPublicationCommand)

    suspend fun touchStoryAccess(
        ref: StorySourceRef,
        accessedAtEpochMs: Long,
    )

    suspend fun releaseStoryDemand(
        ref: StorySourceRef,
        retentionProtectedStoryIds: Set<StoryId>,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics
}
```

`CatalogSourceBinding` is a `:catalog:runtime` composition type, not plugin payload data. It carries the host-authoritative `CatalogSourceKey`, verified `sourceVersion`, optional `CatalogAcquisitionSource`, and optional `SourceAssetPolicy`. Runtime constructs `AcquisitionProvenance` from that binding plus the host clock; acquisition payloads never provide provenance authority. Debug/benchmark/plugin-test bindings contain a source; a release binding may be absent entirely. Focused runtime tests also cover a known binding whose `acquisitionSource == null` so R2.1's `Absent + no source` semantics are executable without inventing a fake release source key.

`CoverRevisionV1` is also frozen here so cache identity cannot drift between adapters. All inputs use strict well-formed UTF-8 encoding and the same `uint32_be(length) + exact UTF-8 bytes` framing as Story identity. Prefixes are exact: `hikari:v2:cover-revision:local:v1\0` for `(logicalAssetId, assetVersion)`, `hikari:v2:cover-revision:remote-uri:v1\0` for one normalized URI, and `hikari:v2:cover-revision:remote-token:v1\0` for one reviewed stable token. All three retain the full SHA-256 digest and encode `cover:v1:<64-lowercase-hex>`. Golden vectors include:

```text
local("seed:manga:001", "1")
-> cover:v1:3bc89e103d522e221a479fc1b64753d3e6c49d403aaff32abbc077fa0b82e4c7
remoteUri("https://cdn.example.com/covers/1.webp?sig=A%2F")
-> cover:v1:b55bfd978ff7aded1b47e8f862627af349a4d7d849692684b7903b25106c9049
remoteStableToken("cover-123-v2")
-> cover:v1:78c1febd35473618f886cb53dc532532a0c2a961cb52f781b688be471bb95be5
```

A raw plugin/payload revision field is never authoritative.

`RemoteHttpsUriV1.parseAndNormalize` freezes the generic safe locator normalization instead of leaving URL identity to whichever HTTP library is later selected: reject malformed UTF-16 and input longer than 4,096 characters; parse a non-opaque absolute URI; require HTTPS; reject userinfo and fragment; require a DNS hostname; canonicalize the hostname with IDNA/STD3 ASCII rules, lowercase it with `Locale.ROOT`, and remove one terminal DNS dot; allow only absent port or `443` and canonicalize `:443` away; preserve the URI's **raw path and raw query exactly** (including query ordering, percent-escape spelling, and an explicitly empty query) rather than stripping/re-sorting signed parameters or applying dot-segment guesses; canonicalize an empty path to `/`; reject backslashes/control characters. `SourceAssetPolicy.allowedHttpsHosts` uses the same host canonicalizer. Redirect targets are resolved against the current validated URI first, then re-run through this same parser/policy before transport follows them.
`CatalogIdentifierRules` lives in the `identity` package (not the higher-level acquisition `validation` package) to preserve the package DAG. `requireValidSourceKey` enforces the R2.1 source-key ceiling at type construction (strict well-formed UTF-8, nonblank, <=128 UTF-8 bytes); `SourceStoryIdV1.derive` calls `requireValidSourceStoryId` immediately before hashing. `COVER_REVISION_PATTERN` is exactly `cover:v1:[0-9a-f]{64}`. `CoverAssetKey.stableCacheKey` is the only production string supplied to image-memory/disk key APIs; never use data-class `toString()`, a raw model object, or an undefined `.value` property as cache identity.

`CatalogWritePort` is the cross-module **atomic mutation boundary**. Runtime never orchestrates a publication by calling DAO-shaped primitives one-by-one: one `publishDiscover(...)` call maps to one Room transaction containing previous-generation lookup, bulk identity/summary/card writes, generation advance, obsolete-generation delete, and bounded delta retention; one `publishStoryDetail(...)` maps to one keyed detail transaction. `releaseStoryDemand(...)` is the separate bounded atomic cleanup used after a pin is removed. Publication-command constructors and the storage boundary both fail closed on authority/alignment drift: provenance source must match the command/ref, every Discover card must belong to the command source/media and provenance sourceVersion, Story summary ref/version must match its command, `CoverAssetKey.storyId` must match the routed Story, trusted-local revisions must equal `CoverRevisionV1.local(logicalAssetId, assetVersion)`, remote locators must carry the same host-authoritative source key and the same revision as the asset key, and section positions are 0-based contiguous values below the 5/9/5 caps. `CatalogMutationDiagnostics.touchedStoryIds` exists only to prove bounded work in tests/benchmarks and is itself bounded by the mutation inputs; it is not a history API. Assert `<=57` touched IDs for Discover publication and `<=2` for release cleanup; exceeding either bound is a test/diagnostic invariant failure, not an invitation to enumerate history. Storage owns `CatalogFailure.IdentityCollision` when an existing `story_id` maps to a different exact `(source_key, source_story_id)` pair and transports it as `CatalogFailureException`; the write-port success signature remains `CatalogMutationDiagnostics`, so collision is a typed exceptional failure rather than a fake success value. Import validation likewise raises `CatalogFailure.Validation`, source execution becomes `CatalogFailure.Acquisition`, framework storage failures become `CatalogFailure.Storage`, and artwork policy/resource failures become `CatalogFailure.Artwork`. Every catch boundary distinguishes external cancellation from owned deadline expiry: external `CancellationException` is rethrown unchanged, while operation-owned deadlines are produced as dedicated timeout results (`withTimeoutOrNull`/equivalent) and only then mapped to the typed timeout/acquisition failure.

---

# Task 0: Admit Step 2 build surface, exact module graph, variants, and product authority

**Files:**
- Create/copy reviewed artifact: `docs/superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.1.md`
- Create/copy reviewed artifact: `docs/superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- Modify: `docs/project/approved-product-design.md`
- Create: `docs/internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `catalog/domain/build.gradle.kts`
- Create: `catalog/storage/build.gradle.kts`
- Create: `catalog/runtime/build.gradle.kts`
- Create: `feature/catalog/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/app/openstory/build/AndroidLibraryConventionPlugin.kt`
- Modify: `build-logic/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `config/architecture/v2-foundation-policy.json`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/ProductionPackageStructureVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyProductionPackageStructureTask.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/Step2BuildSurfaceVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyStep2BuildSurfaceTask.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/FoundationConventionPlugin.kt`
- Test: matching build-logic tests plus `scripts/tests/v2-step2-build-surface-test.sh`

**Interfaces:**
- Consumes: accepted Step 1 graph/ratchets.
- Produces: four empty-but-buildable modules, exact graph/variant allowlist, package-SCC verifier, product amendment enabling Light Novel.
- Must not add production Catalog behavior yet.

- [ ] **Step 1: Write failing architecture tests before adding modules**

Add tests that expect the future exact graph and reject these negative fixtures:

```text
:app -> :catalog:runtime                  => rejected
:feature:catalog -> :catalog:model        => rejected
:catalog:runtime -> :catalog:engine       => rejected
Room dependency outside :catalog:storage  => rejected
Coil outside :feature:catalog             => rejected
OkHttp / concrete HTTP transport anywhere in Step 2 production => rejected (production remote network is not admitted yet)
`coil-network-okhttp` anywhere in Step 2   => rejected (would bypass the custom source-policy transport)
seed source under main/release             => rejected
plugin harness under main/release          => rejected
raw HTTP/client import from any Step 2 production package => rejected
package SCC A -> B -> A                    => rejected
```

Also add one positive build-surface fixture for the exact graph.

- [ ] **Step 2: Run RED**

```bash
./gradlew :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' --tests '*ProductionPackageStructureVerifierTest*' --no-daemon
```

Expected: FAIL because the new verifier/module policy does not exist.

- [ ] **Step 3: Add minimal Android-library convention and reviewed aliases**

Register `openstory.android.library` in `build-logic/build.gradle.kts` and implement the convention with only `com.android.library`, compileSdk 37, minSdk 26, Java 17, plus explicit `benchmarkRelease` and `nonMinifiedRelease` build types compatible with the app. It must **not** apply Room, KSP, Compose, Coil, Hilt, or any source dependency automatically.

Add only the aliases that are actually consumed: KSP 2.3.9 with plugin alias `com.google.devtools.ksp`; Room 2.8.4 with plugin alias `androidx.room` plus `room-runtime`, `room-ktx`, `room-compiler`, `room-testing`; Lifecycle 2.11.0 (`lifecycle-runtime-compose`, `lifecycle-viewmodel`, `lifecycle-viewmodel-compose`); and Coil 3.5.0 `coil-compose`. Step 2 does **not** add OkHttp, MockWebServer, `coil-network-okhttp`, or any other production/test HTTP stack: redirect/timeout/stream-cap behavior is tested against the injectable `RemoteCoverTransport` contract with deterministic in-memory responses, matching the R2.1 controlled-transport gate. Do **not** add a direct `com.android.library` catalog alias merely to duplicate the convention plugin and do **not** add an `openstory.room` plugin. The absence of Coil's network module is deliberate: remote cover bytes may enter only through Task 11's policy-enforcing injected transport port, so a raw URI cannot silently bypass host/redirect/size checks. Root `build.gradle.kts` does not need a new apply-false Room/KSP/Android-library alias solely for this task.

Freeze the module build contracts now rather than leaving Gradle transitivity to Task 1+:

```text
:catalog:domain
  plugins: java-library + openstory.kotlin.jvm
  api(project(":core:common"))
  api(kotlinx-coroutines-core)  # public read ports expose Flow
  test: kotlin-test-junit + kotlinx-coroutines-test

:catalog:storage
  plugins: openstory.android.library + KSP + Room
  namespace: app.openstory.catalog.storage
  implementation(project(":catalog:domain"), project(":core:common"), room-runtime, room-ktx, coroutines-core)
  ksp(room-compiler); room schemaDirectory("$projectDir/schemas")
  androidTest: androidx-junit + androidx-test-core + runner + room-testing + coroutines-test

:catalog:runtime
  plugin: openstory.android.library
  namespace: app.openstory.catalog.runtime
  implementation(project(":catalog:domain"), project(":catalog:storage"), coroutines-core)
  test: junit + coroutines-test

:feature:catalog
  plugins: openstory.android.library + openstory.compose
  namespace: app.openstory.catalog.feature
  implementation(project(":catalog:domain"), project(":catalog:runtime"), Compose BOM/ui/material3, lifecycle-runtime-compose, lifecycle-viewmodel, lifecycle-viewmodel-compose, coroutines-core, coil-compose)
  test: junit + coroutines-test
  androidTest: Compose BOM/ui-test-junit4 + androidx-junit + androidx-test-core + espresso + runner; androidTestUtil orchestrator
  debugImplementation(compose-ui-test-manifest)
```

The Android library convention only supplies SDK/Java/build-type mechanics. Each Android module declares its own `namespace`; `:catalog:storage` and `:feature:catalog` set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and `testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"` when they consume the orchestrator test utility. `:catalog:runtime` does not add unused androidTest/orchestrator wiring until a real connected test exists. Do not add a direct `:core:common` edge from runtime/feature: `:catalog:domain` exposes the `StoryId` API type via `api(project(":core:common"))`.

- [ ] **Step 4: Admit exact modules/edges and evolve app-shell policy**

`module-boundaries.json` must exactly encode the production graph in Global Constraints **and preserve the existing exact test graph**: `:app.testDependencies = [":benchmark"]`, `:benchmark.testDependencies = [":app"]`, every new Step 2 module starts with `testDependencies = []`, and Task 15 alone later changes `:feature:catalog.testDependencies` to `[":plugins:api"]`. Because the existing verifier supports forbidden-prefix matching rather than allow-exceptions, remove the single broad `app.openstory.catalog.` app ban and replace it with explicit forbidden prefixes for `app.openstory.catalog.domain.`, `app.openstory.catalog.runtime.`, `app.openstory.catalog.storage.`, and the quarantined Catalog namespaces. The project edge alone is **not** sufficient permission: `Step2BuildSurfaceVerifier` must scan `app/src/main` and allow exactly one Catalog feature import, `app.openstory.catalog.feature.CatalogEntryPoint`; imports of `feature.discover`, `feature.story`, `feature.assets`, `feature.composition`/router internals, or any other `app.openstory.catalog.feature.*` symbol fail. The exact `:app -> :feature:catalog` project edge plus this one-symbol source allowlist is the app-facing permission; Room/WorkManager/OkHttp/Coil/plugin/Reader/etc. remain forbidden from the app shell.

Replace the foundation policy's blanket `implementation(project(` rejection with exact graph verification; do not remove source, startup, permission, broad-authority, or line/SCC ratchets.

- [ ] **Step 5: Add package-SCC and build-surface Gradle tasks**

`verifyArchitecture` must depend on:

```text
verifyModuleBoundaries
verifyApplicationIdentity
:app:verifyFoundation
verifyProductionPackageStructure
verifyStep2BuildSurface
```

Keep the existing `FoundationConventionPlugin` `androidComponents.onVariants(selector().all())` merged-manifest verification intact. Add a build-logic regression proving `debug`, `release`, `benchmarkRelease`, and `nonMinifiedRelease` each wire a merged-manifest startup check; none may silently drop provider/service/receiver/initializer/forbidden-permission verification when the foundation policy evolves.

The package verifier scans production Kotlin/Java under the four Step 2 modules and reports every SCC with >1 package. `Step2BuildSurfaceVerifier` additionally owns path-scoped/source-dependency rules that the module graph cannot express: Coil implementation imports are confined to `feature/catalog/.../assets`; **no Step 2 production package may import a concrete HTTP client/network adapter**; `discover`, `story`, and root presentation/router code receive only typed cover contracts/request state.

- [ ] **Step 6: Persist the reviewed design/plan and update product authority/checkpoint**

Copy the exact reviewed R2.1 design and this reviewed implementation plan into the canonical `docs/superpowers/specs` / `docs/superpowers/plans` paths listed above before Task 0 closes. Record SHA-256 of both artifacts in the new Step 2 checkpoint so later execution cannot silently resume from a different upload.

Amend the 2026-08-19 Discover block so `MANGA` and `LIGHT_NOVEL` are both enabled from Step 2 onward, and explicitly state Search remains later scope. Create the Step 2 checkpoint with Task 0 as current boundary and all later gates `NOT RUN`.

- [ ] **Step 7: Run focused agent-owned GREEN checks**

```bash
./gradlew :build-logic:test :catalog:domain:test :catalog:storage:compileDebugKotlin :catalog:runtime:compileDebugKotlin :feature:catalog:compileDebugKotlin --no-daemon
```

Expected: PASS. These checks prove the convention/verifier code and four empty module surfaces compile without asking the agent to run repository-wide policy gates.

- [ ] **Step 8: Hand off the required broad architecture/build-surface gate**

User-owned by default under repository `AGENTS.md` unless explicitly delegated back:

```bash
./gradlew verifyArchitecture :app:verifyFoundation --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

Expected: PASS with the exact four new production modules/edges, preserved `:app <-> :benchmark` test edges, no stale allowlist entry, and no production behavior/seed/plugin leakage. Task 0 remains `READY FOR USER VERIFICATION` until this evidence is reviewed.

- [ ] **Step 9: Self-review Task 0**

Inspect module edges, test edges, variant names, source-set fallback, **all four app merged-manifest verification tasks** (debug/release/benchmarkRelease/nonMinifiedRelease), dependency locations, package-SCC scanning scope, and exact forbidden app prefixes. Prove no Room/Coil/seed/plugin support leaked into an unapproved owner and no OkHttp/concrete HTTP transport exists in production Step 2 and no Step 1 startup gate was deleted.

- [ ] **Step 10: Persist state, commit, and stop**

Only after the required user-owned gate is green, update checkpoint + `current-roadmap.md` to Task 1, then commit, e.g.:

```bash
git add -A
git commit -m "build: admit hikari v2 catalog foundation"
```

**Stop.**

---

# Task 1: Freeze pure domain identity, provenance, bounds, semantic sections, and ports

**Files:** the `:catalog:domain` files listed in the ownership map plus focused tests.

**Interfaces:**
- Produces the exact cross-task types shown in the Core interface contract.
- No Android/Room/Compose/Coil/HTTP-client/plugin DTOs.

- [ ] **Step 1: Write RED golden-vector tests for `SourceStoryIdV1`**

Include at least:

```kotlin
@Test fun exactCaseSensitiveUtf8InputsProduceFrozenId()
@Test fun mutableMetadataCannotChangeStoryId()
@Test fun caseOrWhitespaceChangeInOpaqueSourceIdChangesIdentity()
@Test fun overBoundIdentifiersAreRejectedBeforeHashing()
```

Golden tests include hard-coded expected vectors, not only a second implementation of the same algorithm:

```text
("org.openstory.catalog.mangaupdates", "12345")
-> source-story:v1:fbae52bb502eba96312109575f89437f1c1c7a4fa07f7eb69f7ada51e41f999c

("source", "Story-01")
-> source-story:v1:b5296442f511e0e7a9a8699e1edb1c3d97340a8cb972c4182a886ef2de80fa55

("S", "作品-Ä")
-> source-story:v1:87a4154a9ed813f805e3a04ac0f491aa71f44bb95e58449fcfdf43a83797bb50
```

The production bytes are still exactly:

```text
ASCII "hikari:v2:source-story:v1" + 0x00
+ uint32_be(sourceKeyUtf8.length) + sourceKeyUtf8
+ uint32_be(sourceStoryIdUtf8.length) + sourceStoryIdUtf8
```

Then SHA-256 full 32 bytes -> 64 lowercase hex -> `source-story:v1:<hex>`. Add `unpairedHighSurrogateIsRejected`, `unpairedLowSurrogateIsRejected`, and `storySourceRefRejectsMismatchedDerivedStoryId`; never let the JDK encoder replace malformed UTF-16 with U+FFFD/`?` before hashing.

- [ ] **Step 2: Write RED validation/provenance/asset-identity tests**

Cover every R2.1 ceiling and host authority: payload cannot select another `CatalogSourceKey`, sourceVersion, or acquiredAt timestamp; importer-facing validation maps malformed/oversized input to `CatalogFailure.Validation(field, reason)` and is never silently truncated. Strict UTF-8 validation rejects malformed surrogate input before byte counting. Primitive invariant constructors may fail fast internally, but the public acquisition/import boundary must translate those invariant violations to the typed validation failure before persistence/UI exposure. Add failure-taxonomy tests proving `CatalogFailureException` contains only the typed value/cause and that cancellation is not represented by a `CatalogFailure`.

Add `CoverRevisionV1` vectors proving: local revision changes if either logical asset ID or version changes; default remote revision hashes the exact `RemoteHttpsUriV1.value`; source-stable token revision uses a distinct domain prefix; plugin/raw payload data cannot directly provide a trusted `CoverRevision`. Add `RemoteHttpsUriV1` tests for scheme/IDNA-host/default-443 canonicalization, empty-path -> `/`, exact raw-query preservation (including signed/query-order differences), no generic dot-segment/query stripping, relative redirect resolution followed by full revalidation, and rejection of userinfo/fragment/non-HTTPS/non-DNS-host/control/backslash/oversize input. Add hard tests that malformed `CoverRevision` strings are rejected and that `CoverAssetKey.stableCacheKey` is deterministic, changes when either StoryId or revision changes, and never depends on `toString()`. Add `SourceAssetPolicy` validation for canonical nonblank HTTPS host names and mismatched `CatalogSourceKey`. Add publication-command invariant tests: mismatched provenance source/ref/sourceVersion/media fail before storage; section positions are exactly 0-based/contiguous and below `CatalogSectionCaps.cap(kind)`; duplicate `(sectionKind, itemPosition)` or `(sectionKind, storyId)` fails; every published card scalar/locator/rating/timestamp is revalidated against the frozen limits even if a rogue caller bypasses the importer; `CoverAssetKey.storyId` mismatch fails; trusted-local locator/key revision mismatch fails; remote locator source-key mismatch or revision mismatch fails; locator/key nullability mismatch fails. Construct `StoryDetailPublicationCommand` directly with over-bound/malformed summary/detail fields and child collections and prove it fails before `CatalogWritePort`; storage re-runs the same publication validator before opening its mutation transaction so a cross-module caller cannot persist an unchecked projection. Acquisition boundary tests also reject >3 sections, duplicate semantic section kinds, any source section already larger than its 5/9/5 cap, total memberships >19, negative timestamps where a timestamp is present, invalid/non-finite ratings, overbound status/language/asset-token fields, and Story Detail child collections beyond their frozen caps.

- [ ] **Step 3: Write RED semantic projection tests**

Freeze the exact Step 2 ordering so adapters cannot invent their own tie-breaks:

```text
POPULAR: preserve validated source section order; cap 5.
LATEST_UPDATES: latestUpdateEpochMs DESC, then source section ordinal ASC, then StoryId.value ASC; cap 9.
TOP_RATED: normalized score (value / scale) DESC, then source section ordinal ASC, then StoryId.value ASC; cap 5.
```

A score is eligible only when value/scale are finite, `scale > 0`, and `value in 0..scale`; persist/present the original value and scale rather than replacing it with a synthetic 10-point score. Missing timestamp/rating is ineligible, not zero.

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:domain:test --no-daemon
```

Expected: compile/test failure because types are not implemented.

- [ ] **Step 5: Implement minimal pure-domain contracts**

Implement the Core interface contract plus the low-level `catalog.domain.limits.CatalogInputLimits` below. Keep it out of `validation`: identity hashing/identifier rules and asset parsing need these constants, and a validation-package owner would create an upward dependency from lower packages.

```kotlin
object CatalogInputLimits {
    const val SOURCE_KEY_UTF8_BYTES = 128
    const val SOURCE_STORY_ID_UTF8_BYTES = 512
    const val TITLE_UNICODE_SCALARS = 1_024
    const val DESCRIPTION_UTF8_BYTES = 64 * 1_024
    const val AUTHORS = 32
    const val ARTISTS = 32
    const val GENRES = 64
    const val PERSON_UNICODE_SCALARS = 512
    const val GENRE_UNICODE_SCALARS = 256
    const val COVER_LOCATOR_CHARS = 4_096
    const val SOURCE_VERSION_UTF8_BYTES = 256
    const val STATUS_UNICODE_SCALARS = 512
    const val LANGUAGE_UNICODE_SCALARS = 128
    const val LOCAL_ASSET_ID_UTF8_BYTES = 512
    const val LOCAL_ASSET_VERSION_UTF8_BYTES = 128
    const val STABLE_ARTWORK_TOKEN_UTF8_BYTES = 512
    const val DISCOVER_SECTIONS = 3
}
```

Use true Unicode scalar counting, not a naïve `String.length`. `CatalogSourceKey` validates itself through `CatalogIdentifierRules`; `SourceStoryIdV1.derive` validates `sourceStoryId` again at the hash boundary. Reject unpaired UTF-16 surrogates and use a strict UTF-8 encoder (`CodingErrorAction.REPORT` or an equivalent explicit scalar encoder) so malformed strings cannot collapse to replacement bytes. Use UTF-8 byte counting only where the R2.1 limit is byte-based. Host-owned `sourceVersion` is nonblank and <=256 UTF-8 bytes; `acquiredAtEpochMs` must be >=0.

- [ ] **Step 6: Implement typed acquisition/read/asset models and ports**

The acquisition/read model shapes above are load-bearing cross-task contracts. `DiscoverAcquisition` contains source payload data only: explicit semantic sections, source-local Story IDs, bounded card metadata and `AcquisitionCoverInput`. The generic source contract itself is bounded: at most three unique semantic sections, each list already <= its 5/9/5 cap, total <=19. A provider-specific adapter that receives a larger provider response must select/project its bounded semantic output **before** constructing `DiscoverAcquisition`; the generic importer never scans an unbounded raw provider feed. It does **not** contain authoritative `CatalogSourceKey`, sourceVersion, acquiredAt timestamp, or trusted `CoverRevision`; runtime/importer stamps/derives those from the host-owned binding. `reviewedStableArtworkToken` is adapter authority, never a field blindly copied from an untrusted plugin payload. `StoryDetailAcquisition.sourceStoryId` must equal the requested `StorySourceRef.sourceStoryId` before publication. `StoryDetailProjection` always contains the persisted summary; `detail == null` requires `detailProvenance == null`, while a present detail carries its independently persisted provenance. `coverLocator == null` iff `coverAssetKey == null`; otherwise the asset key must use that locator's derived revision. No broad V1 `Story` aggregate is introduced.

Implement `SourceAssetPolicyProvider` as the narrow lookup contract consumed by the feature asset layer; the concrete provider/binding is owned by runtime/composition in Task 5/6, not by presentation payloads.
`CatalogAcquisitionValidator` validates source payloads and `CatalogPublicationValidator` validates both Discover-card and Story-Detail projection publication shapes; neither imports `catalog.domain.write`. Write commands may call the publication validator, preserving the one-way package DAG. Provider adapters that start from a larger provider response must apply the authoritative semantic eligibility/order first (or preserve an explicitly ordered provider semantic list for Popular) and **then** cap; they may not truncate an arbitrary raw/unsorted provider list before the Step 2 semantic rule is applied.

- [ ] **Step 7: Run focused GREEN, then request broad verification**

Agent-owned focused gate:

```bash
./gradlew :catalog:domain:test --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew verifyArchitecture detekt --no-daemon
```

Task 1 remains `READY FOR USER VERIFICATION` until the broad result is reviewed. Domain must have no Android/framework imports and zero package SCC.

- [ ] **Step 8: Self-review, checkpoint, commit, stop**

Review hashing byte exactness, overflow-safe uint32 encoding, scalar/byte distinctions, semantic ordering, API width, and absence of V1 canonical types. Commit e.g. `feat: freeze v2 catalog domain contracts`. **Stop.**

---

# Task 2: Build new V2 Room schema and coherent `Absent | Published` Discover observation

**Files:**
- `catalog/storage/build.gradle.kts`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/CatalogDatabase.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/CatalogStorageFactory.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/discover/DiscoverEntities.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/discover/DiscoverDao.kt`
- identity/summary portions of `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/story/StoryEntities.kt`
- initial `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/RoomCatalogStore.kt`
- `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/DiscoverPersistenceInstrumentedTest.kt`
- `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/CatalogIdentityIntegrityInstrumentedTest.kt`
- `catalog/storage/schemas/app.openstory.catalog.storage.CatalogDatabase/1.json` (generated/exported schema baseline after GREEN).

**Interfaces:**
- Consumes Task 1 domain types.
- Produces lazy new V2 DB creation and `DiscoverReadPort.observe()`. `CatalogStorageFactory.open()` returns one runtime-owned `RoomCatalogStore : AutoCloseable`; `close()` closes the owned Room database exactly once and is called only at final capability-session destruction, not every STOP/navigation transition.
- Does not implement Story Detail children/retention mutation yet.

- [ ] **Step 1: Write RED Room instrumentation tests**

Step 1 has no Robolectric, so real Room behavior belongs under `src/androidTest`. Tests must prove:

```text
fresh DB => Absent
successful zero-card source_state row => Published(empty)
Published(empty) survives close/reopen
published content has deterministic POPULAR -> LATEST_UPDATES -> TOP_RATED ordering
one coherent Discover observation executes one SQL query per invalidation snapshot
child/detail tables are not needed to read Discover
unique (source_key, source_story_id)
unique story_id
a different exact SourceStoryKey cannot reuse an existing story_id => `CatalogFailureException(IdentityCollision)` / fail-closed storage mutation
unique (source, media, generation, section_kind, item_position)
unique (source, media, generation, section_kind, story_id)
invalid section/item positions rejected
POPULAR position outside 0..4 rejected
LATEST_UPDATES position outside 0..8 rejected
TOP_RATED position outside 0..4 rejected
```

Use Room's in-memory database for behavior tests and a file-backed temporary database for the close/reopen case. `room-testing` is `androidTestImplementation` only. Uniqueness/collision invariants are database-backed. Section-position/cap rejection must be proven through the public `RoomCatalogStore` publication boundary (inside the same transaction before rows commit); do not require an unsupported Room `@Check` annotation or pretend a DAO caller bypass is a product write path. A SQLite trigger/check may be used only if it is exported/reproducible and simpler than transaction-bound storage validation.

- [ ] **Step 2: Verify RED compile/contract state**

Agent-owned:

```bash
./gradlew :catalog:storage:compileDebugAndroidTestKotlin --no-daemon
```

Expected: FAIL because DB/DAO/schema types are not implemented yet. Do not add Robolectric merely to make this a JVM test.

- [ ] **Step 3: Implement schema baseline**

Create only the R2.1 tables needed at this point:

```text
catalog_source_state
story_source_identity
story_source_summary
discover_card
```

Freeze the new database as `hikari-v2-catalog.db`, Room schema `version = 1`, with exported schemas under `catalog/storage/schemas`. Do not import V1 migrations/entities. `catalog_source_state` primary identity is `(source_key, media_type)`; its first successful publication starts generation at 1 and later publication uses overflow-checked `previous + 1` (fail closed at `Long.MAX_VALUE`, never wrap).

- [ ] **Step 4: Implement one coherent Discover observation**

Use one Room `@Query` whose left side is `catalog_source_state` and whose optional joined rows are `discover_card`; return a row shape with nullable card columns, then map no rows to `Absent`, one state row + null card to `Published(empty)`, and state row + cards to `Published(content)`. Section order is encoded in SQL with a fixed `CASE POPULAR=0, LATEST_UPDATES=1, TOP_RATED=2`, followed by `item_position ASC`.

Do **not** implement `generationFlow.combine(cardsFlow)`, an inner join, or a second card query. The instrumentation test must count/observe one SQL statement for one snapshot read.

- [ ] **Step 5: Add DB invariants/indices and lazy factory**

Room opens only when runtime requests `CatalogStorageFactory.open()`. No static/object initializer touches Room. `RoomCatalogStore.close()` is idempotent for ownership safety and closes the single DB handle; connected tests prove close/reopen through `CatalogStorageFactory` rather than reaching around the store lifecycle. `CatalogStorageFactory`/`RoomCatalogStore` translate framework open/read/write failures to the appropriate `CatalogFailure.Storage` operation without leaking Room/SQLite classes; every wrapper rethrows `CancellationException` before mapping.

- [ ] **Step 6: Run focused GREEN compile, then request Room/device + broad gates**

Agent-owned:

```bash
./gradlew :catalog:storage:assembleDebug :catalog:storage:compileDebugAndroidTestKotlin --no-daemon
```

Required user-owned behavior gate unless explicitly delegated back:

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.DiscoverPersistenceInstrumentedTest,app.openstory.catalog.storage.CatalogIdentityIntegrityInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

Task 2 stays `READY FOR USER VERIFICATION` until these results are reviewed. Task 13 later repeats Room behavior on both API 26 and API 37; Task 2 needs one available Android target before schema acceptance so later tasks do not build on an unexecuted Room contract.

- [ ] **Step 7: Self-review, checkpoint, commit, stop**

Inspect generated SQL/schema, index coverage, query shape, absence of inner-join empty-state collapse, no broad JSON/blob read contract, no V1 migration chain. Commit e.g. `feat: add coherent v2 discover persistence`. **Stop.**

---

# Task 3: Add atomic keyed Story Detail persistence and bounded orphan-retention index

**Files:**
- Complete `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/story/StoryEntities.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/story/StoryDetailDao.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/retention/StoryRetentionEntity.kt`
- `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/retention/StoryRetentionDao.kt`
- extend `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/CatalogDatabase.kt`
- extend `catalog/storage/src/main/kotlin/app/openstory/catalog/storage/RoomCatalogStore.kt`
- `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/StoryDetailPersistenceInstrumentedTest.kt`
- `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/StoryRetentionInstrumentedTest.kt`.
- `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/DiscoverPublicationRetentionInstrumentedTest.kt` — real one-transaction publication/delta-retention behavior behind `CatalogWritePort`.


**Interfaces:**
- Produces `StoryDetailReadPort.observe(ref)` and storage-side `CatalogWritePort` atomic detail/touch/release primitives; `publishDiscover` is completed in Task 4 when the delta-retention transaction is wired end-to-end.
- `story_orphan_retention` remains <=64 after every successful mutation.

- [ ] **Step 1: Write RED atomicity/keyed-query instrumentation tests**

Prove one Story snapshot executes at most **4 SQL statements** regardless of unrelated row count: one joined summary/detail query plus one bounded ordered query each for authors, artists, and genres. `StoryDetailProjection(summary, detail = null, detailProvenance = null)` is valid before enrichment. A failed transaction preserves prior description + authors + artists + genres + detail provenance together. A later source-version publication does not rewrite old detail provenance. Access touch occurs once on destination activation/coalesced semantic event and does not write on every Flow emission.

- [ ] **Step 2: Write RED bounded-retention instrumentation tests**

Populate an aged test database and prove retention DAO touches only explicitly supplied candidate IDs plus the <=64 retention index. Reject any global `observeAll`, unbounded `COUNT(*)`, or history-wide `ORDER BY story_detail` on the foreground path. Verify child cardinality/order constraints and indexed lookup by `story_id`/source identity.

Do not force the production importer to manufacture thousands of unreachable rows just to create the pathological read fixture: when the measured subject is query shape rather than import semantics, a test-only storage fixture may insert unrelated rows directly. Current/active publication state used by the assertion still goes through the normal storage contract.

- [ ] **Step 3: Implement detail tables and deterministic child ordering**

Add `story_detail`, `story_author`, `story_artist`, `story_genre`, and `story_orphan_retention`. Child rows carry deterministic `position`.

- [ ] **Step 4: Implement one `@Transaction` detail publication**

Before opening the mutation transaction, re-run `CatalogPublicationValidator.requireValidStoryDetail(summary, detail)` and verify provenance/ref/sourceVersion/cover alignment at the storage boundary; a rogue cross-module caller therefore cannot bypass importer validation. Within one transaction: verify the exact persisted `StorySourceRef`/identity mapping; upsert selected summary/detail scalars; replace bounded child rows; store detail provenance; apply at most one explicitly requested/coalesced access touch. Failure before commit leaves the previous complete state. The transaction never rewrites `discover_card`. Constraint/identity collisions preserve the more specific typed `IdentityCollision`; unrelated Room/SQLite write failures map to `CatalogFailure.Storage(PUBLISH_STORY)` only after transaction rollback, never to partially persisted UI state.

- [ ] **Step 5: Implement bounded retention DAO API**

Keep DAO-shaped retention primitives **storage-internal**, not on the cross-module `CatalogWritePort`: `isReachableFromAnyCurrentDiscover(storyId)`, `hasDetail(storyId)`, add/remove/touch one orphan-retention entry, read oldest bounded orphan overflow, and delete one bounded set of unreachable Story rows. `RoomCatalogStore.publishDiscover(...)` and `releaseStoryDemand(...)` compose these primitives inside their own atomic transactions. Indexed eviction selects only from the <=64 retention set. Runtime cannot call these primitives directly, which prevents a future pseudo-transaction assembled from multiple cross-module calls.

- [ ] **Step 6: Run focused compile, then request behavior + broad gates**

Agent-owned:

```bash
./gradlew :catalog:storage:assembleDebug :catalog:storage:compileDebugAndroidTestKotlin --no-daemon
```

Required user-owned behavior gate unless explicitly delegated back:

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.StoryDetailPersistenceInstrumentedTest,app.openstory.catalog.storage.StoryRetentionInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

Task 3 remains on Task 3 until returned evidence is reviewed.

- [ ] **Step 7: Self-review, checkpoint, commit, stop**

Review the <=4 query cap, child bounds, transaction coherence, detail-provenance independence, access-touch invalidation, indices, cross-media reachability primitive shape, and no Discover-card mutation from detail enrichment. Commit e.g. `feat: add bounded story detail storage`. **Stop.**

---

# Task 4: Implement importer, atomic Discover publication, delta pruning, and pin/prune mutation ordering

**Files:**
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/acquisition/CatalogImporter.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/execution/CatalogExecutionDispatchers.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/concurrency/CatalogMutationGate.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/retention/ActiveStoryPins.kt`
- extend `catalog/domain/.../write/CatalogWritePort.kt` with bounded bulk mutation commands only
- extend `catalog/storage/.../RoomCatalogStore.kt` to implement those commands
- focused runtime tests under `catalog/runtime/src/test/...` using a recording/fault-injecting `CatalogWritePort`.
- complete `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/DiscoverPublicationRetentionInstrumentedTest.kt` against the real `RoomCatalogStore`.

**Interfaces:**
- `CatalogImporter.publishDiscover(binding, mediaType, acquisition, acquiredAtEpochMs)` derives host provenance from the `CatalogSourceBinding`; acquisition payload cannot supply it.
- `CatalogImporter.upsertStoryDetail(binding, ref, acquisition, acquiredAtEpochMs)` validates `ref` against the binding and validates the complete payload before Room mutation.
- Active pin registration/removal and publication/pruning share the same short gate.
- Step 2 freezes `MAX_ACTIVE_STORY_PINS = 2` to cover the current Story plus a transient route transition; exceeding it is a typed invariant failure, not silent growth.

- [ ] **Step 1: Write RED publication/identity tests**

Cover successful content publication, successful zero-card publication, transaction failure preserving previous generation, obsolete generation deletion, duplicate section position/story rejection, and detail publication not rewriting Discover cards. Do **not** make the production SHA-256 deriver injectable merely to synthesize a collision. In the connected storage fixture, pre-seed an identity row whose `story_id` equals the normally derived ID for key B but whose exact `(source_key, source_story_id)` is key A, then publish/import key B through the public store boundary; storage must translate the mismatch to typed `IdentityCollision` and leave prior state unchanged. In runtime unit tests, a fake `CatalogWritePort` throws `CatalogFailureException(CatalogFailure.IdentityCollision(...))`; importer/runtime must propagate the typed failure unchanged and assert there is no suffix/random/retry repair.

- [ ] **Step 2: Write RED bounded-work and reachability diagnostics**

Instrument the recording `CatalogWritePort` command/diagnostics plus the real connected `RoomCatalogStore` test so a refresh with 5/9/5 previous and new snapshots can assert the atomic publication touches only previous/current/removed IDs + bounded retention overflow, never thousands of historical rows. Assert `CatalogMutationDiagnostics.touchedStoryIds.size <= CatalogMutationBounds.MAX_DISCOVER_TOUCHED_STORY_IDS` (`57`) for publication and `<= CatalogMutationBounds.MAX_RELEASE_TOUCHED_STORY_IDS` (`2`) for pin release. Runtime supplies only the stable `retentionProtectedStoryIds` snapshot; it does not perform DAO-shaped reachability/pruning calls itself. For each removed Story, test three bounded branches explicitly:

```text
still referenced by another current Discover media scope -> keep, remove any stale orphan marker
not Discover-reachable + actively pinned -> insert/touch bounded orphan-retention candidate marker, keep active, exclude marker from eviction
not Discover-reachable + unpinned -> retain marker only when detail exists; otherwise delete summary/identity + marker
```

A Story that was protected by a pin during publication and becomes unpinned later must be reclassified under the same mutation gate: keyed reachability check -> keep/touch the already-bounded orphan candidate if detail exists, otherwise delete candidate + summary/identity; then evict bounded overflow excluding active pins. The candidate ledger is capped at 64 **including temporarily pinned removed Stories**. If the process dies before normal unpin, the durable candidate prevents an unreachable row from falling outside all future bounded maintenance; a later publication/retention mutation may evict that now-unprotected candidate and deletes its unreferenced rows in the same transaction. This closes both normal-unpin and process-death leaks without a global Story scan.

- [ ] **Step 3: Write RED deterministic pin/prune/unpin race tests**

Use barriers so publication has finished slow validation and is about to enter the gate while navigation registers a pin. Assert either ordering is safe: if pin wins, pruning sees it; if publication wins, route visibility waits until pin registration completes and the selected Story is reconstructed/validated before exposure. Add the inverse release race: a Story removed from Discover while pinned is not leaked after route demand ends; unpin reclassifies it atomically into bounded orphan retention or deletes it.

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:runtime:testDebugUnitTest --tests '*CatalogImporter*' --tests '*CatalogMutationGate*' --no-daemon
```

- [ ] **Step 5: Implement slow-work-before-gate pipeline and execution ownership**

Create `CatalogExecutionDispatchers(cpu = Dispatchers.Default, io = Dispatchers.IO)` with test injection. Identity derivation, input validation, semantic projection/capping and other non-trivial importer CPU work execute on `cpu`; Room suspending work remains storage-owned and source/network I/O remains source/transport-owned off Main. Add a test dispatcher assertion that calling importer from a Main-tagged test context performs the heavy normalization/hash block on the injected CPU dispatcher.

Exact publication order and ownership:

```text
acquire outside gate/Room
runtime binding + host clock stamp provenance
strict validate/cap/derive StoryId and cover revision outside transaction
enter CatalogMutationGate
snapshot ActiveStoryPins -> retentionProtectedStoryIds
call CatalogWritePort.publishDiscover(command, protectedIds) exactly once
    Room transaction: read previous bounded generation IDs for source/media
    bulk verify/upsert identity + summary (IdentityCollision fails closed)
    bulk write new discover_card generation (zero allowed)
    advance source_state
    remove obsolete generation
    for removed IDs only: keyed any-current-Discover reachability + protected-ID classification
    update bounded orphan-retention delta and evict bounded overflow
    commit
release gate
```

Newly reachable IDs are removed from orphan retention within the same bounded mutation. Generation increment is overflow-checked. No query in this path enumerates historical Story rows. When the same `StorySourceRef` legitimately appears in multiple semantic sections, `discover_card` preserves each section-local card row, but `story_source_summary` is derived exactly once before the transaction from the first membership in fixed semantic priority `POPULAR -> LATEST_UPDATES -> TOP_RATED`, then `itemPosition`; identity/contentType disagreement for the same ref fails validation. This prevents batch/upsert iteration order from becoming hidden summary authority and is not cross-source canonical fusion.

- [ ] **Step 6: Implement active pin semantics and post-pin cleanup**

`ActiveStoryPins` is a bounded active-demand set only (`<=2`). Registration under the gate completes before the feature exposes the Story route. Removal occurs under the gate after transition/destination demand ends: remove the pin, snapshot remaining protected IDs, then call `CatalogWritePort.releaseStoryDemand(ref, protectedIds, releasedAt)` exactly once. Storage performs the keyed reachability/orphan/delete/overflow work in one bounded transaction. The pin set is never persisted and terminal routes leave no pin entry.

- [ ] **Step 7: Run focused GREEN, then request broad verification**

Agent-owned:

```bash
./gradlew :catalog:runtime:testDebugUnitTest \
  --tests '*CatalogImporter*' \
  --tests '*CatalogMutationGate*' \
  --tests '*ActiveStoryPins*' \
  --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :catalog:storage:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.storage.DiscoverPublicationRetentionInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

Inspect lock scope for any source/image/suspending network work under the gate, typed collision behavior, cross-media reachability, post-pin cleanup, bulk semantics, bounded set construction, cancellation before commit, and terminal pin cleanup.

- [ ] **Step 8: Checkpoint, commit, stop**

Commit e.g. `feat: publish catalog snapshots with bounded pruning`. **Stop.**

---

# Task 5: Implement demand-owned runtime activation, bootstrap, detail single-flight, and no-source semantics

**Files:**
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogCapabilitySession.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogRuntimeFactory.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/source/CatalogSourceBinding.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/acquisition/CatalogAcquisitionExecutor.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/discover/DiscoverSession.kt`
- `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/story/StoryDetailSession.kt`
- focused runtime tests under matching `catalog/runtime/src/test/...` packages.

**Interfaces:**
- Activation constructs/opens storage asynchronously only after feature demand.
- `CatalogSourceBinding(catalogSourceKey, sourceVersion, acquisitionSource?, assetPolicy?)` is host-owned runtime/composition authority. Runtime stamps `AcquisitionProvenance` with an injected host wall-clock function at successful acquisition acceptance time.
- Exactly one active work entry per `DiscoverRefresh(source, mediaType)` and `StoryDetail(source, sourceStoryId)`.
- A known binding with `acquisitionSource == null` makes `Absent` explicit source-unavailable data and never bootstraps. A completely absent release binding maps directly to capability/source-unavailable without inventing a fake source key or opening a test seed.

- [ ] **Step 1: Write RED lifecycle/single-flight tests**

Prove zero storage open/acquisition before activation; repeated `Absent` emissions join one bootstrap; `Published(content)` and `Published(empty)` never bootstrap; a known binding with null source never bootstraps; an absent release binding exposes source-unavailable without seed substitution; switching media only bootstraps that exact `Absent` scope; terminal/cancelled work entries are removed. Host-stamped provenance must use binding key/version + injected clock and ignore any source payload fields with similar names. A fake source throwing a non-cancellation exception maps once to `CatalogFailure.Acquisition`; a fake storage open/read failure is exposed as `CatalogFailure.Storage`; `CancellationException` propagates unchanged and never becomes an issue.

- [ ] **Step 2: Write RED Story Detail tests**

Missing persisted detail triggers one keyed acquisition; cached detail does not; explicit retry may rerun after failed missing-detail acquisition; source identity comes from the validated `StorySourceRef` and must match the active `CatalogSourceBinding`, never global mutable current-source state. A mismatched ref/binding fails before source execution. `StoryDetailSession` performs exactly one semantic access-touch request when a Story demand session activates (not on every Flow emission/recomposition); repeated emissions in the same active session produce zero extra access writes.

- [ ] **Step 3: Run RED**

```bash
./gradlew :catalog:runtime:testDebugUnitTest --no-daemon
```

- [ ] **Step 4: Implement runtime factory/session, host binding, and keyed executor**

Do not create a global singleton. Feature composition owns one activity/capability session and passes one immutable Step 2 `CatalogSourceBinding?` from `runtime.source`; child runtime packages never import the runtime root facade. Runtime owns a tiny `SourceAssetPolicyProvider` backed by that binding; presentation can ask for policy by `CatalogSourceKey` but cannot mutate registration. Use an injectable `wallClockEpochMs: () -> Long` rather than adding a new dependency edge merely for time. `StoryDetailSession` sends the one-per-demand access touch through the storage port after pin registration; storage coalescing/transaction rules from Task 3 prevent reactive write loops. The runtime may retain an idle Room handle while the session lives but starts no observation/background work when quiescent.

- [ ] **Step 5: Implement bootstrap/source-unavailable state machine**

Only `DiscoverPersistenceState.Absent` under a known binding may call bootstrap, and only if that binding has an acquisition source. `Published(empty)` maps to completed empty state. `known binding + Absent + null source` maps to source-unavailable without execution. `binding == null` also maps to source-unavailable but does not fabricate a `CatalogSourceKey`; release wiring uses this path. Both unavailable paths expose the typed value `CatalogFailure.SourceUnavailable`; they do not throw an arbitrary construction/null error.

- [ ] **Step 6: Run focused GREEN, request broad gate, self-review, checkpoint, commit, stop**

Agent-owned:

```bash
./gradlew :catalog:runtime:testDebugUnitTest --tests '*CatalogCapabilitySession*' --tests '*DiscoverSession*' --tests '*StoryDetailSession*' --tests '*CatalogAcquisitionExecutor*' --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :catalog:runtime:testDebugUnitTest verifyArchitecture detekt --no-daemon
```

Review cancellation, host provenance authority, ref/binding mismatch rejection, asset-policy ownership, map cleanup, no repeated terminal work, no process-scope owner, and no Room creation before activation. Commit only after required evidence is reviewed, e.g. `feat: add demand-owned catalog runtime`. **Stop.**

---

# Task 6: Wire deterministic debug/benchmark sources through the real importer and prove release cleanliness

**Files:**
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogVariantBinding.kt` — defines `internal interface CatalogVariantBinding`; each concrete build type provides exactly one `internal object VariantCatalogBinding : CatalogVariantBinding`.
- `feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt`
- `feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/seed/LocalSeedCatalogSource.kt`
- `feature/catalog/src/debug/res/drawable-nodpi/...` real compressed cover fixtures
- `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt`
- `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/BenchmarkCatalogSource.kt`
- `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/BenchmarkCatalogFixture.kt`
- `feature/catalog/src/benchmarkRelease/res/drawable-nodpi/...`
- `feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt` — returns `CatalogSourceBinding? = null` and contains no seed data/source.
- Modify Android-library source-set mapping so `nonMinifiedRelease` explicitly reuses the `benchmarkRelease` Kotlin/resources/manifest directories, mirroring the accepted Step 1 app pattern; do not create a second duplicate fixture implementation.
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkLaunchStateActivity.kt` — after persisting `initial_setup_completed=true`, invoke `BenchmarkCatalogFixture.prepare(applicationContext)` before exposing the ready marker.
- build-surface verifier/script tests.

**Interfaces:**
- Local sources implement Task 1 `CatalogAcquisitionSource`; build-type `VariantCatalogBinding : CatalogVariantBinding` owns the host `CatalogSourceKey`, `sourceVersion`, optional source, and local/remote asset policy authority.
- UI/read ports never read fixture objects or resources directly.
- Local cover persistence stores logical IDs/versions and `CoverRevisionV1.local(...)`, never `R.drawable` integer values.

- [ ] **Step 1: Write RED variant-resolution/artifact tests**

Prove debug resolves a deterministic local binding; `benchmarkRelease` and `nonMinifiedRelease` resolve the same deterministic benchmark binding/source contract; release resolves `binding = null`; release classes/resources do not contain seed identifiers/assets; androidTest harness classes are absent from release. Add a Gradle/source-set fixture that fails if `nonMinifiedRelease` falls back to release or if benchmark fixture directories are packaged into release.

- [ ] **Step 2: Verify RED with focused build-logic tests**

Agent-owned:

```bash
./gradlew :build-logic:test --tests '*Step2BuildSurfaceVerifierTest*' --no-daemon
```

The shell artifact script is a required user-owned gate later in this task, not an agent polling target.

- [ ] **Step 3: Implement typed fixtures**

Create both media types with the full initial 5/9/5 membership caps, real compressed cover assets, and bounded detail payloads. Fixture source IDs include at least one Unicode/non-ASCII case already covered by StoryId vectors. Local artwork uses stable logical IDs/versions; Android resource integers exist only inside the build-type resolver. Populate only via `CatalogSourceBinding -> CatalogAcquisitionExecutor -> CatalogImporter -> Room`.

- [ ] **Step 4: Make benchmark/profile source mapping explicit**

`benchmarkRelease` owns the deterministic fixture implementation/resources and `nonMinifiedRelease` explicitly maps those exact directories through the Android-library DSL, matching Step 1's accepted app source-set pattern. Neither may fall back to release. Because `app/src/benchmarkRelease` calls across the module boundary, `BenchmarkCatalogFixture` is an intentionally **public but benchmark/profile-source-set-only** entry point; it must not be `internal` (which would not be visible to `:app`) and must not exist in main/release artifacts. `BenchmarkCatalogFixture.prepare(context)` creates a short-lived capability session, executes both enabled media snapshots through `CatalogAcquisitionExecutor -> CatalogImporter -> Room`, waits until publication is durably observable, quiesces/closes the session, and returns only then. The benchmark fixture activity calls this after launch-state setup so measured returning launch performs a persistence read, not bootstrap acquisition. Direct DAO insertion is forbidden for this product fixture.

- [ ] **Step 5: Run focused variant compiles, then request release/artifact gates**

Agent-owned:

```bash
./gradlew :feature:catalog:compileDebugKotlin :feature:catalog:compileReleaseKotlin :feature:catalog:compileBenchmarkReleaseKotlin :feature:catalog:compileNonMinifiedReleaseKotlin --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :feature:catalog:assembleDebug :feature:catalog:assembleRelease :feature:catalog:assembleBenchmarkRelease :feature:catalog:assembleNonMinifiedRelease verifyArchitecture --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

Inspect resolved source inputs/AAR contents, not just filenames. Task 6 does not close until release cleanliness and benchmark source resolution are reviewed.

- [ ] **Step 6: Self-review, checkpoint, commit, stop**

Inspect AAR/APK inputs and source sets, not only source filenames. Commit e.g. `test: add deterministic catalog acquisition fixtures`. **Stop.**

---

# Task 7: Replace static returning Home with demand-activated Catalog feature entry

**Files:**
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogEntryPoint.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogComposition.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogScreen.kt`
- Modify `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt`
- Delete `app/src/main/kotlin/app/openstory/startup/ui/HomeShell.kt` after all callers/tests move.
- Modify `app/src/test/kotlin/app/openstory/AppShellContractTest.kt` and startup trace/source contract tests.
- Add `app/src/androidTest/kotlin/app/openstory/startup/CatalogLaunchHandoffTest.kt` for real Ready/FirstRun handoff behavior.

**Interfaces:**
- `:app` calls one public/narrow `CatalogEntryPoint` Composable/function only after `Ready`.
- Feature composition creates `CatalogCapabilitySession` lazily.
- First frame remains shell-owned and Catalog-independent.

- [ ] **Step 1: Write RED app-shell boundary + instrumentation tests**

Source/unit contract tests assert `:app` imports only `CatalogEntryPoint` and contains no Room/runtime/storage/image/source wiring. Instrumentation asserts `Ready` displays the feature-owned Discover root, FirstRun completion persists before feature activation, and before `Ready` the feature/runtime counters record zero storage/image/acquisition work.

- [ ] **Step 2: Verify RED with focused host checks**

Agent-owned:

```bash
./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*' --tests '*StartupTraceContractTest*' --no-daemon
./gradlew :app:compileDebugAndroidTestKotlin :feature:catalog:compileDebugKotlin --no-daemon
```

The connected handoff behavior is user-owned after implementation.

- [ ] **Step 3: Implement narrow no-argument public entry/composition**

Freeze the public app-facing API to `@Composable fun CatalogEntryPoint()` with no runtime/storage/source/context parameter. `CatalogComposition` obtains `applicationContext` internally from Compose and resolves the build-type `VariantCatalogBinding : CatalogVariantBinding`; production dependency construction remains feature-owned. Internal production composition helpers may accept explicit factories for modularity, but no test-only service locator seam is added to `:app` or global state.

- [ ] **Step 4: Preserve startup traces and add catalog activation trace**

Keep all six Step 1 labels unchanged. Create Android-free `CatalogTrace` as the single Step 2 trace-name authority and freeze all seven R2.1/plan labels there now, plus `fun interface CatalogTraceSink { fun mark(name: String) }`. `AndroidCatalogTraceSink` in the feature module is the only production adapter to `android.os.Trace`; runtime host tests inject a recording/no-op sink and therefore do not require Robolectric. Task 7 emits only `HikariV2:catalog-activation-start` from the feature activation boundary, while later tasks add the matching event marks through the injected sink. Do not define the same trace string ad hoc in feature code and do not repurpose historical Step 1 labels.

- [ ] **Step 5: Run focused GREEN, then request connected/foundation gates**

Agent-owned:

```bash
./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*' --tests '*StartupTraceContractTest*' --no-daemon
./gradlew :app:compileDebugAndroidTestKotlin :feature:catalog:compileDebugKotlin --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.startup.CatalogLaunchHandoffTest \
  --no-daemon
./gradlew :app:verifyFoundation verifyArchitecture --no-daemon
```

- [ ] **Step 6: Self-review, checkpoint, commit, stop**

Review direct app imports/dependencies and trace timing. Commit e.g. `feat: hand ready launch to catalog entry`. **Stop.**

---

# Task 8: Implement bounded multi-section Discover presentation and media selection

**Files:**
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/state/CatalogIssueUi.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverUiState.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverViewModel.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverScreen.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverSections.kt`
- focused reducer tests under `feature/catalog/src/test/.../discover/`.
- `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/discover/DiscoverScreenInstrumentedTest.kt` for actual Compose tree/scroll/accessibility behavior. Golden/artifact screenshot capture is deferred to Task 13 using standard Android/Compose test APIs; Task 8 does not re-admit Roborazzi.

**Interfaces:**
- Consumes one `DiscoverPersistenceState` observer for selected `(source, mediaType)` and runtime refresh/bootstrap status.
- Emits stable `StorySourceRef` + optional `CoverAssetKey` on selection.
- Does not invoke Story Detail acquisition from card rendering/media selection.

- [ ] **Step 1: Write RED ViewModel reducer tests**

Cover mappings:

```text
Absent + bootstrap       -> NoContentLoading
Absent + failed/no source-> NoContentFailure
Published(empty)         -> Empty
Published(content)       -> Content
Content + refresh active -> retained Content + refreshing
Content + refresh failed -> retained Content + issue
```

Also prove both media options enabled, default Manga in a newly created ViewModel after process death, selection retained across ordinary configuration recreation **only through ViewModel lifetime**, collector replacement only for selected media scope, and no card/detail N+1 calls. Do not use `SavedStateHandle`, DataStore, or another durable/saveable store for media selection in Step 2, because R2.1 requires process-death default = Manga. Add safe failure mapping tests: `SourceUnavailable -> SOURCE_UNAVAILABLE`, `Validation/IdentityCollision -> INVALID_SOURCE_DATA`, `Acquisition -> ACQUISITION_FAILED`, `Storage -> STORAGE_FAILED`; raw exception messages, URLs, host lists, and plugin payload strings never enter `CatalogIssueUi`. A storage/read failure with no usable snapshot becomes `NoContentFailure(STORAGE_FAILED)`; if a usable content snapshot is already retained in the ViewModel, a later refresh/acquisition failure is an issue layered on that content rather than destructive loading.

- [ ] **Step 2: Write RED Compose instrumentation tests**

Under `src/androidTest`, prove one vertical scroll owner, semantic order, section omission when one section empty, stable semantic keys/tags, geometry-shaped skeletons, accessibility semantics, both media controls enabled, and <=19 memberships supplied to the UI from policy. Task 8 uses placeholders/local visual slots only; actual cover pipeline behavior starts in Task 10.

- [ ] **Step 3: Verify RED host + instrumentation compile**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Expected RED is either the focused reducer failure or instrumented test compile failure before UI implementation; do not add Robolectric.

- [ ] **Step 4: Implement minimal ViewModel/state and safe issue mapping**

Do not recreate the V1 settlement graph. One selected-media observer + runtime bootstrap/refresh state is the only Discover readiness path. `CatalogIssueUi` is exactly a small feature-state value (`kind: CatalogIssueKind`, `retryable: Boolean`) where `CatalogIssueKind = SOURCE_UNAVAILABLE | INVALID_SOURCE_DATA | ACQUISITION_FAILED | STORAGE_FAILED | ARTWORK_FAILED | INTERNAL_FAILURE`; it is derived only from `CatalogFailure` and has no raw technical message field. Explicit retryability is true for source execution/transient storage failures and false for source-unavailable/validation/identity/invariant failures unless a later reviewed contract says otherwise. Cancellation produces no issue.

- [ ] **Step 5: Implement UI**

Use one `LazyColumn`. Popular may be horizontal hero/pager; Latest bounded compact composition; Top Rated ranked rows. No nested vertical list/grid and no full-feed eager image requests.

- [ ] **Step 6: Run focused GREEN, then request Compose/broad gates**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModel*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.discover.DiscoverScreenInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

- [ ] **Step 7: Self-review, checkpoint, commit, stop**

Inspect recomposition-state width, list keys, card caps, empty semantics, V1 debt avoidance, and absence of Search/chapter code. Commit e.g. `feat: add v2 discover surface`. **Stop.**

---

# Task 9: Implement Story Detail route, pin lifecycle, keyed observation, and atomic enrichment flow

**Files:**
- Reuse `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/state/CatalogIssueUi.kt` from Task 8.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogRoute.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailUiState.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailViewModel.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/story/StoryDetailScreen.kt`
- focused unit tests under `feature/catalog/src/test/.../story/` and route serialization tests.
- `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/story/StoryRouteRestorationInstrumentedTest.kt`.

**Interfaces:**
- Route payload is only `StorySourceRef + optional CoverAssetKey`.
- Feature registers active pin before route becomes visible and releases after transition/destination demand ends.
- Story Detail metadata-only; no Chapter/Reader action.

- [ ] **Step 1: Write RED route/restoration tests**

Prove saved route contains only primitive/string representation of `StorySourceRef + optional CoverAssetKey`; no DTO/entity/bitmap/list. Restoring the route reconstructs `StorySourceRef`, which re-validates derived `StoryId`; a malformed/mismatched saved triple **must not crash the Activity and must not start Story acquisition**. Fail closed by discarding that invalid Story route and returning to the safe Discover destination (with no raw invalid payload exposed to UI). On process recreation while a valid Story is the restored destination, session activation registers the Story pin under `CatalogMutationGate` **before** any resumed publication/pruning can run and before Story route content is exposed.

- [ ] **Step 2: Write RED Story state tests**

Summary/cover can render while detail missing; missing detail triggers one keyed runtime acquisition; detail failure preserves summary/cached detail and exposes inline retry; metadata failure does not erase cover state. Typed `CatalogFailure` values map through the same safe `CatalogIssueUi` mapper as Discover; `CancellationException` never produces inline failure UI.

- [ ] **Step 3: Verify RED host + restoration-test compile**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*StoryDetailViewModel*' --tests '*CatalogRoute*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

- [ ] **Step 4: Implement validated saved route/ViewModel/UI**

Use a small `CatalogRoute` saver/serialization that stores only the validated identity/asset-key strings needed for recreation. Do not persist the media-selection toggle here. When restoring Story, pin-first activation precedes detail observation/acquisition. Keep state domains separable:

```kotlin
data class StoryDetailUiState(
    val ref: StorySourceRef,
    val summary: StorySummaryUi?,
    val detail: StoryDetailUi?,
    val detailLoading: Boolean,
    val issue: CatalogIssueUi?,
)
```

Cover readiness belongs to Task 10 image state and must not force whole-screen loading.

- [ ] **Step 5: Implement Back continuity**

Returning to Discover retains feature route state and the exact same `LazyListState` instance while the activity/session survives. Own that `LazyListState` in `CatalogScreen` **outside** the `Discover vs Story` route branch and pass it into `DiscoverScreen`; do not create it inside a Discover subtree that is disposed when Story becomes active. Add a route test asserting a non-zero firstVisibleItemIndex/offset survives Discover -> Story -> Back. Process death does not promise scroll restoration; it reconstructs Discover from persistence.

- [ ] **Step 6: Run focused GREEN, then request restoration/broad gates + self-review**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*StoryDetailViewModel*' --tests '*CatalogRoute*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.story.StoryRouteRestorationInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

Review pin lifetime/order, <=4-query storage contract consumption, no Library/progress/reconciliation dependency fan-in, no chapters/reader, and no bitmap navigation. Commit only after required evidence, e.g. `feat: add keyed story detail journey`. **Stop.**

---

# Task 10: Add local visual fast path, stable asset identity, bounded caches, viewport demand, and continuity

**Files:**
- Consume the already-frozen Task 1 cover contracts without reopening them.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CatalogImageLoader.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverFetcher.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverEncodedDiskCache.kt` — capability-private explicit encoded-byte cache keyed only by `CoverAssetKey.stableCacheKey`, backed by the one Coil `DiskCache` instance capped at 128 MiB; remote custom fetches read/write it explicitly rather than assuming Coil will persist arbitrary custom-fetcher bytes.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverRequest.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CatalogImageMemoryPressureController.kt` — registers only after image-session creation and forwards real Android memory-pressure signals to the decoded-cache owner without initializing the loader pre-demand.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/LocalCoverAssetResolver.kt` — feature-private port from logical asset ID/version to an Android resource handle; no raw resource integer enters domain/storage.
- build-type `VariantLocalCoverAssets` implementations in `src/debug`, `src/benchmarkRelease`/shared profile sources, and `src/release`; release returns an empty/unavailable resolver and packages no seed assets.
- `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/assets/LocalCoverContinuityInstrumentedTest.kt`
- Discover/Story cover Composable integration in the existing Task 8/9 screen files.

**Interfaces:**
- `CoverAssetKey(storyId, CoverRevision)` is identical across Discover/Story for unchanged artwork.
- Local persistence uses logical asset ID/version; resolver maps to Android resource only at runtime.
- Initial concrete limits are decoded memory **32 MiB**, encoded disk **128 MiB**, active cover jobs **8**, manual offscreen prefetch **0**. These are configuration assertions, not framework defaults. `CoverEncodedDiskCache` is the authoritative encoded disk layer for the custom `CoverFetcher`; memory-hit requests are satisfied before the fetcher, disk-hit requests never invoke `RemoteCoverTransport`, and a validated remote miss writes at most the bounded encoded payload under `CoverAssetKey.stableCacheKey`. `CatalogImageSession : AutoCloseable` owns loader/cache/callback registration; STOP may keep it dormant, final session destruction closes/unregisters it exactly once.

- [ ] **Step 1: Write RED local identity/cache instrumentation tests**

Task 1 already proves revision derivation. Here prove the Android pipeline uses that key unchanged: same revision => same request/cache key, revision change => new key, local raw resource int is never persisted, verified memory hit performs **zero `CoverEncodedDiskCache.read` and zero resolver/transport work**, and repeated navigation stays within the configured 32 MiB decoded-memory ceiling. Add memory-pressure tests with a fake decoded-cache owner: no callback/loader exists before first cover demand; after image-session creation, `onLowMemory()` and real memory-pressure `onTrimMemory` levels clear decoded cover memory, while `TRIM_MEMORY_UI_HIDDEN` alone does not destroy the warm cache; disk cache is not cleared merely by a decoded-memory trim; closing the image session unregisters callbacks and is idempotent. Add focused cache-adapter tests that the one disk cache is configured to <=128 MiB, every remote entry key is exactly `CoverAssetKey.stableCacheKey`, a disk snapshot is closed after consumption, replacement is atomic, and failed/oversized/incomplete remote payloads are never committed. Do **not** require bundled Android resources to be copied into disk cache; deterministic disk-hit/no-transport behavior is exercised with the controlled remote/encoded path in Tasks 11/14.

- [ ] **Step 2: Write RED demand/cancellation/failure tests**

Initial manual prefetch is frozen to zero, so only composed/visible cards and the selected Story transition may own requests. Assert active jobs never exceed 8, offscreen disposal cancels/lowers unnecessary work, selected-Story continuity may remain prioritized during transition, and one stable failure for the same asset key does not create an automatic recomposition retry loop. Task 14 may add at most one viewport of manual prefetch only if measured evidence requires it; if it does, prefetch uses lower-priority/cancellable capacity and a focused test must prove prefetch cannot occupy all 8 slots while a newly visible/selected-Story request waits. Local resolver/decode failures surface as `CatalogFailure.Artwork` to the requesting cover state; they never collapse Story/Discover metadata into whole-screen failure and never expose resource IDs/technical exceptions as UI text.

- [ ] **Step 3: Verify RED instrumentation compile**

Agent-owned:

```bash
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Actual Coil/Android cache behavior is connected evidence; do not add Robolectric.

- [ ] **Step 4: Implement lazy capability-private image loader**

Construct it only after first cover demand, not feature activation by itself. Configure Coil with explicit **32 MiB decoded-memory** cache and create exactly one **128 MiB Coil `DiskCache`** owned by the capability session, plus one capability-owned semaphore/dispatcher ceiling of **8** cover jobs. Wrap these in `CatalogImageSession`; register `CatalogImageMemoryPressureController` only after that session exists. The controller clears only the decoded-memory owner for `onLowMemory()` and memory-pressure trim levels, ignores UI-hidden as a pressure signal by itself, never creates the loader while handling a pre-demand callback, and unregisters on `CatalogImageSession.close()`. Pass that same disk-cache instance to `CoverEncodedDiskCache`; the custom `CoverFetcher` explicitly checks/commits encoded remote bytes through this wrapper, so disk continuity does not depend on undocumented automatic caching for a custom fetcher. The exact `CoverAssetKey.stableCacheKey` frozen in Task 1 is supplied as the memory key and encoded-disk key; UI model object identity, data-class `toString()`, and raw resource IDs are never trusted as cache keys. A memory miss + disk hit returns the disk snapshot as the decode source and does not call remote transport; a full miss validates/preflights the bounded encoded body before atomically committing it to disk and decoding to target size.

- [ ] **Step 5: Integrate stable geometry/continuity**

All cover Composables reserve final aspect/size before bytes arrive; Story requests the same asset key as Discover; Back never invalidates solely due to navigation.

- [ ] **Step 6: Run focused compile/unit checks, then request connected/broad gates**

Agent-owned:

```bash
./gradlew :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
./gradlew :app:testDebugUnitTest --tests '*AppShellContractTest*' --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.assets.LocalCoverContinuityInstrumentedTest \
  --no-daemon
./gradlew verifyArchitecture detekt --no-daemon
```

- [ ] **Step 7: Self-review, checkpoint, commit, stop**

Review exact cache/job ceilings, zero manual prefetch, stable failure behavior, Android memory-pressure behavior, callback registration/unregistration, cache ownership as one capability session, idempotent image-session close, no bitmap copy/navigation, and no loader initialized before first cover demand. Commit e.g. `feat: add bounded catalog cover continuity`. **Stop.**

---

# Task 11: Add remote artwork security, redirect policy, encoded/decode preflight bounds, and source-policy recovery

**Files:**
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/RemoteCoverPolicy.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/RemoteCoverTransport.kt` — small injectable encoded-byte transport interface.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/CoverImagePreflight.kt`
- JVM policy/transport tests under `feature/catalog/src/test/.../assets/` using a deterministic in-memory `RecordingRemoteCoverTransport`; no HTTP client is admitted.
- `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/assets/CoverImagePreflightInstrumentedTest.kt` for Android bounds/decode behavior.
- No test manifest network permission is required for these Task 11 tests: JVM policy tests use only deterministic in-memory transport responses and Android preflight uses local encoded fixtures. Task 15 controlled plugin transport is also in-memory by default.

**Interfaces:**
- Presentation receives typed `CoverLocator`, never a raw plugin URL/HTTP client.
- `CatalogSourceKey` selects host policy from host-owned `SourceAssetPolicyProvider`.
- The feature-private transport seam is exact and contains no HTTP-library type:

```kotlin
data class RemoteCoverTransportRequest(
    val uri: RemoteHttpsUriV1,
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val callTimeoutMillis: Long,
)

interface RemoteCoverTransportResponse : Closeable {
    val statusCode: Int
    val redirectLocation: String?
    val contentType: String?
    val contentLength: Long?
    val body: InputStream
}

fun interface RemoteCoverTransport {
    suspend fun execute(request: RemoteCoverTransportRequest): RemoteCoverTransportResponse
}
```

`CoverFetcher` resolves/revalidates redirects and owns the 8 MiB streaming read/preflight/disk-commit sequence; transport only executes one already-validated hop. Every response is closed exactly once on all terminal paths.

- [ ] **Step 1: Write RED JVM policy/transport tests**

Reject HTTP, `file:`, arbitrary `content:`, userinfo, URI fragment, undeclared host, undeclared redirect target, invalid/too-long URI, bad content type, timeout, and encoded body >8 MiB. Reuse the exact Task 1 `RemoteHttpsUriV1`/source-host canonicalization; test absolute and relative redirect targets, redirect loops, and max-hop exhaustion. Preserve complete raw path/query identity in `CoverRevisionV1.remoteUri`. `RecordingRemoteCoverTransport` must prove every redirect hop is revalidated **before** the next injected request is issued. Follow redirects only for 301/302/303/307/308; any other 3xx is a typed artwork transport failure. Assert each terminal policy/resource case maps to a specific `CatalogFailure.Artwork` reason and that caller/session cancellation is rethrown before mapping. Model operation-owned timeout separately (for example `withTimeoutOrNull`/deadline result -> `CatalogFailure.Artwork(TIMEOUT)`) so `TimeoutCancellationException` is never confused with external cancellation. For body limits, treat missing/negative `Content-Length` as unknown, reject declared `Content-Length > 8 MiB` before read, and enforce a streaming hard cap when length is absent/wrong. Parse `Content-Type` case-insensitively after stripping parameters and admit only the decoder paths explicitly tested for JPEG/PNG/WebP. Every transport response is closed exactly once. Stream/spool through a bounded temporary/cache sink rather than materializing an additional full 8 MiB byte array per concurrent request; cancellation/failure deletes transient partial files before returning. Add a negative build-surface test that production Step 2 resolves no OkHttp/Coil-network artifact and contains no concrete HTTP adapter.

- [ ] **Step 2: Write RED Android image-bomb/preflight tests**

Local encoded JPEG/PNG/WebP fixtures must reject width >8,192; height >8,192; pixel surface >32,000,000 before original-size allocation. Include a malformed/pathological header fixture and compute pixel surface with overflow-safe `Long` arithmetic after individual dimension validation. The admitted remote decoder path is a **static bitmap** path: animated/multi-frame WebP (and any other animated container) is rejected unless the chosen bounds probe can prove it will be decoded as one bounded static frame; Step 2 does not admit an unbounded animated-cover decoder. Declared `Content-Type` never bypasses byte/header/decoder validation. The admitted decoder path must prove bounds-only probing before full decode and target-size sampling for the actual requested surface.

- [ ] **Step 3: Run RED JVM policy tests + instrumentation compile**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

- [ ] **Step 4: Implement policy/transport/preflight**

Freeze the **transport-policy contract** to `maxRedirects = 5`, `connectTimeout = 10s`, `readTimeout = 20s`, and `callTimeout = 20s`. Apply any operation-owned coroutine deadline with `withTimeoutOrNull` (or an equivalent dedicated timeout result) and map only that owned deadline to `CatalogFailure.Artwork(TIMEOUT)`; never catch/map a broad `CancellationException`, because user/session cancellation must propagate unchanged. `RemoteCoverTransport` is the only narrow injected byte-transport port used by the fetcher. Step 2 deliberately provides **no production HTTP implementation** of that port: debug/benchmark local fixtures do not need one, release has no remote source, and Task 15 injects deterministic controlled transport. The fetch/policy layer—not the transport—owns redirect state: resolve `Location`, run `RemoteHttpsUriV1` + source-host policy on every hop, then issue the next transport request. The transport contract receives the finite timeout policy so any later admitted HTTP adapter cannot ignore it. Read/buffer with the hard 8 MiB encoded-image ceiling and close every response/body on success, redirect, rejection, timeout, and cancellation. Admit only `image/jpeg`, `image/png`, and `image/webp` initially because the Android bounds-only path is explicitly tested for them; reject other untrusted remote formats until a separate design/checkpoint admits a safe preflight path. Inspect dimensions before full decode and reject any path that cannot be safely preflighted. Benchmark/androidTest code injects an in-memory implementation of this same interface; it must not add a second image-fetch architecture. A later production remote-source admission may add an HTTP adapter + `INTERNET` only with a separate build/startup/network proof.

- [ ] **Step 5: Implement source-policy recovery after recreation**

Persisted remote locator remains bound to `CatalogSourceKey`; feature/runtime resolves policy from host-owned source registration, never from payload-supplied host claims. Add a recreation test that rebuilds feature/runtime from only the persisted locator/ref + current host-owned binding, successfully recovers the matching policy, and fails closed with `POLICY_REJECTED` when no matching host policy exists; no remembered raw URL policy object is required.

- [ ] **Step 6: Run focused GREEN, then request Android preflight/broad gates**

Agent-owned:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*RemoteCover*' --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Required user-owned gate unless explicitly delegated back:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.assets.CoverImagePreflightInstrumentedTest \
  --no-daemon
./gradlew :app:verifyFoundation verifyArchitecture detekt --no-daemon
```

Main/release manifest must still have no Step 2 `INTERNET` permission.

- [ ] **Step 7: Self-review, checkpoint, commit, stop**

Review redirect loops/count, DNS/host comparison normalization, relative redirect resolution, response close/cancellation, preflight allocation, source-policy authority, raw path/query-preserving revision semantics, and dependency output proving **both OkHttp and `coil-network-okhttp` are absent from production Step 2** so no alternate raw-URL network fetcher bypass exists. Commit e.g. `feat: bound and secure catalog artwork`. **Stop.**

---

# Task 12: Harden manual refresh, failure retention, lifecycle quiescence, and repeated activation

**Files:**
- Create/modify: `catalog/runtime/src/test/kotlin/app/openstory/catalog/runtime/discover/DiscoverRefreshOwnershipTest.kt`
- Create/modify: `catalog/runtime/src/test/kotlin/app/openstory/catalog/runtime/CatalogQuiescenceTest.kt`
- Create/modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/feature/discover/DiscoverRefreshStateTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/lifecycle/CatalogLifecycleInstrumentedTest.kt`
- Modify only as required by failing tests: `DiscoverSession.kt`, `StoryDetailSession.kt`, `CatalogCapabilitySession.kt`, `DiscoverViewModel.kt`, `StoryDetailViewModel.kt`, `CatalogScreen.kt`/feature collection boundary.

**Interfaces:**
- Manual refresh for one `(CatalogSourceKey, mediaType)` joins one existing work owner; no second acquisition is launched.
- Backgrounding starts no substitute WorkManager/scheduler/periodic owner.
- Runtime cancellation/quiescence is testable without Android UI; actual lifecycle-aware Compose collection/disposal is connected Android evidence.
- Existing bounded Room handle/image caches may remain dormant; quiescence means no observation/work, not forced resource churn. Final activity/capability-session destruction is different: the feature composition closes `CatalogImageSession` and runtime `CatalogCapabilitySession`; runtime then closes its owned `RoomCatalogStore` exactly once.

- [ ] **Step 1: Write RED runtime refresh/failure/cancellation tests**

Prove:

```text
Published(content) + refresh failure -> same content + scoped issue
Published(empty) + refresh failure   -> Empty + scoped issue, never Absent
N concurrent refresh calls for same key -> exactly one source execution
cancellation before publication      -> no partial generation/detail write
CancellationException                 -> propagates; is not mapped to user failure
non-cancel source/storage failures     -> typed `CatalogFailure` -> safe scoped `CatalogIssueUi`
terminal success/failure/cancel       -> active single-flight entry removed
```

- [ ] **Step 2: Write RED runtime quiescence/repeated-activation tests**

Using recording read/source/image-demand ports rather than Android lifecycle objects, prove session stop/quiesce cancels active screen-owned acquisition/observation demand, starts no refresh/background continuation, and repeated Discover -> Story -> Discover activation leaves `activeWorkCount == 0` and `activeStoryPinCount == 0` after terminal demand ends. Also prove quiesce does not require closing/reopening the bounded Room handle on every destination transition, while final `CatalogCapabilitySession.close()` cancels remaining child jobs and closes the owned `RoomCatalogStore` exactly once.

- [ ] **Step 3: Write RED connected lifecycle test**

`CatalogLifecycleInstrumentedTest` drives the real feature through `STARTED -> CREATED/STOPPED -> STARTED` using `ActivityScenario`/Compose test APIs. It must prove collectors cease while not visible, composed cover demand is disposed/cancelled, no automatic refresh starts while stopped, and resumed rendering reuses persisted state without a duplicate bootstrap. A terminal Activity destruction case additionally proves feature composition closes/unregisters the image session and closes the runtime/storage session once, whereas a STOP/START cycle does not churn those owned resources. This is `androidTest`; do not admit Robolectric.

- [ ] **Step 4: Run focused RED**

```bash
./gradlew :catalog:runtime:testDebugUnitTest \
  --tests '*DiscoverRefreshOwnershipTest*' \
  --tests '*CatalogQuiescenceTest*' \
  --no-daemon
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverRefreshStateTest*' \
  --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Expected: focused failures/compile gaps before lifecycle hardening exists.

- [ ] **Step 5: Implement minimal hardening**

Use lifecycle-aware collection (`collectAsStateWithLifecycle`/equivalent at the feature surface), session-owned child jobs, and explicit cancellation of viewport/detail demand. Keep a single feature composition owner for final teardown: STOP calls quiescence only; terminal composition/activity session disposal closes image session then runtime session/storage, each idempotently. Keep retry authority in runtime; do not put `LaunchedEffect`-driven source execution in UI. No WorkManager, AndroidX Startup, process-scope `GlobalScope`, or background retry owner is introduced.

- [ ] **Step 6: Run focused agent-owned GREEN checks and static scans**

```bash
./gradlew :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest --no-daemon
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
rg -n "WorkManager|androidx\.work|androidx\.startup|GlobalScope" app/src/main catalog/runtime/src/main feature/catalog/src/main
```

Expected: focused unit tests pass and the static scan finds no unapproved owner/surface.

- [ ] **Step 7: Hand off required lifecycle/broad gate**

User-owned by default unless explicitly delegated back:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.lifecycle.CatalogLifecycleInstrumentedTest \
  --no-daemon
./gradlew :app:verifyFoundation verifyArchitecture detekt --no-daemon
```

Task 12 stays `READY FOR USER VERIFICATION` until the connected lifecycle result and broad policy result are reviewed.

- [ ] **Step 8: Self-review, checkpoint, commit, stop**

Review retained-content semantics, retry authority, cancellation propagation, lifecycle collection, terminal map/pin cleanup, STOP-vs-DESTROY resource semantics, Room/image close ownership, dormant-resource policy, and absence of a background continuation. Only after required evidence is green, commit e.g. `fix: harden catalog lifecycle and refresh ownership`. **Stop.**

---

# Task 13: Close storage connected verification and cross-module correctness acceptance before performance work

**Files:**
- Finish all Room behavior tests under `catalog/storage/src/androidTest/kotlin/app/openstory/catalog/storage/...` from Tasks 2-4.
- Finish all feature/app connected tests created in Tasks 7-12.
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/evidence/CatalogScreenshotEvidenceTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/evidence/ScreenshotEvidence.kt`
- Modify: `scripts/verify-fast.sh`
- Modify: `scripts/verify.sh`
- Modify: `docs/internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md`
- No new production feature scope.

**Interfaces:**
- Produces the complete deterministic **local correctness** gate required before performance/profile work and before the plugin gate.
- Connected/device and full-repository commands are user-owned by default; Task 13 is explicitly a verification checkpoint and does not advance until evidence is returned/reviewed.

- [ ] **Step 1: Finish the API 26/API 37 Room matrix**

The same focused Room test classes must cover fresh DB creation, file-backed close/reopen, durable `Published(empty)`, atomic Discover/detail rollback, identity uniqueness/collision, index/query shape, access-touch non-loop behavior, bounded orphan retention, and the deterministic pin/prune race. Do not create API-specific production branches.

- [ ] **Step 2: Add compact + wider UI screenshot/evidence coverage without Roborazzi**

`CatalogScreenshotEvidenceTest` must render at least:

```text
Discover: loading geometry
Discover: Manga with Popular/Latest/Top Rated
Discover: Light Novel with Popular/Latest/Top Rated
Discover: retained content + refresh issue
Story Detail: summary/cover + detail loading
Story Detail: complete metadata
Story Detail: metadata issue while cover remains usable
```

Use Compose test semantics plus standard Android screenshot capture only. `ScreenshotEvidence` captures the activity/root using Android/Compose test APIs and writes PNG evidence under the test app's external-files/cache evidence directory with deterministic names; the checkpoint records the exact `adb pull`/test artifact path. Run once on a compact phone configuration and once on a wider configuration. This is evidence, not a new golden-diff framework, and does not re-admit Roborazzi.

- [ ] **Step 3: Run focused agent-owned host checks**

```bash
./gradlew :catalog:domain:test :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew :catalog:storage:compileDebugAndroidTestKotlin :feature:catalog:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon
```

These are compile/focused checks only. They do not substitute for connected Room/Compose evidence.

- [ ] **Step 4: Hand off the exact API/device gates**

Run against one API 26 target and one API 37 target with explicit serial selection so “connected” does not accidentally run on an unintended device:

```bash
ANDROID_SERIAL=<api26-serial> ./gradlew :catalog:storage:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon
ANDROID_SERIAL=<api37-serial> ./gradlew :catalog:storage:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon
```

For screenshot evidence, additionally run the screenshot test once on the accepted compact target and once on a wider target/configuration and record `device`, `API`, `resolution/dp class`, command, result, and artifact location. User supplies the real serial values; the plan does not invent device names.

- [ ] **Step 5: Evolve and hand off the full repository verification**

Update `verify-fast.sh`/`verify.sh` to include the admitted Step 2 modules/assemblies while preserving retained/quarantine checks. User-owned by default unless explicitly delegated back:

```bash
./gradlew verifyArchitecture :app:verifyFoundation detekt --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

Task 13 remains `READY FOR USER VERIFICATION` until API 26, API 37, architecture, and full-repository results are reviewed.

- [ ] **Step 6: Self-review local acceptance, checkpoint, commit, stop**

Map every R2.1 criterion that can be proven without performance/profile/plugin execution to exact test/source/artifact evidence. Performance and plugin criteria stay explicitly `OPEN — OWNED BY TASK 14/15`, never silently “pass”. Only after the local gate is green, commit e.g. `test: close step2 local catalog correctness`. **Stop.**

---

# Task 14: Add deterministic performance/aging benchmarks, optimize from evidence, regenerate profiles, and compare startup

**Files:**
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt`
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkLaunchStateActivity.kt`
- Modify/create: `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/BenchmarkCatalogFixture.kt`
- Create: `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/BenchmarkCoverFixture.kt`
- Create when direct pathological storage setup is needed: `catalog/runtime/src/benchmarkRelease/kotlin/app/openstory/catalog/runtime/fixture/BenchmarkAgedCatalogFixture.kt`
- Modify: `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/trace/CatalogTrace.kt` created in Task 7; add event calls/evidence only, never a second trace-name authority.
- Create: `docs/internal/v2/catalog-step2-performance-baseline-2026-09-08.md`
- Modify generated profile files only after final deterministic journey is stable:
  - `app/src/release/generated/baselineProfiles/baseline-prof.txt`
  - `app/src/release/generated/baselineProfiles/startup-prof.txt`
- Production/UI/query files may change only when a measured gate demonstrates an in-scope issue; each such change reruns its focused owning tests first.

**Interfaces:**
- Product/current-state fixtures always enter through `CatalogSourceBinding -> CatalogAcquisitionExecutor -> CatalogImporter -> Room`.
- Exception for **pathological unrelated historical rows only**: `BenchmarkAgedCatalogFixture` may use benchmark-only storage setup to create thousands of irrelevant rows when the measured subject is query/pruning shape rather than import semantics. Current Discover generation, active Story state, refresh publication, and any assertion about importer behavior still use the real importer path. No benchmark helper enters `main`/`release`.
- Final startup comparison happens only after the Step 2 baseline/startup profile is regenerated from the final deterministic journey.
- Macrobenchmark/profile/device execution is user-owned by default under `AGENTS.md`; Task 14 remains open while awaiting those results.

- [ ] **Step 1: Add and contract-test Catalog traces without renaming Step 1 traces**

Freeze exactly:

```text
HikariV2:catalog-activation-start
HikariV2:catalog-storage-ready
HikariV2:discover-first-snapshot
HikariV2:discover-first-cover
HikariV2:discover-content-ready
HikariV2:story-detail-requested
HikariV2:story-detail-content-ready
```

`CatalogTrace` is the single string authority and remains Android-free; runtime code emits through injected `CatalogTraceSink`, while the production feature composition supplies `AndroidCatalogTraceSink`. Trace calls annotate existing work only; they must not trigger initialization or acquire locks/I/O. Host tests use recording/no-op sinks. Freeze metric semantics too: `catalog-storage-ready - catalog-activation-start` is Catalog activation/storage-open latency; `discover-first-snapshot - catalog-activation-start` is persisted first-snapshot latency; `discover-first-cover - catalog-activation-start` is first useful real-cover latency; `discover-content-ready` fires when the first non-empty semantic section for the selected media has its complete bounded card data composed in final geometry (it does **not** wait for every cover, which is measured separately). These definitions cannot drift between benchmark runs.

- [ ] **Step 2: Add deterministic benchmark preparation for every required scenario**

Scenarios:

```text
fresh-install FirstRun path kept comparable to Step 1 (no pre-Ready Catalog activation)
normal both-media 5/9/5 through importer
aged thousands-unrelated rows with bounded active generation
Published(empty) persisted + close/reopen + returning Discover with acquisition/transport counters at zero
repeated successful refresh generations
bounded orphan overflow/pruning
pin-vs-prune race
warm memory-hit cover
cold-memory / deterministic disk-hit cover
repeated Discover <-> Story navigation
long-browse stability: 20 deterministic scroll-bottom -> scroll-top -> Story open/back cycles per benchmark iteration
oversized detail rejection
pathological encoded/decoded image bound fixture where admitted decoder permits it
```

For the disk-hit journey, `BenchmarkCoverFixture` must inject a benchmark-source-set implementation of Task 11's production `RemoteCoverTransport` interface (deterministic in-memory encoded bytes) and otherwise use the unchanged production cover key/cache/fetch path: prime the disk cache once, clear only the decoded-memory cache/create a fresh capability image session, reset the transport request counter, then request the same `CoverAssetKey`. The measured disk-hit assertion is `transportRequestCount == 0`; no external network and no production `INTERNET` permission are needed. Do not claim a bundled-local-resource lookup is a disk-cache proof and do not create a benchmark-only fetcher architecture. Benchmark source-set helpers that must be called across modules (for example app benchmark activity -> feature benchmark fixture, or feature benchmark fixture -> runtime benchmark helper) are explicitly `public` **only in `src/benchmarkRelease`**; they are absent from `main`/`release`, and the build-surface test proves the release artifact cannot resolve those symbols.

- [ ] **Step 3: Extend Macrobenchmark journeys with `FrameTimingMetric` where applicable**

At minimum:

```text
coldFreshInstall            -> preserve Step 1 fresh-start methodology/FirstRun comparison
coldReturningDiscover       -> StartupTimingMetric + meaningful Catalog traces
multiSectionDiscoverScroll  -> FrameTimingMetric; scroll to the final section and back to item 0, verifying first content is visible again
openStoryMemoryHit          -> FrameTimingMetric + trace latency
openStoryDiskHit            -> FrameTimingMetric + trace latency
storyBackToDiscover         -> FrameTimingMetric
agedStorageReturningDiscover-> StartupTimingMetric + Catalog traces/query counters
persistedEmptyReturningDiscover -> Catalog traces + acquisition/transport counters (no auto-bootstrap/network)
longBrowseCacheStability     -> FrameTimingMetric + cache/job/map/pin diagnostics across 20 full browse/open/back cycles
```

Keep the existing Step 1 benchmark driver/build class/`BaselineProfileMode.Require` methodology so historical startup comparison is meaningful.

- [ ] **Step 4: Freeze the frame/jank review triggers now, before measurements decide acceptance**

On the Redmi Note 9S/API 35 reference class used by prior Discover evidence, `multiSectionDiscoverScroll` triggers mandatory optimization/review if **any** final 5-iteration median percentile exceeds:

```text
frameDurationCpuMs P95 > 16.67 ms
frameDurationCpuMs P99 > 25.00 ms
frameOverrunMs     P95 > 16.67 ms
```

This is intentionally looser than the supplied V1 2026-09-06 `discoverScroll` evidence (CPU P95 12.43 ms, P99 13.91 ms, Overrun P95 9.86 ms) while still flagging sustained work beyond a 60 Hz frame budget. For `openStoryMemoryHit`, `openStoryDiskHit`, and `storyBackToDiscover`, trigger review when `frameDurationCpuMs P95 > 33.33 ms` or `frameOverrunMs P95 > 33.33 ms`. Additionally, for any same-device journey, a >10% deterioration in a final P95/Overrun-P95 metric versus the first correctness-green Step 2 measurement from this task requires explanation/optimization rather than being hidden by a good TTID. These are **review triggers**, not permission to waive R2.1 hard ownership/query/cache gates.

If the exact AndroidX version reports equivalent field names rather than these display labels, the checkpoint records the one-to-one metric mapping; it may not substitute an easier metric after seeing results.

- [ ] **Step 5: Add deterministic ownership/query/work counters**

Instrument test/benchmark diagnostics so evidence proves:

```text
startup ordering: Step 1 `HikariV2:first-frame` completes before Catalog storage-ready/first-snapshot work can gate it; Catalog activation is asynchronous and does not block the first application-owned frame
pre-demand: DB opens=0, acquisition starts=0, image-loader inits=0, transport/network requests=0
Discover persisted snapshot: exactly 1 SQL observation query / emitted read snapshot
Story Detail persisted snapshot: <=4 SQL statements (joined summary/detail + authors + artists + genres)
Published(content/empty) returning path: acquisition starts=0
memory hit: disk/source/transport reads=0
disk hit: transport/network reads=0
image pressure: active cover jobs <=8; decoded memory cache current/peak ownership <=32 MiB; encoded disk ownership <=128 MiB; every successful decode records bounded requested target dimensions and never reports original-size decode for a bounded card/detail request
refresh/prune touched-story cardinality <= bounded previous/current delta + bounded orphan overflow
Main thread: DB/source/network/large decode/importer CPU violations=0; Room never enables `allowMainThreadQueries`, source/transport fakes assert non-Main execution, importer dispatcher tests assert CPU work uses the injected CPU dispatcher, and Android decode evidence records/asserts decode thread != main looper
terminal repeated navigation: active-work maps/pins/active destination observers return to 0
long-browse terminal state after each 20-cycle iteration: active work/pins/jobs return to 0, decoded/disk ownership remains within configured ceilings, and ownership counters do not increase monotonically from iteration to iteration
```

Diagnostics are benchmark/test observability, not a production-global event bus or history log.

- [ ] **Step 6: Run focused agent-owned compile/unit checks**

```bash
./gradlew :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest :benchmark:assemble --no-daemon
```

Expected: benchmark fixtures/journeys compile and focused invariant counters pass. Do not auto-run device benchmarks here.

- [ ] **Step 7: Hand off first same-device benchmark measurement**

Use Redmi Note 9S/API 35 when available to preserve comparability. Run the new journeys for 5 iterations under the accepted benchmark build/compilation class and retain raw benchmark JSON + Perfetto output paths. If that exact reference device is unavailable, record the new device as a **new Step 2 local baseline** and do not numerically compare its startup/frame values to the Redmi Step 1/V1 baselines. Use the task name already proven by the supplied Step 1/V1 benchmark tree and filter one method at a time (replace `<serial>` only):

```bash
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldFreshInstall' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldReturningDiscover' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#multiSectionDiscoverScroll' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#openStoryMemoryHit' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#openStoryDiskHit' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#storyBackToDiscover' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#agedStorageReturningDiscover' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#persistedEmptyReturningDiscover' --no-daemon
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#longBrowseCacheStability' --no-daemon
```

- [ ] **Step 8: Perform evidence-driven optimization only where a gate fails/regresses**

Allowed corrections are exactly the R2.1 optimization authority: lower card counts while retaining all three semantic sections, defer below-fold composition, reduce metadata/decode target/prefetch/concurrency/cache, improve index/projection/state width, remove redundant animation/effects. Do not move work to startup, introduce background ownership, bypass persistence/validation, or enlarge caches to mask latency.

After each correction, rerun the affected focused unit/connected evidence and then hand off only the affected benchmark journey. Keep an evidence table of “symptom -> root cause -> bounded change -> before/after”.

- [ ] **Step 9: Regenerate the final Step 2 Baseline/Startup Profile only after code/layout/query shape is frozen**

`BaselineProfileGenerator` must exercise the final deterministic **returning Discover -> Story Detail -> Back** journey through persisted data. User-owned device/profile generation command:

```bash
./gradlew :app:generateBaselineProfile --no-daemon
sha256sum app/src/release/generated/baselineProfiles/baseline-prof.txt \
  app/src/release/generated/baselineProfiles/startup-prof.txt
```

Record both hashes plus source/runtime commit SHA in the performance evidence doc.

- [ ] **Step 10: Run final startup comparison only with the regenerated profile**

Preserve the existing Step 1 `coldFreshInstall` journey unchanged except for verifying Catalog counters remain zero before the FirstRun-owned initial display; returning comparison uses the new persisted Discover path. Run 5 cold fresh and 5 cold returning iterations using the same compilation-mode class as Step 1. On the same Redmi Note 9S/API 35 reference, returning TTID median >`453.8025943 ms` (10% above `412.547813`) is the explicit review trigger; fresh TTID median >`433.6315159 ms` (10% above `394.210469`) is likewise a review trigger for the fresh path. Record Catalog activation/storage-open latency, `discover-first-snapshot`, `discover-first-cover`, and `discover-content-ready` separately so a fast shell cannot hide slow useful content or a slow first complete visible section.

- [ ] **Step 11: Run final scroll/open/back/aged journeys and apply all frozen hard gates**

Review the absolute frame triggers from Step 4, the same-task >10% regression rule, query/work counters, memory/disk hit transport counters, aged-row invariance, and repeated-navigation ownership. TTID cannot override a failed frame/resource/query gate.

- [ ] **Step 12: Self-review, checkpoint, commit, stop**

Confirm profile hashes belong to final code, no stale-profile comparison remains, direct aged-row seeding was used only for unrelated pathological state, product/import semantics still go through importer, memory/maps/pins do not grow monotonically, and optimization did not weaken source/image bounds or ownership. Task 14 remains `READY FOR USER VERIFICATION` until all required device/profile evidence is reviewed; only then commit e.g. `perf: validate and profile v2 catalog journey`. **Stop.**

---

# Task 15: Execute isolated deterministic MangaUpdates real-plugin integration gate

**Files:**
- Create: `feature/catalog/src/androidTest/assets/reference-plugins/mangaupdates/manifest.json`
- Create: `feature/catalog/src/androidTest/assets/reference-plugins/mangaupdates/main.js`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/plugin/ReferencePluginExecutor.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/plugin/ReferencePluginBridge.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/plugin/ControlledPluginTransport.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/plugin/MangaUpdatesCatalogIntegrationTest.kt`
- Create `feature/catalog/src/androidTest/AndroidManifest.xml` **only** if an actually executed test path requires a permission; deterministic in-memory controlled transport does not need `INTERNET`.
- Modify: `feature/catalog/build.gradle.kts` for `androidTest` dependencies only.
- Modify: `gradle/libs.versions.toml` to add the reviewed AndroidX JavaScriptEngine 1.1.0 alias only when this test harness is admitted.
- Modify: `config/architecture/module-boundaries.json` in this same task so `:feature:catalog.testDependencies` becomes exactly `[":plugins:api"]`; before this task it remains empty.
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/Step2BuildSurfaceVerifier.kt` + focused test so the exact new edge is legal only from `:feature:catalog` androidTest/test configurations and remains forbidden from production/main/release.
- Modify: `scripts/tests/v2-step2-build-surface-test.sh` so release-cleanliness checks know the androidTest edge is intentionally admitted without relaxing production leakage rules.
- Modify: `docs/internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md` with reference hashes/provenance and gate evidence.

**Interfaces:**
- `androidTestImplementation(project(":plugins:api"))` is the only new project test edge and is allowed only here.
- Exact Task 15-only additions are `androidTestImplementation(project(":plugins:api"))`, `androidTestImplementation(libs.androidx.javascriptengine)`, `androidTestImplementation(libs.kotlinx.serialization.json)`, and `androidTestImplementation(libs.kotlinx.coroutines.core)`; reuse the feature's already-admitted AndroidX test/Compose-test dependencies. No `:plugins:runtime` production/test project edge is admitted.
- Adapter outputs unchanged Step 2 `DiscoverAcquisition` / `StoryDetailAcquisition`; host `CatalogSourceBinding` supplies `CatalogSourceKey`, verified source version, clock provenance, and source asset policy.
- Controlled transport executes the real reviewed JS deterministically. Live network is outside acceptance.

- [ ] **Step 1: Record exact reference provenance before copying**

From the supplied V1 baseline:

```text
bundled-plugins/mangaupdates-catalog/manifest.json
bundled-plugins/mangaupdates-catalog/main.js
plugin id: org.openstory.catalog.mangaupdates
plugin version: 1.1.4
protocol: 1
semantic Home kinds: POPULAR, LATEST_UPDATES, TOP_RATED
reviewed hosts: api.mangaupdates.com, cdn.mangaupdates.com, mangaupdates.com, www.mangaupdates.com
```

For the supplied V1 archive used to author this plan, freeze the expected provenance anchors:

```text
Hikari-perf-whole-app-big-update-v3(2).zip SHA-256
  c4107742c1c06848aa48fc4e494192d4b9166fe5d364b4cb131c2b3dca2b5b5c
manifest.json SHA-256
  777d257590ca8d1b1791956bed135c5029c62e244807f155a63911db627a2cd5
main.js SHA-256
  b144ef4fd6ab3c0c319c6f9c92c787bb7796f07559ebaf06ce85d6e11f0e3202
```

At execution, copy only files whose bytes match these reviewed manifest/script hashes into `src/androidTest/assets/reference-plugins/mangaupdates/`; load them with the Android instrumentation `AssetManager`, matching the repository's existing androidTest asset pattern. If the source archive differs, stop and record/review the new baseline rather than silently accepting drift. Record repository commit as well when available. The checkpoint must also state that the reference protocol admits looser limits/content kinds than Step 2, so the adapter/importer boundary remains authoritative.

- [ ] **Step 2: Write RED real-JS semantic/provenance tests against controlled transport**

The acceptance test class begins with a hard sandbox capability test, not an assumption/skip:

```kotlin
@Test
fun javascriptSandboxSupportIsRequiredForAcceptance() {
    assertTrue(JavaScriptSandbox.isSupported())
}
```

A device where this assertion fails leaves Task 15 **OPEN** and must not be reported as a skipped/pass integration gate. Execute the actual copied `main.js`. Prove:

```text
Home emits explicit POPULAR/LATEST_UPDATES/TOP_RATED kinds; adapter never infers kind from title/sourceId
MANGA request persists only Manga-eligible entries
LIGHT_NOVEL request persists only Light-Novel-eligible entries
WEB_NOVEL/ANIME outputs are never coerced into MANGA/LIGHT_NOVEL or persisted as a hidden third type
details route uses exact StorySourceRef sourceStoryId
payload cannot choose/override CatalogSourceKey, sourceVersion, acquiredAt, or asset host policy
plugin/source failure leaves prior published/detail state unchanged
real JS -> adapter -> importer -> Room -> unchanged Discover read/UI renders the three semantic sections
real JS detail -> importer -> Room -> unchanged Story Detail UI renders metadata without a chapter/reader action
```

If the real reference operation emits an unsupported content kind in a mixed controlled fixture, the adapter treats it as ineligible for the selected Step 2 media scope; it does not rename/coerce it. A malformed/oversized accepted item remains a typed validation failure rather than being silently coerced into eligibility.

- [ ] **Step 3: Write RED Step 2-boundary tests against deliberately looser valid plugin output**

The copied reference protocol allows values larger than Step 2 (`sourceId` up to 1024 chars, general text up to 4096 chars, authors up to 100, collections up to 200, detail description up to 200,000 chars). Controlled responses must make the **real JS** emit protocol-valid values that violate Step 2 and prove the V2 boundary rejects them before persistence/UI expansion:

```text
sourceStoryId UTF-8 bytes >512
card/detail title >1,024 Unicode scalar values but <= plugin 4,096-char protocol cap
authors >32 but <=100
genres >64 but <=200
description UTF-8 >64 KiB but <=200,000 plugin slice
cover locator text >4,096 => reject at V2 locator boundary if the plugin/bridge can emit it without failing earlier
```

Where the plugin itself rejects/truncates a value before it can reach V2, record that fact and test the nearest protocol-valid larger-than-V2 value instead; never claim Step 2 validated bytes it never received. Prior usable persisted content must survive every V2 bounded-input rejection.

- [ ] **Step 4: Write RED remote-cover integration cases**

Use real-format MangaUpdates cover locators with a test implementation of the unchanged Task 11 `RemoteCoverTransport` interface and controlled encoded responses to exercise: source-scoped allowed host, redirect hop revalidation/count, cleartext/undeclared-host rejection, media type, 8 MiB streaming cap, decoded dimension/pixel preflight, same `CoverAssetKey`, and deterministic disk-hit path without external network. At least one success case must render a non-placeholder cover in the actual Discover UI and then the same artwork identity in Story Detail, proving the plugin probe reaches the unchanged UI/image path rather than stopping at importer rows.

- [ ] **Step 5: Run RED compile**

```bash
./gradlew :feature:catalog:compileDebugAndroidTestKotlin --no-daemon
```

Expected failure until the test-only JS executor/bridge exists.

- [ ] **Step 6: Copy/adapt only the minimum executor support into `androidTest`**

The supplied V1 runtime evidence identifies narrow candidates equivalent to:

```text
plugins/runtime/.../execution/RuntimeLimits.kt
plugins/runtime/.../execution/JavaScriptEngine.kt
plugins/runtime/.../execution/AndroidxJavaScriptEngine.kt
plugins/runtime/.../execution/InvocationScriptBuilder.kt
```

Copy/adapt only what the deterministic harness demonstrably needs. `ReferencePluginExecutor` must preserve bounded execution that the four low-level V1 engine files alone do **not** provide: enforce an owned **15,000 ms deadline via `withTimeoutOrNull`/dedicated timeout result** (not by catching broad `CancellationException`), enforce max heap 16 MiB where JavaScriptEngine supports it, reject bridge messages >256 KiB, and reject final output >2 MiB before protocol/adaptor expansion. External instrumentation/session cancellation still propagates; only the executor-owned deadline becomes the deterministic timeout failure asserted by the harness. Add focused harness tests for timeout, bridge oversize, and output oversize so a broken real plugin cannot hang or allocate unboundedly during acceptance. Do not pull in V1 `PluginOperationRunner` merely for convenience if that would drag runtime capability/diagnostic dependencies; reproduce only these bounded executor semantics in `androidTest`. Do not copy production composition, provisioning/package lifecycle, auth lifecycle, WorkManager, plugin repository/update system, service/provider/initializer, or the `:plugins:runtime` module edge. `ControlledPluginTransport` owns `host.http`; deterministic acceptance must not contact external MangaUpdates.

- [ ] **Step 7: Run focused agent-owned harness compile/static checks**

```bash
./gradlew :feature:catalog:compileDebugAndroidTestKotlin :feature:catalog:testDebugUnitTest --no-daemon
```

Inspect dependency/source-set output to confirm JavaScriptEngine/reference resources/executor classes are test-only before asking for connected execution.

- [ ] **Step 8: Hand off deterministic integration GREEN**

User-owned by default. First choose an explicit connected device/emulator on which `JavaScriptSandbox.isSupported()` is true; record its serial/API/device model. Then run:

```bash
ANDROID_SERIAL=<javascript-sandbox-supported-serial> \
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.plugin.MangaUpdatesCatalogIntegrationTest \
  --no-daemon
```

`<javascript-sandbox-supported-serial>` is an execution-time device value, not a plan placeholder to guess; the checkpoint records the actual serial/model/API used. If no available device supports JavaScriptSandbox, keep Task 15 `OPEN` and obtain a supported device rather than accepting an `assumeTrue` skip. After focused iteration is green, run the full `:feature:catalog:connectedDebugAndroidTest` once on that same supported device for Task 15 regression evidence. Task 15 remains `READY FOR USER VERIFICATION` until the device result is reviewed.

- [ ] **Step 9: Hand off release-cleanliness/architecture proof**

```bash
./gradlew :build-logic:test :feature:catalog:assembleRelease :app:assembleRelease verifyArchitecture :app:verifyFoundation --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
```

Prove release contains no reference JS/manifest, executor/bridge, JavaScriptEngine, `:plugins:api` production edge, test permission, seed source, plugin provider/service/initializer, or production `INTERNET` solely for this gate.

- [ ] **Step 10: Optional live smoke is non-blocking and separately labeled**

Only if explicitly requested and the environment permits it, run one live MangaUpdates smoke separately. Never use live availability/timing for correctness acceptance, performance numbers, or release source admission.

- [ ] **Step 11: Self-review, checkpoint, commit, stop**

Review semantic mapping, unsupported-content handling, Step 2 stricter-bound enforcement, provenance authority, test-only source/dependency placement, copied-support minimum, content hashes, and release cleanliness. Only after required deterministic/architecture evidence is green, commit e.g. `test: prove mangaupdates catalog integration`. **Stop.**

---

# Task 16: Full Step 2 acceptance, documentation freeze, and final self-review

**Files:**
- Modify: `docs/internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify/finalize: `docs/internal/v2/catalog-step2-performance-baseline-2026-09-08.md`
- Generated profile files only if Task 14 produced reviewed replacements.
- No new production behavior unless this red-team identifies a concrete in-scope defect; any such defect returns to its owning focused test/gate before closure.

**Interfaces:**
- Produces the final acceptance/freeze record for **Step 2 as a production-shaped internal/product vertical slice, not a ship-ready production remote-catalog release**.
- Every one of the 67 R2.1 acceptance criteria must have evidence or Step 2 remains open.
- Final full/device/performance commands are user-owned by default. The agent reviews returned evidence; it does not mark acceptance from unexecuted command text.

- [ ] **Step 1: Run focused agent-owned final static/document consistency review**

Before broad execution, inspect the changed cone and run only safe focused host checks needed to catch obvious stale references/compile failures. Verify canonical checked-in spec/plan hashes still match the reviewed artifacts and checkpoint references.

- [ ] **Step 2: Hand off final host/repository verification**

```bash
./gradlew verifyArchitecture :app:verifyFoundation :build-logic:test --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

Record exact commands/results. A failed broad gate returns to the owning task/problem; do not waive it in documentation.

- [ ] **Step 3: Hand off/reconfirm required device correctness matrix**

Reconfirm API 26 and API 37 Room/feature/app instrumentation evidence and the deterministic MangaUpdates integration gate from Tasks 13/15. Re-running is required if code affecting those surfaces changed after their accepted evidence; otherwise the final checkpoint may reference immutable command/device/result artifacts from the final commit candidate. No fabricated “API 26 + 37 passed” claim is allowed without actual device evidence.

- [ ] **Step 4: Reconfirm final performance/profile evidence from the final commit candidate**

The acceptance record must include final profile hashes, source/runtime SHA, five-iteration same-device startup values where comparison is claimed, meaningful-content trace values, frozen frame review results, Story open/back, memory/disk hit transport counts, aged-state SQL/work cardinality, and repeated-navigation memory/map/pin evidence. If production/layout/query/image code changed after Task 14, regenerate profiles and rerun affected benchmark gates before acceptance.

- [ ] **Step 5: Build the explicit 67-row spec-to-implementation acceptance matrix**

For each R2.1 acceptance criterion `1..67`, record:

```text
criterion number + short text
owner task
source/architecture evidence
focused test evidence
connected/device evidence when required
performance/profile evidence when required
PASS | OPEN | FAIL
```

No load-bearing row may be `OPEN` or `FAIL` when Step 2 is marked accepted. “Covered by architecture” is insufficient unless the cited verifier/test actually checks that exact requirement.
Start from the pre-mapped 67-row ownership/evidence table in the plan self-review below; Task 16 fills concrete command/artifact hashes/results rather than inventing owners at closure time. If implementation changes an owner, update both the plan/checkpoint mapping explicitly.

- [ ] **Step 6: Perform final structural/debt red-team across all four modules**

Inspect source plus dependency/package reports for:

```text
package SCCs
broad/god-ish authority growth
new src/main test seams or benchmark seams leaking to release
wrapper-only/dead/duplicate files
per-card/point pseudo-batch persistence
independent multi-stream readiness settlement
global/historical query amplification or retention scans
Main-thread DB/source/decode/importer work
lifecycle/domain observer leaks
single-flight terminal-map leaks
pin/unpin cleanup leaks
cache/concurrency/bounds bypass
any raw HTTP/concrete network-client dependency or source import in Step 2 production
variant/source fixture leakage
production plugin/runtime/INTERNET leakage
app-shell ownership creep
chapter/search/reader/library/download scope creep
```

Fix only concrete Step 2 defects. Every fix reruns its owning focused tests and invalidates/re-runs any affected broad/device/performance evidence before the acceptance matrix returns to PASS.

- [ ] **Step 7: Update canonical state only after all evidence is green**

- mark the Step 2 checkpoint `ACCEPTED` with exact source/runtime SHA;
- update `docs/project/current-state.md` with exact admitted graph, runtime/source limitations, both enabled media types, and “not ship-ready remote acquisition” classification;
- update roadmap to the next **separately designed/admitted** capability without beginning it;
- preserve Step 1 startup baseline as immutable historical evidence;
- record generated baseline/startup profile hashes and performance artifact locations;
- keep Search/Chapters/Reader/Library/Downloads/plugin production runtime outside Step 2.

- [ ] **Step 8: Final commit and stop**

Only after the 67-row matrix and all required gates are PASS:

```bash
git add -A
git commit -m "docs: freeze hikari v2 step2 discover story foundation"
```

**Stop.** The next capability requires its own design/admission; roadmap movement is not permission to start it.

---

## Required verification command map

This map distinguishes commands the implementing agent may normally run from gates that repository `AGENTS.md` makes user-owned by default. A user can explicitly delegate a user-owned gate back, but absent that delegation the agent must stop on the current task and request/await the result rather than polling or auto-advancing.

### Agent-owned focused/compile checks

```bash
# Pure domain
./gradlew :catalog:domain:test --no-daemon

# Runtime focused host tests
./gradlew :catalog:runtime:testDebugUnitTest --no-daemon

# Feature reducer/ViewModel focused host tests
./gradlew :feature:catalog:testDebugUnitTest --no-daemon

# Android behavior compile checks; actual execution is connected/user-owned
./gradlew :catalog:storage:compileDebugAndroidTestKotlin :feature:catalog:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon

# Build-logic focused tests
./gradlew :build-logic:test --no-daemon
```

`catalog:storage:testDebugUnitTest` is not treated as proof of Room behavior. Step 1 has no Robolectric; real Room DB/DAO/transaction/reopen/invalidation/query behavior is under `src/androidTest`.

### User-owned broad/device/performance gates by default

```bash
# Architecture/foundation/build surface
./gradlew verifyArchitecture :app:verifyFoundation --no-daemon
bash scripts/tests/v2-step2-build-surface-test.sh

# API/device correctness; select the intended device explicitly
ANDROID_SERIAL=<serial> ./gradlew :catalog:storage:connectedDebugAndroidTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon

# Deterministic real-plugin gate
ANDROID_SERIAL=<serial> ./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.plugin.MangaUpdatesCatalogIntegrationTest \
  --no-daemon

# Full repository
bash scripts/verify-fast.sh
bash scripts/verify.sh

# Baseline/startup profile generation after final local code is frozen
./gradlew :app:generateBaselineProfile --no-daemon

# Macrobenchmark journeys are invoked with the repository benchmark runner/class filters documented by Task 14,
# on the accepted reference device/build class, with 5 iterations for final evidence.
```

Do not supervise long-running Gradle/device commands with repeated polling. One task remains current until required evidence is reviewed; checkpoint/roadmap updates do not authorize the next task automatically.

---

## Plan self-review

### 1. Spec coverage review

The task mapping covers every normative R2.1 area:

| R2.1 area | Owning tasks |
| --- | --- |
| Product authority / Light Novel enablement | 0 |
| Exact modules/build surface/variant matrix/package SCC | 0, 6, 16 |
| Frozen StoryId v1 / host provenance / input ceilings | 1 |
| New Room boundary / `Absent` vs `Published(empty)` / coherent Discover read | 2 |
| Keyed atomic Story Detail / child bounds / access aging / retention index | 3 |
| Atomic Discover publication / delta pruning / mutation gate / active pins | 4 |
| Demand activation / bootstrap / single-flight / no-source release state | 5 |
| Deterministic seed through real importer / release cleanliness | 6 |
| Step 1 launch handoff / no pre-demand work | 7 |
| Multi-section Discover / both media / one vertical owner / retained-content state | 8 |
| Story Detail route / process recreation / pin restoration / metadata-only | 9 |
| Local cover identity/cache/continuity/viewport/concurrency | 10 |
| Remote cover policy/redirect/encoded+decoded bounds | 11 |
| Manual refresh/failure/quiescence/repeated activation | 12 |
| API26/API37 + local correctness freeze | 13 |
| performance/aged state/query work/frame metrics/profile regeneration/startup delta | 14 |
| isolated deterministic MangaUpdates real-plugin proof | 15 |
| all 67 acceptance criteria + structural freeze | 16 |

No load-bearing R2.1 section is intentionally deferred beyond Step 2.

### 1.1 Acceptance criteria 1–67 pre-mapped to executable evidence

This table is part of the plan contract, not something Task 16 invents at the end. Task 16 replaces the planned proof with concrete command/device/artifact results and may not mark a row PASS from prose alone.

| # | Short criterion | Owner | Planned executable proof |
| ---: | --- | --- | --- |
| 1 | Launch handoff | 7 | CatalogLaunchHandoffTest: returning Ready -> Discover; FirstRun persist -> Discover |
| 2 | First frame independent | 7,14 | app shell/trace contract + pre-demand/startup counters; no Catalog work gates Step 1 first frame |
| 3 | Manga + Light Novel authority | 0,8 | approved-product-design amendment + enabled media reducer/UI tests |
| 4 | Internal slice classification | 0,16 | checkpoint/current-state text explicitly says not ship-ready remote Catalog |
| 5 | Real Room-backed Discover | 2,6 | Room connected tests + deterministic source/importer path; no direct UI JSON/read shortcut |
| 6 | Seed uses real write boundary | 6 | variant fixture tests prove source -> executor -> importer -> Room |
| 7 | Exact variant wiring | 0,6 | Step2BuildSurfaceVerifier + variant compile/artifact tests |
| 8 | Release seed/harness clean | 6,15 | release artifact/source scan before and after plugin test edge |
| 9 | Four modules + fail-closed ratchet | 0 | module-boundary/build-surface verifier tests |
| 10 | Android library + Room confinement | 0 | build-logic negative fixtures; no RoomConventionPlugin |
| 11 | App only feature entry edge | 0,7 | exact graph + app source scanner allows only CatalogEntryPoint |
| 12 | Quarantine model/engine | 0 | module-boundary negative fixtures and final graph |
| 13 | Zero production package SCC | 0,16 | ProductionPackageStructureVerifier + final report |
| 14 | Multi-section both media persisted scopes | 2,5,8 | Room state + selected-media runtime + Compose tests |
| 15 | 5/9/5 bound and no N+1 | 1,2,8 | section-policy tests + one Discover SQL observation + ViewModel no-detail-call test |
| 16 | Absent vs Published(empty) | 2 | DiscoverPersistenceInstrumentedTest including left-side state row |
| 17 | Published(empty) survives reopen/no bootstrap | 2,5 | file-backed reopen test + runtime no-bootstrap test |
| 18 | Only Absent bootstraps | 5 | DiscoverSession single-flight/source-unavailable tests |
| 19 | Host-authoritative CatalogSourceKey | 1,5 | domain authority tests + immutable CatalogSourceBinding tests |
| 20 | Frozen StoryId v1 | 1 | literal SHA-256 golden vectors, malformed UTF-16 and metadata-stability tests |
| 21 | Collision fails closed | 2,4 | pre-seeded conflict through public store + typed IdentityCollision propagation |
| 22 | Explicit StorySourceRef route | 1,9 | self-consistency + route serialization/restoration tests |
| 23 | Metadata-only Story Detail | 9 | Story UI/action tests + architecture/scope scan: no Chapters/Reader |
| 24 | Story read keyed and bounded | 3,14 | connected <=4 SQL proof + aged-row invariance |
| 25 | Acquisition input bounds | 1,15 | domain bound tests + looser real-plugin output rejection |
| 26 | Host-stamped provenance | 1,5,15 | binding/clock authority tests + plugin cannot override |
| 27 | Provenance survives source-version change | 2,3 | Discover/detail connected provenance tests |
| 28 | Coherent Discover publication read | 2 | single Room query/snapshot and atomic generation tests |
| 29 | Discover duplicate/order invariants | 1,2 | publication-command validation + DB/public-store uniqueness tests |
| 30 | Zero-card atomic publication/rollback | 4 | connected publication transaction tests |
| 31 | Bounded delta pruning work | 4,14 | touchedStoryIds diagnostics with aged unrelated rows |
| 32 | No history-wide retention scan/sort/count | 3,4,14 | DAO/query-shape tests + aged critical-path counters |
| 33 | Atomic detail and Discover untouched | 3,4 | Story transaction rollback/coherence + Discover-card invariance tests |
| 34 | Failed detail preserves prior coherent state | 3 | faulted transaction connected test |
| 35 | Pin/prune race closed | 4,9 | barrier race tests + pin-first restored route test |
| 36 | Mutation gate short | 4,16 | lock-scope tests/review; slow source/image/UI work outside gate |
| 37 | No access-touch invalidation loop | 3,5 | one semantic touch per demand; repeated Flow emission = zero extra writes |
| 38 | Real covers local + plugin | 10,15 | LocalCoverContinuityInstrumentedTest + MangaUpdates UI success path |
| 39 | Cover independent of rich detail | 9,10 | Story state test + image state remains separate from detail loading |
| 40 | Discover/Story/Back cache continuity | 9,10,14 | same asset key, no bitmap route copy, memory/disk-hit counters |
| 41 | Process recreation route/pin/policy recovery | 9,11 | StoryRouteRestorationInstrumentedTest + host-policy recreation test |
| 42 | Stable card/image geometry | 8,10 | Compose geometry/skeleton and local cover instrumentation |
| 43 | Presentation owns no raw HTTP/trust | 0,11 | source/import dependency scanner + typed locator/transport contract |
| 44 | Local locator uses logical IDs | 1,6,10 | revision/fixture/storage tests prove no persisted R.drawable integer |
| 45 | Remote locator security | 11,15 | HTTPS/host/redirect/timeout/media/size controlled-transport tests |
| 46 | Remote revision keeps meaningful query | 1,11 | RemoteHttpsUriV1 and CoverRevisionV1 golden/identity tests |
| 47 | Image/cache/decode hard ceilings | 10,11,14 | configuration assertions + preflight bombs + runtime benchmark counters |
| 48 | Manual refresh one owner/retained content | 12 | DiscoverRefreshOwnershipTest + DiscoverRefreshStateTest |
| 49 | No process-start/background initializer | 0,7,12 | all-variant merged-manifest checks + pre-demand/static owner tests |
| 50 | No production INTERNET for plugin gate | 0,11,15 | all-variant merged manifest + release artifact check |
| 51 | Bounded state under aging/navigation | 3,4,10,12,14 | retention/cache caps + repeated activation/long-browse diagnostics |
| 52 | Persisted returning path no acquisition/network | 5,14 | Published(content/empty) runtime tests + benchmark counters |
| 53 | Memory/disk hit no lower-tier fetch | 10,14 | cache instrumentation + memory/disk-hit benchmark counters |
| 54 | No Main DB/source/decode/importer work | 4,11,14 | dispatcher tests + Room/config/static checks + decode-thread evidence |
| 55 | Mandatory performance-validation task | 14 | Task 14 benchmark/optimization gate before plugin integration |
| 56 | Regenerate final profiles first | 14 | BaselineProfileGenerator journey + generated file SHA-256 evidence |
| 57 | Startup and meaningful-content deltas recorded | 14 | same-device 5-iteration baseline document + Catalog trace metrics |
| 58 | TTID >10% review trigger | 14 | frozen numerical thresholds and checkpoint pass/review result |
| 59 | Frame/jank explicit threshold | 14 | FrameTimingMetric thresholds frozen before measurement |
| 60 | No monotonic repeated-navigation growth | 12,14 | terminal map/pin tests + 20-cycle long-browse diagnostics |
| 61 | All local gates before plugin | 13,14,15 | canonical task order; Task 15 cannot start until Tasks 13/14 accepted |
| 62 | MangaUpdates default semantic proof | 15 | real copied JS with explicit three Home kinds; no title/ID guessing |
| 63 | Plugin androidTest feeds unchanged product path | 15 | real JS -> adapter -> importer -> Room -> unchanged Discover/Story UI |
| 64 | Plugin provenance/hash and release isolation | 15 | frozen archive/script hashes + test-edge/release-cleanliness proof |
| 65 | Live network smoke optional only | 15 | explicit non-blocking optional step; deterministic transport owns acceptance |
| 66 | Failures/rejections preserve usable content | 4,12,15 | transaction rollback + retained-refresh + plugin bounded-rejection tests |
| 67 | No load-bearing placeholder/owner gap | 0-16 | this pre-mapping + placeholder/type/command scans + Task 16 concrete 67-row evidence fill |

### 2. Cross-repository contradiction review

**Conflict: Step 1 blanket rejects `implementation(project(...))`, but Step 2 requires one app project edge.**  
Resolution: Task 0 replaces blanket token rejection with exact graph verification while keeping app-shell import/startup/permission bans.

**Conflict: Step 1 structural verifier scans only app source, while R2.1 requires zero package SCC across four new modules.**  
Resolution: Task 0 adds a production-package verifier over all Step 2 modules and attaches it to root `verifyArchitecture`.

**Conflict: Android library convention is absent in Step 1; V1 has one plus a generic Room convention.**  
Resolution: Task 0 restores only the tiny Android-library convention; storage applies Room/KSP directly. No generic Room convention returns.

**Conflict: Step 1 benchmark fixture knows only launch-state persistence; Step 2 benchmarks need Catalog persisted data.**  
Resolution: Tasks 6/14 add deterministic benchmark source/importer preparation and forbid direct DAO fixture shortcuts for measured importer semantics.

**Conflict: V1 Discover uses multiple settling streams/canonical bootstrap and had first-entry jank/debt.**  
Resolution: Tasks 2/5/8 enforce one durable publication state + one selected-media observer; no canonical settlement exists in the production graph.

**Conflict: V1 Story screen depends on canonical/Library/progress/reconciliation.**  
Resolution: Task 9 consumes only keyed source Story summary/detail plus scoped acquisition/image state.

**Conflict: Production release has no remote acquisition source but image implementation can support remote typed locators.**  
Resolution: release composition supplies no acquisition source and no `INTERNET`; remote policy code is dormant production-shaped capability support exercised by deterministic androidTest, not a hidden release source.

**Conflict: final plugin proof needs real JS while production plugin runtime is quarantined.**  
Resolution: Task 15 scopes `:plugins:api`, JavaScriptEngine and copied/adapted executor support to `:feature:catalog/src/androidTest` only and re-verifies release artifact cleanliness.

### 3. Task sizing review

The original R2.1 A-M sequence was split further at the highest-risk seams:

- coherent Discover storage separate from Story Detail/retention;
- importer/mutation ordering separate from runtime single-flight;
- local image continuity separate from remote image security;
- local correctness freeze separate from performance/profile work;
- performance/profile work separate from plugin integration.

Each Task N has an independently rejectable/committable result and a focused test cycle. No task requires beginning the next task to become meaningful.

### 4. Type/interface consistency review

- `CatalogSourceKey`, `SourceStoryKey`, `StorySourceRef`, `DiscoverPersistenceState`, `CoverAssetKey`, and `CoverLocator` are defined once in Task 1 and consumed unchanged later.
- UI route carries `StorySourceRef`, not `StoryId` alone.
- Source authority/provenance comes from host/session metadata, not acquisition payload fields.
- Discover uses `CatalogMediaType` and `CatalogSectionKind`; no V1 broad `ContentType`/canonical engine type leaks into the new boundary.
- Storage write APIs are bulk/validated-publication oriented; no public per-card transaction interface is introduced.

### 5. Placeholder/deferred-decision review

All load-bearing choices frozen by R2.1 are concretized here: module graph/test graph, source sets, identity and cover-revision algorithms, strict UTF-8 validation, self-consistent route identity, publication state, exact Discover/Story query caps, schema responsibilities, retention bound/work shape including unpin cleanup, mutation-gate ordering, cache/concurrency/image limits, lifecycle ownership, plugin reference/stricter V2 bounds, deterministic transport/disk-hit proof, and profile ordering. The frame/jank review triggers are now frozen in Task 14 **before execution** using the available `FrameTimingMetric` family and the supplied V1 Redmi evidence; Task 14 may record equivalent field-name mapping but may not choose an easier threshold after observing results.

### 6. Red-team review against known V1 debt

The plan contains explicit negative gates for every Step 2-relevant V1 family:

- A1/A2/A3/A5-A8: no canonical/global reconciliation/read path;
- L1: execution ownership tests and Main-thread gates;
- L3/L5/L6/L7: keyed single-flight + bulk atomic publication;
- D1: current generation + <=64 orphan index, no unbounded evidence/history;
- X1/X4: one coherent publication state and semantically keyed observers;
- X3: narrow card/detail projections;
- X5/X6: no durable/background competitor;
- X7: batch APIs are real bulk semantics;
- X16-X18: production plugin control plane/runtime absent;
- structural S6/S7/S12: no god-ish migration target, package SCC = 0, build ratchets fail closed.

### 7. Final consistency pass — 2026-09-09

A final red-team pass was run after the main plan self-review. It specifically closed execution-level gaps that were still capable of producing a correct-looking but unsafe implementation:

- publication validation now fails closed at the cross-module command/storage boundary for **both** Discover cards and Story Detail projections; a rogue caller cannot bypass importer bounds;
- operation-owned timeouts use a dedicated deadline result (`withTimeoutOrNull`/equivalent) so `TimeoutCancellationException` cannot be confused with external/session cancellation;
- invalid restored Story route identity fails safely back to Discover, starts no Story acquisition, and cannot crash the Activity;
- any evidence-driven prefetch introduced later must be lower priority and cannot consume all cover-job capacity while visible/selected-Story demand waits;
- benchmark host compilation uses the already-proven `:benchmark:assemble` surface rather than an unproven `:benchmark:assembleBenchmarkRelease` task name;
- package direction was rechecked after splitting low-level limits/caps from acquisition/publication validators so the plan does not create the package SCC it later forbids.

Machine consistency audit of this reviewed artifact: exactly **17 Task headings (0..16)**; exactly **67 unique acceptance rows (1..67)**; zero `TBD`/`TODO`/deferred-placeholder phrases; balanced Markdown code fences; no stale `CatalogSectionPolicy`, old `validation/CatalogInputLimits`, fake collision-success wording, or unproven benchmark assemble task. Existing baseline scripts referenced by the final gate (`scripts/verify-fast.sh`, `scripts/verify.sh`) were confirmed in the supplied Step 1 tree; `scripts/tests/v2-step2-build-surface-test.sh` is intentionally a Task 0 creation.

### 8. Final assessment

**READY FOR IMPLEMENTATION-PLAN REVIEW.** No remaining load-bearing contradiction or uncovered R2.1 acceptance criterion was found in the final pass. This is still not implementation authorization by itself: after plan approval, execution begins at Task 0 only and repository `AGENTS.md` requires stopping after each canonical Task N.
