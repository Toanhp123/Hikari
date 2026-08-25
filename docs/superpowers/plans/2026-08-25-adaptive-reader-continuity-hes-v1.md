# Adaptive Reader Continuity and HES-v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Hikari's Wave-10-rebased Adaptive Reader Continuity engine and HES-v1 reference architecture with deterministic access-aware routing, session-scoped execution, process-shared Reader health, reactive chapter facts, zero-blank transitions, bounded prefetch/hedging, and build-enforced pure-engine boundaries without breaking Wave 10 ownership or moving Room beyond schema 11.

**Architecture:** Add a pure JVM `:reader:engine` that accepts immutable Reader routing facts/policy and returns deterministic route decisions. Keep chapter-model mapping, cache/network/source-availability facts, health registry mutation, scheduling, source execution, session state, document validation, prefetch, hedging, and UI transitions in `:reader` / `:feature:reader` / app adapters; downstream modules never use engine types directly. Execute the R2 design in `R0 -> M0 -> M7` order, with Wave 10 acceptance/governance repair before the seventeenth production module is allowed to become the active baseline.

**Tech Stack:** Kotlin 2.4.10, JDK 17, Gradle convention plugins, Kotlin/JVM for `:reader:engine`, Android library + kotlinx.coroutines in `:reader`, Hilt in `:app`, Room 2.8.4 at schema 11, kotlin-test/JUnit, kotlinx-coroutines-test, existing Android/Robolectric/Compose/instrumentation test stacks, repository shell policy scripts.

**Spec:** `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md` — R2 / Wave 10 production-remediation baseline.

**Implementation status (2026-08-25):** **M0–M2 VERIFIED/CLOSED; M3 NEXT.** M1 Tasks 6–8 and M2 Tasks 9–11 are implemented and verified on the developer host. Evidence is recorded in `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m1-m2.md`. The per-task Step 5 commit commands for Tasks 6–11 are intentionally not executed individually; the repository owner requested one combined M1+M2 commit after this closeout patch. Wave 10 final acceptance remains open under the existing acceptance-rebase.

## Global Constraints

- The entry baseline is the supplied Wave 10 production-remediation tree: **16 production modules plus `:benchmark`, Room schema 11, `MIGRATION_10_11` present, Wave 10 final acceptance still open**.
- `R0` is mandatory: either close Wave 10 on the existing 16-module/schema-11 boundary before HES source work, or explicitly rebase Wave 10 acceptance and rerun its matrix on the HES-containing tree. Never reuse `NOT RUN` evidence as satisfied.
- HES adds exactly one production module: `:reader:engine` at `reader/engine`. The post-HES graph is **17 production modules plus `:benchmark`**, still Room schema 11.
- `:reader:engine` uses `id("openstory.kotlin.jvm")` and has exact production project dependencies `{":core:common"}`.
- Engine production source must not import Android/AndroidX, chapters, Reader effect packages, plugins, downloads/storage, coroutines, serialization, `java.io.*`, or `java.net.*`.
- `:reader` consumes `:reader:engine` with `implementation(project(":reader:engine"))`, never `api`. No downstream module adds a direct engine dependency and no effect port returns engine DTOs.
- `:reader` keeps its existing production dependencies and adds only `:reader:engine`; no `:reader -> :settings` edge is allowed.
- Wave 10 remains owner of `ReaderPreferencesPort`, `languageOrder`, font-scale persistence/rollback, auth/background/notifications, schema 11, and `MIGRATION_10_11`.
- Initial Reader routing waits for both the first persisted `ReaderPreferences` emission and the first session-local `ChapterRepository.observe(storyId)` emission.
- Current `ReaderSourceAvailability` is the only source-enabled Reader availability port; do not create a second equivalent abstraction.
- Production candidate mapping sets `sourceGroupKey = null` and `completeness = BasisPoints(10_000)` until real trusted facts exist.
- All scoring uses integer `BasisPoints(0..10_000)` and `Long` weighted intermediates; the full weighted sum divides once by `10_000`.
- Pure routing is deterministic for identical snapshot + policy + algorithm, independent of candidate/map/set input iteration order.
- Candidate/access eligibility precedes explicit preference, scoring, incumbent arbitration, route construction, and hedge construction.
- Semantic release identity and access mode are distinct. Every planned `LOCAL` attempt must carry one non-blank fingerprint locator; `REMOTE` attempts carry none.
- `ReadingProgress.contentFingerprint` is an exact local/restoration identity, not a provider-side expected fingerprint for future remote content.
- A valid remote document with a changed fingerprint may commit; exact saved block/offset/fraction restoration is applied only when release ID **and** fingerprint match persisted progress.
- Cache metadata inspection is bounded and metadata-only; it must not decode every candidate `ReaderDocument` just to rank cache state.
- With a resume fingerprint, unrelated stored fingerprints are a `MISS` for that exact locator, not corruption. `KNOWN_INVALID` requires an actual read/decode/validation observation.
- Without a resume fingerprint, local locator selection is deterministic: newest explicit-download row for the release if `COMPLETED`, otherwise automatic-cache `lastAccessedAtEpochMillis DESC`, fingerprint ASC.
- Reader source health is process-lifetime, in-memory, operation-specific to `READ_DOCUMENT`, bounded to 20 remote-success latency samples, and reset to neutral on process restart.
- Wave 10 auth/credential/configuration failures are non-penalizing Reader health observations by default.
- A normal remote success that arrives after the circuit has become OPEN cannot close/reset the circuit; only a successful held HALF_OPEN probe can close the OPEN/HALF_OPEN cycle.
- Reader process-wide execution limits are separate from per-screen sessions: max one active Reader REMOTE attempt per `sourceId`, max two concurrent foreground REMOTE attempts, max four total foreground REMOTE attempts, max one process-wide remote prefetch attempt.
- Every foreground user intent gets a new `ReaderGenerationId`; `ReaderPlanRevision` changes only for hard external invalidation of that same active uncommitted generation.
- Every runtime attempt/result identity contains `(sessionId, generationId, planRevision, attemptId, targetChapterId)`; no second revision/hash identity is introduced.
- One generation commits at most one visible semantic document. Cancellation is best effort; stale session/generation/revision validation is the correctness barrier.
- Current committed content owns saved state and reading progress until an atomic successful replacement commit. Starting chapter/release transitions must not overwrite committed saved keys.
- One Reader screen owns one chapter-graph observation; do not call full `snapshot()` for every navigation and do not freeze one graph for the entire ViewModel lifetime.
- Prefetch uses the same engine with `PREFETCH`, targets only N+1, has no default hedge, and never becomes authoritative solely because it completed.
- A non-persistable image prefetch may exercise source health but must not be surfaced later as a reusable local cache fact.
- Keep current `ReaderDocument`, `ReaderDocumentSource`, `ReaderDocumentSourceRegistry`, `ReaderSourceAvailability`, `ReaderDocumentStore`, reading-progress contracts, download storage behavior, cancellation propagation, and fingerprint-addressed quarantine semantics until explicit cleanup.
- Local storage I/O failure is not automatically corruption. Quarantine only a confirmed bad `(releaseId, fingerprint)`; non-cancellation quarantine/cache-write failures are diagnostic/best-effort and must not block remote semantic success.
- HES v1 introduces no Room entity/schema/version change and must not create `MIGRATION_11_12`.
- Do not wire/repair the unrelated cache-quota setting as part of HES.
- Do not introduce a generic `BaseEngine`, engine registry, service locator, reflection discovery, or cross-engine runtime framework.
- Follow TDD for every production behavior: focused failing test -> observe intended RED -> smallest implementation -> focused GREEN -> regression GREEN.
- Commit commands are logical checkpoints. The supplied ZIP has no `.git`; archive execution may skip only the commit command while preserving the task/diff/test boundary.

---

## Locked File Structure

### New pure module `:reader:engine`

- `reader/engine/build.gradle.kts` — JVM build; production dependency only `:core:common`.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingValues.kt` — basis points, versions, intent/access/network/value identities, language normalization.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingPolicy.kt` — validated weights, language, hysteresis, route/hedge budgets.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingFacts.kt` — immutable candidate/local/remote/continuity/health/snapshot facts.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt` — attempts, competitive set, recovery chain, rejections, reasons, trace, confidence.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt` — health state, typed observations, reducer contract/policy.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteEngine.kt` — pure public planner and `v1()` factory.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/EligibilityEvaluator.kt` — local/remote/candidate hard eligibility.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateEvaluator.kt` — semantic + preferred-access feature normalization.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateRanker.kt` — fixed-point weighted/stable ranking.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/ContinuityArbiter.kt` — incumbent resolution/hysteresis.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/RoutePlanner.kt` — access attempts, budgets, confidence, hedge directive.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt` — staged pure orchestration only.
- `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultSourceHealthReducer.kt` — bounded EWMA/circuit reducer.

### New/changed Reader effect boundary

- `reader/src/main/kotlin/app/openstory/reader/routing/LegacyReaderRoutingAdapter.kt` — legacy facts -> engine facts for migration tests; no engine type leakage outside `:reader`.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt` — per-screen generation/plan/commit/graph state.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionFactory.kt` — creates independent sessions.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionState.kt` — semantic runtime execution states and attempt identity.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt` — snapshot/plan/execute/replan/commit orchestration.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt` — local/remote sequential and later competitive execution.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderDocumentValidatorAdapter.kt` — materialized document validation before semantic success.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderAttemptFailure.kt` — typed attempt failure + recovery scope.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceFailureClassifier.kt` — exhaustive current runtime string-code table.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceHealthRegistry.kt` — process-shared in-memory health.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt` — per-source Reader remote lane + HALF_OPEN leases + prefetch priority.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderCacheFactsPort.kt` — Reader-owned cache metadata DTO/port, no engine types.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderNetworkFactsPort.kt` — Reader-owned network DTO/port, no Android/engine types across app boundary.
- `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt` — maps Reader/domain/effect DTOs to engine snapshots.
- `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionScheduler.kt` — hedge delay + monotonic execution clock.
- `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt` — completion-time winner facts independent of notification order.
- `reader/src/main/kotlin/app/openstory/reader/routing/PrefetchCoordinator.kt` — bounded N+1 Reader prefetch.

### Downloads / Room / App adapters

- `downloads/src/main/kotlin/app/openstory/downloads/reader/ReaderCacheMetadataSource.kt` — narrow metadata SPI with no engine types.
- `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt` — existing store plus `ReaderCacheFactsPort` implementation.
- `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt` — one bounded `IN (:releaseIds)` metadata query.
- `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt` — implements Reader metadata SPI; schema unchanged.
- `app/src/main/kotlin/app/openstory/reader/AndroidReaderNetworkFactsPort.kt` — ConnectivityManager adapter.
- `app/src/main/kotlin/app/openstory/di/ReaderModule.kt` — HES engine/coordinator/registry/session/network/store composition.
- `app/src/main/kotlin/app/openstory/di/DownloadModule.kt` — bind `RoomDownloadRepository` as Reader cache metadata source.
- `app/src/main/AndroidManifest.xml` — `ACCESS_NETWORK_STATE` only.

### Existing integration surfaces deliberately preserved/migrated

- `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt`
- `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentSource.kt`
- `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt`
- `reader/src/main/kotlin/app/openstory/reader/content/PluginReaderDocumentSource.kt`
- `reader/src/main/kotlin/app/openstory/reader/preferences/ReaderPreferencesPort.kt`
- `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelector.kt`
- `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelectionPolicy.kt`
- `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelectionResult.kt`
- `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`
- `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`
- `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt` — consumed, not modified for HES.
- `config/architecture/module-boundaries.json`
- `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- `scripts/verify-package-boundaries.sh`
- `scripts/tests/performance-lifecycle-policy-test.sh`

## Milestone Order

```text
R0 Wave 10 governance + acceptance boundary
  -> M0 HES module/constitutional guardrails
  -> M1 legacy-compatible pure reasoner
  -> M2 session/coordinator compatibility boundary
  -> M3 typed observations + validation + process health
  -> M4 adaptive routing + bounded cache/network/reactive graph facts + replan
  -> M5 committed-vs-target UI continuity + N+1 prefetch
  -> M6 one foreground hedge + deterministic competitive execution
  -> M7 verification, stress, cleanup, HES-v1 freeze
```

Do not activate behavior from a later milestone until the prior checkpoint's focused and repository regressions are green.

---

# R0 — Wave 10 Boundary Repair and Acceptance

### Task 1: Repair stale Wave 10 governance and static baseline assertions before HES changes the graph

**Files:**
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `scripts/tests/verify-current-architecture-test.sh`
- Modify: `scripts/tests/source-hygiene-policy-test.sh`
- Modify: `scripts/tests/performance-wave-p4-policy-test.sh`
- Test: existing shell policy scripts above.

**Interfaces:**
- Produces: truthful pre-HES boundary = 16 production modules + `:benchmark`, schema 11, Wave 10 implementation present/final acceptance open.
- Preserves: Wave 10 checkpoint authority; this task does **not** mark Wave 10 accepted.
- Preserves: current Settings route and current intentional `OpenStoryApplication.onCreate()` Wave 10 work without turning static policy into a no-op.

- [ ] **Step 1: Write/adjust failing static assertions to encode the actual Wave 10 tree**

In `verify-current-architecture-test.sh`, change the expected production count to `16` and include `:feature:settings` in the exact app dependency fixture. Keep the mutation tests that prove unapproved modules/dependencies still fail.

In `source-hygiene-policy-test.sh`, replace the obsolete prohibition on `AppRoute.Settings` with a positive current-route assertion and continue forbidding only genuinely retired/future placeholders. At minimum:

```bash
grep -q 'data object Settings : AppRoute' "$app_route" || fail "Wave 10 Settings route is missing"
! grep -q 'data object Plugins : AppRoute' "$app_route" || fail "future-only Plugins route placeholder remains"
```

In `performance-wave-p4-policy-test.sh`, replace the blanket `Application.onCreate` ban with exact bounded Wave 10 startup ownership checks. The policy must require the current three intended actions and reject unrelated broad startup work rather than merely allowing any `onCreate` body:

```bash
grep -q 'NotificationChannelConfig.create(this)' "$application" || fail "notification channel startup hook missing"
grep -q 'backgroundPolicyCoordinator.start()' "$application" || fail "background policy startup hook missing"
grep -q 'notificationDrainScheduler.ensureRecoveryWork()' "$application" || fail "notification recovery startup hook missing"
! grep -Eq 'runBlocking|Thread[.]sleep|database[.]|snapshot\(' "$application" || fail "blocking/heavy startup work introduced"
```

- [ ] **Step 2: Run the repaired policy tests and verify the old literals fail before the source-policy fix, then GREEN after the fix**

```bash
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/tests/source-hygiene-policy-test.sh
bash scripts/tests/performance-wave-p4-policy-test.sh
```

Expected final result: all three PASS on the unchanged Wave 10 production source.

- [ ] **Step 3: Rebase `current-state.md` and roadmap prose to source reality without closing acceptance**

Record exactly:

```text
production modules = 16
:benchmark = android-test/performance module
Room current schema = 11
MIGRATION_10_11 = Wave 10 notification persistence owner
Wave 10 = IMPLEMENTATION PRESENT; FINAL ACCEPTANCE OPEN
```

Do not describe HES as implemented and do not advance Wave 11.

- [ ] **Step 4: Run the repository structural/current-architecture policies**

```bash
bash scripts/verify-current-architecture.sh
bash scripts/check-wave-10-production-policy.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/tests/source-hygiene-policy-test.sh
bash scripts/tests/performance-wave-p4-policy-test.sh
```

- [ ] **Step 5: Record checkpoint**

```bash
git add docs/project/current-state.md docs/implementation/current-roadmap.md scripts/tests
 git commit -m "docs(wave10): rebase governance and static policy to implementation"
```

### Task 2: Close Wave 10 acceptance on the 16-module boundary, or explicitly record the HES acceptance rebase

**Files:**
- Modify only after evidence: `docs/internal/checkpoints/wave-10-production-remediation.md`
- Modify only after evidence: `docs/implementation/current-roadmap.md`

**Interfaces:**
- Produces one of two explicit states:
  - preferred: Wave 10 accepted/closed on the **pre-HES 16-module/schema-11 SHA**;
  - fallback: checkpoint remains open and explicitly states Wave 10 acceptance will be rerun on the HES-containing tree.
- Blocks: Task 3 until one of those states is written; no invisible boundary crossing.

- [ ] **Step 1: Run the complete host acceptance command from the Wave 10 checkpoint**

```bash
bash scripts/check-wave-10-production-policy.sh
./gradlew verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
```

Expected: zero failures. If Gradle dependencies/devices are unavailable, record `NOT RUN`/environment blocker exactly; do not claim acceptance.

- [ ] **Step 2: Run required API 26 and API 37 device matrices when the normal project verification environment provides them**

Use the exact Wave 10 suites/checkpoint commands for:

```text
10 -> 11 migration + notification claim/recovery
Keystore session store + guarded WebView auth
notification delivery/permission/channel/deep-link/navigation
Discover/Home/Library/Reader/Downloads regressions
```

Record device IDs, API levels, counts, failures, fixes, and reruns. API 35 focused evidence does not substitute for these gates.

- [ ] **Step 3: Make the entry decision from evidence, not intent**

If host + API 26 + API 37 are green, mark the Wave 10 checkpoint `ACCEPTED/CLOSED`, record the exact accepted SHA, and freeze `16 production modules / schema 11` as the HES entry boundary.

If any required gate cannot run/pass, leave Wave 10 open and add an explicit governance note:

```text
HES implementation is a deliberate acceptance rebase.
Wave 10 final acceptance must be rerun on the HES-containing tree.
Previous NOT RUN entries remain unsatisfied.
```

- [ ] **Step 4: Re-run current architecture/current-state consistency checks after checkpoint update**

```bash
bash scripts/verify-current-architecture.sh
bash scripts/tests/verify-current-architecture-test.sh
rg -n 'Wave 10|schema 11|16 production' docs/project/current-state.md docs/implementation/current-roadmap.md docs/internal/checkpoints/wave-10-production-remediation.md
```

- [ ] **Step 5: Record checkpoint**

```bash
git add docs/internal/checkpoints/wave-10-production-remediation.md docs/implementation/current-roadmap.md
 git commit -m "docs(wave10): record HES entry acceptance boundary"
```

# M0 — HES Constitutional Guardrails

### Task 3: Add the pure `:reader:engine` module and close both Gradle and shell source-boundary holes

**Files:**
- Modify: `settings.gradle.kts`
- Create: `reader/engine/build.gradle.kts`
- Modify: `reader/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `scripts/tests/verify-current-architecture-test.sh`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Create or modify test: `scripts/tests/verify-package-boundaries-test.sh` if the repository already owns this test entry; otherwise extend the nearest existing package-boundary policy test.

**Interfaces:**
- Produces module `:reader:engine`, path `reader/engine`, platform `jvm`.
- Produces exact engine production dependency `{":core:common"}`.
- Produces `:reader --implementation--> :reader:engine`; no API re-export.
- Produces post-HES architecture count 17 production modules + `:benchmark`, schema remains 11.

- [ ] **Step 1: Write failing constitutional tests before registering the module**

Add a `ModuleGraphTest.readerEngineIsConstitutionallyPureJvm()` assertion equivalent to:

```kotlin
@Test
fun readerEngineIsConstitutionallyPureJvm() {
    val policy = ModuleBoundaryPolicyLoader.load(File("../config/architecture/module-boundaries.json"))
    val rule = policy.modules.getValue(":reader:engine")
    assertEquals("jvm", rule.platform.policyValue)
    assertEquals("exact", rule.dependencyMode.policyValue)
    assertEquals(setOf(":core:common"), rule.productionDependencies)

    val build = File("../reader/engine/build.gradle.kts").readText()
    assertTrue("id(\"openstory.kotlin.jvm\")" in build)
    assertFalse("openstory.android" in build)
    assertFalse("openstory.compose" in build)
    assertFalse("openstory.hilt" in build)
    assertFalse("openstory.room" in build)
    assertFalse("kotlinx.serialization" in build)

    val readerBuild = File("../reader/build.gradle.kts").readText()
    assertTrue("implementation(project(\":reader:engine\"))" in readerBuild)
    assertFalse("api(project(\":reader:engine\"))" in readerBuild)
}
```

Add a verifier test requiring forbidden engine imports:

```text
android.
androidx.
app.openstory.chapters.
app.openstory.reader.content.
app.openstory.reader.routing.
app.openstory.plugins.
app.openstory.downloads.
app.openstory.storage.
kotlinx.coroutines.
kotlinx.serialization.
java.io.
java.net.
```

Add a shell-fixture mutation that places a forbidden import under `reader/engine/src/main` and proves the package verifier detects it. This specifically guards the current nested-source-root blind spot.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :build-logic:test --tests '*ModuleGraphTest*readerEngineIsConstitutionallyPureJvm*' --no-daemon
```

Expected: FAIL because the module/policy/build file do not yet exist.

Run the package-boundary fixture test and verify it also fails to cover `reader/engine/src/main` before the script change.

- [ ] **Step 3: Register the module and exact policy**

Add:

```kotlin
include(":reader:engine")
```

Create:

```kotlin
plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit"))
}
```

Add `implementation(project(":reader:engine"))` to `:reader`, update the exact `:reader` policy dependency set, add the full engine policy entry, teach `verify-package-boundaries.sh` to scan `reader/engine/src/main` explicitly, and advance `verify-current-architecture-test.sh` from the accepted pre-HES 16-module set to the intentional 17-module set. Update `current-state.md`/roadmap to say HES M0 is **in progress**, 17 production modules are now present, Room remains schema 11, and HES is not yet accepted; preserve the R0 Wave 10 acceptance state exactly.

- [ ] **Step 4: Run constitutional gates GREEN**

```bash
./gradlew :build-logic:test --tests '*ModuleGraphTest*' --tests '*ModuleBoundaryVerifierTest*' --no-daemon
./gradlew :reader:engine:compileKotlin :reader:engine:test --no-daemon
./gradlew verifyArchitecture --no-daemon
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

The current-architecture script/test must now report the intentional post-HES count of 17 production modules; update only its exact count/approved module set, not its mutation strength. `current-state.md` must remain truthful during implementation: HES source/guardrail work may be present without claiming M0-M7 acceptance.

- [ ] **Step 5: Record checkpoint**

```bash
git add settings.gradle.kts reader/engine reader/build.gradle.kts config/architecture build-logic/src/test scripts docs/project/current-state.md docs/implementation/current-roadmap.md
 git commit -m "arch(reader): add pure HES reader engine boundary"
```

### Task 4: Define fixed-point values, versions, routing policy, and normalization invariants

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingValues.kt`
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingPolicy.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingPolicyTest.kt`

**Interfaces:**
- Produces: `BasisPoints`, `ReaderPlanRevision`, `ReaderChapterGraphRevision`, `SourceGroupKey`, `RoutingIntent`, `ReaderNetworkClass`, `AccessMode`, `AttemptRole`.
- Produces versions: `HesContractVersion.HES_V1`, `ReaderRoutingAlgorithmVersion.READER_ROUTING_V1`, `ReaderPolicyVersion`, `HealthPolicyVersion`.
- Produces validated `ReaderRoutingPolicy`, `ReaderRoutingWeights`, `HedgePolicy`, `LanguageFallbackMode`.

- [ ] **Step 1: Write failing constructor/policy tests**

Cover at least:

```kotlin
@Test fun basisPointsRejectOutOfRange() {
    assertFailsWith<IllegalArgumentException> { BasisPoints(-1) }
    assertFailsWith<IllegalArgumentException> { BasisPoints(10_001) }
}

@Test fun defaultWeightsSumToTenThousand() {
    assertEquals(10_000, ReaderRoutingPolicy.v1().weights.total)
}

@Test fun strictLanguageRequiresNonEmptyUniqueNormalizedTags() {
    assertFailsWith<IllegalArgumentException> {
        ReaderRoutingPolicy.v1(languageOrder = emptyList(), languageFallbackMode = LanguageFallbackMode.STRICT_ALLOWED)
    }
    assertFailsWith<IllegalArgumentException> {
        ReaderRoutingPolicy.v1(languageOrder = listOf("VI", "vi"))
    }
}
```

Also assert non-negative switch/hedge delays, switch/hedge thresholds in `0..10_000`, `maxRecoveryAttempts in 0..6`, `maxPlannedForegroundRemoteAttempts in 1..4`, and non-blank `SourceGroupKey`.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*ReaderRoutingPolicyTest*' --no-daemon
```

Expected: compilation failure because the types do not exist.

- [ ] **Step 3: Implement immutable values and exact R2 defaults**

Use:

```kotlin
@JvmInline value class BasisPoints(val value: Int) { init { require(value in 0..10_000) } }
@JvmInline value class ReaderPlanRevision(val value: Long) { init { require(value >= 0) } }
@JvmInline value class ReaderChapterGraphRevision(val value: Long) { init { require(value >= 0) } }
@JvmInline value class SourceGroupKey(val value: String) { init { require(value.isNotBlank()) } }
```

Routing weights:

```text
language 2500
continuity 2500
health 1800
reliability 1000
completeness 900
latency 700
freshness 300
cache utility 300
```

Lock policy defaults:

```text
languageFallbackMode = ORDERED_ALLOW
normalSwitchThreshold = 800
degradedSwitchThreshold = 350
allowUnverifiedLocalAttempt = true
maxRecoveryAttempts = 6
maxPlannedForegroundRemoteAttempts = 4
hedgeDelayMillis = 650
hedgePrimaryP95ThresholdMillis = 1200
hedgeMinimumLatencySamples = 3
hedgeAlternateMinimumRemoteAccessScore = 8000
hedgeAlternateMinimumReliability = 9000
```

Normalize language with trim -> `_` to `-` -> locale-independent lowercase. Reject blank/duplicate normalized policy tags.

- [ ] **Step 4: Run policy and architecture tests GREEN**

```bash
./gradlew :reader:engine:test --tests '*ReaderRoutingPolicyTest*' --no-daemon
./gradlew verifyArchitecture --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): define HES routing policy values"
```

### Task 5: Define immutable routing/health facts and decision/trace contracts without effect behavior

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingFacts.kt`
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingFactsTest.kt`

**Interfaces:**
- Produces: `RoutingCandidate`, `CandidateRemoteAccess`, `CandidateLocalAccess`, `ReadingContinuity`, `SourceOperation.READ_DOCUMENT`, `SourceOperationKey`, `CircuitState`, `SourceHealthState`, `SourceHealthSnapshot`, `SourceHealthOrigin`, `ReaderRoutingSnapshot`.
- Produces the public reducer contract exactly as `advance(previous, nowEpochMillis, policy)` and `reduce(previous, observation, nowEpochMillis, policy)`; Task 14 supplies the v1 implementation.
- Produces decision contracts: `RouteAttempt`, `CompetitiveSet`, `HedgeDirective`, `CandidateRejection`, `DecisionReason`, `AccessReason`, `RejectionCode`, `DiagnosticNote`, `ReaderDecisionTrace`, `ReaderRouteDecision`.
- Does not yet implement scoring/reducer/execution.

- [ ] **Step 1: Write failing contract tests**

Assert unique release IDs, non-blank candidate language, valid LOCAL locator invariant, and one plan-revision type:

```kotlin
@Test fun localAttemptRequiresFingerprintAndRemoteForbidsIt() {
    assertFailsWith<IllegalArgumentException> { routeAttempt(accessMode = AccessMode.LOCAL, localFingerprint = null) }
    assertFailsWith<IllegalArgumentException> { routeAttempt(accessMode = AccessMode.REMOTE, localFingerprint = "fp") }
}

@Test fun snapshotRejectsDuplicateReleaseIds() {
    val c = candidate(releaseId = ChapterReleaseId("r"))
    assertFailsWith<IllegalArgumentException> { snapshot(candidates = listOf(c, c)) }
}
```

Also assert `ReaderRoutingSnapshot.planRevision` and `ReaderRouteDecision.planRevision` use exactly `ReaderPlanRevision`, and trace collections are ordered lists rather than decision-critical maps/sets.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*ReaderRoutingFactsTest*' --no-daemon
```

- [ ] **Step 3: Implement exact R2 fact shapes**

Candidate:

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

Local access variants carry fingerprint only for `AVAILABLE_EXACT`, `AVAILABLE_UNVERIFIED`, and `KNOWN_INVALID`. Snapshot includes target chapter, graph revision, plan revision, intent, canonicalized candidate list, health snapshots, continuity, network, explicit release, and caller-supplied wall-clock time.

Define stable final reasons at minimum:

```text
EXPLICIT_ELIGIBLE_RELEASE
TOP_RANKED_NO_INCUMBENT
TARGET_RESUME_INCUMBENT_RETAINED
INCUMBENT_RETAINED_BY_HYSTERESIS
CHALLENGER_EXCEEDED_SWITCH_THRESHOLD
INCUMBENT_UNAVAILABLE
NO_ELIGIBLE_CANDIDATE
```

Define R2 access/candidate rejection codes exactly:

```text
LOCAL_COPY_KNOWN_INVALID
REMOTE_SOURCE_DISABLED_OR_UNAVAILABLE
REMOTE_NETWORK_UNAVAILABLE
REMOTE_CIRCUIT_OPEN
HALF_OPEN_PROBE_NOT_PERMITTED
LANGUAGE_FORBIDDEN
NO_USABLE_ACCESS_PATH
EXPLICIT_RELEASE_NOT_PRESENT // diagnostic, not candidate rejection
```

- [ ] **Step 4: Run all contract/policy tests GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): define immutable routing contracts"
```

# M1 — Legacy-Compatible Pure Reasoner

### Task 6: Add the `:reader` legacy adapter with implementation-only engine visibility

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/LegacyReaderRoutingAdapter.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/LegacyReaderRoutingAdapterTest.kt`

**Interfaces:**
- Consumes current `ReleaseCandidate`, `ReleaseSelectionPolicy`, current chapter/release IDs.
- Produces engine facts **inside `:reader` only**.
- Production baseline mapping: `sourceGroupKey = null`, `completeness = 10_000`; legacy differential fixtures may inject explicit completeness/trusted-group facts.

- [x] **Step 1: Write failing mapping tests**

Assert production mapping does not invent source group/completeness:

```kotlin
val mapped = adapter.productionCandidate(legacy.release, remoteAccess = CandidateRemoteAccess.PERMITTED)
assertEquals(legacy.release.id, mapped.releaseId)
assertEquals(legacy.release.pluginId, mapped.sourceId)
assertNull(mapped.sourceGroupKey)
assertEquals(BasisPoints(10_000), mapped.completeness)
```

Add fixture-only mapping tests that legacy `ReleaseCandidate.completeness` may become `* 100` only for differential fixtures, not production mapping.

- [x] **Step 2: Run focused Reader test and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*LegacyReaderRoutingAdapterTest*' --no-daemon
```

- [x] **Step 3: Implement adapter and verify engine types remain internal to Reader module APIs**

Do not change `ReaderDocumentStore`, `ReaderSourceAvailability`, Downloads, App, or Feature Reader signatures to engine DTOs. Add an architecture/source test that no production file outside `reader/` imports `app.openstory.reader.engine.*`.

- [x] **Step 4: Run Reader + architecture gates GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*LegacyReaderRoutingAdapterTest*' --no-daemon
./gradlew verifyArchitecture --no-daemon
bash scripts/verify-package-boundaries.sh
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/src
 git commit -m "refactor(reader): map legacy routing facts inside reader boundary"
```

### Task 7: Implement the deterministic compatibility planner for the representable legacy envelope

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteEngine.kt`
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRouteEngineCompatibilityTest.kt`

**Interfaces:**
- Produces `ReaderRouteEngine.plan(snapshot, policy)` and `ReaderRouteEngine.v1()`.
- M1 only reproduces representable legacy tiers; adaptive access-aware scoring replaces compatibility internals in M4.
- M1 fixtures use REMOTE-only usable paths and neutral source-operation health.

- [x] **Step 1: Write failing compatibility ordering tests**

Cover the overlap tiers that both models can represent:

```text
explicit release
persisted target resume release
trusted previous/source-group fixture
previous/committed source
language order
fixture completeness
publication time
sourceId/releaseId stable tie
```

Do not emulate legacy per-release `ReleaseHealth`; keep it HEALTHY in M1 fixtures because HES health is source-operation state.

- [x] **Step 2: Run focused pure tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*ReaderRouteEngineCompatibilityTest*' --no-daemon
```

- [x] **Step 3: Implement canonicalized compatibility planning**

Canonicalize input by `(sourceId.value, releaseId.value)` first. Emit deterministic REMOTE route attempts (`attempt-0`, `attempt-1`, ...) and return the unchanged `planRevision`. The compatibility reasoner must not read clocks/randomness/global state.

- [x] **Step 4: Add replay/reversed-input assertions and run GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): add deterministic compatibility planner"
```

### Task 8: Differential-test legacy `ReleaseSelector` before adaptive behavior intentionally diverges

**Files:**
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteEngineDifferentialTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/selection/ReleaseSelectorTest.kt`

**Interfaces:**
- Consumes old selector + adapter + new pure engine.
- Produces a documented overlap envelope; intentional future divergences move to named G01-G26 tests rather than disabled differential assertions.

- [x] **Step 1: Write deterministic differential fixtures/generator**

Generate at least 200 seeded candidate sets. Keep legacy health `HEALTHY`; use facts both systems can express. Assert selected release and unique alternate semantic order equality.

- [x] **Step 2: Run differential tests and verify RED for any mapping/comparator gap**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteEngineDifferentialTest*' --no-daemon
```

- [x] **Step 3: Fix only overlap mismatches**

Do not introduce health, network, local-locator ranking, hysteresis, prefetch, or hedge behavior in this task.

- [x] **Step 4: Run selector + differential + engine suites GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReleaseSelectorTest*' --tests '*ReaderRouteEngineDifferentialTest*' --no-daemon
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/src/test reader/engine/src
 git commit -m "test(reader): lock legacy routing overlap envelope"
```

# M2 — Session and Coordinator Compatibility Boundary

### Task 9: Define per-screen session, generation, plan-revision, graph-revision, and commit identities

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSessionFactory.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionState.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`

**Interfaces:**
- Produces effect identities: `ReaderSessionId`, `ReaderGenerationId`, engine `ReaderPlanRevision`, engine `ReaderChapterGraphRevision`.
- Produces runtime identity: `(sessionId, generationId, planRevision, attemptId, targetChapterId)`.
- Produces `ReaderRouteSessionFactory.create(storyId: StoryId): ReaderRouteSession`.
- Produces session calls `suspend fun updateChapterGraph(groups: List<CanonicalChapterGroup>)`, `suspend fun updateRoutingPreferences(preferences: ReaderPreferences)`, and `suspend fun execute(intent: ReaderForegroundIntent): ReaderForegroundResult`.
- `ReaderForegroundIntent` contains only `targetChapterId` and optional explicit release; the session already owns `storyId`, latest graph, preferences, committed continuity, generation state, and process collaborators.
- `ReaderForegroundResult` has `Committed`, `Exhausted`, and `Superseded` outcomes; `Committed` includes target chapter/group context, chosen `ChapterRelease`, `ReaderDocument`, `fromLocal: Boolean`, previous/next chapter IDs, and exact restoration data when safe. No engine `AccessMode` leaks to Feature Reader.
- Produces per-session latest chapter-group state/revision; process health is deliberately absent from session state.

- [x] **Step 1: Write failing identity/state tests**

Lock user-intent vs hard-replan semantics:

```kotlin
@Test fun everyForegroundUserIntentGetsNewGeneration() {
    val session = testSession()
    assertEquals(ReaderGenerationId(1), session.startForegroundIntent(chapterA, null).generationId)
    session.finishAsCommitted()
    assertEquals(ReaderGenerationId(2), session.startForegroundIntent(chapterB, null).generationId)
}

@Test fun hardInvalidationInActiveUncommittedIntentOnlyIncrementsPlanRevision() {
    val session = executingSession(generation = 4, revision = 2)
    val replanned = session.hardInvalidate()
    assertEquals(ReaderGenerationId(4), replanned.generationId)
    assertEquals(ReaderPlanRevision(3), replanned.planRevision)
}
```

Also assert explicit release selection/retry after exhaustion starts a new generation, two sessions may both have generation `1` without collision, and no `executionRevision`/plan hash exists.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteSessionStateTest*' --no-daemon
```

- [x] **Step 3: Implement semantic execution states and session ownership**

Use:

```kotlin
sealed interface ReaderExecutionState {
    data object Idle : ReaderExecutionState
    data class Planning(...) : ReaderExecutionState
    data class Executing(...) : ReaderExecutionState
    data class Recovering(...) : ReaderExecutionState
    data class Validating(...) : ReaderExecutionState
    data class Committed(...) : ReaderExecutionState
    data class Exhausted(...) : ReaderExecutionState
    data class Cancelled(...) : ReaderExecutionState
}
```

Session state tracks committed identity, transition target, latest chapter groups/revision, active intent/revision, and prefetch ownership. Do not put singleton source health or process source locks here.

- [x] **Step 4: Run session tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteSessionStateTest*' --no-daemon
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/src
 git commit -m "feat(reader): add session-scoped execution identity"
```

### Task 10: Extract current sequential loading into a reusable compatibility executor without changing legacy façade semantics

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorCompatibilityTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/content/ReaderDocumentRepositoryTest.kt`

**Interfaces:**
- Produces an internal sequential execution seam reusable by the future coordinator.
- Preserves current `ReaderDocumentRepository.load(ReaderLoadRequest): ReaderLoadResult` and current selector behavior during M2.
- Does **not** force `ReaderLoadRequest` through `ReaderRouteEngine`; legacy façade lacks reliable target chapter identity.

- [x] **Step 1: Port current repository behavior into executor-focused RED tests**

Lock existing compatibility behavior before changing ownership:

```text
selected cached candidate wins before source enumeration
current explicit download is used when no requested fingerprint exists
requested fingerprint is read exactly when supplied
cancellation is rethrown
sources are enumerated lazily after a cache miss
valid persistable remote document is written
image-page/non-persistable remote document is not stored
fallback order remains selector order
legacy ReaderLoadFailure surface remains unchanged
```

Do not yet encode corrected M3 local-I/O/quarantine semantics as compatibility requirements; those are intentional named behavior changes in Task 13.

- [x] **Step 2: Run focused tests and verify RED before extraction**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteExecutorCompatibilityTest*' --no-daemon
```

- [x] **Step 3: Extract the current loop behind repository without changing public behavior**

`ReaderDocumentRepository` remains selector + façade owner in M2. Move the local/source attempt loop into `ReaderRouteExecutor` with an internal compatibility entry that consumes already ordered `ReleaseCandidate` values. Keep cancellation behavior identical.

- [x] **Step 4: Run executor + repository regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteExecutorCompatibilityTest*' --tests '*ReaderDocumentRepositoryTest*' --no-daemon
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/src
 git commit -m "refactor(reader): extract sequential reader route executor"
```

### Task 11: Introduce the real-target `ReaderRouteCoordinator` and explicit session API without production UI cutover yet

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorCompatibilityTest.kt`

**Interfaces:**
- Produces explicit Reader session foreground contract using a real `CanonicalChapterId`; no synthetic legacy target.
- Keeps legacy `ReaderDocumentRepository` separately usable until M5 Feature Reader cutover.
- M2 coordinator may use compatibility candidate ordering/execution with cache/network/health defaults; adaptive behavior stays disabled.

- [x] **Step 1: Write failing explicit-target/session-isolation tests**

A coordinator request must contain a real target:

```kotlin
data class ReaderForegroundIntent(
    val targetChapterId: CanonicalChapterId,
    val explicitReleaseId: ChapterReleaseId? = null,
)
```

Tests prove two independently created sessions cannot cancel each other's generation and that session result identity contains the real target chapter.
Routing preferences remain session-owned and arrive through `updateRoutingPreferences(...)`; they are not duplicated into each foreground intent.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteCoordinatorCompatibilityTest*' --no-daemon
```

- [x] **Step 3: Implement coordinator/session factory and DI behind non-production-cutover API**

`ReaderRouteCoordinator` orchestrates:

```text
session target + latest chapter facts -> snapshot assembly -> engine decision -> executor -> session result
```

At this milestone, local/cache behavior may still route through the compatibility sequential seam and engine adaptive features remain disabled. The coordinator must not mutate one process-global active generation.

- [x] **Step 4: Run Reader + Feature Reader compile/regression gates GREEN**

```bash
./gradlew :reader:testDebugUnitTest :feature:reader:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **Step 5: Record checkpoint — intentionally deferred to the combined M1+M2 commit**

```bash
git add reader/src app/src/main/kotlin/app/openstory/di/ReaderModule.kt
 git commit -m "feat(reader): add explicit target route coordinator"
```

# M3 — Typed Observations, Validation, and Process Health

### Task 12: Inventory and exhaustively classify every current Reader-reachable plugin/runtime/sanitizer code

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderAttemptFailure.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceFailureClassifier.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceFailureClassifierTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceFailureInventoryTest.kt`

**Interfaces:**
- Produces `RecoveryScope.RELEASE_SCOPED | SOURCE_SCOPED | LOCAL_SCOPED | CLIENT_SCOPED`.
- Produces typed `SourceObservation` family and attempt origin (`NORMAL_REMOTE_ATTEMPT | HALF_OPEN_PROBE`).
- Preserves legacy `ReaderLoadFailure(code, retryable)` only at the old façade boundary.

- [ ] **Step 1: Write the exact classification-table tests before implementation**

At minimum lock these exact current codes:

```text
plugin.execution_timeout                 -> TransportFailure.Timeout, SOURCE_SCOPED, penalizing
plugin.http_request_failed               -> TransportFailure.Connection, SOURCE_SCOPED, penalizing
plugin.http_read_failed                  -> TransportFailure.Connection, SOURCE_SCOPED, penalizing
plugin.auth_unavailable                  -> AuthFailure.CredentialsUnavailable, SOURCE_SCOPED, non-penalizing
plugin.http_credentials_failed           -> AuthFailure.CredentialsUnavailable, SOURCE_SCOPED, non-penalizing
plugin.disabled                          -> SourceStateFailure.DisabledOrNotInstalled, SOURCE_SCOPED, non-penalizing
plugin.not_installed                     -> SourceStateFailure.DisabledOrNotInstalled, SOURCE_SCOPED, non-penalizing
plugin.operation_unavailable             -> SourceStateFailure.OperationUnavailable, SOURCE_SCOPED, non-penalizing
plugin.http_domain_denied                -> PluginPolicyFailure.ConfigurationOrCapability, SOURCE_SCOPED, non-penalizing
plugin.capability_denied                 -> PluginPolicyFailure.ConfigurationOrCapability, SOURCE_SCOPED, non-penalizing
plugin.http_managed_header_collision     -> PluginPolicyFailure.ConfigurationOrCapability, SOURCE_SCOPED, non-penalizing
reader.document_empty                    -> ContentFailure.EmptyDocument, SOURCE_SCOPED, penalizing
reader.document_too_large                -> ContentFailure.InvalidDocument, SOURCE_SCOPED, penalizing
reader.document_title_invalid            -> ContentFailure.InvalidDocument, SOURCE_SCOPED, penalizing
reader.document_block_invalid            -> ContentFailure.InvalidDocument, SOURCE_SCOPED, penalizing
reader.source_payload_invalid            -> ContentFailure.InvalidDocument, SOURCE_SCOPED, penalizing
reader.source_failed                     -> RuntimeFailure.Unexpected or transport fallback according to invocation context, CLIENT_SCOPED/non-penalizing unless source origin is proven
```

Also enumerate the other exact codes reachable from current `CONTENT_CHAPTER` HTTP/runtime output/policy boundary; the inventory test fails whenever a new reachable code appears without an exact table entry. Do not implement prefix/substring classification.

- [ ] **Step 2: Run classifier/inventory tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderSourceFailureClassifierTest*' --tests '*ReaderSourceFailureInventoryTest*' --no-daemon
```

- [ ] **Step 3: Implement typed attempt failure + exact table**

Use an internal Reader wrapper equivalent to:

```kotlin
data class ReaderAttemptFailure(
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val accessMode: AccessMode,
    val observation: SourceObservation,
    val recoveryScope: RecoveryScope,
    val legacyCode: String,
    val retryable: Boolean,
)
```

Unknown remote retryable strings may map to `TransportFailure.Connection` only when the adapter knows the exception/result originated from the remote source invocation boundary. Unknown non-retryable/internal failures remain non-penalizing.

- [ ] **Step 4: Run classification + current plugin/runtime Reader regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderSourceFailure*' --no-daemon
./gradlew :plugins:runtime:testDebugUnitTest --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src reader/src
 git commit -m "refactor(reader): classify Reader source failures semantically"
```

### Task 13: Make materialized document validation explicit and correct local-corruption/cache-write semantics

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderDocumentValidatorAdapter.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt` only where legacy façade delegates to the corrected executor path.
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderDocumentValidatorAdapterTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/content/ReaderDocumentRepositoryTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStoreTest.kt` if storage behavior needs explicit regression coverage.

**Interfaces:**
- Produces semantic validation before commit/store success.
- Changes intentional legacy bug: unclassified local I/O exception no longer automatically quarantines the requested fingerprint.
- Preserves: exact locator mismatch/decode corruption quarantines only that `(releaseId, fingerprint)`.
- Preserves: valid remote semantic success even when best-effort automatic-cache write fails.

- [ ] **Step 1: Write failing R2 validation/error-boundary tests**

Cover:

```text
empty materialized document -> ContentFailure.EmptyDocument
valid ReaderDocument -> success
LOCAL requested fingerprint != decoded document fingerprint -> LocalFailure.FingerprintOrDecodeMismatch + quarantine exact locator
LOCAL missing blob -> LocalFailure.MissingBlob, no quarantine, no source penalty
LOCAL storage I/O exception -> CLIENT/LOCAL failure, no automatic corruption claim/quarantine
quarantine non-cancellation failure -> diagnostic only; remote recovery continues
remote valid changed fingerprint relative to saved progress -> valid semantic success
remote invalid Reader document -> SOURCE_SCOPED penalizing content failure
remote valid persistable document + store.write failure -> semantic success still commits
CancellationException from read/write/quarantine/source -> rethrow
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderDocumentValidatorAdapterTest*' --tests '*ReaderDocumentRepositoryTest*' --no-daemon
```

- [ ] **Step 3: Implement fetch/read -> validate -> best-effort persistence -> semantic success ordering**

Do not compare remote result fingerprint against persisted progress fingerprint. Compare fingerprint only when reading an exact LOCAL locator. When an exact local locator is confirmed corrupt, notify the active session/coordinator so a later snapshot can map that locator to engine `KNOWN_INVALID`; a metadata miss or unrelated fingerprint never creates that state. Keep image/page network probing out of this validator.

- [ ] **Step 4: Run Reader + Downloads storage regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderDocument*' --tests '*ReaderRouteExecutor*' --no-daemon
./gradlew :downloads:testDebugUnitTest --tests '*DownloadAwareReaderDocumentStoreTest*' --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src downloads/src/test
 git commit -m "fix(reader): validate semantic documents without misclassifying cache failures"
```

### Task 14: Implement the pure bounded health reducer including late-normal-success OPEN semantics

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultSourceHealthReducer.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/SourceHealthReducerTest.kt`

**Interfaces:**
- Produces `SourceHealthReducer.v1()`.
- Health key = `SourceOperationKey(sourceId, READ_DOCUMENT)`.
- Retains max 20 successful remote latency samples.

- [ ] **Step 1: Write failing state-machine tests**

Lock exact defaults and race authority:

```kotlin
@Test fun thirdDefaultPenalizingFailureOpensNeutralCircuit() { /* EWMA 5120 */ }
@Test fun advanceMovesOpenToHalfOpenAtCooldownBoundary() { /* 29_999 OPEN, 30_000 HALF_OPEN */ }
@Test fun successfulHalfOpenProbeClosesAndResetsOpenCount() { ... }
@Test fun failedHalfOpenProbeReopensWithExponentialCooldownCappedAtFiveMinutes() { ... }
@Test fun lateNormalSuccessWhileOpenCannotCloseOrResetOpenCycle() { ... }
```

Also test auth/cancellation/local success do not lower reliability, valid normal/probe remote success contributes `10_000`, penalizing remote failures contribute `0`, latency samples cap at 20, fewer than three samples mean unknown latency, and nearest-rank p50/p95. Add `HealthPolicy.v1()` construction tests for alpha `1..10_000`, positive minimum cooldown, maximum >= minimum, open threshold/count bounds, and v1 max-latency-samples `1..20`.

- [ ] **Step 2: Run focused pure tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*SourceHealthReducerTest*' --no-daemon
```

- [ ] **Step 3: Implement exact integer policy**

Defaults:

```text
alpha = 2000
openAfterConsecutivePenalizingFailures = 3
openAtOrBelowReliability = 5500
minimumCooldownMillis = 30000
maximumCooldownMillis = 300000
maxLatencySamples = 20
```

EWMA:

```text
next = (2000 * sample + 8000 * previous) / 10000
```

Cooldown is `30_000 * 2^(openCount - 1)`, clamped to `300_000`. Only successful `HALF_OPEN_PROBE` closes an OPEN/HALF_OPEN cycle. A `NORMAL_REMOTE_ATTEMPT` success observed while OPEN may update reliability/latency but cannot reset circuit/openCount/cooldown/failure-cycle authority.

- [ ] **Step 4: Run all engine tests + architecture gate GREEN**

```bash
./gradlew :reader:engine:test verifyArchitecture --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): add bounded Reader source health reducer"
```

### Task 15: Add process-shared source health registry, Reader remote limiter, and owned HALF_OPEN probe leases

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceHealthRegistry.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceHealthRegistryTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- Process singleton health keyed by `SourceOperationKey`, with Reader-internal methods equivalent to `suspend fun snapshot(key, nowEpochMillis): SourceHealthSnapshot` and `suspend fun record(key, observation, nowEpochMillis)`.
- Process singleton Reader remote lane: max one active Reader REMOTE per `sourceId`.
- Produces held plan-scoped HALF_OPEN probe lease; engine receives only `halfOpenProbePermitted` fact.
- Foreground Reader work has priority over same-source prefetch.

- [ ] **Step 1: Write failing process-sharing/lease/priority tests**

Prove:

```text
session A observations change health snapshot seen by session B
new registry starts neutral; no Room/storage dependency exists
snapshot(now) calls reducer.advance before exposing state
only one probe lease per SourceOperationKey can be held
unused probe lease releases on plan cancel/finalization
only one Reader remote attempt per sourceId is active across different source object instances/sessions
foreground preempts/wins lane priority over Reader prefetch
prefetch preemption becomes Cancellation.PrefetchPreempted and does not penalize health
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderSourceHealthRegistryTest*' --tests '*ReaderSourceExecutionLimiterTest*' --no-daemon
```

- [ ] **Step 3: Implement process-scoped serialization with Reader-side coroutines only**

Use Reader-side mutex/serialized state; do not put coroutines into engine. Probe tokens never cross into engine facts. Cancellation of a source call does not assume plugin transport has stopped; generation/revision guards remain the semantic correctness barrier.

- [ ] **Step 4: Wire singleton registry/limiter and run GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderSourceHealthRegistryTest*' --tests '*ReaderSourceExecutionLimiterTest*' --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src app/src/main/kotlin/app/openstory/di/ReaderModule.kt
 git commit -m "feat(reader): share process Reader health and source execution limits"
```

### Task 16: Assemble explicit health-aware snapshots and complete observational decision-trace plumbing with adaptive ranking still disabled

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRouteDecision.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderDecisionTraceTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerTest.kt`

**Interfaces:**
- Produces explicit `nowEpochMillis`, graph revision, health origin, held-probe-permission facts, explicit release, continuity, network/cache defaults.
- Produces complete trace shape; diagnostics never alter compatibility decision.

- [ ] **Step 1: Write failing trace/snapshot equality tests**

Assert:

```text
candidate IDs are canonical (sourceId, releaseId) order
snapshot.planRevision == decision.planRevision == trace.planRevision
snapshot.chapterGraphRevision == trace.chapterGraphRevision
health origin changes STARTUP_NEUTRAL -> PROCESS_OBSERVED after observation
trace enable/filter persistence has no effect on primary/recovery decision
DecisionReason/AccessReason/RejectionCode/DiagnosticNote remain distinct types
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*ReaderDecisionTraceTest*' --no-daemon
./gradlew :reader:testDebugUnitTest --tests '*RouteSnapshotAssemblerTest*' --no-daemon
```

- [ ] **Step 3: Implement observational trace and assembler defaults**

Until M4 adapters land, assembler may use Reader-owned cache/network `UNKNOWN` facts but must use actual process health snapshots and existing source availability. Do not query Android or Downloads from engine.

- [ ] **Step 4: Run pure + Reader routing regressions GREEN**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src reader/src
 git commit -m "feat(reader): add explainable health-aware route snapshots"
```

# M4 — Adaptive Routing, Bounded Effect Facts, Reactive Graph, and Replanning

### Task 17: Implement exact local/remote/candidate eligibility and language/network semantics

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/EligibilityEvaluator.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/EligibilityEvaluatorTest.kt`

**Interfaces:**
- Produces eligible LOCAL/REMOTE paths and typed rejections.
- Explicit selection is evaluated only after eligibility.

- [ ] **Step 1: Write failing eligibility matrix**

Cover all R2 cases:

```text
AVAILABLE_EXACT -> routable LOCAL with exact fingerprint
AVAILABLE_UNVERIFIED -> routable only when policy allows
KNOWN_INVALID -> LOCAL_COPY_KNOWN_INVALID; remote may remain eligible
MISS/UNKNOWN -> no local attempt, not corruption
OFFLINE -> remote rejected, exact local remains eligible
source unavailable -> remote rejected, local remains eligible
OPEN -> remote rejected, local remains eligible
HALF_OPEN no held lease -> remote rejected
HALF_OPEN held lease -> remote eligible
STRICT_ALLOWED unlisted -> LANGUAGE_FORBIDDEN candidate-wide
all paths rejected -> NO_USABLE_ACCESS_PATH
explicit absent -> diagnostic EXPLICIT_RELEASE_NOT_PRESENT and automatic routing continues
explicit present but rejected -> cannot bypass hard rule
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*EligibilityEvaluatorTest*' --no-daemon
```

- [ ] **Step 3: Implement eligibility before preference/ranking**

`ORDERED_ALLOW` never hard-rejects unlisted languages. Candidate-wide rejection happens only after all access paths are evaluated. Local eligibility never checks remote source health/enabled state.

- [ ] **Step 4: Run engine tests GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): enforce access-aware Reader eligibility"
```

### Task 18: Implement semantic + preferred-access feature normalization and fixed-point ranking

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateEvaluator.kt`
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateRanker.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/CandidateEvaluatorTest.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/CandidateRankerTest.kt`

**Interfaces:**
- Produces semantic features `{language, continuity, completeness, freshness}`.
- Produces access features `{health, reliability, latency, cacheUtility}` from the candidate's preferred usable path.
- Produces stable weighted semantic candidate score and separate REMOTE access evaluation for hedge policy.

- [ ] **Step 1: Write failing exact-boundary normalization tests**

Language:

```text
empty ORDERED_ALLOW -> 10000 all
index0 10000, index1 8000, index2 6000, index>=3 4000, unlisted 2000
```

Preferred LOCAL:

```text
health 10000, reliability 10000, latency 10000
AVAILABLE_EXACT cache 10000
AVAILABLE_UNVERIFIED cache 6000
```

REMOTE latency boundaries:

```text
<=250 10000
<=500 8500
<=1000 6500
<=2000 4000
<=4000 2000
>4000 1000
<3 samples 5000
```

Freshness boundaries:

```text
<=1h 10000
<=24h 9000
<=7d 7500
<=30d 6000
>30d 4000
unknown 5000
all unknown -> 5000 all
```

Health: CLOSED 10000, held HALF_OPEN 6000, OPEN rejected before scoring. Reliability = EWMA directly.

- [ ] **Step 2: Run evaluator/ranker tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*CandidateEvaluatorTest*' --tests '*CandidateRankerTest*' --no-daemon
```

- [ ] **Step 3: Implement fixed-point feature evaluation and stable rank**

Compute one full `Long` weighted sum then divide once. Tie order:

```text
weighted score DESC
sourceId.value ASC
releaseId.value ASC
```

A local-preferred candidate must not inherit degraded/OPEN remote health. Record a separate remote access score/evaluation when the candidate also has an eligible remote path so hedge logic never uses local-inflated access features.

- [ ] **Step 4: Run engine tests + permutation smoke GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): rank Reader candidates with access-aware fixed-point features"
```

### Task 19: Implement continuity, deterministic incumbent resolution, explicit arbitration, and hysteresis

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/ContinuityArbiter.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ContinuityArbiterTest.kt`

**Interfaces:**
- Incumbent resolution: same-target committed release -> target resume release -> trusted group -> committed source -> none.
- Explicit eligible release bypasses automatic hysteresis.
- Degraded incumbent threshold applies only when preferred path is REMOTE and reliability `< 8500` or circuit HALF_OPEN.

- [ ] **Step 1: Write failing incumbent/continuity/hysteresis tests**

Cover:

```text
target resume release continuity = 10000
same committed release on same target = 10000
trusted group = 8000
same source = 6500
same language only = 2000
no trusted group fact -> no group bonus
same-target committed eligible release outranks a conflicting target-resume incumbent
resume eligible beats committed-source incumbent resolution on a cross-chapter target
normal advantage 799 -> incumbent retained
normal advantage 800 -> switch
degraded remote advantage 349 -> retained
350 -> switch
local-preferred incumbent is not degraded because remote source is OPEN/degraded
incumbent unavailable -> immediate switch
explicit eligible release -> semantic winner regardless hysteresis
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*ContinuityArbiterTest*' --no-daemon
```

- [ ] **Step 3: Implement arbitration + stable reasons**

Emit `TARGET_RESUME_INCUMBENT_RETAINED`, `INCUMBENT_RETAINED_BY_HYSTERESIS`, `CHALLENGER_EXCEEDED_SWITCH_THRESHOLD`, `INCUMBENT_UNAVAILABLE`, `EXPLICIT_ELIGIBLE_RELEASE`, or `TOP_RANKED_NO_INCUMBENT` as appropriate.

- [ ] **Step 4: Run engine tests GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): arbitrate target continuity with hysteresis"
```

### Task 20: Implement deterministic local/remote route construction, confidence, recovery/source budgets, with hedge still disabled

**Files:**
- Create: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/RoutePlanner.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/RoutePlannerTest.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Create/modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`

**Interfaces:**
- Produces engine-owned LOCAL/REMOTE plan order.
- `LOCAL` always contains exact locator; semantic winner local failure recovers to same-winner REMOTE before next candidate.
- Enforces planned remote count `<= 4`, recovery chain `<= 6`, unique attempts.
- Executor independently enforces runtime ceilings and `SOURCE_SCOPED` suppression.

- [ ] **Step 1: Write failing route/budget/recovery tests**

Test:

```text
winner exact local -> LOCAL primary then same winner REMOTE recovery
winner unverified local -> LOCAL first only when policy permits
invalid/miss/unknown local -> no LOCAL attempt
remaining candidates follow stable semantic ranking, local before remote each
no duplicate release/access/fingerprint execution attempt
recovery chain capped at 6
planned foreground REMOTE <= 4
SOURCE_SCOPED terminal failure skips later REMOTE attempts for same source
SOURCE_SCOPED failure does not skip already valid LOCAL attempt for that source
RELEASE_SCOPED failure allows another release from same source
runtime malformed plan over remote ceiling -> invariant failure, not silent extra launches
```

- [ ] **Step 2: Run planner/executor tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*RoutePlannerTest*' --no-daemon
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteExecutorAdaptiveTest*' --no-daemon
```

- [ ] **Step 3: Implement route construction + diagnostic confidence**

Confidence:

```text
no eligible -> 0
one eligible semantic candidate -> final winner semantic score
otherwise clamp(5000 + finalWinnerScore - bestAlternativeScore, 0, 10000)
```

Keep hedge absent in M4. Assign deterministic attempt IDs from final route order.

- [ ] **Step 4: Run engine + Reader regressions GREEN**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src reader/src
 git commit -m "feat(reader): construct bounded adaptive Reader routes"
```

### Task 21: Add Reader-owned cache facts and one bounded schema-11 metadata query with deterministic fingerprint locator selection

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderCacheFactsPort.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/reader/ReaderCacheMetadataSource.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Create: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderCacheFactsTest.kt`
- Modify/create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`

**Interfaces:**
- Reader DTOs remain outside the engine. `ReaderLocalCacheFact` has only `Unknown`, `Miss`, `Exact(fingerprint)`, and `Unverified(fingerprint)`; `KnownInvalid` is session/execution evidence created only after an actual bad local read.

```kotlin
interface ReaderCacheFactsPort {
    suspend fun inspect(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact>
}
```

- Downloads SPI returns metadata rows only, not engine facts. Define `ReaderCacheMetadata(releaseId, fingerprint, namespace, checksumPresent, downloadState, lastAccessedAtEpochMillis, updatedAtEpochMillis)` and `ReaderCacheMetadataSource.entriesFor(releaseIds: Set<ChapterReleaseId>): List<ReaderCacheMetadata>`.
- Room stays schema 11; no entity/table/index/migration change is required.

- [ ] **Step 1: Write deterministic cache-selection RED tests**

Lock R2 selection exactly:

```text
resume fingerprint + exact stored row -> EXACT(fingerprint)
resume fingerprint + only other fingerprints -> MISS, never KNOWN_INVALID
no resume + newest explicit row COMPLETED -> choose that exact explicit fingerprint
no resume + newest explicit row not COMPLETED -> do not resurrect older explicit; continue to automatic cache
no usable explicit -> automatic cache max lastAccessedAt DESC, fingerprint ASC tie-break
metadata success with no usable row -> MISS
metadata unavailable/error -> UNKNOWN
inspect never calls ChapterBlobStore.read/decode
```

Add a deterministic tie test with two automatic-cache rows sharing `lastAccessedAtEpochMillis` and verify lexicographically ascending fingerprint wins.

- [ ] **Step 2: Run Downloads/Room focused tests and verify RED**

```bash
./gradlew :downloads:testDebugUnitTest --tests '*DownloadAwareReaderCacheFactsTest*' --no-daemon
./gradlew :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

- [ ] **Step 3: Add one bounded DAO query and adapter mapping without schema changes**

Add a DAO query over requested IDs, for example:

```sql
SELECT * FROM chapter_storage_entries
WHERE chapter_release_id IN (:releaseIds)
  AND namespace IN ('EXPLICIT_DOWNLOAD', 'AUTOMATIC_CACHE')
```

Do not filter away non-COMPLETED explicit rows before selection because the rule needs to know the newest explicit row is not usable. Treat a cache row as a usable locator only when its storage metadata proves bytes are stored (`checksum != null`), and require `download_state == COMPLETED` for explicit-download selection. Automatic-cache rows likewise require stored checksum metadata. Do not alter entity/schema exports/version/migrations.

`RoomDownloadRepository` maps only Reader metadata fields to `ReaderCacheMetadataSource`. `DownloadAwareReaderDocumentStore` implements `ReaderCacheFactsPort` using metadata-only deterministic selection. In Hilt, provide one singleton concrete `DownloadAwareReaderDocumentStore` and bind that same instance separately as `ReaderDocumentStore` and `ReaderCacheFactsPort`; do not construct two stores with divergent cache state/dependencies.

- [ ] **Step 4: Prove bounded query and no schema drift**

Run:

```bash
./gradlew :downloads:testDebugUnitTest :storage:room:testDebugUnitTest :storage:room:compileDebugAndroidTestKotlin --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

When a device/emulator exists, run the focused Room instrumentation test and use Room query callback/counting to prove a 32-release inspection issues one bounded metadata query, not 32 `findDownload()` calls.

Also verify:

```bash
rg -n 'version = 11|MIGRATION_10_11' storage/room app
! rg -n 'MIGRATION_11_12|version = 12' storage/room app
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src downloads/src storage/room/src app/src/main/kotlin/app/openstory/di
 git commit -m "perf(reader): inspect deterministic local cache locators in one batch"
```

### Task 22: Add Reader-owned network facts and the Android adapter without consuming Wave 10 background network policy

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderNetworkFactsPort.kt`
- Create: `app/src/main/kotlin/app/openstory/reader/AndroidReaderNetworkFactsPort.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/kotlin/app/openstory/reader/AndroidReaderNetworkFactsPortTest.kt`

**Interfaces:**
- Reader-owned DTO: `ReaderNetworkState.OFFLINE | METERED | UNMETERED | UNKNOWN`.
- Produces `fun interface ReaderNetworkFactsPort { suspend fun current(): ReaderNetworkState }`.
- App adapter maps Android connectivity to Reader DTO; RouteSnapshotAssembler later maps Reader DTO -> engine `ReaderNetworkClass` internally.
- No dependency/reuse of Wave 10 `requireUnmeteredNetwork` foreground semantics.

- [ ] **Step 1: Write failing Android adapter tests using an injectable connectivity snapshot seam**

Lock:

```text
no active network -> OFFLINE
validated active + metered -> METERED
validated active + not metered -> UNMETERED
insufficient facts/security/runtime exception -> UNKNOWN
```

- [ ] **Step 2: Run focused app test and verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*AndroidReaderNetworkFactsPortTest*' --no-daemon
```

- [ ] **Step 3: Implement adapter + `ACCESS_NETWORK_STATE` permission**

Do not import Android connectivity APIs into `:reader` or engine. Do not read settings/background policy to decide Reader foreground network class.

- [ ] **Step 4: Run App/Reader/architecture gates GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests '*AndroidReaderNetworkFactsPortTest*' --no-daemon
./gradlew :reader:testDebugUnitTest verifyArchitecture --no-daemon
bash scripts/verify-package-boundaries.sh
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src app/src
 git commit -m "feat(reader): supply explicit foreground network facts"
```

### Task 23: Add session-local reactive chapter graph revisioning and hard/soft graph invalidation without per-navigation snapshots

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderChapterGraphInvalidationTest.kt`
- Modify later production subscription in M5: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`

**Interfaces:**
- Session accepts latest `List<CanonicalChapterGroup>` emissions and assigns a monotonic `ReaderChapterGraphRevision` to each distinct graph emission.
- A distinct emission may still be a soft/no-replan fact change; graph revision is diagnostic/versioning, not proof that a hard replan is required.
- Hard invalidation only for active uncommitted intent when current target/route becomes invalid; lower-ranked additions stay soft by default.

- [ ] **Step 1: Write failing graph-revision/invalidation tests with synthetic emissions**

Cover:

```text
first graph emission revision 1 and unlocks graph-ready gate
each distinct graph emission increments revision
metadata/label-only distinct emission may increment graph revision but does not cause a hard replan
identical emission does not create a new revision or needless replan
target chapter disappears -> hard invalidation
planned/selected release removed -> hard invalidation
planned release rebound outside target group -> hard invalidation
all viable selected-candidate paths disappear -> hard invalidation
new lower-ranked candidate -> soft update only
metadata/label-only change -> soft/no route revocation
post-commit graph removal does not blank committed content; affects next intent
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderChapterGraphInvalidationTest*' --no-daemon
```

- [ ] **Step 3: Implement session graph state/revision and coordinator invalidation hooks**

Do not add a `snapshot()` call here. M5 ViewModel will feed one `ChapterRepository.observe(storyId)` subscription into the session. Plan revision increments only when a hard graph invalidation affects an active uncommitted generation.

- [ ] **Step 4: Run routing/session tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteSession*' --tests '*ReaderChapterGraphInvalidationTest*' --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src
 git commit -m "feat(reader): make active route sensitive to reactive chapter graph changes"
```

### Task 24: Complete adaptive snapshot assembly and hard/soft replanning from cache/network/health/preferences/graph facts

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerTest.kt`

**Interfaces:**
- Snapshot consumes current target group/revision, `ReaderPreferences.languageOrder`, current `ReaderSourceAvailability`, Reader cache DTOs, session-local confirmed-invalid local locators, Reader network DTO, process health/probe leases, persisted target progress, committed continuity, wall-clock time.
- Production mapping: `sourceGroupKey = null`, `completeness = 10_000`, `languageFallbackMode = ORDERED_ALLOW` with Wave 10 `languageOrder`.
- Hard replan increments only `ReaderPlanRevision` inside the same active generation.

- [ ] **Step 1: Write failing complete-snapshot and replan matrix**

Assert source-enabled facts come from existing `ReaderSourceAvailability` and no duplicate port exists. Assert target progress supplies resume release/fingerprint but remote fetch does not treat resume fingerprint as expected integrity. Assert cache DTO `Exact/Unverified` is overlaid by a session-local confirmed-invalid locator only when an actual prior local read proved that exact locator bad; Room metadata alone cannot emit engine `KNOWN_INVALID`.

Hard invalidations:

```text
active planned release removed/hard-ineligible
source becomes unavailable or circuit OPEN for required active remote route
confirmed local locator invalidity when current plan requires re-evaluation
network becomes definitely OFFLINE and no viable local/ongoing path remains
languageOrder changes during active uncommitted intent and changes routing policy
hard graph invalidation from Task 23
```

Soft facts:

```text
new latency sample without circuit opening
small EWMA change
new lower-ranked candidate
telemetry/diagnostic data
fontScale change
```

New user actions that create a **new generation**, not plan revision:

```text
navigate chapter
explicit select release
retry after exhaustion/transition failure
explicit reload after prior commit
```

- [ ] **Step 2: Run snapshot/replan tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*RouteSnapshotAssemblerTest*' --tests '*ReaderRouteReplanTest*' --no-daemon
```

- [ ] **Step 3: Implement final M4 snapshot/replan coordinator path**

Acquire HALF_OPEN probe leases before final plan assembly for candidate sources currently HALF_OPEN; pass only boolean permission to engine; release unused leases after plan finalization/cancellation. Ordinary attempt failure stays inside current recovery chain and does not blindly replan the same primary. After a remote transport connection failure, the coordinator may resample `ReaderNetworkFactsPort`; when that fresh fact is definitely `OFFLINE` and the active plan has no viable local/ongoing path, treat it as the R2 hard network invalidation. No continuous Android connectivity subscription is required.

- [ ] **Step 4: Run engine/Reader/Downloads/App architecture regressions GREEN**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest :downloads:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew verifyArchitecture --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src app/src
 git commit -m "feat(reader): replan adaptive routes from explicit bounded facts"
```

# M5 — Committed-vs-Target UI Continuity and Bounded Prefetch

### Task 25: Migrate `ReaderViewModel` to one explicit session, one reactive chapter observation, and committed-vs-target presentation semantics

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderContentTest.kt`
- Modify: `scripts/tests/performance-lifecycle-policy-test.sh`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- `ReaderViewModel` creates one `ReaderRouteSession` for its lifetime.
- It collects exactly one `ReaderPreferencesPort.preferences` stream and exactly one `ChapterRepository.observe(storyId)` stream.
- Initial foreground load starts only when both first emissions exist.
- Presentation separates committed content from transition target; old `ReaderDocumentRepository` is no longer the primary production Reader UI execution path after this task.

- [ ] **Step 1: Write failing zero-blank/progress/saved-state/reactive-gate tests**

Add focused tests proving the current bug is removed:

```kotlin
@Test fun openingNextChapterKeepsCommittedDocumentAndProgressOwnerUntilCommit() = runTest {
    val vm = readyAt("chapter-100", fingerprint = "fp-100")
    val committed = vm.state.value.document

    vm.openChapter(CanonicalChapterId("chapter-101"))

    assertSame(committed, vm.state.value.document)
    assertEquals(CanonicalChapterId("chapter-101"), vm.state.value.transitionTargetChapterId)
    vm.updatePosition(ReadingPosition("block", 1, .5f), completed = false)
    assertEquals(CanonicalChapterId("chapter-100"), progress.lastUpdate.chapterId)
}
```

Also lock:

```text
initial load waits for first persisted preferences AND first chapter graph emission
first routing uses persisted languageOrder
one ViewModel performs one ChapterRepository.observe(storyId) subscription
openChapter does not write CHAPTER_ID_KEY until target commits
selectRelease does not write RELEASE_ID_KEY until selected release commits
successful target commit atomically updates chapter/release/document + saved keys
failed target keeps old committed document + saved keys
initial exhaustion with no committed content -> Unavailable
rapid N -> N+1 -> N+2 leaves N visible until latest valid N+2 commit
stale N+1 cannot change UI/saved keys/progress
exact saved block/offset/fraction restore only when committed release AND fingerprint match persisted progress
same release but changed fingerprint -> no stale exact position restoration
fontScale write failure still rolls back to persisted preference and cancellation still propagates
```

- [ ] **Step 2: Run Feature Reader tests and verify RED on current ViewModel behavior**

```bash
./gradlew :feature:reader:testDebugUnitTest --tests '*ReaderViewModelTest*' --no-daemon
```

Expected current RED includes document clearing and early saved chapter/release mutation.

- [ ] **Step 3: Implement committed/target state and explicit session cutover**

Replace mutable target-as-committed fields with explicit state equivalent to:

```text
CommittedReaderContent?
  chapterId
  releaseId
  document

ReaderTransitionTarget?
  chapterId
  explicitReleaseId?
  generationId
```

`ReaderUiState` may preserve compatibility fields but must represent:

```text
LoadingInitial
Ready(committed)
Transitioning(committed, target)
TransitionFailed(committed, target, retryable)
Unavailable
```

Collect `ChapterRepository.observe(storyId)` once and feed each emission to the session graph state from Task 23. Remove lifetime `cachedChapterGroups` and stop calling `snapshot()` per navigation. Feed persisted preferences to routing; fontScale changes never trigger routing replan.

On commit, update committed chapter/release/document and saved keys together before publishing Ready. Do not persist a pending transition target in v1.

- [ ] **Step 4: Replace the obsolete literal performance guard with the actual session-observation invariant and run regressions**

Update `performance-lifecycle-policy-test.sh` so it no longer requires a field named `cachedChapterGroups`. Instead require one reactive Reader chapter observation and reject per-navigation `chapters.snapshot(storyId)` usage in `ReaderViewModel`:

```bash
grep -q 'chapters.observe(storyId)' "$reader_vm" || fail "Reader no longer owns a session-local reactive chapter graph"
! grep -q 'chapters.snapshot(storyId)' "$reader_vm" || fail "Reader performs a full chapter snapshot per navigation"
! grep -q 'cachedChapterGroups' "$reader_vm" || fail "obsolete lifetime-frozen chapter graph remains"
```

Then run:

```bash
./gradlew :feature:reader:testDebugUnitTest --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
bash scripts/tests/performance-lifecycle-policy-test.sh
```

- [ ] **Step 5: Record checkpoint**

```bash
git add feature/reader/src reader/src app/src/main/kotlin/app/openstory/di/ReaderModule.kt scripts/tests/performance-lifecycle-policy-test.sh
 git commit -m "feat(reader-ui): keep committed content authoritative during transitions"
```

### Task 26: Add bounded session-owned N+1 prefetch through the same engine

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/PrefetchCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`

**Interfaces:**
- Uses `ReaderRouteEngine` with `RoutingIntent.PREFETCH`; no second selector.
- Window: N foreground, at most N+1 prefetch, no proactive N+2/N+3, no proactive N-1 network fetch.
- Process remote prefetch concurrency = 1 and still subject to per-source Reader limiter.

- [ ] **Step 1: Write failing prefetch policy/ownership tests**

Cover:

```text
successful commit N schedules at most one N+1 prefetch
prefetch plan uses PREFETCH and no hedge
OFFLINE/METERED/UNKNOWN disables proactive remote prefetch by default
local inspection/use is still allowed on any network state
UNMETERED may run at most one process remote prefetch
successful valid persistable remote prefetch writes through existing Reader storage rules
non-persistable image prefetch does not create a local-cache fact after completion
foreground navigation to prefetched chapter always assembles a fresh FOREGROUND plan
prefetch completion does not force the prefetched release to semantic primary
same-source foreground preempts Reader prefetch without health penalty
new foreground target cancels obsolete session-owned prefetch
graph update that changes the actual N+1 target cancels/replaces obsolete prefetch without touching committed content
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*PrefetchCoordinatorTest*' --no-daemon
./gradlew :feature:reader:testDebugUnitTest --tests '*ReaderViewModelTest*' --no-daemon
```

- [ ] **Step 3: Implement one bounded prefetch owner**

Prefetch obtains current graph/cache/network/health/preferences and calls the same engine with `PREFETCH`. It validates source results and records valid remote health observations. Persistence remains best effort. It never calls the visible commit gate and never mutates committed Reader UI state.

- [ ] **Step 4: Run Reader/Feature Reader/Downloads regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest :feature:reader:testDebugUnitTest :downloads:testDebugUnitTest --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src feature/reader/src
 git commit -m "feat(reader): prefetch only the next chapter through HES routing"
```

# M6 — One Foreground Hedge and Deterministic Competitive Execution

### Task 27: Add the injected monotonic execution scheduler and virtual-time test scheduler

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionScheduler.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/FakeReaderExecutionScheduler.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderExecutionSchedulerTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- Produces:

```kotlin
interface ReaderExecutionScheduler {
    suspend fun delayMillis(durationMillis: Long)
    fun monotonicNanos(): Long
}
```

- Wall-clock health time remains a separate injected fact/provider.

- [ ] **Step 1: Write failing virtual-time tests**

Assert a delay does not complete before virtual advancement, completes at the exact requested virtual duration, and `monotonicNanos()` never moves backward. Assert wall-clock epoch time is never read from this scheduler by health policy tests.

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderExecutionSchedulerTest*' --no-daemon
```

- [ ] **Step 3: Implement production + fake scheduler**

Production may use coroutine `delay` plus JVM/Android monotonic time. Keep it in `:reader`; engine remains coroutine/time-source free.

- [ ] **Step 4: Wire DI and run GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderExecutionSchedulerTest*' --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src app/src/main/kotlin/app/openstory/di/ReaderModule.kt
 git commit -m "test(reader): add deterministic execution scheduler boundary"
```

### Task 28: Enable pure hedge planning only from REMOTE access evaluations and only when every v1 threshold passes

**Files:**
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/RoutePlanner.kt`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/DefaultReaderRouteEngine.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/HedgePolicyTest.kt`

**Interfaces:**
- Produces zero or one hedge directive.
- Hedge requires primary initial attempt REMOTE and alternate REMOTE source different from primary.
- Alternate threshold uses alternate **REMOTE access score**, never its local-preferred semantic score.

- [ ] **Step 1: Write one failing test for every hedge predicate**

Base case must satisfy all:

```text
intent FOREGROUND
network UNMETERED
initial primary access REMOTE
primary >= 3 remote latency samples
primary p95 >= 1200ms
alternate has eligible REMOTE path
alternate sourceId != primary sourceId
alternate REMOTE access score >= 8000
alternate remote reliability >= 9000
```

Then alter exactly one fact per test and assert hedge is absent. Add explicit regression:

```text
alternate has strong LOCAL-preferred semantic score but REMOTE access score < 8000 -> no hedge
```

Assert default delay exactly `650ms` and PREFETCH never hedges.

- [ ] **Step 2: Run hedge tests and verify RED**

```bash
./gradlew :reader:engine:test --tests '*HedgePolicyTest*' --no-daemon
```

- [ ] **Step 3: Implement deterministic alternate selection**

Pick the highest final-ranked alternate satisfying all REMOTE hedge predicates. Primary and hedge must differ by source ID. Remove the exact hedge REMOTE attempt from the sequential recovery chain so it cannot execute twice. Reassign/validate deterministic attempt IDs from final route order.

- [ ] **Step 4: Run all pure engine tests GREEN**

```bash
./gradlew :reader:engine:test --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src
 git commit -m "feat(reader-engine): plan one remote foreground hedge"
```

### Task 29: Implement competitive primary/hedge execution with completion-time ordering and a single visible commit gate

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionState.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`

**Interfaces:**
- PRIMARY starts immediately. HEDGE starts after directive delay only if primary remains unresolved and intent/revision still active.
- Valid completions record `completedAtNanos` before notification delivery.
- Winner = earliest logical completion; tie PRIMARY; then stable `attemptId`.
- Single serialized commit gate enforces `visible commits per generation <= 1`.

- [ ] **Step 1: Write failing virtual-time competition tests**

Cover:

```text
primary valid before 650ms -> hedge never starts
primary unresolved at 650ms -> exactly one hedge starts
hedge valid @700, primary valid @800 -> hedge wins
primary valid @700, hedge valid @700 -> PRIMARY wins
same role/timestamp tie -> stable attemptId wins
earlier completion record wins even when its notification is delivered later
winner commit best-effort cancels loser
loser cancellation -> Cancellation.HedgeLoser, no health penalty
all competitive attempts terminal -> sequential recovery begins
fallback does not launch just because primary is slow while live hedge remains
concurrent foreground remote count never exceeds 2
total foreground remote attempts never exceeds 4
```

- [ ] **Step 2: Run focused competition tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderCompetitiveExecutionTest*' --no-daemon
```

- [ ] **Step 3: Implement completion registry and atomic semantic commit**

Attempt worker order:

```text
execute source/local effect
-> validate document
-> completedAtNanos = scheduler.monotonicNanos()
-> atomically record valid completion
-> notify coordinator
```

Coordinator chooses from recorded completion facts, not notification order. Before visible commit, validate active `(sessionId, generationId, planRevision)` and that commit gate is open. A second/stale success cannot modify visible state or saved committed identity.

Once client cancellation owns an attempt due to navigation/hedge loss/prefetch preemption, any late transport callback from that cancelled ownership is ignored for health and visible-state classification.

- [ ] **Step 4: Run competitive + routing/session regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderCompetitiveExecutionTest*' --tests '*ReaderRoute*' --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src
 git commit -m "feat(reader): execute one hedge with deterministic single-winner commit"
```

### Task 30: Exhaustively model navigation, graph, replan, health, cancellation, probe, prefetch, and two-session races

**Files:**
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceHealthRegistryTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt`

**Interfaces:**
- Verification task; add no production abstraction unless a failing deterministic model exposes a real boundary defect.

- [ ] **Step 1: Build a deterministic event model with completion and delivery as separate events**

Model at least:

```text
PRIMARY_VALID(t)
PRIMARY_FAILURE(t, scope)
HEDGE_VALID(t)
HEDGE_FAILURE(t, scope)
DELIVER_NOTIFICATION(attempt)
NAVIGATE(chapter)
SELECT_RELEASE(release)
RETRY
GRAPH_REMOVE_RELEASE
GRAPH_ADD_LOWER_CANDIDATE
NETWORK_OFFLINE
SOURCE_OPEN
LOCAL_CONFIRMED_INVALID
LANGUAGE_ORDER_CHANGE
PREFETCH_START
PREFETCH_PREEMPT
HALF_OPEN_LEASE_ACQUIRE/RELEASE
SESSION_B_START
```

- [ ] **Step 2: Add invariants over deterministic seeded interleavings**

Assert every modeled run preserves:

```text
visible commits per generation <= 1
stale generation never commits
stale plan revision never commits
committed saved identity never changes before valid commit
navigation/hedge-loser/prefetch-preempt cancellation never lowers reliability
late normal success while OPEN never closes circuit
held HALF_OPEN probe ownership is unique
fallbacks sequential outside one primary/hedge pair
foreground remote concurrent <= 2
foreground remote total <= 4
process Reader source lane <= 1 active remote per source
hard invalidation increments only plan revision in active intent
new user intent increments generation
soft graph/metric update does not revoke valid plan
two sessions share health but cannot invalidate each other's execution state
exhaustion emits one semantic initial/transition failure, not source-by-source UI churn
```

- [ ] **Step 3: Run model tests and inspect every failure as a correctness defect**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderCoordinatorModelTest*' --no-daemon
```

Do not weaken the invariant or event generator to hide a race.

- [ ] **Step 4: Fix root causes only and rerun model + focused concurrency suites GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderCoordinatorModelTest*' --tests '*ReaderCompetitiveExecutionTest*' --tests '*ReaderSourceExecutionLimiterTest*' --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src
 git commit -m "test(reader): model HES navigation and competitive execution races"
```

# M7 — Golden Verification, Stress, Cleanup, and HES-v1 Freeze

### Task 31: Encode G01-G26 plus HES verification layers L1-L6

**Files:**
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderGoldenScenariosTest.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderPermutationPropertyTest.kt`
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderMetamorphicTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteEngineDifferentialTest.kt`
- Keep runtime golden evidence in: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`, `ReaderCompetitiveExecutionTest.kt`, `ReaderRouteReplanTest.kt`, `ReaderSourceHealthRegistryTest.kt`.

**Interfaces:**
- Golden behavior contract = G01-G26 from R2 design.
- L1 examples, L2 permutation, L3 replay, L4 deterministic properties, L5 metamorphic, L6 migration differential. L7 is Task 30.

- [ ] **Step 1: Encode all 26 named scenarios with exact identifiers**

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

Pure scenarios live in engine tests. Runtime/UI/cache scenarios point to focused runtime tests; do not duplicate slow fixtures merely to put every name in one module.

- [ ] **Step 2: Add deterministic permutation/replay/property suites**

For at least 1,000 seeded routing inputs, assert:

```text
repeated candidate shuffles -> exact decision equality
same snapshot/policy/algorithm repeated -> exact equality
candidate with no usable path never wins
OFFLINE produces no REMOTE attempt
OPEN remote never routes while valid local may route
eligible explicit release is semantic winner
all scores remain in 0..10000
attempt identities unique
LOCAL attempt always has non-blank locator
stable ties resolve by sourceId then releaseId
```

- [ ] **Step 3: Add metamorphic tests and shrink the legacy differential suite to its final valid envelope**

Metamorphic assertions:

```text
adding a disabled candidate cannot change prior eligible winner
improving winner reliability alone cannot make it lose
permuting rejected candidates cannot alter eligible ranking
challenger improvement below hysteresis threshold cannot switch incumbent
removing an unavailable path cannot recreate it
adding unrelated old cache fingerprint cannot turn exact-resume MISS into KNOWN_INVALID
```

Where adaptive final behavior intentionally diverges from old selector, replace old equality assertions with the relevant named golden case. No ignored/disabled differential suite remains.

- [ ] **Step 4: Run L1-L7 aggregate Reader verification GREEN**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src/test reader/src/test
 git commit -m "test(reader): freeze HES-v1 G01-G26 behavior"
```

### Task 32: Add deterministic scale/complexity contracts without brittle wall-clock thresholds

**Files:**
- Create: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRouteEngineStressTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt`

**Interfaces:**
- Pure scale: 50 sources, 500 releases, 1,000 replans, sampled permutations, max-20 health samples.
- Runtime scale: rapid generations/graph revisions/multiple sessions with process health and bounded execution.

- [ ] **Step 1: Write deterministic pure scale tests**

Generate 500 candidates over 50 sources. Across 1,000 replans, assert:

```text
same input remains exactly deterministic
health latency sample state never exceeds 20
planned foreground REMOTE attempts <= 4
no duplicate attempt identity
LOCAL attempts always carry locators
trace does not materialize an all-pairs candidate matrix
```

Use operation counters/test hooks only in test/internal scope if needed to detect accidental O(n²) loops; do not expose them in public engine API.

- [ ] **Step 2: Write runtime scale tests**

Rapidly navigate, hard-replan, update graph revisions, start/cancel prefetch, and run two Reader sessions sharing process health. Assert only active generation/revision can commit and process source lanes remain bounded.

- [ ] **Step 3: Run stress suites**

```bash
./gradlew :reader:engine:test --tests '*ReaderRouteEngineStressTest*' --no-daemon
./gradlew :reader:testDebugUnitTest --tests '*ReaderRuntimeStressTest*' --no-daemon
```

These are scale-contract tests, not host millisecond pass/fail benchmarks.

- [ ] **Step 4: If excess work is observed, remove root-cause pairwise/duplicate work and rerun all Reader tests**

Expected algorithm shape remains:

```text
eligibility/evaluation O(n)
ranking O(n log n)
route construction O(n)
health percentile O(k log k), k <= 20
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/engine/src/test reader/src/test
 git commit -m "test(reader): enforce adaptive routing scale bounds"
```

### Task 33: Remove obsolete selector internals only after explicit session production cutover is proven

**Files:**
- Evidence-driven delete: `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelector.kt`
- Evidence-driven delete: `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelectionResult.kt`
- Evidence-driven reduce/delete: `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelectionPolicy.kt`
- Evidence-driven reduce/delete: `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseCandidate.kt` if it is defined in the policy/result file and no external façade requires it.
- Delete after proof: `reader/src/test/kotlin/app/openstory/reader/selection/ReleaseSelectorTest.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/LegacyReaderRoutingAdapter.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`
- Modify/delete migration-only portions: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteEngineDifferentialTest.kt`

**Interfaces:**
- Removes old ranking code only when no production caller uses it.
- May retain `ReaderLoadRequest` / `ReaderLoadResult` façade and a minimal candidate DTO if external callers/tests still require source compatibility, but no legacy comparator remains in final production routing.

- [ ] **Step 1: Prove actual remaining references before deleting anything**

```bash
rg -n 'ReleaseSelector|ReleaseSelectionResult|ReleaseSelectionPolicy|ReleaseCandidate' \
  --glob '*.kt' reader feature app downloads storage benchmark
```

Classify every hit as production façade, migration test, benchmark fixture, or obsolete implementation. Do not force-delete a DTO still required by a public/benchmark compatibility surface.

- [ ] **Step 2: Add/adjust final façade compatibility tests before deleting ranking internals**

If `ReaderDocumentRepository.load(ReaderLoadRequest)` remains, test that it is a compatibility wrapper and does not become a second production ranking algorithm. Production Feature Reader must exclusively use explicit `ReaderRouteSession` by this point.

- [ ] **Step 3: Delete obsolete comparator/selection internals and migration-only adapter code proven unused**

Final production routing procedure is only:

```text
eligibility -> explicit preference -> access-aware features -> weighted stable rank
-> incumbent/hysteresis -> route construction -> optional hedge -> trace
```

No legacy comparator remains reachable from production Reader UI.

- [ ] **Step 4: Run full Reader/Feature/Downloads/App regression suite GREEN**

```bash
./gradlew :reader:engine:test :reader:testDebugUnitTest :feature:reader:testDebugUnitTest :downloads:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

- [ ] **Step 5: Record checkpoint**

```bash
git add reader/src feature/reader/src app/src downloads/src
 git commit -m "refactor(reader): retire legacy release selection after HES cutover"
```

### Task 34: Freeze HES-v1 architecture/governance evidence and rerun the final Wave 10/HES verification boundary

**Files:**
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`.
- Modify: `scripts/verify-current-architecture.sh`
- Modify: `scripts/tests/verify-current-architecture-test.sh`
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md`
- Keep: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
- Keep: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
- If R0 used acceptance-rebase path: Modify again with fresh final evidence `docs/internal/checkpoints/wave-10-production-remediation.md`.

**Interfaces:**
- Final architecture boundary = 17 production modules + `:benchmark`, Room schema 11.
- Engine remains JVM exact `:core:common`, Reader consumes via implementation, no downstream engine imports, no Reader->settings.
- Produces fresh evidence for G01-G26, architecture, Reader/Downloads/Feature/App/Room regressions, and Wave 10 acceptance if it was intentionally rebased.

- [ ] **Step 1: Add final constitutional/smoke assertions against the implemented tree**

Assert:

```text
:reader:engine platform jvm
exact engine production project dependencies == {:core:common}
reader build uses implementation(:reader:engine), never api
no production engine import outside reader module
no :reader -> :settings edge
17 production modules + :benchmark
Room version remains 11
MIGRATION_10_11 remains registered/unchanged ownership
no MIGRATION_11_12
verifyArchitecture includes engine
shell package/source guard scans reader/engine/src/main
```

Historical static guards must describe current intentional architecture rather than simply exclude new files from scans.

- [ ] **Step 2: Run fresh host verification**

```bash
./gradlew :reader:engine:test \
  :reader:testDebugUnitTest \
  :downloads:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:testDebugUnitTest \
  :build-logic:test \
  --no-daemon

./gradlew verifyArchitecture --no-daemon
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/tests/performance-lifecycle-policy-test.sh
bash scripts/check-wave-10-production-policy.sh
./scripts/verify-fast.sh
```

Then run broader host gates required by the repository/Wave 10 boundary:

```bash
./gradlew test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
```

Do not infer unrun commands are passing.

- [ ] **Step 3: Compile and, where environment provides devices, run Room/App connected gates**

Always compile instrumentation:

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon
```

When API 26/API 37 devices/emulators exist, run the HES-relevant Reader/cache/Room regression plus any Wave 10 final matrix still owed from R0. Record exact devices, API levels, test counts, failures, fixes, reruns.

- [ ] **Step 4: Write evidence/checkpoint and update governance only from actual outputs**

`adaptive-reader-continuity-hes-v1.md` records:

```text
R0/M0-M7 status
accepted/rebased Wave 10 entry decision
commands and exact pass/fail/not-run states
G01-G26 status
architecture/module/source-boundary status
17 production modules + benchmark
Room schema still 11; no migration consumed
ReaderPreferencesPort/ReaderSourceAvailability ownership preserved
trusted sourceGroupKey production fact still absent by design
process health remains in-memory only
known non-blocking follow-ups, if any
```

If R0 deliberately rebased Wave 10 acceptance, Wave 10 may be marked accepted/closed only now if the full required matrix is green on the final HES tree. Otherwise leave it open and state the blocker.

Update `current-state.md` only after evidence reflects actual implementation/acceptance; do not claim HES implemented merely because source files exist.

- [ ] **Step 5: Record final checkpoint**

```bash
git add build-logic/src/test app/src/test scripts docs/project docs/implementation docs/internal/checkpoints
 git commit -m "docs(reader): freeze HES-v1 implementation evidence"
```

---

## Task-to-Spec Coverage Matrix

| R2 design sections | Primary task(s) | Plan coverage |
|---|---|---|
| 1-3 Purpose/rebase authority/R0 | 1-2, 34 | Truthful Wave 10 boundary, close-or-rebase gate, no silent acceptance reuse. |
| 4 Current baseline | 1, 6, 10, 21, 25 | Existing selector/repository/preferences/graph/storage contracts explicitly consumed. |
| 5-8 Goals/non-goals/HES model/classes | 3-34 | Pure reasoner/effect coordinator split and YAGNI constraints applied globally. |
| 9 Module boundary | 3, 34 | JVM module, exact dependency, implementation-only Reader edge, nested source guard. |
| 10-12 Public contracts/versions/fixed point | 4-5, 14 | Engine/reducer contracts and validated integer identities. |
| 13-14 Adapter/candidate model | 5-6, 24 | Chapter models remain outside engine; production group null/completeness 10000/source availability reuse. |
| 15-17 Local locator/cache selection/resume fingerprint | 13, 17, 20-21, 25, 31 | Executable fingerprint locators, deterministic batch metadata choice, no remote fingerprint oracle, exact restoration guard. |
| 18 Reactive graph | 23, 25 | One session observation, revisions, hard/soft invalidation, performance guard replacement. |
| 19-20 Continuity/incumbent | 19, 24-25 | Committed vs target resume continuity and deterministic incumbent order. |
| 21-24 Snapshot/intent/network/language | 4-5, 16, 22, 24-25 | Explicit time/graph/network/preferences, FOREGROUND/PREFETCH, ordered/strict language. |
| 25-35 Pipeline/eligibility/features/ranking/hysteresis/route/confidence | 17-20, 28 | Full staged access-aware algorithm and hedge-independent remote evaluation. |
| 36-39 Health/probes/process registry | 14-16, 24, 30 | EWMA/circuit, late normal success rule, held probe leases, process-shared in-memory state. |
| 40-41 Typed observations/current code taxonomy | 12-14 | Exact classification table/inventory; Wave 10 auth protected from reliability penalties. |
| 42 Source limiter | 15, 26, 29-30 | Process per-source Reader remote lane, foreground priority, prefetch preemption. |
| 43-46 Session/generation/state/commit | 9, 11, 24-25, 29-30 | Per-screen execution state, new generation per intent, one revision, one visible commit. |
| 47 Validation | 13, 21 | Explicit local locator validation, quarantine boundary, cache write best effort. |
| 48-49 Hedge/competition determinism | 27-30 | Virtual scheduler, pure hedge thresholds, completion-record winner semantics. |
| 50 Prefetch | 26 | Same engine, N+1 bound, no default hedge, fresh foreground replan. |
| 51-52 UI continuity/states | 25 | Committed/saved/progress identity remains authoritative through transitions. |
| 53-55 compatibility/selector/Wave 10 ownership | 6-11, 25, 33-34 | Compatibility without fake target, explicit session cutover, settings/migration ownership preserved. |
| 56 Architecture gates | 3, 25, 34 | Gradle + shell/static constitutional enforcement and updated lifecycle guard. |
| 57 Verification layers | 8, 30-32 | L1-L7 fully owned. |
| 58 Golden scenarios | 31 | G01-G26 named and mapped to pure/runtime suites. |
| 59 Runtime invariants | 15, 23-30 | Staleness, local independence, cancellation, session sharing, UI exhaustion contracts. |
| 60-61 Stress/performance/observability | 32, 34 | Deterministic scale bounds; no brittle host timing gate. |
| 62 Policy validation | 4, 14, 28 | Constructor/factory validation and exact policy ceilings. |
| 63-66 API/persistence/trace/replay | 5, 14-16, 31, 34 | Small engine API, no health persistence, structured trace, exact replay. |
| 67 Migration sequence | 1-34 | Exact R0/M0-M7 ordering. |
| 68 Acceptance criteria | 1-34 | See AC matrix below. |
| 69-70 ownership/invariants | 1-34 | Wave 10/canonical ownership preserved and final evidence governs status. |
| SR-01..SR-32 | 1-34 | Explicit contradiction mapping in Self-Review Record. |

## Acceptance-Criteria Coverage (R2 §68)

| AC | Task(s) | Proof |
|---:|---|---|
| 1 | 1-2, 34 | Wave 10 accepted before HES or explicitly rebased with fresh final evidence. |
| 2 | 3, 34 | Pure JVM `:reader:engine`, exact `:core:common`. |
| 3 | 3, 34 | Forbidden imports enforced by Gradle + shell nested-source scan. |
| 4 | 3, 6, 34 | Reader implementation dependency only; no downstream engine transport. |
| 5 | 3, 34 | 17 production modules; Room still 11. |
| 6 | 21, 34 | No schema/migration change; `MIGRATION_10_11` preserved. |
| 7 | 24-25, 34 | Existing `ReaderPreferencesPort` is sole routing preference owner. |
| 8 | 23-25 | Initial load waits for first preferences + first graph emission. |
| 9 | 6, 24, 34 | Existing `ReaderSourceAvailability` reused; no duplicate port. |
| 10 | 6, 24 | Production `sourceGroupKey = null`. |
| 11 | 6, 24 | Production completeness = 10000. |
| 12 | 18, 31 | Exact decision permutation stability. |
| 13 | 16, 31 | Replay equality. |
| 14 | 5, 17, 20-21 | Every LOCAL attempt has a fingerprint. |
| 15 | 21 | One metadata batch; no N document decode. |
| 16 | 21, 31 | Other historical fingerprints yield MISS, never false corruption. |
| 17 | 17-20, 31 | Exact local can route with disabled/OPEN remote. |
| 18 | 13, 25, 31 | Valid changed remote fingerprint may commit. |
| 19 | 25, 31 | No exact stale restoration across changed fingerprint. |
| 20 | 17, 22, 31 | OFFLINE rejects only REMOTE. |
| 21 | 17, 19, 31 | Explicit eligible wins but hard path/language rules remain. |
| 22 | 4, 17, 31 | Strict language rejects unlisted. |
| 23 | 19, 24, 31 | Target resume release is strongest non-explicit incumbent when eligible. |
| 24 | 19, 31 | Hysteresis thresholds. |
| 25 | 14 | Third qualifying failure opens; one/two do not. |
| 26 | 15, 17, 24, 31 | HALF_OPEN requires held probe lease. |
| 27 | 14-15, 30 | Late normal success cannot close OPEN. |
| 28 | 12, 14, 31 | Auth/credential/config failures non-penalizing. |
| 29 | 12, 14, 26, 29-30 | Navigation/hedge/prefetch cancellations non-penalizing. |
| 30 | 14-15, 30 | READ_DOCUMENT-keyed, process-shared health. |
| 31 | 15, 34 | New process registry starts neutral. |
| 32 | 9, 25, 30 | Per-screen execution state isolated. |
| 33 | 9, 24-25, 30 | New generation per foreground user intent. |
| 34 | 9, 23-24, 30 | Hard external invalidation uses one plan revision. |
| 35 | 20, 24 | Attempt failures consume recovery chain instead of blind replan. |
| 36 | 9, 24-25, 29-30 | Stale generation/revision cannot mutate UI/saved state. |
| 37 | 29-30 | One visible commit per generation. |
| 38 | 25 | Committed content/progress remains authoritative during transition. |
| 39 | 25 | Failed transition keeps prior content/saved keys. |
| 40 | 23-24, 30 | Active release removal hard invalidates; lower-ranked addition soft. |
| 41 | 23, 25 | One reactive session graph, no per-navigation snapshot/lifetime freeze. |
| 42 | 28-30 | At most one primary + hedge. |
| 43 | 15, 20, 26, 29-30 | Four total remote / one source lane / bounded concurrency. |
| 44 | 27, 29-30 | Logical completion time then PRIMARY then attempt ID. |
| 45 | 26 | Same engine, N+1, fresh foreground snapshot, no authority-by-prefetch. |
| 46 | 26, 31 | Image/non-persistable prefetch never becomes false local hit. |
| 47 | 25, 30 | UI emits semantic failure only, no source flicker. |
| 48 | 31 | G01-G26. |
| 49 | 32 | Bounded state/O(n log n) scale contract. |
| 50 | 3, 34 | Gradle + shell engine source coverage. |
| 51 | 1, 25, 34 | Historical guards updated to intentional current architecture. |
| 52 | 25-34 | Reader/Downloads/Feature/App/Room final applicable matrix. |

## R2 Design Self-Review Contradiction Coverage

| R2 self-review item | Primary task(s) | Resolution in this plan |
|---|---|---|
| SR-01 stale repository-status baseline | 1-2 | Rebase current-state/roadmap/static truth before HES. |
| SR-02 unaccepted Wave 10 boundary would move | 2, 34 | Close before HES or explicitly rerun acceptance on final HES tree. |
| SR-03 engine type leakage through Reader API | 3, 6, 21-22 | `implementation`, Reader-owned effect DTOs, downstream engine-import ban. |
| SR-04 nested engine source guard hole | 3, 34 | RED shell mutation under `reader/engine/src/main` and explicit scan. |
| SR-05 cache status lacked executable locator | 5, 17, 20-21 | Every LOCAL fact/attempt owns exact fingerprint. |
| SR-06 metadata mismatch != corruption | 13, 21, 24 | Other fingerprints are MISS; invalid requires actual bad local read. |
| SR-07 automatic-cache choice nondeterministic | 21, 31 | `lastAccessed DESC`, fingerprint ASC tie rule. |
| SR-08 resume fingerprint misused as provider truth | 13, 25, 31 | Remote changed fingerprint may commit; restoration requires exact release+fingerprint. |
| SR-09 hard rejections referenced nonexistent facts | 5, 17 | Only R2 language/access rejections are implemented. |
| SR-10 remote health downgraded valid local | 18-20, 31 | Preferred LOCAL receives local access features independent of remote health. |
| SR-11 hedge score inflated by local cache | 18, 28 | Separate REMOTE access evaluation drives hedge threshold. |
| SR-12 lifetime `cachedChapterGroups` contradicted replan | 23, 25 | One reactive graph observation per session. |
| SR-13 reactive graph could regress performance | 23, 25 | No per-navigation snapshot; static guard verifies observation invariant. |
| SR-14 Wave 10 initial preference ordering regression | 25 | Initial intent gates on first preferences + first graph. |
| SR-15 duplicate source availability | 24, 34 | Existing `ReaderSourceAvailability` only. |
| SR-16 Wave 10 auth failures poison health | 12, 14, 31 | Exact non-penalizing credential classifications. |
| SR-17 per-object source mutex insufficient | 15, 30 | Process `sourceId` execution limiter. |
| SR-18 late normal success could close OPEN | 14, 30 | Attempt-origin-aware reducer; only probe closes cycle. |
| SR-19 HALF_OPEN boolean race | 15, 24 | Held process probe leases precede boolean engine fact. |
| SR-20 user intent vs hard replan generations | 9, 24, 30 | User intent => generation; external invalidation => revision. |
| SR-21 `selectRelease()` saved state mutated early | 25 | Saved release changes only at atomic commit. |
| SR-22 progress owner followed mutable target | 25 | Progress always uses committed identity until commit. |
| SR-23 non-persistable prefetch false cache hit | 26, 31 | Execution success separated from reusable local fact. |
| SR-24 Reader network policy could consume Wave 10 background policy | 22 | Reader-only network DTO/adapter; no settings policy reuse. |
| SR-25 cache quota currently unwired | Global, 21 | Explicit non-goal; cache routing does not repair quota ownership. |
| SR-26 legacy façade lacked real target identity | 10-11, 25 | Legacy façade stays separate; explicit session uses real target. |
| SR-27 decision reason categories overloaded | 5, 16 | Separate decision/access/rejection/diagnostic types. |
| SR-28 source health after client cancellation ambiguous | 29-30 | Cancellation ownership suppresses late health classification. |
| SR-29 repository static baseline not globally green | 1-2 | Historical guards repaired before HES. |
| SR-30 local I/O and corruption conflated | 13 | Quarantine only confirmed exact locator corruption. |
| SR-31 cache persistence became semantic success dependency | 13, 26 | Non-cancellation cache write failure is best effort. |
| SR-32 scope inflation | Global, 33-34 | No cache-quota/schema/other-engine/framework expansion. |

## Execution Checkpoints

```text
Checkpoint R0: Tasks 1-2 — truthful Wave 10 boundary and explicit acceptance decision
Checkpoint A: Tasks 3-5 — pure module + policy/fact constitution green
Checkpoint B: Tasks 6-8 — compatibility reasoner + differential green
Checkpoint C: Tasks 9-11 — session/coordinator compatibility boundary green
Checkpoint D: Tasks 12-16 — typed observations/validation/process health/trace green; adaptive ranking not active
Checkpoint E: Tasks 17-24 — adaptive routing + cache/network/reactive graph/replan green; hedge disabled
Checkpoint F: Tasks 25-26 — explicit production session, zero-blank transition, prefetch green
Checkpoint G: Tasks 27-30 — one hedge + competitive/concurrent model green
Checkpoint H: Tasks 31-34 — G01-G26, stress, cleanup, final architecture/acceptance evidence green
```

No production behavior crosses a checkpoint merely because later source files exist; the checkpoint test/evidence gate controls activation.

# Self-Review Record — Plan vs R2 Design and Current Wave 10 Tree

The complete plan was re-read against all R2 design sections, all 52 acceptance criteria, the 32 R2 design contradiction records, and the supplied Wave 10 source paths. The following plan-level contradictions/gaps were found and corrected inline before handoff.

## PR-01 — R0 was missing from the old plan execution graph

**Problem:** The old plan started by adding `:reader:engine` while Wave 10 final acceptance was still open. That would move the module/dependency/UI/Room-query boundary before closing the checkpoint that still owned those regressions.

**Resolution:** Tasks 1-2 are mandatory R0. They either close Wave 10 on the untouched 16-module/schema-11 boundary or explicitly record an acceptance rebase requiring fresh final evidence on the HES tree.

## PR-02 — Governance repair cannot mark Wave 10 accepted

**Problem:** Correcting stale `current-state.md` could be mistaken for closing the wave.

**Resolution:** Task 1 records `IMPLEMENTATION PRESENT; FINAL ACCEPTANCE OPEN`. Only Task 2/34 may close the checkpoint, and only from actual host/API 26/API 37 evidence.

## PR-03 — Historical architecture test still expected 15 modules while source had 16

**Problem:** The old plan treated architecture gates as green baseline assumptions.

**Resolution:** Task 1 repairs `verify-current-architecture-test.sh` before HES; Task 3 then deliberately advances that verified count from 16 to 17.

## PR-04 — Old source-hygiene policy prohibited the now-real Settings route

**Problem:** Leaving the guard untouched would make final verification fail for a Wave 10 feature unrelated to HES.

**Resolution:** Task 1 requires positive `Settings` presence and continues forbidding only the future `Plugins` placeholder; it does not weaken the policy by excluding navigation files.

## PR-05 — Old performance P4 guard banned any `Application.onCreate`, conflicting with Wave 10

**Problem:** Simply deleting the guard would remove useful startup protection.

**Resolution:** Task 1 converts it to exact bounded Wave 10 startup ownership checks and explicit blocking/heavy-work prohibitions.

## PR-06 — `:reader:engine` must not be re-exported through `api`

**Problem:** The old plan made Reader API-expose engine DTOs to App/Downloads.

**Resolution:** Task 3 requires `implementation(project(":reader:engine"))`, Task 6 adds a no-downstream-engine-import assertion, and Reader-owned DTOs back cache/network ports.

## PR-07 — Nested `reader/engine/src/main` could escape shell source verification

**Problem:** Current Reader root scans do not automatically prove the nested module is scanned.

**Resolution:** Task 3 requires a RED mutation fixture under the nested source root before modifying `verify-package-boundaries.sh`.

## PR-08 — Legacy façade has no trustworthy target chapter identity

**Problem:** The old plan invented a deterministic synthetic chapter ID to force `ReaderLoadRequest` through the engine.

**Resolution:** Tasks 10-11 keep the legacy façade separate and introduce a real-target explicit session API. Production engine cutover happens in Task 25 with real `CanonicalChapterId`.

## PR-09 — Production completeness/source-group facts were still inherited from legacy DTOs

**Problem:** That would manufacture facts current `ChapterRelease` does not own.

**Resolution:** Tasks 6 and 24 hard-lock production `completeness = 10_000` and `sourceGroupKey = null`; only differential fixtures can inject richer synthetic facts.

## PR-10 — Cache state without a fingerprint is not executable

**Problem:** Automatic cache reads require exact `(releaseId, fingerprint)`.

**Resolution:** Tasks 5, 17, 20, 21 require every routable LOCAL fact/attempt to carry a concrete locator and validate that invariant at construction.

## PR-11 — Resume fingerprint mismatch was still being treated as local corruption in metadata selection

**Problem:** Another cached fingerprint for the same release does not prove the resume fingerprint is corrupt.

**Resolution:** Task 21 maps exact resume miss to `MISS`; `KNOWN_INVALID` is only produced after actual local read/decode/validation evidence.

## PR-12 — DAO filtering could erase the newest non-COMPLETED explicit row and resurrect an older download

**Problem:** A query filtering only completed rows before selection violates R2 step 3.

**Resolution:** Task 21 explicitly queries relevant rows without filtering away explicit state, then applies newest explicit row semantics in the adapter.

## PR-13 — Cache batch query could accidentally require schema 12/index changes

**Problem:** The existing table already has a release-ID index and required fields.

**Resolution:** Task 21 permits only DAO/query/adapter work; final grep/evidence rejects Room version 12 or `MIGRATION_11_12`.

## PR-14 — Progress fingerprint was still usable as a remote integrity oracle in old executor logic

**Problem:** Legitimately revised remote content could be rejected forever.

**Resolution:** Task 13 compares fingerprint only for exact LOCAL locator reads. Task 25 restores exact position only on release+fingerprint match; changed valid remote fingerprint may commit.

## PR-15 — Generic local I/O exceptions were conflated with corruption/quarantine

**Problem:** Current repository quarantines requested fingerprint after any non-cancellation local read exception.

**Resolution:** Task 13 intentionally changes that behavior: quarantine only confirmed decode/fingerprint corruption; other local/storage failures are local/client failures and remote recovery continues.

## PR-16 — Cache write failure could accidentally convert valid remote content to a load failure

**Problem:** Routing correctness must not depend on best-effort automatic-cache persistence.

**Resolution:** Task 13 orders validation/semantic success independently from non-cancellation cache write failure.

## PR-17 — Source availability could be duplicated

**Problem:** Creating a new HES-enabled-source port would conflict with current `ReaderSourceAvailability`.

**Resolution:** Task 24 consumes the existing port. No new enabled-source interface appears in Locked File Structure.

## PR-18 — Wave 10 auth failures could be collapsed into retryable transport failures

**Problem:** `plugin.auth_unavailable` / `plugin.http_credentials_failed` could OPEN healthy sources.

**Resolution:** Task 12 exact-classifies them as non-penalizing credential failures and adds an inventory test so future reachable codes cannot silently bypass the table.

## PR-19 — Broad prefix classifier could hide new runtime semantics

**Problem:** `plugin.http_*` prefix matching would treat policy/config/auth/network failures the same.

**Resolution:** Task 12 uses exact code entries and a reachable-code inventory test. Unknown fallback requires explicit remote invocation context.

## PR-20 — Health state race: late normal success after OPEN could close the circuit

**Problem:** Reducer ordering could grant circuit authority to a normal in-flight request.

**Resolution:** Task 14 adds attempt origin and explicit test that OPEN-cycle closure is reserved for successful held HALF_OPEN probes.

## PR-21 — HALF_OPEN permission as a boolean check has TOCTOU race

**Problem:** Multiple sessions could both observe “permitted” before execution.

**Resolution:** Task 15 owns real process probe leases; Task 24 passes only the already-held lease fact to engine and releases unused leases.

## PR-22 — Per-source object mutex is not a process Reader limiter

**Problem:** `enabled()` may create new source objects.

**Resolution:** Task 15 introduces process-wide `sourceId` lanes independent of source object identity and tests two sessions/source instances.

## PR-23 — Remote health could downgrade a valid local copy

**Problem:** Candidate scoring that always used remote health would make downloaded content lose while source is OPEN.

**Resolution:** Task 18 gives LOCAL preferred access neutral/max local access features and keeps a separate remote evaluation only for remote routing/hedge.

## PR-24 — Hedge could use a semantic score inflated by local cache

**Problem:** A cached alternate might pass hedge threshold even though its REMOTE route is weak.

**Resolution:** Task 18 records separate remote access evaluation and Task 28 tests local-strong/remote-weak alternate does not hedge.

## PR-25 — Lifetime `cachedChapterGroups` and per-navigation `snapshot()` are both wrong endpoints

**Problem:** One freezes routing facts forever; the other violates performance intent.

**Resolution:** Tasks 23/25 use one `ChapterRepository.observe(storyId)` subscription per screen/session and replace the literal static guard with the actual performance invariant.

## PR-26 — Reactive graph label-only emissions could churn plan revisions

**Problem:** Treating every emission as hard invalidation would cause replan storms.

**Resolution:** Task 23 distinguishes routing-relevant hard invalidation from lower-ranked/metadata soft updates and tests post-commit graph changes do not blank content.

## PR-27 — Initial graph readiness could race initial persisted preferences

**Problem:** Whichever flow emitted first could trigger selection with defaults/stale facts.

**Resolution:** Task 25 explicitly gates initial foreground intent on both first preference and first graph emissions.

## PR-28 — User intent and hard replan could share one generation boundary ambiguously

**Problem:** Explicit selection/retry/navigation should not look like environment-driven plan revision.

**Resolution:** Tasks 9/24 hard-lock new generation for every foreground user intent; only external invalidation of an active uncommitted intent increments plan revision.

## PR-29 — `selectRelease()` could still overwrite committed saved release before success

**Problem:** Current UI does this today.

**Resolution:** Task 25 tests and removes all early CHAPTER_ID_KEY/RELEASE_ID_KEY mutation; only atomic commit writes committed saved identity.

## PR-30 — Progress owner could still follow transition target

**Problem:** Current `updatePosition()` uses mutable `chapterId`.

**Resolution:** Task 25 ties progress updates to committed chapter/release/document until replacement commit.

## PR-31 — Non-persistable image prefetch could become a false cache hit

**Problem:** Successful prefetch execution is not equivalent to stored reusable content.

**Resolution:** Task 26 separates source execution success from local-cache fact creation and adds explicit image/non-persistable regression.

## PR-32 — Foreground Reader network policy could accidentally reuse Wave 10 background `requireUnmeteredNetwork`

**Problem:** Different product policies/owners would be coupled.

**Resolution:** Task 22 creates a narrow Reader network-fact adapter only; no settings/background network-policy dependency is introduced.

## PR-33 — Current cache quota setting is not actually wired into Reader store

**Problem:** HES could expand scope by trying to fix that while touching cache composition.

**Resolution:** Global non-goal explicitly forbids cache-quota wiring; Tasks 21/34 verify no such ownership expansion is required for routing.

## PR-34 — Completion notification order could still choose hedge winner

**Problem:** Coroutine delivery ordering is hidden policy.

**Resolution:** Tasks 27/29 separate monotonic completion recording from notification delivery and test reversed delivery ordering.

## PR-35 — Client-cancelled attempt could later mutate health

**Problem:** A plugin ignoring cancellation may return a late source result.

**Resolution:** Task 29 declares cancellation ownership terminal for health classification of that attempt; Task 30 models late callback cases.

## PR-36 — Prefetch and foreground could exceed same-source/process limits independently

**Problem:** Separate coordinators without a shared lane would violate limits.

**Resolution:** Task 15 owns shared lanes; Task 26 uses them; Task 30 asserts process-wide constraints across two sessions and prefetch.

## PR-37 — Old plan's cache adapter exposed engine DTOs through Downloads/App

**Problem:** That violates R2 dependency exposure rule.

**Resolution:** Task 21 defines `ReaderLocalCacheFact` in `:reader`; Downloads/Room implement Reader SPI/DTOs only. `RouteSnapshotAssembler` performs the only Reader DTO -> engine fact conversion.

## PR-38 — App network adapter could expose engine enum directly

**Problem:** That would make App an engine transport consumer despite no engine dependency.

**Resolution:** Task 22 uses Reader-owned `ReaderNetworkState`; assembler maps it internally to engine `ReaderNetworkClass`.

## PR-39 — Legacy selector cleanup could delete still-public request DTOs prematurely

**Problem:** `ReleaseCandidate` may still be part of compatibility façade/benchmark tests.

**Resolution:** Task 33 is evidence-driven: delete ranking internals, retain minimal DTOs only if `rg` proves caller compatibility still needs them.

## PR-40 — Final Wave 10 acceptance could be falsely inherited from R0 if HES was an acceptance rebase

**Problem:** R0's deliberate-rebase branch leaves Wave 10 evidence open.

**Resolution:** Task 34 explicitly reruns/updates the Wave 10 checkpoint on the final HES tree when required and refuses to close it without the complete matrix.

## PR-41 — Plan had to prove all 52 ACs, not merely mirror milestones

**Problem:** Milestone coverage can hide acceptance gaps.

**Resolution:** The AC matrix maps every R2 criterion 1-52 to concrete tasks/tests/evidence.

## PR-42 — Placeholder/no-detail risk in a very large plan

**Problem:** A task saying “handle errors/tests appropriately” would not be executable by a fresh agent.

**Resolution:** Every production task names files, contracts, RED behavior, exact policy semantics, verification commands, and checkpoint. No placeholder marker, “similar to” shortcut, or unresolved design decision is intentionally left in the plan.

## Final Plan Invariants

```text
R0 is explicit and evidence-based.
Wave 10 ownership is never silently superseded.
HES engine types stop at :reader.
Every LOCAL route has an executable fingerprint locator.
Resume fingerprint controls exact local/restoration identity, not remote validity.
Reader graph facts are reactive per screen without per-navigation full snapshots.
Reader source availability is reused, not duplicated.
Remote health never poisons valid local access.
Auth/config/cancellation/cache failures do not poison source reliability.
HALF_OPEN authority is lease-owned, not inferred from a racy boolean check.
New user intent => new generation.
External hard invalidation => one plan revision increment.
One generation => at most one visible commit.
Committed content owns progress/saved identity until replacement commit.
Prefetch is bounded/advisory.
Hedge uses REMOTE access facts and logical completion time.
Room remains schema 11.
Final status comes from fresh verification evidence, not source presence.
```
