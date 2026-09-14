# Hikari V2 Step 3 - Base App UX/UI Completion

Date: 2026-09-14
Status: **TASK 2 COMPLETED/ACCEPTED**

## Authority

- Design: `../../superpowers/specs/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.5.md`
- Decision traceability audit: `../v2/2026-09-13-hikari-v2-step-3-R1.5-decision-traceability-final-audit.md`
- Implementation plan: `../../superpowers/plans/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-implementation-plan-R1.1.md`
- Accepted predecessor: `hikari-v2-step-2-discover-story-foundation.md`
- Completed/accepted execution boundary: Tasks 0-2.
- Current execution boundary: Task 3 is next. Task 3 was not authorized or started in the Task 2
  closure turn.

Reviewed artifact SHA-256:

- design R1.5: `d96572756782c225fb623afc0fc69492c520f36da8a2cfdd487ec49b2252ddec`
- plan R1.1: `c7b416179a22b1a67cc9f1a6c26a4220c6c9d0249b4ee80c64a1a714a223ad2a`
- decision audit: `c62478e35ccd045a3d1e18fc65c74aa35a8981ad90daa91ed900d7e18ecebcad`
- immutable Step 2 module policy: `7bc78c1aed8655bff66685351d8063f6c1c171142b77697bac58cb2682e78aac`

## Task 0 Delta

- Persisted the approved R1.5 design, R1.1 implementation plan, and final decision-traceability
  audit at their canonical repository paths.
- Copied the accepted Step 2 module-boundary policy verbatim to
  `config/architecture/history/step2-module-boundaries.json`; the live policy remains the current
  graph authority while the archived policy remains exact Step 2 evidence.
- Replaced the live exact-graph `verifyStep2BuildSurface` task with
  `verifyStep3BuildSurface`. The Step 3 verifier accepts live graph growth while retaining release
  fixture/plugin-harness isolation, Design System runtime isolation, allowlisted Room/artwork
  ownership, app composition/navigation import scopes, and `:core:network`-only HTTP ownership.
- Derived production package-cycle inputs from the current `:app` production dependency cone
  instead of `STEP2_MODULE_DIRECTORIES`. The package resolver now resolves declared package/type
  ownership and does not misclassify external-module imports below an app-owned package prefix.
- Registered `verifyStep3FastModules` and `verifyStep3FullModules` from the evaluated subproject
  graph. JVM modules contribute `test`; Android app/library modules contribute debug unit and
  assemble gates; the full aggregate adds release assembly and lint; Android test/benchmark
  modules remain outside these host aggregates.
- Evolved the app structural ratchet to schema v2 with total `<=1800` production Kotlin lines and
  prefix budgets: startup `<=320`, navigation `<=800`, composition `<=500`, and remaining app
  UI/root `<=300`.
- Replaced the Step 2 shell entrypoint with `scripts/tests/v2-step3-build-surface-test.sh` and made
  `scripts/verify-fast.sh` / `scripts/verify.sh` consume the dynamic Step 3 aggregates.

No production dependency, manifest permission, application source, or runtime behavior changed.

## Agent-Owned Evidence

- Focused RED: filtered build-logic compilation failed because `Step3BuildSurfaceVerifier`,
  `verifyGraph`, dynamic aggregate mapping, and prefix-budget policy support did not exist.
- Focused GREEN: 31 filtered Step 3 surface/foundation/plugin tests passed.
- Expanded changed-cone GREEN: Step 2/Step 3 surface, foundation loader/ratchet, architecture
  plugin, and module-graph tests passed in 35s.
- Package resolver regression GREEN: package-prefix and app production-cone tests passed in 46s.
- Navigation-owner RED -> GREEN: a non-app Navigation 3 dependency/import initially escaped the
  scope gate; the focused regression now rejects both, and the complete Step 3 verifier test class
  passes.
- `./gradlew verifyStep3BuildSurface verifyProductionPackageStructure --no-daemon` - PASS in 19s;
  Step 3 surface passed and the package gate covered 7 production-reachable modules.
- `./gradlew tasks --all --no-daemon` - PASS; `verifyStep3BuildSurface`,
  `verifyStep3FastModules`, and `verifyStep3FullModules` are registered.
- Fresh final agent gate after the navigation-owner correction:
  `./gradlew :build-logic:test :app:verifyFoundation verifyStep3BuildSurface
  verifyProductionPackageStructure --no-daemon` - PASS in 32s, 55 tasks; all four merged-manifest
  checks passed and the package gate covered 7 production-reachable modules.
- `./gradlew verifyArchitecture verifyStep3FastModules verifyStep3FullModules --dry-run
  --no-daemon` - PASS in 22s; all dynamic task paths resolved without executing the broad gates.
- Bash syntax/static entrypoint checks are `NOT RUN`: this Windows environment resolves `bash` to
  WSL, but `/bin/bash` is unavailable.

## Preserved Step 2 Evidence And Debt

- Step 2 Tasks 0-18 remain `COMPLETED/ACCEPTED`; final runtime/source SHA remains
  `13af96625a93b3e45f7d7db18e539286ce075c79`.
- Baseline/Startup Profile fixture evidence remains 20,874 byte-identical rules per file with
  SHA-256 `797b58730c732777698f3aba24dc6ea11302cd8bfa4b17e75c351568f4ac54eb`.
- Accepted no-growth performance debt remains visible: Story Detail CPU/overrun P95 memory-hit
  `47.175 / 40.432 ms`, disk-hit `41.906 / 41.678 ms`, Story-back `66.212 / 59.902 ms`, startup TTID
  fresh `507.138 ms`, and returning `467.346 ms`.

## Task 0 Self-Review

- Live architecture no longer compares the mutable Step 3 graph to an exact Step 2 module set;
  the archived policy and Step 2 graph tests preserve that historical evidence independently.
- Dynamic package and broad module aggregates cannot silently omit a newly included production
  module. Benchmark/device work remains separately user-owned.
- App runtime/source imports are admitted only through `app/openstory/composition/**`; storage
  remains forbidden everywhere in app, and Navigation 3 imports are admitted only through
  `app/openstory/navigation/**`.
- HTTP client dependency/import ownership is reserved for `:core:network`; the unchanged current
  graph contains no production remote transport or INTERNET permission.
- The total and prefix structural caps are finite and independently enforced; current app sources
  remain within startup `270/320`, remaining `65/300`, and total `335/1800`.
- No Step 3 product capability, provider, network permission, database, navigation runtime, or UI
  behavior was implemented by Task 0.

## Task 1 Delta

- App Shell now owns Manga/Home/Light Novel top-level navigation through three serializable
  Navigation3 back stacks while composing only the selected root `NavDisplay`.
- Home is the cold default after the unchanged `Unknown -> FirstRun -> Ready` and first-frame gate.
  Its temporary local shell exposes only real Explore Manga / Explore Light Novels actions and
  constructs no Catalog root while Home is selected.
- Route wires contain only `entryId` plus app-owned media enum values. `RouteEntryId` validates
  decoded wire values, and route history enforces the `32` process / `12` per-root policy.
- Catalog root ownership is immutable-media. Manga and Light Novel use distinct keyed
  `DiscoverViewModel` owners even when they share one Activity `ViewModelStore`.
- `DiscoverUiState.selectedMediaType`, `selectMedia`, the Catalog-owned top-level nav, its tests,
  tags, and obsolete layout metrics are removed. Discover receives media as an immutable boundary
  value instead.
- App build/runtime governance admits Kotlin serialization, Navigation3 runtime/ui `1.1.4`, and
  the direct app-to-`:catalog:domain` codec boundary approved by Task 0.

## Task 1 Agent-Owned Evidence

- Navigation RED: focused app test compilation failed on the absent app route/state types; the
  initial test harness defect (`Assert.assertFailsWith`) was corrected to JUnit 4 `assertThrows`.
- Startup/fixed-media RED: focused tests failed on the absent startup destination resolver,
  fixed-media constructor, and media-specific ViewModel key.
- State-bound regression RED: restored child stacks were rejected and a root-only policy threw
  `IndexOutOfBoundsException`; both focused regressions now pass.
- Fresh final focused command:
  `./gradlew :app:testDebugUnitTest --tests app.openstory.AppShellContractTest --tests
  app.openstory.navigation.AppRouteTest --tests app.openstory.navigation.AppNavigationStateTest
  --tests app.openstory.startup.StartupDestinationTest :feature:catalog:testDebugUnitTest --tests
  app.openstory.catalog.feature.discover.DiscoverViewModelTest --tests
  app.openstory.catalog.feature.discover.DiscoverRefreshStateTest
  :feature:catalog:compileDebugAndroidTestKotlin :app:assembleDebug :app:verifyFoundation
  verifyStep3BuildSurface --no-daemon` - PASS in 18s, 177 tasks.
- Focused unit result: 33 tests, 0 failures/errors. Four merged-manifest startup checks and the Step
  3 build-surface verifier also passed.

## Task 1 Self-Review

- Production search finds no `PersistentTopLevelNavDisplay`, `selectedMediaType`, `selectMedia`,
  `CatalogMediaDestinationNav`, or Navigation3 ViewModel-store decorator.
- Navigation3 `1.1.4` bytecode confirms the selected `NavDisplay` overload supplies only its
  default saveable-state holder decorator. App Shell adds root-keyed `SaveableStateHolder` state,
  preserving lightweight root UI context without retaining inactive root compositions.
- Root Back never traverses tab history; reselect affects only the selected root stack and does not
  issue a refresh intent.
- App route serialization contains no Catalog domain object. Media conversion occurs only at the
  Catalog composition boundary.
- Fixed-media owners cannot cross-observe or cross-refresh Manga/Light Novel state, including when
  both keys live in one shared `ViewModelStore`.
- `git diff --check` reports no whitespace errors. Existing generated baseline/startup profile
  outputs were intentionally not edited.

## Task 1 User-Owned Evidence

Status: **PASS**

Required broad gates:

```bash
./gradlew :build-logic:test verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface --no-daemon
bash scripts/tests/v2-step3-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

User-reported verification reviewed on 2026-09-14:

- The architecture/foundation/Step 3 aggregate command passed in 25s with 57 actionable tasks.
- `scripts/tests/v2-step3-build-surface-test.sh` passed in 21s with 7 actionable tasks.
- The initial fast/full sequence then exposed a stale Step 1 static assertion that still classified
  the Task 1-required `navigation3 =` catalog entry as inactive. Because the repository entrypoints
  use `set -e`, `verify-fast.sh` stopped at that assertion and `verify.sh` was not reached.
- A focused fixture regression reproduced the contradiction. The legacy gate now permits the
  explicitly admitted Navigation3 surface while retaining every other inactive-dependency check.
- Fresh delegated reruns of `bash scripts/verify-fast.sh` and `bash scripts/verify.sh` both completed
  with `BUILD SUCCESSFUL`; the new fixture regression also passes.

All required Task 1 broad evidence is reviewed and accepted. Task 1 is completed/accepted.

## Task 0 User-Owned Evidence

Status: **PASS**

User-reported verification reviewed on 2026-09-14:

```bash
./gradlew :build-logic:test verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface --no-daemon
bash scripts/tests/v2-step3-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

All four commands were reported successful. This concise PASS summary is accepted user-owned gate
evidence under `AGENTS.md`; no historical `NOT RUN` result was inferred.

## Task 2 Delta

- Removed `CatalogRoute`, the Catalog-owned Story navigation state, and Story presentation sources
  from `:feature:catalog`; Catalog now emits immutable `StoryRouteArgs` only.
- Added `:feature:story` for Story Detail presentation, with a route-entry keyed
  `StoryPresentationStore` and `StoryPresentationOwner` rather than Activity/Nav ViewModel-store
  ownership. Story arguments are immutable and there is no mutable `open(ref)` API.
- Added bounded `RouteEntryId`, `RouteLifecycle`, `RouteLifecycleChange`, and
  `RouteLifecycleSource` primitives to `:core:common`.
- Added `StoryRoutePreview` / `StoryRouteArgs` to `:catalog:domain`, including Unicode-scalar title
  bounds and aligned cover locator/key validation.
- Added app-owned serializable `StoryRouteWire` / `ArtworkRoutePreviewWire` and a validated
  `StoryRouteCodec` under `app/openstory/composition/navigation/**`. Malformed restored wire fails
  closed instead of fabricating Story identity.
- `AppNavigationState` now emits ACTIVE/RETAINED/RELEASED transitions, rejects roots as child
  entries, enforces globally unique route-entry IDs, sanitizes invalid/duplicate restored child IDs
  fail-closed, and trims deterministic reconstructible ancestors while preserving every root,
  current top, and immediate Back parent. Every explicit pop/reselect/trim emits terminal RELEASED
  for the removed entry; lifecycle publication fails fast rather than silently dropping an event.
- Story lifecycle ownership now survives root switching without retaining inactive route Compose
  trees. RETAINED cancels active acquisition/retry work and quiesces its demand; RELEASED performs
  terminal cleanup exactly once; reactivation waits for quiescence and reacquires a fresh demand.
  Lifecycle epochs reject late non-cooperative activation/retry completion from an obsolete ACTIVE
  generation so stale work cannot overwrite a reactivated Story route.
- Production Story rendering is wired through the existing Catalog runtime and image loader via a
  composition-only bridge, so Task 2 does not create the Task 5 `:core:artwork` boundary early and
  does not regress accepted cover behavior.
- Story connected/screenshot coverage moved to `:feature:story`; route restoration coverage moved
  to `:app`. Catalog integration coverage now verifies Discover artwork plus Story projection
  identity without retaining a cross-feature Story UI dependency.
- Runtime/source composition wiring is confined to `app/openstory/composition/**`; Navigation3
  imports remain confined to `app/openstory/navigation/**`.

## Task 2 Self-Review And Evidence

- `git diff --check` passes with no whitespace errors.
- `scripts/structural-review-report.sh` exits successfully with `Structural hard policies verified`.
  Task 2 no longer leaves the new App navigation destination graph above the structural function
  threshold; remaining review notices are non-blocking pre-existing/general source-shape notices.
- Static scope scan finds zero app runtime/source imports outside `app/openstory/composition/**` and
  zero Navigation3 imports outside `app/openstory/navigation/**`.
- Static production scan finds no live `CatalogRoute`, old
  `app.openstory.catalog.feature.story` reference, or `StoryDetailViewModel` reference in Task 2
  source surfaces (generated profile fixtures excluded).
- Live boundary policy and Gradle declarations agree for the new `:feature:story` production cone;
  `:app` declares the Task 2 direct edges required by codec/composition wiring.
- Focused tests were expanded for route ID/lifecycle behavior, child-stack trimming, malformed
  and duplicate restored child IDs, malformed codec input, Unicode-scalar preview bounds, pending
  activation, RETAINED/RELEASED cleanup, reactivation sequencing, stale activation epochs, retry
  cancellation/failure, and retained-owner lookup.
- Narrow standalone Kotlin diagnostics compile the changed pure-Kotlin `:core:common` and
  `:catalog:domain` production cones, plus the app navigation/codec cone against minimal Android/
  Navigation stubs. These diagnostics are syntax/type-surface evidence only and do not replace the
  required Android/Compose Gradle gate.
- Required Gradle evidence is **NOT RUN in this sandbox**. The checkout has no cached Gradle 9.5.0
  wrapper distribution and no system Gradle executable; network download of the wrapper
  distribution is unavailable. A standalone Kotlin compiler is present and was used only for the
  narrow diagnostics above. No historical or inferred PASS is recorded for Task 2.

Required Task 2 verification before acceptance:

```bash
./gradlew :core:common:test :catalog:domain:test \
  :feature:catalog:testDebugUnitTest :feature:story:testDebugUnitTest \
  :app:testDebugUnitTest :build-logic:test verifyArchitecture \
  :app:verifyFoundation verifyStep3BuildSurface verifyProductionPackageStructure \
  verifyModuleBoundaries --no-daemon
bash scripts/tests/v2-step3-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

Device/connected suites remain user-owned under the existing repository execution policy unless
explicitly delegated.

## Task 2 User-Owned Evidence

Status: **PASS**

User-reported verification reviewed on 2026-09-14:

```bash
./gradlew :core:common:test :catalog:domain:test \
  :feature:catalog:testDebugUnitTest :feature:story:testDebugUnitTest \
  :app:testDebugUnitTest :build-logic:test verifyArchitecture \
  :app:verifyFoundation verifyStep3BuildSurface verifyProductionPackageStructure \
  verifyModuleBoundaries --no-daemon
bash scripts/tests/v2-step3-build-surface-test.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

All four commands were reported successful. This concise PASS summary is accepted user-owned gate
evidence under `AGENTS.md`; the earlier sandbox-local `NOT RUN` remains an accurate record of agent
execution and is not relabeled as agent-owned PASS. All required Task 2 evidence is reviewed and
accepted. Task 2 is completed/accepted.

## Exact Resume Boundary

Tasks 0-2 are completed/accepted. Resume at Task 3 from the owning plan and this checkpoint. The
Task 2 closure turn updated acceptance and routing but did not authorize or start Task 3.
