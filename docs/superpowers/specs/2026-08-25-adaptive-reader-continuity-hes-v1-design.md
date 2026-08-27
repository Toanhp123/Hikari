# Adaptive Reader Continuity and Hikari Engine Standard v1 Design

**Date:** 2026-08-25  
**Status:** **PLANNED — REBASED AGAINST THE CURRENT WAVE 10 IMPLEMENTATION TREE**  
**Rebase revision:** **R2 / Wave 10 production-remediation baseline**  
**Scope owner:** Reader routing, Reader continuity, Reader execution coordination, and the first reference implementation of Hikari Engine Standard v1 (HES-v1).  
**Supersedes:** the earlier 2026-08-25 Adaptive Reader Continuity/HES-v1 design draft.  
**Does not supersede:** Wave 10 ownership of settings/auth/background/notifications, `MIGRATION_10_11`, or the Wave 10 acceptance checkpoint.

> Post-freeze note (2026-08-26): M7.2 constitutional hardening is **VERIFIED/CLOSED** and HES-v1 is
> re-frozen after repairing runtime and verification conformance gaps. See
> `2026-08-26-adaptive-reader-continuity-hes-v1-m7-2-constitutional-hardening-design.md` and
> `../plans/2026-08-26-adaptive-reader-continuity-hes-v1-m7-2-constitutional-hardening.md`. Historical
> milestone evidence remains preserved; final freeze authority follows
> `../../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md`.

---

## 1. Purpose

Hikari already owns canonical story identity, canonical chapter grouping, release aggregation, reading progress, local downloads/cache, and plugin-backed chapter document loading. The remaining Reader problem is not “how to fetch one chapter”; it is how to make a deterministic and stable runtime decision among several releases and access paths while keeping reading continuity intact under failures, cache hits, source degradation, prefetch, concurrent attempts, and navigation races.

This design introduces an **Adaptive Reader Continuity and Source Routing Engine** and makes it the first **reference implementation of Hikari Engine Standard v1 (HES-v1)**.

The Reader engine decides:

- which semantic chapter release should serve a target chapter;
- whether that release should be read from a specific local fingerprint or remotely;
- how persisted target progress and currently committed content influence continuity;
- when source health should alter routing;
- when an automatic source switch is justified;
- when one foreground hedge is justified;
- how fallback attempts remain bounded and ordered;
- how observations change source-health state;
- and how asynchronous results are prevented from committing after navigation or replan invalidation.

HES-v1 is an architectural contract, not a framework. It defines immutable facts, explicit policy, deterministic pure reasoning, typed observations, effectful coordinators outside the pure core, bounded state, replayable decisions, explainable traces, and build-enforced dependency boundaries.

HES-v1 deliberately does **not** create a shared `BaseEngine`, generic engine hierarchy, engine registry, service locator, reflection discovery system, or generic runtime framework.

---

## 2. Rebase Authority and Source-of-Truth Rule

This revision is rebased against the actual supplied Wave 10 production-remediation source tree, not against stale status prose.

The source tree currently proves all of the following:

- `settings.gradle.kts` declares **16 production modules** plus `:benchmark` as an Android test/performance module;
- Room is **schema 11** and `MIGRATION_10_11` is registered;
- Wave 10 implementation is present, while `docs/internal/checkpoints/wave-10-production-remediation.md` explicitly keeps **final acceptance open**;
- `ReaderPreferencesPort` and `SettingsReaderPreferencesAdapter` already exist;
- `ReaderSourceAvailability` already exists and is implemented by `PluginReaderDocumentSourceRegistry`;
- `ChapterRepository.observe(storyId)` already exposes a reactive `Flow<List<CanonicalChapterGroup>>`;
- Reader production still uses `ReleaseSelector` plus sequential `ReaderDocumentRepository` execution;
- `ReaderViewModel` still clears the committed document and mutates saved chapter identity before a target chapter succeeds;
- Reader currently freezes one chapter graph through `cachedChapterGroups` for the lifetime of the ViewModel;
- `DownloadAwareReaderDocumentStore.readCurrent()` resolves only a completed explicit download, while automatic cache reads require an exact fingerprint;
- automatic-cache entries are keyed by `(namespace, releaseId, fingerprint)` and more than one fingerprint can exist for one release;
- current Reader storage wiring still uses a fixed default automatic-cache quota in `DownloadAwareReaderDocumentStore`; the source tree does not contain a Reader cache-policy port that HES can safely assume already exists;
- Wave 10 auth/runtime work has added failure codes such as `plugin.auth_unavailable` and `plugin.http_credentials_failed` that must not be misclassified as source reliability failures.

Repository governance is itself temporarily inconsistent: `current-state.md` still describes the pre-Wave-10 14-module/schema-10 state, while the source, roadmap header, and Wave 10 checkpoint show the newer implementation. Static verification also contains historical tests that still hard-code pre-Wave-10 assumptions.

Therefore this design follows this rule:

> **For implementation facts, current source and executable verification take precedence over stale prose; acceptance status still comes only from checkpoints.**

The earlier design remains useful architectural provenance, but this R2 document is the normative Reader/HES design after rebase.

---

## 3. Required Entry Gate R0 — Close or Explicitly Rebase the Wave 10 Boundary

HES implementation adds a seventeenth production module and modifies Reader, Downloads, App DI, Room DAO/query behavior, Reader UI state, and architecture guards. Those surfaces overlap the still-open Wave 10 final regression matrix.

The default required entry sequence is:

```text
current Wave 10 implementation tree
        |
        v
repair stale governance/static guard drift
        |
        v
run and close Wave 10 final host/API 26/API 37 acceptance
        |
        v
freeze accepted 16-production-module / schema-11 boundary
        |
        v
start HES-v1 Reader implementation
        |
        v
17 production modules, still schema 11
```

If implementation is intentionally started before Wave 10 acceptance closes, that is a **governance rebase**, not an invisible continuation. In that case the Wave 10 acceptance matrix must be rerun on the HES-containing tree and previous `NOT RUN` evidence must not be treated as satisfied.

This design does not mark Wave 10 accepted and does not consume its missing acceptance evidence.

---

## 4. Current Reader Baseline Consumed by This Design

The design preserves or explicitly supersedes these current contracts.

### 4.1 Release selection

Current `ReleaseSelector` orders `ReleaseCandidate` by:

```text
explicit release
previous release
previous source group
previous source/plugin
language order
legacy health
completeness
recency
plugin ID
release ID
```

Production `ReaderViewModel` currently constructs `ReleaseCandidate` directly from `ChapterRelease`, so production source-group continuity is effectively absent and legacy candidate health/completeness are default facts.

### 4.2 Sequential document execution

Current `ReaderDocumentRepository`:

- selects once;
- attempts candidates sequentially;
- checks the store before remote source fetch;
- lazily enumerates enabled sources;
- writes persistable remote documents to automatic cache;
- quarantines a requested local fingerprint after local read failure/mismatch handling;
- preserves coroutine cancellation;
- returns string-code `ReaderLoadFailure` values.

### 4.3 Reader preferences

Wave 10 already owns:

```kotlin
ReaderPreferencesPort.preferences: Flow<ReaderPreferences>
ReaderPreferencesPort.setFontScale(value)
```

and supplies `languageOrder` through `SettingsReaderPreferencesAdapter`.

Reader currently waits for the first preference emission before its first load. Font write failure restores the persisted preference value, and cancellation propagates. Those are preserved regression contracts.

### 4.4 Chapter graph

`ChapterRepository` already provides both:

```text
observe(storyId): Flow<List<CanonicalChapterGroup>>
snapshot(storyId): ChapterGraphSnapshot
```

Current Reader uses one `snapshot()` result and freezes it in `cachedChapterGroups`. HES supersedes that implementation detail with one **session-local reactive graph observation** while preserving the performance intent of avoiding a full graph reload for every chapter navigation.

### 4.5 Source availability

`ReaderSourceAvailability` already exposes enabled Reader source IDs and is backed by plugin runtime capability filtering. HES reuses this port. It must not introduce a second plugin-enabled/source-availability abstraction for Reader.

### 4.6 Local storage

`DownloadAwareReaderDocumentStore` already supports:

```text
read(releaseId, fingerprint) -> explicit download then automatic cache
readCurrent(releaseId)       -> current completed explicit download only
write(...)                    -> automatic cache
quarantine(releaseId, fingerprint)
```

Because automatic cache is fingerprint-addressed and may contain several fingerprints for the same release, HES routing must carry a concrete local fingerprint locator whenever it plans a `LOCAL` attempt.

---

## 5. Goals

The implementation produced from this design must:

1. choose a semantic release deterministically from multiple releases for one canonical chapter;
2. route through a specific local fingerprint or a remote source without conflating semantic release identity with access mode;
3. preserve persisted target progress and current source/group continuity when doing so remains safe and competitive;
4. avoid chapter-to-chapter source flapping through explicit hysteresis;
5. recover automatically when the preferred route is unavailable or fails;
6. distinguish local attempts, a primary remote attempt, at most one optional foreground hedge, and ordered sequential fallbacks;
7. prefetch only the bounded next target through the same routing engine;
8. model source health as deterministic bounded state transitions from typed observations;
9. keep auth/configuration/user-state failures from poisoning source reliability;
10. keep current committed content and its progress identity authoritative while another target loads;
11. prevent stale generations and stale replans from changing visible content;
12. isolate mutable execution state per Reader screen/session while sharing only process-lifetime source health and source execution limits;
13. consume Wave 10 `ReaderPreferencesPort` instead of creating another settings path;
14. consume current `ReaderSourceAvailability` instead of duplicating plugin availability logic;
15. inspect cache metadata in bounded batches without decoding every candidate document;
16. keep the pure engine free of Android, Room, plugins, coroutines, serialization, filesystem, network, and persistence behavior;
17. produce replayable decisions and structured decision traces with stable reason/rejection codes;
18. establish HES-v1 as a build-enforced architectural reference for later engine migrations;
19. preserve the current Reader/document/download/progress compatibility envelope until an explicit cleanup cutover;
20. remain on Room schema 11 for the initial implementation.

---

## 6. Non-goals

The first implementation does not:

- compare rendered pixels, OCR content, or perform semantic image similarity;
- use machine learning for release ranking;
- launch more than one hedge;
- fan out to every source for latency minimization;
- persist Reader source health to Room;
- synchronize Reader source health across devices;
- change canonical story/chapter aggregation or source mapping semantics;
- change the plugin protocol;
- change the `ReaderDocument` wire/domain format solely for routing;
- make Reader own cache quota/settings policy;
- repair unrelated Wave 10 settings/cache-policy wiring as part of HES;
- add trusted translation/source-group metadata that the current source model does not contain;
- migrate Catalog, Chapter aggregation, or Canonical reconciliation engines in this change;
- introduce a generic engine superclass or shared runtime framework.

A process restart starts Reader health from neutral unless a future separately reviewed adapter supplies a validated snapshot.

---

## 7. HES-v1 Execution Model

Every HES-v1 engine follows one direction:

```text
External world / repositories / plugin runtime / Android
                         |
                         v
                   immutable facts
                         |
                         v
                   PURE REASONER
                         |
                         v
                    decision/plan
                         |
                         v
                    COORDINATOR
                         |
                         v
                       effects
                         |
                         v
                typed observations
                         |
                         v
                    PURE REDUCER
                         |
                         v
                    new facts/state
                         |
                         +-------> replan only when policy says facts invalidate plan
```

Pure reasoners never call a repository, plugin runtime, cache, clock, scheduler, analytics API, filesystem, Room, Android service, or network stack.

---

## 8. HES-v1 Component Classes

Every component participating in the Reader HES implementation belongs conceptually to one of four classes.

### 8.1 Fact

Immutable known information, including:

- `RoutingCandidate`
- `ReaderRoutingSnapshot`
- `ReadingContinuity`
- `CandidateLocalAccess`
- `SourceHealthState`
- `ReaderNetworkClass`
- chapter-graph revision facts

### 8.2 Policy

Immutable versioned intentional behavior, including:

- `ReaderRoutingPolicy`
- `ReaderRoutingWeights`
- `HealthPolicy`
- `HedgePolicy`

### 8.3 Reasoner

Pure transformations, including:

- `ReaderRouteEngine`
- eligibility evaluation
- candidate/access evaluation
- ranking
- continuity incumbent resolution
- hysteresis arbitration
- route construction
- `SourceHealthReducer`

### 8.4 Coordinator

Effectful orchestration outside `:reader:engine`, including:

- `ReaderRouteCoordinator`
- `ReaderRouteSession`
- route executor
- cache/network/source snapshot adapters
- process health registry
- source execution limiter
- prefetch coordinator
- completion registry
- observation recorder

---

## 9. New Module Boundary

Create:

```text
:reader:engine
path = reader/engine
platform = jvm
```

using:

```kotlin
plugins {
    id("openstory.kotlin.jvm")
}
```

Its exact production project dependency set is:

```text
{ :core:common }
```

After this module is added, the production graph becomes **17 production modules** plus `:benchmark` as the test/performance module. Room remains schema 11.

### 9.1 Forbidden engine production imports/dependencies

Production code in `:reader:engine` must not import or depend on:

```text
android.*
androidx.*
app.openstory.chapters.*
app.openstory.reader.content.*
app.openstory.reader.routing.*
app.openstory.plugins.*
app.openstory.downloads.*
app.openstory.storage.*
kotlinx.coroutines.*
kotlinx.serialization.*
java.io.*
java.net.*
```

JVM CPU-only utilities such as collections, integer/math operations, and deterministic value handling remain allowed.

### 9.2 Dependency exposure rule

`:reader` consumes the engine as an **implementation dependency**, not an API re-export:

```text
:reader --implementation--> :reader:engine
```

Downstream modules such as `:downloads`, `:feature:reader`, `:app`, and `:storage:room` do **not** add direct `:reader:engine` dependencies and do not exchange engine types with effect ports. Consumer-owned effect ports use Reader-owned DTOs; `:reader` maps them to pure engine facts internally.

This keeps the HES public surface narrow and prevents engine fact types from becoming an accidental cross-module transport format.

### 9.3 Build and shell coverage

The existing architecture policy and Gradle verifier must include the new module. The repository's shell/package boundary verification must also explicitly scan `reader/engine/src/main`; the current Reader root guard only scans `reader/src/main` and would otherwise leave a source-level coverage hole.

A constitutional build-logic test must assert that the Reader reference engine remains JVM-only with exact project dependency `{ :core:common }` and no effect/framework plugins.

---

## 10. Public Engine Contract

The main pure routing API remains deliberately small:

```kotlin
interface ReaderRouteEngine {
    fun plan(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): ReaderRouteDecision
}
```

The pure health API is:

```kotlin
interface SourceHealthReducer {
    fun advance(
        previous: SourceHealthState,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState

    fun reduce(
        previous: SourceHealthState,
        observation: SourceObservation,
        nowEpochMillis: Long,
        policy: HealthPolicy,
    ): SourceHealthState
}
```

Implementation classes remain `internal` unless a later design proves a real cross-module need.

---

## 11. Version Model

The architecture distinguishes:

```text
HES contract version      = HES_V1
Reader algorithm version  = READER_ROUTING_V1
Reader policy version     = independently versioned
Health policy version     = independently versioned
```

Weight/threshold tuning changes policy version. Routing procedure changes change algorithm version. HES architectural invariant changes require a reviewed HES contract revision.

---

## 12. Fixed-Point Values

All scoring uses integer basis points:

```kotlin
@JvmInline
value class BasisPoints(val value: Int)
```

with range:

```text
0 .. 10_000
```

Other deterministic identity values include:

```text
ReaderPlanRevision(value >= 0)
ReaderChapterGraphRevision(value >= 0)
SourceGroupKey(non-blank, trusted/provider-scoped unless an explicit cross-provider mapping says otherwise)
```

`ReaderGenerationId` and `ReaderSessionId` remain effect-layer runtime identities because they never participate in pure candidate ranking.

Weighted products use `Long` intermediates. The full weighted sum is divided exactly once by `10_000` using deterministic integer truncation.

Floating-point values do not participate in routing comparison or tie-breaking.

---

## 13. Adapter Boundary: Current Chapter Models Never Enter the Pure Engine

The pure engine does not depend on `ChapterRelease` or `CanonicalChapterGroup`.

`:reader`/Reader presentation orchestration maps current chapter-group facts into engine facts:

```text
CanonicalChapterGroup
ChapterRelease
ReaderPreferences
ReaderSourceAvailability
cache metadata facts
network facts
process health snapshots
committed/progress continuity
                 |
                 v
         ReaderRoutingSnapshot
```

Production candidate mapping in v1 uses only facts the current source actually owns:

```text
releaseId             <- ChapterRelease.id
sourceId              <- ChapterRelease.pluginId
languageTag           <- ChapterRelease.languageTag
publishedAt           <- ChapterRelease.publishedAtEpochMillis
sourceGroupKey        <- null in production
completeness          <- 10_000 in production
remote enabled        <- existing ReaderSourceAvailability
local access          <- bounded cache metadata inspection
```

`sourceGroupKey` remains `null` in production until a separately reviewed trusted metadata source exists. Display labels are never used to infer cross-provider translation-group equivalence.

Legacy selector differential fixtures may still provide synthetic trusted group/completeness facts for migration testing, but those are not production inference rules.

---

## 14. Candidate Model

A routing candidate represents one semantic chapter release:

```kotlin
data class RoutingCandidate(
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val languageTag: String,
    val sourceGroupKey: SourceGroupKey?,
    val publishedAtEpochMillis: Long?,
    val completeness: BasisPoints,
    val remoteAccess: CandidateRemoteAccess,
    val localAccess: CandidateLocalAccess,
)
```

The candidate does not contain persistence, plugin, network, or repository behavior.

`CandidateRemoteAccess` is deliberately small in v1:

```text
PERMITTED
SOURCE_UNAVAILABLE
```

`PERMITTED` means the existing `ReaderSourceAvailability` snapshot says that source is currently enabled for Reader `CONTENT_CHAPTER` work. It does **not** claim that a specific release exists remotely; release-level `NotFound` remains an execution observation. Network state, circuit state, and HALF_OPEN probe ownership remain separate snapshot facts rather than being collapsed into this enum.

Snapshots require unique `releaseId` values. Duplicate semantic release IDs are invalid snapshot input, not a tie resolved by input order.

### 14.1 Unsupported rejection facts are removed from v1

The previous draft named `MANUALLY_BLOCKED`, `STALE_MAPPING`, and generic content/chapter identity mismatch as Reader-engine hard rejections without defining a trustworthy current source for those facts.

R2 does **not** manufacture those facts.

Canonical chapter membership is owned upstream by `ChapterRepository`/`CanonicalChapterGroup`. The Reader snapshot assembler must use releases from the target canonical group. If future chapter mapping exposes an explicit stale/mismatch fact, a later policy/algorithm revision may add a typed eligibility rule.

---

## 15. Local Access Is a Locator, Not Only a Cache Status

The current storage contract requires an exact fingerprint to read automatic cache. Therefore v1 uses a local-access fact that contains the selected fingerprint:

```text
CandidateLocalAccess
  UNKNOWN
  MISS
  AVAILABLE_EXACT(fingerprint)
  AVAILABLE_UNVERIFIED(fingerprint)
  KNOWN_INVALID(fingerprint)
```

Semantics:

- `AVAILABLE_EXACT` means metadata contains the exact target resume fingerprint for that release;
- `AVAILABLE_UNVERIFIED` means metadata selected one deterministic usable local fingerprint without a trusted expected fingerprint;
- `KNOWN_INVALID` is produced only after an actual read/decode/validation observation in the current process/session facts;
- metadata containing some *other* fingerprint does **not** make the expected fingerprint “invalid”; it is simply a miss for the exact resume locator;
- `UNKNOWN` means the effect adapter could not establish cache state;
- `MISS` means bounded metadata inspection completed and found no usable chosen locator.

Every planned `LOCAL` attempt therefore contains its fingerprint locator:

```text
releaseId
sourceId
accessMode = LOCAL
localFingerprint = non-null
```

A `REMOTE` attempt has no local fingerprint.

This is a route invariant and is validated at decision construction.

---

## 16. Deterministic Cache Metadata Selection

A new Reader-owned effect port may inspect bounded cache metadata, but it does not expose engine types to Downloads/App.

Conceptually:

```kotlin
interface ReaderCacheFactsPort {
    suspend fun inspect(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact>
}
```

`:downloads` may provide a narrow `ReaderCacheMetadataSource` implemented by Room. It must perform a bounded metadata query over requested release IDs rather than calling `findDownload()` once per candidate and must not decode `ReaderDocument` blobs merely to rank candidates.

The deterministic v1 selection rule per release is:

1. if a target resume fingerprint exists and stored metadata contains that exact fingerprint, select it as `AVAILABLE_EXACT`;
2. if a target resume fingerprint exists but only different fingerprints exist, return `MISS` for the exact resume locator;
3. with no resume fingerprint, inspect the newest explicit-download row for that release exactly as the current `readCurrent()` contract does; select it only when it is `COMPLETED`, otherwise continue to automatic-cache selection rather than resurrecting an older explicit row;
4. otherwise choose one automatic-cache fingerprint by `lastAccessedAtEpochMillis` descending, then fingerprint ascending as the stable tie-break;
5. if metadata access fails/has no authoritative answer, return `UNKNOWN`;
6. if inspection succeeds with no usable entry, return `MISS`.

The exact selected fingerprint is passed into the engine and later into `ReaderDocumentStore.read(releaseId, fingerprint)`.

A stale metadata row whose blob is missing is handled as a local attempt miss at execution time. It is not a source-health failure. An unclassified storage/I/O exception also remains local/client-side and must not be converted into source unreliability or automatic corruption; quarantine is reserved for confirmed fingerprint/decode corruption.

No schema change is required. Existing schema-11 `chapter_storage_entries` already has the release ID, fingerprint, namespace, checksum, state, and access timestamps required for bounded inspection.

---

## 17. Resume Fingerprint Is Not a Remote Integrity Oracle

Persisted `ReadingProgress.contentFingerprint` identifies the exact document against which the saved position was recorded. It is useful for:

- exact local cache lookup;
- exact position restoration;
- determining whether a newly committed document can safely reuse block/character-offset restoration.

It is **not** a trusted provider-side expected fingerprint for a future remote fetch.

Therefore:

- a remote fetch returning a new valid fingerprint for the same release is not `FingerprintMismatch` source corruption;
- that new document may commit if otherwise valid;
- exact saved block/character restoration occurs only when the committed release **and** fingerprint match persisted progress;
- if release matches but fingerprint changed, v1 restores **no saved block ID, character offset, or progress fraction** and starts from the normal chapter entry point; approximate cross-fingerprint restoration is deferred to a separately designed semantic-anchor feature;
- local decode fingerprint must equal the requested local locator; violation is local corruption and quarantines that exact fingerprint.

This prevents a source from becoming permanently unreadable merely because content was legitimately revised after progress was saved.

---

## 18. Reactive Chapter Graph Contract

The Reader session must not reload the entire chapter graph for every chapter navigation, but it also must not freeze one graph forever.

V1 uses one session-local subscription to:

```text
ChapterRepository.observe(storyId)
```

and keeps the latest `List<CanonicalChapterGroup>` as an effect-layer session fact.

Each distinct graph emission receives a monotonically increasing:

```text
ReaderChapterGraphRevision
```

The graph revision is captured in routing snapshots and traces for diagnostics. It is not a ranking feature.

### 18.1 Initial load gate

Initial routing starts only after both are available:

```text
first ReaderPreferences snapshot
first chapter graph snapshot
```

This preserves the current Wave 10 requirement that initial selection uses persisted `languageOrder` while making chapter facts reactive.

### 18.2 Graph changes during active work

For one active uncommitted generation:

**Hard invalidation** occurs when:

- the target canonical chapter disappears or becomes unusable;
- the selected/planned release is removed from the target group;
- the selected/planned release is rebound so that the current route no longer represents the target group;
- all currently viable paths for the selected semantic candidate disappear.

**Soft graph change** includes:

- a newly discovered lower-ranked candidate;
- a metadata/label update that does not affect routing inputs;
- addition of a candidate that does not invalidate the current route.

Soft changes are visible to the next plan but do not revoke valid in-flight work.

After a document has committed, later graph changes do not blank or revoke already committed content. They affect the next foreground intent.

### 18.3 Performance policy supersession

The historical static requirement for a literal `cachedChapterGroups` field is an implementation-detail guard. HES supersedes that literal while preserving the underlying performance invariant:

> one Reader screen owns one chapter-graph observation and does not execute a full one-shot chapter graph reload on every previous/next navigation.

Repository performance policy must be updated to verify that invariant rather than the obsolete field name.

---

## 19. Reading Continuity Model

Continuity separates **currently committed content** from **target-chapter resume state**.

Conceptually:

```text
ReadingContinuity
  committedChapterId?
  committedReleaseId?
  committedSourceId?
  committedSourceGroupKey?
  committedLanguageTag?

  targetResumeReleaseId?
  targetResumeFingerprint?
```

Current `ReadingProgress` supplies target resume release/fingerprint. Current committed Reader state supplies cross-chapter source/group/language continuity.

The engine never assumes “same source” and “same translation group” are equivalent.

---

## 20. Automatic Incumbent Resolution

Hysteresis requires a deterministic incumbent semantic candidate. V1 resolves it in this order from **eligible candidates only**:

1. if the target chapter is the same as the committed chapter and the committed release is still eligible, that release is incumbent;
2. otherwise, if persisted target progress names an eligible release, that resume release is incumbent;
3. otherwise, if a trusted committed source-group key exists, choose the highest-ranked eligible candidate in that same trusted group;
4. otherwise, choose the highest-ranked eligible candidate from the committed source/plugin;
5. otherwise there is no incumbent.

An explicit user-selected release bypasses automatic hysteresis after eligibility succeeds.

This preserves exact target resume continuity without pretending that the previous chapter's release ID can exist in the next chapter.

---

## 21. Routing Snapshot

`ReaderRoutingSnapshot` is complete and immutable for one logical plan revision.

It contains at least:

```text
target canonical chapter ID
chapter graph revision
plan revision
routing intent
canonicalized candidate set
source health snapshots
reading continuity
network class
explicit release selection?
logical wall-clock nowEpochMillis
```

The engine never reads a clock.

Candidate traversal is canonicalized by:

```text
(sourceId.value ascending, releaseId.value ascending)
```

before any evaluation or trace collection. Input list/set/map iteration order never becomes policy.

---

## 22. Routing Intent

V1 supports:

```text
FOREGROUND
PREFETCH
```

Both call the same `ReaderRouteEngine`. Policy changes behavior by intent; there is no second prefetch selector.

---

## 23. Reader Network Facts

`:reader` owns a narrow network facts port; `:app` supplies the Android connectivity adapter. Android types never enter `:reader:engine`.

The normalized fact is:

```text
OFFLINE
METERED
UNMETERED
UNKNOWN
```

Semantics:

- `OFFLINE`: reject remote access paths, keep local paths eligible;
- `METERED`: foreground remote allowed, hedge disabled by default, proactive remote prefetch disabled;
- `UNKNOWN`: foreground remote allowed conservatively, hedge and proactive remote prefetch disabled;
- `UNMETERED`: foreground remote allowed; hedge and remote prefetch may be permitted if all other thresholds pass.

The current source does not expose this Reader port and the current app manifest does not request `ACCESS_NETWORK_STATE`; adding the app adapter/permission is part of HES integration, not engine code.

Wave 10 background setting `requireUnmeteredNetwork` remains owned by its existing background/settings policy and is not silently reused as Reader foreground routing policy.

V1 may sample network facts at planning/replanning boundaries. A continuous Android connectivity subscription is not required for correctness; if a definite connectivity-loss observation arrives during an active plan, it is handled as a hard fact invalidation when no viable local/ongoing path remains.

---

## 24. Language Policy

Reader consumes `ReaderPreferencesPort.languageOrder` from Wave 10.

V1 engine policy supports:

```text
ORDERED_ALLOW
STRICT_ALLOWED
```

Production uses `ORDERED_ALLOW` unless a future Reader UX explicitly exposes strict behavior.

Language tags are normalized in the pure boundary by:

```text
trim
replace '_' with '-'
lowercase with locale-independent semantics
```

Blank tags are invalid candidate facts.

`ORDERED_ALLOW` behavior:

```text
empty order        -> every language score 10_000
index 0            -> 10_000
index 1            ->  8_000
index 2            ->  6_000
index >= 3         ->  4_000
unlisted           ->  2_000
```

`STRICT_ALLOWED` hard-rejects unlisted languages and requires a non-empty unique normalized policy list.

A `languageOrder` change during an active uncommitted foreground intent is a routing-policy change and triggers a hard replan. Font-scale changes never affect routing.

---

## 25. Decision Pipeline

V1 uses a staged pipeline:

```text
Candidates
   |
   v
1. Candidate/access eligibility
   |
   v
2. Explicit user preference
   |
   v
3. Semantic + preferred-access feature evaluation
   |
   v
4. Weighted stable ranking
   |
   v
5. Incumbent resolution + hysteresis
   |
   v
6. Local/remote route construction
   |
   v
7. Optional hedge construction
   |
   v
8. Structured decision trace
```

No single score is allowed to override a hard rejection.

---

## 26. Eligibility

Eligibility is evaluated at access-path level first, then candidate level.

### 26.1 Local path eligibility

A LOCAL path exists only for `AVAILABLE_EXACT(fingerprint)` or policy-permitted `AVAILABLE_UNVERIFIED(fingerprint)`.

`KNOWN_INVALID(fingerprint)` produces:

```text
LOCAL_COPY_KNOWN_INVALID
```

`UNKNOWN` and `MISS` simply mean no routable local path exists; they are not corruption and do not produce a fake local attempt.

### 26.2 Remote path rejection

```text
REMOTE_SOURCE_DISABLED_OR_UNAVAILABLE
REMOTE_NETWORK_UNAVAILABLE
REMOTE_CIRCUIT_OPEN
HALF_OPEN_PROBE_NOT_PERMITTED
```

### 26.3 Candidate-wide rejection

```text
LANGUAGE_FORBIDDEN
NO_USABLE_ACCESS_PATH
```

A source being disabled or its remote circuit being OPEN invalidates only the remote path. A valid local fingerprint remains routable.

Explicit preference cannot make a rejected candidate/access path eligible. A missing/invalid local path does **not** reject the semantic candidate when a valid REMOTE path remains, and a rejected REMOTE path does **not** reject the semantic candidate when a valid LOCAL path remains.

If the explicit release ID is absent from the current target candidate set, the trace records `EXPLICIT_RELEASE_NOT_PRESENT` and automatic routing continues; the engine does not invent a candidate.

---

## 27. Explicit Preference

If the user explicitly selects a release and that semantic candidate has at least one eligible access path, it becomes the semantic winner before automatic hysteresis.

Its local/remote recovery routes remain policy-owned. Explicit selection does not mean “retry this failing release forever”. If its attempted paths fail, execution follows the already-planned recovery chain.

A later user retry or another explicit selection is a new foreground generation.

---

## 28. Feature Evaluation Is Access-Aware

Semantic and access facts must not be conflated.

Every candidate has semantic features:

```text
language
continuity
completeness
freshness
```

and access-dependent features:

```text
health
reliability
latency
cache utility
```

The candidate's **preferred usable access path** is:

```text
eligible LOCAL path, if one exists and local attempts are allowed
otherwise eligible REMOTE path
```

Candidate ranking evaluates access-dependent features for that preferred path.

This resolves an important invariant:

> A downloaded/cached document must not be downgraded merely because the source's remote circuit is degraded or OPEN; local access does not exercise that remote source.

### 28.1 Local preferred-path features

For a usable local path:

```text
healthScore       = 10_000
reliabilityScore  = 10_000
latencyScore      = 10_000
cacheUtility      = 10_000 for AVAILABLE_EXACT
                    6_000 for AVAILABLE_UNVERIFIED
```

Local data still validates before commit. These values model independence from remote source health and expected low local access cost; they do not declare the bytes valid before validation.

### 28.2 Remote preferred-path features

For a remote path:

```text
cacheUtility = 0
health/reliability/latency = source-operation facts
```

The trace records both semantic candidate score and access-path evaluations so local preference and remote hedge eligibility remain explainable.

---

## 29. Continuity Score

The highest applicable continuity rule wins:

```text
target resume release or same committed release on same target    10_000
trusted same translation/source group                              8_000
same source/plugin                                                  6_500
same language only                                                  2_000
none                                                                    0
```

Production group continuity remains unavailable until a trusted group key exists.

---

## 30. Completeness, Freshness, and Unknown Facts

Production `ChapterRelease` currently exposes no completeness measure, so production v1 supplies:

```text
completeness = 10_000
```

Legacy differential fixtures may map old `ReleaseCandidate.completeness` for the overlap envelope.

Freshness is relative to the newest known eligible publication timestamp:

```text
<= 1 hour    -> 10_000
<= 24 hours  ->  9_000
<= 7 days    ->  7_500
<= 30 days   ->  6_000
> 30 days    ->  4_000
unknown      ->  5_000
```

If all eligible candidates have unknown publication times, all receive `5_000` freshness.

Unknown optional facts are neutral or absent, never iteration-order dependent.

---

## 31. Remote Health and Latency Feature Normalization

For an eligible remote path:

```text
health:
  CLOSED                     -> 10_000
  HALF_OPEN with held permit ->  6_000
  OPEN                       -> path rejected

reliability:
  SourceHealthState.successEwmaBasisPoints
  neutral startup            -> 10_000
```

Remote p50/p95 use at most the 20 retained successful remote latency samples and nearest-rank percentile.

Latency score when at least three samples exist:

```text
<= 250 ms   -> 10_000
<= 500 ms   ->  8_500
<= 1,000 ms ->  6_500
<= 2,000 ms ->  4_000
<= 4,000 ms ->  2_000
> 4,000 ms  ->  1_000
```

Fewer than three samples -> `5_000` neutral latency.

Local latency is never mixed into remote source latency history.

---

## 32. Weighted Ranking

Default Reader Routing Policy v1 weights remain:

| Feature | Weight |
|---|---:|
| Language | 2,500 |
| Continuity | 2,500 |
| Health | 1,800 |
| Reliability | 1,000 |
| Completeness | 900 |
| Latency | 700 |
| Freshness | 300 |
| Cache utility | 300 |
| **Total** | **10,000** |

Score:

```text
sum(featureBasisPoints * weight) / 10_000
```

using `Long` intermediates and one final division.

Stable ties resolve by:

```text
sourceId.value ascending
releaseId.value ascending
```

Input position, hash iteration, coroutine ordering, object identity, and random IDs are never tie-breakers.

---

## 33. Hysteresis

After raw ranking, automatic selection compares the incumbent and raw challenger.

Default thresholds:

```text
normal switch threshold      = 800 basis points
degraded incumbent threshold = 350 basis points
incumbent unavailable        = switch immediately
```

An incumbent is “degraded” when its **preferred access path is remote** and either:

```text
remote reliability < 8_500
or remote circuit is HALF_OPEN
```

A candidate currently served through a usable local path is not considered degraded merely because its remote source is degraded/OPEN.

Explicit user selection skips this arbitration.

---

## 34. Route Construction

The decision separates semantic choice from executable attempts:

```text
competitiveSet
  primary attempt
  optional hedge attempt

recoveryChain
  ordered fallback attempts

rejections
trace
confidence
versions
planRevision
```

Each attempt contains:

```text
attemptId
releaseId
sourceId
accessMode = LOCAL | REMOTE
localFingerprint?  // required only for LOCAL
role = PRIMARY | HEDGE | FALLBACK
```

`attemptId` is deterministic from final plan order:

```text
attempt-0
attempt-1
...
```

No random UUID belongs to pure routing semantics.

### 34.1 Access order for one semantic candidate

```text
AVAILABLE_EXACT local       -> LOCAL before REMOTE
AVAILABLE_UNVERIFIED local  -> LOCAL before REMOTE when policy permits
KNOWN_INVALID/MISS/UNKNOWN  -> no LOCAL attempt
```

If the semantic winner's local read fails validation or is missing at execution time, its eligible remote path is the first recovery path before moving to the next semantic candidate.

Remaining semantic candidates follow final stable ranking, local before remote for each candidate.

### 34.2 Budgets

Default policy:

```text
max recovery attempts            = 6
max planned foreground REMOTE    = 4
max concurrent foreground REMOTE = 2
max concurrent prefetch REMOTE   = 1
```

The executor independently enforces runtime ceilings even if a malformed/future plan violates policy.

---

## 35. Confidence

Confidence is diagnostic only and never feeds back into routing.

```text
no eligible candidate -> 0
one eligible semantic candidate -> winner semantic score
otherwise -> clamp(5_000 + finalWinnerScore - bestAlternativeScore, 0, 10_000)
```

If hysteresis retains an incumbent below the raw challenger, confidence may correctly remain low.

---

## 36. Source Health Key and State

Health is keyed by:

```text
SourceOperationKey(sourceId, READ_DOCUMENT)
```

Reader health never implies health for catalog search, chapter listing, authentication, or other plugin operations.

`SourceHealthState` contains bounded values only:

```text
circuitState = CLOSED | OPEN | HALF_OPEN
consecutivePenalizingFailures
successEwmaBasisPoints
recentLatencySamplesMillis (max 20)
openCount
openedAtEpochMillis?
nextProbeAtEpochMillis?
```

No unbounded event history is retained.

---

## 37. Health EWMA and Circuit Policy

Default health policy:

```text
alpha                           = 2_000 basis points
open consecutive failure count = 3
open reliability threshold     = 5_500
minimum cooldown               = 30 seconds
maximum cooldown               = 5 minutes
max latency samples            = 20
```

For reliability sample `10_000` success or `0` penalizing failure:

```text
next = (alpha * sample + (10_000 - alpha) * previous) / 10_000
```

A CLOSED circuit opens only when both are true:

```text
consecutivePenalizingFailures >= 3
successEwmaBasisPoints <= 5_500
```

From neutral `10_000`, three default penalizing failures produce `5_120`, so the third consecutive qualifying failure opens the circuit.

Cooldown:

```text
30 seconds * 2^(openCount - 1)
```

clamped to five minutes.

`advance()` performs OPEN -> HALF_OPEN after the cooldown without inventing a success/failure observation.

---

## 38. HALF_OPEN Probe Ownership

HALF_OPEN probe serialization is an effect-layer resource rule.

The process-wide Reader source limiter owns one probe lease per `SourceOperationKey`.

Before planning, the coordinator may try to acquire plan-scoped probe leases for HALF_OPEN candidate sources. The engine receives only:

```text
halfOpenProbePermitted = true | false
```

Unused leases are released when the plan is finalized/cancelled. The permit token itself never enters `:reader:engine`.

A HALF_OPEN remote path without a held permit is rejected.

A successful permitted probe closes the circuit and resets `openCount`. A penalizing permitted probe failure reopens the circuit with the next cooldown.

### 38.1 Late normal success cannot bypass OPEN

A normal remote attempt may have started while CLOSED and complete after another concurrent observation has already OPENed the circuit.

Therefore observations distinguish:

```text
NORMAL_REMOTE_ATTEMPT
HALF_OPEN_PROBE
```

A normal success observed while state is OPEN may update bounded reliability/latency facts, but it **does not close the circuit, reset `openCount`, reset the OPEN failure counters, or cancel its cooldown**. Only a successful permitted HALF_OPEN probe can close an OPEN/HALF_OPEN cycle.

This removes completion-order races from circuit authority.

---

## 39. Process-Lifetime Health Registry

`:reader` owns a process-lifetime in-memory `ReaderSourceHealthRegistry` distinct from per-screen sessions.

It:

- keys state by `SourceOperationKey`;
- serializes reducer transitions per key;
- calls `advance()` using an effect-layer wall clock before snapshot creation;
- exposes immutable health snapshots;
- records observation origin for diagnostics;
- resets to neutral on process restart;
- uses no Room schema or persistence migration.

Two Reader sessions share fresh process-local source health, but never share generation/plan/commit state.

---

## 40. Typed Observation and Recovery Taxonomy

String plugin/runtime codes remain adapter-boundary input only. They must be classified before pure health reasoning.

The engine-level observation family includes:

```text
Success.Remote(kind, latencyMillis)
Success.Local

TransportFailure.Timeout
TransportFailure.Connection
TransportFailure.RateLimited

AuthFailure.CredentialsUnavailable
SourceStateFailure.DisabledOrNotInstalled
SourceStateFailure.OperationUnavailable
PluginPolicyFailure.ConfigurationOrCapability

ReleaseFailure.NotFound

ContentFailure.EmptyDocument
ContentFailure.InvalidDocument
ContentFailure.CorruptDocument
LocalFailure.MissingBlob
LocalFailure.FingerprintOrDecodeMismatch

Cancellation.Navigation
Cancellation.HedgeLoser
Cancellation.PrefetchPreempted

RuntimeFailure.Unexpected
```

### 40.1 Penalizing observations

Remote reliability sample `0` by default:

```text
remote timeout
remote connection failure
remote rate limit
remote invalid/empty/corrupt Reader payload after source execution
```

Valid normal/probe remote success contributes `10_000` and a valid non-negative latency sample.

### 40.2 Non-penalizing observations

Do not lower source reliability by default:

```text
valid local success
release-specific not found
auth/session/credential failure
disabled or not-installed source
operation unavailable
plugin capability/domain/header/configuration policy failure
local cache miss/corruption/fingerprint failure
navigation cancellation
hedge-loser cancellation
prefetch preemption
unexpected internal runtime failure without explicit source blame
```

This explicitly protects Wave 10 auth failures such as `plugin.auth_unavailable` and `plugin.http_credentials_failed` from opening the Reader source circuit.

### 40.3 Recovery scope

Typed failures also carry:

```text
RELEASE_SCOPED
SOURCE_SCOPED
LOCAL_SCOPED
CLIENT_SCOPED
```

Default mapping:

```text
release not found                           -> RELEASE_SCOPED
transport timeout/connection/rate limit    -> SOURCE_SCOPED
auth/disabled/not-installed/unavailable    -> SOURCE_SCOPED, non-penalizing
plugin policy/configuration failure         -> SOURCE_SCOPED, non-penalizing
remote invalid content                      -> SOURCE_SCOPED, penalizing
local miss/corrupt/fingerprint              -> LOCAL_SCOPED, non-penalizing
cancellation/unexpected internal            -> CLIENT_SCOPED, non-penalizing by default
```

A `SOURCE_SCOPED` terminal result suppresses remaining REMOTE attempts from that source within the same generation. It does not suppress already planned valid local attempts for releases from that source.

---

## 41. Current Runtime Error-Code Classification Requirements

The adapter must maintain an **exhaustive classification table for every string code reachable from the current `CONTENT_CHAPTER` invocation/sanitization boundary**. Adding a new reachable runtime code without adding a classification is a test failure; no unclassified string is allowed to enter the pure health reducer.

The current source inventory includes, among others:

```text
plugin.execution_timeout
plugin.http_request_failed
plugin.auth_unavailable
plugin.http_credentials_failed
plugin.disabled
plugin.not_installed
plugin.operation_unavailable
plugin.http_domain_denied
plugin.capability_denied
plugin.http_managed_header_collision
reader.document_empty
reader.document_too_large
reader.document_title_invalid
reader.document_block_invalid
reader.source_payload_invalid
reader.source_failed
```

The table also covers the other currently reachable HTTP/runtime policy/output codes (for example `plugin.http_read_failed`, HTTPS/URL/budget/redirect/response-policy failures, runtime execution/sandbox failures, and plugin output/package/capability failures) according to whether the Reader source itself is being exercised, whether the host/runtime is the failing owner, and whether repeated attempts should be suppressed for that generation. Tests use exact code entries rather than prefix matching.

Unknown remote retryable errors may be mapped to transport connection failure only when the adapter knows the error originated from the remote source invocation boundary. Unknown non-retryable/internal failures remain non-penalizing unless explicitly classified.

Classification is tested as a table, not implemented by broad substring heuristics.

---

## 42. Source Execution Limiter

The current `PluginReaderDocumentSource` has an invocation mutex per source object, but `PluginReaderDocumentSourceRegistry.enabled()` can create new source objects. That is insufficient as a process-wide Reader concurrency guarantee across sessions/prefetch.

`:reader` therefore owns a process-wide Reader execution limiter:

```text
max active Reader REMOTE attempts per sourceId = 1
```

Foreground Reader work has priority over Reader prefetch work for the same source.

Prefetch preemption is best-effort. If a plugin ignores cancellation, foreground correctness still comes from generation/plan identity and the limiter does not assume immediate transport termination.

This limiter governs Reader-originated `READ_DOCUMENT` work only; it does not redefine global plugin-runtime concurrency for unrelated operations.

---

## 43. Runtime Session Boundary

Mutable Reader execution state is scoped to one logical Reader screen/session.

Conceptually:

```text
ReaderRouteSession
  sessionId
  next generation counter
  active generation?
  active plan revision
  committed content identity
  transition target
  session-local latest chapter graph/revision
  prefetch ownership
```

`ReaderViewModel` owns one session for its lifetime through a factory.

A singleton repository/coordinator service may exist, but it must not store one global active Reader generation. Two Reader screens cannot cancel or invalidate each other merely because they share singleton services.

---

## 44. Generation and Plan Revision Semantics

Correctness does not rely on cancellation alone.

### 44.1 Generation

Every new foreground **user intent** receives a monotonically increasing `ReaderGenerationId` within its session:

```text
initial foreground load
navigate to another chapter
explicitly select another release/source
retry after exhaustion/transition failure
explicit reload after a prior commit
```

This makes user intent boundaries unambiguous.

### 44.2 Plan revision

`ReaderPlanRevision` changes only when the same active, uncommitted generation must be replanned because external decision facts hard-invalidate its plan.

Examples:

```text
active release removed from latest graph
source becomes unavailable/OPEN
local path proven invalid and current decision requires replan
network becomes definitely offline with no viable local/ongoing route
routing language policy changes while target is still uncommitted
```

Ordinary attempt failure normally follows the already-planned recovery chain and does not automatically call `plan()` again.

### 44.3 Attempt identity

Every runtime attempt/result carries:

```text
sessionId
generationId
planRevision
attemptId
targetChapterId
```

A result may affect visible Reader state only if session, generation, and plan revision still match the active execution and the semantic commit gate is open.

No second execution revision or opaque plan hash is introduced.

---

## 45. Coordinator State Machine

Foreground execution uses semantic states:

```text
Idle
Planning
Executing
Recovering
Validating
Committed
Exhausted
Cancelled
```

Transition presentation is separate from effect-state internals.

Boolean combinations such as `isLoading + didRetry + hasFallback` must not become execution semantics.

---

## 46. Exactly-One Visible Commit

For every foreground generation:

```text
successful visible commit count <= 1
```

The coordinator owns one serialized semantic commit gate.

After a valid result commits:

- committed chapter/release/document identity swaps atomically;
- all other active competitive attempts are cancelled best-effort;
- late results cannot mutate visible state;
- a losing hedge is a non-penalizing cancellation;
- that generation is closed for visible commits.

Health observation recording and visible commit ownership are separate concerns, but once an attempt is client-cancelled because of navigation/hedge loss/prefetch preemption, its eventual late transport result does not get reclassified into a source success/failure for that cancelled ownership.

---

## 47. Document Validation Boundary

A fetch/read is not a semantic success until Reader validation passes.

Current plugin sanitizer already validates and canonicalizes remote payloads. The coordinator/executor still validates the materialized `ReaderDocument` before commit/store success for invariants available at this layer:

```text
non-empty document topology
valid ReaderDocument fields already expected by current contracts
LOCAL requested fingerprint == decoded document fingerprint
persistability rules before automatic-cache write
```

The current `ReaderDocument` contains no canonical chapter/release identity, so HES does not claim to validate a field that does not exist.

Actual image URL load success is a presentation/network concern, not part of pure route validation.

A local locator mismatch/decode corruption quarantines that exact `(releaseId, fingerprint)` and continues to remote/fallback. It does not penalize source health. Quarantine is best-effort: coroutine cancellation still propagates, while a non-cancellation quarantine failure is recorded diagnostically and the current generation still marks that local path unusable rather than blocking remote recovery.

A valid remote document may commit even if best-effort automatic-cache persistence fails; cache persistence is not part of semantic Reader success. Cancellation still propagates.

A valid new remote fingerprint is allowed as described in Section 17.

---

## 48. Competitive Primary/Hedge Execution

V1 permits:

```text
one primary attempt
zero or one hedge attempt
ordered sequential recovery attempts
```

A hedge exists only when the **initial primary attempt is REMOTE**. Primary and hedge must use different `sourceId` values.

If a semantic winner starts from LOCAL, local failure enters sequential recovery; v1 does not launch a hedge merely because an unverified local copy failed.

Default foreground hedge eligibility requires:

```text
primary REMOTE p95 >= 1_200 ms
primary has >= 3 remote latency samples
alternate has eligible REMOTE path
alternate source differs from primary source
alternate REMOTE access score >= 8_000
alternate remote reliability >= 9_000
network == UNMETERED
intent == FOREGROUND
```

Default hedge delay:

```text
650 ms
```

Prefetch never hedges by default.

The alternate threshold uses the alternate's **REMOTE access evaluation**, not a semantic score inflated by a local cache path.

---

## 49. Competitive Completion Determinism

Pure planning is deterministic. Real execution observes real completion time.

The coordinator uses an injected monotonic scheduler/clock and records `completedAtNanos` immediately after document validation succeeds, before valid-completion publication or coordinator notification delivery. The clock boundary is monotonic and non-decreasing: identical timestamps are legal and must remain observable by winner policy.

Winner rule:

```text
earliest valid completedAtNanos
then PRIMARY over HEDGE when timestamps are equal
then stable attemptId
```

Callback delivery order is never policy.

Tests may deliberately deliver notifications in a different order from logical completion timestamps; the semantic winner must remain the same.

Wall-clock health time and monotonic execution time are separate abstractions and must never be substituted for one another.

---

## 50. Prefetch

Prefetch uses the same engine with:

```text
routingIntent = PREFETCH
```

V1 window:

```text
N     foreground
N + 1 at most one session-owned prefetch target
N - 1 no proactive network fetch by default
N + 2 and later no automatic prefetch
```

Prefetch policy:

- no hedge;
- at most one Reader remote prefetch attempt active process-wide, still subject to the per-source limiter;
- proactive remote prefetch only on `UNMETERED` by default;
- local inspection remains valid on any network class;
- successful valid remote prefetch updates `READ_DOCUMENT` reliability because it exercised the same operation;
- client preemption/cancellation does not penalize health;
- persist only through existing Reader document storage rules;
- non-persistable image documents may complete prefetch execution but do not become a reusable local cache fact.

When foreground navigation reaches a prefetched chapter, the Reader always assembles a **fresh FOREGROUND snapshot** from the latest chapter graph/cache/network/health/preferences. Prefetch completion never makes one release authoritative.

---

## 51. UI Continuity Contract

The current Reader behavior clears the document and changes committed saved chapter identity before the target succeeds. HES intentionally supersedes that behavior.

Presentation/runtime state separates:

```text
CommittedReaderContent
  chapterId
  releaseId
  document
  exact progress/restoration identity

ReaderTransitionTarget
  chapterId
  explicitReleaseId?
  generationId
```

Flow:

```text
committed chapter N stays visible and owns progress
        |
        +-- target N+1 plans/executes
        |
        v
N+1 valid document wins commit gate
        |
        v
atomic committed identity + document swap to N+1
```

### 51.1 Saved state

Committed saved keys change only after successful semantic commit.

Starting `openChapter(N+1)` must not overwrite the committed `CHAPTER_ID_KEY`. Starting `selectRelease(B)` must not overwrite committed release state before B succeeds.

V1 does not need to persist a pending transition target. Process death safely restores the last committed chapter/release rather than masquerading an unfinished target as committed reading progress.

### 51.2 Progress ownership

While a transition is pending, `updatePosition()` continues to write progress for the committed chapter/release/document fingerprint.

After commit, progress ownership atomically moves to the new committed content.

Exact block/offset restoration is applied only when persisted release and fingerprint match the committed document.

### 51.3 Failure presentation

If no content has ever committed, exhaustion produces normal initial unavailable/error state.

If target N+1 exhausts while N is committed:

```text
N remains visible
transition target remains retryable/non-destructive
source-by-source attempt noise remains diagnostics
```

The UI must not flicker through every source failure.

### 51.4 Rapid navigation

For N -> N+1 -> N+2 before either target commits:

- N remains committed/visible;
- N+1 generation becomes stale/cancelled;
- only the latest valid N+2 generation may commit;
- stale N+1 results cannot modify saved state, progress ownership, or presentation.

---

## 52. Presentation-Facing Semantic States

A small semantic model is sufficient:

```text
LoadingInitial
Ready(committedContent)
Transitioning(committedContent, target)
TransitionFailed(committedContent, target, retryable)
Unavailable // only when no committed content exists
```

The exact Kotlin representation may preserve current public UI shape during migration, but the committed-versus-target semantics are mandatory.

Wave 10 Reader preference behavior remains orthogonal:

- first routing waits for first persisted preference snapshot;
- font changes update presentation without routing replan;
- failed font writes roll back to persisted preference state;
- cancellation propagates.

---

## 53. Integration with Existing Reader Contracts

Migration is compatibility-first, but the new explicit Reader session is the long-term production contract.

Existing contracts preserved through the migration envelope:

```text
ReaderLoadRequest / ReaderLoadResult façade while callers still exist
ReaderDocumentRepository façade while needed
ReaderDocumentSource
ReaderDocumentSourceRegistry
ReaderSourceAvailability
ReaderDocumentStore
ReaderDocument
ReadingProgressRepository / ReadingProgressService semantics
DownloadAwareReaderDocumentStore storage behavior
coroutine cancellation propagation
fingerprint-addressed quarantine behavior
```

Production `feature:reader` migrates to one explicit `ReaderRouteSession` rather than relying on a process-global generation hidden inside singleton `ReaderDocumentRepository`.

The legacy repository façade may remain as a compatibility wrapper during migration, but HES correctness does not require inventing missing chapter-identity facts to force old tests through the new engine prematurely.

---

## 54. ReleaseSelector Migration Envelope

`ReleaseSelector` remains a temporary migration reference.

Migration preserves the overlap envelope where the old and new model express the same facts:

```text
explicit release
persisted target resume release
previous/committed source
language order
completeness fixture
publication time
stable IDs
```

Legacy per-release `ReleaseHealth` does not have a semantically equivalent HES source-operation fact and is excluded from strict differential equivalence. Production HES health enters through the typed process health model instead of an invented compatibility field.

Legacy source-group differential fixtures may use explicit trusted keys, but production does not infer them.

Adaptive behavior may intentionally diverge only through named golden tests.

---

## 55. Wave 10 Compatibility and Ownership

Wave 10 remains authoritative for:

```text
ReaderPreferencesPort
languageOrder persistence/source
font-scale persistence behavior
settings capability
background/auth/notification scope
Room MIGRATION_10_11
schema-11 notification persistence
```

HES:

- consumes `ReaderPreferencesPort`;
- does not add `:reader -> :settings`;
- does not create a second settings repository/port;
- does not redefine `MIGRATION_10_11`;
- introduces no Room schema 12 migration;
- does not claim to finish currently incomplete/unwired cache quota behavior;
- explicitly classifies Wave 10 auth failure codes as non-penalizing Reader health observations.

Any future cache-quota wiring is a separate owner decision and must not be smuggled into routing architecture.

---

## 56. Architecture Gates

HES-v1 Reader compliance requires both dependency and source-level enforcement.

### 56.1 Module policy

`config/architecture/module-boundaries.json` must declare:

```text
:reader:engine
platform = jvm
dependencyMode = exact
productionDependencies = [":core:common"]
```

`:reader` gains one implementation dependency on `:reader:engine` and retains its other current dependencies. No `:reader -> :settings` edge is added.

### 56.2 Constitutional build-logic test

The build must assert that the reference engine:

```text
is JVM, not Android
uses openstory.kotlin.jvm
has exact project dependency {:core:common}
does not apply Android/Compose/Hilt/Room/serialization plugins
does not depend on known effect modules
```

Editing policy JSON and build dependencies together must not be enough to silently relax this reference boundary.

### 56.3 Shell/static verification

The new nested engine source root must be covered explicitly by package/source-boundary scripts. Historical static tests that hard-code the pre-Wave-10 module count or forbid now-valid Wave 10 routes/application work must be reconciled before HES acceptance; HES must not weaken them by ignoring failures.

---

## 57. HES-v1 Verification Layers

Every HES-v1 reasoner requires:

### L1 — Example tests

Named readable behavior scenarios.

### L2 — Permutation tests

Candidate input shuffle must preserve exact semantic decision, access plan, hedge, fallback order, rejections, and trace semantic fields.

### L3 — Replay tests

Identical snapshot/policy/version gives exactly equal pure decision.

### L4 — Property tests

Deterministic generators prove invariants such as:

```text
candidate with no usable path cannot win
OFFLINE cannot produce REMOTE attempt
OPEN remote cannot execute
valid local can survive disabled/OPEN remote source
eligible explicit release is semantic winner
all scores stay bounded
attempt identities are unique
stable ties use stable IDs
LOCAL attempt always has fingerprint locator
```

### L5 — Metamorphic tests

Examples:

```text
adding a disabled candidate cannot change an existing winner
improving winner reliability alone cannot make it lose
permuting rejected candidates cannot change eligible ranking
below-threshold challenger improvement cannot switch incumbent
removing an unavailable path cannot recreate it
adding an unrelated old cache fingerprint cannot turn exact-resume MISS into KNOWN_INVALID
```

### L6 — Differential migration tests

Old selector vs new engine only inside the explicitly defined representable overlap envelope.

### L7 — Concurrent model tests

Virtual scheduler/model exploration for navigation, graph updates, hard replans, hedge timing, completion-vs-notification ordering, cancellation, process health, and two-session isolation.

---

## 58. Golden Reader Scenarios

R2 retains the original long-lived scenarios and adds rebase-specific contracts.

```text
G01_STICKY_HEALTHY_SOURCE
G02_TRANSIENT_FAILURE_DOES_NOT_SWITCH
G03_DEGRADED_SOURCE_HEDGED
G04_OPEN_REMOTE_SOURCE_WITHOUT_LOCAL_SWITCHES
G05_EXPLICIT_ELIGIBLE_RELEASE_WINS
G06_EXPLICIT_RELEASE_FAILURE_FALLS_BACK
G07_PREFETCHED_LOCAL_COPY_CAN_WIN
G08_STALE_PREFETCH_IS_REPLANNED
G09_TRUSTED_GROUP_CONTINUITY_ACROSS_SOURCE
G10_STRICT_LANGUAGE_NEVER_SWITCHES_TO_UNLISTED
G11_HEDGE_REDUCES_TAIL_LATENCY
G12_HEDGE_LOSER_NOT_PENALIZED
G13_NAVIGATION_CANCEL_NOT_PENALIZED
G14_CORRUPT_LOCAL_CONTENT_QUARANTINED
G15_STALE_GENERATION_CANNOT_COMMIT
G16_STALE_REPLAN_CANNOT_COMMIT
G17_ALL_ROUTES_EXHAUSTED
G18_INPUT_PERMUTATION_STABLE
G19_HALF_OPEN_REQUIRES_PROBE_PERMIT
G20_USER_OVERRIDE_CANNOT_BYPASS_HARD_REJECTION

G21_RESUME_FINGERPRINT_CHANGE_ACCEPTS_VALID_REMOTE_WITHOUT_STALE_EXACT_OFFSET
G22_OPEN_REMOTE_SOURCE_WITH_EXACT_LOCAL_COPY_REMAINS_LOCALLY_COMPETITIVE
G23_REACTIVE_GRAPH_REMOVAL_INVALIDATES_ACTIVE_PLAN
G24_AUTH_CREDENTIAL_FAILURE_DOES_NOT_OPEN_SOURCE_CIRCUIT
G25_AUTOMATIC_CACHE_LOCATOR_SELECTION_IS_DETERMINISTIC
G26_TWO_READER_SESSIONS_SHARE_HEALTH_BUT_NOT_EXECUTION_STATE
```

G09 remains an engine fixture only until trusted production group metadata exists.

---

## 59. Runtime Invariants

Coordinator/model tests must prove:

1. stale session/generation results cannot mutate visible state;
2. stale plan revisions cannot mutate visible state;
3. one generation has at most one visible semantic commit;
4. navigation cancellation does not penalize source health;
5. hedge-loser cancellation does not penalize source health;
6. prefetch preemption does not penalize source health;
7. fallbacks remain sequential outside the primary/hedge competitive pair;
8. callback delivery order cannot override logical completion-time ordering;
9. hard graph/source/network/policy invalidation increments plan revision;
10. soft metric/candidate changes do not unnecessarily revoke valid work;
11. a local path never requires its remote source to be healthy/enabled;
12. every LOCAL attempt has one deterministic fingerprint locator;
13. expected resume fingerprint mismatch against newly fetched valid remote content is not source corruption;
14. auth/configuration failures cannot lower source reliability by default;
15. two sessions cannot invalidate each other's generation/revision state;
16. process health observations are serialized by source-operation key;
17. late normal success while circuit is OPEN cannot bypass HALF_OPEN probe policy;
18. exhausted transition routes expose one semantic transition failure, not per-source UI flicker.

---

## 60. Stress and Complexity Contracts

Pure routing target complexity:

```text
eligibility/evaluation   O(n)
ranking                  O(n log n)
route construction       O(n)
health percentile        O(k log k), k <= 20
```

No O(n²) release-pair matrix belongs in Reader routing v1.

Stress scale includes at least:

```text
50 sources
500 releases
1,000 repeated replans
sampled input permutations
rapid navigation generations
reactive chapter graph revisions
bounded 20-sample source-health histories
multiple Reader sessions sharing process health
```

Stress tests are deterministic scale contracts, not brittle host wall-clock benchmarks.

---

## 61. Performance Targets and Observability

For typical Reader routing (`candidate count <= 32`), pure planning should be sub-millisecond to low-single-digit milliseconds on ordinary mobile-class hardware and allocate only bounded short-lived data proportional to candidate count.

Runtime observability may record after the decision:

```text
time to first valid document p50/p95
fallback recovery latency
prefetch hit ratio
hedge launch/win rate
remote attempts per committed chapter
source-switch rate between consecutive chapters
local exact/unverified hit ratio
route exhaustion rate
source circuit OPEN/HALF_OPEN counts
```

Telemetry never feeds decisions implicitly and is never called from `:reader:engine`.

---

## 62. Policy Validation

Policy construction fails fast for invalid configuration.

At minimum:

```text
BasisPoints in 0..10_000
weights non-negative and total exactly 10_000
switch thresholds in 0..10_000
hedge delay >= 0
hedge score/reliability thresholds in 0..10_000
health alpha in 1..10_000
cooldown minimum > 0
cooldown maximum >= minimum
max latency samples in 1..20 for v1
STRICT_ALLOWED language order non-empty
language order has no blank/normalized duplicates
max recovery attempts in 0..6
max planned foreground REMOTE in 1..4
runtime remote ceilings >= policy planned ceilings where required
```

Contradictory values are programming/configuration errors, not silently normalized policy.

---

## 63. Public API Stability

The pure engine exports only Reader-domain contracts actually consumed by `:reader`:

```text
ReaderRouteEngine
ReaderRoutingSnapshot
ReaderRoutingPolicy
ReaderRouteDecision
RoutingCandidate
routing value/reason/rejection types
SourceHealthReducer
SourceHealthState
SourceObservation
HealthPolicy
```

Room entities, download metadata rows, plugin DTOs, Android network types, analytics payloads, UI state, session IDs, coroutine jobs, probe tokens, and persistence DTOs are not engine API.

---

## 64. Persistence Boundary

`SourceHealthState` is not a Room entity and is not serialized directly as a public storage contract.

Initial v1:

```text
process start -> neutral health
process lifetime -> health shared across Reader sessions
process restart -> unpersisted health discarded
```

If durable health is later justified:

```text
persistence record <-> effect adapter <-> SourceHealthState
```

requires a separate persistence design/migration ownership review. The pure engine contract does not change merely because storage is added.

---

## 65. Decision Trace

Every decision returns a structured trace sufficient to answer “why?”.

Trace fields include:

```text
HES contract version
algorithm version
policy version
plan revision
chapter graph revision
canonical ordered candidate IDs
candidate/access rejections
semantic feature vector
preferred-access feature vector
semantic weighted score
remote-access evaluation when present
stable ranking
incumbent candidate + incumbent kind
raw challenger
switch advantage
required hysteresis threshold
final semantic winner
local/remote route construction
hedge directive or typed omission reason
final decision reason
health origin (startup/process observed)
```

Trace collections use canonical stable ordering.

Diagnostics are observational only: enabling/disabling/persisting them cannot change the decision.

Reason classes are separated rather than overloaded:

```text
DecisionReason
AccessReason
RejectionCode
DiagnosticNote
```

Stable final decision reasons include at least:

```text
EXPLICIT_ELIGIBLE_RELEASE
TOP_RANKED_NO_INCUMBENT
TARGET_RESUME_INCUMBENT_RETAINED
INCUMBENT_RETAINED_BY_HYSTERESIS
CHALLENGER_EXCEEDED_SWITCH_THRESHOLD
INCUMBENT_UNAVAILABLE
NO_ELIGIBLE_CANDIDATE
```

---

## 66. Decision Replay

A pure route decision is replayable from:

```text
ReaderRoutingSnapshot
ReaderRoutingPolicy
Reader routing algorithm version
```

All decision-relevant time, network, cache-locator, health, chapter-graph revision, explicit selection, and continuity facts are explicit.

No hidden singleton, thread state, collection iteration, random value, Android service, or implicit clock may influence pure output.

Execution outcomes are separate observations and are replayed through coordinator model tests plus the pure health reducer.

---

## 67. Migration Sequence

The architectural milestones remain ordered, with one new entry gate and corrected boundaries.

### R0 — Wave 10 acceptance/governance repair

Close or explicitly rebase the still-open Wave 10 boundary before modifying overlapping surfaces. Repair stale `current-state`/roadmap/static guard contradictions as their owning governance work.

### M0 — HES guardrails

Add the pure engine module, exact architecture policy, source-boundary coverage, and constitutional tests before adaptive production behavior.

### M1 — Compatibility reasoner

Implement pure deterministic compatibility behavior over the representable legacy envelope and differential tests.

### M2 — Session/coordinator compatibility boundary

Introduce session-scoped execution identities and extract the existing sequential/local-first execution behavior without enabling health/hysteresis/hedge.

### M3 — Typed observations and process health

Introduce failure classification, validation, pure health reducer, process registry, and traces. Hedge remains disabled.

### M4 — Adaptive routing and bounded effect facts

Enable eligibility, access-aware fixed-point ranking, resume/source continuity, hysteresis, bounded local-locator inspection, network facts, reactive chapter-graph invalidation, and plan revision safety.

### M5 — Committed-versus-target UI continuity and prefetch

Migrate Reader presentation to explicit session state, zero-blank transitions, correct progress ownership, then add bounded N+1 prefetch through the same engine.

### M6 — Hedged foreground execution

Enable one hedge only after virtual scheduler, process source limiter, plan/generation guards, completion registry, and exactly-one commit tests are green.

### M7 — Legacy cleanup and HES-v1 freeze

Remove obsolete selector internals only after production cutover and complete golden/property/concurrent/stress/architecture evidence.

---

## 68. Acceptance Criteria

The design is implemented successfully only when all applicable criteria are proven on the final tree:

1. Wave 10 entry acceptance is either closed before HES or explicitly rebased with fresh evidence.
2. `:reader:engine` is a pure JVM module with exact production project dependency `:core:common` only.
3. Engine source guards reject Android, chapters, Reader effects, plugins, downloads/storage, coroutines, serialization, filesystem, and network imports.
4. `:reader` consumes the engine without re-exporting engine types as a downstream transport contract.
5. The post-HES production graph has 17 production modules and Room remains schema 11.
6. `MIGRATION_10_11` is untouched and no HES Room migration is introduced.
7. Existing `ReaderPreferencesPort` remains the sole Reader settings/preference owner.
8. Initial Reader routing waits for first persisted preference and first reactive chapter-graph snapshot.
9. Existing `ReaderSourceAvailability` supplies source-enabled facts; no duplicate port is introduced.
10. Production `sourceGroupKey` remains null until trusted metadata exists.
11. Production completeness uses neutral/full `10_000` until a real completeness fact exists.
12. Candidate input permutation cannot alter an exact pure decision.
13. Identical snapshot/policy/version replays exactly.
14. Every LOCAL attempt contains an explicit fingerprint locator.
15. Cache-aware ranking uses bounded metadata inspection and does not decode every candidate document.
16. Unrelated historical cache fingerprints are never labeled corrupt merely because they do not match resume fingerprint.
17. Exact resume fingerprint local hit can route even when the remote source is disabled or circuit-OPEN.
18. Valid new remote content with a changed fingerprint may commit without being classified as source corruption.
19. Exact saved block/offset restoration is not applied across a changed document fingerprint.
20. OFFLINE rejects only remote paths.
21. Explicit eligible release wins semantic selection but cannot bypass hard access/language rejection.
22. Strict language mode never routes an unlisted language.
23. Automatic target resume release participates as the strongest non-explicit incumbent when eligible.
24. Hysteresis prevents automatic switching below configured thresholds.
25. One/two default penalizing failures do not OPEN a neutral circuit; the third qualifying failure does.
26. HALF_OPEN remote access requires a held process probe lease.
27. A late normal success cannot close an already OPEN circuit outside HALF_OPEN probe semantics.
28. Auth/credential/configuration failures do not lower Reader source reliability by default.
29. Navigation, hedge-loser, and prefetch-preemption cancellations do not penalize source health.
30. Source health is operation-specific for `READ_DOCUMENT` and process-shared across Reader sessions.
31. Process restart resets unpersisted Reader source health to neutral.
32. Per-screen generation/plan/commit state is isolated between Reader sessions.
33. Every new foreground user intent receives a new generation.
34. Hard external invalidation within one active intent increments the one plan revision counter.
35. Ordinary attempt failure consumes the planned recovery chain instead of blindly replanning the same primary.
36. Stale generation or plan revision cannot mutate visible Reader state or saved committed identity.
37. One generation visibly commits at most one semantic document.
38. Current committed chapter/document/progress remains authoritative while another target loads.
39. Failed transition leaves committed content visible and does not overwrite saved committed chapter/release keys.
40. Reactive chapter graph removal of an active release invalidates its active plan; lower-ranked additions remain soft by default.
41. Reader uses one session-local chapter observation instead of one `snapshot()` per navigation or one immutable lifetime snapshot.
42. Foreground execution launches at most one primary plus one hedge concurrently.
43. Foreground total remote attempts stay within four and process per-source Reader remote concurrency stays at one.
44. Competitive winner follows logical completion time, then PRIMARY, then stable attempt ID—not callback delivery order.
45. Prefetch uses the same engine, is bounded to N+1, has no default hedge, and never becomes authoritative solely by completing.
46. Non-persistable prefetch content does not falsely appear as a reusable local cache fact.
47. Source-by-source recovery noise does not create UI flicker.
48. G01–G26 pass.
49. Stress tests preserve bounded health state and expected O(n log n) route complexity.
50. Gradle architecture verification and shell/package source verification both cover `:reader:engine`.
51. Historical static guards are updated to current intentional architecture rather than ignored or weakened.
52. Reader, Downloads, Feature Reader, App, and relevant Room regressions pass on the final applicable verification matrix.

---

## 69. Documentation Ownership and Supersession

After this design is approved for implementation:

- this R2 design owns Reader source routing, continuity, Reader source health, access-path planning, prefetch/hedge semantics, session execution correctness, and the HES-v1 reference-engine architecture;
- Wave 10 remains authoritative for settings/auth/background/notifications and schema-11 migration ownership;
- canonical/chapter engines remain authoritative for chapter aggregation/mapping;
- current Reader selector documents become migration history after final cutover;
- current project status still belongs in `docs/project/current-state.md`, but that file must be corrected to the actual accepted/implemented boundary before it can resume its documented canonical role;
- acceptance evidence belongs in checkpoints, not checkbox state inside this design;
- this design does not declare Wave 10 accepted or HES implemented.

---

## 70. HES-v1 Invariants Summary

```text
Facts are immutable.
Policy is explicit and versioned.
Reasoning is pure and deterministic.
Time is supplied, never read inside the reasoner.
Input order is not policy.
Hard access/language eligibility precedes preference and scoring.
Do not invent facts the current domain does not expose.
Semantic release and access path are distinct.
Every local path has a concrete fingerprint locator.
Resume fingerprint is restoration/cache identity, not a remote integrity oracle.
Valid local access is independent of remote source health.
Continuity uses persisted target resume + committed source/group context.
Automatic source switching uses hysteresis.
Health is a bounded pure reducer keyed by READ_DOCUMENT.
Auth/configuration/cancellation/local-cache failures do not poison source reliability.
Only a permitted HALF_OPEN probe closes an OPEN cycle.
Effects live outside :reader:engine.
Reader execution state is session-scoped; process health/source limits are shared separately.
Every foreground user intent has a new generation.
External hard replans use one plan-revision counter.
One generation commits at most one visible document.
Stale generation/revision results cannot commit.
Committed content owns progress until atomic replacement succeeds.
Chapter facts are reactive per session without per-navigation full reload.
Prefetch is advisory and bounded.
Primary + one hedge may compete; fallbacks remain sequential.
Callback delivery order is not policy.
Diagnostics explain decisions but never change them.
The build and static guards enforce the HES reference boundary.
Room stays schema 11 in v1.
Wave 10 ownership is preserved.
```

---

# 71. Detailed Self-Review Record

This section records the contradiction/gap review performed after rebasing the design against the supplied source tree. Items are written as design defects first, then the R2 resolution.

## SR-01 — Stale repository-status baseline

**Conflict:** the old design described Wave 10 ownership largely as future/planned work, while actual source already contains settings, feature settings, Reader preferences integration, auth/background/notification implementation, schema 11, and 16 production modules. `current-state.md` still says 14 modules/schema 10/Wave 10 not started.

**Resolution:** R2 explicitly defines the source-derived implementation baseline and separately preserves checkpoint authority for acceptance. It does not use stale `current-state.md` as an implementation fact until governance is repaired.

**Remaining condition:** governance files/static historical guards must be reconciled at R0; this design does not falsify their history.

---

## SR-02 — Starting HES would move an unaccepted Wave 10 boundary

**Conflict:** adding `:reader:engine` changes the production graph and HES touches several surfaces still listed in the open Wave 10 final regression matrix.

**Resolution:** add required Entry Gate R0. Default sequence closes Wave 10 on its 16-module/schema-11 tree first. Any earlier HES implementation is an explicit acceptance rebase requiring fresh Wave 10 evidence.

---

## SR-03 — Engine type leakage through `api(:reader:engine)`

**Conflict:** the earlier implementation direction would expose pure engine fact types transitively through `:reader` so `:downloads`/`:app` adapters could implement ports with engine values. That would turn the reference-engine API into a cross-module transport format and make later engine refactors costly.

**Resolution:** `:reader` uses an implementation dependency. Effect ports use Reader-owned DTOs and `:reader` maps them internally to engine facts. Downstream modules do not depend directly on `:reader:engine`.

---

## SR-04 — Package guard would miss nested `reader/engine`

**Conflict:** current `verify-package-boundaries.sh` scans `reader/src/main`, not `reader/engine/src/main`. Adding a nested Gradle module without updating static coverage creates a constitutional blind spot.

**Resolution:** R2 requires explicit static scan coverage for the engine root in addition to Gradle architecture policy/build-logic tests.

---

## SR-05 — Cache status without fingerprint cannot execute automatic cache

**Conflict:** old `CandidateCacheState` could say “available” while route attempts contained only release/source/access mode. Current automatic cache requires `read(releaseId, fingerprint)` and `readCurrent()` only resolves explicit downloads.

**Resolution:** `CandidateLocalAccess` carries the chosen fingerprint and every LOCAL `RouteAttempt` contains `localFingerprint`.

---

## SR-06 — Metadata mismatch was incorrectly equivalent to local corruption

**Conflict:** if progress expects fingerprint A and metadata contains fingerprint B, calling B `KNOWN_INVALID` would quarantine or devalue valid historical content without ever reading it.

**Resolution:** different fingerprints are simply an exact-resume MISS. `KNOWN_INVALID` requires an actual local read/decode/validation observation for the selected fingerprint.

---

## SR-07 — No deterministic automatic-cache choice without expected fingerprint

**Conflict:** more than one automatic-cache fingerprint can exist for one release. “Available unverified” without a selection order is non-deterministic.

**Resolution:** newest completed explicit download first; otherwise automatic cache by last-access descending then fingerprint ascending. The chosen fingerprint enters the fact/route.

---

## SR-08 — Resume fingerprint was treated like provider truth

**Conflict:** old validation language could reject a fresh remote document because its fingerprint differs from persisted progress. In current Reader, fingerprint is host-computed content identity, not a provider-supplied immutable version contract.

**Resolution:** fingerprint is exact cache/restoration identity. Valid changed remote content can commit; any saved block/offset/fraction restoration requires both release and fingerprint equality. Cross-fingerprint approximate restoration is explicitly out of v1 scope.

---

## SR-09 — Candidate hard rejections referenced facts that do not exist

**Conflict:** `MANUALLY_BLOCKED`, `STALE_MAPPING`, and generic chapter/content identity mismatch were specified without current Reader fact sources.

**Resolution:** remove them from v1 rather than invent data. Canonical group membership remains upstream chapter authority. Future explicit mapping facts may add rules later.

---

## SR-10 — Remote health could accidentally downgrade a valid local copy

**Conflict:** old candidate-level health/reliability features were defined from remote source state even when the candidate remained routable through local storage. With large health weights, an OPEN/degraded remote source could make a downloaded exact local copy lose.

**Resolution:** feature evaluation is access-aware. A usable local preferred path gets local access health/reliability/latency values independent of remote health. Remote path evaluation remains available separately for hedge/recovery diagnostics.

---

## SR-11 — Hedge threshold could use a score inflated by local cache

**Conflict:** if one semantic candidate's final score came from a local path, using that same score to approve it as a REMOTE hedge would measure the wrong access path.

**Resolution:** hedge eligibility uses the candidate's explicit REMOTE access evaluation score and remote reliability, not its local-preferred semantic score.

---

## SR-12 — Lifetime `cachedChapterGroups` contradicts dynamic replan semantics

**Conflict:** current Reader freezes chapter facts for the ViewModel lifetime, while old HES requirements talk about candidate removal/addition and stale plan invalidation.

**Resolution:** one `ChapterRepository.observe(storyId)` subscription per Reader session supplies the latest graph and a session-local graph revision. This preserves performance while making graph invalidation observable.

---

## SR-13 — Reactive graph could regress the prior performance contract

**Conflict:** replacing cache with `snapshot()` on each navigation would fix staleness but violate the accepted Reader performance intent.

**Resolution:** no per-navigation full snapshot. One reactive grouped flow is held for the session. Historical static test must move from implementation-name assertion to behavior/ownership assertion.

---

## SR-14 — Initial preference ordering could regress Wave 10

**Conflict:** moving load initiation into a new session/coordinator could start routing before persisted `languageOrder` arrives, recreating the exact Wave 10 preference race already fixed in source.

**Resolution:** initial plan waits for both the first Reader preference snapshot and first chapter graph snapshot. Font write rollback/cancellation contracts remain explicit acceptance criteria.

---

## SR-15 — Duplicate source-availability abstraction

**Conflict:** designing a new “source enabled” effect port would duplicate current `ReaderSourceAvailability` and risk divergent plugin capability rules.

**Resolution:** reuse `ReaderSourceAvailability` as the source-enabled fact owner. Remote invocation may still return disabled/not-installed/unavailable due races; those observations are classified and handled.

---

## SR-16 — Wave 10 auth failures could poison Reader health

**Conflict:** `plugin.http_credentials_failed` is retryable in current runtime. A broad “retryable remote failure = connection failure” classifier would penalize/open a healthy source because user/auth composition failed.

**Resolution:** explicit table mapping makes auth/credentials/configuration/source-state failures source-scoped for recovery but non-penalizing for reliability.

---

## SR-17 — Per-object source mutex is not process Reader concurrency control

**Conflict:** `PluginReaderDocumentSource` has a mutex, but the registry creates source objects. Multiple Reader sessions/prefetch can therefore bypass a per-instance mutex.

**Resolution:** add a Reader process-wide per-source `READ_DOCUMENT` limiter outside the pure engine. It coordinates sessions/prefetch without redefining unrelated plugin runtime concurrency.

---

## SR-18 — OPEN circuit could be closed by a late normal success

**Conflict:** concurrent normal attempts can complete after other failures have already OPENed a source. A naïve reducer that treats every success in OPEN as close would bypass cooldown/probe policy depending on completion order.

**Resolution:** observations distinguish normal remote attempts from permitted HALF_OPEN probes. Normal success in OPEN may update bounded quality data but does not close the circuit.

---

## SR-19 — Probe “permission fact” had a race if checked without ownership

**Conflict:** asking “is probe available?” without holding a lease lets two sessions both plan HALF_OPEN probes.

**Resolution:** the effect coordinator acquires plan-scoped probe leases before marking `halfOpenProbePermitted=true`. The token remains outside the engine and unused leases are released.

---

## SR-20 — User intent and hard replan were ambiguously sharing generations

**Conflict:** the previous wording sometimes treated explicit source changes as a plan-revision change and sometimes as a new foreground generation, making the “one commit per generation” invariant hard to reason about after a prior commit.

**Resolution:** every foreground user intent (navigation, explicit release selection, retry/reload) creates a new generation. Plan revision is reserved for external invalidation inside that still-uncommitted intent.

---

## SR-21 — Current `selectRelease()` mutates committed saved state too early

**Conflict:** current code stores selected release before the new release succeeds. The old zero-blank section emphasized chapter navigation but must cover same-chapter source switching too.

**Resolution:** committed chapter/release saved keys update only at semantic commit for both navigation and release selection.

---

## SR-22 — Current progress owner follows mutable target `chapterId`

**Conflict:** once zero-blank UI keeps old document visible, leaving `updatePosition()` bound to mutable target chapter ID would write old content progress into the new target chapter.

**Resolution:** `CommittedReaderContent` owns progress identity until atomic commit. Transition target has a separate identity.

---

## SR-23 — Prefetch of image-only/non-persistable document could be treated as cache hit

**Conflict:** current `ReaderDocument` with remote image pages is not locally persistable. A prefetch “success” cannot automatically become local availability.

**Resolution:** prefetch remote success may update source health, but only successful store persistence produces later local metadata. Foreground always replans from actual cache facts.

---

## SR-24 — Network policy could accidentally consume Wave 10 background settings

**Conflict:** Wave 10 has `requireUnmeteredNetwork`, but it is part of existing background/settings policy. Reusing it silently for foreground Reader would change ownership and product behavior.

**Resolution:** Reader network class is a new narrow effect fact. Foreground/prefetch network behavior is explicit Reader routing policy. No `:reader -> :settings` dependency is added.

---

## SR-25 — Cache quota setting exists but is not currently wired into Reader store

**Conflict:** Wave 10 settings own `automaticCacheQuotaBytes`, while current ReaderModule still constructs `DownloadAwareReaderDocumentStore` with its default quota. An HES design that assumes a completed cache-policy port would be false; fixing it silently would expand scope.

**Resolution:** HES cache-facts work uses current storage behavior and does not claim cache-quota ownership. Any quota wiring remains separate Wave 10/settings/storage remediation unless explicitly brought into scope later.

---

## SR-26 — Legacy façade lacks a clean explicit target identity

**Conflict:** `ReaderLoadRequest` currently contains candidates/policy/fingerprints but no target canonical chapter ID. Forcing strict new target-identity safety through this old façade would require synthetic/invented facts in tests and compatibility callers.

**Resolution:** HES v1 does not add unsupported candidate identity rejection. The explicit session used by production Reader owns the real target chapter. The legacy façade remains a migration wrapper until cutover rather than dictating the pure engine model.

---

## SR-27 — Decision reason categories were overloaded

**Conflict:** prior reason lists mixed semantic winner explanations (`EXPLICIT...`), access preference (`LOCAL...`), filters (`STRICT_LANGUAGE_FILTER`), and terminal state (`NO_ELIGIBLE...`) in one conceptual enum.

**Resolution:** separate `DecisionReason`, `AccessReason`, `RejectionCode`, and `DiagnosticNote`. Trace remains richer while stable semantics are clearer.

---

## SR-28 — Source health observation after client cancellation was ambiguous

**Conflict:** best-effort cancellation means a plugin can finish later. Recording that late result as health success/failure after navigation could make cancelled, ownerless work mutate health nondeterministically.

**Resolution:** once ownership marks an attempt as client-cancelled, its terminal Reader observation is the typed cancellation and late transport completion cannot be promoted back into a source observation for that attempt.

---

## SR-29 — Static repository baseline is not globally green

**Conflict:** current executable audit shows the main architecture/package/Wave 10 policy checks pass, while historical contract tests still fail on obsolete assumptions (for example the pre-feature-settings module count and future-route/Application `onCreate` restrictions).

**Resolution:** R0 distinguishes implementation presence from accepted clean baseline. HES acceptance requires stale historical guards to be intentionally reconciled, not ignored. The design does not claim the supplied ZIP is already globally green.

---

## SR-30 — Local I/O failure and confirmed corruption were conflated

**Conflict:** the current compatibility repository quarantines after broad local read exceptions, but an I/O/storage availability exception is not proof that a fingerprint is corrupt. Carrying that behavior into HES would delete data based on an untyped infrastructure failure.

**Resolution:** v1 quarantines only confirmed fingerprint/decode corruption. Generic local storage failure is local/client-scoped, non-penalizing, and falls back without asserting corruption. Quarantine failure itself is diagnostic/non-blocking except cancellation.

---

## SR-31 — Cache persistence could accidentally become part of semantic remote success

**Conflict:** a valid remote document is usable even if the automatic cache cannot be written. Coupling semantic commit to cache persistence would turn disk/quota/transient storage failures into false Reader source failures.

**Resolution:** validate remote content first; automatic-cache persistence is best-effort and does not gate visible commit. Cancellation remains authoritative.

---

## SR-32 — Scope inflation risk


**Conflict:** once HES touches Reader, cache, Room metadata, source runtime observations, network, and UI transitions, it could easily become a disguised Wave 10 cleanup or generic engine-framework rewrite.

**Resolution:** v1 explicitly excludes cache quota wiring, persistent health, canonical/chapter changes, plugin protocol changes, group metadata creation, other engine migrations, and generic framework construction. Only contracts necessary for adaptive Reader routing are changed.

---

## 72. Self-Review Result

After the R2 corrections above, no known contradiction is intentionally left between:

```text
current Wave 10 source ownership
schema-11 migration ownership
Reader preference startup behavior
chapter graph freshness and Reader performance intent
fingerprint-addressed local storage
source availability
Wave 10 auth failures
local-vs-remote health semantics
session/process concurrency boundaries
generation/revision/commit semantics
prefetch/hedge behavior
HES pure-module constraints
```

The remaining **external entry risks** are deliberately not disguised as design gaps:

1. Wave 10 final host/API 26/API 37 acceptance remains open in the supplied checkpoint.
2. `current-state.md` and parts of roadmap/static shell tests are stale relative to current source and must be reconciled by governance before the HES implementation boundary is frozen.
3. Trusted cross-provider source-group metadata still does not exist; production group continuity remains disabled (`null`).
4. Reader cache quota settings exist in Wave 10 settings but are not currently wired into `DownloadAwareReaderDocumentStore`; this remains out of HES scope unless separately approved.
5. Full Gradle/device verification of the supplied archive is evidence work, not a design assumption. HES implementation acceptance must record fresh executable outputs rather than infer success from source presence.

These are explicit prerequisites/follow-ups, not unresolved architectural ambiguity in the Reader/HES contract.
