# Hikari V2 Step 3 — Base App UX/UI Completion Implementation Plan R1.1

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Repository `AGENTS.md` is stricter: execute **one canonical Task N only**, update the single Step 3 checkpoint/roadmap/evidence, then stop. Never continue to Task N+1 without a new explicit user instruction.

**Goal:** Complete Hikari V2's base application on top of the accepted Step 2 Discover → Story foundation while preserving Step 1 startup, Step 2 bounded Catalog behavior, and the R1.5 rule that bounded user actions must not scale primarily with unrelated global or historical state.

**Architecture:** Migrate ownership before adding capability. `:app` owns application navigation/composition only; Catalog, Library, Reading Source, Settings, artwork and provider adapters remain separate semantic owners. Navigation 3 is used only for serializable route-stack mechanics; route/domain payloads are reconstructed through validated codecs and route runtime/session lifetime is not delegated to retained Compose trees or Navigation ViewModel stores. Dedicated Catalog/Library/Reading databases are used for Step 3 to remove hidden cross-domain startup coupling. Remote byte transport is centralized in a narrow bounded `:core:network`; provider modules own endpoint/DTO semantics, not sockets/pools. Search/Listing/Similar/Detect remain transient and bounded.

**Tech Stack:** accepted Step 2 baseline: Kotlin 2.4.10, JDK 17, AGP 9.3.0, minSdk 26 / targetSdk 37, Compose BOM 2026.06.00, coroutines 1.11.0, Room 2.8.4, DataStore 1.1.3, Lifecycle 2.11.0, Coil 3.5.0, Macrobenchmark/Baseline Profile 1.5.0-beta01. Step 3 may admit Navigation 3 `1.1.4` and OkHttp `5.3.0`, which already exist in the supplied V1 tree, only behind the explicit Step 3 build/security gates below.

**Spec:** `docs/superpowers/specs/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.5.md`

**Traceability audit:** `docs/internal/v2/2026-09-13-hikari-v2-step-3-R1.5-decision-traceability-final-audit.md`

**Baseline repository:** branch `v2/discover-story-foundation`; accepted Step 2 SHA `13af96625a93b3e45f7d7db18e539286ce075c79`.

**Owning checkpoint:** `docs/internal/checkpoints/hikari-v2-step-3-base-app-ux-ui-completion.md`

---

## Global constraints

- Preserve `Unknown -> FirstRun -> Ready`; Step 3 changes only the post-Ready destination to `AppShell -> Home` after the existing first-frame gate.
- Home cold Ready performs zero Catalog remote acquisition, Reading Detect, Chapter/Reader/progress work and zero remote request by default.
- Catalog identity remains `StorySourceRef`; no title/alias/artwork/author canonical fusion or V1 reconciliation topology is admitted.
- Discover + Story remain bounded durable Catalog material. Search + Listing + Similar + Detect remain bounded transient route/session material by default.
- Library membership, `StoryReadingBinding`, and Settings are durable user/configuration truth independent of Catalog cache retention.
- Route payload, route-entry/history metadata and live route-scoped collector cardinality are three separate bounded process resources.
- RETAINED is logical navigation state, not permission to keep inactive root/route Compose trees, image consumers, effects, ViewModel stores or remote work alive indefinitely.
- App route keys contain only small serializable wire values. Domain types are rebuilt through validated codecs at composition/feature boundaries; navigation serialization never forces Catalog/Reading domain models to become serializable.
- Search and Detect do not query while typing. Home local search is reactive but physically indexed/windowed.
- No collection surface performs N per-card Story Detail acquisition.
- Similar is enabled only when the provenance Catalog exposes a real Similar capability.
- `Read from Chapter 1` and `Chapters` are true-disabled Step 4 affordances with zero hidden Step 4 work.
- Detect targets exactly one explicitly selected Reading Source. Binding confirmation commits source + sourceStory + compatible language atomically.
- No Step 3 path invokes Chapters, Reader, progress, downloads, background mapping, V1 plugin runtime, source fan-out, canonical reconciliation or V1 mapping workers.
- Production remote traffic is HTTPS-only, allowlisted, bounded, cancellable, manually redirect-validated, with no implicit cross-origin sensitive-header forwarding and no hidden automatic semantic retry loop.
- Collection-scale CPU, DB I/O, network I/O and large decode do not intentionally run on Main.
- Process-wide expensive-work admission bounds **active and pending** work and reserves foreground progress under noncritical artwork/decode pressure.
- Step 2 accepted performance debt is no-growth debt; it may improve but may not silently worsen/rebaseline.
- A provider capability is never declared merely because a test fixture or old V1 adapter suggests it. Provider admission requires the evidence record defined below.
- Each Task uses focused RED → GREEN evidence, self-reviews the changed cone, updates the one Step 3 checkpoint, commits cleanly, and stops.
- Broad full-repo, connected-device, screenshot and benchmark/profile gates remain user-owned unless explicitly delegated, per `AGENTS.md`.

### Seed implementation bounds

These are safety ceilings, not product semantics. Task 23 may tune them only from recorded evidence and must retain finite bounds.

```text
route history:              <= 32 entries process-wide, <= 12 per product root
transient payload:          <= 240 poster/candidate units process-wide
Search retained window:     <= 60 items / route
Listing retained window:    <= 100 items / route
Similar retained window:    <= 30 items / route
Detect retained window:     <= 60 items / route
Home DB window:             <= 60 rows / query window
network admission:          <= 6 active, <= 12 pending; >=2 active capacity reserved from noncritical work
decode admission:           <= 4 active, <= 8 pending; >=1 active capacity reserved from noncritical work
artwork encoded response:   <= 8 MiB
artwork decoded cache seed: 32 MiB
artwork encoded cache seed: 128 MiB
manual offscreen prefetch:  0
HTTP redirects:             <= 3 validated hops
HTTP connect/read/call:     5s / 10s / 15s
JSON response body:         <= 2 MiB before parsing
```

### Provider-admission evidence record

Every production provider task must check in one record under `docs/internal/v2/providers/` containing:

```text
Provider/source identity:
Capability being admitted:
Authoritative contract URL or captured real endpoint evidence:
Contract/revision/capture date:
Exact endpoint/method:
Exact request identity and paging fields:
Required response fields and bounds:
Ordering/ranking guarantee if Listing is admitted:
Artwork hosts/origins:
Authentication/sensitive-header policy:
Unsupported capabilities:
Deterministic checked-in fixtures derived from the contract/evidence:
Live-smoke requirement if no stable published contract exists:
Decision: ADMITTED | NOT_ADMITTED
```

A Hikari-authored controlled fixture is deterministic regression evidence, but **not by itself proof of a provider's real production contract**.

---

## Repository audit basis

### Newest V2 facts that constrain implementation

- Step 2 Tasks 0–18 are completed/accepted; Task 16 performance debt remains authoritative.
- `StartupGate.kt` still owns `Unknown -> FirstRun -> Ready`; Ready currently calls `CatalogEntryPoint` after `firstFrameReached`.
- `feature/catalog` still owns `CatalogRoute = Discover | Story`, media selection and the current Story route.
- `DiscoverViewModel` has `init { activateIfNeeded() }`, `selectedMediaType`, and `selectMedia()`; top-level media ownership is therefore still inside Catalog.
- `StoryDetailViewModel` owns one mutable active Story rather than one route-scoped immutable Story identity.
- `CatalogRuntimeHost` currently lives in feature composition; `assetPolicyProvider()` can activate the Catalog runtime.
- Catalog image loader/cache/remote-cover mechanics are feature-local.
- release `VariantCatalogBinding.binding` is `null`; there is no production Catalog adapter/HTTP client/INTERNET permission yet.
- `StorySourceRef`, `CatalogSourceKey`, `CatalogMediaType`, `CoverAssetKey` and `CoverLocator` are not navigation-serialization types.
- `verifyStep2BuildSurface` compares the **exact live module set/graph** to Step 2. It cannot remain a live exact-graph gate after Step 3 adds modules.
- `verifyProductionPackageStructure` and `scripts/verify-fast.sh` / `scripts/verify.sh` are Step-2-module lists today; they would silently omit new Step 3 modules unless evolved.
- `v2-foundation-policy.json` has a 377-line total app structural ratchet; current app production Kotlin is already 335 lines, so Step 3 App Shell cannot be added by simply keeping the Step 2 total unchanged.

### V1 evidence used as negative/positive reference

Reuse only focused mechanics after characterization:

```text
provider parsing/value normalization
request identity/cancellation mechanics
bounded HTTP/security ideas
Navigation 3 stack mechanics
```

Do not reuse V1 topology:

```text
PersistentTopLevelNavDisplay composing every visited top-level root
multi-provider Catalog Search fan-out
canonical fusion/reconciliation
ContentMappingService / auto-link / scoring / rejection history
background mapping workers
broad observeAll repositories
Reader source-routing engine for Step 3 mapping
generic plugin runtime merely to obtain HTTP
```

V1 Navigation 3 route keys are serializable primitive-wire records, while `PersistentTopLevelNavDisplay` retains all visited roots in composition. Step 3 keeps the former idea and explicitly rejects the latter.

V1 MangaDex Search mapping and its checked-in Search fixture contain title/aliases/authors but do **not** prove candidate-specific verified language metadata. The repository does not contain `availableTranslatedLanguages`. Provider-language admission therefore remains evidence-gated in Task 18; the plan must not invent a field.

The Step 2 MangaUpdates controlled plugin proves Hikari's desired mapping mechanics and freezes source key `org.openstory.catalog.mangaupdates`, but its Hikari-authored fixture does not by itself prove real upstream ranking/continuation guarantees. Task 14 admits only provider capabilities supported by a production evidence record.

---

## Final production module target

Modules are created only in the task that first needs them.

```text
:app
  -> :core:common, :core:designsystem, :core:artwork, :core:network
  -> :catalog:domain, :catalog:runtime
  -> :library:domain, :library:runtime
  -> :reading:domain, :reading:runtime
  -> :settings:domain, :settings:runtime
  -> :feature:catalog, :feature:library, :feature:story, :feature:reading, :feature:settings
  -> :sources:mangaupdates
  -> :sources:mangadex only if Task 18 returns ADMITTED

:core:network -> :core:common
:core:artwork -> :core:common, :core:network (edge added only in Task 13)

:catalog:runtime -> :catalog:domain, :catalog:storage, :core:common
:feature:catalog -> :catalog:domain, :catalog:runtime, :core:common, :core:designsystem, :core:artwork
:sources:mangaupdates -> :catalog:domain, :core:common, :core:network

:library:domain -> :catalog:domain, :core:common
:library:storage -> :library:domain, :catalog:domain, :core:common
:library:runtime -> :library:domain, :library:storage, :core:common
:feature:library -> :library:domain, :library:runtime, :catalog:domain, :core:common, :core:designsystem, :core:artwork

:reading:domain -> :catalog:domain, :core:common
:reading:storage -> :reading:domain, :catalog:domain, :core:common
:reading:runtime -> :reading:domain, :reading:storage, :core:common
:feature:reading -> :reading:domain, :reading:runtime, :catalog:domain, :core:common, :core:designsystem, :core:artwork
:sources:mangadex -> :reading:domain, :core:common, :core:network (conditional)

:settings:domain -> :catalog:domain, :reading:domain, :core:common
:settings:runtime -> :settings:domain, :core:common
:feature:settings -> :settings:domain, :settings:runtime, :reading:domain, :reading:runtime,
                     :catalog:domain, :catalog:runtime, :core:common, :core:designsystem

:feature:story -> :catalog:domain, :catalog:runtime,
                  :library:domain, :library:runtime,
                  :reading:domain, :reading:runtime,
                  :core:common, :core:designsystem, :core:artwork
```

`:app` may import runtime/source implementations only inside `app/openstory/composition/**`; storage implementations remain forbidden everywhere in `:app`. Navigation files use app-owned route-wire types and `:core:common` navigation IDs only.

Physical persistence:

```text
hikari-v2-catalog.db      bounded Catalog cache/material
hikari-v2-library.db      Library user truth
hikari-v2-reading.db      Story Reading user/config truth
DataStore                 Theme/App-locale/Reading defaults only
```

---

# Tasks

## Task 0 — Freeze Step 3 authority and migrate live architecture governance

**Goal:** Make R1.5 + this plan canonical and make the repository's live structural gates capable of accepting Step 3 without deleting Step 2 historical evidence.

**Files:**
- Create: `docs/superpowers/specs/2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.5.md`
- Create: `docs/internal/v2/2026-09-13-hikari-v2-step-3-R1.5-decision-traceability-final-audit.md`
- Create: this canonical plan file.
- Create: `docs/internal/checkpoints/hikari-v2-step-3-base-app-ux-ui-completion.md`
- Create: `config/architecture/history/step2-module-boundaries.json` as an immutable copy of the accepted Step 2 policy.
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/Step3BuildSurfaceVerifier.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyStep3BuildSurfaceTask.kt`
- Create: `build-logic/src/test/kotlin/app/openstory/build/architecture/Step3BuildSurfaceVerifierTest.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicy.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/FoundationPolicyLoader.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/AppStructuralVerifier.kt`
- Modify corresponding build-logic tests.
- Delete/replace live static script: `scripts/tests/v2-step2-build-surface-test.sh`
- Create: `scripts/tests/v2-step3-build-surface-test.sh`
- Modify: `scripts/verify-fast.sh`, `scripts/verify.sh`
- Modify: `docs/implementation/current-roadmap.md`

**Policy contract:**
- `verifyArchitecture` depends on live `verifyStep3BuildSurface`, module boundaries, identity, dynamic package-cycle verification and `:app:verifyFoundation`.
- The exact Step 2 graph verifier becomes historical fixture evidence; it is not run against the mutated Step 3 live graph.
- Step3 verifier carries forward still-relevant Step2 no-growth safety rules: release cannot contain debug/benchmark seed/plugin-harness artifacts; JavaScriptEngine/plugin API stay non-production; Design System stays runtime-free; storage owners remain allowlisted; HTTP imports are forbidden everywhere except `:core:network` after Task13 admission.
- Production package-cycle inputs are derived from the live module-boundary policy/subprojects rather than `STEP2_MODULE_DIRECTORIES`.
- Register live `verifyStep3FastModules` / `verifyStep3FullModules` aggregate tasks from the current subproject graph so broad scripts automatically include modules added later. JVM modules contribute `test`; Android app/library modules contribute debug unit/assemble gates; full adds applicable release/lint gates. Benchmark/device tasks remain separately user-owned.
- Evolve the app structural ratchet to retain a finite total cap **and** prefix budgets. Seed total <=1800 production Kotlin lines: startup package <=320, navigation <=800, composition <=500, remaining app UI/root <=300; adjust only if the implementation cannot fit after decomposition, never by removing the ratchet.

- [ ] **RED governance tests.** Prove current exact Step2 live verifier would reject one added Step3 module; prove new verifier distinguishes immutable Step2 history from current Step3 graph.
- [ ] **RED scope tests.** Verify runtime/source imports are allowed in `:app` only under `app/openstory/composition/**`; storage remains forbidden; Navigation3 is allowed only in `app/openstory/navigation/**`; OkHttp remains forbidden outside `:core:network`.
- [ ] **Implement live Step3 verifier + dynamic package inputs.** Remove `verifyStep2BuildSurface` from live `verifyArchitecture`; preserve its unit fixture against archived Step2 policy.
- [ ] **Replace shell static gate.** `scripts/tests/v2-step3-build-surface-test.sh` must call `verifyStep3BuildSurface verifyProductionPackageStructure verifyModuleBoundaries` and must not assert Step2-only absence of INTERNET/HTTP after later explicit admission. Update `verify-fast.sh`/`verify.sh` to call the dynamic Step3 aggregate tasks so a newly included production module cannot be silently omitted from broad verification.
- [ ] **GREEN.** `./gradlew :build-logic:test verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface --no-daemon` on the unchanged runtime graph.
- [ ] **Self-review.** No production dependency, manifest permission or runtime behavior changed; the Step2 accepted SHA/debt is copied verbatim into checkpoint history.
- [ ] **Commit/stop:** `docs(step3): establish live R1.5 architecture governance`.

---

## Task 1 — App Shell top-level roots, serializable route wires, fixed-media Discover

**Goal:** Move Manga/Home/Light Novel ownership to App Shell without preserving Catalog's mutable media authority or V1's persistent offscreen-root composition.

**Files:**
- Modify: `gradle/libs.versions.toml` — Navigation3 runtime/ui `1.1.4`; no Navigation3 ViewModel-store decorator dependency.
- Modify: `app/build.gradle.kts` — add Kotlin serialization plugin and Navigation3 runtime/ui.
- Create: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppMediaRoute.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavigationPolicy.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppFocusedDestination.kt`
- Create focused app tests.
- Modify: `app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogEntryPoint.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogComposition.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/DiscoverScreen.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/feature/discover/DiscoverViewModelTest.kt`
- Delete: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/CatalogMediaDestinationNav.kt`
- Modify module/foundation policy as admitted by Task0.

**Route wire:**
```kotlin
@Serializable
enum class AppMediaRoute { MANGA, LIGHT_NOVEL }

@Serializable
sealed interface AppRoute : NavKey {
    val entryId: String

    @Serializable
    data class Discover(
        override val entryId: String,
        val media: AppMediaRoute,
    ) : AppRoute

    @Serializable
    data class Home(override val entryId: String) : AppRoute
}
```

`entryId` is validated into `RouteEntryId` at the boundary; domain `CatalogMediaType` does not live inside NavKey serialization.

```kotlin
data class AppNavigationPolicy(
    val maxTotalEntries: Int = 32,
    val maxEntriesPerRoot: Int = 12,
)
```

Catalog root becomes immutable-media:
```kotlin
@Composable
fun CatalogRootEntryPoint(mediaType: CatalogMediaType)
```

`DiscoverViewModel` constructor receives one immutable `mediaType`; `DiscoverUiState.selectedMediaType` and `selectMedia()` are removed in this task.

- [ ] **RED navigation tests.** Home default; three independent root stacks; switch restores root state; reselect selected root pops to root without refresh; root-level Back follows platform exit/background behavior, not tab history.
- [ ] **RED startup handoff.** `Unknown` and `FirstRun` unchanged; only `Ready + firstFrameReached` creates App Shell/Home; Home construction records zero Catalog activation.
- [ ] **RED fixed-media Discover.** One Manga owner cannot switch to LN and vice versa; no production symbol named `selectMedia`/`selectedMediaType` remains.
- [ ] **Implement one-active-root composition.** Keep three serializable back stacks/state records, render only the selected root's `NavDisplay`. Do **not** use V1 `PersistentTopLevelNavDisplay` and do not retain inactive roots through Navigation ViewModel-store decorators.
- [ ] **Temporary truthful Home root.** Until Task8, render local empty shell with real Explore Manga / Explore Light Novels actions. Remove fake Search icon; Task15 adds it only when Search exists.
- [ ] **GREEN.** Focused app navigation/startup + Discover tests and compile.
- [ ] **Self-review.** Search production for `PersistentTopLevelNavDisplay`, multiple simultaneously composed root `NavDisplay`s, `selectedMediaType`, `selectMedia`; all absent. App route keys contain no Catalog domain object.
- [ ] **Commit/stop:** `feat(app): own lazy serializable top level navigation`.

---

## Task 2 — App-owned child route wires, Story module, explicit route lifecycle source

**Goal:** Remove `CatalogRoute`, move Story UI out of Catalog feature, and make ACTIVE/RETAINED/RELEASED a navigation-state event independent of Compose/ViewModel retention.

**Files:**
- Create: `core/common/src/main/kotlin/app/openstory/common/navigation/RouteEntryId.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/navigation/RouteLifecycle.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/navigation/RouteLifecycleChange.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/navigation/RouteLifecycleSource.kt`
- Create tests for ID bounds/lifecycle transitions.
- Create: `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/StoryRouteArgs.kt`
- Create: `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/read/StoryRoutePreview.kt`
- Add module: `:feature:story`
- Move `feature/catalog/.../story/*` to `feature/story/src/main/kotlin/app/openstory/story/feature/` and rename production `StoryPreview*` wrappers that now own real behavior.
- Create: `feature/story/.../StoryEntryPoint.kt`, `StoryCatalogFacet.kt`, `StoryPresentationOwner.kt`.
- Move corresponding unit/connected/screenshot tests.
- Modify: App navigation files, `CatalogEntryPoint.kt`, `CatalogComposition.kt`, Discover story callback.
- Delete: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogRoute.kt`.

**Serializable Story wire in `:app`:**
```kotlin
@Serializable
data class StoryRouteWire(
    val entryId: String,
    val storyId: String,
    val catalogSourceKey: String,
    val sourceStoryId: String,
    val originMedia: AppMediaRoute,
    val previewTitle: String?,
    val previewArtwork: ArtworkRoutePreviewWire?,
)
```

```kotlin
@Serializable
data class ArtworkRoutePreviewWire(
    val authorityKey: String,
    val stableAssetKey: String,
    val locatorKind: String,
    val locatorValue: String,
    val locatorAux: String?,
    val revision: String,
)
```

`ArtworkRoutePreviewWire` contains only bounded primitive locator/presentation fields. `StoryRouteCodec` in `app/openstory/composition/navigation/` reconstructs and validates `StorySourceRef`, media and optional `StoryRoutePreview`; malformed restored wire is rejected without fabricating identity.

```kotlin
@JvmInline value class RouteEntryId(val value: String)
enum class RouteLifecycle { ACTIVE, RETAINED, RELEASED }
data class RouteLifecycleChange(val entryId: RouteEntryId, val lifecycle: RouteLifecycle)
interface RouteLifecycleSource { val changes: Flow<RouteLifecycleChange> }
```

- [ ] **RED serialization/codec tests.** Round-trip primitive wire; malformed story/source/cover alignment fails closed; saved state contains no bitmap/runtime/domain object/large result list.
- [ ] **RED lifecycle tests.** Story A→B emits A RETAINED/B ACTIVE; Back emits B RELEASED/A ACTIVE; root switch retains current child; trim/pop emits RELEASED exactly once.
- [ ] **RED child-history tests.** Independent Manga/Home/LN child stacks; current immediate Back parent preserved under trimming.
- [ ] **Implement deterministic history trimming.** Enforce 32-global/12-per-root bounds; preserve every root identity, each root top/current route and current immediate Back parent first; oldest reconstructible ancestors are collapsed back to their own root, never another root; every trimmed entry emits RELEASED exactly once and trimming triggers no hidden reload.
- [ ] **Move Story and remove mutable open API.** Story owner construction takes immutable `StoryRouteArgs`; remove `open(ref)` mutation. Feature Catalog emits args only and never renders Story.
- [ ] **Route runtime ownership rule.** Feature/runtimes may subscribe to the small app-owned lifecycle source by `RouteEntryId`; retained domain/session state is not tied to retained Compose trees or Navigation ViewModel stores.
- [ ] **GREEN focused tests.** App route recreation, Story move, Catalog compile.
- [ ] **Self-review.** `CatalogRoute`, old Story package, mutable global current Story and retained full Story composition are absent.
- [ ] **Commit/stop:** `refactor(navigation): move child story routes to app lifecycle ownership`.

---

## Task 3 — Multi-authority Catalog Runtime Host and store lifetime

**Goal:** Move runtime hosting into Catalog runtime, separate authority sessions from physical Catalog DB lifetime, and remove construction-driven activation.

**Files:**
- Create: `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/CatalogAuthorityDescriptor.kt`
- Create: `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/CatalogCapabilitySet.kt`
- Create: `catalog/domain/src/main/kotlin/app/openstory/catalog/domain/source/CatalogAuthorityResolver.kt`
- Create: `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogRuntimeHost.kt`
- Create: `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/CatalogStoreOwner.kt`
- Create focused runtime tests.
- Modify: `CatalogRuntimeFactory.kt`, `CatalogCapabilitySession.kt`, retention/active-pin code, Catalog composition and Discover presentation owner.
- Delete feature-local `CatalogRuntimeHost` from `CatalogComposition.kt` after migration.

**Interfaces:**
```kotlin
data class CatalogAuthorityDescriptor(
    val sourceKey: CatalogSourceKey,
    val displayName: String,
    val mediaTypes: Set<CatalogMediaType>,
    val capabilities: CatalogCapabilitySet,
    val artworkPolicy: SourceAssetPolicy?,
)

fun interface CatalogAuthorityResolver {
    fun authorityFor(mediaType: CatalogMediaType): CatalogSourceKey?
}

interface CatalogRuntimeHost : AutoCloseable {
    fun descriptor(sourceKey: CatalogSourceKey): CatalogAuthorityDescriptor?
    fun authorityResolver(): CatalogAuthorityResolver
    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation
}
```

- [ ] **RED lifetime.** Two authorities may share one Catalog store; closing/quiescing one session does not close store; host close does. Cross-authority active Story pins protect shared retention.
- [ ] **RED activation.** Constructing inactive Discover owner makes zero `activate()` calls. ACTIVE demand activates; RETAINED quiesces route work.
- [ ] **Implement descriptor/control-plane resolver.** Descriptor lookup and `authorityFor(media)` are local/synchronous and open no DB/network/provider work. Search/Discover route creation resolves once and freezes the returned key; a later default change cannot mutate existing route authority.
- [ ] **Implement host/store split.** Catalog DB opens lazily on first durable demand and closes only with store owner/host.
- [ ] **GREEN.** Runtime/Discover focused tests.
- [ ] **Self-review.** Host owns registration/store lifetime only; no Search/Library/Reading use-case orchestration or global historical scan is added.
- [ ] **Commit/stop:** `refactor(catalog): separate authority and store lifetimes`.

---

## Task 4 — Process work admission and retained-payload accounting

**Goal:** Add bounded process resource primitives without turning infrastructure into a global business coordinator.

**Files:**
- Create: `core/common/src/main/kotlin/app/openstory/common/execution/ProcessWorkAdmission.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/execution/WorkPriority.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/execution/WorkAdmissionResult.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/retention/RetainedPayloadBudget.kt`
- Create focused coroutine tests.

**Interfaces:**
```kotlin
enum class WorkPriority { FOREGROUND_COMMAND, VISIBLE_ARTWORK, NONCRITICAL }
enum class WorkResource { NETWORK, DECODE }
enum class WorkRejectionReason { SATURATED }

sealed interface WorkAdmissionResult<out T> {
    data class Completed<T>(val value: T) : WorkAdmissionResult<T>
    data class Rejected(val reason: WorkRejectionReason) : WorkAdmissionResult<Nothing>
}

interface ProcessWorkAdmission {
    suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T>
}

data class RetainedPayloadOwner(
    val entryId: RouteEntryId,
    val units: Int,
    val lifecycle: RouteLifecycle,
    val recency: Long,
)

data class RetentionDecision(val compactEntryIds: List<RouteEntryId>)

interface RetainedPayloadBudget {
    fun update(owner: RetainedPayloadOwner): RetentionDecision
    fun release(entryId: RouteEntryId)
}
```

- [ ] **RED active/pending bounds.** Saturate permits and pending queue; overflow returns `Rejected(SATURATED)` rather than creating an unbounded suspended queue.
- [ ] **RED fairness/cancellation.** Foreground obtains reserved capacity under sustained noncritical load; cancelled queued owner is removed promptly; permits are not stranded.
- [ ] **RED retention.** >240 units returns deterministic inactive owner IDs to compact. Budget stores accounting only—no feature callbacks/session references.
- [ ] **Implement bounded scheduler/accounting.** Business code maps `Rejected`: artwork may fall back/defer; foreground feature shows local retry/busy state, never false offline/provider failure.
- [ ] **GREEN deterministic race tests.** Use `kotlinx-coroutines-test`.
- [ ] **Self-review.** No Catalog/Reading query/source semantics in either primitive. Do **not** route generic Catalog runtime work through `NETWORK`; only concrete network transport introduced in Task13 uses network admission.
- [ ] **Commit/stop:** `feat(core): bound process work and retained payload accounting`.

---

## Task 5 — Shared artwork runtime without Catalog activation

**Goal:** Move image/cache/coalescing/security-policy lookup out of feature Catalog before Home/Reading become image consumers. This task remains transport-agnostic; production HTTP arrives in Task13.

**Files:**
- Add module: `:core:artwork`
- Create exact files: `ArtworkRequest.kt`, `ArtworkRequestIdentity.kt`, `ArtworkPolicy.kt`, `ArtworkPolicyResolver.kt`, `ArtworkRuntime.kt`, `ArtworkInFlight.kt`, `ArtworkTransport.kt`, `ArtworkCaches.kt` under `core/artwork/src/main/kotlin/app/openstory/artwork/`.
- Add focused unit/android tests.
- Migrate/delete feature-local `CatalogImageLoader.kt`, `CatalogImageMemoryPressureController.kt`, `CoverEncodedDiskCache.kt`, `CoverFetcher.kt`, `RemoteCoverPolicy.kt`, `RemoteCoverTransport.kt` after callers move.
- Modify Catalog cover presentation, composition, variant diagnostics/tests and module policy.

**Interfaces:**
```kotlin
@JvmInline value class ArtworkAuthorityKey(val value: String)

data class ArtworkRequestIdentity(
    val authority: ArtworkAuthorityKey,
    val stableAssetKey: String,
    val locator: String,
    val transformKey: String,
    val varyKey: String,
)

fun interface ArtworkPolicyResolver {
    fun policyFor(authority: ArtworkAuthorityKey): ArtworkPolicy?
}
```

- [ ] **RED no-activation.** Policy/cache lookup for a Library-like request never calls `CatalogRuntimeHost.activate()`.
- [ ] **RED coalescing/shared cancellation.** Equivalent security/policy/transform/vary identity -> one fetch/decode pipeline; one consumer cancel does not kill remaining consumer; final cancel releases work.
- [ ] **RED policy security.** Preserve Step2 HTTPS/host/redirect/body/dimension bounds at artwork layer; non-equivalent auth/cache-vary scope never coalesces.
- [ ] **Implement process-scoped runtime.** 32MiB/128MiB seed caches, no manual prefetch, DECODE work via Task4 admission. Remote transport may be null/fake in production until Task13; local/cache/fallback still work.
- [ ] **Migrate Catalog callers.** Immutable artwork policy descriptors register without Catalog payload activation.
- [ ] **GREEN.** Artwork + Catalog asset tests.
- [ ] **Self-review.** `:core:artwork` contains no Story/Library/Reading business truth; Design System imports no acquisition runtime.
- [ ] **Commit/stop:** `refactor(artwork): share bounded process image runtime`.

---

## Task 6 — Step 3 Catalog contracts and Catalog schema v2

**Goal:** Add narrow Search/Listing/Similar contracts and Story metadata needed by Step3 without making one God acquisition interface or persisting transient query corpora.

**Files:**
- Create under `catalog/domain/.../source/`: `CatalogDiscoverCapability.kt`, `CatalogStoryCapability.kt`, `CatalogSearchCapability.kt`, `CatalogSectionCapability.kt`, `CatalogSimilarCapability.kt`, `CatalogSectionDescriptor.kt`, `CatalogTransientStory.kt`, `CatalogPage.kt`.
- Modify acquisition/detail models, limits and validators.
- Migrate `feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/seed/LocalSeedCatalogSource.kt` and `feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/BenchmarkCatalogSource.kt` to the narrow capability ports; keep reference-plugin bridging androidTest-only.
- Create storage entities: `StoryAliasEntity.kt`, `StoryLanguageEntity.kt`; modify `StoryEntities.kt`, DAO/mappers/`CatalogDatabase.kt`; add `MIGRATION_1_2` and migration tests/schema export.
- Modify Discover UI section model/labels/headers.

**Interfaces:**
```kotlin
enum class SectionExpansion { NONE, PAGED }

data class CatalogSectionDescriptor(
    val key: String,
    val kind: CatalogSectionKind,
    val displayLabel: String?,
    val expansion: SectionExpansion,
    val globallyOrdered: Boolean,
)

data class CatalogTransientStory(
    val ref: StorySourceRef,
    val title: String,
    val contentType: CatalogMediaType,
    val coverAssetKey: CoverAssetKey?,
    val coverLocator: CoverLocator?,
    val supportingText: String?,
    val rating: CatalogRating?,
)

data class CatalogPage<T>(
    val items: List<T>,
    val nextContinuation: String?,
)
```

Story detail adds bounded `alternateTitles: List<String>` and normalized `catalogLanguageTags: List<String>`.

- [ ] **RED bounds.** Query/continuation/section keys/page size/aliases/language tags/source-local duplicates and malformed metadata fail closed.
- [ ] **RED DB migration.** v1 -> v2 preserves all durable Discover/Story rows; aliases/languages default empty; no destructive fallback.
- [ ] **Split narrow capabilities.** Durable Discover/Story continue materializing to Catalog storage; Search/Section/Similar return validated transient pages only.
- [ ] **Truthful Discover cleanup now.** Remove fake `See All`; no section action until Task16 has an admitted Listing. Rename `LATEST_UPDATES` UI/skeleton from `Recommended for You` to localized `Latest Updates`.
- [ ] **GREEN.** Domain/storage/feature focused tests.
- [ ] **Self-review.** No transient Search/List/Similar tables, no alias identity merge, no provider-config bag in domain.
- [ ] **Commit/stop:** `feat(catalog): add step3 capability and metadata contracts`.

---

## Task 7 — Library domain, dedicated Room truth, indexed window queries

**Goal:** Admit durable Library user truth with point observation and physically bounded Home query behavior.

**Files:**
- Add modules: `:library:domain`, `:library:storage`, `:library:runtime`.
- Domain exact files: `LibraryEntry.kt`, `LibraryPresentationSnapshot.kt`, `LibraryArtworkSnapshot.kt`, `LibraryFilter.kt`, `LibraryQuery.kt`, `LibraryCursor.kt`, `LibraryMutationResult.kt`, `LibraryPort.kt`.
- Storage exact files: `LibraryDatabase.kt`, `LibraryEntryEntity.kt`, `LibrarySearchFts.kt`, `LibraryDao.kt`, `RoomLibraryStore.kt` plus schema export/android tests.
- Runtime exact files: `LibraryRuntime.kt`, `LibraryMutationOwner.kt`, `LibraryQuerySession.kt` plus tests.
- Modify module policy/settings.

**Interfaces:**
```kotlin
data class LibraryCursor(val savedAtEpochMs: Long, val storyId: String)

data class LibraryArtworkSnapshot(
    val coverAssetKey: CoverAssetKey,
    val coverLocator: CoverLocator,
)

data class LibraryPresentationSnapshot(
    val title: String,
    val artwork: LibraryArtworkSnapshot?,
    val supportingText: String?,
)

data class LibraryEntry(
    val ref: StorySourceRef,
    val originMediaContext: CatalogMediaType,
    val savedAtEpochMs: Long,
    val snapshot: LibraryPresentationSnapshot,
)

enum class LibraryFilter { ALL, MANGA, LIGHT_NOVEL }

data class LibraryQuery(
    val text: String,
    val filter: LibraryFilter,
    val after: LibraryCursor?,
    val limit: Int = 60,
)

data class LibraryWindow(val items: List<LibraryEntry>, val nextCursor: LibraryCursor?)
enum class LibraryMutationResult { CHANGED, NO_OP }

interface LibraryPort {
    fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?>
    fun observeWindow(query: LibraryQuery): Flow<LibraryWindow>
    suspend fun add(entry: LibraryEntry): LibraryMutationResult
    suspend fun remove(ref: StorySourceRef): LibraryMutationResult
    suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult
}
```

- [ ] **RED idempotency.** Repeated Add while saved = NO_OP, keeps `savedAt`; identical enrichment = NO_OP/no UPDATE; remove touches Library only; remove→later add receives new clock time.
- [ ] **RED point observation.** No `observeAll()->filter` path exists.
- [ ] **RED physical query shape.** Add indexes for default/filter order; blank query is keyset `savedAt DESC, storyId`; nonblank uses FTS + bounded window. Query plan/test forbids user-space full-corpus filter/sort.
- [ ] **Implement `hikari-v2-library.db`.** No Catalog FK/cascade. Use existing `app.openstory.common.Clock` seam.
- [ ] **Latest-query-wins session.** Stale query work is cancelled where storage permits and cannot publish over newer input.
- [ ] **GREEN.** JVM/storage Android tests including statement/query-plan diagnostics.
- [ ] **Self-review.** No Catalog runtime/storage import, no broad observer, no timestamp churn.
- [ ] **Commit/stop:** `feat(library): add durable bounded local collection`.

---

## Task 8 — Home Library root and shared poster primitives

**Goal:** Replace transitional Home with real local Library UI while proving Home Ready is independent of Catalog/Reading runtime.

**Files:**
- Add module `:feature:library`.
- Create exact files: `HomeEntryPoint.kt`, `HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`, `LibraryStoryPosterUi.kt`, `HomeTestTags.kt` plus tests.
- Add in Design System: `HikariArtworkFrame.kt`, `HikariPosterCard.kt`, `HikariPosterGrid.kt`, `HikariPosterSkeleton.kt` with tests.
- Modify Discover poster call sites and AppNavHost/composition.

**State:**
```kotlin
data class HomeUiState(
    val inputQuery: String,
    val filter: LibraryFilter,
    val content: HomeContentState,
)
```

- [ ] **RED UX.** True-empty differs from filtered-empty; filters exactly All/Manga/Light Novel; default savedAt descending; local typing latest-wins; Explore actions route to correct roots.
- [ ] **RED lifecycle.** RETAINED quiesces query collector; ACTIVE re-establishes bounded local observation and reconciles membership while preserving query/filter/scroll token.
- [ ] **Promote poster primitives only now** because Home is the second real consumer. Catalog and Library keep separate semantic UI models over one visual primitive.
- [ ] **Wire Home.** Local DB publication is Home Ready. Visible artwork cache miss may ask `ArtworkRuntime`; network absence/miss never blocks Ready.
- [ ] **GREEN.** Home + migrated Discover tests/screenshots/compile.
- [ ] **Self-review.** Home-only trace: Catalog activation=0, Reading=0, remote request=0 before visible artwork demand.
- [ ] **Commit/stop:** `feat(home): add bounded local library surface`.

---

## Task 9 — Story Library facet and multi-facet presentation reducer

**Goal:** Add Library membership to Story without making Story the persistence owner.

**Files:**
- Modify `:feature:story` entry/presentation files.
- Create: `StoryReducer.kt`, `StoryLibraryFacet.kt`, `LibraryMembershipUi.kt` plus focused tests.
- Modify app composition to inject Library runtime port.

**State:**
```kotlin
sealed interface LibraryMembershipUi {
    data object NotSaved : LibraryMembershipUi
    data object Saving : LibraryMembershipUi
    data object Saved : LibraryMembershipUi
    data object Removing : LibraryMembershipUi
}
```

- [ ] **RED Add/Remove.** Add uses already-visible Story data; no Catalog reacquisition; per-Story Library owner serializes duplicates; failure restores previous stable state.
- [ ] **RED snapshot enrichment.** While saved and ACTIVE, semantically changed trusted Story presentation may point-update snapshot; identical snapshot writes nothing and never changes savedAt.
- [ ] **RED lifecycle.** Membership observation keyed to current Story while ACTIVE; RETAINED quiesces; reactivation reconciles local truth without remote refresh.
- [ ] **Implement thin reducer.** Reducer combines facet states only; Library runtime owns mutation.
- [ ] **GREEN Home continuity.** Add Story→Back Home appears; Remove→Back Home disappears while Home route state remains.
- [ ] **Self-review.** No event bus for durable truth and no Library storage import in Story feature.
- [ ] **Commit/stop:** `feat(story): compose durable library membership facet`.

---

## Task 10 — Design System Step 3 visual policy

**Goal:** Centralize repeated presentation policy without moving feature semantics/runtime into Design System.

**Files:**
- Create/evolve in `core/designsystem`: `HikariDimensions.kt`, `HikariBreakpoints.kt`, poster rail/grid family, `HikariSearchField.kt`, `HikariIconAction.kt`, `HikariFilterChip.kt`, `HikariFloatingDestinationNav.kt`, `HikariFocusedHeader.kt`, `HikariActionSheet.kt`, `HikariChoiceSheet.kt`, `HikariValueRow.kt`, `HikariInfoRow.kt`.
- Modify `HikariPullToRefresh.kt` to resource-backed generic labels.
- Add DS strings/resources and tests; migrate only proven repeated callers.

- [ ] **RED architecture.** DS rejects Catalog/Library/Reading/runtime/Room/WorkManager/OkHttp/artwork acquisition imports.
- [ ] **RED layout/a11y.** 600dp compact/wide policy and >=48dp interaction target are shared; poster skeleton geometry matches poster frame.
- [ ] **Implement small primitives.** Caller owns labels, semantic state, execution and navigation.
- [ ] **GREEN.** DS tests + selective migrated screenshots.
- [ ] **Self-review.** No flag-heavy universal row, `GlobalUiState`, generic paging state or premature `:core:presentation` module.
- [ ] **Commit/stop:** `feat(designsystem): add step3 shared presentation policy`.

---

## Authorized maintenance interlude after Task 10 — Module-local cleanup

This is **not** a new Step 3 feature task and does not renumber Tasks 11-13. The user has explicitly
authorized a repository cleanup interlude before Task 11. During this interlude:

- `docs/project/file-package-ownership-policy.md` is the canonical source-placement/ownership policy;
- `docs/superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md` owns campaign-wide cleanup
  constraints;
- `docs/internal/checkpoints/hikari-repository-module-cleanup.md` owns the cleanup resume/evidence boundary;
- exactly one primary module is selected and audited per cleanup turn;
- the target package/file tree must be approved before source implementation;
- module additions/removals, dependency-edge changes, UI/feature redesign, DB semantic changes, and
  Reading Source work are out of scope;
- each selected module receives a bounded module-specific implementation plan only after its audit is
  approved; there is intentionally no placeholder-heavy all-module plan;
- completion of one module never authorizes the next module automatically.

Task 11 remains frozen until the cleanup interlude is explicitly ended.

---

## Task 11 — Reading Source domain, binding DB and owner-level serialization

**Goal:** Admit Story Reading configuration truth with no Chapter/Reader consumption dependency.

**Files:**
- Add modules `:reading:domain`, `:reading:storage`, `:reading:runtime`.
- Domain exact files: `ReadingSourceId.kt`, `LanguageTag.kt`, `ReadingSourceDescriptor.kt`, `StoryReadingBinding.kt`, `ReadingSourceCandidate.kt`, `ReadingSourceSearchCapability.kt`, `StoryReadingPort.kt`.
- Storage exact files: `ReadingDatabase.kt`, `StoryReadingBindingEntity.kt`, `StoryReadingDao.kt`, `RoomStoryReadingStore.kt` plus schema tests.
- Runtime exact files: `ReadingSourceDirectory.kt`, `StoryReadingRuntime.kt`, `StoryReadingMutationOwner.kt`, `DetectSession.kt` plus tests.

**Interfaces:**
```kotlin
data class StoryReadingBinding(
    val storyRef: StorySourceRef,
    val readingSourceId: ReadingSourceId,
    val readingSourceStoryId: String,
    val sourceStoryDisplayTitle: String?,
    val selectedLanguageTag: LanguageTag,
    val verifiedLanguageTags: Set<LanguageTag>,
    val updatedAtEpochMs: Long,
)

data class ReadingSourceCandidate(
    val sourceStoryId: String,
    val title: String,
    val authors: List<String>,
    val aliases: List<String>,
    val mediaType: CatalogMediaType,
    val verifiedLanguageTags: Set<LanguageTag>,
)
```

Candidate artwork is deliberately omitted from the initial domain contract because R1.5 makes it optional and no production Reading provider has yet proved a safe candidate-artwork contract. UI uses deterministic fallback until separately proven.

- [ ] **RED persistence.** One binding per Story provenance; no Catalog FK; clear removes binding + Story-specific language coherently; identical confirmed aggregate = NO_OP/stable updatedAt; changed verified-language snapshot is real update.
- [ ] **RED concurrency.** Same-Story mutations serialized in Reading owner across routes; later accepted explicit command wins after commits; different Stories proceed independently.
- [ ] **RED availability.** Descriptor presence is local control-plane availability; request failure does not mutate descriptor presence or delete committed binding.
- [ ] **Implement `hikari-v2-reading.db`.** Point observation only; schema export; no destructive migration fallback.
- [ ] **Effective precedence helper.** committed binding > route draft > media default. Clear returns to unmapped/default without changing global default.
- [ ] **GREEN.** Assert module graph contains no Chapter/Reader/WorkManager dependency.
- [ ] **Self-review.** Distinct `ReadingSourceId`; no generic SourceId/Catalog-source coercion.
- [ ] **Commit/stop:** `feat(reading): add explicit story reading bindings`.

---

## Task 12 — Theme, App locale and Reading-default preference semantics

**Goal:** Add narrow durable preferences without merging launch-state or provider status into a mega Settings model.

**Files:**
- Add modules `:settings:domain`, `:settings:runtime`.
- Domain exact files: `ThemeMode.kt`, `AppLocalePreference.kt`, `ReadingDefaults.kt`, `ThemePreference.kt`, `AppLocalePreferencePort.kt`, `ReadingDefaultsPort.kt`.
- Runtime exact files: `SettingsDataStore.kt`, `ThemePreferenceStore.kt`, `AppLocalePreferenceStore.kt`, `ReadingDefaultsStore.kt`, `AppLocaleApplier.kt` plus tests.
- Modify app theme root/activity/startup integration.

**Interfaces:**
```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }
sealed interface AppLocalePreference {
    data object System : AppLocalePreference
    data class Specific(val languageTag: String) : AppLocalePreference
}
```

Do not persist a fake Catalog default while only one Catalog authority per media exists. Initial **exposed** app locales are `System` + English only; Vietnamese resources may be authored incrementally but `vi` becomes selectable only in Task21 after the complete active-surface coverage gate passes.

- [ ] **RED same-value no-op.** Same Theme/locale/default -> no DataStore edit, recreation, navigation reset or capability reconfiguration.
- [ ] **RED isolation.** App locale cannot change Reading language, Catalog provenance, Library or trigger Catalog work.
- [ ] **Implement separate Settings DataStore.** Launch-state DataStore remains untouched.
- [ ] **Theme integration.** Do not block accepted first frame on disk; verify no material wrong-theme flash using explicit startup test/evidence.
- [ ] **Locale projection.** `AppLocalePreference` is semantic authority; `AppLocaleApplier` is platform mechanism/projection. Recreation restores route truth.
- [ ] **GREEN.** Focused settings/app tests.
- [ ] **Self-review.** No `AppSettings` stream, no provider probing, no unsupported Catalog-language preference.
- [ ] **Commit/stop:** `feat(settings): add narrow theme locale and reading defaults`.

---

## Task 13 — Shared bounded HTTP transport, INTERNET admission, remote artwork wiring

**Goal:** Admit network capability once at infrastructure level rather than duplicating OkHttp/security code per provider.

**Files:**
- Add OkHttp `5.3.0` alias in `gradle/libs.versions.toml`.
- Add module `:core:network`.
- Create exact files: `BoundedHttpTransport.kt`, `HttpRequestSpec.kt`, `HttpResponse.kt`, `HttpSecurityPolicy.kt`, `HttpFailure.kt`, `OkHttpBoundedTransport.kt` under `core/network/src/main/kotlin/app/openstory/network/` plus tests.
- Modify `app/src/main/AndroidManifest.xml` to add only `android.permission.INTERNET`.
- Modify Step3/foundation merged-manifest policy to admit exactly INTERNET while still rejecting ACCESS_NETWORK_STATE/POST_NOTIFICATIONS/eager initializers.
- Modify `:core:artwork` to depend on `:core:network`; create `NetworkArtworkTransport.kt` and tests.
- Modify composition to create one application-scoped transport/pool and one process admission owner.

**Interfaces:**
```kotlin
data class HttpSecurityPolicy(
    val allowedHttpsHosts: Set<String>,
    val maxRedirects: Int,
    val sensitiveHeaders: Set<String>,
    val crossOriginForwardableHeaders: Set<String> = emptySet(),
)

data class HttpRequestSpec(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val maxResponseBytes: Long,
    val policy: HttpSecurityPolicy,
)

data class HttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val finalUrl: String,
)

sealed interface BoundedHttpResult {
    data class Success(val response: HttpResponse) : BoundedHttpResult
    data class Rejected(val reason: WorkRejectionReason) : BoundedHttpResult
    data class Failed(val failure: HttpFailure) : BoundedHttpResult
}
```

- [ ] **RED manifest/startup.** Permission present only after explicit policy migration; cold FirstRun/Home through local Ready executes zero transport calls.
- [ ] **RED transport.** HTTPS only; host allowlist; <=3 manually validated redirects; each redirect target revalidated; sensitive headers removed across origin unless policy explicitly permits; `retryOnConnectionFailure=false`; 5/10/15s bounds; cancellation; bounded body before consumer parse.
- [ ] **RED admission.** NETWORK/priority uses Task4 active+pending budget; saturated local admission is not mapped to offline/provider failure.
- [ ] **Implement one process HTTP client/pool.** `:core:network` owns OkHttp import; source/artwork modules consume its neutral contract.
- [ ] **Wire artwork.** Visible Home/Catalog cache miss may fetch bytes through shared network transport and artwork provenance policy; API auth/header state is never copied blindly to artwork/CDN origin.
- [ ] **GREEN.** Core network + artwork + startup tests.
- [ ] **Self-review.** `rg 'import okhttp3'` in production must match `core/network` only.
- [ ] **Commit/stop:** `feat(network): admit bounded production transport`.

---

## Task 14 — MangaUpdates production Catalog provider admission and native adapter

**Goal:** Admit the required production Catalog authority only from proven real provider contracts while preserving the exact Step2 source identity.

**Files:**
- Add module `:sources:mangaupdates`.
- Create exact files: `MangaUpdatesCatalogSource.kt`, `MangaUpdatesDescriptor.kt`, `MangaUpdatesApi.kt`, `MangaUpdatesDtos.kt`, `MangaUpdatesMapper.kt`, `MangaUpdatesPaging.kt` plus tests.
- Create: `docs/internal/v2/providers/mangaupdates-catalog-step3.md`.
- Create sanitized deterministic contract fixtures under `sources/mangaupdates/src/test/resources/contracts/`.
- Modify app composition registration/module policy.

**Frozen identity:** `CatalogSourceKey("org.openstory.catalog.mangaupdates")`.

**Blocking provider requirement:** production evidence must prove `Discover + Story Detail + Search` for both `MANGA` and `LIGHT_NOVEL`, including a truthful way to scope/validate results for the requested media without unbounded mixed-media page walking. If any required capability/media cannot be proven, Task14 records `NOT_ADMITTED` and Step3 execution stops for an approved replacement-provider plan; do not coerce WEB_NOVEL, show mixed-media results in the wrong root, or weaken Search semantics.

- [ ] **Provider-evidence gate first.** Record authoritative docs or captured real endpoint evidence/date for Discover/Story/Search, media-type fields and media-scoping/filter behavior. Controlled Step2 JS remains regression reference, not sole production proof.
- [ ] **RED native mapping.** Exact source key/StorySourceRef derivation; WEB_NOVEL rejected; aliases bounded; language tags only from explicit provider metadata—never infer `ja/ko/zh/en` from `manga/manhwa/manhua/OEL` type text.
- [ ] **RED network policy.** API host `api.mangaupdates.com`; artwork hosts recorded separately. Provider maps status/body failures; transport mechanics remain `:core:network`.
- [ ] **Search capability admission.** Admit only when contract proves endpoint/query/paging **and media-scope semantics for both shipped roots**. Client-side filtering of arbitrary mixed pages is insufficient if it can require unbounded zero-delta page walking to find the requested media.
- [ ] **Section capability admission independently.** `POPULAR` and/or `TOP_RATED` become `PAGED` only if evidence proves global ordering/continuation (or explicit global rank). A Hikari `orderby=week_pos/rating` fixture alone is insufficient. `LATEST_UPDATES` remains non-expandable unless separately proven.
- [ ] **Similar remains unsupported** unless the provider record proves a real Similar operation. Configurable Catalog language remains unsupported unless proven.
- [ ] **Register descriptor only.** Startup registration creates no provider request/storage work; the shared core transport may already exist, but first foreground capability demand is the first provider request.
- [ ] **Decision gate.** End Task14 as `ADMITTED + green evidence` or `NOT_ADMITTED + execution stop for replacement-provider design`. Unlike optional Reading Source admission, `NOT_ADMITTED` cannot flow into Task15 because R1.5 requires real Catalog roots/Search.
- [ ] **GREEN when ADMITTED.** Source contract tests + release compile + controlled integration replay.
- [ ] **Self-review.** No JavaScriptEngine/plugin runtime/provider fan-out in release graph.
- [ ] **Commit/stop:** `feat(catalog): admit proven MangaUpdates production authority`.

---

## Task 15 — Explicit-submit Catalog Search

**Goal:** Add Search with frozen authority, bounded transient payload and explicit reload after compaction.

**Files:**
- Create `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/search/CatalogSearchSession.kt` and test.
- Create feature exact files under `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/search/`: `CatalogSearchEntryPoint.kt`, `CatalogSearchViewModel.kt`, `CatalogSearchUiState.kt`, `CatalogSearchScreen.kt`, `CatalogSearchPosterUi.kt`, `CatalogSearchTestTags.kt` plus tests.
- Extend AppRoute with primitive `SearchRouteWire`; create `SearchRouteCodec.kt` in app composition navigation package.

**Domain args/state:**
```kotlin
data class CatalogSearchRouteArgs(
    val mediaType: CatalogMediaType,
    val authority: CatalogSourceKey,
    val routeEntryId: RouteEntryId,
)

data class DisplayedSearchResult(
    val query: String,
    val items: List<CatalogTransientStory>,
    val nextContinuation: String?,
)

sealed interface SearchRequestState {
    data object Idle : SearchRequestState
    data class Running(val query: String) : SearchRequestState
    data class Failed(val query: String, val issue: CatalogIssueUi) : SearchRequestState
}

sealed interface AppendState {
    data object Idle : AppendState
    data object Running : AppendState
    data object EndReached : AppendState
    data class Failed(val issue: CatalogIssueUi) : AppendState
}
```

- [ ] **RED zero-work.** Open/focus/typing/clear -> zero request; clear edits input only; Enter/Search submits exactly once.
- [ ] **RED identity/latest-wins.** Query B supersedes A; stale A cannot publish; B failure may retain displayed A clearly labeled A.
- [ ] **RED lifecycle/compaction.** RETAINED cancels request/append neutrally; retained payload restores if still budgeted; compacted route retains input/submitted identity and requires explicit Reload/Search, never hidden replay.
- [ ] **RED pagination guard.** Same/cyclic token and bounded zero-delta pages stop; one append demand -> max one page request.
- [ ] **Implement transient session.** <=60 retained items, exact ref dedupe, no Room table, no per-card detail enrichment. Root Search icon is added only now and routes to this real destination.
- [ ] **GREEN navigation.** Search→Story→Back restores retained query/result/scroll with zero request while payload remains.
- [ ] **Self-review.** No debounce remote Search, provider fan-out or lookup of a newly changed default after route creation.
- [ ] **Commit/stop:** `feat(catalog): add explicit bounded search`.

---

## Task 16 — Truthful Section Listing

**Goal:** Add Listing only for sections Task14 actually admitted as expandable/ranked.

**Files:**
- Create `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/listing/CatalogSectionSession.kt` plus tests.
- Create exact feature files under `feature/catalog/.../listing/`: `SectionListingEntryPoint.kt`, `SectionListingViewModel.kt`, `SectionListingUiState.kt`, `SectionListingScreen.kt`, `RankedStoryRow.kt`, `SectionListingTestTags.kt` plus tests.
- Extend AppRoute with primitive `ListingRouteWire`; add `ListingRouteCodec.kt`.
- Modify Discover section action wiring.

- [ ] **RED descriptor truth.** `See All` appears **only** for descriptors with `SectionExpansion.PAGED`; tests consume Task14 capability record rather than hardcoding Popular/Top Rated as guaranteed.
- [ ] **RED append.** Single-flight per continuation; exact source-local dedupe; delta append only; failure retains pages; no pull-to-refresh state machine.
- [ ] **RED ranking.** Ranked presentation only when provider contract says globally ordered/ranked; no per-page local sort masquerading as global rank.
- [ ] **RED lifecycle/continuation.** Retention cancels append; late result discarded; cyclic/nonadvancing/zero-delta walking bounded.
- [ ] **GREEN navigation.** Discover→Listing→Story→Back restores pages/scroll; Listing→Back restores Discover position; navigation itself causes no page1/Discover refetch.
- [ ] **Self-review.** No cumulative rebuild over all prior pages except bounded exact-identity set/index maintenance; no transient page persistence.
- [ ] **Commit/stop:** `feat(catalog): add capability-gated section listings`.

---

## Task 17 — Story non-reading UX completion and Similar gate

**Goal:** Complete Story interactions that are real before Reading-source workflow exists; do not show action-looking Reading rows prematurely.

**Files:**
- Modify/create in `:feature:story`: hero, action row, tabs, synopsis, metadata, `StoryMoreSheet.kt`, Share/Copy-title handlers, Similar facet/state.
- Create `catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/similar/CatalogSimilarSession.kt` plus tests.
- Update Story tests/screenshots/a11y tests.

**Behavior at Task17 completion:**
```text
Hero overlay: Back only
Actions: Add/Saved, Share, More
Tabs: Synopsis enabled; Chapters true-disabled; Similar enabled only if descriptor supports it
Read from Chapter 1: true-disabled
Share: title only unless an app-owned stable public link has been separately admitted
More: Catalog information + Copy title only
```

Reading Source / Language rows are added in Task19 only when their destinations/actions exist.

- [ ] **RED progressive Story.** Preview title/artwork shell first; optional detail omission is not acquisition failure; aliases/language tags bounded.
- [ ] **RED Similar.** MangaUpdates real descriptor normally disabled unless Task14 proved Similar. Controlled supported fixture: NotRequested until first tab activation, one load, retained result, retry same Story/authority, no durable import.
- [ ] **RED nested Story.** Parent Similar tab/result/scroll restored; parent in-flight Similar cancels on RETAINED and never cross-publishes to child.
- [ ] **Implement More functional subset only.** Opening probes nothing. Catalog information is small product provenance, not diagnostics. Copy title is local.
- [ ] **Implement Share.** No provider URL fallback.
- [ ] **GREEN visual/a11y.** Hero Back contrast/insets/48dp; remove Heart/Favorite; no mirrored filler action; true-disabled Read/Chapters/unsupported Similar.
- [ ] **Self-review.** No Chapter/Reader dependency and no visible dead Reading row.
- [ ] **Commit/stop:** `feat(story): complete admitted non-reading interactions`.

---

## Task 18 — Reading Source provider admission gate; MangaDex only if proven

**Goal:** Attempt a production Manga Reading Source admission without weakening R1.5's candidate-specific verified-language contract or using Chapters to manufacture compatibility evidence.

**Files if evidence supports MangaDex:**
- Add module `:sources:mangadex`.
- Create exact files: `MangaDexReadingSource.kt`, `MangaDexDescriptor.kt`, `MangaDexApi.kt`, `MangaDexDtos.kt`, `MangaDexCandidateMapper.kt`, `MangaDexPaging.kt` plus tests.
- Create `docs/internal/v2/providers/mangadex-reading-step3.md`.
- Create sanitized contract fixtures under `sources/mangadex/src/test/resources/contracts/`.
- Modify app ReadingSourceDirectory registration only if record ends `ADMITTED`.

**Frozen identity if admitted:** `ReadingSourceId("org.openstory.content.mangadex")`.

**Important evidence fact:** the supplied V1 MangaDex mapper/search fixture does not contain candidate-specific verified languages and the repository does not establish an `availableTranslatedLanguages` field. Do not encode that field name into production until current provider contract evidence proves it.

- [ ] **Evidence gate before module registration.** Record current authoritative OpenAPI/contract revision or captured real search/title response and identify the **exact non-Chapter response field(s)** that prove candidate-specific available/verified languages.
- [ ] **Decision branch.** If no suitable non-Chapter candidate-specific language metadata is proven, write `NOT_ADMITTED`, do not register MangaDex, and keep Reading Source UI truthful-unavailable. This is not permission to query Chapters or copy broad manifest languages.
- [ ] **If ADMITTED — RED candidate contract.** Exact field parses to bounded normalized `LanguageTag`s; empty candidate set remains visible but unconfirmable; broad source descriptor never substitutes.
- [ ] **If ADMITTED — RED no-Chapter.** Transport trace rejects any `/feed`, chapter, aggregate, AtHome or content-consumption endpoint during Search/Detect.
- [ ] **If ADMITTED — implement Search/paging/security** through `:core:network`; preserve provider result order; no confidence scoring/alias fan-out/manual URL mapping.
- [ ] **GREEN or truthful absence.** Checkpoint must record one of two closed outcomes: `ADMITTED + tests green` or `NOT_ADMITTED + no production registration`. No unresolved middle state may pass Task18.
- [ ] **Self-review.** No inferred language compatibility and no old V1 plugin/content engine dependency.
- [ ] **Commit/stop:** `feat(reading): resolve production detect-source admission`.

---

## Task 19 — Reading Source picker, Detect, confirmation, language and clear-to-default

**Goal:** Complete Story-specific mapping UX over local descriptors/binding truth; operate correctly even when Task18 admitted no production source.

**Files:**
- Add module `:feature:reading`.
- Create exact files: `ReadingSourcePickerSheet.kt`, `DetectEntryPoint.kt`, `DetectViewModel.kt`, `DetectUiState.kt`, `DetectScreen.kt`, `ReadingCandidateUi.kt`, `ReadingCandidateCard.kt`, `ReadingSourceConfirmation.kt`, `ReadingLanguageChoiceSheet.kt`, `ReadingTestTags.kt` plus tests.
- Extend AppRoute with primitive `DetectRouteWire`; create `DetectRouteCodec.kt`.
- Modify Story reducer/More sheet to add Reading Source + Language rows only now.

**Domain args:**
```kotlin
data class DetectRouteArgs(
    val storyRef: StorySourceRef,
    val originMediaContext: CatalogMediaType,
    val draftSourceId: ReadingSourceId,
    val routeEntryId: RouteEntryId,
)
```

- [ ] **RED picker zero-work.** Descriptor read only; no probe/Search; committed source remains visibly stable while draft changes. No-source state is informational and has no dead Manage Sources control.
- [ ] **RED More semantics.** Unmapped default is labeled `Default: X`, never committed. Mapped+present, mapped+absent, default-unavailable states are distinct. Language default is not presented as candidate-verified.
- [ ] **RED initial Detect.** Pressing Detect is explicit command -> exactly one primary-title Search on one selected draft source. After that typing/alias suggestion changes input only; explicit Search required.
- [ ] **RED paging/failure.** Empty != Failure; false offline is not invented without evidence; append failure retains candidates; continuation guards apply; old committed binding always survives.
- [ ] **RED confirmation/language.** Selection does not commit. Source+sourceStory+compatible language commit atomically. Preselection: current Story language if verified, else media default if verified, else sole candidate language, else explicit choice.
- [ ] **RED clear/use-default.** Clears Story binding only, preserves global defaults/Library/Catalog and starts zero Step4 work.
- [ ] **RED lifecycle/compaction.** RETAINED cancels Detect Search/append neutrally; compacted explicit-submit state does not silently replay.
- [ ] **GREEN focused flow.** If no provider admitted, truthful no-source state passes. If admitted, full picker→Detect→confirm flow passes.
- [ ] **Self-review.** No source fan-out, automatic alias request, Chapter call or route-local durable-binding mutex.
- [ ] **Commit/stop:** `feat(reading): add explicit story mapping flow`.

---

## Task 20 — Settings UI, App menu, About and app-level focused routing

**Goal:** Complete app-level non-content navigation and settings surfaces without activating capabilities.

**Files:**
- Add module `:feature:settings`.
- Create exact files: `SettingsEntryPoint.kt`, `SettingsScreen.kt`, `GeneralSettingsScreen.kt`, `CatalogSourcesScreen.kt`, `ReadingSourcesScreen.kt`, `AboutScreen.kt`, `ThemeChoiceSheet.kt`, `AppLanguageChoiceSheet.kt` plus tests.
- Create app presentation `app/src/main/kotlin/app/openstory/ui/AppMenuSheet.kt`.
- Extend App focused-destination routing and tests.

**IA:**
```text
Settings
  General -> Theme, App language
  Reading -> Catalog Sources, Reading Sources
  About
```

- [ ] **RED focused routing.** Open Settings/About from any root/child; Back returns exact origin; no separate Settings stack per root.
- [ ] **RED zero-work.** Local preferences/descriptors only; zero Catalog activation/Detect/artwork/provider-health probe.
- [ ] **Implement source settings truthfully.** Single Catalog per media -> informational row/no fake default picker. Reading rows reflect Task18 admission/default possibilities. No Catalog-language setting unless a real Catalog capability later proves one.
- [ ] **Immediate apply.** Same-value no-op; no full-screen Save.
- [ ] **App menu.** Hikari identity + Settings + About only; semantic label `App menu`, never Profile/Account; opening does not preload Settings.
- [ ] **About real content only.** No placeholder Privacy/Terms links.
- [ ] **GREEN.** Modal dismissal/focus/no-commit-on-dismiss tests.
- [ ] **Self-review.** No Reader/Downloads/progress/plugin-management/diagnostics placeholders.
- [ ] **Commit/stop:** `feat(settings): complete app menu settings and about`.

---

## Task 21 — Localization, accessibility, responsive policy and screenshot freeze

**Goal:** Finish the active Step3 surface before exposing Vietnamese as a selectable locale.

**Files:**
- Add/update `values/strings.xml` + `values-vi/strings.xml` in app/features/Design System.
- Update all active Step3 UI copy/semantics.
- Extend screenshot evidence harness and accessibility/font-scale tests.
- Modify settings locale allowlist to add `vi` only after coverage test passes.

- [ ] **RED coverage/literal scan.** Active Step3 UI/accessibility copy resource-backed except provider/user content/test tags. Resource-key parity/coverage proves every exposed locale complete.
- [ ] **RED a11y.** True disabled controls, selected states, grouped poster semantics, rank only when real, skeleton exclusion, modal focus containment/restoration, >=48dp targets.
- [ ] **RED large-font/long Vietnamese.** Home filters, Story actions/tabs/More, Settings, picker/confirmation use min heights/no clipping.
- [ ] **Screenshot matrix.** Selective compact/wide light/dark + Vietnamese large-font examples for Home, Discover, Search, admitted Listing (if any), Story, Detect/no-source, Settings.
- [ ] **Expose Vietnamese atomically.** Add `vi` to selectable locale list only after all coverage/a11y/screenshots required by this task are green.
- [ ] **Visual review.** Preserve Step2 blueprint except explicit R1.5 changes.
- [ ] **Self-review.** No hardcoded Refresh/Refreshing or action-looking no-op outside true-disabled Step4 affordances.
- [ ] **Commit/stop:** `feat(ui): freeze step3 localization accessibility and responsive polish`.

---

## Task 22 — Aged-state, lifecycle, query, security and resource hardening matrix

**Goal:** Prove physical-work/lifetime rules before benchmarking.

**Files:**
- Extend app/catalog/library/reading/artwork/network/source integration tests and diagnostics.
- Add aged Library/Catalog/deep-history/concurrent-pressure fixtures.
- Extend Step3 verifier/query diagnostics where a static guard can catch regressions.

**Required deterministic evidence:**
```text
Home cold local Ready: Catalog remote=0, Reading Detect=0, Chapter/Reader=0
Search open/typing: remote=0
Story before Similar tab: Similar=0
Reading picker: Detect=0
inactive root construction: remote activation=0
retained route depth: route collectors do not grow linearly
route entries <= global/root bounds
retained payload <= process budget
Library 10k fixture: indexed/windowed query <=60 rows, no user-space full-corpus transform
Catalog retention: bounded batch/index work, no full historical reaggregation
same artwork identity: one fetch/decode pipeline
network/decode admission: active+pending bounded; foreground not starved
Main: no DB/network/large decode/collection-scale transform
redirects: <=3, hostile host blocked, sensitive headers not forwarded implicitly
pagination: cyclic/nonadvancing/zero-delta walking bounded
route cancellation: neutral lifecycle, late results cannot repopulate inactive compacted route
```

- [ ] Deep navigation stress >100 attempted pushes/root switches and bounded saved-state size.
- [ ] Collector instrumentation across repeated ACTIVE↔RETAINED.
- [ ] Library 10k query count/`EXPLAIN QUERY PLAN` evidence.
- [ ] Aged multi-authority Catalog retention slope/batch evidence.
- [ ] Catalog+Detect+artwork resource-pressure/fairness evidence; if no Reading provider was admitted, use controlled Reading capability fixture for scheduler pressure only and label it non-production.
- [ ] Network adversarial redirect/body/timeout/cancel/header tests.
- [ ] Execution-owner tracing.
- [ ] Self-review: any operation whose slope is primarily unrelated global/history state blocks acceptance.
- [ ] **Commit/stop:** `test(step3): harden lifetime scaling and resource bounds`.

---

## Task 23 — Step 3 performance journeys, no-growth debt and profile regeneration

**Goal:** Measure the final production graph and prevent Step2 debt from being hidden by new baselines.

**Files:**
- Extend `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`, `HikariMacrobenchmark.kt` and evidence/preparation helpers.
- Add Step3 performance evidence under `docs/internal/v2/`.
- Regenerate baseline/startup profiles through the accepted profile task.

**Journeys:**
```text
cold launch -> Home
Home -> Manga
Manga -> Home
Manga -> Search
Search -> Story -> Back
Listing -> Story -> Back (only if production Listing admitted; otherwise controlled capability benchmark is labeled fixture)
Story A -> Similar-supported fixture -> Story B -> Back
rapid root switching
deep Story chain
large Library local query/filter
Reading Detect (production if admitted; otherwise controlled fixture clearly labeled)
concurrent Catalog + Reading fixture + artwork pressure
production INTERNET-permission startup/FirstRun zero-request proof
```

- [ ] Add markers for active runtimes/requests/collectors/admission occupancy/query counts/thread owners.
- [ ] Re-run inherited Step2 journeys under final graph and compare against accepted Task16 debt; no silent rebaseline.
- [ ] Measure aged-state slopes, not only fresh medians.
- [ ] Tune only seed ceilings with recorded evidence; never remove finite bounds.
- [ ] Regenerate/validate final Baseline/Startup Profiles.
- [ ] Self-review: accepted red remains explicit no-growth debt; retirement requires equivalent-or-stronger evidence.
- [ ] **Commit/stop after user-owned evidence review:** `perf(step3): freeze base app performance evidence`.

---

## Task 24 — Final R1.5 acceptance and repository freeze

**Goal:** Close the exact R1.5 matrix and leave one authoritative Step4 boundary.

**Files:**
- Update Step3 checkpoint, `docs/project/current-state.md`, `docs/implementation/current-roadmap.md`, architecture graph docs/evidence references.
- Do not create a duplicate checkpoint.

- [ ] **Build acceptance matrix.** Map R1.5 §22 + §25.1–25.8 to exact tests/evidence. Provider-dependent rows record `ADMITTED` or truthful `UNSUPPORTED/NOT_ADMITTED`; required Catalog authority for both shipped media cannot be waived.
- [ ] **Verification-script coverage audit.** Prove Task0 dynamic `verifyStep3FastModules` / `verifyStep3FullModules` sees every currently admitted production module; no manual Step2-only module list remains in fast/full scripts.
- [ ] **Focused closure checks.** `git diff --check`, exact docs/path/graph checks.
- [ ] **Hand user-owned final commands.** Include `verifyArchitecture`, `:app:verifyFoundation`, `verifyStep3BuildSurface`, build-logic tests, updated fast/full scripts, connected matrix, screenshot acceptance, benchmark/profile gates.
- [ ] **Do not close** while required gate is NOT RUN/FAIL, Task14 required Catalog authority is unresolved or NOT_ADMITTED without an approved replacement, or Task18 is left in an unresolved middle state.
- [ ] **Final topology scan.** No V1 persistent top-level composition, broad observeAll, canonical/provider fan-out, feature-local image runtime, Chapter/Reader reachability, dead transitional wrappers, OkHttp outside core network, or undocumented performance debt.
- [ ] **Freeze.** Step3 accepted; roadmap next boundary Step4 design/admission only; record final SHA/profile/provider evidence.
- [ ] **Commit/stop:** `docs(step3): freeze base app ux ui completion`.

---

# Dependency order

```text
0 governance
 -> 1 App Shell roots/fixed Discover
   -> 2 child routes + Story + lifecycle
     -> 3 Catalog host/store lifetime
       -> 4 process work/retention bounds
         -> 5 shared artwork
           -> 6 Catalog contracts/schema
             -> 7 Library -> 8 Home -> 9 Story Library -> 10 Design System
             -> 11 Reading binding -> 12 Settings preferences
             -> 13 shared network + INTERNET + remote artwork
               -> 14 MangaUpdates Catalog admission [blocking ADMITTED gate]
                 -> 15 Search -> 16 Listing -> 17 Story completion
               -> 18 Reading provider admission
                 -> 19 Reading UX
                   -> 20 Settings/App menu
                     -> 21 localization/a11y/visual freeze
                       -> 22 adversarial hardening
                         -> 23 performance/profile
                           -> 24 final acceptance
```

No canonical Task runs in parallel by default because `AGENTS.md` requires one Task boundary per turn. Independent reasoning does not authorize parallel repository mutation.

---

# Capability admission record template

Each owning task appends this record to the Step3 checkpoint:

```text
Capability:
Activation trigger:
Authority/request identity:
ACTIVE observer/work:
RETAINED behavior:
RELEASED behavior:
CPU owner:
Blocking I/O/network owner:
Single-flight/latest-wins key:
Physical scaling dimension:
Active-work bound:
Pending-work bound:
Cancellation/late-completion behavior:
Durable truth touched:
Explicitly forbidden side effects:
Evidence:
```

Required records: App Shell lifecycle, Home/Library, artwork, core network, production MangaUpdates, Catalog Search, each admitted Section Listing, Similar, Reading binding, Reading provider admission outcome, Detect, Settings preference application.

---

# Final user-owned verification shape

Task24 must provide the exact final class/task list; this is the shape, not permission for an agent to run broad gates during ordinary Task N work.

```powershell
.\gradlew.bat verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface :build-logic:test --no-daemon
& 'C:\Program Files\Git\bin\bash.exe' scripts/verify-fast.sh
& 'C:\Program Files\Git\bin\bash.exe' scripts/verify.sh

$env:ANDROID_SERIAL='<device>'
.\gradlew.bat :app:connectedDebugAndroidTest `
  :feature:catalog:connectedDebugAndroidTest `
  :feature:library:connectedDebugAndroidTest `
  :feature:story:connectedDebugAndroidTest `
  :feature:reading:connectedDebugAndroidTest `
  :feature:settings:connectedDebugAndroidTest --no-daemon

# Task23/24 checkpoint supplies exact benchmark/profile tasks and variants.
```

---

# Deep self-review after V1/V2 re-audit

## Coverage result

| R1.5 concern | Tasks | Result |
|---|---:|---|
| Step1 startup handoff | 1, 12, 13, 23 | PASS |
| App Shell + independent histories | 1–2, 20, 22 | PASS |
| serializable small route state / process restoration | 1–2, 15–16, 19 | PASS |
| retained payload/history/collector bounds | 2, 4, 15–16, 19, 22 | PASS |
| Catalog multi-authority/store lifetime | 3 | PASS |
| artwork no-activation/coalescing | 5, 13, 22 | PASS |
| Catalog aliases/languages/sections | 6 | PASS |
| Library durable truth/query scaling/idempotency | 7–9, 22 | PASS |
| Search explicit-submit/latest-wins/transient | 15 | PASS |
| Listing truthful capability/rank | 14, 16 | PASS |
| Story progressive/actions/tabs/Similar | 2, 9, 17 | PASS |
| Reading binding/default/clear/serialization | 11, 19 | PASS |
| Reading provider language proof/no Chapters | 18–19 | PASS |
| Settings/theme/App locale/About | 12, 20–21 | PASS |
| three language domains/no fake Catalog language | 6, 11–12, 20 | PASS |
| Design System boundaries | 8, 10 | PASS |
| production network/security | 13–14, 18, 22 | PASS |
| cross-capability active+pending fairness | 4, 13, 22 | PASS |
| Main-thread execution ownership | 7, 13–19, 22 | PASS |
| localization/a11y/responsive | 21 | PASS |
| no-growth Step2 debt/profile regeneration | 23 | PASS |
| final repository freeze | 24 | PASS |

## Audit findings fixed in R1.1

1. **Live Step2 verifier conflict:** old plan said “keep Step2 verifier active,” but `Step2BuildSurfaceVerifier` compares exact current module set; Task1 would immediately fail it. R1.1 archives Step2 graph evidence and makes Step3 verifier the live authority.
2. **Wrong build-logic owner:** old Task0 targeted `FoundationConventionPlugin.kt` for `verifyArchitecture`; actual live aggregation is `ArchitectureConventionPlugin.kt`. Corrected.
3. **Package-cycle/static scripts were Step2-only:** dynamic package inputs are required, and final broad scripts get an explicit coverage meta-gate.
4. **App structural line cap would fail immediately:** current app is 335/377 Kotlin lines. R1.1 evolves—not deletes—the structural ratchet into total + package-prefix budgets before App Shell growth.
5. **NavKey/domain serialization mismatch:** V2 domain identities are not serializable. R1.1 uses primitive `@Serializable` route wires + validated codecs, avoiding serialization pollution of domain models.
6. **V1 retained-root topology risk:** Navigation3 is retained as stack mechanics, but Navigation ViewModel-store retention and `PersistentTopLevelNavDisplay`-style multi-root composition are explicitly excluded.
7. **Discover media authority was not actually removed:** old Task1 omitted `DiscoverViewModel/DiscoverUiState`; R1.1 removes `selectedMediaType/selectMedia` in Task1.
8. **Lifecycle was tied too loosely to Compose:** R1.1 adds an explicit app-owned `RouteLifecycleSource`; route session lifetime is keyed by `RouteEntryId`, not offscreen composition.
9. **Work-admission API could misclassify saturation:** old generic `run(): T` had no bounded-queue rejection contract. R1.1 adds explicit `Rejected(SATURATED)` so local capacity is not shown as offline/provider failure.
10. **Retention budget captured business callbacks:** old `onCompact` callback could retain feature sessions/globalize semantics. R1.1 stores accounting only and returns IDs for owners to compact outside the budget lock.
11. **Catalog work was incorrectly labeled NETWORK:** Catalog runtime can be local/storage/fixture. R1.1 puts NETWORK admission only at concrete HTTP transport.
12. **HTTP/security duplication:** old plan put one bounded client in MangaUpdates and another in MangaDex while artwork still lacked a release transport. R1.1 adds one `:core:network`, one process pool/admission path, and wires artwork through it.
13. **MangaUpdates capabilities were overclaimed:** Step2 fixture proves Hikari mapping, not upstream global ranking/continuation. R1.1 makes Search/Listing capability admission provider-evidence gated; required Manga/LN Catalog authority still blocks freeze if unproven.
14. **Source identity drift risk:** exact Catalog key is frozen to `org.openstory.catalog.mangaupdates`; conditional MangaDex Reading ID is frozen to `org.openstory.content.mangadex`.
15. **MangaDex language field was invented by the old plan:** neither supplied repository proves `attributes.availableTranslatedLanguages`. R1.1 removes the assertion and makes exact non-Chapter candidate-language metadata a provider admission gate; failure yields truthful no-source UI, not a lowered contract.
16. **Undefined plan types:** `RetentionLease`, `LibraryCursor`, `LibrarySnapshot`, `ReadingArtworkRef`, `SectionExpansion`, `DisplayedSearchResult`, `SearchRequestState`, `AppendState` were previously used without definitions. R1.1 defines/replaces them explicitly and removes candidate artwork until a provider proves it.
17. **Premature locale exposure:** old Task12 exposed Vietnamese before full active-surface localization. R1.1 exposes it only after Task21 coverage/a11y/visual gates.
18. **Story More would contain premature Reading actions:** Task17 now shows only functional Catalog info/Copy title; Task19 adds Reading rows when real flows exist.
19. **Production artwork network gap:** Task13 now provides concrete bounded remote artwork transport while preserving Home local Ready.
20. **Broad verification could silently omit Step3 modules:** Task0 now replaces Step2 manual module lists with dynamic Step3 fast/full aggregate tasks; Task24 verifies the live graph is fully covered before freeze.

## Contradiction scan

- FirstRun bypassed by Home default: **NO**.
- Feature Catalog still owns media/root navigation after Task1: **NO**.
- NavKey stores nonserializable domain/runtime objects: **NO**.
- Retained root means retained full Compose/ViewModel tree: **NO**.
- Home artwork activates Catalog to resolve policy: **NO**.
- Library stored as Catalog flag/FK: **NO**.
- Home per-keystroke full-corpus transform: **NO**.
- Snapshot enrichment bumps `savedAt`: **NO**.
- Search/Detect auto query while typing: **NO**.
- Default Catalog hot-swaps retained authority: **NO**.
- Reading default rewrites committed binding: **NO**.
- Detect fan-out/automatic aliases: **NO**.
- Candidate language copied from broad source metadata: **NO**.
- Chapters used to make Step3 mapping contract pass: **NO**.
- Similar faked with Search: **NO**.
- Listing assumed from a shelf label/orderby parameter: **NO**.
- Transient result pages permanently imported for Back: **NO**.
- Equivalent artwork consumers duplicate pipeline: **NO**.
- Bounded active permits with unbounded waiters: **NO**.
- Artwork backlog starves foreground: **NO**.
- INTERNET permission means eager startup networking: **NO**.
- Cross-origin redirect implicitly forwards source credentials: **NO**.
- OkHttp clients duplicated per provider: **NO**.
- Lifecycle cancellation shown as offline/provider failure: **NO**.
- Step2 red performance debt silently rebaselined: **NO**.

## Placeholder/type scan

The revised plan has a clean placeholder scan: no unresolved implementation marker, no cross-task shorthand that hides required steps, and no unverified provider field presented as fact. Provider-dependent capability uncertainty is represented as an explicit **ADMITTED / NOT_ADMITTED gate**, which is a closed implementation outcome rather than deferred ambiguity.

**Self-review result:** **PASS after R1.1 corrections.** The plan is now implementable from Task0 without a known governance contradiction, navigation serialization leak, duplicated transport owner, invented provider field, premature action surface, or known V1 global/lifetime topology regression.
