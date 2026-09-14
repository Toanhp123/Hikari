# Hikari V2 Step 3 - Base App UX/UI Completion

Date: 2026-09-14
Status: **TASK 4 COMPLETED/ACCEPTED; TASK 5 IS THE NEXT RESUME POINT AND HAS NOT STARTED**

## Authority

- Design: `../../superpowers/specs/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.5.md`
- Decision traceability audit: `../v2/2026-09-13-hikari-v2-step-3-R1.5-decision-traceability-final-audit.md`
- Implementation plan: `../../superpowers/plans/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-implementation-plan-R1.1.md`
- Accepted predecessor: `hikari-v2-step-2-discover-story-foundation.md`
- Completed/accepted execution boundary: Tasks 0-4.
- Current execution boundary: Task 4 implementation, focused evidence, and the required broad
  user-owned gate are accepted. Task 5 requires a new explicit user instruction.

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

## Task 1 Post-Acceptance Connected-Contract Closure

Status: **PASS**

A later Task 1/2 self-review found that two `:app` connected startup classes still encoded the
Step 2 startup destination (`Ready -> Discover`) even though Task 1 production and focused host
contracts had already moved the product boundary to `Ready -> AppShell(Home)`. Those connected
classes were not part of the accepted broad host gate, so the stale contract could remain green in
source while contradicting the accepted Step 3 behavior.

The closure patch is intentionally narrow:

- transitional Home now exposes stable app-owned semantics for the root and its Explore actions;
- `StartupFlowTest` now proves FirstRun completion, returning Ready launch, and Activity recreation
  all settle on Home while Discover is not simultaneously composed;
- `CatalogLaunchHandoffTest` now proves Home records zero Catalog activation/storage/acquisition/
  image-loader work, then proves explicit `Explore Manga` is the boundary that may compose Discover
  and start Catalog activation;
- no navigation/runtime ownership, Catalog activation policy, Story lifecycle, or Task 2 production
  behavior changes in this closure patch;
- Step 2/Task 16 benchmark journeys and generated profiles remain intentionally untouched. Task 23
  still owns benchmark-journey/profile regeneration, and the existing benchmark source contract is
  therefore not rewritten early.

Agent-side review confirms the old connected test names/direct startup-to-Discover assertions are
removed from the affected startup classes, and production remains free of the Task 1/2 forbidden
symbols. Android/Compose Gradle execution is not available in this sandbox because the Gradle 9.5.0
wrapper distribution is not cached and network retrieval is unavailable. Do not infer connected PASS
from the previously accepted host gates.

Required user-owned closure command before starting Task 3:

```bash
./gradlew :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.startup.StartupFlowTest,app.openstory.startup.CatalogLaunchHandoffTest' \
  --no-daemon
```

Task 1/2 architecture acceptance remains unchanged. The user explicitly authorized Task 3 to start
on 2026-09-14 while this supplemental connected contract correction remained open. On 2026-09-14,
the user returned PASS for the focused `:app:connectedDebugAndroidTest` command covering
`StartupFlowTest` and `CatalogLaunchHandoffTest`; the corrected `Ready -> Home` and explicit Explore
activation boundary are accepted.

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

## Task 3 Delta

- Added immutable Catalog authority descriptors, explicit capability sets, and a synchronous
  media-to-authority resolver in `:catalog:domain`; descriptor lookup does not activate provider,
  storage, or network work.
- Added runtime-owned `CatalogRuntimeHost` and lazy `CatalogStoreOwner`. Multiple authority sessions
  share one Catalog store and one active-Story retention domain; authority session close/quiescence
  does not close the store, while host close closes it exactly once.
- Moved cross-authority active Story pins into the shared retention domain so releasing one Story
  protects active Stories belonging to other authorities during bounded retention mutation.
- Extended `CatalogRuntimeFactory` and variant composition to register zero or more authorities.
  Legacy direct `createSession()` remains available only for zero/one authority and fails fast for a
  multi-authority registration.
- Deleted the feature-local `CatalogRuntimeHost`. Catalog feature composition now consumes the
  runtime-owned host, reads artwork policy from local descriptors, and freezes the resolved Discover
  authority when a route runtime is created.
- Story activation now selects the authority from `StorySourceRef.catalogSourceKey` rather than an
  implicit singleton binding.
- `DiscoverViewModel` no longer activates from construction. Lifecycle `resume()` is the explicit
  ACTIVE demand boundary; `quiesce()` continues to cancel observation/refresh and route work.

## Task 3 Agent-Owned Evidence

- RED lifetime/control plane: focused runtime test compilation failed because the Task 3
  capability/host/descriptor APIs did not exist.
- RED activation: inactive `DiscoverViewModel` construction failed with one unexpected activation
  (`expected 0, was 1`).
- RED frozen authority: focused feature test compilation failed because the frozen Discover runtime
  boundary did not exist.
- RED hardening: mutable registration metadata changed host descriptors/resolution, and direct
  session creation accepted two authorities; both focused tests failed before implementation.
- GREEN runtime/feature iterations passed host lifetime, cross-authority pin, construction-driven
  activation, frozen-authority, and hardening tests.
- Debug, release, and benchmarkRelease production source compilation passed for `:catalog:runtime`
  and `:feature:catalog`; immediate `:feature:story` and `:app` debug callers also compiled.
- Fresh final focused gate on 2026-09-14: 98 tests, 0 failures, `BUILD SUCCESSFUL in 22s`. It covered
  runtime host/session/quiescence/pins/Discover/Story, feature authority and Discover lifecycle/
  reducer tests, Story presentation, app shell/route contracts, and release/benchmark compilation.
- The returned broad host gate then failed only at `verifyStep3BuildSurface`: live Step 3 delegated
  Catalog variants to the historical Step 2 verifier, whose frozen release contract requires the
  superseded literal `override val binding = null`. The current Task 3 release correctly exposes
  `bindings` and registers an empty authority list, so the failure was verifier ownership drift,
  not production-provider or fixture leakage.
- RED verifier regression: a Step 3 integration fixture containing the current source-free
  multi-authority release shape failed through real `:feature:catalog` delegation. A separate
  hardening RED proved that an `emptyList() + registeredBindings` prefix could not count as an
  empty release authority set.
- GREEN verifier remediation on 2026-09-14: Step 2 keeps the historical default Catalog release
  policy and its literal-null regression; Step 3 delegates through a live Catalog variant entry
  point that requires an auditable empty `bindings` initializer, rejects release authority/source
  registration, and retains seed/benchmark/plugin-harness isolation across `src/main` and
  `src/release`. Fresh `./gradlew :build-logic:test verifyStep3BuildSurface --no-daemon` passed 98
  tests with zero failures/errors and `BUILD SUCCESSFUL in 32s`.

## Task 3 Self-Review

- Host owns only authority registration, shared Catalog-store lifetime, and shared retention/pin
  coordination. No Search, Library, Reading, Settings, reconciliation, or historical-scan
  orchestration was added.
- Descriptor/resolver reads are immutable local snapshots and remain storage/network free. Default
  resolution considers only authorities that declare Discover for the media.
- Store opening remains lazy on first durable activation. One authority session cannot close the
  shared store; host close is idempotent and closes an opened store once.
- Discover owner construction is inert. ACTIVE lifecycle demand activates; RETAINED/STOP quiesces
  work without changing the route's frozen authority.
- No feature-local runtime host, stale single-variant binding access, new dependency edge, manifest
  permission, or Task 4 functionality remains in the changed cone.
- Historical Step 2 acceptance was not loosened: its default verifier path still requires
  `override val binding = null`, while only the Step 3 entry point selects the multi-authority
  release policy. No compatibility property/wrapper or dead Step 2 production API was restored.
- Step 3 release verification now models the current contract as zero registered production
  authorities and source-free production variants. Debug/benchmark required files, compressed
  fixture assets, benchmark source mapping, and non-minified fixture de-duplication remain under
  the shared isolation checks; production seed/benchmark/plugin-harness references remain rejected.
- The verifier-only remediation does not change Catalog runtime host, shared store lifetime,
  authority-session, descriptor/resolver, activation, or cross-authority retention behavior.
- A broader `:feature:catalog:compileDebugAndroidTestKotlin` diagnostic is not Task 3 evidence and
  remains failing on two pre-existing test-source defects outside this changed cone: missing
  `CatalogIssueUi` import in `CatalogScreenshotEvidenceTest` and missing `onStorySelected` argument
  in `CatalogLifecycleInstrumentedTest`. They were not modified or relabeled as Task 3 regressions.

## Task 3 Required User-Owned Gates

Status: **PASS**

On 2026-09-14, the user returned PASS for the complete Task 3 broad host gate:

```bash
./gradlew :catalog:domain:test :catalog:runtime:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest :feature:story:testDebugUnitTest \
  :app:testDebugUnitTest verifyArchitecture :app:verifyFoundation \
  verifyStep3BuildSurface verifyProductionPackageStructure verifyModuleBoundaries --no-daemon
```

This accepts the domain/runtime/feature/app regressions plus architecture, foundation, Step 3 build
surface, production package structure, and module-boundary verification. The supplemental Task 1
connected-contract command is independently recorded as PASS above. Task 3 is completed/accepted.

## Task 4 Delta

- Added process-wide bounded work admission primitives in `:core:common` with independent NETWORK
  and DECODE active/pending ceilings, priority ordering, and foreground capacity reserved from
  NONCRITICAL work.
- Queue overflow returns `Rejected(SATURATED)` without running the submitted block. Block failures
  and cancellation retain their original coroutine semantics rather than being mapped to provider
  or offline failures.
- Queued cancellation removes accounting synchronously; admitted cancellation/failure releases its
  active slot and admits the next eligible waiter without resuming coroutine code under the
  scheduler lock.
- Added process-wide retained-payload accounting with the 240-unit seed ceiling. Only inactive
  RETAINED owners are compacted, oldest recency first with `RouteEntryId` as the deterministic
  tie-break; ACTIVE route identity/payload is not selected for compaction.
- Compaction returns route entry IDs only and zeroes their accounted payload. Release removes the
  owner. The budget retains no feature callback, session, query, source, or presentation reference.
- Added `/app/release/` to `.gitignore` for the user-confirmed local APK/baseline-profile install
  output; no generated release artifact is tracked.

## Task 4 Agent-Owned Evidence

- RED: the focused Task 4 test command failed compilation on the missing admission/result/resource,
  scheduler, retained-owner, decision, and budget types.
- GREEN: active+pending saturation, foreground reserve, queued priority, queued/active cancellation,
  block-failure propagation, deterministic multi-owner compaction, release, compacted accounting,
  and negative-unit validation pass under `kotlinx-coroutines-test`.
- Fresh final focused command on 2026-09-14:

```bash
./gradlew :core:common:test --tests '*ProcessWorkAdmissionTest*' \
  --tests '*RetainedPayloadBudgetTest*' --no-daemon
```

Result: 8 tests, 0 failures/errors, `BUILD SUCCESSFUL in 9s`; no compiler warning was emitted.

## Task 4 Self-Review

- Scheduler state is isolated per resource. Pending work is finite and admitted by priority, then
  FIFO within the same priority; callers execute outside the accounting lock.
- Foreground reserve constrains only NONCRITICAL occupancy. Generic Catalog/runtime work is not
  classified as NETWORK; Task 13 remains the owner of concrete HTTP admission.
- Cancellation cannot strand an active slot or queued entry. Exceptions thrown by the submitted
  block propagate unchanged after accounting is released.
- Retained accounting uses `Long` aggregation to avoid integer overflow, selects only RETAINED
  owners, and deterministically compacts enough reconstructible payload to reach the process bound
  when inactive payload is available.
- No Catalog/Reading query, provider, source, session, lifecycle callback, dispatcher owner, or
  feature dependency was introduced into either primitive.

## Task 4 Required User-Owned Gate

Status: **PASS**

```bash
./gradlew :core:common:test :catalog:domain:test :catalog:runtime:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest :feature:story:testDebugUnitTest \
  :app:testDebugUnitTest :build-logic:test verifyArchitecture :app:verifyFoundation \
  verifyStep3BuildSurface verifyProductionPackageStructure verifyModuleBoundaries --no-daemon
```

On 2026-09-14, the user reported that the complete command succeeded. This concise PASS summary is
accepted as user-owned broad module/host/architecture evidence under `AGENTS.md`; it is not
relabeled as an agent-owned run. Task 4 is completed/accepted.

## Task 5 Delta

- Added `:core:artwork` with the exact eight planned production files. It owns lazy decoded-memory
  and encoded-disk caches, image loading, in-flight coalescing, process admission, remote artwork
  policy/transport contracts, memory-pressure handling, and generic image container/dimension
  preflight.
- Cache seeds are frozen at 32 MiB decoded and 128 MiB encoded; encoded responses remain bounded to
  8 MiB and manual offscreen prefetch remains zero. Container/media/dimension validation is now a
  default core-artwork behavior rather than a Catalog-injected implementation.
- Added deterministic domain-separated SHA-256 cache keys. Encoded identity includes authority,
  stable asset key, locator, and vary scope; decoded identity additionally includes transform.
  Policy host sets are defensively snapshotted before they participate in in-flight key equality.
- Added runtime-owned coalescing before visible DECODE admission. Equivalent consumers share one
  pipeline; one consumer cancellation leaves shared work alive, while final cancellation releases
  it. Benchmark diagnostics continue to observe admitted active decode jobs after removal of the
  feature-local limiter.
- Preserved the Step 3 artwork security envelope: HTTPS only, canonical allowlisted DNS hosts, no IP
  literals/user info/fragments/non-443 ports, locator length bound, at most three revalidated
  redirects, 5/10/15 second transport bounds, admitted media types, bounded streaming bodies,
  caller cancellation, owned timeout mapping, and temporary-payload cleanup.
- App Shell now creates one shared Catalog descriptor/runtime access owner, one shared lazy artwork
  runtime, and one application-owned `BoundedProcessWorkAdmission`. Discover and Story receive
  those shared objects; Home construction does not activate Catalog storage or initialize artwork
  caches/session.
- Catalog artwork policy lookup reads immutable `CatalogRuntimeHost.descriptor()` data and never
  calls `activate()`. Catalog cover presentation maps only Catalog locator/failure semantics at the
  feature boundary.
- Deleted the six planned feature-local artwork production implementations and their duplicate unit
  suites. Existing Step 2 Android fixtures compile through one Android-test-only compatibility
  adapter; no legacy implementation or adapter ships in production.
- Added `:core:artwork` and its exact project edges to Gradle/module-boundary policy. Dependency
  verification adds only the required `androidx.collection:collection-jvm:1.4.2` artifact checksum.

## Task 5 Agent-Owned Evidence

- RED/GREEN covered missing shared coalescing/cancellation APIs, descriptor-only policy lookup,
  decode admission, encoded cache atomicity, cache-key separation, locator/canonical-host bounds,
  immutable policy snapshots, and admitted active-decode diagnostics.
- Final fresh focused test command on 2026-09-14 passed with zero failures/errors/skips:
  `:core:artwork:testDebugUnitTest` 18 tests, selected `:feature:catalog:testDebugUnitTest` contracts
  25 tests, selected benchmarkRelease fixture tests 39 tests, and `AppShellContractTest` 8 tests;
  `BUILD SUCCESSFUL in 25s`.
- Final fresh compile cone passed `:app:compileDebugKotlin`, Catalog release and benchmarkRelease
  production compilation, benchmarkRelease unit-test compilation, and the complete Catalog debug
  Android-test source set; `BUILD SUCCESSFUL in 21s`.
- `git diff --check` exits zero. The core production audit finds exactly eight Kotlin files and no
  Catalog/Library/Reading/Story/plugin/Compose/Room/WorkManager/OkHttp imports. The production symbol
  audit finds none of the six deleted feature-local artwork implementations or their old composition
  local.
- The independent reviewer requested by the review workflow could not start because the child-agent
  provider returned 404/no active credentials. No reviewer result is inferred; the changed cone was
  instead checked against the repository self-review contract before the fresh verification above.

## Task 5 Self-Review

- Core artwork owns generic image acquisition mechanics only. It contains no Catalog, Story,
  Library, Reading, plugin, UI, source, or provider business truth; production HTTP remains absent
  until Task 13.
- Descriptor/policy lookup is synchronous and storage/network-free. Creating App Shell on Home
  constructs lightweight holders only; Catalog storage and artwork cache/session initialization
  remain demand-lazy.
- Cache and in-flight identities cannot alias across authority, locator, vary, transform, decode
  size, or policy differences. Caller-owned mutable host sets cannot corrupt in-flight map keys.
- Remote responses and temporary files close on success, redirect, body failure, cancellation,
  response-close failure, timeout, and size rejection. Every remote hop uses NETWORK admission at
  `VISIBLE_ARTWORK`; every shared pipeline uses DECODE admission at `VISIBLE_ARTWORK`.
- Memory pressure clears decoded memory only. Runtime/session close is idempotent and cancels
  in-flight work before shutting down the image loader and disk cache.
- The remaining Android-test compatibility adapter is source-set isolated. Low-level core types it
  bridges are transport/runtime primitives, not a restored feature-local production implementation.

## Task 5 Required User-Owned Gates

Status: **READY FOR USER VERIFICATION**

Broad module/host/architecture and Detekt gate:

```powershell
.\gradlew.bat :core:artwork:testDebugUnitTest :core:common:test :catalog:domain:test `
  :catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest `
  :feature:story:testDebugUnitTest :app:testDebugUnitTest :build-logic:test `
  verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface `
  verifyProductionPackageStructure verifyModuleBoundaries detekt --no-daemon
```

Focused device lifecycle/security/continuity gate:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.startup.StartupFlowTest,app.openstory.startup.CatalogLaunchHandoffTest' `
  --no-daemon

.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.assets.LocalCoverContinuityInstrumentedTest,app.openstory.catalog.feature.assets.CoverImagePreflightInstrumentedTest,app.openstory.catalog.feature.lifecycle.CatalogLifecycleInstrumentedTest,app.openstory.catalog.feature.plugin.MangaUpdatesCatalogIntegrationTest,app.openstory.catalog.feature.plugin.MangaUpdatesCatalogBoundaryIntegrationTest' `
  --no-daemon
```

## Exact Resume Boundary

Tasks 0-4 remain completed/accepted. Task 5 implementation, focused tests, variant compilation, and
Android-test compilation are complete, but Task 5 is not accepted until the user-owned broad and
device gates above are returned and reviewed. Resume only by reviewing that evidence and fixing any
Task 5 failure. On PASS, mark Task 5 completed/accepted and persist Task 6 as the next pointer; do not
begin Task 6 without a new explicit user instruction.
