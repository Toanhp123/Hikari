# Hikari Content State Contract v1 (CSC-v1) Design

**Date:** 2026-08-27
**Status:** Proposed architecture specification; design-only, not yet implemented
**Primary scope:** `:feature:catalog` presentation state and UX readiness semantics
**Out of scope for v1 migration:** `:feature:reader`, domain cache/sync engines, WorkManager scheduling, Search/Mapping/Reconciliation screens unless explicitly audited in the follow-up phase

## 1. Purpose

Hikari already has a coherent visual language for loading, empty, error, refresh, and non-blocking feedback in `:core:designsystem`. The remaining problem is below the visual layer: feature ViewModels do not share one precise definition of when content is still pending, when an empty snapshot is authoritative, when cached/current content remains usable, or which dependencies are allowed to block first render.

CSC-v1 standardizes those **presentation-state semantics** without centralizing domain lifetime ownership.

The target architecture is:

```text
Domain lifetime owners
Catalog / Library / Chapters / Downloads / Reader
cache, persistence, freshness, sync, retry, routing
                 |
                 v
Feature-owned readiness and projection
required dependency vs enrichment
bootstrap completion / projection completeness
usable-content decision
                 |
                 v
CSC-v1 feature contract
Pending | Ready(value) | Failed
manual RefreshState
retained observation semantics
                 |
                 v
:core:designsystem
Loading / Empty / Error / PullRefresh / InlineFeedback
```

CSC-v1 is intentionally **strong in invariants and weak in ownership**. It must not become a global loading engine, cache engine, refresh engine, repository coordinator, or background-work coordinator.

## 2. Authority and compatibility

This specification is authoritative only for the presentation-state concerns it defines.

It does **not** supersede:

- Catalog metadata TTL, stale/revalidation, canonical convergence, or source-selection ownership;
- Library membership and mapping ownership;
- Chapter synchronization/aggregation ownership;
- Download durability and WorkManager ownership;
- Reader HES-v1 routing, continuity, cache, prefetch, or session semantics;
- `:core:designsystem` ownership of generic visual primitives.

It extends the existing design-system rule that the design system owns **how** generic states are rendered while features own **when** those states apply.

### 2.1 Stale refresh documentation

Older refresh UX documents state that Story Chapters are not pull-refreshable until a chapter-sync pipeline exists. That condition is no longer true in the current tree:

- `ChapterListViewModel.refresh()` calls `ChapterSyncService.sync(storyId)` directly;
- `ChapterList` uses `HikariPullToRefresh`;
- Wave 10 explicitly preserves direct manual chapter refresh through `ChapterSyncService` while background work calls the same capability through workers.

CSC-v1 therefore treats **Chapter pull-to-refresh as current accepted runtime behavior**. Any active documentation that still says Chapters are intentionally non-refreshable must be corrected during the CSC migration. Historical/archive documents remain historical evidence and do not need rewriting.

## 3. Current-state problem statement

The current production tree uses several incompatible conventions for the same UX concepts:

```text
loading: Boolean
refreshing: Boolean
failure: String? / typed failure?
nullable primary model
synthetic emptyList()/emptySet()/0 fallbacks
feature-local preserveLatest() helpers
Flow.catch { emit(emptyList()) }
stateIn(initialValue = loading state)
```

Those constructs are individually reasonable but collectively erase distinctions that the UI needs.

The important missing distinctions are:

1. the first authoritative snapshot has not arrived;
2. the first authoritative snapshot arrived and is truly empty;
3. usable content exists while a manual refresh runs;
4. usable content exists while background/domain revalidation runs;
5. a required observation failed before any usable content existed;
6. an observation failed after usable content existed;
7. a dependency is required to determine content membership;
8. a dependency only enriches labels, artwork, progress, or capabilities;
9. optional data has not arrived yet versus it authoritatively says “none/zero/false”.

A single `loading: Boolean` cannot represent these correctly.

## 4. Audited current behavior

The following table records the relevant runtime baseline from the current repository.

| Screen | Current readiness shape | Current drift/gap |
|---|---|---|
| Downloads | `combine(downloads, chapters, catalog, command/failure)`; all three data flows must emit before `loading=false` | Chapters/catalog block first render even though projector already has release/story fallback; observation/command `state.failure` is not rendered by `DownloadsScreen`; failure before first download snapshot can become synthetic empty |
| Updates | `combine(library, catalog, chapters, mappings, reader availability)` | All dependencies block; catalog and reader capability are enrichment; `state.failure` is not rendered by `UpdatesScreen`; empty Library cannot short-circuit immediately |
| Library | Library membership is combined with Catalog/Mapping/Progress; enrichment helper emits synthetic empty values immediately | Pending mapping observation is presented as `NO_MAPPING`; enrichment failure is silently swallowed; Library membership observation itself has no failure mapping; Catalog title affects query/TITLE sort and Progress affects LAST_ACTIVITY sort, so dependency roles are control-sensitive |
| Home | Library/catalog/progress/chapters/mappings/download count/reader capability are all combined before content projection | Entire Home blocks on section-level enrichment; fallback `0`/empty values can become false facts after early failure; only one merged observation failure is exposed; shelf-only `isEmpty` can show the no-Library CTA for a non-empty all-DROPPED Library |
| Discover | Manual `initialLoading`, `refreshing`, home observation, visible canonical projections, semantic projection | Initial empty-cache bootstrap is presented as both loading and refreshing; non-empty homes with unresolved canonical projections can project false-empty or compact/reorder ranked slots; canonical prewarm swallows per-story outcomes; one `globalFailure` collapses refresh and observation issue priority |
| Story | `story: StoryUiModel?`, `refreshing`, one shared `failure`; canonical `Preparing` maps to `story=null` | No explicit Pending state; `ensureReady()` may complete while canonical is still Preparing; observation/bootstrap/source-preference/refresh failures share one field; personal enrichment emits synthetic empties |
| Chapters | Chapter groups are combined with reader capability; repository failure catches and emits empty list | Reader capability unnecessarily blocks first chapter render; first observation failure becomes empty + inline failure rather than blocking failure; observation/refresh/correction failures share one field |

These are presentation-state inconsistencies. They are not evidence that domain engines should be merged.

## 5. Goals

CSC-v1 must:

1. distinguish Pending from authoritative Ready(empty);
2. keep usable content visible during manual refresh;
3. keep usable content visible during background/domain revalidation;
4. distinguish required dependencies from enrichment dependencies;
5. allow progressive enrichment without re-entering full-screen loading;
6. prevent synthetic empty/zero/false defaults from impersonating authoritative facts;
7. preserve the latest usable value across observation failure where safe;
8. convert a required first-observation failure into a blocking Failed state;
9. surface non-blocking observation/refresh/command failures through explicit channels;
10. preserve current feature/domain ownership and dependency direction;
11. make screen state transitions directly testable as architecture contracts;
12. eliminate feature-local `preserveLatest()` variants once migrated;
13. fix current active documentation that contradicts the chapter refresh runtime;
14. keep the abstraction local to `:feature:catalog` until reuse outside that feature is proven.

## 6. Non-goals

CSC-v1 must not:

- create a global `UiState` in `:core:designsystem`;
- move feature state machines into `:core:designsystem`;
- introduce `:core:loading`, `:core:presentation`, `:sync`, or a new engine module;
- decide Catalog TTL or stale/revalidation policy;
- decide Chapter sync cadence, paging, or aggregation policy;
- decide Download WorkManager policy;
- decide Reader cache eviction, routing, prefetch, or continuity;
- route manual refresh through WorkManager;
- expose WorkManager state as screen loading state by default;
- add a universal network/offline/freshness model;
- redesign screen hierarchy or visual styling;
- migrate Reader HES-v1 to generic content state in this wave;
- silently broaden v1 into Search, Mapping, or Reconciliation behavior.

## 7. Core architecture decisions

### DECISION-CSC-001 — CSC-v1 is a contract, not an engine

CSC-v1 is a small set of feature-local value types, observation semantics, and invariants. It owns no data, performs no I/O, schedules no work, and has no process-wide singleton lifecycle.

There must be no `ContentStateEngine`, `LoadingEngine`, `CacheEngine`, `RefreshEngine`, or equivalent coordinator.

### DECISION-CSC-002 — The first implementation lives in `:feature:catalog`

The initial contract belongs under a feature-local package such as:

```text
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/
```

It must not be placed in `:core:common` merely to make it reusable, and it must not be placed in `:core:designsystem` because the design system explicitly does not own feature state machines.

Promotion to a cross-feature module is a later architecture decision after at least one non-Catalog feature proves identical semantics are useful.

### DECISION-CSC-003 — Pending is not Empty

The canonical content algebra is:

```kotlin
sealed interface ContentState<out T> {
    data object Pending : ContentState<Nothing>
    data class Ready<T>(val value: T) : ContentState<T>
    data class Failed(val failure: CatalogUiFailure) : ContentState<Nothing>
}
```

`Empty` is intentionally **not** a top-level state.

An empty collection is authoritative only after required dependencies have produced enough facts to emit:

```text
Ready(emptyValue)
```

Therefore:

```text
Pending != Ready(empty)
```

Feature-specific distinctions such as true-empty, filtered-empty, and section-empty remain derived from a `Ready` payload.

### DECISION-CSC-004 — Screen-level blocking failures use a minimal feature failure value

CSC-v1 may use one feature-local presentation failure value:

```kotlin
data class CatalogUiFailure(
    val code: String,
    val retryable: Boolean,
)
```

It contains no Throwable, repository object, plugin object, origin enum, navigation effect, or retry implementation.

Domain-specific reports may remain richer. For example, Discover may retain a per-plugin refresh report while also using `CatalogUiFailure` for a screen-level boundary failure.

### DECISION-CSC-005 — Manual refresh is orthogonal to content availability

Refresh must not be encoded by replacing `Ready` with `Pending`.

The shared feature-level shape is intentionally small:

```kotlin
data class RefreshState(
    val inProgress: Boolean = false,
    val failure: CatalogUiFailure? = null,
)
```

Valid transition:

```text
Ready(A) + Idle
    -> Ready(A) + Refreshing
    -> Ready(B) + Idle
```

Valid refresh failure:

```text
Ready(A) + Refreshing
    -> Ready(A) + refresh failure
```

Invalid when A remains usable:

```text
Ready(A)
    -> Pending
    -> Ready(B)
```

### DECISION-CSC-006 — Bootstrap is not manual refresh

A screen may call the same underlying domain service during initial bootstrap and user refresh, but those invocations do not have the same presentation meaning.

Initial bootstrap with no usable content is represented through `ContentState.Pending`. It must not set `RefreshState.inProgress=true` merely because the same refresh service is reused internally.

This specifically changes the current Discover presentation where empty-cache bootstrap is simultaneously `loading=true` and `refreshing=true`.

### DECISION-CSC-007 — Background work is not manual refresh

WorkManager, periodic chapter sync, download workers, canonical maintenance, and other durable work must not flip a screen into full-screen loading or manual refreshing merely because they run.

Their normal presentation path is:

```text
Ready(A)
  -> repository/domain facts change
  -> Ready(B)
```

A background operation is shown explicitly only if the product needs that domain status, for example a Download record whose domain state is `RUNNING`.

### DECISION-CSC-008 — Domain owners decide freshness and cache usability

CSC-v1 does not define global TTL, Fresh/Stale enums, revalidation intervals, or cache eviction.

A domain owner decides whether an existing snapshot remains usable. If it is usable, presentation must remain `Ready` while refresh/revalidation proceeds. If the domain invalidates the snapshot and no usable content remains, the feature may return to `Pending` or `Failed` according to the new identity/outcome.

This preserves ownership such as `CatalogMetadataCoordinator` TTL/version logic and Reader cache identity/eviction policy.

### DECISION-CSC-009 — Required and enrichment dependencies are explicit architecture roles

A dependency is **required** when the screen cannot truthfully determine the projection it currently promises without it. That promise includes not only membership and authoritative emptiness, but also a user-selected filter, sort, rank, or other semantic ordering whose correctness depends on the input.

A dependency is **enrichment** when the primary projection remains semantically correct without it and the missing fact can be represented as unknown/fallback/local-pending rather than a false negative or knowingly incorrect ordering.

Rules:

1. Data used to include/exclude records is normally required for the relevant projection.
2. Data used by an active filter, semantic rank, or explicitly selected sort is required for that local projection until its first authoritative snapshot for the current projection identity arrives.
3. Data used only for title, artwork, optional progress, or an action capability is enrichment only when its absence does not make the currently promised filter/sort/rank semantically false.
4. An enrichment may never use `empty`, `0`, or `false` to mean “not observed yet” if that value has a real domain meaning.
5. A dependency may be required for one subsection/filter/sort while remaining enrichment for the screen as a whole.
6. “Authoritative” is relative to the current durable/domain snapshot, not to all possible future background work. CSC-v1 must not keep a screen Pending merely because a background worker may discover new facts later.
7. Required/enrichment classification belongs to the feature projector/readiness owner, not to a generic engine.

### DECISION-CSC-010 — Synthetic negative facts are forbidden

CSC-v1 must eliminate patterns where an observation that has not emitted is represented as an authoritative domain absence, including:

```text
no mapping observation yet -> NO_MAPPING
no download count yet      -> 0 downloads
no reader capability yet   -> definitely unreadable
no chapter snapshot yet    -> no chapters exist
observation failed first   -> authoritative empty collection
```

Pending enrichment must instead use an explicit unknown/local-pending representation, omit the dependent presentation, or retain a prior valid value.

### DECISION-CSC-011 — Observation retention is keyed, retained, and explicitly restartable

Feature-local `preserveLatest()` variants are replaced by one retained-observation contract. Its conceptual state is:

```text
ObservationState<T>
    Pending
    Available(value, latestFailure?)
    Unavailable(failure)
```

Every retained observation also has a **readiness identity/key**. The key is the identity for which the value is valid: for example a Story ID, a set of Library Story IDs, or another feature-owned projection key.

Required behavior:

- before a real upstream value for the current key, state is Pending;
- first real `emptyList()` is `Available(emptyList())`, not Pending;
- cancellation is always rethrown;
- failure before any value is `Unavailable(failure)`;
- failure after a value preserves the latest value as `Available(latest, failure)` when that value remains valid for the same key;
- changing the key invalidates values/issues from the old key unless the feature can prove they remain valid for the new key; old-key data must never satisfy new-key readiness;
- ordinary lifecycle stop/restart for the same key preserves the latest usable value;
- a Flow exception terminates that collection unless the source itself recovers, so CSC-v1 must not assume `catch` will later resume emitting;
- **normal upstream completion before the current attempt emits any real value is also terminal for that attempt**. It must become `Unavailable(failure)` when no same-key value exists, or `Available(latest, issue)` when a same-key retained value exists. It must never leave a required observation in `Pending` forever;
- normal completion **after at least one real value** is not itself a failure; the emitted value remains authoritative for that key;
- a retryable observation therefore has an **explicit restart path** for the same key (for example a retry epoch/trigger or equivalent feature-owned reconstruction of the observation);
- retry before any usable value may transition `Unavailable -> Pending -> Available/Unavailable`;
- retry after a usable value keeps the retained value visible while the observation restarts;
- the prior issue is cleared only by a successful value for the same key, by key invalidation, or by an explicit feature rule that supersedes that issue;
- the shared primitive must not add an automatic tight retry/backoff loop around repositories; retry policy remains feature/domain-owned;
- it never emits a synthetic initial empty/zero/false value;
- it must not hide a first-observation failure by converting it to empty.

The exact implementation may use `StateFlow`, `shareIn`, `stateIn`, `flatMapLatest` over a retry/key epoch, or a small holder, but these semantics are normative.

### DECISION-CSC-012 — Full-screen readiness and subsection readiness may differ

A screen must not remain globally Pending merely because one optional shelf, badge, count, or action capability is unresolved.

Examples:

- Home can be Ready from Library membership while Continue Reading, latest updates, download count, and artwork enrich progressively.
- Chapters can be Ready from chapter groups while Reader capability is unresolved.
- Library can be Ready from membership while source-state mapping is locally resolving, provided it does not fabricate `NO_MAPPING`.

CSC-v1 does not require a generic nested state framework. Feature-specific local readiness should be introduced only where a real subsection needs it.

### DECISION-CSC-013 — Failure channels are scoped, and observation issues are dependency-keyed

Different operations have different UX consequences. A screen may need separate channels for:

```text
content blocking failure
observation issue(s) with retained content
manual refresh failure
command/action failure
partial domain report
```

A single `failure = commandFailure ?: observationFailure ?: refreshFailure` is not a stable architecture contract.

The observation channel itself may contain failures from multiple independent inputs. Therefore a migrated screen with more than one fallible observation must retain observation issues **per dependency/readiness key** (for example a small keyed map/set or equivalent feature-owned structure). One dependency recovering must not accidentally clear another dependency's still-active failure, and one failure must not silently overwrite another.

Failures may share the same minimal `CatalogUiFailure` value; the dependency/operation context belongs to the containing state channel/key rather than being hidden in one global catch-all failure object. UI may choose one deterministic issue to render at a time, but the ViewModel must preserve correct issue lifetime internally.

The retained-observation primitive's per-key failure and the screen-level `observationIssues` collection are **not two mutable authorities**. The primitive owns/retains the issue for its observation key; any screen-level collection is a derived aggregation of those keyed observation states (or an equivalent single-source representation). Implementations must not maintain a second independently-cleared failure map that can drift from the observation states.

### DECISION-CSC-014 — Resubscription must not blank retained content

`SharingStarted.WhileSubscribed(...)` may stop upstream collection when the screen leaves composition, but a ViewModel that already reached `Ready(A)` must not visibly reset to Pending when a subscriber returns to the same content identity merely because upstream collection restarts.

A retained `StateFlow` value remains the presentation authority until new facts replace it.

A new Pending state is allowed when:

- the actual content identity changes and the previous content is not valid for the new identity; or
- the owning domain explicitly declares the old snapshot unusable.

### DECISION-CSC-015 — Reader HES-v1 is excluded from v1 migration

Reader has committed-document, route/session, transition, replan, fallback, prefetch, and cache identity semantics that are materially different from Catalog projection screens.

CSC-v1 must not be used as a reason to flatten Reader into a generic loader. Reader may be audited later for reusable principles, but no cross-feature abstraction is promoted until that audit proves compatibility.

### DECISION-CSC-016 — Do not add a universal activity/freshness state machine in v1

Earlier design exploration considered generic `Activity` and `Fresh/Stale` axes. The current repository does not need a global version of those concepts to solve the audited defects.

CSC-v1 standardizes only:

- content availability;
- manual refresh;
- retained observation;
- dependency readiness;
- cache visibility invariants.

Domain-specific activity/freshness remains with domain owners unless future evidence justifies promotion.

### DECISION-CSC-017 — Blocking content retry is not manual refresh

`retryable=true` is not sufficient by itself. Every retryable blocking `ContentState.Failed` must have a feature-owned **content retry boundary** that re-runs the operation that can actually restore content readiness.

Examples:

- a failed Chapter repository observation retries/restarts that observation boundary; it is not automatically the same thing as `ChapterSyncService.sync()`;
- a Story canonical bootstrap failure retries canonical bootstrap/readiness, not source-detail metadata refresh;
- a Discover home-observation/bootstrap failure retries the failed content-readiness boundary and must not merely toggle pull-to-refresh chrome.

A screen may expose this as a single `retryContent()` action, but that action must target the active failed readiness boundary. Reusing `onRefresh` is allowed only when the feature proves both actions are the same operation.

### DECISION-CSC-018 — Pending must have an observable exit condition

`Pending` may not mean “we hope some unrelated background work eventually fixes this.” For every Pending branch the feature must be able to name the event/result that advances it to Ready or Failed.

If an explicit bootstrap/resolve attempt completes but returns a still-unusable domain state and there is no observable continuation owned by that readiness boundary, the screen must leave Pending and classify the terminal outcome (normally Failed, setup-required, or another feature-owned no-content outcome). Likewise, if a required observation Flow completes normally before emitting any value for the current readiness key, that completion is an observable terminal event and must be mapped out of Pending rather than silently leaving a permanent skeleton.

This rule prevents permanent skeletons caused both by domain APIs whose method names imply readiness but can legally return a non-ready state and by finite/contract-violating observations that complete without a first snapshot.

### DECISION-CSC-019 — Partial Ready must preserve semantic slot/order stability

Progressive enrichment is allowed only when the partial projection is honest and visually stable. Unresolved required/ranking inputs must not be compacted away in a manner that temporarily promotes lower-ranked content into a higher semantic slot and then moves it later.

For ranked/ordered projections, a feature must either:

- wait for the unresolved earlier slot to settle;
- reserve a local placeholder for that slot; or
- prove that the unresolved input cannot change already-rendered ordering.

This is especially important for Discover, where current `mapNotNull` compaction can make a lower-ranked story temporarily become the Popular hero.

### DECISION-CSC-020 — Refresh failure lifetime is explicit

`RefreshState.failure` describes the most recent **completed attempt for the current content/readiness identity**. It is not a permanent screen error history.

For a user-initiated refresh:

```text
Idle(failure = oldFailure)
    -> new refresh starts
Refreshing(failure = null)
    -> success
Idle(failure = null)

or
Refreshing(failure = null)
    -> failure
Idle(failure = newFailure)
```

A deduplicated refresh request while one is already in progress does not create a new attempt or mutate failure lifetime. A content/readiness identity change clears refresh failure that belongs to the old identity. Refresh success/failure never clears observation or command issues.

This prevents stale refresh errors from remaining visible while the user is actively retrying and prevents a successful attempt from leaving obsolete failure chrome behind.

### DECISION-CSC-021 — Blocking failure may summarize multiple required causes, but retry remains truthful

`ContentState.Failed(CatalogUiFailure)` is the **screen presentation summary**, not necessarily the complete internal set of failed required dependencies. A readiness owner with multiple required observations may retain keyed blocking causes internally while exposing one deterministic screen failure.

Rules:

- one required failure must not erase another required failure merely because only one message is rendered;
- `CatalogUiFailure.retryable=true` is allowed only when the exposed `retryContent()` action is a valid recovery attempt for the current blocking classification;
- when several retryable required boundaries are simultaneously blocking the same content identity, `retryContent()` must restart all currently failed boundaries needed to make progress, unless the UI explicitly exposes separate retry actions;
- if an unrecoverable/setup-required cause alone is sufficient to keep content unavailable, the screen must not advertise a generic retry that cannot make the content Ready;
- deterministic presentation priority may choose which failure code/message is shown, but internal cause lifetime remains keyed until recovery/key invalidation.

This keeps the small three-branch `ContentState` algebra without making a single failure value a lossy architecture authority.

### DECISION-CSC-022 — No-content reason belongs to the Ready payload

`ContentState.Ready` means the feature has enough authoritative facts to present the current projection. It does **not** imply that every empty-looking payload has the same product meaning.

When UX copy/actions differ, the Ready payload must preserve enough feature-owned reason/truth to distinguish cases such as:

```text
true source/feed empty
filtered empty
no Library membership
Library present but no Home sections
setup/provider unavailable but not a transient load
all candidate items deliberately excluded
```

A bare `List.empty()`/`isEmpty` flag is insufficient when it would make the screen choose incorrect copy or actions. A setup/provider-unavailable condition may be represented as a feature-specific Ready no-content reason only when it is an authoritative non-error product state; if it is a failed readiness operation, it remains `ContentState.Failed`.

This decision does not add `Empty` as a top-level CSC state. It makes the feature payload responsible for the semantic reason behind an authoritative no-content projection.

## 8. Screen contracts

### 8.1 Downloads

**Required dependency:**

- `DownloadRepository.observeAll()`.

**Enrichment:**

- `ChapterRepository.observeAll()`;
- `CatalogStoryProjectionRepository.observe()`.

**Orthogonal state:**

- pending removal;
- retry/cancel/remove command state and command failure.

Normative behavior:

```text
no download snapshot yet
    -> Pending

first download snapshot = []
    -> Ready(empty) immediately
       even if Chapter/Catalog enrichment is pending

first download snapshot has records
    -> Ready(records with stable fallback labels)

Chapter/Catalog later emit
    -> Ready(enriched records)

background worker updates Room
    -> Ready(updated records)
    -> never Pending
```

The existing fallback behavior using release/story identifiers proves Chapter/Catalog metadata is not required for first meaningful render.

Observation failure rules:

- before first Download snapshot -> `ContentState.Failed`;
- after a Download snapshot -> retain `Ready` plus visible non-blocking observation issue;
- Chapter/Catalog enrichment failure -> retain `Ready` plus non-blocking issue;
- command failure remains a command failure and must be rendered; it must not overwrite observation failure.

`DownloadsScreen` must no longer ignore screen-level observation/command failure state.

Downloads remains non-pull-refreshable because its authoritative state is durable local download state plus worker/service updates.

### 8.2 Updates

Updates is a reactive projection, not a refresh owner.

**Required for authoritative update membership:**

- Library membership;
- Chapter groups for non-empty Library story IDs;
- Content mappings for non-empty Library story IDs.

**Enrichment:**

- Catalog story projections (title/artwork fallback already exists);
- Reader source availability (controls action capability, not update membership).

Short-circuit rule:

```text
first Library snapshot = empty
    -> Ready(empty)
```

The screen must not wait for Catalog/Chapters/Mappings/Reader capability to confirm an empty Updates screen when there are no Library stories.

For non-empty Library membership:

```text
Library available
Chapters or Mappings still Pending
    -> Pending unless prior usable Updates content is retained

Library + Chapters + Mappings available
    -> Ready(projected updates)
```

Catalog may arrive later and replace StoryId fallback labels/artwork. Reader capability may arrive later and enable Reader actions. Neither may block update list content.

Observation failure must be visible. The current `UpdatesScreen` ignores `state.failure`; CSC-v1 requires blocking failure without usable content and non-blocking feedback with retained content.

### 8.3 Library

**Required for screen membership:**

- Library membership observation.

**Normally enrichment:**

- Catalog projection;
- Reading progress;
- Content mapping.

These roles are **control-sensitive**. A normally-enrichment input becomes local-required when an active control promises semantics that depend on its first authoritative snapshot for the current projection identity:

- non-blank title query or `TITLE` sort -> Catalog projection is local-required for that filtered/sorted projection;
- `LAST_ACTIVITY` sort -> Reading Progress is local-required because the current projector includes progress timestamps in `updatedAt`;
- `LINKED` / `NO_MAPPING` source filter -> Content Mapping is local-required.

Library must still be able to establish screen-level `Ready` from membership without waiting for those enrichments. Local-required means the affected list/filter/sort region has local readiness; it does not return the whole destination to full-screen Pending.

Library must be able to render a membership record with stable fallback identity before Catalog metadata arrives when no active control requires Catalog title semantics.

#### Mapping observation vs mapping-search lifecycle

CSC-v1 must distinguish two different facts:

```text
mapping repository has not emitted its first snapshot
    !=
background mapping search is actively running
```

The current `ContentMappingRepository` exposes durable mappings only; it does not expose WorkManager mapping-search lifecycle. Therefore CSC-v1 must **not** reuse `LibrarySourceState.SEARCHING` to mean merely “the mapping Flow has not emitted yet” unless a real mapping-lifecycle signal is later provided by the owning domain.

Before the first mapping snapshot, use a local observation/readiness `Unknown/Pending` representation. After an authoritative mapping snapshot arrives, absence means “no durable mapping in the current snapshot” for CSC projection purposes. It does **not** claim that every possible background mapping attempt has terminally finished. Adding a true background mapping lifecycle/status port is a separate domain capability decision, not a hidden CSC requirement.

If a LINKED/NO_MAPPING source filter is selected while the mapping observation for the current projection identity is unresolved, the UI must not claim “No stories match these filters” as authoritative. The screen remains `ContentState.Ready` from Library membership, but the filtered-list projection has local Pending semantics: retain a prior projection only if it is still semantically valid for the same controls/key; otherwise show a local resolving affordance rather than filtered-empty.

The same rule applies to query/sort semantics: an unresolved Catalog/Progress observation required by the active control must not produce a false filtered-empty or knowingly incorrect authoritative sort.

Library observation failure:

- before first membership snapshot -> blocking Failed;
- after membership snapshot -> retain Ready + non-blocking observation issue.

Enrichment/local-required failures preserve base membership. They surface as keyed non-blocking issues; the affected local projection may retain its last valid same-key result or degrade to an explicit local unavailable state, but must not fabricate negative facts.

### 8.4 Home

**Required for screen-level readiness:**

- Library membership.

All other current inputs are section/field enrichment:

- Catalog projection;
- Reading progress;
- Chapters;
- mappings;
- completed download count;
- Reader capability.

Normative behavior:

```text
Library Pending
    -> Home Pending

Library first snapshot empty
    -> Home Ready(NO_LIBRARY empty reason) immediately

Library non-empty
    -> Home Ready(base membership-derived content/summary)
       while optional sections enrich progressively
```

The screen must not block its entire first render on latest-updates or continue-reading data.

However, **Home true-empty must not be derived only from whether the currently rendered shelves are empty.** The current projector excludes `LibraryStatus.DROPPED` from its base shelves, while `HomeDashboardUiState.isEmpty` ignores `summary.libraryCount`. A Library containing only DROPPED entries can therefore trigger the existing “Find a story and add it to your Library” empty CTA even though the Library is non-empty. CSC-v1 forbids that false-empty/copy mismatch.

The migrated payload must preserve an explicit feature-owned empty reason or equivalent truth, at minimum distinguishing:

```text
NO_LIBRARY
    -> the existing add/discover CTA may be used

LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS
    -> must not claim the Library is empty; render summary/appropriate feature copy or another intentional product state
```

Missing enrichment must not fabricate facts. In particular:

- unknown download count must not be presented as authoritative `0` merely because the count flow has not emitted;
- missing progress may omit/defer Continue Reading rather than claim the story has no progress;
- missing Catalog metadata may use stable StoryId fallback;
- missing Reader capability must remain explicitly unresolved for capability presentation rather than being encoded as an authoritative false capability.

Library observation failure before the first snapshot is blocking. Enrichment failures are keyed, non-blocking once base Library content is usable.

Background sync and Room invalidation update Home through `Ready -> Ready` transitions.

### 8.5 Chapters

**Required:**

- `ChapterRepository.observe(storyId)`.

**Enrichment:**

- `ReaderSourceAvailability` capability sets.

Normative behavior:

```text
chapter observation Pending
    -> Pending

first chapter snapshot = []
    -> Ready(empty)

chapter snapshot available, Reader capability Pending
    -> Ready(chapters with capability locally unresolved)

Reader capability arrives
    -> Ready(same chapters with actions enriched)
```

A Reader capability lookup failure must not convert existing chapters into no-content.

Story-level primary read presentation must also preserve that truth. `Ready(empty chapters)` is authoritative **no chapters**, not authoritative **no Reader source**. Likewise, a non-empty canonical chapter snapshot may still contain chapter groups with **zero releases**; that is authoritative **no releases currently available**, not authoritative **no Reader source**. While Chapters are Pending/Failed, no release target exists, or Reader capability is unresolved, Story must use a neutral/local-unavailable primary action state. `Find source` is allowed only when Chapters are authoritatively non-empty, at least one release target exists, Reader capability has resolved successfully for those releases, and no readable target exists. This keeps GAP-CSC-025 from reappearing one layer above the Chapter list.

First chapter observation failure must become blocking Failed rather than `empty + inline failure`.

The current manual refresh path remains valid:

```text
ChapterListViewModel.refresh()
    -> ChapterSyncService.sync(storyId)
```

Manual refresh with `Ready(chapters)` preserves chapter content and uses `RefreshState`. Refresh failure is non-blocking when the current chapter snapshot remains usable.

Observation failure, manual refresh failure, and chapter-correction command failure must no longer share one undifferentiated `failure` field.

### 8.6 Discover

Discover owns the most complex readiness policy because source-home cache and canonical presentation converge asynchronously.

**Required inputs:**

- home snapshots for the selected content type;
- enough canonical resolution for each feed-visible semantic slot to distinguish projected content, deliberate exclusion, terminal failure, and unresolved work.

**Domain-specific readiness owner:**

- Discover bootstrap/projection pipeline, not CSC-v1 itself.

#### Empty-cache bootstrap

Current empty-cache startup reuses `performRefresh()` and therefore sets both loading and refreshing. CSC-v1 changes only the presentation semantics:

```text
no usable cached home
initial automatic source refresh/canonical bootstrap running
    -> ContentState.Pending
    -> RefreshState.inProgress = false
```

The same Catalog refresh service may still be reused internally.

A refresh call returning no usable home is **not automatically authoritative empty**. Discover must distinguish at least:

- source refresh completed with an authoritative empty feed -> candidate for `Ready(empty)` after canonical/feed readiness settles;
- no enabled/usable providers or another setup-required condition -> feature-owned Ready no-content reason when this is an authoritative setup/product state, or Failed when it represents a failed readiness operation; never false true-empty;
- terminal refresh failure with no usable cache -> Failed;
- partial provider success -> continue from the committed homes that are actually usable and keep provider failures as partial report data.

This prevents “zero results” from conflating “the feed is truly empty” with “nothing could be loaded.”

#### Canonical settlement is per expected Story ID/slot

Current `projectSemanticDiscoverContent()` uses `mapNotNull` over canonical projections. Therefore:

```text
homes non-empty
canonical projection missing
```

can incorrectly collapse into an empty semantic list or temporarily promote a lower-ranked story into a higher-ranked slot.

Discover must track a feature-owned settlement state for every expected Story ID in the ordered semantic feed, conceptually:

```text
Pending
Projected(CatalogStoryProjection)
ResolvedExcluded(reason)
Failed(CatalogUiFailure)
```

`ResolvedExcluded` covers a terminally resolved canonical Story that intentionally cannot contribute to the selected feed (for example a canonical content-type mismatch) so the pipeline does not wait forever for a projection that should never occupy that slot.

The current `DiscoverCanonicalBootstrapPipeline.prewarm()` swallows individual failures and exposes no result. It must return/emit enough per-Story outcome to establish this settlement. Importantly, `CanonicalBootstrapUseCase.ensureReady()` may legally return `CanonicalStoryState.Preparing`; method completion alone does **not** prove Ready. A returned Preparing state is a terminal unresolved outcome for that bootstrap attempt unless another explicit continuation signal is observed. It cannot leave the slot Pending forever merely because future background work might eventually change it.

#### Partial content and slot stability

Partial Discover content may become screen-level Ready only when the already-rendered semantic positions remain honest. Unresolved earlier-ranked slots must not be removed with `mapNotNull` and thereby compact lower-ranked stories upward.

For each ranked section the implementation must either:

- retain/reserve the unresolved slot with a local placeholder; or
- wait for earlier slots to settle before exposing later items in positions that would otherwise move; or
- prove the unresolved slot cannot affect the ordering already shown.

Normative results:

- no expected Story IDs after an authoritative feed refresh -> Ready(empty);
- expected IDs exist and the required leading/visible slots are unresolved -> Pending or local section Pending according to whether any stable screen content is usable;
- some slots are settled and can be rendered without semantic compaction/reordering -> Ready(partial stable content) while unresolved slots remain locally Pending;
- canonical settlement terminates with failures but usable stable content exists -> keep Ready + keyed non-blocking issues;
- all relevant slots settle as deliberate exclusions and no item remains -> Ready(empty);
- settlement terminates with failures that prevent any usable content -> Failed, not false-empty.

#### Manual refresh

When usable cached/Ready Discover content exists:

```text
Ready(A)
    + user pull refresh
    -> Ready(A) + Refreshing
    -> Ready(B) or Ready(A) + refresh failure
```

Partial per-plugin refresh failures remain domain-specific report data and may render inline without invalidating cached content. Observation/bootstrap issues remain a different keyed channel and must not be overwritten by refresh failure.

### 8.7 Story

**Required:**

- canonical Story observation for the routed Story identity;
- explicit canonical bootstrap attempt/outcome when the observed state is not yet Ready.

Story has two related but non-interchangeable identities. The **route StoryId** owns canonical observation/bootstrap/source commands for the lifetime of this ViewModel. A canonical `Ready` state may expose a different **resolved canonical StoryId** after reconciliation/merge; Library membership and ReadingProgress enrichment must be keyed to that resolved ID. Retained-state normalization must therefore compare canonical observation against the route ID and personal enrichment against the current resolved ID. A resolved-ID change must not invalidate a valid route-keyed canonical `Ready`, and old personal state for resolved ID A must not satisfy resolved ID B.

**Enrichment/orthogonal state:**

- Library membership status;
- reading progress/resume target;
- source inspection selection;
- reconciliation prompt/control state;
- best-effort Full metadata revalidation once canonical bootstrap succeeds.

Story must distinguish **bootstrap in flight** from **bootstrap attempt completed but canonical remains Preparing**. The current `CanonicalBootstrapUseCase.ensureReady()` can return `CanonicalStoryState.Preparing` without throwing (for example when canonical source records are empty), so the following mapping is required:

```text
canonical Preparing + bootstrap attempt in flight
    -> Pending

bootstrap attempt returns canonical Ready
    -> Ready(StoryUiModel)

bootstrap attempt throws/fails with no usable canonical Story
    -> Failed

bootstrap attempt completes but returns Preparing, with no explicit observable continuation owned by this readiness boundary
    -> terminal no-content classification (normally Failed)
       NOT permanent Pending
```

If some other domain process later changes the canonical observation to Ready, the screen may naturally transition from Failed/no-content to Ready. CSC-v1 does not require blocking on that unrelated future background work.

`story == null` must no longer be used to mean both Pending and unavailable. This fixes the current path where `CanonicalStoryState.Preparing` can produce `story=null`, `refreshing=false`, `failure=null`, causing `StoryScreen` to render “Story unavailable” before bootstrap reaches a terminal outcome.

Story also needs separate failure ownership. The current single `failure` field is written by:

- canonical observation failure;
- canonical bootstrap failure;
- source-preference/canonical rebuild command failure;
- source-detail manual refresh failure.

Those operations cannot clear/overwrite each other through one field. Blocking content readiness, retained observation issue, source-preference command issue, and source-detail `RefreshState` must be distinct.

Manual source-detail refresh remains orthogonal:

```text
Ready(story) + Refreshing
```

Cached/current Story content stays visible. Source-detail refresh failure remains non-blocking when Story content is usable.

A retry action shown for blocking Story failure must call the content retry boundary that restarts canonical observation/bootstrap readiness. It must **not** call the current source-detail `refresh()` merely because both actions previously shared one UI callback.

## 9. Cache visibility contract

CSC-v1 standardizes **visibility**, not cache freshness policy.

The invariant is:

> If the owning domain still considers an existing snapshot usable for the current screen identity, presentation must not replace it with blocking loading merely because newer data is being fetched, reconciled, synchronized, or revalidated.

Examples:

```text
Discover cached home + manual refresh
    -> keep Ready

Story canonical generation + Full metadata revalidation
    -> keep Ready

Chapter snapshot + periodic/manual sync
    -> keep Ready

Downloads Room records + worker progress
    -> keep Ready
```

A domain may declare existing data unusable because identity changed, policy invalidated it, or the data cannot safely represent the requested content. CSC-v1 does not override that decision.

## 10. Presentation mapping

`:core:designsystem` remains unchanged in ownership.

Canonical mapping:

```text
ContentState.Pending
    -> HikariLoadingState / existing screen skeleton composition

ContentState.Failed
    -> HikariErrorState

ContentState.Ready(value) where feature-specific value is true-empty
    -> HikariEmptyState

ContentState.Ready(value) with content
    -> real content

Ready + RefreshState.inProgress
    -> keep real content; HikariPullToRefresh owns manual refresh indicator where supported

Ready + non-blocking observation/refresh/command issue
    -> keep content; HikariInlineFeedback/snackbar/action-local feedback as appropriate
```

CSC-v1 does not add new generic Compose state components unless an implementation proves a repeated domain-neutral visual need. A new generic `ContentStateScreen` wrapper is not required by this design.

## 11. UI-state structure

Migrated ViewModels should separate the content payload from controls/orthogonal actions rather than keep flattened lists plus `loading:Boolean`.

Illustrative shapes:

```kotlin
data class DownloadsUiState(
    val content: ContentState<DownloadsContent> = ContentState.Pending,
    val pendingRemoval: ChapterReleaseId? = null,
    val observationIssues: Map<String, CatalogUiFailure> = emptyMap(),
    val commandFailure: CatalogUiFailure? = null,
)
```

```kotlin
data class ChapterListUiState(
    val storyId: StoryId,
    val content: ContentState<ChapterListContent> = ContentState.Pending,
    val refresh: RefreshState = RefreshState(),
    val selectedFilter: ChapterListFilter = ChapterListFilter.ALL,
    val showTombstones: Boolean = false,
    val observationIssues: Map<String, CatalogUiFailure> = emptyMap(),
    val commandFailure: CatalogUiFailure? = null,
)
```

Exact payload names may differ, but these invariants are required:

- content-owned fields cannot contradict the `ContentState` branch;
- `loading:Boolean` is not retained as a second authority after migration;
- refresh does not own content;
- command state does not own content;
- non-blocking issues do not force content out of Ready;
- if multiple observations can fail independently, issue lifetime is keyed per dependency/readiness identity rather than represented by one nullable observation error;
- a retryable blocking content state has a distinct feature-owned content retry action even if the screen also supports pull-to-refresh.

## 12. State-transition invariants

The following are architecture contracts and require automated tests.

### CSC-I01 — First snapshot

```text
no real required snapshot -> Pending
first real empty snapshot -> Ready(empty)
first real non-empty snapshot -> Ready(value)
```

### CSC-I02 — First required failure

```text
required observation fails before any usable snapshot -> Failed
```

It must not become Ready(empty).

### CSC-I03 — Failure after usable content

```text
Ready(A)
required/enrichment observation fails
    -> Ready(A) + non-blocking issue
```

when A remains usable.

### CSC-I04 — Manual refresh retention

```text
Ready(A) + refresh start -> Ready(A) + Refreshing
```

No blocking Pending transition is allowed while A remains usable.

### CSC-I05 — Background update

```text
Ready(A) + background work -> Ready(A)
Room/domain update -> Ready(B)
```

No screen-loading transition is introduced only because work is backgrounded.

### CSC-I06 — Enrichment does not block base content

A pending/failing enrichment cannot delay first Ready if the base projection remains semantically correct without it.

### CSC-I07 — No false negative defaults

Pending/failed optional input cannot be represented as an authoritative empty/zero/false domain fact. Likewise, one authoritative negative fact cannot be substituted for a different one: an authoritative empty Chapter list is “no chapters”, a non-empty chapter snapshot with zero release targets is “no releases”, and neither is “no readable source”.

### CSC-I08 — Authoritative empty requires required readiness

A screen cannot emit true-empty until all inputs that determine membership for that empty state are authoritative, unless a required input allows a valid short-circuit such as empty Library membership.

### CSC-I09 — Resubscription retention

A ViewModel that already holds Ready(A) must not visibly reset to Pending after `WhileSubscribed` stop/restart for the same content identity.

### CSC-I10 — Failure channels stay scoped

Observation, refresh, and command failures cannot overwrite each other merely because they share a presentation code type.

### CSC-I11 — Ready-to-Pending is exceptional

`Ready -> Pending` is allowed only if:

- content identity changes and the old value is invalid for the new identity; or
- the domain declares the prior snapshot unusable.

It is not allowed for ordinary manual refresh, background sync, optional enrichment, or resubscription.

### CSC-I12 — Bootstrap and manual refresh are distinct

Automatic no-content bootstrap may leave content Pending but must not claim that the user is performing a pull refresh.

### CSC-I13 — Retention is readiness-key scoped

A value retained for key/identity A cannot satisfy Pending/Ready for key B unless the feature explicitly proves the value is valid for both. Dynamic `flatMapLatest` projections must reset required readiness for the new key instead of leaking old-key values into the new projection.

### CSC-I14 — Pending has a named exit

Every Pending branch must have an observable completion/recovery event. Completion of a bootstrap/resolve attempt in a still-unusable domain state without an explicit continuation cannot remain Pending indefinitely. A required observation that completes normally before its first same-key value must likewise become a terminal observation failure/Unavailable state rather than remaining Pending.

### CSC-I15 — Partial projections preserve semantic order

Unresolved required/ranked slots cannot be compacted away if doing so changes the semantic position of already-rendered content. Partial Ready must reserve, wait, or otherwise prove stable ordering.

### CSC-I16 — Retry targets the failed content boundary

A retryable blocking failure must retry/restart the content-readiness operation that failed. Manual refresh, background sync, and command retry are not interchangeable by default.

### CSC-I17 — Refresh failure is attempt-scoped

```text
previous refresh failure
    + new explicit refresh attempt
    -> Refreshing(failure = null)
    -> success: Idle(failure = null)
    -> failure: Idle(failure = newFailure)
```

No observation/content/command issue is cleared by those transitions.

### CSC-I18 — Blocking retryability is truthful

If several required causes keep content unavailable, the feature may render one deterministic blocking failure but must retain enough cause state to make `retryContent()` truthful. The UI must not advertise generic Retry when the available retry action cannot make progress toward Ready.

### CSC-I19 — No-content reason remains semantically distinguishable

When two authoritative no-content situations require different user copy or actions, they cannot collapse to the same bare `isEmpty` fact. Their feature-owned Ready payload must preserve the reason/equivalent truth used by presentation.

## 13. Lifecycle and concurrency

CSC-v1 must preserve structured concurrency and existing ViewModel ownership.

Rules:

- all cancellation exceptions are rethrown;
- no global scope is introduced;
- feature observations remain scoped to `viewModelScope`;
- `SharingStarted.WhileSubscribed(5_000L)` may remain where appropriate;
- upstream restarts must not reset retained screen content for the same readiness key;
- a readiness-key change invalidates old-key retained values/issues unless the feature proves cross-key validity;
- an exception-completed Flow is not assumed to resume by itself; retryable observation failures require an explicit restart/reconstruction path;
- manual refresh deduplication remains feature-owned (`refreshing`/Job guard or equivalent);
- observation restart must not become an automatic tight retry/backoff loop; repository/domain retry policy remains with the existing owner;
- domain services remain the authority for source isolation/retry classification.

## 14. Failure semantics

CSC-v1 applies the existing design-system error rule consistently:

| Situation | Required presentation |
|---|---|
| Required first observation/bootstrap fails; no usable content | `ContentState.Failed` -> full-surface error |
| Observation fails after usable content | Keep Ready + non-blocking feedback |
| Enrichment fails but base content is usable | Keep Ready + non-blocking feedback or feature-approved silent degradation |
| Manual refresh fails with usable content | Keep Ready + refresh failure feedback |
| Manual refresh fails while authoritative empty content is still usable | Keep Ready(empty) + refresh failure feedback |
| Command fails | Keep current content; action-local/inline/snackbar feedback |
| Partial provider refresh fails while other cached/current content exists | Keep Ready + partial report/inline feedback |
| User retries blocking Failed state | Restart the failed content-readiness boundary; show Pending only when no usable content exists |

A feature may deliberately make an enrichment failure silent only if the product does not promise that enrichment and the absence is not confused with a negative fact. Silent swallowing is not the default architecture policy.

Issue lifetime is operation/dependency scoped: a success for dependency A cannot clear dependency B's active issue, refresh success cannot clear an observation issue, and changing content/readiness identity clears only issues that belong to the old identity.

Manual refresh failure follows DECISION-CSC-020: a new refresh attempt clears the previous refresh-attempt failure for the same identity when that new attempt starts; a successful attempt leaves no refresh failure; a failed attempt publishes only the new refresh failure. This clearing rule is local to `RefreshState` and does not touch content/observation/command failures.

When more than one required dependency fails before usable content exists, the feature may expose one deterministic `ContentState.Failed` summary while retaining the keyed blocking causes needed for truthful retry/recovery semantics under DECISION-CSC-021.

## 15. Required/enrichment audit rules

Every migrated ViewModel must document its input role in code/test naming or a nearby projector/readiness type.

An input must be classified using these questions:

1. Can the screen truthfully show primary membership without this input?
2. Can the screen truthfully declare empty without this input?
3. Can it honor the currently selected filter, sort, or semantic rank without this input?
4. If missing, can the UI say “unknown/pending” rather than “none/false/zero”?
5. Does this input only enrich label/artwork/action capability without making the promised ordering/filter false?
6. Does a local subsection/control depend on it even if the whole screen does not?
7. What readiness key/identity makes a retained value valid, and what key change invalidates it?
8. If this input fails, what explicit action or future observation can recover it?

If questions 1, 2, or 3 fail, the input is required for that projection. If only a subsection/control depends on it, use local readiness instead of global blocking where practical.

## 16. Module and API boundary

Expected feature-local package:

```text
app.openstory.catalog.ui.state
```

Expected concepts, not frozen class names:

```text
ContentState<T>
CatalogUiFailure
RefreshState
retained observation primitive/state
```

No production dependency is added from domain modules to `:feature:catalog`.

Dependency direction remains:

```text
:feature:catalog
    -> :catalog
    -> :library
    -> :chapters
    -> :downloads
    -> :reader public capability ports
    -> :core:designsystem

never the reverse
```

The shared observation primitive must not import Room, WorkManager, plugin runtime, Catalog engines, or Android storage implementations.

## 17. Migration boundaries

CSC-v1 is intentionally staged so semantic regressions are caught before broad cleanup.

### UX-R0 — Contract lock

Add pure contract tests for state transitions/readiness semantics without changing screen runtime behavior.

### UX-R1 — Foundation

Introduce feature-local `ContentState`, minimal failure/refresh types, keyed/restartable retained-observation semantics, and dependency-keyed observation issue lifetime.

### UX-R2 — Reactive screens

Migrate Downloads and Updates first. They directly reproduce the observed “blank then content appears” issue and have clear required/enrichment boundaries.

### UX-R3 — Local projections

Migrate Library, Home, and Chapters. Add local readiness where an enrichment controls only part of the screen.

### UX-R4 — Bootstrap/refresh owners

Migrate Discover and Story after the simpler reactive screens prove the contract. Strengthen Discover canonical readiness and Story canonical Preparing/bootstrap mapping.

### UX-R5 — Cleanup/freeze

Remove migrated `loading:Boolean` authorities, duplicated `preserveLatest()` helpers, synthetic fallback observation patterns, stale active refresh docs, and obsolete tests that encode pre-CSC semantics.

### UX-R6 — Follow-up audit only

Audit Search, Mapping, Reconciliation, and Reader for related semantics. Do not automatically migrate them. Decide separately whether the feature-local contract remains local or has earned promotion.

This sequence is a delivery boundary, not the implementation plan. A task-by-task plan is written only after this design is approved.

## 18. Verification strategy

CSC-v1 prioritizes transition tests over screenshots because the defect is semantic.

### 18.1 Shared contract tests

Required cases:

- Pending before first value;
- first empty value is Available/Ready empty;
- first failure is Unavailable/Failed;
- value then failure retains value and exposes issue;
- same-key explicit retry after first failure can reach Available;
- same-key retry after retained-value failure keeps the value visible until success;
- successful same-key value clears only that dependency's issue;
- key change discards old-key readiness/value when it is not valid for the new key;
- one dependency recovering does not clear another dependency's issue;
- refresh retry transition clears the previous refresh failure at attempt start, success leaves it clear, and failure publishes only the new attempt failure;
- multiple simultaneous required failures retain keyed causes while exposing a deterministic blocking summary and truthful retryability;
- no-content payload reasons cannot collapse NO_LIBRARY/setup/filtered-empty into one bare empty collection when UX behavior differs;
- cancellation propagates;
- no synthetic empty/zero/false initial emission;
- unsubscribe/re-subscribe preserves Ready for the same key/identity.

### 18.2 Downloads tests

Required cases:

- first Download snapshot renders before Chapter/Catalog metadata;
- fallback labels are stable;
- metadata later enriches without Pending;
- worker-driven record update is Ready -> Ready;
- first Download observation failure is blocking Failed;
- post-value observation failure retains content and is visible;
- command failure is visible and does not overwrite observation issue.

### 18.3 Updates tests

Required cases:

- empty Library short-circuits to Ready(empty);
- non-empty Library waits for Chapter + Mapping membership facts;
- Catalog and Reader capability do not block first usable projected updates;
- Catalog enrichment failure retains fallback title/content;
- required failure before first usable projection is blocking;
- post-value failure retains updates and is visible.

### 18.4 Library tests

Required cases:

- Library membership first empty -> Ready(true empty);
- membership renders with missing Catalog/progress enrichment;
- unresolved mapping observation is not represented as `NO_MAPPING`;
- mapping observation Pending is not mislabeled as active background `SEARCHING`;
- source-state filter does not produce false filtered-empty while mapping is unresolved;
- title query / TITLE sort waits locally for first Catalog snapshot rather than claiming a false filtered result/order;
- LAST_ACTIVITY sort waits locally for first Progress snapshot required by its ordering semantics;
- membership first failure -> Failed;
- enrichment failure preserves membership and surfaces non-blocking issue.

### 18.5 Home tests

Required cases:

- empty Library -> Ready(NO_LIBRARY) without waiting for six enrichments;
- non-empty Library produces base membership/summary before optional sections;
- all-DROPPED non-empty Library never renders the no-Library “add a story” empty CTA;
- unknown download count is not authoritative zero;
- progress/chapter/mapping enrichment arrives without full-screen Pending;
- enrichment failure preserves base Home;
- Library first failure is blocking.

### 18.6 Chapters tests

Required cases:

- chapter snapshot renders before Reader capability;
- first empty chapter snapshot is Ready(empty);
- first chapter observation failure is Failed;
- Reader capability failure does not remove chapters;
- manual refresh preserves Ready content;
- refresh/correction/observation failure channels remain distinct.

### 18.7 Discover tests

Required cases:

- empty cache bootstrap is Pending but not manual Refreshing;
- empty bootstrap success -> Ready(empty);
- bootstrap failure with no usable content -> Failed;
- cached usable content never returns to skeleton during manual refresh;
- homes non-empty + canonical resolution active cannot become false-empty;
- per-Story bootstrap returning `CanonicalStoryState.Preparing` does not leave an unobservable permanent Pending slot;
- terminal resolved-excluded Story IDs settle without waiting forever;
- partial canonical success renders only when ranked slot/order stability is preserved;
- unresolved higher-ranked Popular item cannot temporarily promote a lower-ranked story to the hero slot without reserving/waiting;
- zero usable providers/setup-required is not automatically classified as true-empty;
- terminal canonical failure with no semantic content -> Failed;
- terminal partial failure with usable content -> Ready + issue;
- manual refresh failure preserves cached Ready content.

### 18.8 Story tests

Required cases:

- `CanonicalStoryState.Preparing` while bootstrap is in flight -> Pending, not “Story unavailable”;
- bootstrap attempt returning Preparing without an explicit continuation exits Pending to a terminal no-content classification;
- canonical Ready -> Ready(story);
- bootstrap terminal failure without content -> Failed;
- content Retry restarts canonical observation/bootstrap, not source-detail refresh;
- observation/bootstrap/source-preference/refresh failures cannot overwrite each other;
- manual refresh preserves Ready story;
- personal/reconciliation enrichment cannot block Story body.

### 18.9 Compose/visual verification

Existing screenshot/semantics tests remain useful for:

- loading surface;
- true-empty surface;
- full-surface error;
- cached-content + inline error;
- pull-to-refresh with retained content.

Visual tests do not replace transition tests.

## 19. Current-code contradiction and gap audit

This section is normative evidence for why CSC-v1 exists and records issues the implementation plan must not forget.

### GAP-CSC-001 — `preserveLatest()` semantics differ by screen

- Library/Story helpers emit synthetic initial values immediately.
- Home/Downloads/Updates/Discover helpers hold an initial fallback but do not emit it until failure.
- Chapters bypasses those helpers and catches failure by emitting empty.

Result: identical “not observed yet” conditions produce different UX behavior.

**Resolution:** one retained-observation contract; no synthetic negative initial facts.

### GAP-CSC-002 — Downloads waits for enrichment it does not need

`DownloadsViewModel` combines downloads, chapters, and catalog before emitting content, but its projector already falls back to release/story IDs when metadata is missing.

**Resolution:** Download records are required; Chapter/Catalog are enrichment.

### GAP-CSC-003 — Updates blocks on Catalog and Reader capability

`LibraryActivityProjector` can use StoryId fallback when Catalog is absent, and Reader capability only decides `readerTarget`.

**Resolution:** Catalog/Reader capability are enrichment. Chapter + Mapping remain required for update membership when Library is non-empty.

### GAP-CSC-004 — Downloads/Updates observation failures are not rendered

Both ViewModels expose failure state, but their Screens branch only on loading/empty/content and never render the screen-level failure.

**Resolution:** blocking failure without usable content; visible non-blocking issue with retained content.

### GAP-CSC-005 — Library maps pending enrichment to negative facts

Library enrichment emits initial `emptyList()` values. Before mapping observation is authoritative, `mapped=false` becomes `LibrarySourceState.NO_MAPPING`.

**Resolution:** unresolved mapping observation is explicit local unknown/Pending; it must not become `NO_MAPPING`, and it must not be labeled active `SEARCHING` without a real domain mapping-work lifecycle signal.

### GAP-CSC-006 — Library membership observation has no failure mapping

`LibraryViewModel` catches enrichment failure but not `library.observe()` failure.

**Resolution:** membership is a required retained observation; first failure -> Failed, later failure -> retained Ready + issue.

### GAP-CSC-007 — Home globally blocks on section enrichment

Home combines all Library/Catalog/Progress/Chapter/Mapping/Download/Reader inputs before first content and can inject synthetic empty/zero fallback after early observation failure.

**Resolution:** Library controls screen readiness; other inputs enrich sections/fields progressively and must represent unknown values honestly.

### GAP-CSC-008 — Discover bootstrap conflates Loading and Refreshing

`bootstrapEmptyCache()` calls `performRefresh()`, and `performRefresh()` sets `refreshing=true`. Current tests explicitly assert initial `loading=true` and `refreshing=true`.

**Resolution:** automatic bootstrap stays Pending but is not presented as user refresh.

### GAP-CSC-009 — Discover can project false empty while canonical data is unresolved

Semantic projection drops missing canonical projections with `mapNotNull`, while `sourceEmpty` only checks whether homes are empty.

**Resolution:** explicit Discover canonical readiness/terminal signal; unresolved projections cannot become authoritative empty.

### GAP-CSC-010 — Discover canonical prewarm hides terminal failure detail

`DiscoverCanonicalBootstrapPipeline.prewarm()` catches each exception and returns no success/failure report.

**Resolution:** expose or derive enough domain-specific terminal readiness to distinguish Pending, partial usable content, and no-content failure.

### GAP-CSC-011 — Story has no explicit canonical Pending state

`CanonicalStoryState.Preparing` becomes `story=null`. `StoryScreen` only shows loading when `refreshing && failure == null`; bootstrap does not set `refreshing`.

**Resolution:** canonical Preparing is `ContentState.Pending` only while an explicit bootstrap/readiness attempt or other observable continuation can settle it. If that attempt completes and the canonical state is still Preparing with no owned continuation, the screen must leave Pending and classify the terminal no-content outcome; this remains independent of manual refresh.

### GAP-CSC-012 — Chapters conflates first observation failure with empty

`repository.observe(storyId).catch { emit(emptyList()) }` makes failure indistinguishable from authoritative no chapters; the Screen then shows inline failure plus empty state.

**Resolution:** first required failure -> blocking Failed; post-value failure retains chapters.

### GAP-CSC-013 — Chapters blocks on Reader capability

Chapter groups and a one-shot Reader availability lookup are combined before `loading=false`.

**Resolution:** chapter groups are required; capability is enrichment.

### GAP-CSC-014 — Chapter failures share one state channel

Observation failure, sync refresh failure, and manual grouping/separation correction failure all write the same `failure` state.

**Resolution:** separate observation, refresh, and command failure channels.

### GAP-CSC-015 — Active refresh documentation is stale for Chapters

`docs/ui/design-system.md` and older pull-to-refresh design/cleanup documents still say Story Chapters are intentionally non-refreshable until chapter sync exists. Current code and Wave 10 now provide that pipeline.

**Resolution:** update active documentation during CSC cleanup; keep historical docs unchanged or annotate only where repository policy requires.

### GAP-CSC-016 — Existing design-system “no global UiState” rule must remain intact

`docs/ui/design-system.md` explicitly forbids the design system from defining global `UiState`, `UiError`, retry policy, or feature state machines.

**Resolution:** CSC-v1 remains feature-local in `:feature:catalog`; `:core:designsystem` continues to own only visual vocabulary.

### GAP-CSC-017 — Current catch-and-retain streams do not have a real recovery path

The current `preserveLatest()`/`catch` patterns emit a retained fallback after an exception and then the collected Flow completes. They do not clear the failure on recovery because there is no guaranteed later emission in the same collection.

**Resolution:** retained observations are explicitly restartable for retryable failures; no implementation may assume `catch` resumes upstream. Same-key recovery clears only that observation's issue.

### GAP-CSC-018 — Dynamic observation keys need retention invalidation

Updates/Home use `flatMapLatest` from changing Library Story-ID sets into Catalog/Chapter/Mapping observations. A shared retained primitive that retains “latest” without key identity could reuse facts from the old Story set for the new Story set and produce false Ready/empty states.

**Resolution:** every retained observation has a readiness key; old-key data cannot satisfy new-key readiness.

### GAP-CSC-019 — `CanonicalBootstrapUseCase.ensureReady()` can complete while still Preparing

`ensureReady()` rebuilds and then returns the repository's current `CanonicalStoryState`; `CanonicalFusionService` can return/leave `Preparing` when no source records exist. Therefore a completed bootstrap call is not proof of Ready and may not throw.

**Resolution:** Story and Discover classify the returned state/outcome explicitly. Preparing after a completed attempt cannot remain Pending forever without a separate observable continuation.

### GAP-CSC-020 — Library has no authoritative background mapping-search status

`ContentMappingRepository` exposes durable mappings, while mapping automation is scheduled through WorkManager. The feature currently has no port that says a mapping worker is actively searching or has terminally exhausted sources.

**Resolution:** CSC-v1 distinguishes mapping-observation Pending from mapping-search activity and must not misuse `LibrarySourceState.SEARCHING`. First mapping snapshot makes durable mapping presence/absence authoritative for the current projection only; a true worker lifecycle UI requires a separate domain capability decision.

### GAP-CSC-021 — Library dependency roles change with active controls

`projectLibrary()` uses Catalog title for text query and TITLE sort, and Reading Progress contributes to `updatedAt` used by LAST_ACTIVITY sort. Treating Catalog/Progress as unconditional enrichment can produce false filtered-empty or knowingly provisional ordering while those first snapshots are unresolved.

**Resolution:** required/enrichment is control-sensitive; Catalog/Progress become local-required for the controls whose semantics depend on them.

### GAP-CSC-022 — Home shelf emptiness can contradict Library truth

Home shelves intentionally omit `LibraryStatus.DROPPED`, while `HomeDashboardUiState.isEmpty` checks only shelves/updates. An all-DROPPED but non-empty Library can therefore show the current “Find a story and add it to your Library” empty CTA.

**Resolution:** Home payload carries an explicit empty reason/equivalent membership truth; NO_LIBRARY copy is only used for a truly empty Library.

### GAP-CSC-023 — One observation-failure field cannot model multiple independent inputs

Home, Downloads, Updates, and Discover observe multiple fallible dependencies but expose one merged/overwritable observation failure. A later failure can replace an earlier one, and a future recovery of one source would have no safe way to clear only its own issue.

**Resolution:** observation issue lifetime is keyed per dependency/readiness identity; rendering may prioritize one, storage/lifetime may not collapse them.

### GAP-CSC-024 — Story and Discover still collapse operations that require different retry actions

Story's single `failure` field is written by canonical observation, bootstrap, source-preference rebuild, and source-detail refresh. Discover's `globalFailure` prioritizes refresh over observation failure. A generic Retry callback can therefore invoke the wrong operation or hide another active issue.

**Resolution:** blocking content retry, observation issue, refresh failure, command failure, and partial report remain separate; Retry targets the failed content-readiness boundary.

### GAP-CSC-025 — Pending Reader capability is currently encoded as a negative capability

Chapters falls back to empty `ReaderAvailability`, making `readerCapable/downloadCapable=false`; Home falls back to an empty Reader plugin set. Before the capability lookup is authoritative, those values are indistinguishable from a real “unsupported” result.

**Resolution:** capability presentation uses an explicit unknown/local-pending representation (nullable/tri-state/equivalent) until the first capability snapshot/lookup completes.

### GAP-CSC-026 — Discover partial projection can reorder ranked content

The current `mapNotNull` projection preserves order only among resolved items. If a higher-ranked Story is unresolved while a lower-ranked Story resolves, the lower-ranked Story can temporarily occupy the Popular hero/earlier slot and move later when the missing projection arrives.

**Resolution:** partial Ready obeys semantic slot stability: reserve, wait, or prove ordering cannot change.

### GAP-CSC-027 — Refresh-failure lifetime is inconsistent across refresh owners

Current refresh owners do not share one attempt-lifetime rule: Chapter clears its shared failure when a new refresh starts; Discover retains the previous `refreshFailure` throughout a new attempt and clears it only after success; Story writes refresh results into the same field used by unrelated operations. This can leave stale error feedback visible during a retry or let an unrelated operation clear/replace it.

**Resolution:** `RefreshState` has explicit attempt lifetime: new refresh attempt -> clear prior refresh failure for that identity, success -> no refresh failure, failure -> only the new attempt failure. Observation/command/content failures remain independent.

## 20. Risks and controls

### Risk: abstraction becomes a disguised engine

**Control:** value types and pure observation semantics only; no I/O, DI singleton, WorkManager, repository ownership, TTL, or retry orchestration.

### Risk: progressive enrichment produces visual churn

**Control:** stable item keys, deterministic fallback identity, local placeholders only where necessary, and no blocking screen reset. Performance/screenshot tests validate unacceptable layout churn.

### Risk: making dependencies “optional” creates false facts

**Control:** DECISION-CSC-010. Enrichment is allowed only when missing data is explicitly unknown/fallback, never authoritative zero/false/none.

### Risk: Discover waits forever for canonical convergence

**Control:** readiness must have a domain-specific terminal outcome/report. Pending cannot depend on an unobservable hope that another projection will eventually appear.

### Risk: failure visibility becomes noisy

**Control:** distinguish blocking content failure, dependency-keyed retained observation issues, refresh failure, command failure, and partial provider report. UI may render one prioritized issue, but issue lifetime remains independent internally. Do not stack duplicate messages for the same root operation.

### Risk: retained observation becomes a hidden retry engine

**Control:** explicit restart only; no automatic tight retry/backoff. A Flow exception is treated as collection termination unless the source contract says otherwise. Domain retry policy is not moved into CSC.

### Risk: retained data leaks across dynamic projection identities

**Control:** readiness keys/epochs are mandatory for dynamic observations. Old-key values cannot satisfy new-key readiness.

### Risk: progressive enrichment causes semantic reorder, not just visual churn

**Control:** data that determines an active filter/sort/rank is local-required until its current-key first snapshot arrives; ranked partial projections reserve/wait for unresolved earlier slots rather than compact them away.

### Risk: Retry invokes the wrong operation

**Control:** blocking `ContentState.Failed` has a content-retry boundary distinct from pull refresh and command retry. Story canonical retry is the reference case.

### Risk: Reader gets forced into the abstraction prematurely

**Control:** explicit v1 exclusion and UX-R6 audit gate.

### Risk: broad state model churn destabilizes all screens at once

**Control:** migrate reactive/simple screens first, preserve domain APIs, and verify transition contracts per phase before moving to bootstrap-heavy screens.

## 21. Rejected alternatives

### 21.1 Keep `loading:Boolean` and only share helper functions

Rejected because it cannot represent Pending vs Ready(empty), required vs enrichment, blocking vs retained failure, or bootstrap vs refresh.

### 21.2 Global loading/cache/refresh engine

Rejected because it would need knowledge of Catalog TTL, canonical readiness, chapter sync, download durability, Reader cache/session semantics, Room, and WorkManager. That is a god-object boundary and duplicates existing domain lifetime owners.

### 21.3 Put CSC-v1 in `:core:designsystem`

Rejected because the current design-system architecture explicitly owns presentation vocabulary, not feature state machines or domain readiness.

### 21.4 Put CSC-v1 in `:core:common` immediately

Rejected because reuse outside Catalog is not yet proven and would turn a generic core module into a presentation dumping ground.

### 21.5 Introduce universal Fresh/Stale/Activity state now

Rejected for v1 as unnecessary generalization. Domain owners already have materially different freshness semantics, and the audited defects can be solved without flattening them.

## 22. Acceptance criteria

CSC-v1 implementation is architecturally complete only when all of the following are true:

- no migrated screen uses `loading:Boolean` as an independent content authority;
- Pending and Ready(empty) are independently testable;
- first required observation failure cannot become synthetic empty;
- every migrated dependency has an explicit required/enrichment role;
- optional enrichment cannot fabricate empty/zero/false negative facts;
- Downloads can render from Download records before Chapter/Catalog enrichment;
- Updates can short-circuit an empty Library and does not block on Catalog/Reader capability;
- Library does not call unresolved mapping observation `NO_MAPPING` and does not mislabel observation Pending as active mapping `SEARCHING`;
- Library query/TITLE sort/LAST_ACTIVITY sort honor their control-sensitive local-required observations;
- Home does not globally block on optional shelves/counts/capabilities;
- a non-empty all-DROPPED Library cannot render the no-Library Home CTA;
- Chapters render before Reader capability, represent capability Pending separately from authoritative unsupported, preserve the distinction between no chapters / no releases / no readable source, and first observation failure is blocking;
- Discover initial bootstrap is not presented as manual refreshing;
- Discover cannot produce false-empty solely because canonical projections are unresolved;
- Discover partial canonical content cannot compact unresolved higher-ranked slots into unstable hero/rank positions;
- Discover distinguishes true empty from provider/setup unavailability;
- Story Preparing maps to Pending only while bootstrap/readiness work has an observable exit; a completed bootstrap that remains Preparing cannot hang forever;
- manual refresh never clears usable content;
- background work never creates full-screen loading merely because it is background work;
- post-value observation failure retains usable content and has an explicit same-key recovery path where retryable;
- observation issues are dependency-keyed so one recovery cannot clear another active issue;
- blocking Retry restarts the failed content-readiness boundary rather than blindly invoking manual refresh;
- observation/refresh/command failure channels are not collapsed;
- starting a new manual refresh does not keep the previous refresh failure visible, and refresh success clears only refresh failure;
- a blocking failure backed by multiple required causes preserves those causes internally and advertises Retry only when `retryContent()` can actually make progress;
- migrated empty/no-content screens preserve a semantic reason whenever copy/actions differ, instead of deciding solely from `List.isEmpty()`;
- resubscription does not blank retained Ready content;
- dynamic readiness-key changes cannot reuse old-key values to satisfy new-key Ready;
- no Pending branch depends indefinitely on an unobservable future background event;
- duplicated feature-local `preserveLatest()` implementations are removed from migrated screens;
- active refresh documentation agrees that Chapter pull-to-refresh now exists;
- `:core:designsystem` remains free of feature `UiState`/domain readiness ownership;
- Reader HES-v1 remains unchanged by UX-R0 through UX-R5;
- architecture/module verification and focused feature tests remain green.

## 23. Final architecture statement

CSC-v1 does not unify Hikari by making every screen run through one powerful engine. It unifies Hikari by giving presentation code one precise language for **availability**, one rule for **usable cache visibility**, one rule for **manual refresh retention**, and one disciplined way to distinguish **required facts from enrichment**.

The resulting ownership model is:

```text
Domain decides truth, freshness, persistence, sync, and lifetime.
Feature decides readiness and projection completeness.
CSC-v1 expresses Pending / Ready / Failed and refresh semantics.
Design system renders those semantics consistently.
```

That boundary solves the observed UX drift without weakening the domain architectures Hikari already has.
## 24. Design self-review result

CSC-v1 received a second deep red-team review against the current repository after the first complete draft. That review found several real contract holes; they were corrected in this revision rather than being deferred to implementation.

### Placeholder scan

No unresolved marker, placeholder, or intentionally vague acceptance requirement remains. Exact class/file names are illustrative only where architecture does not require a frozen API name.

### Deep-review corrections

The final plan review added two clarifications without changing CSC ownership/scope:

- Story canonical readiness is route-keyed while Library/Progress enrichment is resolved-canonical-ID-keyed; these identities must not be normalized against each other.
- Story hero read presentation must distinguish Chapter Pending/Failed/empty, non-empty chapter groups with no releases, and unresolved Reader capability from fully resolved non-empty Chapters with release targets but no readable source; only the latter may present `Find source`.


The second review added or tightened the following architecture constraints:

- retained observations are keyed by the identity for which their value is valid; old-key values cannot satisfy new-key readiness;
- a caught Flow exception is treated as collection termination unless explicitly restarted; retryable observations have a feature-owned restart path rather than assuming `catch` later resumes;
- observation issues are dependency-keyed so independent failures cannot overwrite/clear each other incorrectly;
- blocking content Retry targets the failed readiness boundary and is distinct from manual refresh;
- every Pending branch has an observable exit; a completed bootstrap returning a still-unusable state cannot hang forever;
- `CanonicalBootstrapUseCase.ensureReady()` is explicitly treated as capable of returning `Preparing`;
- Discover canonical settlement is per expected Story slot and must distinguish projected, excluded, failed, and unresolved outcomes;
- partial ranked Discover content cannot use `mapNotNull` compaction to create unstable hero/rank positions;
- Library mapping-observation Pending is not equated with active WorkManager mapping search; CSC does not invent a background mapping lifecycle signal that the domain does not expose;
- Library Catalog/Progress/Mapping roles are control-sensitive for title query, TITLE sort, LAST_ACTIVITY sort, and source-state filters;
- Home true-empty semantics distinguish an actually empty Library from a non-empty Library with no currently rendered Home shelf (for example all-DROPPED);
- pending Reader capability cannot be encoded as authoritative unsupported/false;
- manual refresh failure has attempt-scoped lifetime so stale failure is not rendered during a new retry;
- a blocking `ContentState.Failed` may summarize multiple keyed required causes without losing truthful retry semantics;
- authoritative no-content payloads retain semantic reasons when copy/actions differ rather than collapsing to one `isEmpty` bit;
- screen-level observation issue collections are derived from keyed observation state rather than becoming a second mutable failure authority.

### Internal-consistency review

The revised design has no known contradiction between its ownership rules:

- CSC-v1 remains feature-local and does not violate the design-system prohibition on global `UiState`;
- manual refresh remains direct-to-capability where current architecture requires it, including Chapter sync;
- blocking content Retry is separate from manual refresh, preventing Story/Chapter retry from invoking the wrong operation;
- WorkManager/background execution remains outside the presentation state machine;
- cache freshness/TTL remains domain-owned while cache **visibility** is standardized;
- `Pending`, authoritative `Ready(empty)`, and blocking `Failed` have non-overlapping meanings;
- `Pending` cannot be permanent merely because unspecified background work may eventually run;
- non-blocking failures cannot replace usable `Ready` content;
- subsection/control-local readiness is allowed without turning every optional dependency into a screen-level required dependency;
- retained observation does not become a retry engine because restart is explicit and retry/backoff policy remains feature/domain-owned.

### Current-code contradiction review

The source claims used by the design were revalidated against the current tree, including:

- Chapter manual refresh calls `ChapterSyncService.sync(storyId)` and `ChapterList` uses `HikariPullToRefresh`;
- Library and Story `preserveLatest()` helpers emit synthetic initial values;
- current catch-and-retain helpers complete after exception and expose no dependable same-collection recovery;
- Updates/Home dynamic observations are keyed from changing Library Story-ID sets;
- Downloads already contains metadata fallback paths proving Chapter/Catalog metadata is enrichment;
- Library title query/TITLE sort depends on Catalog title and LAST_ACTIVITY sort depends on Reading Progress;
- the Library domain exposes durable Content Mapping observations but no background mapping-worker lifecycle status;
- Home omits DROPPED shelves while `isEmpty` ignores `summary.libraryCount`;
- Discover semantic projection drops unresolved canonical entries with `mapNotNull`;
- Discover canonical prewarm swallows per-story exceptions without returning readiness detail;
- `CanonicalBootstrapUseCase.ensureReady()` can return `CanonicalStoryState.Preparing`;
- Story only projects `CanonicalStoryState.Ready`, while one shared failure field is also used by observation/bootstrap/preference/refresh operations;
- Downloads and Updates Screens do not currently render their ViewModel-level `state.failure`;
- Chapter/Home Reader capability fallback currently converts unresolved lookup into an empty capability set; current Chapter/Story fixtures also allow canonical chapter groups with zero releases, which must not be collapsed into “no Reader source”;
- active `docs/ui/design-system.md` still contains the stale “Chapters not refreshable until chapter sync exists” statement.

These current-tree mismatches and implementation hazards are captured as `GAP-CSC-001` through `GAP-CSC-027`.

### Scope review

The design remains an implementation-program-sized presentation refactor. UX-R0 through UX-R5 stay centered on `:feature:catalog`; Reader HES-v1, Search, Mapping UI, Reconciliation UI, domain cache engines, and WorkManager policy remain excluded.

CSC-v1 is allowed to consume an existing domain readiness signal, but it must not invent a WorkManager/domain lifecycle API merely to satisfy presentation. The Library mapping rule was intentionally narrowed to current durable mapping-observation readiness so the design does not silently expand into a mapping-work-state subsystem.

### Ambiguity review

The major ambiguities are resolved as follows:

- Empty is `Ready(empty)`, never its own peer to Pending;
- “authoritative” means authoritative for the current durable/domain snapshot, not proof that no future background work can add data;
- bootstrap does not set manual Refreshing merely because it reuses a refresh service;
- no universal Fresh/Stale or Activity model is introduced in v1;
- retained values/issues are readiness-key scoped;
- a Flow failure does not magically resume after `catch`; explicit retry/restart owns recovery;
- Library unresolved mapping observation is local unknown/Pending, never automatically `NO_MAPPING` or active `SEARCHING`;
- Home readiness is controlled by Library membership, with an explicit empty reason preventing no-Library copy from lying about non-empty Libraries;
- Updates requires Chapters + Mappings only for its current non-empty Library projection, while dynamic key changes invalidate old-key readiness;
- Discover canonical readiness has explicit per-slot terminal outcomes and ranked partial-content stability;
- Story canonical Preparing is Pending only while bootstrap has an observable exit; completed-but-still-Preparing is terminally classified instead of hanging;
- Chapter observation, refresh, correction, and capability readiness are distinct semantics;
- RefreshState failure is attempt-scoped: retry start clears the prior refresh failure, while other failure channels remain untouched;
- one rendered blocking failure may summarize multiple keyed required causes, but retryability remains tied to a real recovery action;
- authoritative no-content is a Ready payload classification, and feature-specific empty/setup/filter reasons remain available for correct UX copy/actions.

No known unresolved architectural contradiction remains after this deep review. Exact Kotlin names, local placeholder visuals, which keyed issue is visually prioritized when several exist, and future promotion beyond `:feature:catalog` remain implementation/presentation choices constrained by the contract rather than gaps in it.
