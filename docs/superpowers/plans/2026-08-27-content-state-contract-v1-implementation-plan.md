# Hikari Content State Contract v1 (CSC-v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement CSC-v1 across the audited `:feature:catalog` destinations so Pending, authoritative empty, retained usable content, manual refresh, observation failure, and required-vs-enrichment readiness have one testable semantic contract without creating a global loading/cache engine.

**Architecture:** Add a small feature-local CSC foundation (`ContentState`, `CatalogUiFailure`, `RefreshState`, keyed restartable retained observations), then migrate screens in risk order. Domain services keep ownership of cache freshness, sync, WorkManager, canonical convergence, downloads, and Reader; each ViewModel remains the readiness owner for its own projection and exposes only truthful Retry/refresh actions.

**Tech Stack:** Kotlin/JVM + Android/Kotlin, kotlinx.coroutines `Flow`/`StateFlow`, Jetpack Compose Material 3, Hilt ViewModels, Robolectric/Compose UI tests, Gradle 9.5.x, existing architecture/package-boundary verification scripts.

**Spec:** `docs/superpowers/specs/2026-08-27-content-state-contract-v1-design.md`

## Global Constraints

- CSC-v1 is a contract, not an engine: add no `ContentStateEngine`, `LoadingEngine`, `CacheEngine`, `RefreshEngine`, process-wide singleton, WorkManager coordinator, or repository coordinator.
- Initial implementation remains in `:feature:catalog`; do not move CSC types to `:core:common`, `:core:designsystem`, or a new module during UX-R0 through UX-R5.
- `:core:designsystem` continues to own **how** generic loading/empty/error/feedback surfaces render, not **when** feature state is Pending/Ready/Failed.
- `ContentState.Pending` is never equivalent to `ContentState.Ready(empty)`.
- Manual refresh is orthogonal to content availability; usable `Ready` content stays visible while refresh runs or fails.
- Automatic bootstrap is not manual refresh; background WorkManager/domain revalidation is not manual refresh.
- Domain owners keep all TTL/freshness/cache/sync/retry/routing ownership; CSC adds no global Fresh/Stale or activity state machine.
- Required/enrichment classification is projection-specific and may become control-sensitive for Library filters/sorts.
- Pending/failed optional inputs must not be encoded as authoritative empty/zero/false facts.
- Retained observation values and issues are scoped by readiness identity/key; old-key values must never satisfy new-key readiness.
- Flow exceptions terminate that collection unless explicitly restarted; retryable observation failure must have an explicit same-key restart path and no automatic tight retry/backoff loop.
- Cancellation exceptions are always rethrown; non-cancellation `Exception` values may be mapped to observation failures, while JVM `Error`/fatal `Throwable` values are never swallowed by CSC.
- A screen already holding usable `Ready` content must not visibly reset to Pending on same-identity `WhileSubscribed` resubscription.
- Every Pending branch must have a named observable exit; no branch may wait forever for unspecified future background work.
- Blocking Retry must restart the failed content-readiness boundary, not blindly call manual refresh.
- Observation, refresh, and command failures have separate lifetimes; one success cannot clear an unrelated issue.
- A failure already consumed as the current blocking `ContentState.Failed` or local-required `Unavailable` state must not also render as a duplicate non-blocking `observationIssue`. Optional-enrichment `Unavailable` and retained `Available(issue)` may surface non-blocking feedback, but issue selection/retry only reads states normalized to the **current readiness key**.
- Reader HES-v1 and `:feature:reader` remain unchanged through UX-R0–UX-R5.
- Search, Mapping, and Reconciliation presentation readiness are an approved 2026-08-29 scope extension inside `:feature:catalog`; Task 10 freezes their existing CSC adoption, while their domain command lifetimes remain screen-local and Reader remains audit-only.
- No Room schema, repository-domain API, WorkManager scheduling, or module-graph change is required for CSC-v1.
- Existing Chapter pull-to-refresh remains accepted runtime behavior and active docs must be corrected to match it.
- **Reuse budget:** share only domain-neutral semantic primitives (`ContentState`, `RefreshState`, `CatalogUiFailure`, keyed retained observation and tiny key/issue helpers). Screen/domain readiness models such as `LibraryCollectionState`, `ChapterCapabilityState`, Discover settlement, Story bootstrap, and no-content reason enums stay local.
- A new shared helper is allowed only when at least two migrated screens need the same state shape and the helper imports no Story/Chapter/Library/Plugin/domain type; otherwise keep it private to the owning screen.
- Do not introduce a lambda-heavy generic screen reducer/Compose host that hides readiness decisions. Reuse presentation components from `:core:designsystem`, but keep dependency classification visible in each ViewModel reducer.
- Progressive UX must prefer retained/partial truthful content over blocking chrome: use local skeletons/placeholders only inside unresolved regions, never promote unresolved data to an authoritative negative state, and avoid layout/rank jumps when enrichment arrives.

---

## File structure and ownership map

### New feature-local CSC files

- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/ContentState.kt`
  - Public presentation availability algebra used by public feature `UiState` classes.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/CatalogUiFailure.kt`
  - Minimal public failure value (`code`, `retryable`), no Throwable/domain object/retry function.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/RefreshState.kt`
  - Public manual-refresh state plus internal attempt transition helpers.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/RetainedObservation.kt`
  - Internal keyed observation state/holder with same-key retention, explicit retry trigger, cancellation propagation, and no synthetic initial facts.
- `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/state/ContentStateContractTest.kt`
  - Pure contract tests for Pending/Ready/Failed and refresh-attempt semantics.
- `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/state/RetainedObservationTest.kt`
  - Coroutine tests for first value/failure, post-value failure, retry, key changes, independent issues, and resubscription retention.
- `build-logic/src/test/kotlin/app/openstory/build/ContentStateContractArchitectureTest.kt`
  - Repository-level freeze test for legacy loading helpers, CSC module placement, and Reader non-adoption; belongs in build-logic because it scans multiple modules.

### Existing screens migrated in order

1. Downloads — one required local source, two metadata enrichments.
2. Updates — dynamic Library Story-ID key; Chapters + Mappings required, Catalog + Reader capability enrichment.
3. Library — membership required; Catalog/Progress/Mapping become local-required under controls.
4. Home — Library required; all other inputs progressive section/field enrichment.
5. Chapters — Chapter groups required; Reader capability enrichment; separate refresh/correction/observation failure channels.
6. Discover — source-home bootstrap + ordered canonical settlement; highest readiness risk.
7. Story — canonical observation/bootstrap required; personal/reconciliation enrichment; source-detail refresh orthogonal.

### Navigation integration

`app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt` will only gain callback wiring and Story `content` inspection. It must not gain readiness logic.

---

## Task 1: UX-R0/R1 — Lock the CSC foundation with pure tests

**Why R0 and R1 share one reviewable task:** UX-R0 requires executable contract tests, while those tests need the feature-local value types to compile. Creating the types without wiring any screen leaves runtime behavior unchanged, so this task satisfies “contract lock before runtime migration” without inventing test-only duplicate state models.

**Files:**
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/ContentState.kt`
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/CatalogUiFailure.kt`
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/RefreshState.kt`
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/RetainedObservation.kt`
- Create `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/state/ContentStateContractTest.kt`
- Create `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/state/RetainedObservationTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface ContentState<out T>` with `Pending`, `Ready<T>(value)`, `Failed(failure)`.
  - `data class CatalogUiFailure(val code: String, val retryable: Boolean)`.
  - `data class RefreshState(val inProgress: Boolean = false, val failure: CatalogUiFailure? = null)`.
  - `internal fun RefreshState.startAttempt(): RefreshState`.
  - `internal fun RefreshState.completeSuccess(): RefreshState`.
  - `internal fun RefreshState.completeFailure(failure: CatalogUiFailure): RefreshState`.
  - `internal sealed interface ObservationState<out K, out T>` inside `RetainedObservation.kt`.
  - `internal class RetainedObservation<K, T>` exposing `val state: StateFlow<ObservationState<K,T>>` and `fun retry()`.
  - `internal fun ObservationState<*, *>.hasRetainedIssue(): Boolean`.
  - `internal fun ObservationState<*, *>.hasIssueOrUnavailable(): Boolean`.
  - `internal fun <K,T> ObservationState<K,T>.forExpectedKey(expectedKey: K): ObservationState<K,T>`.
  - `internal fun <K,T> CoroutineScope.retainedObservation(...)` factory.
- Consumes only kotlinx.coroutines and CSC failure types; imports no domain module, Room, WorkManager, plugin runtime, or Android storage implementation.

- [ ] **Step 1: Write the pure content/refresh contract tests first**

Create `ContentStateContractTest.kt` with explicit type distinction and refresh-attempt tests:

```kotlin
package app.openstory.catalog.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentStateContractTest {
    @Test
    fun pendingAndAuthoritativeEmptyAreDifferentStates() {
        val pending: ContentState<List<String>> = ContentState.Pending
        val empty: ContentState<List<String>> = ContentState.Ready(emptyList())

        assertIs<ContentState.Pending>(pending)
        assertEquals(emptyList(), assertIs<ContentState.Ready<List<String>>>(empty).value)
    }

    @Test
    fun refreshAttemptClearsOnlyPreviousRefreshFailure() {
        val oldFailure = CatalogUiFailure("refresh.old", retryable = true)
        val started = RefreshState(failure = oldFailure).startAttempt()

        assertTrue(started.inProgress)
        assertNull(started.failure)
    }

    @Test
    fun refreshSuccessLeavesRefreshFailureClear() {
        val completed = RefreshState(inProgress = true).completeSuccess()

        assertFalse(completed.inProgress)
        assertNull(completed.failure)
    }

    @Test
    fun refreshFailurePublishesOnlyTheCurrentAttemptFailure() {
        val failure = CatalogUiFailure("refresh.new", retryable = true)
        val completed = RefreshState(inProgress = true).completeFailure(failure)

        assertFalse(completed.inProgress)
        assertEquals(failure, completed.failure)
    }
}
```

- [ ] **Step 2: Run the content contract test and confirm RED**

Run:

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ContentStateContractTest*' \
  --no-daemon
```

Expected before implementation: compilation failure for missing `ContentState`, `CatalogUiFailure`, and `RefreshState`.

- [ ] **Step 3: Implement the minimal public CSC value types**

`ContentState.kt`:

```kotlin
package app.openstory.catalog.ui.state

sealed interface ContentState<out T> {
    data object Pending : ContentState<Nothing>
    data class Ready<T>(val value: T) : ContentState<T>
    data class Failed(val failure: CatalogUiFailure) : ContentState<Nothing>
}
```

`CatalogUiFailure.kt`:

```kotlin
package app.openstory.catalog.ui.state

data class CatalogUiFailure(
    val code: String,
    val retryable: Boolean,
)
```

`RefreshState.kt`:

```kotlin
package app.openstory.catalog.ui.state

data class RefreshState(
    val inProgress: Boolean = false,
    val failure: CatalogUiFailure? = null,
)

internal fun RefreshState.startAttempt(): RefreshState =
    copy(inProgress = true, failure = null)

internal fun RefreshState.completeSuccess(): RefreshState = RefreshState()

internal fun RefreshState.completeFailure(failure: CatalogUiFailure): RefreshState =
    RefreshState(inProgress = false, failure = failure)
```

Do not add generic freshness/activity enums or UI rendering helpers in this task.

- [ ] **Step 4: Run the content contract test and confirm GREEN**

Run the same focused command. Expected: PASS.

- [ ] **Step 5: Write retained-observation tests covering all normative lifetime rules**

Create `RetainedObservationTest.kt` with `StandardTestDispatcher`, `MutableStateFlow` keys, and explicit restartable source factories. Cover these exact cases:

```kotlin
@Test fun firstRealEmptyValueIsAvailableNotPending()
@Test fun failureBeforeFirstValueIsUnavailable()
@Test fun valueThenFailureRetainsValueAndIssue()
@Test fun retryAfterFirstFailureReturnsToPendingThenAvailable()
@Test fun retryAfterRetainedFailureKeepsValueVisibleUntilSuccess()
@Test fun successfulSameKeyValueClearsOnlyThatObservationIssue()
@Test fun normalCompletionBeforeFirstValueBecomesUnavailable()
@Test fun normalCompletionAfterValueKeepsAvailableWithoutIssue()
@Test fun retryThatCompletesWithoutValueRetainsSameKeyValueAndAddsIssue()
@Test fun keyChangeDropsOldValueAndStartsPendingForNewKey()
@Test fun staleAvailableStateNormalizesToPendingForNewExpectedKey()
@Test fun cancellationPropagates()
@Test fun upstreamRestartForSameKeyDoesNotEmitVisiblePendingAfterAvailable()
```

Use a test source factory controlled by `MutableStateFlow<Int>` attempt counters rather than delay/timeouts. For the key-change test, emit `"A-value"`, change key to `"B"`, and assert no `Available(key="B", value="A-value")` is ever observed.

Also test the reducer-facing race guard directly:

```kotlin
val stale: ObservationState<String, String> =
    ObservationState.Available(key = "A", value = "A-value")

assertIs<ObservationState.Pending<String>>(stale.forExpectedKey("B"))
```

Every reducer with a dynamic readiness key must call `forExpectedKey(currentExpectedKey)` before reading Pending/Available/Unavailable. This closes the scheduling window where the owner fact changes before the dependent retained holder has emitted its new-key Pending state.

Because the production holder uses `SharingStarted.WhileSubscribed`, every coroutine test must explicitly start collection before driving sources, for example:

```kotlin
val collected = backgroundScope.launch { holder.state.collect() }
runCurrent()
// emit/throw/change key, then runCurrent() and assert holder.state.value or recorded states
collected.cancel()
```

Do not write a test that only reads `holder.state.value` and assumes the upstream repository Flow has started. Because the holder and test collectors intentionally run in `backgroundScope`, drive zero-delay scheduled background work with `runCurrent()` (or `testScheduler.runCurrent()`), not `advanceUntilIdle()`: kotlinx-coroutines-test deliberately lets `advanceUntilIdle()` stop once only background-scope work remains.

- [ ] **Step 6: Run retained-observation tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*RetainedObservationTest*' \
  --no-daemon
```

Expected: compilation failure because `RetainedObservation` does not exist.

- [ ] **Step 7: Implement the keyed retained-observation primitive**

Use this exact public/internal shape; keep the mutable retained authority private to one holder:

```kotlin
package app.openstory.catalog.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal sealed interface ObservationState<out K, out T> {
    val key: K

    data class Pending<K>(override val key: K) : ObservationState<K, Nothing>

    data class Available<K, T>(
        override val key: K,
        val value: T,
        val issue: CatalogUiFailure? = null,
    ) : ObservationState<K, T>

    data class Unavailable<K>(
        override val key: K,
        val failure: CatalogUiFailure,
    ) : ObservationState<K, Nothing>
}

internal fun ObservationState<*, *>.hasRetainedIssue(): Boolean =
    this is ObservationState.Available<*, *> && issue != null

internal fun ObservationState<*, *>.hasIssueOrUnavailable(): Boolean =
    when (this) {
        is ObservationState.Available<*, *> -> issue != null
        is ObservationState.Unavailable<*> -> true
        is ObservationState.Pending<*> -> false
    }

internal fun <K, T> ObservationState<K, T>.forExpectedKey(
    expectedKey: K,
): ObservationState<K, T> =
    if (key == expectedKey) this else ObservationState.Pending(expectedKey)

private class ObservationCompletedWithoutValueException :
    IllegalStateException("Observation completed before emitting a value")

internal class RetainedObservation<K, T> internal constructor(
    val state: StateFlow<ObservationState<K, T>>,
    private val retryEpoch: MutableStateFlow<Long>,
) {
    fun retry() {
        retryEpoch.update { it + 1L }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <K, T> CoroutineScope.retainedObservation(
    key: Flow<K>,
    initialKey: K,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000L),
    observe: (K) -> Flow<T>,
    mapFailure: (K, Exception) -> CatalogUiFailure,
): RetainedObservation<K, T> {
    val retryEpoch = MutableStateFlow(0L)
    val retained = MutableStateFlow<ObservationState<K, T>>(ObservationState.Pending(initialKey))

    val state = combine(key.distinctUntilChanged(), retryEpoch) { currentKey, epoch -> currentKey to epoch }
        .flatMapLatest { (currentKey, _) ->
            flow {
                when (val current = retained.value.takeIf { it.key == currentKey }) {
                    is ObservationState.Available -> emit(current)
                    is ObservationState.Pending -> emit(current)
                    else -> {
                        val pending = ObservationState.Pending<K>(currentKey)
                        retained.value = pending
                        emit(pending)
                    }
                }

                var emittedThisAttempt = false
                try {
                    observe(currentKey).collect { value ->
                        emittedThisAttempt = true
                        val available = ObservationState.Available(currentKey, value)
                        retained.value = available
                        emit(available)
                    }
                    if (!emittedThisAttempt) {
                        throw ObservationCompletedWithoutValueException()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    val failure = mapFailure(currentKey, exception)
                    val failed = when (val current = retained.value.takeIf { it.key == currentKey }) {
                        is ObservationState.Available -> current.copy(issue = failure)
                        else -> ObservationState.Unavailable(currentKey, failure)
                    }
                    retained.value = failed
                    emit(failed)
                }
            }
        }
        .stateIn(this, started, ObservationState.Pending(initialKey))

    return RetainedObservation(state, retryEpoch)
}
```

Implementation notes that are part of the contract:

- The private `MutableStateFlow` retained authority is not an active eager collector or global cache; it only stores the latest state for one observation holder inside one ViewModel scope. Repository collection still follows the returned `stateIn(... WhileSubscribed ...)` lifecycle.
- Do not clear an `Available.issue` when retry starts; clear only when the same-key upstream emits a real value.
- On same-key lifecycle restart, the inner flow re-emits retained Available rather than Pending.
- Normal completion before any real value for the current attempt is mapped through `ObservationCompletedWithoutValueException` so a required holder cannot remain Pending forever. With no same-key retained value it becomes `Unavailable`; with a same-key retained value it remains `Available(latest, issue)`.
- Normal completion after at least one real value is not an observation failure; the last real value remains `Available` without synthesizing an issue.
- On key change, `takeIf { it.key == currentKey }` fails and the holder emits Pending for the new key.
- No automatic retry loop, delay, exponential backoff, or WorkManager integration is permitted.
- `key` must be a non-failing identity/readiness-owner Flow, not repository I/O. If identity discovery itself can fail, model that failure in the owning required observation and derive the key only from its real `Available` value.
- Tests must actively collect `holder.state` (for example with `backgroundScope.launch { holder.state.collect() }`) before driving upstream flows; reading `.value` alone must not be assumed to start `SharingStarted.WhileSubscribed`. Drive zero-delay `backgroundScope` work with `runCurrent()`/`testScheduler.runCurrent()` rather than `advanceUntilIdle()`, which intentionally stops once only background work remains.

- [ ] **Step 8: Run both foundation test classes**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ContentStateContractTest*' \
  --tests '*RetainedObservationTest*' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Verify no screen runtime was changed**

```bash
rg -n 'ContentState|RetainedObservation|retainedObservation' \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui \
  -g'*.kt'
```

Expected at this checkpoint: matches only under `ui/state/`.

- [ ] **Step 10: Commit the contract lock/foundation**

```bash
git add \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/state
git commit -m "feat(catalog): add CSC presentation state contract"
```

---

## Task 2: UX-R2A — Migrate Downloads to required-first progressive enrichment

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreenshotTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:60-77`

**Interfaces:**
- Consumes `ContentState`, `CatalogUiFailure`, and `retainedObservation` from Task 1.
- Produces:
  - `DownloadsContent(active, completed, failed)` as the only content payload.
  - `DownloadsUiState.content: ContentState<DownloadsContent>`.
  - `DownloadsUiState.observationIssue: CatalogUiFailure?` as a deterministic UI summary derived from keyed observation states.
  - `DownloadsUiState.commandFailure: CatalogUiFailure?` independent from observation state.
  - `DownloadsViewModel.retryContent()` for blocking Download observation failure.
  - `DownloadsViewModel.retryObservation()` for the currently surfaced retained observation issue.

- [ ] **Step 1: Add failing ViewModel tests for required-vs-enrichment behavior**

Add these tests before changing production code:

```kotlin
@Test fun firstDownloadSnapshotRendersBeforeChapterAndCatalogMetadata()
@Test fun firstEmptyDownloadSnapshotIsReadyEmpty()
@Test fun firstDownloadObservationFailureIsBlockingFailed()
@Test fun postValueDownloadFailureRetainsReadyContentAndIssue()
@Test fun chapterMetadataFailureDoesNotRemoveDownloadContent()
@Test fun catalogMetadataFailureDoesNotRemoveDownloadContent()
@Test fun retryContentRestartsTheDownloadObservationOnly()
@Test fun commandFailureDoesNotOverwriteObservationIssue()
```

For the first test, make Chapter and Catalog flows never emit using `MutableSharedFlow(replay = 0)` while Download records emit immediately. Expected state must become `ContentState.Ready` with release-ID fallback labels.

For the first-failure test, use `flow<List<DownloadRecord>> { throw IllegalStateException("db") }`; expected state is `ContentState.Failed(CatalogUiFailure("downloads.observe_failed", true))`, not empty.

- [ ] **Step 2: Run Downloads ViewModel tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DownloadsViewModelTest*' \
  --no-daemon
```

Expected: failures/compile errors around old `loading`, `failure`, and all-input `combine` behavior.

- [ ] **Step 3: Replace Downloads state shape**

`DownloadsUiState.kt` target shape:

```kotlin
data class DownloadsContent(
    val active: List<DownloadItemUiModel> = emptyList(),
    val completed: List<DownloadItemUiModel> = emptyList(),
    val failed: List<DownloadItemUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty() && failed.isEmpty()
}

data class DownloadsUiState(
    val content: ContentState<DownloadsContent> = ContentState.Pending,
    val pendingRemoval: ChapterReleaseId? = null,
    val observationIssue: CatalogUiFailure? = null,
    val commandFailure: CatalogUiFailure? = null,
)
```

Remove `loading` and the catch-all `failure` field.

- [ ] **Step 4: Replace all three `preserveLatest()` streams with keyed retained observations**

Use private dependency keys:

```kotlin
private enum class DownloadsObservationKey { DOWNLOADS, CHAPTERS, CATALOG }
```

Each observation uses its own stable enum value as the readiness key rather than a shared `Unit`:

```kotlin
val downloadObservation = viewModelScope.retainedObservation(
    key = flowOf(DownloadsObservationKey.DOWNLOADS),
    initialKey = DownloadsObservationKey.DOWNLOADS,
    observe = { downloads.observeAll() },
    mapFailure = { _, _ -> CatalogUiFailure("downloads.observe_failed", retryable = true) },
)
```

Create the remaining holders explicitly:

```kotlin
val chapterObservation = viewModelScope.retainedObservation(
    key = flowOf(DownloadsObservationKey.CHAPTERS),
    initialKey = DownloadsObservationKey.CHAPTERS,
    observe = { chapterRepository.observeAll() },
    mapFailure = { _, _ -> CatalogUiFailure("downloads.chapters.observe_failed", retryable = true) },
)

val catalogObservation = viewModelScope.retainedObservation(
    key = flowOf(DownloadsObservationKey.CATALOG),
    initialKey = DownloadsObservationKey.CATALOG,
    observe = { catalogRepository.observe() },
    mapFailure = { _, _ -> CatalogUiFailure("downloads.catalog.observe_failed", retryable = true) },
)
```

Distinct keys and failure codes make retained issues and targeted retries unambiguous.

Do not use synthetic `emptyList()` as initial values.

- [ ] **Step 5: Project content from Download records alone, then enrich opportunistically**

Implement a private pure function:

```kotlin
private fun projectDownloads(
    records: List<DownloadRecord>,
    groups: List<CanonicalChapterGroup>?,
    projections: List<CatalogStoryProjection>?,
): DownloadsContent
```

Rules:

- `records` is required and never nullable once projecting Ready.
- `groups == null` means metadata unresolved/unavailable; use release-ID fallback without claiming “no chapter exists”.
- `projections == null` means Catalog enrichment unresolved/unavailable; use StoryId/release-ID fallback.
- Worker/Room record changes are direct `Ready -> Ready` updates.

- [ ] **Step 6: Derive blocking state, issue summary, and truthful Retry methods**

Projection logic:

```text
Download Pending       -> ContentState.Pending
Download Unavailable   -> ContentState.Failed(download failure)
Download Available     -> ContentState.Ready(projectDownloads(...))
```

If `Download Available` carries an issue after a prior value, keep Ready and include the issue in deterministic observation priority before Chapter/Catalog issues.

Implement:

```kotlin
fun retryContent() {
    if (downloadObservation.state.value is ObservationState.Unavailable) {
        downloadObservation.retry()
    }
}

fun retryObservation() {
    when {
        downloadObservation.state.value.hasRetainedIssue() -> downloadObservation.retry()
        chapterObservation.state.value.hasIssueOrUnavailable() -> chapterObservation.retry()
        catalogObservation.state.value.hasIssueOrUnavailable() -> catalogObservation.retry()
    }
}
```

Keep item-level `retry(releaseId)` unchanged; it is a Download command, not content retry.

- [ ] **Step 7: Render Pending/Failed/Ready explicitly in `DownloadsScreen`**

Change signature to add:

```kotlin
onRetryContent: () -> Unit,
onRetryObservation: () -> Unit,
```

Render:

```text
Pending -> HikariLoadingState("Loading downloads")
Failed  -> HikariErrorState("Downloads unavailable", Retry only if retryable)
Ready(empty) -> existing empty state
Ready(content) -> existing lists
```

Inside Ready, render `observationIssue` as `HikariInlineFeedback` with `Retry` action and `commandFailure` as a separate inline feedback without pretending it retries an observation.

Do not add pull-to-refresh.

- [ ] **Step 8: Wire navigation callbacks only**

In `DownloadsDestination` pass:

```kotlin
onRetryContent = viewModel::retryContent,
onRetryObservation = viewModel::retryObservation,
```

Keep `onRetry = viewModel::retry` for failed Download records.

- [ ] **Step 9: Run Downloads ViewModel tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DownloadsViewModelTest*' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 10: Update and run Downloads screenshot/Compose tests**

Add explicit screen fixtures for:

- blocking Failed with Retry;
- Ready content + observation issue;
- Ready content + command issue;
- existing empty and populated states.

Run:

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DownloadsScreenshotTest*' \
  --no-daemon
```

If connected Android tests are available:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.downloads.DownloadsScreenTest \
  --no-daemon
```

- [ ] **Step 11: Commit Downloads migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): migrate downloads to CSC state"
```

---

## Task 3: UX-R2B — Migrate Updates with dynamic Library Story-ID readiness keys

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/activity/LibraryActivityProjector.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/activity/LibraryActivityProjectorTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/updates/UpdatesScreenshotTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/updates/UpdatesScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:79-88`

**Interfaces:**
- Required: Library membership; Chapters and Mappings for the current non-empty `Set<StoryId>`.
- Enrichment: Catalog projections for current Story IDs; Reader plugin IDs.
- Produces `UpdatesContent(groups)` and `UpdatesUiState(content, observationIssue)` plus `retryContent()` / `retryObservation()`.
- Evolves the **existing reused** `LibraryActivityProjector` once so both Updates and Home can accept nullable Catalog/Reader enrichment without duplicating activity membership logic. Chapters and Mappings remain non-null required inputs to that projector.

- [ ] **Step 1: Add failing dynamic-key/short-circuit tests**

Required tests:

```kotlin
@Test fun emptyLibraryShortCircuitsToReadyEmptyWithoutOtherEmissions()
@Test fun nonEmptyLibraryWaitsForChapterAndMappingMembershipFacts()
@Test fun catalogAndReaderAvailabilityDoNotBlockProjectedUpdates()
@Test fun storyIdKeyChangeCannotReuseOldChapterOrMappingValues()
@Test fun firstRequiredFailureIsBlocking()
@Test fun postValueRequiredFailureRetainsUpdatesAndIssue()
@Test fun catalogFailureRetainsFallbackTitle()
@Test fun retryContentRestartsOnlyUnavailableRequiredInputs()
```

The key-leak regression must:

1. emit Library `{A}` + Chapter/Mapping for A and reach Ready;
2. change Library to `{B}` without emitting B Chapter/Mapping values;
3. assert state does not claim B is Ready using A data.

- [ ] **Step 2: Run Updates tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*UpdatesViewModelTest*' \
  --no-daemon
```

- [ ] **Step 3: Replace Updates state shape**

```kotlin
data class UpdatesContent(
    val groups: List<UpdatesGroupUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

data class UpdatesUiState(
    val content: ContentState<UpdatesContent> = ContentState.Pending,
    val observationIssue: CatalogUiFailure? = null,
)
```

Remove `loading` and string `failure`.

- [ ] **Step 4: Build one retained observation per dependency**

Use:

- Library key `Unit`.
- Chapters key = current `Set<StoryId>`.
- Mappings key = current `Set<StoryId>`.
- Catalog key = current `Set<StoryId>`.
- Reader capability key `Unit`.

Use these observation failure codes so each holder remains independently retryable:

```text
updates.library.observe_failed
updates.chapters.observe_failed
updates.mappings.observe_failed
updates.catalog.observe_failed
updates.reader.observe_failed
```

The Story-ID key flow is derived only from the latest real Library value. Initial key may be `emptySet()`, but the content reducer must still treat Library Pending as screen Pending; synthetic empty Story IDs must never short-circuit Library readiness.

Before evaluating Chapters/Mappings/Catalog for a non-empty Library, normalize each dynamic observation against the current Library Story-ID set:

```kotlin
val expectedKey = entries.map(LibraryEntry::storyId).toSet()
val chapterState = chapterObservation.state.value.forExpectedKey(expectedKey)
val mappingState = mappingObservation.state.value.forExpectedKey(expectedKey)
val catalogState = catalogObservation.state.value.forExpectedKey(expectedKey)
```

An old-key `Available` is therefore treated as Pending for the new projection even if the dependent holder has not yet processed its key-flow update. Apply the same `forExpectedKey(expectedKey)` normalization before deriving `observationIssue`, deciding which holder `retryObservation()` targets, or checking retryability; stale-key issues are not user-visible facts for the new Library identity.

Update the shared projector signature exactly once:

```kotlin
open fun project(
    library: List<LibraryEntry>,
    catalog: List<CatalogStoryProjection>?,
    chapters: List<CanonicalChapterGroup>,
    mappings: List<ContentMapping>,
    readerPluginIds: Set<PluginId>?,
): List<LibraryActivityItem>
```

Inside it use `catalog.orEmpty()` for title/cover fallback. A `null` Reader set means unresolved capability and yields `readerTarget = null`; an authoritative non-null set may determine support. Add projector tests `nullCatalogUsesStoryIdFallback()` and `nullReaderCapabilityKeepsActivityButOmitsReaderTarget()`. Do not create a second Updates-only projector.

- [ ] **Step 5: Implement the authoritative Updates readiness reducer**

Use exact branch order:

```text
Library Pending -> Pending
Library Unavailable -> Failed
Library Available(empty) -> Ready(empty) immediately
Library Available(non-empty):
    Chapter Unavailable or Mapping Unavailable before values -> Failed(summary)
    Chapter Pending or Mapping Pending -> Pending
    Chapter + Mapping Available -> Ready(projected updates)
```

Catalog/Reader states never block the list. Pass nullable enrichment to projection:

```kotlin
private fun projectUpdates(
    entries: List<LibraryEntry>,
    groups: List<CanonicalChapterGroup>,
    mappings: List<ContentMapping>,
    projections: List<CatalogStoryProjection>?,
    readerPluginIds: Set<PluginId>?,
): UpdatesContent
```

Use StoryId fallback title when Catalog is unresolved/failed. When Reader capability is unresolved, create update items with `readerTarget = null` but do not label that as authoritative unsupported in screen copy; no unsupported badge is added.

- [ ] **Step 6: Preserve keyed blocking causes for truthful Retry**

Keep required observation states themselves as the only mutable authorities. Derive a deterministic blocking summary with priority Library → Chapters → Mappings, but `retryContent()` retries every currently `Unavailable` required observation for the current key so a generic Retry can actually make progress.

- [ ] **Step 7: Render blocking/non-blocking failure in `UpdatesScreen`**

Add:

```kotlin
onRetryContent: () -> Unit,
onRetryObservation: () -> Unit,
```

Use `HikariErrorState` for blocking `ContentState.Failed`; use `HikariInlineFeedback` inside Ready for observation issue. No pull refresh.

- [ ] **Step 8: Wire `UpdatesDestination` callbacks**

Pass `viewModel::retryContent` and `viewModel::retryObservation` only; navigation owns no state logic.

- [ ] **Step 9: Run focused tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*UpdatesViewModelTest*' \
  --tests '*LibraryActivityProjectorTest*' \
  --no-daemon
```

- [ ] **Step 10: Update visual/semantics coverage and run it**

Add/adjust screenshot cases for Pending, blocking Failed, Ready empty, Ready content + issue. Run:

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*UpdatesScreenshotTest*' \
  --no-daemon
```

- [ ] **Step 11: Commit Updates migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): migrate updates to keyed CSC readiness"
```

---

## Task 4: UX-R3A — Migrate Library with control-sensitive local readiness

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryFilterBar.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryStoryCard.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryScreenshotTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibrarySemanticsTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryTopLevelChromeTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library/LibraryScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:162-189`

**Interfaces:**
- Screen required: Library membership.
- Normally enrichment: Catalog, mappings, progress.
- Local-required controls:
  - non-blank query or `TITLE` sort -> Catalog.
  - `LAST_ACTIVITY` sort -> Progress.
  - `LINKED`/`NO_MAPPING` source filter -> Mappings.
- Produces `LibraryContent` and `LibraryCollectionState` while screen `ContentState` remains Ready from membership.

- [ ] **Step 1: Add failing tests for truthful local readiness**

Add exact cases:

```kotlin
@Test fun firstEmptyMembershipIsReadyTrueEmpty()
@Test fun dateAddedMembershipRendersBeforeCatalogMappingAndProgressWhenControlsDoNotRequireThem()
@Test fun unresolvedMappingUsesUnknownNotNoMapping()
@Test fun unresolvedMappingDoesNotUseSearchingWithoutLifecycleSignal()
@Test fun sourceFilterShowsLocalResolvingInsteadOfFalseFilteredEmpty()
@Test fun titleQueryWaitsLocallyForFirstCatalogSnapshot()
@Test fun titleSortWaitsLocallyForFirstCatalogSnapshot()
@Test fun lastActivitySortWaitsLocallyForFirstProgressSnapshot()
@Test fun membershipFirstFailureIsBlockingFailed()
@Test fun enrichmentFailurePreservesMembershipAndSurfacesIssue()
```

Keep existing search/sort/status-count/saved-state tests and adapt only their state access. In the progressive-enrichment test explicitly select `LibrarySort.DATE_ADDED`; the production default is `LAST_ACTIVITY`, which intentionally makes Progress local-required.

- [ ] **Step 2: Run Library tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*LibraryViewModelTest*' \
  --no-daemon
```

- [ ] **Step 3: Introduce explicit Library content and local collection state**

Target types:

```kotlin
sealed interface LibraryCollectionState {
    data object Resolving : LibraryCollectionState
    data class Ready(val items: List<LibraryItemUiModel>) : LibraryCollectionState
    data class Unavailable(val failure: CatalogUiFailure) : LibraryCollectionState
}

data class LibraryContent(
    val totalCount: Int,
    val statusCounts: Map<LibraryStatus, Int>,
    val collection: LibraryCollectionState,
)

data class LibraryUiState(
    val content: ContentState<LibraryContent> = ContentState.Pending,
    val selectedStatus: LibraryStatus? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.GRID,
    val sourceFilter: LibrarySourceState? = null,
    val observationIssue: CatalogUiFailure? = null,
)
```

Add `LibrarySourceState.UNKNOWN`. Keep filter options restricted to LINKED and NO_MAPPING; UNKNOWN is presentation-only and must not be persisted/restored as a filter. Update `LibrarySourceState.label()` exhaustively, but **do not render that label for UNKNOWN in story cards**. In `LibraryStoryCard`, preserve the source-line footprint with a small `HikariSkeleton` using the existing body-small line height/shape while mappings are unresolved; omit source-state wording from `accessibilityDescription()` until the state becomes authoritative. This avoids both a false negative and noisy “loading source” text while keeping card height stable. `LibraryFilterBar` must never offer UNKNOWN as a selectable chip.

- [ ] **Step 4: Replace `preserveLatest(initial)` with retained observations**

Use constant Unit keys for:

- Library membership required.
- Catalog global projection.
- Mapping global projection.
- Progress global projection.

Map failures exactly as:

```text
library.membership.observe_failed
library.catalog.observe_failed
library.mappings.observe_failed
library.progress.observe_failed
```

All are observation-boundary failures with `retryable = true`; no code claims that WorkManager mapping search itself failed.

- [ ] **Step 5: Split base item projection from control-sensitive collection projection**

Define the control/dependency vocabulary in `LibraryViewModel.kt` before the helpers:

```kotlin
private enum class LibraryDependency {
    CATALOG,
    MAPPINGS,
    PROGRESS,
}

private data class LibraryControls(
    val query: String,
    val sort: LibrarySort,
    val sourceFilter: LibrarySourceState?,
)
```

Then implement pure helpers:

```kotlin
private fun projectLibraryBaseItems(
    entries: List<LibraryEntry>,
    catalog: List<CatalogStoryProjection>?,
    mappings: List<ContentMapping>?,
    progress: List<ReadingProgress>?,
): List<LibraryItemUiModel>

private fun requiredLocalDependencies(controls: LibraryControls): Set<LibraryDependency>

private fun projectLibraryCollection(
    baseItems: List<LibraryItemUiModel>,
    controls: LibraryControls,
): List<LibraryItemUiModel>
```

Rules:

- `mappings == null` -> `sourceState = UNKNOWN`, never NO_MAPPING/SEARCHING.
- `catalog == null` -> StoryId fallback title only when query/TITLE sort is not active.
- `progress == null` -> `updatedAt = entry.updatedAt` only when LAST_ACTIVITY is not currently promised as progress-aware ordering; otherwise local collection is Resolving.

- [ ] **Step 6: Implement local readiness without returning the whole destination to Pending**

Reducer:

```text
Library Pending -> ContentState.Pending
Library first failure -> ContentState.Failed
Library Available -> ContentState.Ready(LibraryContent(...))
```

Inside Ready:

- if an active local-required dependency is Pending -> `LibraryCollectionState.Resolving`;
- if active local-required dependency is first-value Unavailable -> `LibraryCollectionState.Unavailable(failure)`;
- otherwise -> `LibraryCollectionState.Ready(projected items)`.

Enrichment failure with a retained value stays Ready with issue.

- [ ] **Step 7: Add truthful retry boundaries**

Implement:

```kotlin
fun retryContent() // only Library membership when blocking
fun retryCollection() // retries currently unavailable local-required dependencies
fun retryObservation() // retries deterministically surfaced non-blocking issue
```

Do not schedule Library mapping WorkManager from CSC retry; Mapping observation retry only reconstructs the repository observation. Background mapping discovery remains domain-owned.

- [ ] **Step 8: Update Library presentation**

`LibraryContent` maps:

- screen Pending -> existing HikariLoadingState.
- screen Failed -> `HikariErrorState("Library unavailable", Retry)`.
- Ready with `totalCount == 0` -> existing true-empty Library copy.
- Ready + `collection.Resolving` -> render a **local collection skeleton** using the existing design-system `HikariSkeleton` inside the list/grid region; keep toolbar/filter/sort chrome interactive and preserve its measured region instead of switching the whole screen to a blocking spinner.
- Ready + `collection.Unavailable` -> local `HikariErrorState`/inline state with `retryCollection`, not “No stories match”.
- Ready + `collection.Ready(empty)` with non-empty membership -> existing filtered-empty copy.
- Ready + items -> existing grid/list. Within an otherwise Ready collection, any item whose mapping enrichment is still `UNKNOWN` keeps its source metadata line as the local `HikariSkeleton` defined in Step 3; the card itself remains interactive and does not disappear/reorder solely because mapping metadata is pending.

Render non-blocking observation issue separately from local blocking state.

- [ ] **Step 9: Wire Library Retry callbacks through `AppDestinations`**

Add screen callbacks for `onRetryContent`, `onRetryCollection`, `onRetryObservation`. Do not put dependency classification in navigation.

- [ ] **Step 10: Run focused Library unit + visual semantics tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*LibraryViewModelTest*' \
  --tests '*LibrarySemanticsTest*' \
  --tests '*LibraryTopLevelChromeTest*' \
  --tests '*LibraryScreenshotTest*' \
  --no-daemon
```

- [ ] **Step 11: Commit Library migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): add truthful library local readiness"
```

---

## Task 5: UX-R3B — Make Home Ready from Library membership and enrich sections progressively

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjector.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardSummary.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjectorTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreenshotTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardSemanticsTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:90-112`

**Interfaces:**
- Required: Library membership only.
- Enrichment: Catalog, Progress, Chapters, Mappings, completed download count, Reader capability.
- Produces a Ready payload that preserves no-content reason and unknown download count.

- [ ] **Step 1: Add failing Home contract tests**

Add:

```kotlin
@Test fun emptyLibraryBecomesReadyNoLibraryWithoutWaitingForEnrichment()
@Test fun nonEmptyLibraryRendersBaseShelvesBeforeOtherDependencies()
@Test fun allDroppedLibraryNeverUsesNoLibraryReason()
@Test fun missingDownloadCountIsUnknownNotZero()
@Test fun progressArrivalAddsContinueReadingWithoutFullScreenPending()
@Test fun chapterAndMappingArrivalAddsUpdatesShelfBeforeReaderCapability()
@Test fun readerCapabilityArrivalEnrichesHomeUpdatesWithoutFullScreenPending()
@Test fun libraryFirstFailureIsBlockingFailed()
@Test fun enrichmentFailureKeepsBaseHomeAndSurfacesIssue()
```

- [ ] **Step 2: Run Home tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*HomeDashboardViewModelTest*' \
  --tests '*HomeDashboardProjectorTest*' \
  --no-daemon
```

- [ ] **Step 3: Replace Home UiState with a single content authority**

Target types:

```kotlin
enum class HomeNoContentReason {
    NO_LIBRARY,
    LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS,
}

data class HomeReadingSummary(
    val libraryCount: Int = 0,
    val readingCount: Int = 0,
    val completedCount: Int = 0,
    val downloadedCount: Int? = null,
)

data class HomeDashboardContent(
    val summary: HomeReadingSummary,
    val continueReading: List<HomeDashboardItem>,
    val reading: List<HomeDashboardItem>,
    val planned: List<HomeDashboardItem>,
    val paused: List<HomeDashboardItem>,
    val completed: List<HomeDashboardItem>,
    val latestUpdates: List<HomeUpdateItem>,
    val noContentReason: HomeNoContentReason?,
)

data class HomeDashboardUiState(
    val content: ContentState<HomeDashboardContent> = ContentState.Pending,
    val observationIssue: CatalogUiFailure? = null,
)
```

Delete `HomeDashboardFailure`; use `CatalogUiFailure`.

- [ ] **Step 4: Make projector enrichment inputs nullable instead of synthetic negative defaults**

Refactor input:

```kotlin
data class HomeDashboardInput(
    val library: List<LibraryEntry>,
    val catalog: List<CatalogStoryProjection>? = null,
    val progress: List<ReadingProgress>? = null,
    val chapters: List<CanonicalChapterGroup>? = null,
    val mappings: List<ContentMapping>? = null,
    val readerPluginIds: Set<PluginId>? = null,
    val downloadedCount: Int? = null,
)
```

`project()` returns `HomeDashboardContent`, not UiState.

Projection rules:

- Library shelves render immediately with StoryId fallback if Catalog unresolved.
- Continue Reading is omitted/deferred while Progress has never emitted; do not conclude “no progress”.
- Chapter label enrichment may arrive after Continue Reading without blocking it.
- Latest Updates is a subsection: project it once **Chapters + Mappings** have real current-key values. Catalog and Reader capability remain nullable enrichment; unresolved Reader capability keeps activity items visible with `readerTarget = null` rather than delaying the shelf.
- Offline metric is `null` until completed-download count has a real value.
- `NO_LIBRARY` only when `library.isEmpty()`.
- If Library non-empty and all renderable shelves/updates are empty, use `LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS`, never the add-to-Library CTA.

- [ ] **Step 5: Replace all Home `preserveLatest(initial)` helpers**

Create retained observations for Library, Catalog-by-current-story-set, Progress-by-current-story-set, Chapters-by-current-story-set, Mappings-by-current-story-set, completed download count, and Reader capability. Reuse the nullable-enrichment `LibraryActivityProjector` contract established in Task 3 for Home Latest Updates; do not fork its membership/mapping logic.

Use existing Home failure-code names where they already exist, adding only Reader capability:

```text
home.library.observe_exception
home.catalog.observe_exception
home.progress.observe_exception
home.chapters.observe_exception
home.mappings.observe_exception
home.downloads.observe_exception
home.reader.observe_exception
```

Use Library Story-ID set as the key for scoped Catalog/Progress/Chapter/Mapping observations. A Story-ID key change resets only those scoped observations; it must not blank the retained screen content until the new candidate becomes usable.

Normalize every scoped observation with `forExpectedKey(currentStoryIds)` before projection. While the new-key enrichment is Pending, keep Home base content Ready and pass `null` for that enrichment; never read the old-key value during the owner/dependency scheduling window. Use those same normalized states for `observationIssue` priority and `retryObservation()` targeting so a stale issue from the old Story set cannot flash after membership changes.

- [ ] **Step 6: Implement Home base-ready reducer and same-identity content retention**

Required reducer:

```text
Library Pending -> Pending
Library Unavailable -> Failed
Library Available -> Ready(project(
    library,
    catalog = current-key Catalog Available?.value,
    progress = current-key Progress Available?.value,
    chapters = current-key Chapters Available?.value,
    mappings = current-key Mappings Available?.value,
    downloadedCount = DownloadCount Available?.value,
    readerPluginIds = ReaderCapability Available?.value,
))
```

Because Library itself supplies usable base content, optional observation key changes never push Home back to Pending. Reproject with nullable enrichment on each observation update.

- [ ] **Step 7: Make observation issues dependency-keyed internally**

Do not keep one mutable `failure` field. Derive `observationIssue` from observation states in deterministic order (Library retained issue first once Ready, then Catalog, Progress, Chapters, Mappings, Downloads, Reader capability). Keep all underlying issue states intact so recovering one input does not clear another.

Implement `retryContent()` for blocking Library failure and `retryObservation()` for the surfaced issue.

- [ ] **Step 8: Update Home UI for semantic no-content and unknown metrics**

- Pending -> existing full-screen loading.
- Failed -> full-surface `HikariErrorState("Home unavailable", Retry)`.
- Ready + `NO_LIBRARY` -> existing “Find a story and add it” CTA.
- Ready + `LIBRARY_PRESENT_BUT_NO_HOME_SECTIONS` -> render Home summary plus a non-misleading local empty message such as `"No active reading shelves yet"`; do not claim Library is empty and do not reuse the add-to-Library CTA.
- Change the summary helper to `private fun SummaryMetric(value: Int?, label: String)`; render `value?.toString() ?: "—"`. Library/Reading/Completed still pass non-null Int values, while `downloadedCount = null` renders an em dash (`—`), not `0`.
- Ready content + observation issue -> existing inline feedback with Retry action.

- [ ] **Step 9: Wire Home Retry callbacks**

Add `onRetryContent` and `onRetryObservation` to `HomeDashboardScreen`; wire from `HomeDestination`.

- [ ] **Step 10: Run Home tests and visual regressions**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*HomeDashboardProjectorTest*' \
  --tests '*HomeDashboardViewModelTest*' \
  --tests '*HomeDashboardSemanticsTest*' \
  --tests '*HomeDashboardScreenshotTest*' \
  --no-daemon
```

- [ ] **Step 11: Commit Home migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): make home progressively ready"
```

---

## Task 6: UX-R3C — Separate Chapter content, capability, refresh, and correction state

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListUiModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHero.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroActions.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListScreenshotTest.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryLayouts.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySections.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`

**Interfaces:**
- Required: `ChapterRepository.observe(storyId)` keyed by StoryId.
- Enrichment: Reader capability lookup.
- Orthogonal: `RefreshState`; correction command failure.
- Produces explicit capability `UNKNOWN` rather than false `readerCapable=false`/`downloadCapable=false` before or after a failed lookup.

- [ ] **Step 1: Add failing Chapter transition tests**

Add:

```kotlin
@Test fun chapterSnapshotRendersBeforeReaderCapability()
@Test fun firstEmptyChapterSnapshotIsReadyEmpty()
@Test fun firstChapterObservationFailureIsBlockingFailed()
@Test fun readerCapabilityPendingIsNotAuthoritativeUnsupported()
@Test fun storyHeroDoesNotShowFindSourceBeforeReaderCapabilityResolves()
@Test fun emptyChapterListOffersFindSource()
@Test fun chapterGroupsWithoutReleasesDoNotShowFindSource()
@Test fun chapterObservationFailureDoesNotShowFindSource()
@Test fun readerCapabilityFailureKeepsChapters()
@Test fun manualRefreshKeepsReadyChaptersVisible()
@Test fun newRefreshAttemptClearsOnlyPriorRefreshFailure()
@Test fun correctionFailureDoesNotOverwriteRefreshOrObservationIssue()
@Test fun retryContentRestartsChapterObservationNotChapterSync()
```

- [ ] **Step 2: Run Chapter ViewModel tests and confirm RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ChapterListViewModelTest*' \
  --no-daemon
```

- [ ] **Step 3: Introduce explicit Chapter content/capability types**

Use:

```kotlin
enum class ChapterCapabilityState {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

data class ChapterListContent(
    val chapters: List<ChapterItemUiModel>,
    val readableTargets: List<ReaderTarget>,
    val downloadableTargets: List<ReaderTarget>,
    val releaseTargets: List<ReaderTarget>,
    val chapterCount: Int,
) {
    val readerAvailabilityResolved: Boolean
        get() = chapters.flatMap(ChapterItemUiModel::releases)
            .none { it.readerCapability == ChapterCapabilityState.UNKNOWN }
}

data class ChapterListUiState(
    val storyId: StoryId,
    val content: ContentState<ChapterListContent> = ContentState.Pending,
    val refresh: RefreshState = RefreshState(),
    val selectedFilter: ChapterListFilter = ChapterListFilter.ALL,
    val showTombstones: Boolean = false,
    val observationIssue: CatalogUiFailure? = null,
    val correctionFailure: CatalogUiFailure? = null,
)
```

Change `ChapterReleaseUiModel` from Boolean capability fields to:

```kotlin
val readerCapability: ChapterCapabilityState
val downloadCapability: ChapterCapabilityState
```

Keep the Story hero decision as a **Story-local presentation model**, not another shared CSC primitive. Add in the Story presentation package:

```kotlin
internal sealed interface StoryPrimaryReadAction {
    data object CheckingChapters : StoryPrimaryReadAction
    data object ChaptersUnavailable : StoryPrimaryReadAction
    data object NoReleases : StoryPrimaryReadAction
    data object CheckingSources : StoryPrimaryReadAction
    data object FindSource : StoryPrimaryReadAction
    data class Read(val target: ReaderTarget, val isResume: Boolean) : StoryPrimaryReadAction
}
```

This type is intentionally Story-specific: it converts Chapter readiness/capability truth into one stable hero action contract and prevents multiple boolean combinations from drifting between compact and medium layouts.

- [ ] **Step 4: Retain Chapter observation independently from one-shot Reader capability**

- Chapter observation uses `retainedObservation(key = storyId)` and is the sole content blocker.
- Reader capability uses retained Unit observation around `enabledPluginIds()` + `offlineDownloadPluginIds()`.
- Reader capability Pending maps each release capability to `UNKNOWN` and leaves base Chapters Ready.
- A Reader-capability observation failure also maps capabilities to `UNKNOWN` plus an observation issue. Failure is **not** proof of unsupported capability.
- Only a successful capability snapshot may map a release to `SUPPORTED` or authoritative `UNSUPPORTED`.

Use existing Chapter codes where possible:

```text
chapter.list.observe_failed
chapter.list.reader_capability_failed
chapter.list.correction_failed
chapter.sync_failed
```

Observation/capability failures are `retryable = true`. Correction failure is `CatalogUiFailure("chapter.list.correction_failed", retryable = false)` because CSC does not retain correction arguments or invent a generic replay action; the user may explicitly repeat the original grouping/separation command. For `ChapterSyncReport.Failure`, preserve the first concrete failure code for presentation and set Retry truth from the whole refresh operation:

```kotlin
val primary = report.failures.firstOrNull()
CatalogUiFailure(
    code = primary?.code ?: "chapter.sync_failed",
    retryable = report.failures.any { it.retryable },
)
```

A thrown non-cancellation sync exception maps to `CatalogUiFailure("chapter.sync_failed", retryable = true)`.

- [ ] **Step 5: Preserve refresh semantics with the shared `RefreshState` helpers**

Keep current `ChapterSyncService.sync(storyId)` manual refresh path.

At refresh start:

```kotlin
refreshState.update(RefreshState::startAttempt)
```

On success: `completeSuccess()`.
On report/exception failure: `completeFailure(CatalogUiFailure(...))`.

Do not clear correction/observation issues at refresh start or success.

- [ ] **Step 6: Separate correction command state**

`saveOverride()` writes only `correctionFailure`; starting/succeeding a correction clears only the prior correction failure. It does not mutate `RefreshState` or observation state.

- [ ] **Step 7: Implement truthful content retry**

`retryContent()` calls Chapter observation `retry()`. It must not call `syncService.sync()` because a repository observation exception and a remote chapter refresh are distinct boundaries.

`retryObservation()` may retry the surfaced post-value Chapter/Reader-capability observation issue.

- [ ] **Step 8: Update Chapter UI behavior**

`ChapterList` maps `ContentState` explicitly:

- Pending -> current linear progress within Chapter section.
- Failed -> local/full section `HikariErrorState` with content Retry.
- Ready(empty) -> “No chapters available”.
- Ready(chapters) -> normal list.

Refresh failure remains inline while Ready. Correction failure is separate inline feedback. `UNKNOWN` capability must not display labels like “List only” or “Online only”; use a neutral unresolved label/disabled action until a successful capability snapshot resolves it. `UNSUPPORTED` may use the existing negative labels because it is authoritative.

Story embeds Chapter state in its hero action. Derive one `StoryPrimaryReadAction` in `StoryScreen` and thread that single model through `StoryHero`/`StoryHeroContent` → `StoryHeroActions`; do not separately thread `readerAvailabilityResolved`, `readerTarget`, and ad-hoc booleans into both responsive layouts. Use this exact precedence:

```text
chapterState missing or chapter content Pending
    -> CheckingChapters
chapter content Failed
    -> ChaptersUnavailable
chapter Ready with chapterCount == 0
    -> FindSource
chapter Ready with chapterCount > 0 but releaseTargets.isEmpty()
    -> NoReleases
chapter Ready with chapters/releases but any reader capability UNKNOWN
    -> CheckingSources
validated resume target exists
    -> Read(target, isResume = true)
first readable target exists
    -> Read(target, isResume = false)
otherwise (real non-empty chapters + capability fully resolved + no readable target)
    -> FindSource
```

Presentation mapping:

- `CheckingChapters` -> disabled primary action `"Loading chapters"`, test tag `story-chapters-checking`.
- `ChaptersUnavailable` -> disabled `"Chapters unavailable"`; Chapter section owns Retry, so the hero must not invent a second retry boundary.
- Chapter Ready(empty) -> existing `Find source` action so every first-entry path into Story can open source discovery; the Chapter section still owns the authoritative empty copy.
- `NoReleases` -> disabled `"No releases available"`, never `"Find source"`; this covers canonical chapter groups whose release list is empty (a shape already exercised by current Chapter/Story tests).
- `CheckingSources` -> disabled `"Checking sources"`, test tag `story-reader-checking`.
- `Read` -> existing Read/Resume action.
- `FindSource` -> existing source-discovery action.

This closes all three false-negative cases: unresolved Reader capability, authoritative empty/failed Chapter content, and non-empty canonical chapter groups that currently contain no release targets. Preserve the primary-action footprint across all states to avoid hero layout jumps.

- [ ] **Step 9: Run focused Chapter tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ChapterListViewModelTest*' \
  --tests '*ChapterPaginationTest*' \
  --tests '*ChapterListScreenshotTest*' \
  --tests '*StoryScreenshotTest*' \
  --no-daemon
```

- [ ] **Step 10: Commit Chapter migration**

```bash
git add feature/catalog
git commit -m "refactor(catalog): separate chapter readiness and capability"
```

---

## Task 7: UX-R4A — Build Discover’s terminal canonical-settlement pipeline before touching screen state

**Files:**
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalSettlement.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipeline.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipeline.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipelineTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipelineTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionTest.kt`

**Why this is a separate task:** canonical settlement and ranked-slot stability are correctness prerequisites. The ViewModel migration must consume a proven settlement model rather than inventing readiness inside a large `combine` expression.

**Interfaces:**
- Produces ordered feed slots independent from projection availability.
- Produces per-Story terminal settlement states: Pending, Projected, ResolvedExcluded, Failed.
- Produces a refresh execution result that can distinguish no enabled providers, all-failed refresh, and successful committed homes.

- [ ] **Step 1: Add failing bootstrap-settlement tests**

Add cases:

```kotlin
@Test fun existingProjectionSettlesWithoutBootstrap()
@Test fun existingLaterProjectionIsSeededBeforeEarlierMissingStoryBootstrapCompletes()
@Test fun canonicalReadyWithMatchingProjectionSettlesProjected()
@Test fun canonicalReadyWithDifferentContentTypeSettlesExcluded()
@Test fun ensureReadyReturningPreparingSettlesFailedInsteadOfPermanentPending()
@Test fun canonicalReadyWithoutProjectionSettlesFailed()
@Test fun oneStoryFailureDoesNotPreventLaterStorySettlement()
@Test fun settlementEmitsProgressivelyInInputOrder()
```

Use explicit feature failure codes and retryability:

- `catalog.discover.canonical_still_preparing`, `retryable = false` — the owned bootstrap attempt completed without a usable state, so an immediate generic retry is not claimed to make progress.
- `catalog.discover.canonical_bootstrap_failed`, `retryable = true` — a thrown bootstrap boundary may be retried.
- `catalog.discover.projection_lookup_failed`, `retryable = true` — canonical was Ready but the projection repository read failed.
- `catalog.discover.projection_missing`, `retryable = true` — canonical is Ready but its derived projection was not observable yet; restarting current settlement is the truthful recovery boundary.

All are feature presentation failures; do not modify `CanonicalBootstrapUseCase`.

- [ ] **Step 2: Introduce settlement types**

`DiscoverCanonicalSettlement.kt`:

```kotlin
internal enum class DiscoverExclusionReason {
    CONTENT_TYPE_MISMATCH,
}

internal sealed interface DiscoverCanonicalSettlement {
    val storyId: StoryId

    data class Projected(
        override val storyId: StoryId,
        val projection: CatalogStoryProjection,
    ) : DiscoverCanonicalSettlement

    data class ResolvedExcluded(
        override val storyId: StoryId,
        val reason: DiscoverExclusionReason,
    ) : DiscoverCanonicalSettlement

    data class Failed(
        override val storyId: StoryId,
        val failure: CatalogUiFailure,
    ) : DiscoverCanonicalSettlement
}
```

Pending is represented by absence from the current settlement map for an expected Story ID; this avoids a second mutable Pending authority.

- [ ] **Step 3: Make `DiscoverCanonicalBootstrapPipeline` return progressive terminal outcomes**

Inject `CatalogStoryProjectionRepository` in addition to `CanonicalBootstrapUseCase`.

Target API:

```kotlin
internal fun settle(
    storyIds: List<StoryId>,
    selectedContentType: ContentType,
): Flow<Map<StoryId, DiscoverCanonicalSettlement>>
```

Algorithm:

1. Deduplicate input IDs while preserving order, then attempt **one initial snapshot** with `projections.observeForStories(expectedIds.toSet()).first()`. Rethrow cancellation. If this best-effort seed snapshot throws a non-cancellation `Exception`, continue with an empty seed and let the per-Story bootstrap/projection checks below decide terminal outcomes; the optimization itself must not become a new global blocker. When the snapshot succeeds, seed every matching existing projection before any bootstrap begins and seed a known content-type mismatch as `ResolvedExcluded`. Emit the immutable seed map immediately. This prevents one slow/missing high-ranked Story from delaying unrelated Stories whose durable projections already exist.
2. Iterate only the still-unsettled Story IDs in original order and call `bootstrap.ensureReady(storyId)` sequentially. Keep this bootstrap loop sequential in CSC-v1; do not add new concurrency/domain scheduling policy just for presentation.
3. If result is `CanonicalStoryState.Preparing`, settle Failed with `canonical_still_preparing`; never leave absent/Pending after the attempt completes.
4. If Ready content type differs, settle ResolvedExcluded.
5. If Ready type matches, call `projections.find(storyId)` after bootstrap in its own exception boundary: projection present -> Projected; null -> Failed `projection_missing`; non-cancellation projection exception -> Failed `projection_lookup_failed`. Do not mislabel a projection read failure as bootstrap failure.
6. Rethrow cancellation from both bootstrap and projection reads; non-cancellation exceptions thrown by `bootstrap.ensureReady` -> Failed `canonical_bootstrap_failed`.
7. Emit a new immutable map after each terminal outcome. Stable-prefix projection still decides whether an unresolved earlier slot may expose later slots **within its own section**; pre-seeding only reduces unnecessary waiting, it does not promote rank.

Do not add automatic retries.

- [ ] **Step 4: Add ordered semantic slot types**

Refactor private `projectPopular/projectLatest/projectTopRated` results into:

```kotlin
internal data class DiscoverFeedSlots(
    val popular: List<StoryId>,
    val latestUpdates: List<StoryId>,
    val topRated: List<StoryId>,
) {
    val expectedStoryIds: List<StoryId>
        get() = buildList {
            addAll(popular)
            addAll(latestUpdates)
            addAll(topRated)
        }.distinct()
}
```

Create `discoverFeedSlots(homes, selectedContentType)` and make `discoverCanonicalBootstrapStoryIds()` delegate to `expectedStoryIds` so ranking logic has one authority.

- [ ] **Step 5: Implement stable-prefix projection rules**

Refactor projection to consume ordered slots plus current settlement map/live projections. For each section:

- Projected -> include item.
- ResolvedExcluded -> skip permanently and continue.
- Failed -> skip permanently, record issue, continue.
- Missing settlement for an expected earlier slot -> stop exposing later slots for that section until it settles.

This “wait at first unresolved slot” rule is the chosen CSC-v1 implementation of semantic slot stability. It avoids placeholder UI and prevents rank-2 from temporarily becoming hero while rank-1 is unresolved.

Return a projection result carrying:

```kotlin
internal data class DiscoverProjectionResult(
    val content: DiscoverSemanticContent,
    val pendingSlots: Int,
    val failures: Map<StoryId, CatalogUiFailure>,
    val expectedSlots: Int,
)
```

- [ ] **Step 6: Add projection tests for rank stability**

Required tests:

```kotlin
@Test fun unresolvedPopularLeaderPreventsLowerRankPromotion()
@Test fun resolvedExcludedLeaderAllowsNextStoryToBecomeStableLeader()
@Test fun failedLeaderAllowsNextStoryAfterFailureIsTerminal()
@Test fun unresolvedLaterSlotDoesNotHideStableEarlierPrefix()
@Test fun allResolvedExcludedProducesAuthoritativeEmpty()
@Test fun terminalFailuresWithNoProjectedItemsAreNotFalseEmpty()
```

- [ ] **Step 7: Make Discover refresh execution classify provider availability from real post-refresh homes**

Refactor `DiscoverRefreshPipeline` to inject `CatalogRepository` and change its feature API to:

```kotlin
internal suspend fun refresh(): DiscoverRefreshExecution
```

The old `cachedHomes` argument is removed; the post-refresh repository snapshot becomes the sole report baseline. Return:

```kotlin
internal data class DiscoverRefreshExecution(
    val report: DiscoverRefreshReport,
    val homes: List<CatalogHomeSnapshot>,
    val anyRetryableFailure: Boolean,
) {
    val noEnabledProviders: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isEmpty()

    val allProvidersFailed: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isNotEmpty()
}
```

After `CatalogRefreshService.refresh(...)` completes, obtain a fresh repository snapshot using a **new** `repository.observeHomes().first()` collection so the returned execution reflects committed durable state, including successful empty sections. Build `refreshedAtEpochMillis` from this post-refresh snapshot, not stale pre-refresh homes. Compute `anyRetryableFailure` from the raw `CatalogRefreshResult` failures **before** converting them into the existing code-only `DiscoverRefreshReport`; do not infer retryability from a string code.

Keep Catalog refresh service/domain API unchanged.

- [ ] **Step 8: Run Discover pipeline tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverCanonicalBootstrapPipelineTest*' \
  --tests '*DiscoverProjectionPipelineTest*' \
  --tests '*DiscoverProjectionTest*' \
  --no-daemon
```

Expected: PASS before ViewModel migration.

- [ ] **Step 9: Commit settlement pipeline**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover
git commit -m "refactor(catalog): make discover canonical settlement terminal"
```

---

## Task 8: UX-R4B — Migrate Discover ViewModel and UI to bootstrap-vs-refresh CSC semantics

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverContentItems.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverEmptyContent.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverFeedback.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenshotTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverTopLevelChromeTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:114-140`

**Interfaces:**
- Required: Home observation plus terminal settlement of ordered expected slots.
- Automatic empty-cache bootstrap state is internal content readiness, never `RefreshState.inProgress`.
- Manual user refresh uses `RefreshState` and preserves last Ready content.
- Produces `DiscoverContent` with `DiscoverNoContentReason`.

- [ ] **Step 1: Add failing ViewModel transition tests from CSC §18.7**

Add/replace tests for:

```kotlin
@Test fun emptyCacheBootstrapIsPendingButNotRefreshing()
@Test fun successfulProviderWithEmptyFeedBecomesReadyEmptyFeed()
@Test fun noEnabledProvidersBecomesReadyNoProviderReason()
@Test fun allProviderFailuresWithNoCacheBecomeBlockingFailed()
@Test fun cachedReadyContentNeverReturnsToPendingDuringManualRefresh()
@Test fun unresolvedCanonicalLeaderCannotCreateFalseEmptyOrHeroPromotion()
@Test fun terminalPartialCanonicalFailureKeepsStableReadyContentAndIssue()
@Test fun terminalCanonicalFailureWithNoUsableContentIsFailed()
@Test fun retryContentRestartsHomeObservationOrBootstrapBoundaryNotPullRefreshChrome()
@Test fun newManualRefreshAttemptClearsOnlyOldRefreshFailure()
```

Keep existing source-isolation/priority-selector tests; adapt pipeline result access only.

- [ ] **Step 2: Replace Discover state shape**

Use:

```kotlin
enum class DiscoverNoContentReason {
    EMPTY_FEED,
    NO_ENABLED_PROVIDERS,
}

data class DiscoverContent(
    val selectedContentType: ContentType,
    val mediaTypeOptions: List<DiscoverMediaTypeOption>,
    val popular: List<DiscoverStoryItem>,
    val latestUpdates: List<DiscoverStoryItem>,
    val topRated: List<DiscoverStoryItem>,
    val noContentReason: DiscoverNoContentReason? = null,
) {
    val hasContent: Boolean
        get() = popular.isNotEmpty() || latestUpdates.isNotEmpty() || topRated.isNotEmpty()
}

data class DiscoverUiState(
    val content: ContentState<DiscoverContent> = ContentState.Pending,
    val refresh: RefreshState = RefreshState(),
    val refreshReport: DiscoverRefreshReport? = null,
    val observationIssue: CatalogUiFailure? = null,
)
```

Remove `loading`, `refreshing`, `refreshFailure`, `observationFailure`, and `globalFailure` as independent authorities.

- [ ] **Step 3: Replace home `preserveLatestOnFailure` with retained observation**

Home observation is key `Unit`. A first observation failure is blocking unless a prior same-key home snapshot has already led to usable Ready content. Expose `homeObservation.retry()` through content Retry when it is the blocking cause.

- [ ] **Step 4: Build keyed settlement observation from current feed identity**

Define an internal key:

```kotlin
private data class DiscoverSettlementKey(
    val contentType: ContentType,
    val storyIds: List<StoryId>,
)
```

The key uses ordered `expectedStoryIds`, not a Set, because ordering is semantic. Use retained observation around `canonicalBootstrap.settle(key.storyIds, key.contentType)`.

Old-key settlement maps/issues must be discarded when homes/content type change. Before candidate projection, `observationIssue` selection, `retryObservation()`, or retryability decisions, normalize settlement observation with `forExpectedKey(currentSettlementKey)`; an issue from the prior content type/feed identity must disappear immediately when the key changes.

- [ ] **Step 5: Separate automatic bootstrap state from manual refresh state**

Use a private bootstrap state such as:

```kotlin
private sealed interface DiscoverBootstrapState {
    data object NotNeeded : DiscoverBootstrapState
    data object InFlight : DiscoverBootstrapState
    data class Completed(val execution: DiscoverRefreshExecution) : DiscoverBootstrapState
    data class Failed(val failure: CatalogUiFailure) : DiscoverBootstrapState
}
```

On first real Home snapshot:

- non-empty homes -> NotNeeded; do not auto source-refresh.
- empty homes -> InFlight, call `DiscoverRefreshPipeline.refresh()` without mutating `RefreshState`.
- execution no providers -> Completed and `Ready(no providers)`.
- execution all providers failed and no usable homes -> Failed.
- execution with success -> treat `DiscoverRefreshExecution.homes` as an immediate bootstrap candidate snapshot while the long-lived Home observation catches up; feed that candidate through the exact same slot/settlement projection path, then replace it with the real observed snapshot when it arrives. Never maintain two projection algorithms.

A completed bootstrap that still has no observable path toward content must leave Pending and become Ready no-content or Failed; it may not wait indefinitely.

- [ ] **Step 6: Implement a candidate projection and retain last usable Ready across same-identity convergence**

Build a `candidateContent: Flow<ContentState<DiscoverContent>>` from Home state + bootstrap + feed slots + settlement/projection result.

Then apply a feature-owned reducer with these exact rules:

```text
previous Ready + candidate Pending, same selected content identity -> keep previous Ready
previous Ready + candidate Failed caused by new background/refresh convergence -> keep previous Ready and surface issue
no previous Ready + candidate Pending -> Pending
no previous Ready + terminal failure -> Failed
candidate Ready -> replace previous Ready
```

Do not generalize this reducer into a global cache engine; keep it private to Discover because it depends on Discover feed identity/canonical convergence.

- [ ] **Step 7: Implement manual refresh independently**

`refresh()`:

1. guard duplicate attempt;
2. `refreshState = refreshState.startAttempt()`;
3. run `DiscoverRefreshPipeline.refresh()`;
4. store `refreshReport`;
5. on success `completeSuccess()` even if provider report contains partial failures (the report handles partial domain detail);
6. on thrown boundary failure `completeFailure(CatalogUiFailure("catalog.home.refresh_exception", true))`;
7. never clear `content` or observation issue.

- [ ] **Step 8: Implement truthful blocking/non-blocking Retry**

`retryContent()` selects the actual blocking boundary:

- Home observation Unavailable -> retry Home observation.
- Bootstrap Failed is retryable only when the captured bootstrap/refresh execution proves a retryable boundary (for all-provider failures use `anyRetryableFailure`); if retryable, rerun the automatic bootstrap operation without setting pull-refresh chrome. `NO_ENABLED_PROVIDERS` is a Ready setup state, not a blocking failure and has no generic Retry.
- Settlement observation Unavailable/terminal retryable canonical failures preventing all content -> restart current settlement observation.

`retryObservation()` retries the currently surfaced retained issue while Ready.

- [ ] **Step 9: Update Discover UI mapping**

`HikariPullToRefresh(refreshing = state.refresh.inProgress)` only.

Content rendering:

- Pending -> `DiscoverLoadingContent`.
- Failed -> `HikariErrorState` with `onRetryContent`.
- Ready + `NO_ENABLED_PROVIDERS` -> distinct setup/no-provider empty copy; do not call it an empty feed.
- Ready + `EMPTY_FEED` -> normal Discover empty copy.
- Ready content -> existing Popular/Latest/Top Rated sections.

`DiscoverFeedback` renders refresh failure and observation issue as separate rows. Refresh Retry calls `onRefresh`; observation Retry calls `onRetryObservation`.

- [ ] **Step 10: Wire `DiscoverDestination`**

Pass:

```kotlin
onRetryContent = viewModel::retryContent,
onRetryObservation = viewModel::retryObservation,
```

Keep `onRefresh = viewModel::refresh` distinct.

- [ ] **Step 11: Run Discover ViewModel/presentation tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverViewModelTest*' \
  --tests '*DiscoverSemanticsTest*' \
  --tests '*DiscoverTopLevelChromeTest*' \
  --tests '*DiscoverScreenshotTest*' \
  --no-daemon
```

- [ ] **Step 12: Commit Discover CSC migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): migrate discover bootstrap and refresh state"
```

---

## Task 9: UX-R4C — Migrate Story canonical bootstrap without conflating source-detail refresh

**Files:**
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryLayouts.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySections.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHero.kt`
- Modify `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroActions.kt` only for truthful Library enrichment readiness
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`
- Modify `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story/StoryScreenTest.kt`
- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:216-267`

**Interfaces:**
- Required: route-keyed canonical Story observation + explicit route-keyed canonical bootstrap outcome.
- Enrichment: resolved-Story-keyed Library membership and ReadingProgress, reconciliation, best-effort Full metadata.
- Manual refresh: source-detail `CatalogMetadataCoordinator.refresh`, distinct from content Retry.
- Command failure: source-preference/rebuild, distinct from refresh/observation.
- Produces `retryContent()` for the blocking canonical boundary and `retryObservation()` for the currently surfaced non-blocking canonical/Library/Progress observation issue.

- [ ] **Step 1: Add failing Story readiness/failure tests**

Add:

```kotlin
@Test fun preparingWhileBootstrapInFlightIsPendingNotUnavailable()
@Test fun bootstrapReturningPreparingTerminatesAsFailedNotPermanentPending()
@Test fun canonicalReadyBecomesReadyStory()
@Test fun bootstrapReadyRendersEvenWhenCanonicalObservationFirstFails()
@Test fun resolvedStoryIdChangeCannotReuseOldPersonalObservationState()
@Test fun canonicalObservationStaysScopedToStableRouteStoryId()
@Test fun bootstrapExceptionWithoutContentIsBlockingFailed()
@Test fun contentRetryRestartsCanonicalObservationAndBootstrapNotSourceRefresh()
@Test fun sourceDetailRefreshKeepsReadyStoryVisible()
@Test fun observationBootstrapPreferenceAndRefreshFailuresDoNotOverwriteEachOther()
@Test fun libraryAndProgressEnrichmentDoNotBlockStoryBody()
@Test fun unresolvedLibraryMembershipDoesNotPretendStoryIsNotInLibrary()
```

Retain existing canonical-presentation/source-order/reconciliation tests.

- [ ] **Step 2: Replace Story content/failure shape**

Target:

```kotlin
data class StoryUiState(
    val storyId: StoryId,
    val content: ContentState<StoryUiModel> = ContentState.Pending,
    val selectedSource: StorySourceIdentity? = null,
    val refresh: RefreshState = RefreshState(),
    val observationIssue: CatalogUiFailure? = null,
    val commandFailure: CatalogUiFailure? = null,
    val libraryStatus: LibraryStatus? = null,
    val libraryStatusResolved: Boolean = false,
    val resumeTarget: ReaderTarget? = null,
    val selectedSection: StorySection = StorySection.OVERVIEW,
    val reconciliationPrompt: StoryReconciliationPromptUiModel? = null,
    val reconciliationResolving: Boolean = false,
    val reconciliationFailureMessage: String? = null,
)
```

Remove `story: StoryUiModel?`, `refreshing`, and shared `StoryRefreshFailure`. Reuse `CatalogUiFailure`.

- [ ] **Step 3: Create retained canonical and personal observations**

Name the two identities explicitly before creating observations:

```kotlin
val routeStoryId: StoryId = assistedArgs.storyId
// resolvedStoryId is derived from a real canonical Ready state, otherwise routeStoryId.
```

- **Canonical observation/bootstrap key = `routeStoryId` only.** The source is `canonical.observeStory(routeStoryId)` and its `Available` value may legitimately be `null`. `Available(null)` means “no canonical state in this real snapshot”, **not** observation failure and not Ready; while bootstrap is InFlight it remains Pending, and a failed/completed-nonready bootstrap terminally classifies it. `bootstrap.ensureReady(...)`, content Retry, metadata source lookup, and canonical source-preference commands continue to address this route-owned canonical identity.
- **Library observation key = current `resolvedStoryId`.** It may still consume the existing global `library.observe()` and select the current resolved story inside the observation factory.
- **Progress observation key = current `resolvedStoryId`.** It may consume `progress.observeAll()` and select the current resolved story.
- Do not use `Unit` for Library/Progress: when canonical reconciliation changes the resolved Story from A to B inside the same route ViewModel, old A membership/progress is no longer truthful for B.
- Do not emit synthetic empty Library/Progress before first values.
- Normalize canonical state against `routeStoryId`; normalize Library/Progress against the **current `resolvedStoryId`** before reducer logic, issue selection, or Retry targeting. Never normalize the canonical observation against a different resolved canonical ID: doing so would turn a valid route-keyed canonical Ready state into false Pending after reconciliation.
- `StoryUiState.storyId` remains the resolved canonical Story ID used by presentation/navigation targets; the canonical readiness boundary itself remains route-keyed.

For personal enrichment:

- `libraryStatusResolved=false` until a real membership snapshot arrives; Story body remains Ready independently.
- Once resolved, null status means authoritatively not in Library.
- Progress Pending may omit Resume enrichment; it must not block Story body.

- [ ] **Step 4: Add an explicit canonical bootstrap-attempt state**

Use:

```kotlin
private sealed interface StoryBootstrapState {
    data object InFlight : StoryBootstrapState
    data class Completed(val state: CanonicalStoryState) : StoryBootstrapState
    data class Failed(val failure: CatalogUiFailure) : StoryBootstrapState
}
```

Initial task:

1. set InFlight;
2. call `bootstrap.ensureReady(routeStoryId)`;
3. if Ready -> Completed(Ready), then best-effort `fullMetadata.requireFull(routeStoryId)`;
4. if Preparing -> Completed(Preparing), which maps terminally to `CatalogUiFailure("catalog.story.canonical_still_preparing", retryable = false)` unless canonical observation has already moved to Ready;
5. exception -> Failed `CatalogUiFailure("catalog.story.canonical_bootstrap_failed", retryable = true)`.

Do not change `CanonicalBootstrapUseCase` or wait on unspecified background continuation.

- [ ] **Step 5: Build Story content reducer with explicit Pending exit**

Branch order (normalize the canonical observation to `routeStoryId` first; personal observations are normalized separately to the current `resolvedStoryId`):

```text
canonical observation Available(Ready) -> ContentState.Ready(model)
bootstrap Completed(Ready) + no observed Ready yet -> ContentState.Ready(model from bootstrap result)
bootstrap InFlight + no usable Ready -> Pending
bootstrap Failed + no usable Ready -> Failed(bootstrap failure)
bootstrap Completed(Preparing) + still no usable Ready -> Failed(canonical_still_preparing)
canonical observation Unavailable + bootstrap has no InFlight/Completed(Ready) recovery -> Failed(observation failure)
```

A first canonical observation failure must **not** pre-empt an in-flight bootstrap that can still produce a usable `CanonicalStoryState.Ready`. If bootstrap provides Ready while observation is unavailable, render that Story immediately and surface the observation failure as a non-blocking issue; the next successful observation may replace/confirm it. If canonical later emits Ready after a terminal Failed, naturally transition Failed -> Ready.

- [ ] **Step 6: Implement `retryContent()` as canonical readiness retry**

`retryContent()`:

- retries canonical observation if Unavailable;
- starts a new bootstrap attempt for `routeStoryId`;
- does **not** call source-detail `refresh()`;
- sets content Pending only when no usable Story is retained.

Keep source-detail `refresh()` as a separate public method for pull refresh.

Implement `retryObservation()` separately for Ready content. Select the first currently surfaced issue in deterministic order: canonical route-keyed issue/unavailable → Library current-resolved-key issue/unavailable → Progress current-resolved-key issue/unavailable. Normalize every holder to its own expected key before both issue selection and retry targeting. Do not rerun canonical bootstrap from `retryObservation()` unless canonical readiness itself is blocking; that belongs to `retryContent()`.

- [ ] **Step 7: Convert source-detail manual refresh to `RefreshState`**

At new attempt start, clear only old refresh failure. Keep current Story `Ready` content.

Map metadata result failures to `CatalogUiFailure` using the existing code mapping. Change the helper signatures explicitly:

```kotlin
private fun CatalogMetadataResult.failureOrNull(): CatalogUiFailure?
private fun CatalogMetadataFailure.toUiFailure(): CatalogUiFailure
```

Delete `StoryRefreshFailure` only after every Story call site uses the scoped CSC channels. Refresh success must not clear observation/command failures.

- [ ] **Step 8: Separate source-preference command failure**

`updatePreference()` writes only `commandFailure`; success clears only prior command failure. Selecting an inspection source must not clear unrelated observation/refresh issues.

- [ ] **Step 9: Make Library actions truthful while membership is unresolved**

Pass `libraryStatusResolved` through `StoryHero`/`StoryHeroActions`.

While false:

- keep Story body/read actions usable;
- disable Library mutation choices or show them as locally loading;
- do not interpret `libraryStatus=null` as “not in Library”.

Once true, existing add/change/remove behavior applies.

Also guard the ViewModel mutation boundary itself:

```kotlin
fun changeLibraryStatus(status: LibraryStatus?) {
    if (!state.value.libraryStatusResolved) return
    // existing mutation path
}
```

The UI disablement is presentation; this guard prevents tests, stale callbacks, or future call sites from converting unresolved membership into an authoritative mutation.

- [ ] **Step 10: Update StoryScreen to map `ContentState` explicitly**

- Pending -> current `HikariLoadingState("Loading story")`.
- Failed -> `HikariErrorState("Story unavailable", action=retryContent)`.
- Ready -> existing responsive Story layout.

`HikariPullToRefresh` uses `state.refresh.inProgress`; refresh failure is inline in Ready layout. `observationIssue` uses `onRetryObservation`; `commandFailure` is a separate inline feedback row with no generic Retry unless the original command arguments are still explicitly available. A later explicit command attempt clears/replaces only its own command failure.

- [ ] **Step 11: Update App destination prewarm gate**

Replace:

```kotlin
LaunchedEffect(storyId, state.story != null)
```

with Ready inspection:

```kotlin
val storyReady = state.content is ContentState.Ready
LaunchedEffect(storyId, storyReady) {
    if (!storyReady || prewarmSections) return@LaunchedEffect
    withFrameNanos { }
    prewarmSections = true
}
```

Pass `onRetryContent = viewModel::retryContent` and `onRetryObservation = viewModel::retryObservation` separately from `onRefresh = viewModel::refresh`.

- [ ] **Step 12: Run Story tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*StoryViewModelTest*' \
  --tests '*StoryScreenshotTest*' \
  --no-daemon
```

Also run Chapter tests because Story embeds Chapter state:

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ChapterListViewModelTest*' \
  --tests '*StoryViewModelTest*' \
  --no-daemon
```

- [ ] **Step 13: Commit Story migration**

```bash
git add feature/catalog app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "refactor(catalog): separate story readiness from refresh"
```

---

## Task 10: UX-R5A — Remove legacy loading/preserveLatest authorities and correct active refresh docs

**Files:**
- Verify/remove legacy authorities from these exact migrated production files if the prior task left any match:
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListUiModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingUiState.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModel.kt`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewUiState.kt`
- Create `build-logic/src/test/kotlin/app/openstory/build/ContentStateContractArchitectureTest.kt`
- Modify `docs/ui/design-system.md:109-114`

**Interfaces:** none new. This task freezes the migration and removes compatibility crutches without placing repository-boundary assertions inside a feature test module.

- [ ] **Step 1: Add the repository-level architecture freeze test**

Create `build-logic/src/test/kotlin/app/openstory/build/ContentStateContractArchitectureTest.kt`:

```kotlin
package app.openstory.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContentStateContractArchitectureTest {
    private val root = File("..").canonicalFile

    private val migratedViewModels = listOf(
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModel.kt",
    )

    private val migratedUiStates = listOf(
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListUiModel.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingUiState.kt",
        "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewUiState.kt",
    )

    @Test
    fun migratedCatalogViewModelsContainNoLegacyRetainedHelpers() {
        val forbidden = Regex("""\bpreserveLatest\w*\s*\(""")
        val offenders = migratedViewModels.flatMap { relative ->
            val source = File(root, relative).readText()
            forbidden.findAll(source).map { match -> "$relative -> ${match.value}" }.toList()
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun migratedUiStatesContainNoIndependentContentLoadingAuthority() {
        val storedUiLoading = Regex("""\b(?:val|var)\s+\w*[Ll]oading\w*\s*:\s*Boolean\b(?!\s*get\s*\()""")
        val mutableViewModelLoading = Regex(
            """\b(?:val|var)\s+\w*[Ll]oading\w*.*(?:MutableStateFlow|mutableStateOf|=\s*(?:true|false))""",
        )
        val offenders = migratedUiStates.filter { relative ->
            storedUiLoading.containsMatchIn(File(root, relative).readText())
        } + migratedViewModels.filter { relative ->
            mutableViewModelLoading.containsMatchIn(File(root, relative).readText())
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun cscFoundationStaysAtTheApprovedFeatureLocalPathAndReaderDoesNotAdoptIt() {
        val expected = listOf(
            "ContentState.kt",
            "CatalogUiFailure.kt",
            "RefreshState.kt",
            "RetainedObservation.kt",
        ).map { File(root, "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state/$it") }

        val missing = expected.filterNot(File::isFile).map { it.relativeTo(root).path }
        val readerImports = listOf("feature/reader/src/main", "reader/src/main", "reader/engine/src/main")
            .asSequence()
            .flatMap { File(root, it).walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { "app.openstory.catalog.ui.state" in it.readText() }
            .map { it.relativeTo(root).path }
            .toList()

        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
        assertTrue(readerImports.isEmpty(), readerImports.joinToString("\n"))
    }
}
```

The production test must additionally scan all ten migrated package roots for extracted `preserveLatest*` helpers and synthetic observation fallbacks, allow only the reviewed optional Story reconciliation fallback, verify the four CSC declarations are unique at the approved path, reject non-Kotlin/non-coroutines imports from the CSC foundation, and reject CSC adoption by `:core:designsystem`, `:feature:reader`, `:reader`, or `:reader:engine`.

- [ ] **Step 2: Run the build-logic freeze test and fix every reported migrated-path match**

```bash
./gradlew :build-logic:test \
  --tests '*ContentStateContractArchitectureTest*' \
  --no-daemon
```

Expected: PASS only after all duplicated helpers/authorities are gone and CSC is still feature-local.

- [ ] **Step 3: Correct Chapter refresh documentation**

Update `docs/ui/design-system.md` so the active policy states:

- pull gesture exists only where the feature has a matching refresh pipeline;
- Story Overview and Sources use Story-owned source-detail metadata refresh, while Story Chapters uses its own `ChapterSyncService.sync(storyId)` pipeline;
- all three sections are refreshable, but progress and failure channels remain operation-scoped;
- refresh remains on the Story surface/section rather than reintroducing old visible reload glyphs;
- background sync does not equal pull-refresh state;
- the design-system document links to `docs/superpowers/specs/2026-08-27-content-state-contract-v1-design.md` as the authoritative **state-semantics** contract, while explicitly stating that `:core:designsystem` owns only rendering primitives and does not own feature `UiState`, cache lifetime, or refresh scheduling.

Do not rewrite archive/checkpoint documents that were historically correct when written.

- [ ] **Step 4: Search for stale CSC contradictions**

Run:

```powershell
rg -n 'Story Chapters is intentionally not refreshable|preserveLatest\(|preserveLatestOnFailure|\w*[Ll]oading\w*\s*:\s*Boolean' docs/ui feature/catalog/src/main/kotlin/app/openstory/catalog/ui -g '*.md' -g '*.kt'
```

Expected:

- no stale Chapter non-refresh statement in active docs;
- no local preserveLatest helper in migrated paths;
- no independent `loading:Boolean` content authority in migrated UiStates.

- [ ] **Step 5: Commit cleanup/docs freeze**

```bash
git add feature/catalog build-logic/src/test/kotlin/app/openstory/build/ContentStateContractArchitectureTest.kt docs/ui/design-system.md
git commit -m "test(catalog): freeze CSC presentation boundaries"
```

---

## Task 11: UX-R5B — Cross-screen CSC regression suite and architecture closure

**Files:**
- Create `docs/internal/checkpoints/content-state-contract-v1-closure.md` **only after** the verification commands below have been executed.
- No production/test source change is owned by this task. If any command fails, return to the earlier task that owns that behavior, fix it there, rerun that task's focused tests, then restart Task 11. Do not hide a production or contract change inside the closure gate.

- [ ] **Step 1: Run all focused migrated ViewModel tests together**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*ContentStateContractTest*' \
  --tests '*RetainedObservationTest*' \
  --tests '*DownloadsViewModelTest*' \
  --tests '*UpdatesViewModelTest*' \
  --tests '*LibraryViewModelTest*' \
  --tests '*HomeDashboardViewModelTest*' \
  --tests '*ChapterListViewModelTest*' \
  --tests '*DiscoverCanonicalBootstrapPipelineTest*' \
  --tests '*DiscoverProjectionPipelineTest*' \
  --tests '*DiscoverViewModelTest*' \
  --tests '*StoryViewModelTest*' \
  --tests '*SearchViewModelTest*' \
  --tests '*MappingViewModelTest*' \
  --tests '*ReconciliationReviewViewModelTest*' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run the complete Catalog feature unit suite**

```bash
./gradlew :feature:catalog:testDebugUnitTest --no-daemon
```

Expected: PASS, including the approved Search/Mapping/Reconciliation CSC regressions.

- [ ] **Step 3: Run app navigation/unit regressions**

Because `AppDestinations.kt` callback signatures and Story Ready inspection changed:

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Run architecture/module gates**

```bash
./gradlew :build-logic:test \
  --tests '*ModuleGraphTest*' \
  --tests '*ModuleBoundaryVerifierTest*' \
  --tests '*ContentStateContractArchitectureTest*' \
  --no-daemon

./gradlew verifyArchitecture --no-daemon

bash scripts/tests/verify-current-architecture-test.sh
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Expected:

- no new module/dependency cycle;
- no domain module depends on `:feature:catalog`;
- `:core:designsystem` contains no feature UiState/readiness ownership;
- Reader architecture remains unchanged.

- [ ] **Step 5: Run compile + full relevant feature regression**

```bash
./gradlew \
  :feature:catalog:compileDebugKotlin \
  :feature:catalog:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :reader:testDebugUnitTest \
  --no-daemon
```

The Reader suites are guardrails only; CSC should not require Reader production changes.

- [ ] **Step 6: Run connected UI tests when an emulator/device is available**

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest --no-daemon
```

If unavailable, record it as environment-blocked; do not claim this gate passed.

- [ ] **Step 7: Run diff hygiene checks**

```bash
git diff --check

git diff --name-only | sort
```

Audit expected production changes only under `feature/catalog`, navigation callback wiring under `app`, active UI docs, and CSC spec/plan files. Any change to Reader engine/domain cache/WorkManager requires rejection or a separate approved design.

- [ ] **Step 8: Record the closure result in one repository checkpoint**

Create `docs/internal/checkpoints/content-state-contract-v1-closure.md` with this exact structure and replace each status from the command output just executed:

```markdown
# Content State Contract v1 — Closure

- Source revision: `<git rev-parse --short HEAD before closure-doc commit>`
- Spec: `docs/superpowers/specs/2026-08-27-content-state-contract-v1-design.md`
- Plan: `docs/superpowers/plans/2026-08-27-content-state-contract-v1-implementation-plan.md`

## Verification

| Gate | Status | Evidence |
| --- | --- | --- |
| Focused CSC/ViewModel suite | PASS / FAIL | command + final Gradle result |
| Full `:feature:catalog` unit suite | PASS / FAIL | command + final Gradle result |
| `:app:testDebugUnitTest` | PASS / FAIL | command + final Gradle result |
| Build-logic + architecture scripts | PASS / FAIL | commands + final results |
| Catalog/Reader compile+regression | PASS / FAIL | command + final Gradle result |
| Connected Catalog UI tests | PASS / ENVIRONMENT_BLOCKED / FAIL | command or exact environment blocker |
| `git diff --check` | PASS / FAIL | command result |

## Scope confirmation

- CSC foundation remains feature-local: YES / NO
- Reader production changes required by CSC: NO / YES (YES blocks closure)
- WorkManager/cache/domain lifetime ownership moved into CSC: NO / YES (YES blocks closure)
- Known unresolved correctness issue: NONE / describe and leave phase open
```

Do not write PASS from expectation; copy only fresh evidence from Steps 1–7. A failed non-environment gate leaves CSC-v1 open and routes back to its owning task.

- [ ] **Step 9: Commit the verified closure checkpoint**

Only when all non-environment verification rows are PASS and scope confirmations are clean:

```bash
git add docs/internal/checkpoints/content-state-contract-v1-closure.md
git commit -m "docs(catalog): record CSC v1 closure"
```

---

## Task 12: UX-R6 — Audit accepted adjacent migrations and Reader compatibility

**Files:**
- Create `docs/internal/checkpoints/content-state-contract-v1-r6-audit.md`
- Read only for post-migration/compatibility audit; no additional production migration is owned here:
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/`
  - `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/`
  - `feature/reader/`

**Goal:** confirm the approved Search/Mapping/Reconciliation readiness migrations keep their specialized command semantics local, verify Reader remains outside CSC, and decide whether CSC has earned a separate cross-feature promotion proposal.

- [ ] **Step 1: Audit Search**

Start with:

```bash
rg -n 'loading|refresh|failure|catch|stateIn|combine|search|query' \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search
```

Record the accepted CSC presentation boundary:

- what constitutes query Pending vs empty search result;
- whether partial provider failures retain usable search content;
- whether query identity changes correctly invalidate retained results;
- which query/filter/selection semantics remain specialized and must not move into a generic CSC reducer.

- [ ] **Step 2: Audit Mapping**

Start with:

```bash
rg -n 'SEARCHING|mapping|loading|failure|WorkManager|observe|stateIn' \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library \
  app/src/main/kotlin/app/openstory/work
```

Record:

- mapping observation readiness vs actual background mapping search lifecycle;
- whether `SEARCHING` has a truthful domain signal anywhere outside Library;
- whether a future domain mapping-status port is justified independently of CSC.

Do not create that port in UX-R6.

- [ ] **Step 3: Audit Reconciliation**

Start with:

```bash
rg -n 'reconcil|resolving|failure|loading|retry|stateIn|combine' \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story
```

Record blocking/partial state semantics and confirm reconciliation command lifecycle remains materially different from CSC content observation.

- [ ] **Step 4: Audit Reader only for principle compatibility**

Start with:

```bash
rg -n 'committed|transition|route|prefetch|loading|failure|retry|StateFlow' \
  feature/reader/src/main reader/src/main reader/engine/src/main
```

Explicitly verify Reader HES-v1 has committed-document/session/route semantics that should remain outside generic CSC. Record only these reusable principles: retain the committed usable document during recovery, distinguish content retry from prefetch/background activity, and scope retained state to route/session identity. Do not import Catalog CSC types into Reader.

- [ ] **Step 5: Make the promotion decision**

The checkpoint must end with one of these exact outcomes:

```text
KEEP_FEATURE_LOCAL
```

when no second feature proves identical reusable types, or:

```text
PROPOSE_CROSS_FEATURE_SPEC
```

only when another feature demonstrably needs the same types/semantics. `PROPOSE_CROSS_FEATURE_SPEC` means write a new design later; it does not authorize moving CSC in this task.

- [ ] **Step 6: Commit audit only**

```bash
git add docs/internal/checkpoints/content-state-contract-v1-r6-audit.md
git commit -m "docs(catalog): audit CSC adjacent state semantics"
```

---

## Per-task review gates

Every task above must pass these checks before the next task starts:

1. **Spec gate:** identify the exact CSC decisions/invariants implemented by the task; no unrelated refactor.
2. **TDD gate:** new transition behavior has a failing test before production wiring and passes afterward.
3. **Ownership gate:** no cache/sync/background/Reader ownership moved into CSC.
4. **Failure gate:** cancellation rethrows; blocking/non-blocking failure lifetime is explicit; Retry targets the correct boundary.
5. **Identity gate:** any dynamic Flow has a named readiness key; reducers normalize dependent states with `forExpectedKey(currentKey)` so old-key values cannot satisfy new-key readiness even during scheduling races.
6. **UX gate:** usable content never blanks during manual refresh/background update/optional enrichment; unresolved local regions preserve layout with neutral/skeleton presentation and never emit a negative CTA/badge before the owning fact is authoritative.
7. **False-fact gate:** unresolved input never becomes authoritative empty/zero/false.
8. **Compile gate:** focused task tests and affected call-site tests pass before commit.
9. **Reuse gate:** before creating any new shared helper, prove that at least two migrated screens have identical domain-neutral semantics; otherwise keep it local. Reject helpers that hide required/enrichment classification behind generic lambdas.
10. **Diff gate:** review `git diff --check` plus changed file list before commit.

## Final expected commit sequence

A clean execution should normally produce commits close to:

```text
feat(catalog): add CSC presentation state contract
refactor(catalog): migrate downloads to CSC state
refactor(catalog): migrate updates to keyed CSC readiness
refactor(catalog): add truthful library local readiness
refactor(catalog): make home progressively ready
refactor(catalog): separate chapter readiness and capability
refactor(catalog): make discover canonical settlement terminal
refactor(catalog): migrate discover bootstrap and refresh state
refactor(catalog): separate story readiness from refresh
test(catalog): freeze CSC presentation boundaries
docs(catalog): record CSC v1 closure
docs(catalog): audit CSC adjacent state semantics
```

Do not squash these boundaries during implementation unless the user explicitly requests a different commit strategy; each boundary corresponds to an independently reviewable readiness risk.

## Definition of done

CSC-v1 is complete only when all spec acceptance criteria are represented by automated tests or an explicit final audit, including:

- migrated screens have no independent `loading:Boolean` content authority;
- Pending and authoritative Ready(empty) are independently observable;
- first required observation failure cannot become synthetic empty;
- Downloads renders before Chapter/Catalog enrichment;
- Updates empty-Library short-circuit works and Story-ID key changes do not leak retained values;
- Library mapping Pending is UNKNOWN, not NO_MAPPING/SEARCHING, and control-sensitive filters/sorts never claim false results;
- Home base content is ready from Library membership, download count can be unknown, and all-DROPPED Library never receives the no-Library CTA;
- Chapters render before Reader capability, capability `UNKNOWN` is not authoritative unsupported, and Story hero shows “Find source” for authoritative empty Chapters or for non-empty Chapters that have release targets, fully resolved Reader capability, and no readable target; failed/pending Chapters and chapter groups with no releases use truthful neutral hero states;
- Discover empty-cache bootstrap is not manual Refreshing, durable existing projections are pre-seeded before unresolved bootstrap, canonical attempts are terminal, ranked slots are stable, and no-provider/true-empty/failure are distinguishable;
- Story Preparing has an explicit terminal path and content Retry is distinct from source-detail refresh;
- refresh attempt failures clear only within RefreshState lifetime;
- observation issues are dependency/key scoped and recover independently;
- same-key resubscription does not blank Ready content;
- required observations that complete before their first value leave Pending deterministically, while one-shot observations that emit then complete remain Ready;
- no new CSC/global state engine/module exists;
- active Chapter refresh docs match runtime;
- Reader HES-v1 production code remains unchanged;
- `:feature:catalog:testDebugUnitTest`, app navigation tests, architecture/package gates, and available connected UI tests pass.


## Spec-to-plan traceability matrix

This matrix is a required execution aid, not documentation decoration. Before a task is accepted, its reviewer must verify the referenced decision/invariant/gap rows still match the implementation diff. If an executor discovers a spec requirement that does not map cleanly to one of these tasks, stop and amend the plan/spec rather than hiding the change inside a neighboring task.

### Architecture decisions

| Spec decision | Primary implementation/review task(s) |
| --- | --- |
| DECISION-CSC-001 — contract, not engine | Task 1 foundation; Task 10 freeze; Task 11 ownership gates |
| DECISION-CSC-002 — feature-local first implementation | Tasks 1–10; Task 10 architecture test |
| DECISION-CSC-003 — Pending is not Empty | Task 1 contract tests; Tasks 2–9 screen transitions |
| DECISION-CSC-004 — minimal blocking failure value | Task 1 `CatalogUiFailure`; Tasks 2–9 failure mapping |
| DECISION-CSC-005 — refresh orthogonal to content | Tasks 6, 8, 9; Task 11 regression gate |
| DECISION-CSC-006 — bootstrap is not refresh | Tasks 8 and 9 |
| DECISION-CSC-007 — background work is not refresh | Tasks 2–9 reducers; Task 11 ownership gate |
| DECISION-CSC-008 — domain owns freshness/cache usability | Tasks 2–9 boundaries; Task 11 architecture verification |
| DECISION-CSC-009 — required vs enrichment explicit | Tasks 2–9, especially Tasks 3–6 |
| DECISION-CSC-010 — no synthetic negative facts | Task 1 retained observation; Tasks 2–9 projections |
| DECISION-CSC-011 — keyed/restartable retained observation | Task 1; dynamic-key consumers Tasks 3, 5, 8, 9 |
| DECISION-CSC-012 — subsection readiness may differ | Tasks 4, 5, 6, 8, 9 |
| DECISION-CSC-013 — failure channels scoped/keyed | Task 1; Tasks 2–9 failure reducers |
| DECISION-CSC-014 — resubscription does not blank content | Task 1 holder tests; Tasks 2–9 retention behavior |
| DECISION-CSC-015 — Reader excluded | Task 10 architecture test; Tasks 11–12 guard/audit |
| DECISION-CSC-016 — no universal freshness/activity machine | Task 1 reuse budget; Task 10 freeze |
| DECISION-CSC-017 — content Retry is not refresh | Tasks 2–9 Retry methods |
| DECISION-CSC-018 — Pending has named exit | Task 1 finite-observation terminal test; Tasks 4, 7, 8, 9 per-task review gate |
| DECISION-CSC-019 — partial Ready preserves order | Tasks 7 and 8 Discover settlement/projection |
| DECISION-CSC-020 — refresh failure is attempt-scoped | Task 1 `RefreshState`; Tasks 6, 8, 9 |
| DECISION-CSC-021 — blocking summary may aggregate, Retry stays truthful | Tasks 3, 4, 7, 8, 9 |
| DECISION-CSC-022 — no-content reason belongs to Ready payload | Tasks 5 and 8; Library filtered-empty remains local collection truth |

### State-transition invariants

| Spec invariant | Primary verification task(s) |
| --- | --- |
| CSC-I01 — First snapshot | Task 1; first-snapshot tests in Tasks 2–9 |
| CSC-I02 — First required failure | Tasks 2–9 blocking-failure tests |
| CSC-I03 — Failure after usable content | Task 1 retention; Tasks 2–9 issue tests |
| CSC-I04 — Manual refresh retention | Tasks 6, 8, 9 |
| CSC-I05 — Background update | Tasks 2, 3, 5, 6, 8; full suite Task 11 |
| CSC-I06 — Enrichment does not block base content | Tasks 2, 3, 4, 5, 6, 9 |
| CSC-I07 — No false negative defaults | Tasks 2–9; especially Library/Chapter/Story hero |
| CSC-I08 — Authoritative empty requires readiness | Tasks 2–9 empty-state tests |
| CSC-I09 — Resubscription retention | Task 1 holder test; Task 11 combined regression |
| CSC-I10 — Failure channels stay scoped | Tasks 2, 4, 6, 8, 9 |
| CSC-I11 — Ready→Pending is exceptional | Tasks 3, 5, 8, 9 identity/convergence tests |
| CSC-I12 — Bootstrap and refresh distinct | Tasks 8 and 9 |
| CSC-I13 — Retention is key-scoped | Task 1; Tasks 3, 5, 8, 9 |
| CSC-I14 — Pending has named exit | Task 1 finite-observation terminal test; Tasks 4, 7, 8, 9 |
| CSC-I15 — Partial projection order stable | Tasks 7 and 8 |
| CSC-I16 — Retry targets failed content boundary | Tasks 2–9 |
| CSC-I17 — Refresh failure attempt-scoped | Task 1; Tasks 6, 8, 9 |
| CSC-I18 — Blocking retryability truthful | Tasks 2–9, especially Discover/Story terminal outcomes |
| CSC-I19 — No-content reason distinguishable | Tasks 5 and 8; Task 4 filtered-empty semantics |

### Current-code gap closure

| Gap | Owning task(s) | Closure evidence required |
| --- | --- | --- |
| GAP-CSC-001 | Tasks 1, 10 | one retained-observation primitive; no migrated `preserveLatest*` helper remains |
| GAP-CSC-002 | Task 2 | Download records reach Ready before Chapter/Catalog emissions |
| GAP-CSC-003 | Task 3 | Catalog/Reader are nullable enrichment; Chapters+Mappings own update membership |
| GAP-CSC-004 | Tasks 2, 3 | blocking failure without content; inline retryable issue with retained content |
| GAP-CSC-005 | Task 4 | unresolved mapping renders UNKNOWN/local Resolving, never false NO_MAPPING/SEARCHING |
| GAP-CSC-006 | Task 4 | Library membership first failure becomes blocking Failed |
| GAP-CSC-007 | Task 5 | Library makes Home Ready; section inputs enrich progressively |
| GAP-CSC-008 | Task 8 | empty-cache bootstrap is Pending with `refresh.inProgress == false` |
| GAP-CSC-009 | Tasks 7, 8 | canonical unresolved slots cannot become authoritative empty |
| GAP-CSC-010 | Task 7 | per-Story terminal settlement/report retains failure detail |
| GAP-CSC-011 | Task 9 | Preparing has explicit in-flight and terminal branches |
| GAP-CSC-012 | Task 6 | first Chapter observation failure is Failed, not empty |
| GAP-CSC-013 | Task 6 | Chapters render before Reader capability lookup |
| GAP-CSC-014 | Task 6 | observation / refresh / correction channels separated |
| GAP-CSC-015 | Task 10 | active design-system doc matches current Chapter refresh runtime |
| GAP-CSC-016 | Tasks 1, 10 | CSC remains feature-local; design system receives no feature state machine |
| GAP-CSC-017 | Task 1 | same-key retry reconstructs upstream collection; no assumption that `catch` resumes |
| GAP-CSC-018 | Tasks 1, 3, 5, 8, 9 | key changes invalidate/normalize stale values and issues before reducer use |
| GAP-CSC-019 | Tasks 7, 9 | completed `ensureReady()` returning Preparing becomes terminal, not permanent Pending |
| GAP-CSC-020 | Tasks 4, 12 | no fake mapping SEARCHING; R6 records whether a real domain lifecycle port is warranted |
| GAP-CSC-021 | Task 4 | Catalog/Progress/Mapping become local-required only under controls that need them |
| GAP-CSC-022 | Task 5 | all-DROPPED/non-empty Library never receives NO_LIBRARY copy/CTA |
| GAP-CSC-023 | Tasks 2–9 | underlying issues remain per dependency/key; UI chooses deterministic summary only |
| GAP-CSC-024 | Tasks 8, 9 | content Retry, observation Retry, refresh, and command operations remain distinct |
| GAP-CSC-025 | Tasks 3, 5, 6 | unresolved Reader capability is nullable/UNKNOWN; no unsupported claim before success |
| GAP-CSC-026 | Tasks 7, 8 | stable-prefix tests prevent lower-ranked temporary promotion |
| GAP-CSC-027 | Task 1 plus Tasks 6, 8, 9 | every new refresh attempt clears only its previous refresh failure |

---

## Plan self-review result

### Scope and reuse review

- The plan creates only the four approved shared CSC production files plus one Discover-local settlement file. No new module, process singleton, repository coordinator, WorkManager abstraction, cache owner, or Reader dependency is introduced.
- Reuse is concentrated in semantic mechanics (`ContentState`, `RefreshState`, failure value, keyed retained observation) and in the already-shared `LibraryActivityProjector`. Screen truth remains local where semantics differ.
- Story hero read-state modeling is intentionally Story-local; it is not promoted to CSC because Chapter-empty/Chapter-failed/Reader-source semantics are not generic content-state semantics.
- Discover stable-prefix settlement remains Discover-local because ranked canonical convergence is not shared by other migrated screens.

### Identity/concurrency review

- Dynamic dependent observations use explicit keys and reducers normalize `ObservationState` against the current expected key before projection, issue selection, retryability, or Retry targeting.
- Story uses **two distinct identities**: route StoryId for canonical observation/bootstrap/commands, resolved canonical StoryId for Library/Progress enrichment. The plan forbids normalizing route-keyed canonical state against the resolved ID.
- Same-key `WhileSubscribed` restart retains usable Available content; key change invalidates old content without requiring the dependent holder to win a scheduling race.
- Retained observation retry never creates automatic retry/backoff behavior and never swallows cancellation/JVM fatal errors.

### UX truthfulness review

- No migrated screen uses unresolved optional data to assert empty, zero, unsupported, no mapping, no source, or no Library.
- Full-screen blocking state is limited to unavailable **required** screen content. Local-required dependencies use local skeleton/error presentation where the screen base remains usable.
- Story hero differentiates Chapters Pending, Chapters Failed, authoritative no Chapters, non-empty chapter groups with no releases, Reader capability Pending, real readable target, and authoritative no readable source; authoritative no Chapters and resolved no-readable-source may show `Find source`.
- Manual refresh retains usable content and has an attempt-scoped failure lifetime. Automatic bootstrap/background observation never turns on pull-refresh chrome.
- Discover uses durable pre-seeding and stable-prefix projection to reduce perceived loading without temporary hero/rank promotion.

### Migration-safety review

- Each task has a focused RED→GREEN test gate and a commit boundary before the next screen migration.
- Discover canonical settlement is verified before Discover ViewModel migration; shared projector changes land with Updates before Home consumes them.
- Task 11 owns only a closure checkpoint document. Any source/test failure routes back to the owning task so closure cannot conceal a production fix.
- Reader migration remains outside UX-R0–UX-R5; the approved Search/Mapping/Reconciliation presentation migrations are frozen in Task 10, and R6 records whether a separate cross-feature proposal is justified.

### Mechanical review requirements before execution

The checked-in plan is considered ready for execution only when all of these scans pass on the exact plan revision:

```bash
# no placeholder language that delegates an undefined implementation decision
rg -n -i '\b(T[B]D|T[O]DO|implement[[:space:]]+later|fill[[:space:]]+in|what[e]ver|similar[[:space:]]+to|as[[:space:]]+needed|if[[:space:]]+needed)\b' \
  docs/superpowers/plans/2026-08-27-content-state-contract-v1-implementation-plan.md

# every existing-path reference in Files sections resolves; Create paths are absent before implementation
# (run the repository-local plan verification helper/check used when packaging this plan)

# patch/docs hygiene
git diff --check
```

Any non-empty placeholder result, missing Modify/Test path, already-existing Create path, or `git diff --check` error blocks execution until the plan is corrected.
