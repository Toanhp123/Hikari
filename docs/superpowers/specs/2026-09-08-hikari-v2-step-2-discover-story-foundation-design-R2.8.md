# Hikari V2 Step 2 — Discover + Story Detail Foundation Design

Date: 2026-09-08
Status: **REVISED — TASK 14 BLUEPRINT APPROVED; TASKS 0-13 ACCEPTED; TASK 14 READY TO IMPLEMENT**
Branch baseline: `v2/foundation-clean-boot`
Step 1 runtime/source baseline: `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`
Revision: **R2.8 — Task 14 controlled Design System visual refresh + Dantotsu composition + user-owned visual acceptance (2026-09-11)**

---

## 1. Purpose

Step 2 establishes Hikari V2's first real product journey without reopening the V1 application runtime.

The user-visible mission is deliberately simple:

```text
launch Hikari
    ↓
FirstRun when required by the Step 1 install-state contract
    ↓
Discover
    ↓
browse multiple semantic sections with real cover artwork
    ↓
select a Story
    ↓
Story Detail metadata
    ↓
Back
    ↓
Discover with stable visual continuity
```

The architectural mission is stricter:

> Add a real, persistence-backed Discover → Story Detail capability to the accepted Step 1 clean-boot shell while preserving demand activation, bounded working sets, narrow reads, coherent observation, bounded image continuity, and measurable startup/runtime cost.

Step 2 is not a port of V1 `feature/catalog`, V1 Room, V1 Home aggregation, V1 canonical orchestration, or V1 plugin runtime. V1 supplies product semantics, invariants, tests worth recreating, and negative performance evidence. V2 owns the new runtime shape.

---

## 2. Authority and supersession

This design is subordinate to the current V2 authorities except where this section explicitly records a Step 2 supersession:

1. `docs/project/approved-product-design.md` for product/domain invariants.
2. `docs/project/current-state.md` for the implemented Step 1 boundary.
3. `docs/internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md` for accepted Step 1 evidence.
4. `docs/internal/v2/capability-admission-contract.md` for Step 2+ capability admission.
5. `docs/internal/v2/v1-salvage-ledger.md` for KEEP/REDESIGN/DROP inheritance.
6. `docs/internal/v2/startup-baseline-2026-09-07.md` for the immutable Step 1 performance comparison point.

### 2.1 Explicit Step 2 supersession — Light Novel is enabled

Step 2 explicitly supersedes the earlier Discover delivery restriction that kept `LIGHT_NOVEL` visible but disabled.

From Step 2 onward:

- `MANGA` is enabled;
- `LIGHT_NOVEL` is enabled;
- both are first-class Discover presentation/read keys;
- each media type owns an independent bounded persisted Discover scope;
- switching media type may bootstrap only the newly selected scope when that scope is durably `Absent`; `Published(empty)` does not auto-bootstrap.

The authoritative product-design document/amendment must be updated in the same implementation change that admits Step 2 so that no older `LIGHT_NOVEL visible but disabled` rule remains active by accident.

This supersession changes only media enablement. It does not admit Search, Chapters, Reader, multi-source fusion, or production plugin runtime.

### 2.2 Step 2 milestone classification

Step 2 is the first **real internal/product vertical slice** of Hikari V2, but it is **not yet a ship-ready remote-catalog release milestone**.

The deterministic local acquisition source is development/benchmark/test wiring only. The final real-plugin proof is isolated integration-test wiring only. Therefore Step 2 acceptance proves the production-shaped persistence/read/UI/image/runtime boundaries, but does not claim that a release build already owns a production remote acquisition source.

Consequences:

- a production remote Catalog acquisition source is a later capability admission;
- Step 2 must not smuggle a V1 plugin runtime into the release graph merely to make the milestone appear remotely usable;
- the main/release manifest does not gain `android.permission.INTERNET` solely for an isolated integration test;
- test/integration wiring may declare the minimum network permission/environment needed by the real-plugin probe without changing the production app graph.

Historical V1 sources are evidence, not execution instructions. In particular this design draws from:

- accepted Discover semantic-feed behavior;
- Content State Contract retained-content semantics;
- Wave 05 Home/Story Detail source-preservation behavior;
- Discover Performance Recovery findings;
- the Whole-App Performance Big Update root-cause inventory;
- the Structural Simplification deep audit.

This document does **not** admit `:catalog:model` or `:catalog:engine` into the Step 2 runtime. They remain quarantine/reference modules until a separate Catalog Engine/Model admission explicitly proves that their cost shape is appropriate.

At R2.8, Tasks 0-13 are already completed and accepted. Task 14's visual/IA design is approved and its implementation plan is the next runnable Step 2 boundary; Task 14 implementation has not run yet. `docs/implementation/current-roadmap.md` must therefore point at Task 14 rather than the historical pre-Step-2 gate.

### 2.3 Scoped Step 2 Design System admission before benchmark

Step 2 now admits a **minimal root-and-Catalog-consumed `:core:designsystem` slice before local screenshot/correctness freeze and before performance/profile benchmarking**. This is intentionally narrower than the historical V1 design-system module. The purpose is to establish one app-wide visual environment now, without reintroducing V1 runtime/data ownership or growing a speculative component catalog before a caller exists.

The admission preserves only the V1 visual/interaction semantics that have a proven Step 2 caller:

- `HikariTheme`, which configures the reviewed light/dark Material3 color scheme, typography, and shapes without runtime font/resource I/O;
- one public immutable `HikariSpacing` scale for spacing values that are genuinely repeated across the Step 2 surfaces, exposed as a stable singleton through `MaterialTheme.hikariSpacing` rather than a CompositionLocal that never varies in Step 2;
- one domain-neutral segmented single-choice control for Manga/Light Novel selection **as the Task 13 migration primitive only; Task 14 retires it under §2.4 once feature-local media navigation replaces its sole production caller**;
- one static non-shimmer skeleton primitive;
- one domain-neutral section-header primitive;
- domain-neutral empty/error/inline-feedback primitives with optional feature-owned actions;
- one `HikariPullToRefresh` primitive for **Discover only**, because Discover already owns the matching manual foreground refresh pipeline.

The following V1 implementation surfaces are **not** admitted by this decision:

- `designsystem.artwork` or any design-system-owned image/network/cache path;
- Coil, `coil-network-okhttp`, OkHttp, raw HTTP, source-host policy, Room, WorkManager, plugin runtime, or any domain/repository dependency in `:core:designsystem`;
- backdrop/blur/glass rendering, shimmer/infinite motion, expensive custom shadows/layers, or a global animation system before benchmark evidence requires one;
- Roborazzi/Robolectric as a Step 2 requirement;
- navigation, modal/snackbar hosts, global action hierarchy, generic `HikariText`/layout/card wrappers, public dimensions/semantic-shape hierarchies, fixed-token `CompositionLocal` trees, or other abstractions without a proven Step 2 caller.

`:core:designsystem` has **zero production project dependencies** and owns no collector, coroutine, effect, app-owned semantic mutable state, mutable registry/cache, service locator, initializer, provider, worker, or process-lifetime work. Material3's transient pull-gesture state is allowed only inside the enabled pull-refresh branch and is never promoted into feature/application state. `:feature:catalog` consumes the shared product primitives. `:app` gains one reviewed **presentation-infrastructure** edge to `:core:designsystem`, but may import only `app.openstory.designsystem.theme.HikariTheme`; its only product/capability edge remains `:feature:catalog`/`CatalogEntryPoint`.

`HikariTheme` is installed exactly once at the `HikariStartupApp` composition root and replaces the temporary `HikariBootTheme` fork. Unknown, FirstRun, Discover, and Story therefore consume one visual environment. This root theme does **not** own destination state or trigger Catalog composition: the existing `Ready + firstFrameReached` gate remains unchanged. The theme must preserve the current white/black root background so the Android window-to-Compose transition does not flash, and its startup cost is measured explicitly in Task 16 rather than assumed to be zero.

The current `docs/ui/design-system.md` in the supplied V2 tree is copied V1 canonical policy and contains rules that directly conflict with this scoped admission (including public dimensions/semantic-shape families, blanket tokenization of feature geometry, custom shadow/backdrop ownership, and broader Story/Chapter refresh claims). Task 13 must **rewrite that active document as the V2 Step 2 canonical policy**, not merely append a disclaimer. Historical V1 rules remain evidence only through the salvage ledger/reference tree; contradictory V1 policy must not remain active beside the R2.8 contract.

Discover manual refresh semantics are now explicit: `HikariPullToRefresh` is only enabled for durable `Published(empty)` or `Published(content)` presentation states; the primitive never appends `fillMaxSize`, never owns a scroll container/state, and uses the caller modifier as its sizing contract. When disabled it renders a plain `Box` branch and does not compose Material3 pull-gesture state; initial `Absent` bootstrap/loading and no-content failure continue to use their explicit loading/Retry path. While a durable snapshot is refreshing, real content/empty state remains visible; the pull indicator is the only refresh-progress chrome; a retryable refresh failure keeps a visible Retry action. The feature owns two user intents: `DiscoverViewModel.refresh()` for normal pull refresh and `DiscoverViewModel.retry()` for failure recovery. They are **not** forced into one callback: Retry may need to re-activate or re-observe after a storage/activation failure. However, whenever either path needs source acquisition, both converge on the same guarded runtime foreground single-flight owner. Task 12 owns and verifies this orchestration before presentation consolidation; Task 13 only wires the already-accepted intents into pull/Retry UI and must not reopen acquisition ownership. The design system owns only gesture/indicator/accessibility rendering. Story Detail remains **non-pull-refreshable** in Step 2 because adding a normal Story refresh pipeline would expand acquisition behavior rather than merely polish presentation.

This scoped admission does not change Chapters/Reader/Search/Library/Downloads/plugin-runtime scope and is not permission to migrate the whole application design system.

**Task ownership split:** Task 13 ends when the root Design System, shared stateless primitives, Discover pull-refresh wiring, active V2 design-system policy, and structural Big Update regression gates are green. Task 13 is **not** responsible for making Discover/Story visually match V1 and must not be described as the final polished surface. Task 14 begins only after Task 13 acceptance and owns feature-local visual restoration/UX polish. Task 14 uses the approved Dantotsu-inspired blueprint plus V1 screenshots as visual evidence while preserving the Task 13 module/work-ownership ratchets. One **shrink-only API exception** is explicitly approved in R2.8: because Manga/Light Novel becomes top-level media navigation, Task 14 retires the now-unused shared segmented-control primitive and updates the exact Design System slice gate rather than keeping dead public API. Task 14 adds no replacement global navigation primitive. Screenshot correctness follows after Task 14, and benchmark/profile measurement must run only against the accepted Task 14 visual surface.

### 2.4 Task 14 UI/IA supersession — Manga and Light Novel are top-level media destinations

R2.8 preserves the R2.5 IA decision and supersedes the Task 13 presentation choice that rendered Manga/Light Novel as a shared segmented form control. The underlying selected-media state and single-owner acquisition contract do **not** change: `DiscoverUiState` still carries one selected `CatalogMediaType`, and switching media replaces exactly one bounded Discover observation/bootstrap scope through the existing `onMediaSelected(mediaType)` intent.

The final Task 14 Discover surface renders Manga and Light Novel as a **feature-local floating bottom pill navigation** inspired by Dantotsu/ReDantotsu navigation silhouette:

- exactly two enabled destinations: `Manga` and `Light Novel`;
- one selected destination with accessible selected/tab semantics;
- persistent overlay on Discover only, outside the caller-owned pull-refresh scroller;
- no corresponding media navigation on focused Story Detail;
- tapping the already selected destination is a no-op;
- selecting the other destination resets the newly selected feed to its content start, without creating a second observer or acquisition owner;
- Discover → Story → Back preserves selected media and the existing Discover scroll position when the session survives.

The navigation is **not** a new global app-navigation framework and is not admitted into `:core:designsystem`. It is a Catalog presentation component under `:feature:catalog`. Task 14 removes `HikariSegmentedControl` and `HikariSegmentedOption` from the public Design System slice, updates `docs/ui/design-system.md`, and updates `scripts/tests/v2-step2-designsystem-slice-test.sh` so every remaining admitted public symbol still has a real production caller. This is an API-surface reduction only; no new project edge or Design System runtime authority is allowed.

The shrink is accepted only as an **atomic Task 13 contract migration**: the two segmented imports/tests in `HikariDesignSystemContractTest.kt` and the segmented-specific validation block in the slice script must be removed in the same Task 14 change. Every unrelated Task 13 Design System contract test remains intact and must still compile/run. A feature-green result with `:core:designsystem` androidTest compilation broken is a Task 14 regression, not an acceptable intermediate/final state.

### 2.5 Task 14 approved visual authority

Task 14 must read and visually inspect both:

- `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.png`;
- `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.md`.

The PNG is authoritative for **hierarchy, proportions, density, spacing rhythm, component silhouette, and visual mood**, but it is not a literal token sheet and may not invent product semantics. The blueprint's illustrated Design System sidebar (sample hex values, typography labels/sizes, and component annotations) is explanatory artwork. **Only the written R2.8 token migration in §2.6 may change root Design System values.** Written Step 2 product/data/architecture contracts continue to outrank the image. Visible concept elements such as `Read`, `Add to Library`, `Chapters`, `See all`, `Year`, and `R15+` are ignored when the current Step 2 state/route does not expose a trusted user-safe field or destination. The Story media-type eyebrow remains admitted because `StorySummaryProjection.contentType` already carries the trusted persisted media type; using it is presentation completion, not scope expansion. Search/Library/Reader/Chapter behavior may not be faked merely to make the implementation resemble the concept image.

When the user supplies an additional reference image directly to Codex/another agent, the agent must inspect it visually and record the attachment/file identity plus SHA-256 when a file path is available in the Task 14 visual-acceptance ledger before editing production UI. The agent must also record an `accepted visual cue / rejected semantic cue` extraction so direct image reference cannot silently broaden scope.

R2.8 preserves the execution-level clarifications found during the R2.5-R2.7 reviews:

- the existing Discover `LazyColumn(verticalArrangement = spacedBy(20.dp))` is removed; header/section spacing is explicit so the blueprint's 12.dp title-to-content and 32.dp major-section rhythm can actually be satisfied;
- edge-to-edge safe insets are explicit: Discover/Story top content respects status bars, and the outer floating-nav wrapper consumes navigation-bar inset **outside** the fixed 64.dp pill;
- the final Top Rated row must be scrollable fully above the floating nav, proven by bounds rather than by merely asserting that both nodes exist;
- Task 14 owns real-device/emulator captures and user visual acceptance of its composition; Task 15 owns the reproducible cross-device screenshot-evidence harness/freeze. Codex/agents may prepare evidence but may not self-mark the human visual gate as PASS.

### 2.6 Task 14 controlled Design System visual refresh

Task 13's **architecture is accepted and remains authoritative**: `:core:designsystem` stays presentation-only with zero production project dependencies, `HikariTheme` remains the single root theme, no runtime/downloadable font path is admitted, no image/network/data owner moves into the Design System, and the public primitive budget remains caller-driven. Task 14 is nevertheless allowed to evolve the **visual vocabulary** now, while V2 still has few product surfaces and migration cost is low.

R2.8 therefore supersedes the Task 13 palette/typography values under a narrow, explicit migration:

- **unchanged:** `HikariSpacing = 4 / 8 / 12 / 16 / 20 / 24 / 32.dp`;
- **unchanged:** Material3 shape radii `8 / 12 / 20 / 28 / 36.dp`;
- **unchanged:** root `background = Color.White` in light mode and `background = Color.Black` in dark mode so Step 1 window-to-Compose continuity does not regress;
- **unchanged:** platform `FontFamily.Serif` / `FontFamily.SansSerif`; no runtime font/resource/downloadable-font I/O;
- **changed intentionally:** neutral artwork-first surface palette, stronger accessible Coral/Teal accent roles, and tighter editorial headline hierarchy;
- **changed intentionally:** shared Error/Inline-feedback text treatment may consume the new hierarchy/colors while preserving action/state/accessibility semantics;
- **not admitted:** blur/glass/backdrop shaders, custom global elevation engine, animation system, feature dimensions, or new state/data ownership.

#### 2.6.1 R2.8 palette contract

The root palette is migrated to the following values. These are source-of-truth values for Task 14; the concept image's sampled colors are not.

```text
LIGHT
primary                  #C94C40
onPrimary                #FFFFFF
primaryContainer         #FFDAD5
onPrimaryContainer       #3B0905
secondary                #2A786F
onSecondary              #FFFFFF
secondaryContainer       #C9E9E3
onSecondaryContainer     #08201D
tertiary                 #6B6658
onTertiary               #FFFFFF
background               #FFFFFF          // Step 1 continuity lock
onBackground             #211A18
surface                  #FFF9F6
onSurface                #211A18
surfaceVariant           #F0E7E3
onSurfaceVariant         #6A605D
surfaceBright            #FFF9F6
surfaceDim               #E5DCD7
surfaceContainerLowest   #FFFFFF
surfaceContainerLow      #FAF4F1
surfaceContainer         #F5EFEB
surfaceContainerHigh     #EFE8E4
surfaceContainerHighest  #E9E1DD
outline                   #8D7F7B
outlineVariant            #D8CBC6
error                     #BA1A1A

DARK
primary                  #FF8E80
onPrimary                #4B0E08
primaryContainer         #653028
onPrimaryContainer       #FFDAD5
secondary                #8ED8CC
onSecondary              #003832
secondaryContainer       #185149
onSecondaryContainer     #ACEFE4
tertiary                 #CFC7B1
onTertiary               #353025
background               #000000          // Step 1 continuity lock
onBackground             #F5F1F0
surface                  #121217
onSurface                #F5F1F0
surfaceVariant           #24242E
onSurfaceVariant         #C9C2C0
surfaceBright            #2B2B36
surfaceDim               #0B0B0F
surfaceContainerLowest   #0B0B0F
surfaceContainerLow      #111116
surfaceContainer         #17171D
surfaceContainerHigh     #1D1D25
surfaceContainerHighest  #24242E
outline                   #9B9290
outlineVariant            #423B3D
error                     #FFB4AB
```

The light `primary/onPrimary` pair and the major body/surface pairs are chosen to remain readable rather than copying the concept's brightest coral blindly. Task 14 tests exact token values through `HikariTheme`; it does not add a separate semantic-color system.

#### 2.6.2 R2.8 typography contract

Typography keeps the zero-I/O Serif/Sans split but tightens the roles actually used by Catalog:

```text
displayLarge     Serif Bold      48 / 54sp   letterSpacing -0.40sp
displayMedium    Serif Bold      40 / 46sp
displaySmall     Serif Bold      34 / 40sp
headlineLarge    Serif Bold      32 / 38sp
headlineMedium   Serif SemiBold  30 / 36sp
headlineSmall    Serif SemiBold  20 / 26sp
titleLarge       Sans Bold       20 / 26sp
titleMedium      Sans SemiBold   16 / 22sp
titleSmall       Sans SemiBold   14 / 20sp
bodyLarge        Sans Normal     16 / 24sp
bodyMedium       Sans Normal     14 / 20sp
bodySmall        Sans Normal     12 / 18sp
labelLarge       Sans Bold       14 / 18sp
labelMedium      Sans SemiBold   12 / 16sp
labelSmall       Sans SemiBold   11 / 14sp
```

This deliberately makes page/Story identity more editorial while reducing the old 24sp section-header/card-title pressure. Feature code still chooses the Material3 role; it does not hard-code font sizes.

#### 2.6.3 Shared-state visual migration and ownership

The visual migration applies globally, therefore Task 14 must verify the existing shared states instead of checking only the happy-path Catalog cards:

- `HikariSkeleton` continues to use `surfaceVariant` and remains static/non-progress semantics;
- `HikariEmptyState` keeps title/body hierarchy and gains the new palette/type automatically;
- `HikariErrorState` keeps the same action contract; body copy uses `bodyMedium/onSurfaceVariant` so error hierarchy remains restrained rather than competing with the screen identity;
- `HikariInlineFeedback` keeps the same action contract; message uses `bodyMedium/onSurfaceVariant`;
- `HikariPullToRefresh` keeps the existing accessibility/custom-action behavior and receives the new accent through Material3 theme only;
- `HikariSectionHeader` remains a heading and consumes the new `headlineSmall` role.

No shared state primitive may acquire a ViewModel, coroutine owner, lifecycle collector, domain type, image loader, or new navigation behavior during this migration.

#### 2.6.4 Verification split — correctness is automated, appearance is user-owned

Task 14 does **not** transfer correctness judgment to the user. Codex/agent owns the correctness contracts, implementation, focused compile/unit checks, and every fix required by automated failures. Per the repository workflow, connected/device commands may still be **executed by the user** and returned as evidence; that is mechanical automated verification, not a request for the user to reason about retry/refresh/semantics by eye. Only after the automated Design-System + Catalog connected evidence and architecture gates are green may the task become `READY FOR USER VISUAL ACCEPTANCE`.

The separate user visual gate then judges the properties automation cannot honestly certify: hierarchy, density, artwork prominence, color balance, typography feel, state coherence, compact/wide composition, and whether the new global theme looks acceptable in Loading/Error/Empty/Refresh as well as Ready content. The minimum manual visual checklist is:

```text
1. Discover Manga — Ready / compact dark
2. Discover Light Novel — Ready / compact dark + media-nav selection
3. Discover — representative light theme
4. Discover — Loading skeleton geometry
5. Discover — Empty
6. Discover — retryable Error + retained refresh failure / inline feedback
7. Discover — pull-to-refresh idle -> refreshing -> settled
8. Story — Ready with contentType + latest-update label
9. Story — summary-null / rich-detail loading continuity
10. Story — retryable/retained failure plus one representative light or >=600dp composition
11. FirstRun after clear-data when practical, because typography/palette are root-global
```

The user may reject any item on visual grounds even when automated tests pass. Codex/agent records `READY FOR USER VISUAL ACCEPTANCE`, never human `PASS`.

---

## 3. Product outcome

### 3.1 Returning launch

A returning user who has completed Step 1 setup reaches Discover as the first real product destination.

```text
Application / MainActivity minimal shell
        ↓
launch state resolves Ready
        ↓
Catalog capability demand begins
        ↓
Discover shell renders immediately
        ↓
real persisted Discover snapshot appears
```

Catalog activation must not gate the first application-owned frame.

### 3.2 Fresh install

Step 1 first-run semantics remain valid. Step 2 does not silently delete or bypass the accepted install-state contract.

```text
fresh install
   ↓
FirstRun
   ↓ completion persisted successfully
Discover
```

The first Catalog activation occurs only after Discover is actually selected as the destination. In Step 2 local/benchmark/test builds, an empty scope may then bootstrap through the typed local acquisition source. Step 2 does not claim a ship-ready release remote source.

### 3.3 Discover

The initial Step 2 product target is a multi-section Discover experience inheriting the useful V1 semantic families:

- **Popular**
- **Latest Updates**
- **Top Rated**

Step 2 enables both approved **Manga | Light Novel** media destinations as first-class local presentation/read keys. This explicitly supersedes the earlier delivery restriction that left Light Novel disabled. Task 14 presents the pair as a persistent feature-local floating bottom pill navigation rather than a form/filter segmented control. Changing media type still replaces only the selected bounded Discover observation/bootstrap scope; it does not trigger arbitrary Story Detail calls or execute acquisition directly from UI.

The canonical product design also includes Search on the primary Discover surface. Search is intentionally deferred from this Step 2 delivery because it owns a separate query/acquisition/cancellation contract. This is incremental delivery, not a product-scope deletion or supersession of the approved Search journey.

The exact visual language is redesignable and Step 2 is not pixel-compatible with V1, but visual freedom is **not** permission to ship a visibly weaker prototype. After Task 13 establishes the presentation foundation, Task 14 must produce a Discover/Story result that is at least comparable to the reviewed V1 reference quality for artwork prominence, hierarchy, content density, typography/spacing coherence, metadata scanability, light/dark treatment, and compact/wider-screen composition. Pixel equality, V1 runtime/component transplantation, and visual effects without performance evidence are not required.

Initial composition targets per selected media type are intentionally bounded and are performance-tunable:

- Popular: up to 5 stories;
- Latest Updates: up to 9 stories;
- Top Rated: up to 5 stories.

The first implementation therefore displays at most 19 section memberships for the selected media type. If both Manga and Light Novel snapshots are locally available, persistence may hold at most 38 current Discover-card memberships under the initial policy while foreground presentation/query cardinality remains at most 19. A Story may appear in more than one semantic section when that is product-correct; identities and image cache keys remain stable.

A later Step 2 performance-validation task may reduce card counts, defer below-the-fold sections, merge or simplify layouts, or alter preload policy when evidence shows that the original composition is too expensive. The resulting product must remain multi-section. Any removal of a semantic section must be recorded as an explicit evidence-backed design correction rather than an incidental optimization.

### 3.4 Story Detail

Selecting a Story opens a metadata/detail surface containing the useful available fields, including where present:

- cover;
- title;
- **media/content type** (`Manga` / `Light Novel`) from the already-persisted `StorySummaryProjection.contentType`;
- description;
- author(s);
- artist(s);
- publication/status information;
- genres/tags;
- score/rating with source scale preserved;
- **latest source update time** from the already-persisted `latestUpdateEpochMs`, rendered as a stable presentation label rather than discarded;
- source identity/provenance required for routing, persistence, authority, and integration evidence. Raw `CatalogSourceKey`, `sourceVersion`, detail-acquisition timestamp/provenance, host-policy values, and similar internal authority fields are **not** user-facing labels in Step 2. A visible friendly source name requires an explicit user-safe presentation contract and must not be invented from internal provenance values.

R2.8 freezes the Story projection-to-UI consumption audit so Task 14 cannot accidentally expand scope while trying to use more of the data already present:

| Projection field | Task 14 presentation use | Rule |
|---|---|---|
| `ref` | route/key only | never rendered as raw identity |
| `title` | hero title | rendered |
| `contentType` | hero eyebrow | map exactly to `MANGA` / `LIGHT NOVEL`; no inference |
| `coverLocator` / `coverAssetKey` | portrait artwork | existing image ownership unchanged |
| `rating` | hero rating | preserve source scale |
| `publicationStatusSummary` | hero status | optional; no invented fallback |
| `latestUpdateEpochMs` | latest-update label | deterministic English `MMM d, uuuu` using UTC for Step 2 screenshot stability; omit when null |
| `sourceVersion` | authority only | never user-facing |
| rich `description/authors/artists/genres/publicationStatus/language` | metadata body | all existing bounded fields remain renderable |
| `detailProvenance` / acquired-at timestamp | authority only | never user-facing |

Story Detail does **not** include Chapter list or Reader entry in Step 2.

### 3.5 Navigation continuity

Discover → Story Detail → Back must preserve a stable user experience:

- Story identity is stable;
- Story navigation Back retains the active media destination and Discover scroll position where feasible;
- switching from Manga to Light Novel or vice versa starts the newly selected feed at item 0 so a deep offset from a different media home is not reused;
- an already-visible cover should not flash back to a placeholder merely because the destination changed;
- no bitmap is copied through navigation arguments;
- continuity is provided by stable asset identity plus bounded memory/disk cache, not by unbounded RAM retention.

---

## 4. Step 2 design principles

### D2-01 — Build the real vertical slice, not a mock screen

Discover and Story Detail read from real persistence. UI code never reads a bundled JSON file or a test-only repository.

A development/benchmark local seed may substitute for remote/plugin acquisition, but it must enter the same production import/write boundary later used by a production acquisition adapter.

### D2-02 — Product semantics may be inherited; runtime ownership may not

Keep V1 product behaviors that remain useful. Reject V1 orchestration merely because it already exists.

### D2-03 — Discover is a bounded materialized read model

The primary Discover read path is optimized for what the screen needs. It does not materialize a global canonical graph, full Story details, complete source history, Library state, Download state, Chapters, or Reader state.

### D2-04 — Story Detail is a keyed **and bounded** read

Opening one Story scales with that Story and a fixed number of bounded child collections. It may not load or observe the entire Catalog to answer a point question, and one malformed Story payload may not become an unbounded working set merely because the read is keyed.

### D2-05 — Visual usefulness is progressive

Cover loading begins as soon as a publishable Discover/Story visual locator is available. Full detail readiness is not a prerequisite for starting or rendering the image pipeline.

### D2-06 — Persistence and image cache have different jobs

Room owns durable structured Catalog state. Cover bytes live in the image cache/store. Large image blobs are not stored inside the Catalog database merely to make the cache persistent.

### D2-07 — Capability activation follows demand

Neither Room nor the image loader nor acquisition logic is created by `Application.onCreate`, `MainActivity.onCreate`, an AndroidX Startup initializer, a manifest provider, or a process-wide service locator.

### D2-08 — No invisible performance debt

Step 2 is not accepted until startup delta, Discover working-set cost, Story Detail keyed-read cost, multi-section scroll behavior, image continuity, cache aging, and repeated activation have measured evidence **and enforceable review gates**.

### D2-09 — Source identity is stable without canonical fusion

Step 2 does not need the quarantined canonical engine to create stable identity. A source item is identified by `SourceStoryKey(catalogSourceKey, sourceStoryId)`. `StoryId` is a deterministic stable derivation of that source key in Step 2 and must not depend on mutable metadata such as title, author, cover, rating, description, section membership, or position.

A future multi-source Catalog Engine may reconcile several `SourceStoryKey` values into a higher-level canonical identity, but Step 2 source identity must remain reproducible and stable on its own.

### D2-10 — Untrusted acquisition and artwork inputs are bounded before they become app state

Section payloads, Story Detail text/child collections, source identifiers, locators, encoded image bytes, decoded image dimensions, and active work concurrency all have explicit ceilings. “Keyed” or “cached” is not accepted as a substitute for a resource bound.

### D2-11 — Publication existence is explicit; empty is data, not “not initialized”

Discover persistence distinguishes **never successfully published** from **successfully published with zero eligible cards**. `Published(empty)` is a valid terminal persisted result and must survive reopen/process recreation without triggering automatic bootstrap again. Runtime bootstrap authority is keyed to `Absent`, never to `cards.isEmpty()`.

### D2-12 — Bounded results require bounded foreground algorithms

A limit on the final row count is not enough. Refresh/import/retention work on the foreground path must scale with the current bounded snapshot, the bounded delta removed by that publication, and bounded retention overflow—not with total historical Catalog rows. A global Story/detail sweep or unbounded `ORDER BY` over history is rejected even if it ultimately keeps only 64 rows.

### D2-13 — Source identity and provenance authority are host-owned

Untrusted acquisition payloads may supply source-local Story identifiers and metadata, but they do not choose their own Catalog authority. `CatalogSourceKey`, source contract version, acquisition timestamp, and source-scoped remote-asset authority are stamped/verified by the host-side admitted source/session adapter. Payload data cannot impersonate another source to inherit its identity or image-host policy.

---

## 5. V1 inheritance matrix

### 5.1 KEEP as product/semantic evidence

Step 2 intentionally preserves these V1 ideas:

- Discover is source-agnostic at the presentation layer.
- Manga | Light Novel selection is a local semantic/read key and does not itself execute a plugin/detail request.
- Popular, Latest Updates, and Top Rated are distinct semantic sections; one generic score function must not accidentally define all three.
- Stable Story identity is used for list keys and navigation.
- Source-specific identity and source-provided metadata are preserved.
- One source/acquisition failure must not erase previously usable content.
- Cached usable content remains visible during a failed manual refresh.
- Initial empty-state loading uses final-layout-shaped placeholders rather than a blank screen.
- A partial section failure/absence does not require blanking unrelated sections.
- One vertical scroll owner is preferred; nested vertical scrolling is rejected.
- Story Detail enriches the selected Story and does not mutate the published Discover-card snapshot as a side effect.
- UI does not perform N+1 detail calls merely to render Discover cards.

### 5.2 REDESIGN rather than transplant

Step 2 explicitly rejects these V1 runtime patterns as migration targets:

- broad `Story`/Catalog models as default read types;
- app-wide/global Catalog repository ownership;
- V1 Room schema/migration chain as a compatibility requirement;
- canonical reconciliation/fusion settlement on the Discover critical path;
- multiple asynchronously settling Home streams whose intermediate states define readiness;
- table/global reads for Story-local questions;
- unbounded canonical/evidence history;
- foreground and durable/background refresh paths racing as separate owners;
- plugin metadata/control-plane operations implicitly loading executable payloads;
- production ViewModels and workaround pipelines as architecture authority;
- a giant aggregate Home object that reactivates unrelated capabilities.

### 5.3 DROP from Step 2 scope

- V1 `feature/catalog` implementation;
- V1 Android DI/composition root;
- V1 plugin runtime/package provisioning;
- V1 background workers/schedulers;
- V1 Search/Mapping implementation;
- V1 Chapters/Reader/Downloads/Library integrations;
- V1 broad Home dashboard aggregation.

---

## 6. Proposed production module boundary

Step 2 adds the smallest graph that makes ownership visible without creating one new god module.

```text
:app
  +--> :feature:catalog        # only product/capability edge
  +--> :core:designsystem     # presentation infrastructure; HikariTheme import only

:feature:catalog
  +--> :core:designsystem
  +--> :catalog:domain
  +--> :catalog:runtime
           +--> :catalog:domain
           +--> :catalog:storage
                    +--> :catalog:domain
                    +--> :core:common

:catalog:domain --> :core:common
:core:designsystem --> (zero production project dependencies)

:catalog:model   (quarantine; no Step 2 runtime edge)
:catalog:engine  (quarantine; no Step 2 runtime edge)
:plugins:api     (retained; no production Step 2 app edge)
:reader:engine   (retained; unrelated)
:benchmark --> :app
```

The Step 1 root currently includes seven reviewed modules. Step 2 intentionally admits exactly five new production modules: `:catalog:domain`, `:catalog:storage`, `:catalog:runtime`, `:feature:catalog`, and the minimal presentation-only `:core:designsystem`. `:app` has exactly one direct Step 2 **product/capability** edge (`:feature:catalog`) plus one reviewed **presentation-infrastructure** edge (`:core:designsystem`). `:feature:catalog` also consumes `:core:designsystem` primitives. Architecture verification must distinguish those edge classes and assert the exact admitted set/import surface rather than relying only on a loose module-count ceiling.

### 6.1 `:catalog:domain`

Pure JVM contracts for the Step 2 capability:

- `SourceStoryKey(catalogSourceKey, sourceStoryId)` and deterministic Step 2 `StoryId` derivation through reviewed `:core:common` primitives;
- narrow Discover projections;
- bounded Story Detail projection;
- acquisition/import command types;
- semantic section kind/policy;
- acquisition provenance types;
- typed cover locator/asset policy contracts that do not expose Coil/OkHttp types;
- capability-facing read/write interfaces;
- failure types that do not depend on Room, Coil, Android, or plugin runtime classes.

It must not copy the quarantined broad `:catalog:model` merely under a new package.

### 6.2 `:catalog:storage`

Android Room adapter owned by the Catalog capability.

Responsibilities:

- Step 2 schema;
- DAO/query implementations;
- one coherent Discover published-generation read;
- transactions for snapshot publication and detail enrichment;
- explicit uniqueness constraints and indices;
- bounded retention/pruning;
- mapping storage rows to narrow domain projections.

It does not own product ranking, Compose, image loading, plugin execution, background work, or app startup.

Room framework/compiler dependencies are declared explicitly by this module in Step 2. A generic `RoomConventionPlugin` is **not** reintroduced merely because V1 used one. Shared Room build logic may be reconsidered only after real repetition appears in multiple admitted persistence modules.

### 6.3 `:catalog:runtime`

Demand-activated capability runtime.

Responsibilities:

- lazy storage creation/open;
- one foreground acquisition owner;
- one keyed Story Detail acquisition owner per source item;
- bootstrap state that does not infer execution ownership from transient Room emptiness;
- import orchestration and input validation;
- active Story retention pins used by bounded pruning;
- explicit manual refresh single-flight;
- cancellation/quiescence;
- local-seed adapter wiring in development/benchmark/test configurations;
- source-scoped asset-policy authority exposed through a narrow port when remote cover integration is exercised.

It does not own app navigation or global startup.

### 6.4 `:feature:catalog`

The first real product surface and capability-local composition root.

Responsibilities:

- Discover UI;
- Story Detail UI;
- capability-local route state between those two destinations;
- screen-scoped ViewModels/presenters;
- presentation lifetime of cover requests;
- capability-private image-loader implementation/configuration behind a narrow asset port;
- progressive visual state;
- accessibility and screenshot semantics;
- exposing one narrow Catalog destination/entry point to `:app`.

The feature may own the concrete image-loader instance for Step 2, but **Compose/presentation code does not own remote trust policy, raw HTTP clients, redirect validation, or source-host authority**. Those concerns remain behind capability-owned typed asset/policy contracts. `:app` must not depend directly on `:catalog:storage` or `:catalog:runtime`.

### 6.5 `:core:designsystem` — minimal Step 2 slice with R2.8 visual evolution

`:core:designsystem` remains a presentation-only Android/Compose library with zero production project dependencies. In the **final R2.8 Step 2 surface after Task 14**, it owns only the visual vocabulary with a proven production caller: root `HikariTheme`; reviewed light/dark Material3 palette and typography; the unchanged 8/12/20/28/36.dp Material3 shape family; the unchanged public immutable `HikariSpacing` singleton; `HikariSectionHeader`; `HikariSkeleton`; `HikariEmptyState`; `HikariErrorState`; `HikariInlineFeedback`; and `HikariPullToRefresh`. The accepted Task 13 intermediate state also contained `HikariSegmentedControl`/`HikariSegmentedOption`; Task 14 removes those two migration-only APIs atomically with their segmented-specific contract tests/script checks when the sole Discover caller moves to feature-local media navigation.

Task 14 R2.8 **does not discard Task 13**. It preserves the module/API/ownership architecture and performs one explicit token migration under §2.6:

- `HikariPalette.kt` adopts the exact R2.8 palette while preserving white/black root backgrounds;
- `HikariTypography.kt` adopts the exact R2.8 Serif/Sans role sizes/line-heights while keeping platform font families and zero runtime font I/O;
- `HikariSpacing.kt` remains byte-equivalent in values at `4 / 8 / 12 / 16 / 20 / 24 / 32.dp`;
- Material3 shapes remain `8 / 12 / 20 / 28 / 36.dp`;
- `HikariErrorState` and `HikariInlineFeedback` may receive the exact visual-only text treatment in §2.6.3, with semantics/actions unchanged;
- shared Loading/Empty/Refresh visuals otherwise inherit the refreshed tokens rather than growing parallel feature-local color/type constants.

The R2.8 palette/typography migration is tested through `HikariTheme` so future changes cannot silently drift. Theme values are selected, not rebuilt, during recomposition; `HikariTheme.kt` does not construct color schemes/typography/shapes or install a fixed-token `CompositionLocalProvider`. Step 2 still performs no runtime/downloadable-font or Context/resource lookup.

The Design System must not own feature copy, `DiscoverUiState`/`StoryDetailUiState`, retry/acquisition scheduling, `CoverLocator`, `CoverAssetKey`, image loading/cache, source trust, persistence, navigation state, Catalog domain semantics, data observation, lifecycle collection, coroutines, effects, service lookup, application-lifetime mutable state, or any vertical/horizontal scroll owner. Shared state/feedback primitives are caller-sized and do not force `fillMaxSize`. Production build files still do not admit Coil, `coil-network-okhttp`, OkHttp/raw HTTP, backdrop/blur/glass, Room, WorkManager, JavaScriptEngine, or plugin/runtime dependencies. Step 2 does not admit Robolectric/Roborazzi as part of this module's verification strategy.

The blueprint remains composition/mood evidence, not a literal token file. The only allowed Task 14 root-token changes are the exact R2.8 written values in §2.6; feature-specific card/poster/nav dimensions remain in `:feature:catalog`. Every public primitive must retain a real production caller. Small local UI duplication is preferred over a generic parameter-heavy base component when no stable shared rule exists.

### 6.6 Why no separate image module yet

Step 2 treats cover loading as a serious bounded subsystem, but does not create a reusable global image module before another capability proves a shared abstraction is needed.

The ownership split is:

```text
presentation/composition lifetime
        ↓
CoverAssetPort / typed CoverLocator
        ↓
capability-private asset implementation
        ↓
cache + decode + source-scoped network/security policy
```

A future Reader or other capability may justify extraction into a shared asset module. Step 2 must not pre-emptively create a global image service or let presentation code become the security authority.

### 6.7 Framework choices

Step 2 does not require Hilt or Navigation 3. A manual capability-local composition root and small internal Discover/Story route state are sufficient for two destinations and keep the Step 1 framework surface small.

Introducing a global DI or navigation framework requires separate evidence that the capability genuinely benefits from it and that merged-manifest/startup behavior remains controlled.

### 6.8 Step 1 architecture ratchet evolution

Step 1's foundation policy intentionally encodes a zero-capability shell. Step 2 evolves that policy without weakening it into a permissive check.

Required changes in principle:

- `config/architecture/module-boundaries.json` changes `:app` from zero production project dependencies to exactly one reviewed direct edge: `:feature:catalog`;
- all new Step 2 module edges are recorded explicitly, not merely tolerated by a larger global count;
- the blanket Step 1 build-token rejection of every `implementation(project(...))` is replaced by exact dependency validation and remains fail-closed;
- `:app` production source remains forbidden from importing Room, image-loader/OkHttp implementation types, Catalog storage/runtime packages, plugin runtime, Reader, Downloads, Chapters, Library, WorkManager, Hilt, or broad service-locator authority;
- `:app` may know only the narrow `:feature:catalog` entry point required to render the admitted destination;
- merged-manifest verification remains active for every app variant and rejects unexpected providers/initializers/services/receivers introduced transitively by Room/image dependencies;
- the Step 1 app-shell structural/source-line ratchet is updated only for the smallest intentional destination handoff, not reset to a large new allowance;
- the release/main manifest **does not** gain `android.permission.INTERNET` in Step 2 solely for the isolated real-plugin integration gate. Network permission for that gate belongs to test/integration wiring. A later production acquisition admission may add production `INTERNET` with its own startup/network proof.

Static app-shell code must not own an HTTP client, and runtime/trace tests must prove that no storage/image/network work is issued before Catalog/cover demand.

### 6.9 Step 2 build-surface admission

Step 1 intentionally removed Android-library/Room/image build surface and fail-closed when those capabilities existed without admission. Step 2 re-admits only the exact surface required by the first Catalog capability.

Normative build-surface changes:

1. Reintroduce a **minimal reviewed Android library convention** for admitted Android library modules. Do not transplant the old V1 convention implementation wholesale without review.
2. Re-add reviewed Room version-catalog/library/compiler aliases, but allow Room use **only** from `:catalog:storage`.
3. Do **not** restore a generic `RoomConventionPlugin` in Step 2.
4. Re-add the chosen image-loader dependency alias only for the capability-private image implementation; it is forbidden from `:app`, `:catalog:domain`, and `:catalog:storage`.
5. `:catalog:domain` remains pure JVM and Android-free.
6. `:catalog:runtime` may use only the Android/runtime primitives needed for lifecycle/resource ownership; it does not gain Compose.
7. `:feature:catalog` may use Android + Compose and the concrete image implementation behind its narrow capability contracts.
8. The Step 1 `v2-build-surface-test.sh`/equivalent verifier is **rewritten from zero-capability blacklist to exact Step 2 allowlist**. It is not deleted or globally relaxed.
9. Version-catalog presence by itself is not architectural permission. Verification checks module ownership/edges and forbidden imports/dependencies.
10. Architecture tests must fail if Room leaks outside `:catalog:storage`, if Catalog storage/runtime leaks into `:app`, if image/network implementation leaks into the app shell/domain/storage, or if quarantined Catalog modules enter the production graph.
11. The architecture ratchet also checks **package-level strongly connected components** inside every admitted Step 2 production module. The accepted target is zero new production package SCCs; a module DAG is not sufficient evidence if packages inside one module form a cycle.
12. `:core:designsystem` has zero production project dependencies. `:app -> :core:designsystem` is explicitly admitted only for `theme.HikariTheme`; `:feature:catalog -> :core:designsystem` is the product-UI primitive edge. The build-surface ratchet rejects any other app Design System import and rejects Design System dependencies/imports on Catalog/domain/runtime/storage, Room, WorkManager, Coil/network, OkHttp/raw HTTP, plugin/JavaScriptEngine, backdrop/blur/glass, hidden reactive/effect/coroutine ownership, or process-lifetime mutable resources.

The implementation plan begins with a build-surface/architecture-admission task and must make these gates green before DAO/UI implementation begins.

### 6.10 Build-variant and fixture-source admission

The current Step 1 app already owns custom `benchmarkRelease` and `nonMinifiedRelease` build types. Step 2 must preserve the distinction between deterministic development/benchmark acquisition and a release build that has no production remote source yet.

Normative variant/source matrix:

| Variant/source set | Step 2 acquisition wiring |
| --- | --- |
| `debug` | deterministic local development source allowed |
| `benchmarkRelease` | deterministic benchmark source required |
| `nonMinifiedRelease` | same deterministic benchmark source contract as `benchmarkRelease` when used for profile/benchmark generation |
| `release` / `main` | **no local seed and no production remote source** in Step 2 |
| unit-test source sets | test fixtures/fakes allowed |
| `androidTest` | isolated reference-plugin harness and controlled remote-asset fixtures allowed |

The deterministic source implementation belongs to non-release source sets of the Catalog feature/composition boundary; it is not compiled into `main`/`release`. The Android library build convention must expose compatible `benchmarkRelease`/`nonMinifiedRelease` variants (or an equivalently exact reviewed variant mapping) so the application does not fall back to a release artifact that accidentally lacks the benchmark fixture or, conversely, package benchmark fixtures into release.

Architecture/build tests must inspect the resolved variant graph and release artifact/source inputs so that:

- `release` contains no deterministic seed implementation or bundled seed data;
- `benchmarkRelease` and `nonMinifiedRelease` can exercise the real importer/persistence path deterministically;
- no test/plugin harness source enters a production variant;
- variant fallback is explicit and reviewed rather than an incidental Gradle resolution behavior.

### 6.10 Structural cycle ratchet

Step 2 must not recreate V1 structural debt inside otherwise clean module edges.

Permanent structural verification therefore covers both:

```text
production module graph: acyclic + exact allowlisted edges
production package graph inside each Step 2 module: zero SCC > 1 package
```

Generated/test-only code is classified separately. Production SCC exceptions are not introduced casually; any unavoidable exception requires an explicit architecture amendment with the exact cycle and owner documented.

---

## 7. Capability activation and lifecycle

### 7.1 Activation

Catalog activation begins only when the accepted Step 1 launch state selects the real Catalog destination.

```text
Step 1 shell first frame
       ↓
launch state Ready
       ↓
Catalog destination composed
       ↓
CatalogCapabilitySession activation starts asynchronously
       ↓
Room + read ports + acquisition executor become available
       ↓
Discover persistence observation begins
```

No Catalog work may be started from process creation merely because Discover is the normal returning destination.

### 7.2 First frame

The Discover surface may render a stable layout-shaped shell immediately. Database creation/open and first read run asynchronously and must not block the first application-owned frame.

### 7.3 Active observation

Observers are destination-scoped:

- Discover collects one coherent bounded Discover snapshot keyed by selected media type while Discover is active.
- Story Detail collects one keyed Story projection while that Story destination is active.
- Cover requests exist only for composed/near-viewport artwork plus the selected Story continuity item.

### 7.4 Quiescence

When the application leaves the started/visible lifecycle:

- UI collectors stop through lifecycle-aware collection;
- viewport image work is cancellable;
- no automatic refresh is launched;
- no background worker takes over the same logical acquisition item;
- no maintenance work is scheduled because the app became backgrounded.

The activity/capability session may retain an already-open Room handle and bounded image caches for warm reuse. On final session destruction it closes owned resources. Process death remains the final cleanup boundary.

This dormant retention is allowed because it is bounded and performs no observation or work while quiescent.

---

## 8. Persistence architecture

### 8.1 New V2 database boundary

Step 2 introduces a new V2 Catalog database owned by `:catalog:storage`.

It does not migrate the V1 Room schema. Schema versioning starts from a new Step 2 baseline because Step 1 explicitly did not promise same-application-ID V1 data migration.

The database is created lazily after Catalog activation.

### 8.2 Stable source identity contract

Step 2 identity is source-stable without invoking canonical fusion.

The source authority type is explicit:

```text
CatalogSourceKey(value)

SourceStoryKey(
    catalogSourceKey,
    sourceStoryId
)
        ↓ deterministic v1 derivation
StoryId
```

`CatalogSourceKey` is host-assigned/verified for the admitted source contract. It is deliberately named differently from older V1 canonical-engine `SourceKey` concepts so Step 2 code cannot accidentally inherit fusion semantics.

#### 8.2.1 Frozen `SourceStoryKey -> StoryId` derivation v1

Step 2 freezes the derivation before implementation planning:

1. Validate `CatalogSourceKey.value` and `sourceStoryId` against the Step 2 identifier bounds before hashing.
2. Treat both identifiers as opaque, case-sensitive source identity. Do **not** trim, case-fold, Unicode-normalize, title-normalize, or otherwise derive identity from mutable/semantic text.
3. Encode both identifiers as their exact validated UTF-8 byte sequences.
4. Build canonical hash input as:

```text
ASCII "hikari:v2:source-story:v1" + 0x00
+ uint32_be(catalogSourceKeyUtf8.length)
+ catalogSourceKeyUtf8
+ uint32_be(sourceStoryIdUtf8.length)
+ sourceStoryIdUtf8
```

5. Compute SHA-256 over those bytes using the platform/JDK cryptographic primitive; no new identity library is required.
6. Encode the full 32-byte digest as 64 lowercase hexadecimal characters.
7. Produce the stable ID string:

```text
source-story:v1:<64-lowercase-hex>
```

The full 256-bit digest is retained; Step 2 does not truncate it merely to make IDs shorter.

Collision handling is fail-closed. Persistence owns both a uniqueness constraint for `(source_key, source_story_id)` and a uniqueness constraint for `story_id`. If one derived `StoryId` is ever observed for a different exact `SourceStoryKey`, import fails with a typed identity-collision failure. **No numeric suffix, insertion-order repair, random fallback, or DB-dependent collision resolver is permitted**, because any such resolver would destroy deterministic identity across devices/reinstalls.

Future derivation changes require a new explicit derivation version and a migration/compatibility decision; existing `v1` identities are never silently recomputed under changed rules.

Requirements:

- `(catalogSourceKey, sourceStoryId)` is unique for one source item;
- mutable metadata such as title, authors, description, cover, rating, status, section membership, item position, or source version must not change the Step 2 `StoryId`;
- a future multi-source canonical layer may map several source items together, but does not retroactively make Step 2 source identity unstable.

### 8.3 Read-model-first schema

The schema separates Discover hot-path data from Story Detail rich metadata so a Story-local detail update cannot force a full Discover detail model to be decoded.

Conceptual structures:

```text
catalog_source_state
- source_key
- media_type
- source_version
- published_generation        # exists only after a successful publication
- published_at_epoch_ms
- last_success_epoch_ms

story_source_identity
- story_id
- source_key
- source_story_id

story_source_summary
- story_id
- source_key
- source_version
- title
- content_type
- cover_locator_type
- cover_locator_value
- cover_revision
- rating_value
- rating_scale
- publication_status_summary
- latest_update_epoch_ms
- last_seen_epoch_ms

# materialized Discover hot-path copy
discover_card
- source_key
- media_type
- source_version
- generation
- section_kind
- item_position
- story_id
- source_story_id
- title
- content_type
- cover_locator_type
- cover_locator_value
- cover_revision
- rating_value
- rating_scale
- publication_status_summary
- latest_update_epoch_ms

story_detail
- story_id
- source_key
- source_story_id
- source_version
- description
- publication_status
- language / other detail-owned scalars
- fetched_at_epoch_ms
- last_accessed_epoch_ms

story_author
story_artist
story_genre
- keyed by story_id/source_key with deterministic position/value

# explicit bounded orphan-retention index
story_orphan_retention
- story_id
- last_accessed_epoch_ms
```

`section_position` is intentionally absent. The Step 2 section order is defined by the admitted semantic-section policy (`Popular -> Latest Updates -> Top Rated`), while `item_position` orders cards inside one section. Step 2 does not create a second mutable section-order authority in storage.

`catalog_source_state` is also the durable publication-existence marker. A successful acquisition that produces zero eligible cards still advances/creates `published_generation`; therefore zero `discover_card` rows does **not** mean “never published”.

`story_orphan_retention` is an explicit bounded index/set, not an unbounded history table. Its invariant is `size <= 64` after every successful foreground mutation. It exists so retention work can be driven by bounded deltas without scanning all Story Detail rows.

Exact Room entity naming is an implementation detail. The ownership, provenance, uniqueness, publication-existence semantics, and query shape are normative.

### 8.4 Acquisition provenance and authority

Every persisted acquisition snapshot that can outlive the executing source records enough provenance to answer **which admitted source contract produced this data**.

At minimum:

```text
AcquisitionProvenance(
    catalogSourceKey,
    sourceVersion,
    acquiredAtEpochMs
)
```

The provenance fields are host/session authority, not arbitrary plugin payload fields:

- `catalogSourceKey` is assigned/verified by the admitted source registration/session;
- `sourceVersion` is taken from the verified local fixture contract or verified plugin/package/session metadata used to execute the request;
- `acquiredAtEpochMs` is stamped by the host at acquisition completion/acceptance time;
- plugin/source payloads may not override these values or claim another source identity;
- a remote `CoverLocator` imported from that payload remains bound to the same host-authoritative `catalogSourceKey`, so it cannot inherit another source's host allowlist.

Discover publication records source version for the published generation/cards. Story Detail records the source version used for that keyed acquisition independently of the latest Discover source state.

A later source/plugin version change must not silently rewrite the provenance of already persisted detail rows.

### 8.5 Discover query shape — one coherent published-generation state

The Discover DAO reads a bounded materialized `discover_card` projection for the currently published generation and selected media type. This table intentionally duplicates the tiny card fields needed by Discover so Story Detail enrichment and retention updates do not invalidate/re-read the Discover surface merely because they touch a shared Story table.

It must not return Story Detail description/child collections and must not join a broad Story table on the hot path.

The persistence/read contract is explicitly stateful:

```text
DiscoverPersistenceState
    ├── Absent
    └── Published(
            generation,
            provenance,
            cards     # may be empty
        )
```

`Absent` means the selected source/media scope has **never completed a successful Discover publication**. `Published(emptyList())` means a successful acquisition was published but no cards were eligible/present; it is a durable valid result and must survive DB reopen/process recreation.

The normative target is **one coherent SQL/Room observation** that resolves publication existence, the published generation, provenance, and optional cards together. Conceptually it uses the publication/source-state row as the left side of the read:

```text
catalog_source_state(current published generation)
        LEFT JOIN discover_card ON same source/media/generation
        ↓
Absent OR Published(generation, ordered bounded cards)
```

An inner join that makes `Published(empty)` indistinguishable from `Absent` is rejected.

Do not implement publication readiness as independently observed `publishedGenerationFlow + cardsFlow + combine/settle`, because that recreates the multi-stream readiness ambiguity Step 2 is intended to remove.

A fixed two-query transactional/read-snapshot shape is acceptable only if Room/SQLite constraints make it materially clearer/cheaper **and** tests prove callers cannot observe mismatched generation/card state **and** can still distinguish `Absent` from `Published(empty)`. Per-card DAO calls are rejected.

The duplication is deliberate and bounded: the initial product snapshot contains at most 19 section memberships per selected media type.

### 8.6 Story Detail query shape

Story Detail is keyed by a `StorySourceRef`/equivalent containing stable source identity, not by hidden global source state.

A fixed small query set is permitted for one Story:

- one summary/detail projection;
- fixed child queries for authors/artists/genres where Room mapping requires them.

The query count must be independent of total Catalog size and must not scan/observe all Story rows. Child/result cardinality is bounded by the importer limits in Section 9.6.

### 8.7 Transactional Discover publication and bounded delta retention

One successful Discover acquisition is published atomically for one `(CatalogSourceKey, mediaType)` scope.

Slow/untrusted work is completed before the short mutation/Room critical section:

1. acquire source data outside Room and outside the Catalog mutation gate;
2. host-stamp/verify acquisition provenance;
3. validate identifiers, text, locators, section membership, section caps, and provenance;
4. normalize/cap semantic sections and derive deterministic `StoryId` values outside the DB transaction.

The runtime then enters one short `CatalogMutationGate` shared with active-pin transitions. While that gate is held, storage performs one Room transaction:

5. read only the **previous current generation's bounded Story IDs** for this source/media scope;
6. compute `removedStoryIds = previousCurrentStoryIds - newCurrentStoryIds` (both sets are bounded by the Discover caps);
7. upsert source identity/summary rows in bulk;
8. write the new bounded materialized `discover_card` generation in bulk; a generation with zero cards is valid;
9. advance/create the source/media `catalog_source_state.published_generation` and publication provenance;
10. delete the obsolete prior Discover-card generation;
11. update reachability/retention only for the bounded `removedStoryIds`, the newly reachable IDs, and bounded orphan-retention overflow while honoring the active-pin snapshot protected by the same mutation gate;
12. commit, then release the mutation gate.

Failure before commit leaves the previous published state for that source/media scope visible and does not disturb the other media type. If the new successful snapshot is empty, the committed state is `Published(empty)`, not `Absent`.

#### 8.7.1 Foreground pruning complexity invariant

Retention is required to have **bounded work**, not merely a bounded final row count.

A successful publication may perform retention work proportional only to:

```text
O(current bounded snapshot
  + removed bounded delta
  + bounded orphan-retention overflow)
```

It must not perform a global Story/detail scan, unbounded `COUNT`, or unbounded `ORDER BY` over historical Catalog rows on each Discover refresh.

The explicit `story_orphan_retention` index/set contains at most 64 entries after every mutation. Because the previous state is already bounded and each publication can remove only a bounded number of current Stories, any overflow introduced by one publication is also bounded. Indexed `LIMIT` operations over this bounded retention set are allowed; scanning all `story_detail` rows to rediscover the same set is rejected.

The UI never observes a half-replaced Discover-card generation.

### 8.8 Discover database invariants

Storage must enforce—not merely assume—basic materialized-snapshot correctness.

At minimum, one published source/media/generation scope must reject:

- duplicate `(sectionKind, itemPosition)`;
- duplicate `(sectionKind, storyId)` within one section;
- invalid section/item positions;
- membership outside the admitted section/card caps.

The same Story may legitimately appear in different semantic sections.

### 8.9 Story Detail atomic enrichment

Story Detail enrichment updates only the selected source Story's keyed summary/detail/child rows. It must not rewrite the materialized Discover-card snapshot or execute a Catalog-wide reconciliation pass. A later explicit Discover refresh may publish newer card metadata.

A successful `upsertStoryDetail(...)` is one atomic keyed publication:

1. validate/normalize the complete bounded detail payload and host provenance before opening a Room transaction;
2. begin one Room transaction;
3. upsert the selected Story's summary/detail scalars;
4. replace that Story's authors/artists/genres in deterministic order;
5. persist the detail-specific acquisition provenance;
6. apply one bounded/coalesced access touch when appropriate;
7. commit.

A failure before commit leaves the prior usable detail/children/provenance unchanged. Observers must not see a new description with stale child collections merely because the importer wrote tables independently.

The keyed Story read may use Room's fixed small multi-query relation/`@Transaction` form where needed, but the observable projection must represent one coherent committed Story state.

Updating retention metadata such as `last_accessed_epoch_ms` must not create a tight reactive invalidation/write loop. The implementation may touch access metadata once on destination activation or via another bounded/coalesced mechanism; it must not write on every observer emission/recomposition.

### 8.9.1 Active-pin / pruning serialization

An in-memory pin snapshot taken without synchronization is insufficient because a user can open a Story while a Discover publication is between “snapshot pins” and “prune”.

Step 2 therefore freezes one short runtime `CatalogMutationGate` (name may vary, semantics may not):

- registering an active Story pin and making the Story route visible are ordered under the gate; the pin exists before publication/pruning can remove that Story;
- Discover publication enters the same gate only for its short mutation/Room transaction after all slow acquisition/validation work is complete;
- removing the pin is ordered under the gate after the Story destination/transition no longer owns demand;
- the gate is never held across remote/source I/O, image fetch/decode, long UI work, or waiting for user interaction.

This provides a deterministic happens-before relation between active UI demand and pruning without turning the runtime into a global long-lived lock.

### 8.10 No encoded giant payload as the read contract

A giant serialized Story JSON/blob stored as the only durable representation is rejected for Step 2. The UI must not deserialize full Story Detail merely to obtain a cover locator or title.

### 8.11 Indices

At minimum, storage review must prove indexed access for:

- `(source_key, source_story_id)` source identity;
- `story_id` and/or the full keyed source-detail route used by Story Detail;
- current Discover card rows by `(source_key, media_type, generation, section_kind, item_position)`;
- orphan/detail retention ordering where pruning uses `last_accessed_epoch_ms`.

Query-plan evidence belongs in the implementation checkpoint when practical and is required for the aged-storage performance checkpoint if query behavior is uncertain.

---

## 9. Local acquisition and real persistence simulation

### 9.1 Seed is a source, not a repository shortcut

The local Step 2 path uses a typed deterministic acquisition source in development/benchmark/test builds.

```text
LocalSeedAcquisitionSource
        ↓
CatalogAcquisitionExecutor
        ↓
CatalogImporter
        ↓
Room transaction
        ↓
Catalog read ports
        ↓
Discover / Story Detail
```

Discover and Story Detail do not know that the acquisition source is local.

### 9.2 No JSON read path

The deterministic seed is expressed as typed fixture/source data and real bundled cover image resources. It must not be a JSON file read directly by a UI or repository path.

### 9.3 Same write contract as a future production acquisition adapter

The local seed and final test-isolated plugin adapter both produce the same acquisition command types a later production adapter would use:

```text
Acquire Discover(catalogSourceKey, mediaType)
source implementation
      ↓
DiscoverAcquisitionSnapshot + AcquisitionProvenance
      ↓
CatalogImporter.publishDiscover(...)

Acquire Story Detail(StorySourceRef)
source implementation
      ↓
StoryDetailAcquisition + AcquisitionProvenance
      ↓
CatalogImporter.upsertStoryDetail(...)
```

No plugin-specific DTO/type escapes into Discover or Story UI state.

### 9.4 Development/benchmark/test only

The local deterministic source is not a permanent production demonstration catalog. It belongs to development/benchmark/test wiring and exists to validate the real V2 storage/read/UI path before production plugin/runtime admission.

This preserves the Step 1 salvage decision that production demonstration catalogs are not migration assets.

Step 2 acceptance therefore distinguishes:

```text
production-shaped capability architecture: required
ship-ready production remote acquisition: not yet required
```

### 9.5 Bootstrap ownership — only `Absent` may auto-bootstrap

Runtime bootstrap authority uses `DiscoverPersistenceState`, not card count:

```text
Absent + admitted acquisition source
    -> exactly one foreground bootstrap single-flight

Absent + no admitted acquisition source
    -> explicit capability/source-unavailable state; no fake seed

Published(empty)
    -> completed Empty product state; no automatic bootstrap

Published(content)
    -> render persisted content; no automatic bootstrap
```

If the selected `(CatalogSourceKey, mediaType)` scope is `Absent`, `:catalog:runtime` may trigger exactly one active foreground bootstrap acquisition for that scope **only when the current build wiring provides an admitted acquisition source**.

Ownership is explicit runtime state/single-flight. Intermediate Room invalidations, zero card rows, or a successfully published empty generation must not create repeated bootstrap executions.

A successful empty acquisition advances durable publication state. Reopening the database or recreating the process must read `Published(empty)` and must not automatically call the source again. A user may still invoke an explicit manual refresh if the product exposes that action.

If `Published(content)` exists for the selected media type, normal launch reads it immediately and does not reacquire merely because the process restarted. Switching to the other enabled media type bootstraps only when that exact scope is `Absent` and an acquisition source is present.

A production/release build without a production acquisition source must not pretend that bootstrap can succeed. `Absent + no source` remains explicit rather than silently wiring a test seed into production.

### 9.6 Acquisition input ceilings

All acquisition data is validated before it becomes durable/UI state. The implementation plan must freeze concrete values no looser than these initial Step 2 ceilings unless a smaller limit is chosen:

- `CatalogSourceKey.value`: ≤128 UTF-8 bytes;
- `sourceStoryId`: ≤512 UTF-8 bytes;
- Story title: ≤1,024 Unicode scalar values;
- Story description: ≤64 KiB UTF-8;
- author entries: ≤32, each value ≤512 Unicode scalar values;
- artist entries: ≤32, each value ≤512 Unicode scalar values;
- genre/tag entries: ≤64, each value ≤256 Unicode scalar values;
- cover locator text: ≤4,096 characters before typed locator validation;
- semantic section memberships: bounded by the approved 5/9/5 policy before persistence.

Oversized/malformed values fail import as typed acquisition validation failures. They are not truncated invisibly in a way that could corrupt identity/provenance. If implementation evidence shows a legitimate reference source needs a higher value, changing a ceiling requires an explicit design/checkpoint update rather than an ad-hoc parser exception.

---

## 10. Discover semantic projection

### 10.1 Popular

Popular preserves provider/source Popular membership/order when supplied by the acquisition source. Rating must not silently substitute for popularity.

Initial maximum: 5.

### 10.2 Latest Updates

Latest requires a trustworthy source update timestamp and orders newest first with deterministic tie-breaking.

Initial maximum: 9.

### 10.3 Top Rated

Top Rated requires a valid rating signal and preserves its source scale. Ranking normalizes only enough to compare eligible entries deterministically; absent rating means ineligible rather than zero.

Initial maximum: 5.

### 10.4 Media selection

Both `MANGA` and `LIGHT_NOVEL` are enabled Step 2 media selections.

Media selection is feature-owned UI state. Changing selection cancels/replaces only the previous Discover projection collector/bootstrap scope as needed and reads the already-published materialized cards for the new key. UI does not invoke Story Detail acquisition or source/plugin execution directly.

The selected media type is saveable across ordinary configuration recreation. Step 2 does not add another durable preference solely for this control; after process death the default is **Manga**, matching the approved visible order, unless a later settings capability owns a persisted preference.

### 10.5 Stable identity and ordering

Every rendered item has a stable `StoryId`, a stable source reference, and deterministic section/item key. Progressive loading must not invent placeholder identities that are replaced later, because that causes composition churn and scroll instability.

`StoryId` follows the deterministic `SourceStoryKey(catalogSourceKey, sourceStoryId)` derivation from Section 8.2. Mutable Story metadata never participates in Step 2 identity derivation.

### 10.6 Cross-source canonicalization

Step 2 admits only one active acquisition source for the product path and therefore does not solve multi-plugin canonical fusion. The UI and persistence use stable source-derived Story identity, but cross-source reconciliation/deduplication remains a future Catalog Engine capability admission.

This is deliberate: Step 2 must not reintroduce V1 A1/A2/A3 canonical/global work merely to prepare for future multi-source support.

---

## 11. Discover UI baseline

### 11.1 One vertical scroll owner

Discover uses one `LazyColumn`-style vertical scroll owner. Section internals may scroll horizontally when useful, but nested vertical grids/lists are rejected. `:core:designsystem` may wrap or decorate that caller-owned content but does not create `LazyColumn`, `LazyVerticalGrid`, `verticalScroll`, `ScrollState`/`LazyListState`, or force full-screen sizing.

### 11.2 Baseline composition

The final Step 2 app presentation used for screenshot and performance acceptance consumes the minimal admitted `:core:designsystem` slice from the application root. `HikariStartupApp` installs `HikariTheme` once; Unknown, FirstRun, Discover, and Story consume it without nesting another theme at `CatalogEntryPoint`. The existing `Ready + firstFrameReached` composition gate remains the only trigger for Catalog entry. Repeated Hikari visual language (Material3 palette/typography/shapes, shared spacing, section heading, static skeleton, empty/error/inline feedback, pull refresh) comes from the shared module. The media segmented control was a Task 13 migration primitive and is retired by Task 14 under the R2.8 shrink-only exception; final Manga/Light Novel navigation remains feature-local. The root shape family reuses the reviewed V1 base values (8/12/20/28/36.dp) without re-admitting V1 semantic-shape locals; generic card/state/skeleton corner roles consume those Material3 shapes instead of recreating equivalent `RoundedCornerShape` objects feature-locally. Repeated generic spacing that exactly matches the admitted Hikari scale consumes `MaterialTheme.hikariSpacing`. Bounded feature-specific cover sizes/aspect ratios, odd geometry, section semantics, copy, state, and image ownership remain in `:feature:catalog`. Step 2 does not mechanically move every one-off `dp` value into shared tokens or add wrapper-for-wrapper abstractions merely to satisfy visual purity before benchmark.

The first visual proposal may reuse or redesign the V1 hierarchy:

- Popular as a wide hero/pager or bounded horizontal hero composition;
- Latest as a compact bounded card composition;
- Top Rated as ranked full-width rows.

The exact visual form is not constitutional. Section semantics, stable identity, accessibility, and bounded cost are. Manga and Light Novel availability is frozen Step 2 presentation policy. In the final Task 14 surface, a feature-local `CatalogMediaDestinationNav` renders exactly the two literal destinations and dispatches the existing media-selection intent; the options are not rebuilt from `CatalogMediaType.entries.map` or promoted into `DiscoverUiState`. The navigation owns no observer/acquisition state. The bounded section list is emitted directly into the existing `LazyListScope`; Step 2 does not materialize a second flattened `DiscoverViewportRow` list/model solely to adapt section data to Compose.

### 11.3 Empty/bootstrap state

When persistence is `Absent` and bootstrap is active, render geometry-matching skeletons rather than a blank body. A durable `Published(empty)` renders the completed product Empty state instead of returning to bootstrap/loading.

### 11.4 Cached/manual-refresh state

When durable `Published(content)` or `Published(empty)` exists, Discover wraps its existing single vertical scroll owner in the admitted `HikariPullToRefresh` primitive. Manual refresh does not replace usable content with full-screen loading. Existing content/empty state stays visible; the pull indicator is the only refresh-progress chrome; a retryable failure appears as non-blocking inline feedback with a visible Retry action. Pull uses the normal `refresh()` intent; visible failure actions use the distinct `retry()` intent, while source acquisition remains single-owner. The previous feature-local full-width `LinearProgressIndicator` and empty-state manual `Refresh` button are removed so one operation does not have competing progress/trigger chrome.

`Absent` initial bootstrap/loading and no-content failure do not expose pull refresh; they retain explicit loading/Retry behavior. Retryable activation/open-storage failure must retry activation rather than render a decorative no-op action; a retryable active-session storage observation failure must be able to restart bounded observation before/while converging on the same acquisition owner when acquisition is still required. Non-retryable failures expose no action. The design-system primitive suppresses duplicate pull dispatch while `refreshing = true` and exposes one accessibility custom action labeled `Refresh` only when pull refresh is enabled. It uses the feature-provided modifier without appending `fillMaxSize`; when disabled it bypasses `PullToRefreshBox` entirely so unavailable refresh does not retain unnecessary Material3 gesture state.

### 11.5 Partial content

If a semantic section has no eligible data while others have data, omit the empty section. A screen-level empty state is used only when loading has completed and no section has usable content.

### 11.6 State restoration

Returning from Story Detail should preserve Discover scroll position and section identities whenever the Activity/capability session survives. Process recreation restores the destination and can reconstruct Discover from persistence without relying on retained bitmap objects.


### 11.7 Task 13 foundation versus Task 14 visual restoration

Task 13 may replace shared chrome (theme, media selector, section header, skeleton/state/feedback primitives, Discover pull refresh) but preserves the existing feature composition unless a minimal migration change is required. It must not be used as an excuse to mix Design System admission with a large visual rewrite.

Task 14 then owns the Step 2 visual composition. V1 screenshots are **comparative product evidence**, not pixel goldens and not permission to copy V1 architecture. The Task 14 quality floor is:

- artwork remains a primary information surface and portrait covers are not forced through obviously inappropriate landscape/full-width crop geometry;
- Popular, Latest Updates, and Top Rated are visually differentiated and remain quick to scan;
- compact Discover retains useful content density rather than turning every bounded item into a large generic full-width surface;
- Story Detail presents cover/title/rating/status as a coherent summary and groups rich metadata for scanning; content type is shown only when an existing trusted user-safe field actually supplies it, never inferred for visual parity;
- typography and spacing use the Task 13 Hikari environment consistently without requiring feature geometry to become global tokens;
- loading/error/refresh states use the same final geometry and do not destroy retained usable content;
- compact and wider configurations reflow intentionally;
- product surfaces contain product copy, not architecture/debug commentary;
- Search is not visually faked before the Search capability exists;
- Chapters/Reader remain absent.

Task 14 prefers feature-local composables such as Discover-specific hero/card/ranked-row/media-nav or Story-specific hero/metadata sections over new generic Design System wrappers. It does not add blur/backdrop/glass/shimmer/infinite animation, another vertical scroll owner, eager full-feed prefetch, N+1 detail acquisition, or new image/runtime ownership merely to imitate V1 aesthetics.

### 11.8 Task 14 Dantotsu-inspired geometry and composition contract

The companion blueprint document is normative for exact Task 14 presentation geometry: `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.md`. The compact reference is 360.dp and the feature-local wide reflow threshold is 600.dp. Required compact geometry includes:

```text
screen horizontal inset                 20 dp
major section gap                        32 dp
section title -> content gap             12 dp
portrait artwork ratio                    2:3

floating media nav height                64 dp
floating media nav max width            400 dp
floating nav outer padding                4 dp
selected nav item height                 56 dp
outer / selected radius              36 / 28 dp
bottom gap above navigation safe inset   16 dp
Discover content bottom clearance       104 dp + navigation-bar bottom inset

Popular hero card                   296 x 184 dp
Popular cover                       104 x 156 dp
Popular cover -> copy gap                16 dp
Latest poster                        92 x 138 dp
Latest tile gap                          12 dp
Top Rated minimum row height             88 dp
Top Rated cover                      48 x 72 dp
Story compact cover                 112 x 168 dp
Story wide cover                    144 x 216 dp
```

Token application rule: the numeric geometry table is the visual target, but values that match the admitted generic spacing/shape vocabulary are implemented through those Task 13 tokens. In particular, the Popular card does not introduce a 14.dp padding token: its 156.dp cover is vertically centered inside the 184.dp card, producing 14.dp visual breathing room as derived geometry.

Required silhouettes:

- **Popular:** horizontal asymmetric artwork-led hero rail, <=5; at 360.dp the 320.dp content width with a 296.dp card and 12.dp item gap leaves a deliberate 12.dp next-card peek;
- **Latest Updates:** horizontal poster-first rail, <=9, approximately three full compact posters plus partial next item visible at 360.dp;
- **Top Rated:** vertical rank-led rows, <=5, not a third card rail;
- **Story Detail:** portrait cover + title/rating/status identity cluster, followed by About, Authors, Artists, Genres, and Status/Language groups;
- **Manga / Light Novel:** persistent floating bottom pill navigation on Discover only.

The page header uses the selected destination name (`Manga` or `Light Novel`) as the identity and may use the product copy `Discover extraordinary stories.`. Developer copy such as `Three distinct signals. One deliberately bounded shelf.` is forbidden. `See all` is not rendered until a real destination exists. The Story concept image's Read/Library/Chapters controls are explicitly outside Step 2.

Feature code consumes the R2.8 Hikari theme for generic spacing/colors/shapes/typography. Task 14 intentionally migrates the root palette/typography under §2.6, but it does **not** replace the Task 13 spacing or shape scale and does not change `HikariTheme` ownership/construction. The existing `HikariSpacing` values remain exactly `4 / 8 / 12 / 16 / 20 / 24 / 32.dp`, and the Material shape roles remain `8 / 12 / 20 / 28 / 36.dp`. Therefore compact/wide insets, section gaps, shelf gaps, nav padding/product gap/breathing room, and identity gaps consume `MaterialTheme.hikariSpacing`; 12/20/28/36.dp corner roles consume `MaterialTheme.shapes.small/medium/large/extraLarge` rather than new `RoundedCornerShape` literals. `HikariPalette.kt` and `HikariTypography.kt` are explicit Task 14 migration targets; `HikariSpacing.kt` and `HikariTheme.kt` remain locked unless a compile-only import adjustment is mechanically required, in which case no spacing/theme-ownership semantics may change.

Cover sizes, rail widths, fixed nav height/selected height/max width, rank width/row height, and the 600.dp reflow rule are Catalog-only geometry and must not be promoted into public Design System dimensions before a second proven caller exists. The 104.dp Discover bottom clearance is a derived layout quantity (`64.dp nav + space16 + space24`) plus the navigation-bar inset, not a new Design System token.

Visual acceptance is an explicit human-reviewed evidence gate. Task 14 compares real-app compact/wide/light/dark captures against the approved blueprint and reviewed V1 quality floor on hierarchy, artwork presentation, content density, typography/spacing coherence, metadata scanability, and adaptive layout. Task 15 owns the permanent deterministic cross-device screenshot harness/freeze. A screenshot test that executes successfully but records an obviously weaker UI is not a visual pass. Any deliberate regression requires an evidence-backed product/performance rationale in the Step 2 checkpoint.

---

## 12. Story Detail flow

### 12.1 Route payload and acquisition authority

Navigation carries an explicit stable source reference plus only a small optional visual continuity hint.

Conceptually:

```text
StorySourceRef(
    storyId,
    catalogSourceKey,
    sourceStoryId
)
+
optional CoverAssetKey
```

`StoryId` remains the UI/persistence identity; `catalogSourceKey + sourceStoryId` prevents Story Detail acquisition from guessing source authority from mutable/global runtime state.

Do not pass:

- bitmap bytes;
- a full Story DTO;
- a Room entity;
- a plugin DTO;
- the entire Discover card list.

### 12.2 Progressive detail rendering

On selection:

```text
StorySourceRef + optional CoverAssetKey
        ↓
Story Detail surface composed immediately
        ├── request same cover asset key from bounded image cache
        └── begin keyed persisted Story Detail observation
                         ↓
                  if detail missing
                         ↓
              one keyed acquisition
                         ↓
                 validate + write persistence
                         ↓
                  observer enriches UI
```

The cover/summary surface can remain useful while rich detail is loading.

### 12.3 Detail acquisition

A Story Detail source fetch is triggered only when the selected source Story lacks the required persisted detail, or when the user explicitly retries a failed missing-detail acquisition.

The acquisition key is the explicit source item identity, not `StoryId` plus an inferred currently active source.

Step 2 has no background TTL refresh loop for Story Detail.

### 12.4 Failure

If detail acquisition fails while a Discover summary/cover exists, keep the useful visual/summary state visible and show an inline retryable detail issue. Do not collapse the entire screen to a blank error surface.

If an acquisition payload violates Step 2 bounds, surface a typed scoped acquisition/import issue; do not partially persist an unbounded detail and do not erase the prior usable summary/detail.

---

## 13. Visual Asset Fast Path and continuity

Cover artwork is a first-class Step 2 performance/UX concern.

### 13.1 Stable asset identity

A cover request uses a stable key derived from Story identity plus a narrow cover revision/artwork identity, conceptually:

```text
CoverAssetKey(storyId, coverRevision)
```

Using only `StoryId` is insufficient because a source may change the cover. Re-hashing a giant Story payload is also rejected; cover identity must be narrow.

`coverRevision` is an acquisition-adapter output with these rules:

- it must be stable for the same authoritative artwork and change when the source declares/observes a different authoritative artwork identity;
- for a trusted local deterministic asset, it derives from a **stable logical asset key/version**, not an Android integer `R.drawable.*` value persisted into Room;
- for a remote source with an immutable artwork/version token, the adapter may use/hash that token;
- otherwise the safe default is a hash of the complete validated normalized remote URI, including meaningful query data; generic code must not strip query parameters in an attempt to guess that signed/volatile URLs refer to the same bytes;
- a source adapter may deliberately stabilize volatile signed URLs only when its reviewed source contract supplies a separate stable artwork identity/version;
- unrelated Story metadata never participates.

This contract prioritizes correctness over cache-hit speculation: unnecessary revision churn is preferable to serving stale/wrong artwork under an identity that did not actually prove equivalence.

### 13.2 Typed locator and source policy

Presentation code never receives a plugin-controlled raw URL as generic image-loader authority.

Conceptually:

```text
CoverLocator.TrustedLocalResource(...)

CoverLocator.RemoteHttps(
    catalogSourceKey,
    normalizedUri,
    revision
)
```

A `SourceAssetPolicyProvider`/equivalent supplies the allowed remote-host policy for the host-authoritative `CatalogSourceKey` used by integration/remote wiring. Local trusted locators and remote source-controlled locators are distinct types and cannot be confused by presentation code.

A persisted remote locator remains bound to its host-authoritative `CatalogSourceKey`; process recreation must not require presentation code to invent/guess the trust policy.

Trusted local locators persist a stable logical asset identifier/version resolved by the capability's non-release fixture wiring. Raw Android resource integer IDs are not a durable Catalog identity contract.

### 13.3 Cache hierarchy

```text
composed request
      ↓
bounded decoded-memory cache
      ↓ miss
bounded disk cache
      ↓ miss
trusted local resource/file OR validated HTTPS asset source
      ↓
bounded sampled decode to requested display size
      ↓
render
```

Step 2 may use Coil or an equivalent Android image loader, but the loader is created lazily inside the Catalog capability and must not add a hidden process-start initializer.

### 13.4 Initial cache ceilings

The implementation must configure explicit ceilings; framework defaults without reviewed bounds are insufficient.

Step 2 acceptance ceilings:

- decoded memory cache: **no more than 32 MiB**;
- disk cover cache: **no more than 128 MiB**.

The performance-validation task may lower these limits. Raising them requires measured evidence and explicit review.

The loader must cooperate with Android memory pressure/trim behavior.

### 13.5 Viewport demand

Do not eagerly request every cover in every section.

High priority is limited to:

- currently composed/visible cards;
- a small bounded near-viewport prefetch window;
- the Story currently transitioning to/from Story Detail.

No more than roughly one additional viewport of manual cover prefetch may be introduced without benchmark evidence. Full-feed prefetch is rejected.

### 13.6 Concurrency

Image fetch/decode concurrency must be explicitly bounded. The initial implementation must not exceed **8 concurrent cover fetch/decode jobs** for Step 2 foreground work; the performance task may lower this based on frame/memory evidence.

Visible requests take priority over optional prefetch. Prefetch may not occupy all image capacity while visible covers wait.

### 13.7 Stable geometry

Every card reserves final cover geometry before the image is ready. Placeholder → real cover must not change card dimensions or shift the scroll position.

### 13.8 Discover → Story continuity

Story Detail requests the same stable asset key already used by Discover. If the decoded image remains in memory, the transition is immediate. If memory was evicted, disk cache is the fast fallback. Remote network is the last fallback when that build/test wiring has an admitted remote source.

A navigation transition does not create a second bitmap copy solely for continuity.

### 13.9 Back continuity

Returning to Discover should not force a new network/source fetch for covers that remain in memory/disk cache. The UI may recompose, but cache identity stays stable.

### 13.10 Failure isolation

Image failure is local to the cover asset. Metadata can remain usable. A metadata/detail failure likewise does not delete an already-rendered cover.

Stable image failures must not create a tight retry loop while the same card remains composed.

### 13.11 Remote cover security and encoded resource bounds

For the final real-plugin integration probe—and any later production source that adopts the same contract—remote artwork must pass a source-scoped image policy:

- HTTPS only;
- hostname must be declared/allowed for the acquisition source;
- every redirect is revalidated against the allowed-host policy;
- connect/read timeouts are finite;
- encoded response size is bounded, with **8 MiB** as the Step 2 maximum per cover response;
- response content must be an accepted image media type/decoder path rather than arbitrary content;
- plugin-provided `file:`, arbitrary `content:`, cleartext HTTP, and undeclared-host locators are rejected;
- local development seed resources use an explicit trusted-local locator type and cannot be confused with plugin-controlled remote locators.

The image policy is keyed by source identity, not application-global trust.

### 13.12 Decoded-image resource bounds

Encoded-byte bounds alone are insufficient because a highly compressed image can declare an extreme decoded pixel surface.

Step 2 freezes these initial hard source-image ceilings:

- maximum encoded response: **8 MiB** (Section 13.11);
- maximum declared/source width: **8,192 px**;
- maximum declared/source height: **8,192 px**;
- maximum declared/source pixel surface: **32,000,000 pixels**.

A source image exceeding any one of these ceilings is rejected as a typed artwork-resource failure before an original-size allocation is attempted. The performance task may **lower** these ceilings. Raising them requires an explicit design/checkpoint change with device evidence.

Step 2 also requires:

- bounds/probes on source image dimensions before full decode where supported by the selected decoder stack;
- sampled/target-size decode for the actual rendered card/detail dimensions;
- no original-resolution decode merely to display a bounded card/detail surface;
- decode/allocation work outside Main;
- if a decoder path cannot safely inspect/bound dimensions before dangerous allocation, that path is not admitted for untrusted remote cover input;
- tests using a small encoded payload with pathological dimensions/metadata where the chosen decoder stack permits construction of such a fixture.

The implementation plan converts these frozen constants into executable tests/configuration; it does not choose looser values ad hoc.

---

## 14. Foreground acquisition and refresh ownership

### 14.1 One owner

`:catalog:runtime` owns all Step 2 foreground acquisition.

The two logical work keys are conceptually:

```text
DiscoverRefresh(catalogSourceKey, mediaType)
StoryDetail(catalogSourceKey, sourceStoryId)
```

Only one active execution exists for one logical key. Additional callers join or observe the existing execution rather than launching duplicate I/O.

### 14.2 Manual refresh only

Step 2 exposes user-initiated Discover refresh through `HikariPullToRefresh` only after a durable publication exists. `DiscoverViewModel.refresh()` is the normal pull-refresh intent. `DiscoverViewModel.retry()` is the failure-recovery intent and may re-activate/re-observe before acquisition when the failure occurred earlier in the pipeline. Those intents may differ in orchestration, but **all source acquisition converges on one guarded foreground single-flight owner**; Retry cannot create a second acquisition pipeline. The design-system primitive cannot start acquisition itself and cannot create a work owner.

There is no:

- WorkManager continuation;
- periodic refresh;
- process-start refresh;
- background recovery owner;
- durable retry queue.

### 14.3 Cancellation

Cancellation propagates through acquisition. A canceled source fetch must not publish a partial new snapshot. Room critical sections protect short state transitions/commits, never remote/slow source work.

### 14.4 Cached-content semantics

If manual refresh fails and a prior Discover snapshot exists:

```text
content remains visible
+ issue/retry information
```

The failure does not erase the last successful generation.

---

## 15. Retention and aging

Step 2 must remain bounded after long use, not only on a fresh install.

### 15.1 Discover materialized snapshot

Only the current published materialized Discover-card generation is reachable for presentation. Obsolete generations are deleted after successful publication; refresh history is not retained as an ever-growing log.

### 15.2 Story summaries/details

Step 2 currently has no Library/Reader/Download pins. Therefore storage may retain:

- Stories referenced by the current Discover snapshots;
- any Story explicitly pinned by the currently active Story Detail/transition session;
- at most **64 recently accessed orphan Story Detail records** represented by the explicit bounded `story_orphan_retention` index/set.

Older unreferenced details/summaries are pruned deterministically during successful foreground publication/maintenance owned by the Catalog runtime. No background process is required.

Retention maintenance is delta-driven. A Discover publication starts from the previous bounded generation and the new bounded generation, computes the removed/newly reachable Story IDs, and updates only those reachability candidates plus bounded orphan-retention overflow. It must not rediscover orphan candidates by globally scanning/sorting all Story Detail/history rows.

Future capabilities that need durable Story reachability must explicitly extend this contract through their own admission rather than relying on accidental Step 2 retention.

### 15.3 Active Story pins

The runtime owns a bounded active pin set, conceptually:

```text
activeStoryPins: Set<StorySourceRef>
```

Pins exist only for currently demanded Story Detail/transition work and are removed at terminal destination/session lifetime. They are coordinated with pruning by the `CatalogMutationGate` in Section 8.9.1:

- pin registration happens-before exposing the Story route as active demand;
- publication/pruning snapshots/uses pins while holding the same short gate around its mutation transaction;
- pin removal happens only after the destination/transition releases demand.

Import/pruning therefore cannot delete a Story that became actively viewed in the race window between an unsynchronized pin snapshot and commit.

The pin set is not a process-long history/cache and must remain bounded by active UI demand.

### 15.4 Access aging writes

`last_accessed_epoch_ms` is retention metadata, not a reason to write on every Flow emission/recomposition. Access touches are performed once on a bounded semantic event such as Story destination activation, or through an explicitly coalesced mechanism.

The implementation must prove that reading/observing Story Detail does not create a read → touch write → invalidation → read loop.

### 15.5 Runtime maps

Single-flight maps contain only active logical work and remove terminal entries. There is no process-long map of every Story ever requested.

### 15.6 Image caches

Image caches follow the byte ceilings in Section 13 and may evict assets regardless of session age. Visual continuity is best-effort within the bounded hierarchy, never a promise of permanent RAM residency.

---

## 16. Failure and retry model

### 16.1 Discover

- `Absent` + admitted source + bootstrap active → layout-shaped loading;
- `Absent` + bootstrap/source failure → no-content failure surface with explicit retry;
- `Absent` + no admitted acquisition source → explicit source-unavailable/capability-local state;
- `Published(empty)` → completed Empty state; no automatic bootstrap loop;
- `Published(content)` + manual refresh active → retained content + refresh indicator;
- `Published(content)` + refresh failure → retained content + non-blocking issue/retry;
- `Published(empty)` + manual refresh failure → remain Empty + scoped issue/retry rather than reverting to `Absent`;
- storage-open/read failure with no usable published state → fatal capability-local error/retry;
- cancellation → propagate; do not convert to user failure.

### 16.2 Story Detail

- summary/cover available + detail missing → useful visual shell + detail loading;
- cached detail available → render immediately;
- detail acquisition failure → retain summary/cover/cached detail and expose scoped retry;
- image failure → scoped artwork placeholder; do not invalidate metadata;
- cancellation → propagate.

### 16.3 Retry authority

Step 2 has explicit user Retry, explicit durable-state pull Refresh, and bootstrap single-flight only. Retry may re-run activation or restart bounded observation when that is the failed stage; source acquisition itself still has one single-flight authority shared with pull Refresh. There is no exponential background retry engine, durable failure queue, or automatic repeated plugin execution.

---

## 17. Plugin integration gate at the end of Step 2

Plugin integration happens only after the complete local persistence/read/UI/image path is green.

### 17.1 Goal

Prove that one real reviewed reference plugin can supply Discover and Story Detail data through the same acquisition/import boundary without changing Discover UI, Story UI, Room read contracts, or image cache ownership.

This is a **correctness/integration gate**, not production plugin-runtime admission.

### 17.2 Reference plugin — MangaUpdates is the Step 2 default

The default Step 2 reference is the reviewed V1 **MangaUpdates Catalog** behavior, because its Home contract already exposes the three semantic families Step 2 is required to prove (`POPULAR`, `LATEST_UPDATES`, `TOP_RATED`) and its content mapping can exercise the enabled Manga/Light Novel boundary without asking the Step 2 adapter to infer semantic section meaning from provider titles/IDs.

The reference behavior to provenance-track comes from the reviewed V1 paths equivalent to:

```text
bundled-plugins/mangaupdates-catalog/manifest.json
bundled-plugins/mangaupdates-catalog/main.js
```

The implementation checkpoint records the exact source baseline/archive/commit and content hash actually imported/executed. If later evidence requires a different reference plugin, changing the default is a design/checkpoint amendment; the adapter must never manufacture missing Step 2 semantics by provider-specific string guessing.

### 17.3 Isolation and harness ownership

Step 2 does **not** admit the full V1 plugin runtime into the production app graph.

The final gate executes the reviewed reference plugin through an isolated **`:feature:catalog` `androidTest` harness** (or code physically owned by that test source set). The harness may use `androidTestImplementation(project(":plugins:api"))` and the minimum reviewed executor/transport support copied or adapted into test-only sources.

Normative restrictions:

- no `:plugins:runtime` or V1 production plugin-composition dependency is added to the Step 2 production graph merely for this gate;
- imported executor/support code lives under test/integration sources and is absent from `main`/`release` artifacts;
- test-only `AndroidManifest.xml` may declare `INTERNET` when needed; main/release does not;
- no WorkManager owner, package provisioning flow, production initializer/provider/service/receiver, or production credential lifecycle is admitted;
- if the chosen plugin executor cannot be isolated without introducing a production runtime edge, stop and review the boundary rather than widening production Step 2.

### 17.4 Deterministic execution before optional live smoke

The acceptance gate must be deterministic. It executes the **real reviewed plugin code** against controlled fixture transport/responses so tests can reproduce semantic Home sections, details, errors, redirects, and artwork-policy cases without external-service flakiness.

A live external MangaUpdates network probe may be retained as an **optional smoke test** outside deterministic acceptance. Live timing/availability never decides Step 2 correctness or performance acceptance.

### 17.5 Reference-plugin provenance and required proof

Required provenance evidence:

- source repository/baseline commit or archive used for the reference plugin;
- exact reference plugin path/artifact and relevant API contract version;
- content hash for the plugin package/script/fixture when practical;
- minimal test execution support copied/reused and why each part is necessary;
- proof that imported support is `androidTest`/integration scoped only;
- no casual copy of the V1 production plugin runtime/composition root merely to make the test pass.

Required data-flow proof:

```text
real MangaUpdates plugin Home operation
       ↓ controlled deterministic transport
Step 2 plugin acquisition adapter
       ↓ host-stamped/validated DiscoverAcquisitionSnapshot + provenance
       ↓
CatalogImporter.publishDiscover
       ↓
real V2 Room persistence
       ↓
Discover reads unchanged

real MangaUpdates plugin details operation for StorySourceRef
       ↓ controlled deterministic transport
Step 2 plugin acquisition adapter
       ↓ host-stamped/validated StoryDetailAcquisition + provenance
       ↓
CatalogImporter.upsertStoryDetail
       ↓
real V2 Room persistence
       ↓
Story Detail reads unchanged
```

Real-format remote cover locators then exercise the unchanged typed locator, source-policy, image cache, redirect and decode-bound path through controlled transport. The gate must prove the adapter does not derive `CatalogSourceKey`, source version, or source-host authority from untrusted payload claims.

The gate does not build:

- plugin marketplace/provisioning;
- multi-plugin aggregation;
- package updates;
- background plugin refresh;
- credential lifecycle beyond what the deterministic reference execution strictly requires;
- full production JavaScript runtime admission;
- cross-source canonical fusion;
- a ship-ready remote-catalog release source.

Those remain later capabilities.

### 17.6 Performance interpretation

External plugin/network timing is not used as the deterministic Step 2 performance baseline. The plugin gate proves integration/correctness. Deterministic local images and persistence fixtures own repeatable Macrobenchmark evidence.

---

## 18. Performance constitution mapping

### PERF-01 — Working-set scope

- Discover reads only the bounded current snapshot for the selected media type, initially at most 19 memberships (at most 38 current persisted memberships when both approved media types are populated).
- Story Detail reads one Story.
- Unrelated historical Story rows do not change foreground query cardinality.
- Aged-state tests include thousands of unrelated stored rows to prove the read path remains bounded/indexed.

### PERF-02 — Narrow read/allocation ownership

- Discover projection excludes rich Story Detail fields and is keyed by selected media type.
- Story Detail does not decode global Catalog state.
- Cover locator/revision are available without decoding a giant Story payload.
- Image bytes are not copied through navigation or Room entities.

### PERF-03 — Reactive scope matches semantic demand

- one Discover snapshot observer keyed by selected media type while Discover is active;
- one keyed Story observer while Story Detail is active;
- Story Detail child tables do not force an unrelated global Catalog observer;
- image requests follow composition/viewport lifetime.

### PERF-04 — Batch work has batch semantics

- Discover import upserts summaries/memberships in batches/one transaction;
- no per-card transaction loop;
- no per-card DAO read loop;
- no cumulative reprocessing of prior refresh generations.

### PERF-05 — One execution owner per expensive work item

- one foreground Discover acquisition owner per `(CatalogSourceKey, mediaType)`;
- one Story Detail acquisition owner per source Story;
- capability-private asset subsystem owns image I/O/decode concurrency and enforces the source-scoped remote-cover policy supplied by the runtime/domain policy authority;
- no background competitor exists in Step 2.

### PERF-06 — Lifetime/aging is bounded

- current materialized Discover-card generation only;
- no growing refresh-generation history;
- explicit `story_orphan_retention` set capped at 64;
- pruning work is delta-driven from bounded previous/current generations rather than a history-wide sweep;
- active pins are synchronized with pruning through the short mutation gate;
- active-only single-flight maps;
- ≤32 MiB decoded cover cache;
- ≤128 MiB disk cover cache.

### PERF-07 — Control plane does not imply payload execution

- local Step 2 does not load plugin executable payloads;
- the final plugin gate is isolated/test-only;
- capability metadata/state checks do not imply plugin runtime startup.

### PERF-08 — Performance evidence is part of the interface

Step 2 acceptance records startup delta, first useful Discover visual/content, query/work counts, scroll/frame metrics, Story open/back latency, image cache hit continuity, repeated activation, and aged-state behavior.

Evidence is not report-only. Step 2 also freezes hard structural/performance gates: fixed query-count upper bounds, zero pre-demand Catalog/network work, zero network on deterministic persisted-returning Discover, zero network after verified memory/disk cache hits, no Main-thread DB/network/decode work, no monotonic memory growth across repeated navigation, and explicit frame/jank review criteria on the benchmark device.

---

## 19. Root-cause prevention matrix

| V1 family | Step 2 prevention |
| --- | --- |
| A1/A2/A3 | no canonical reconciliation/evidence rebuild on Discover path; quarantined engine remains out of runtime |
| A4 | collision-heavy candidate/fusion work out of scope; single-source Step 2 |
| A5/A6/A7/A8 | bounded Discover projection and keyed Story Detail queries; no global canonical/redirect/mapping reads |
| L1 | explicit I/O/CPU ownership; Room/source work never intentionally runs heavy processing on Main |
| L3/L4/L5 | single-flight foreground acquisition; no duplicate background owner; image capacity bounded |
| L6 | one bulk transactional Discover publication instead of per-card persistence |
| L7/L8 | no canonical fusion pipeline or global subscription-triggered rebuild in Step 2 |
| D1 | no retained canonical evidence history; explicit bounded orphan-retention set; delta pruning avoids global history maintenance |
| X3 | new narrow V2 domain/read projections; quarantined broad model not admitted |
| X4 | one coherent published Discover **state** rather than independently settling UI streams; durable `Published(empty)` cannot collapse into `Absent` |
| X5 | one Catalog acquisition owner per work key |
| X6 | no durable backlog/background candidate enumeration |
| X7 | batch importer/DAO contracts; no N point operations as fake batch |
| X16-X18 | production plugin runtime/control-plane not admitted in Step 2 |
| X19 | no content-mapping N+1 path in Step 2; plugin adapter performs bounded batch/source mapping |
| Discover perf recovery | stable source-derived identities, bounded sections, no canonical settlement critical path, one scroll owner, dedicated macrobenchmarks |
| Step 1 build-surface regression | exact Step 2 module/framework/variant allowlist; release seed leakage is forbidden; zero-capability tests evolve rather than disappear |
| Identity drift | frozen full-width SHA-256 v1 `StoryId` derives only from host-authoritative `CatalogSourceKey + sourceStoryId`; collision fails closed |
| Image bombs / oversized detail | encoded + decoded image bounds and bounded Story Detail/import payloads before persistence/UI |
| Empty/bootstrap feedback loop | `Absent` is distinct from durable `Published(empty)`; only `Absent` may auto-bootstrap |
| Pin/prune race | pin transitions and publication pruning share one short mutation gate |
| Story Detail half-publication | summary/detail/children/provenance publish in one keyed Room transaction |
| Same-module structural debt | zero production package SCC ratchet across all Step 2 modules |
| Benchmark fixture leakage | explicit debug/benchmark/profile/release/androidTest source matrix + release artifact checks |
| Profile comparison drift | regenerate Step 2 baseline/startup profile from the final deterministic journey before final startup comparison |

---

## 20. Performance validation task — mandatory before plugin gate

After correctness/local acceptance is green, Step 2 includes an explicit optimization/red-team task. It is allowed to change presentation parameters rather than defend the first layout at all costs.

### 20.1 What it measures

- Step 1 cold startup metrics versus Step 2 on the same device/build conditions;
- Catalog activation time;
- persisted Discover first-snapshot latency;
- first useful real-cover latency;
- time until the first complete visible section;
- multi-section scroll frame timing/jank;
- scroll-to-top behavior;
- Discover → Story Detail latency;
- Story Detail → Back latency;
- RAM-hit and disk-hit continuity;
- decode/allocation pressure;
- database query count;
- repeated activation/navigation memory behavior;
- aged database with large unrelated history;
- long browse session cache stability.

### 20.2 Deterministic fixtures

At minimum use:

- normal product fixture: both enabled media types, each with 3 semantic sections using the bounded 5/9/5 target and real compressed cover assets;
- aged-storage fixture: thousands of unrelated Story/source rows while the active Discover snapshot remains bounded;
- successful-empty fixture proving `Published(empty)` survives reopen and never auto-rebootstraps;
- repeated refresh fixture proving old Discover-card generations do not accumulate;
- aged/pruning fixture proving publication work touches only bounded generation delta + bounded orphan overflow rather than historical rows;
- deterministic pin-registration versus publication/prune race fixture;
- repeated navigation fixture covering many Discover ↔ Story Detail loops including process-recreation-style route restoration;
- cold memory-cache miss + disk hit;
- warm memory hit;
- oversized Story Detail/import fixtures that prove validation occurs before durable/UI expansion;
- pathological image fixture(s) proving encoded/decoded bounds when feasible with the selected decoder stack.

Fixtures enter storage through the same importer contract rather than direct DAO seeding when the measured behavior includes import semantics.

### 20.3 Optimization authority

If evidence is poor, this task may:

- reduce section card counts;
- defer below-the-fold section composition/data materialization;
- simplify card metadata;
- change Popular/Latest/Top Rated visual composition;
- reduce cover decode target size;
- lower image concurrency;
- shrink prefetch distance;
- lower memory/disk cache ceilings;
- reduce recomposition state width;
- alter query projection/indices;
- remove redundant animation/effects.

It may not solve jank by moving work to process startup, making caches unbounded, hiding global reads behind a background dispatcher, bypassing persistence, weakening source/image validation, or replacing one expensive owner with duplicate foreground/background owners.

### 20.4 Baseline-profile normalization before final comparison

Step 1 startup evidence was collected with the accepted Baseline Profile mode/configuration. Step 2 changes the returning product path materially, so the final Step 2 benchmark must not compare a stale Step 1-era profile against the new Discover path.

Before the final same-device Step 2 comparison:

1. complete local correctness and performance-driven UI/query simplification;
2. regenerate the Step 2 baseline/startup profile from the **final deterministic Step 2 returning Discover + Story path** using the existing benchmark/profile mechanism;
3. record the generated profile artifact/checksum (or equivalent reproducible evidence) in the Step 2 checkpoint;
4. build the measured Step 2 artifact with the regenerated profile and the same benchmark/profile mode class used by the accepted Step 1 baseline;
5. only then record the final startup delta.

Profile regeneration is not permission to move Catalog work earlier. Pre-demand trace gates remain authoritative.

### 20.5 Startup review budget

The Step 1 Redmi Note 9S/API 35 medians remain the immutable historical comparison point:

- fresh: `394.210469 ms`;
- returning: `412.547813 ms`.

A same-device Step 2 cold `timeToInitialDisplayMs` median regression greater than **10%** is an explicit review failure trigger, not an automatic waiver. The team must optimize or record a separately approved evidence-backed exception.

The new meaningful-content metrics are recorded separately so Step 2 cannot appear fast merely by preserving a quick shell while Discover becomes slow.

### 20.6 Hard performance/ownership gates

Regardless of device latency variance, the following are acceptance gates unless an explicit design change is approved:

1. **Pre-demand:** zero Catalog database open, Catalog acquisition, image/network request, and image-loader initialization before Catalog/cover demand.
2. **Discover DB work:** one coherent bounded published-generation state observation, or an explicitly proven fixed two-query equivalent; never per-card reads; `Published(empty)` must remain distinguishable from `Absent`.
3. **Story Detail DB work:** a documented fixed upper-bound query count independent of total Catalog rows.
4. **Persisted returning Discover:** deterministic local returning path performs no acquisition/network work for either `Published(content)` or `Published(empty)`.
5. **Cache continuity:** a verified memory hit performs no disk/network fetch; a verified disk hit performs no network fetch.
6. **Main thread:** no database I/O, source/network I/O, large decode, or importer CPU work intentionally executes on Main.
7. **Aging query shape:** thousands of unrelated historical rows do not change Discover result cardinality or Story Detail query count.
8. **Bounded retention work:** Discover publication/pruning touches only the bounded current/removed delta and bounded orphan-retention overflow; no global Story/detail sweep or history-wide sort/count is allowed on the refresh critical path.
9. **Repeated navigation:** repeated Discover ↔ Story loops do not produce monotonic growth in active-work maps, observers, decoded-cache ownership beyond configured ceilings, or retained Story pins.
10. **Pin/prune race:** concurrent Story open/transition and Discover publication cannot prune the newly active Story; the mutation gate is exercised by a deterministic concurrency test.
11. **Frame behavior:** the implementation plan must freeze a benchmark-device frame/jank review threshold using the available Macrobenchmark/frame metrics. A material regression versus the accepted local Step 2 baseline triggers optimization/review; it cannot be hidden by a fast TTID.
12. **Image safety:** oversized encoded/decoded artwork fails boundedly rather than causing original-size decode/OOM behavior.
13. **Profile validity:** final Step 2 startup numbers are recorded only after regenerating the Step 2 baseline/startup profile from the final deterministic journey.

The implementation checkpoint records both the numerical measurements and pass/fail evidence for these structural gates.

---

## 21. Trace and benchmark additions

Preserve all Step 1 milestones and add Catalog-specific slices rather than redefining historical labels.

Recommended traces:

```text
HikariV2:catalog-activation-start
HikariV2:catalog-storage-ready
HikariV2:discover-first-snapshot
HikariV2:discover-first-cover
HikariV2:discover-content-ready
HikariV2:story-detail-requested
HikariV2:story-detail-content-ready
```

The exact final trace names are frozen by the implementation plan/tests, but the measured concepts are required.

Macrobenchmarks should cover at least:

1. cold returning launch to Discover;
2. Discover multi-section scroll;
3. Discover → Story Detail with memory-hit cover;
4. Discover → Story Detail with memory miss/disk hit;
5. Story Detail → Back to Discover;
6. aged-storage returning launch/Discover read.

---

## 22. UI/content-state contract

Step 2 does not copy a generic V1 `ContentState` type blindly, but preserves its useful semantics.

### Discover conceptual states

```text
NoContentLoading
Content(snapshot, refreshing?, issue?)
Empty
NoContentFailure(issue)
```

The important invariant is that `Content` can coexist with refresh failure/issue; failure does not imply destroying usable data. Persistence maps into presentation explicitly: `Absent + bootstrap` becomes `NoContentLoading`, `Absent + failed/unavailable source` becomes `NoContentFailure`, and durable `Published(empty)` becomes `Empty`. Card-count emptiness by itself is never bootstrap authority.

### Story conceptual state

```text
visual summary / cover availability
+
rich detail availability
+
scoped issue
```

The image and metadata readiness domains are independent enough that one can succeed while the other fails.

A single giant enum that forces `CoverReady + MetadataLoading` back into whole-screen `Loading` is rejected.

---

## 23. Testing strategy

### 23.1 `:catalog:domain`

Pure unit tests:

- frozen `SourceStoryKey -> StoryId` v1 byte encoding with golden test vectors, full SHA-256 lowercase-hex output, stability across mutable metadata changes, and fail-closed collision behavior with no suffix fallback;
- `CatalogSourceKey` authority/source-story identifier validation/ceilings;
- payload cannot override host-stamped source identity/version/acquisition time;
- Popular/Latest/Top Rated eligibility/order/caps;
- deterministic tie-breaking;
- rating-scale preservation;
- missing timestamp/rating exclusion semantics;
- acquisition provenance mapping;
- import validation rejects malformed/unbounded section and Story Detail payloads;
- typed local/remote cover locator validation independent of Coil/Android.

### 23.2 `:catalog:storage`

Room tests:

- lazy fresh schema creation;
- indices/foreign-key/uniqueness integrity;
- coherent `Absent` versus `Published(generation, cards)` Discover observation;
- successful zero-card publication yields durable `Published(empty)` across DB reopen/process-style recreation and cannot collapse back to `Absent`;
- transactional generation publication;
- failed transaction preserves previous snapshot/state;
- duplicate section position/story membership is rejected;
- one bounded Discover query returns deterministic section order;
- Story Detail is keyed and does not touch unrelated rows;
- detail enrichment does not mutate materialized Discover membership/card snapshot;
- detail provenance survives independently of later source-version changes;
- bulk import does not perform per-card transaction loops;
- Story Detail scalars + authors/artists/genres + provenance publish atomically; failed detail transaction preserves the complete prior detail state;
- active Story pin survives pruning during a Discover refresh, including a deterministic pin-registration/publication race;
- orphan pruning keeps current + active pins + bounded recent data using only bounded generation delta + bounded `story_orphan_retention` work;
- query-plan/diagnostic evidence rejects global Story/detail scans or unbounded history sorting/counting on the publication/pruning critical path;
- access aging does not create a reactive write/invalidation loop;
- large unrelated dataset does not alter result cardinality/query count or foreground pruning work cardinality;
- process recreation/reopen preserves published snapshot and provenance.

API 26 and API 37 connected storage verification are required because Room is newly admitted.

### 23.3 `:catalog:runtime`

Unit/integration tests:

- no work before activation;
- empty selected source/media scope triggers one bootstrap single-flight only when an acquisition source is wired;
- release/no-source wiring does not silently substitute the development seed;
- repeated empty emissions do not create repeated acquisition for the same source/media scope;
- successful empty acquisition becomes durable `Published(empty)` and process/reopen observation performs zero automatic re-bootstrap;
- persisted `Published(content)` or `Published(empty)` avoids bootstrap on normal returning launch; switching to an `Absent` media type bootstraps only that scope;
- both Manga and Light Novel are enabled;
- manual refresh is single-flight;
- refresh failure retains prior snapshot;
- cancellation does not publish partial data;
- Story Detail acquisition uses explicit `StorySourceRef` and is keyed/single-flight;
- active Story pins are registered before route demand is exposed and removed after demand ends;
- pin add/remove and publication pruning obey the shared short `CatalogMutationGate` ordering without holding it across source/image/UI work;
- terminal active-work map entries are removed;
- quiescence cancels screen-owned work and starts no background continuation;
- oversized acquisition data fails before durable expansion.

### 23.4 `:feature:catalog`

Compose/ViewModel tests:

- returning launch reaches Discover;
- FirstRun completion transitions to Discover;
- multi-section rendering;
- Manga and Light Novel controls are both enabled;
- Manga | Light Novel selection replaces only the selected persisted observation/bootstrap scope and does not invoke Story Detail from UI;
- one vertical scroll owner behavior;
- partial section omission;
- initial geometry-shaped skeletons;
- cached refresh keeps content visible;
- stable list keys across enrichment;
- tap routes with correct `StorySourceRef` and optional asset key;
- Story Detail renders cover/summary before rich metadata when appropriate;
- image failure does not blank metadata;
- metadata failure does not erase visible cover;
- Back restores Discover state/scroll when session survives;
- process recreation restores only the small `StorySourceRef` + optional `CoverAssetKey`, re-registers the active pin before resumed pruning can run, recovers source asset policy from host-authoritative `CatalogSourceKey`, and reconstructs Story Detail from persistence rather than retained DTO/bitmap state;
- presentation never owns raw HTTP/source-host policy;
- accessibility semantics for sections/cards/detail;
- screenshot coverage on compact phone plus one wider configuration.
- Task 14 deterministic screenshots are compared against both the approved Task 14 blueprint and the reviewed V1 reference set with an explicit human PASS/FAIL ledger for navigation silhouette, artwork prominence, hierarchy, density, typography/spacing, metadata scanability, light/dark coherence, and adaptive layout; successful capture alone is not visual acceptance.
- visual-polish tests must not add Search/Chapter/Reader behavior, N+1 Story Detail reads, nested vertical scrolling, or Design-System-owned image/runtime work.

### 23.5 Build-surface/architecture tests

Required Step 2 architecture evidence:

- exactly the four new **production** Step 2 modules/approved edges are admitted;
- minimal Android-library convention exists only as reviewed build support;
- Room dependency/compiler use is confined to `:catalog:storage`;
- no generic `RoomConventionPlugin` is restored;
- image-loader implementation dependency is confined to the approved Catalog image owner;
- `:catalog:domain` remains Android/Room/Compose/image-loader free;
- `:app` depends directly only on `:feature:catalog` among Step 2 Catalog modules;
- `:catalog:model` and `:catalog:engine` remain quarantined from production edges;
- main/release manifest has no Step 2 production network permission/initializer/provider/service/receiver leak;
- old Step 1 build-surface test is evolved into the exact Step 2 fail-closed allowlist rather than deleted;
- `debug`, `benchmarkRelease`, `nonMinifiedRelease`, `release`, unit-test, and `androidTest` source/variant wiring matches the matrix in Section 6.9;
- release artifacts/source inputs contain no deterministic seed implementation/data and no plugin integration harness;
- benchmark/profile variants deterministically wire the seed through the same importer/persistence path rather than falling back accidentally to source-less release behavior;
- package dependency analysis reports **zero production SCCs** across `:catalog:domain`, `:catalog:storage`, `:catalog:runtime`, and `:feature:catalog` (and preserves the existing app-shell ratchet).

### 23.6 Image continuity/security tests

- Discover and Story use identical asset key for same cover revision;
- changed cover revision invalidates stale asset key;
- trusted local locators persist stable logical asset identity/version rather than raw Android resource integers;
- remote revision defaults do not strip query data unless a reviewed source contract supplies a separate stable artwork token;
- memory hit does not start a disk/source/network fetch;
- disk hit does not start network fetch;
- remote cover rejects cleartext/undeclared hosts and revalidates redirects;
- source policy is recovered by `CatalogSourceKey` after recreation rather than trusted from raw presentation URL;
- oversized encoded remote cover response is rejected without unbounded allocation;
- source width/height >8,192 px or source pixel surface >32,000,000 pixels is bounded/rejected before original-size allocation;
- cache ceilings are explicitly configured;
- image concurrency is ≤8 and visible work is not starved by prefetch;
- offscreen card disposal cancels/lowers unnecessary image work;
- repeated navigation does not accumulate unbounded decoded assets;
- no image loader/network work occurs before demand.

### 23.7 Plugin integration tests

Only after local gates pass:

- reviewed V1 MangaUpdates reference-plugin provenance/hash is recorded;
- the real reviewed MangaUpdates plugin code executes under `:feature:catalog` `androidTest` against controlled deterministic transport;
- `POPULAR`, `LATEST_UPDATES`, and `TOP_RATED` outputs map to the Step 2 import contract without provider-ID/title semantic guessing;
- real reference plugin detail output maps to keyed Story Detail import;
- host-stamped persisted `CatalogSourceKey`/source version/acquisition time matches the executed reference source and cannot be overridden by payload fields;
- persisted rows are read by unchanged UI/read ports;
- real-format remote cover locators display through unchanged typed locator/security/cache path using controlled transport, including redirect/host/size/media-type failure cases;
- plugin failure leaves prior local/persisted data intact;
- the harness is absent from production variants, adds no production startup dependency, and adds no main/release network permission;
- an optional live MangaUpdates smoke test, if retained, is explicitly non-blocking for deterministic acceptance and is not used for performance numbers.

---

## 24. Required capability admission record

This section completes the ten required fields from `docs/internal/v2/capability-admission-contract.md`.

### 1. Activation trigger and owner

Trigger: the Step 1 launch state selects Catalog/Discover after FirstRun has completed, or the user returns from Story Detail within the same Catalog journey.
Owner: `:feature:catalog` requests a `CatalogCapabilitySession`; `:catalog:runtime` owns activation internals.
Prohibited trigger: process/Application/Activity creation by itself.

### 2. Deactivation / quiescence rule

Discover/Story collectors are lifecycle/destination scoped. Leaving visible lifecycle cancels foreground observation/acquisition that no longer has demand. No background continuation starts. The activity/capability session may retain an idle Room handle and bounded image caches until final session destruction/process death. Active Story pins are removed when their Story destination/transition no longer owns demand.

### 3. Production dependency/build graph

New direct app edge: `:app -> :feature:catalog` only.

New Step 2 modules: `:catalog:domain`, `:catalog:storage`, `:catalog:runtime`, `:feature:catalog` with the exact graph in Section 6.

Framework ownership:

- `:catalog:domain` — pure JVM;
- `:catalog:storage` — Android + Room only as required for storage;
- `:catalog:runtime` — demand/runtime ownership, no Compose;
- `:feature:catalog` — Android + Compose + approved capability-private image implementation;
- `:app` — no direct Room/image/network/Catalog-runtime/storage implementation ownership.

No Step 2 production project edge from `:app`/`:feature:catalog` to `:catalog:model`, `:catalog:engine`, production plugin runtime, Reader, Downloads, Chapters, Library, or WorkManager. The Step 1 build-surface verifier evolves to an exact fail-closed allowlist. A generic Room convention is not restored.

Because Step 2 is not yet a ship-ready production remote acquisition milestone, the main/release manifest does not gain `INTERNET` solely for the isolated plugin gate.

### 4. Foreground working-set / cardinality

Discover reads the current bounded semantic snapshot for the selected media type, initially ≤19 memberships; both enabled media types may occupy ≤38 current memberships in persistence. Story Detail reads one source Story plus fixed bounded child collections. Foreground work may not scale with total historical Story count. Image work is viewport/near-viewport bounded. Acquisition imports and decoded artwork are also bounded by Sections 9 and 13.

### 5. Observer keys, invalidation scope, and lifetime

Discover: one coherent observer of the current published materialized Discover-card read model keyed by selected media type while Discover is active; Story Detail table changes do not invalidate it.
Story Detail: one observer keyed by explicit `StorySourceRef` while the Story screen is active.
Image: per-asset request scoped to composition/near-viewport or selected Story transition.
No application-lifetime domain observer.

### 6. CPU / I/O / network execution owner

Room I/O/import: `:catalog:runtime` + `:catalog:storage`, off Main.
Discover semantic projection/validation: bounded domain/runtime operation before commit.
Source acquisition: one `CatalogAcquisitionExecutor` owner per logical key, including media type for Discover work.
Cover fetch/decode: capability-private image implementation with explicit cache/concurrency plus typed source-scoped HTTPS/redirect/encoded/decoded-resource policy when remote integration is active.
Presentation owns request lifetime, not raw network trust authority.
Visible work has priority over optional prefetch.

### 7. Durable / background work owner

Not applicable in Step 2 because no durable/background acquisition is admitted. Manual/bootstrap work is foreground-only. This is evidence-backed by the explicit non-goal and architecture gates rejecting WorkManager/schedulers/manifest startup surfaces.

### 8. Retention / aging / eviction

Current materialized Discover-card generation only; obsolete generations deleted.
Current Discover-referenced Stories + active Story pins + at most 64 recent orphan details represented by the bounded orphan-retention index.
Pruning is delta-driven from previous/current bounded generations and may not globally scan/sort historical Story Detail rows.
Active pin transitions and pruning publication are serialized by the short Catalog mutation gate.
Access aging is bounded/coalesced and may not create observer write loops.
Active-only single-flight maps.
Decoded cover memory ≤32 MiB; disk cover cache ≤128 MiB.
Aged-state tests include large unrelated persistence and repeated navigation/refresh.

### 9. Failure / retry / terminal-state ownership

Runtime owns acquisition failures. Explicit user retry plus exactly-one active bootstrap per `(CatalogSourceKey, mediaType)` are the only retry authorities when an acquisition source exists. Cached content survives refresh failure. Story/image failures are scoped. Oversized/malformed acquisition/image data fails boundedly. Cancellation propagates. No terminal work entry remains in active maps and no automatic retry storm exists.

### 10. Benchmark / scaling delta

Compare cold startup against the frozen Step 1 same-device baseline only after regenerating the Step 2 baseline/startup profile from the final deterministic journey. Record the Step 2 profile artifact/hash, Catalog activation, Discover first snapshot/cover/content, fixed query counts, bounded pruning-work diagnostics, scroll/frame behavior, Story open/back latency, memory/disk cache continuity, aged-storage behavior, repeated activation/navigation, input-bound failures, and image cache stability. >10% same-device TTID median regression is a review trigger, and the hard gates in Section 20.6 remain acceptance criteria independent of TTID.

---

## 25. Explicit non-goals

Step 2 does not implement:

- Chapters;
- Reader;
- Downloads;
- Library;
- reading progress;
- Search implementation (Search remains approved product scope for a later capability delivery);
- Mapping/review URL import;
- multi-plugin aggregation;
- cross-source canonical reconciliation/fusion;
- production plugin runtime/provisioning/package lifecycle;
- a ship-ready production remote Catalog acquisition source;
- production `INTERNET` admission solely for the integration probe;
- background refresh/work scheduling;
- notifications;
- global DI framework;
- Navigation 3 unless separately justified;
- V1 Room migration/upgrade path;
- final onboarding redesign;
- final app-wide design-system migration beyond the minimal Catalog-consumed Step 2 slice (Task 14 visual polish remains feature-local on top of that slice);
- image retention guarantees that require unbounded memory;
- preload of all Discover covers;
- a Story Detail chapter list hidden behind the metadata screen;
- a generic shared image module before another admitted capability proves shared ownership is needed;
- restoration of the V1 Room convention/plugin runtime merely for convenience.

If implementation discovers that one of these is required merely to make Step 2 function, stop and review the boundary rather than silently expanding scope.

---

## 26. Acceptance criteria

Step 2 design intent is satisfied only when all of the following are true:

1. A returning launch reaches Discover; fresh install preserves Step 1 FirstRun then reaches Discover.
2. First application-owned frame remains independent of Catalog database/image/source initialization.
3. Step 2 explicitly enables both Manga and Light Novel, and authoritative product docs no longer retain a conflicting disabled-Light-Novel rule.
4. Step 2 is recorded as a production-shaped internal/product vertical slice, not falsely declared a ship-ready production remote-catalog release.
5. Discover is backed by real V2 Room persistence, not direct JSON/mock UI data.
6. Development/benchmark/test seed enters the same importer/write boundary as a future production acquisition adapter.
7. Variant wiring is explicit: `debug` may use development seed, `benchmarkRelease`/`nonMinifiedRelease` use deterministic benchmark seed, `release/main` contains no seed/remote source, and `androidTest` owns the isolated plugin harness.
8. Release artifacts contain neither deterministic seed data/implementation nor plugin integration harness code.
9. The Step 1 build-surface ratchet is evolved, not deleted: exactly five new Step 2 **production** modules are admitted (`:core:designsystem` plus the four Catalog modules); `:app` has exactly one direct product/capability edge (`:feature:catalog`) plus one presentation-infrastructure edge (`:core:designsystem`), and architecture/import checks remain fail-closed.
10. A minimal reviewed Android-library build capability is re-admitted; Room is confined to `:catalog:storage`; a generic `RoomConventionPlugin` is not restored in Step 2.
11. `:app` has exactly one reviewed Step 2 product edge (`:feature:catalog`) and one reviewed Design System infrastructure edge; app production code may import only `CatalogEntryPoint` from Catalog and `HikariTheme` from Design System, with no direct storage/runtime/image/network/control/state ownership.
12. `:catalog:model` and `:catalog:engine` remain quarantine/reference with no Step 2 runtime edge.
13. Production package dependency verification reports zero new package SCCs inside all Step 2 production modules, including the minimal `:core:designsystem`; the design-system module has zero production project dependencies, no Catalog/storage/network/image/plugin/reactive-work ownership, no scroll owner/full-screen sizing authority, no runtime font/resource lookup, and no unbounded/transient collection-building validation path.
14. Root Unknown/FirstRun/Catalog surfaces share one `HikariTheme`; Task 14 performs the exact R2.8 palette/typography migration while preserving White/Black root backgrounds, the 4/8/12/16/20/24/32 spacing scale, the 8/12/20/28/36 shape family, zero-runtime-font-I/O, and all Design-System ownership boundaries. Discover renders multiple semantic sections and both media destinations as bounded persisted-data scopes; final Manga/Light Novel navigation is feature-local and the zero-caller segmented API is retired atomically.
15. After Task 13 foundation acceptance, Task 14 produces Discover/Story plus Loading/Error/Empty/Refresh presentation aligned with the approved Dantotsu-inspired direction and human-reviewed as at least comparable to the reviewed V1 quality for navigation silhouette, artwork prominence, hierarchy, content density, typography/color/spacing coherence, metadata scanability, state coherence, light/dark treatment, portrait-cover geometry, and compact/wider-screen reflow. Automated correctness gates must pass before user visual acceptance; the user owns the final appearance PASS. Story media type and latest-update information come only from already-persisted trusted summary fields; pixel parity, fake blueprint actions, and V1/Dantotsu architecture transplantation are not required.
16. The default first snapshot is bounded to the approved section caps; no per-card DB query or detail request is needed to render it.
17. Storage/read state distinguishes `Absent` from `Published(generation, cards)`; `Published(empty)` is a valid durable result.
18. A successful zero-card publication survives DB reopen/process recreation as `Published(empty)` and does not trigger automatic bootstrap again.
19. Only `Absent` may auto-bootstrap, and only when that build wiring has an admitted acquisition source; `Absent + no source` remains explicit.
20. `CatalogSourceKey` is host-authoritative and distinct from old canonical-engine source concepts.
21. `StoryId` uses the frozen `source-story:v1:<full SHA-256>` derivation from exact length-prefixed UTF-8 `CatalogSourceKey + sourceStoryId` bytes and does not change with mutable Story metadata.
22. Identity collisions fail closed; there is no numeric suffix/random/insertion-order collision repair.
23. Story selection routes with explicit source identity (`StorySourceRef`/equivalent), not implicit global source inference.
24. Story selection opens metadata-only Story Detail; its UI consumes the trusted user-safe summary/rich-detail fields frozen in §3.4 (including content type and latest update when present), while raw source/provenance fields remain hidden; Chapters/Reader are absent.
25. Story Detail read cost is keyed, has a fixed query-count upper bound, and uses bounded child/result cardinality independent of total Catalog size.
26. Acquisition identifiers/text/child collections/locators are validated against explicit bounds before persistence/UI expansion.
27. `CatalogSourceKey`, source version, and acquisition timestamp are host-stamped/verified provenance; untrusted payload data cannot impersonate another source or asset policy.
28. Persisted Discover/Story Detail provenance survives later source-version changes accurately.
29. Discover persistence uses one coherent materialized published-generation state observation; UI cannot observe half-replaced/mismatched generation/card snapshots.
30. Storage enforces duplicate `(sectionKind, itemPosition)` and `(sectionKind, storyId)` invariants inside one generation, using fixed section semantics + `item_position` without a redundant mutable `section_position` authority.
31. Discover publication accepts a zero-card generation atomically and leaves the prior state intact on failed publication.
32. Discover publication/pruning work scales with bounded previous/current snapshots, bounded removed delta, and bounded orphan overflow—not total historical Catalog rows.
33. The refresh critical path performs no global Story/detail scan or history-wide unbounded sort/count to maintain the 64-item orphan-retention policy.
34. Detail enrichment publishes summary/detail/children/provenance atomically and does not mutate the published Discover-card snapshot as a side effect.
35. Failed Story Detail publication preserves the complete prior usable detail state; observers do not see mixed old/new child collections.
36. Active Story pin registration/removal and Discover pruning obey one short mutation-gate ordering so a Story opened during refresh cannot be pruned in the race window.
37. The mutation gate is never held across source/network I/O, image work, long UI work, or user interaction.
38. Access-aging writes do not create reactive invalidation loops.
39. Real cover images render in Discover and Story Detail in local deterministic fixtures and the final plugin integration probe.
40. Cover acquisition/rendering can progress independently of rich Story Detail readiness.
41. Discover → Story Detail → Back uses stable asset identity and bounded memory/disk cache continuity without bitmap navigation copies.
42. Process recreation reconstructs Story Detail from saved narrow route identity + persistence, re-registers pins safely, and recovers source asset policy from `CatalogSourceKey`; it does not require retained DTO/bitmap state.
43. Card/image geometry remains stable while cover bytes arrive; Discover/Story loading reserves final geometry, including the summary-null Story hero, uses static non-shimmer shared skeleton presentation, and does not replace retained content during refresh.
44. Presentation code does not own raw plugin URLs, source-host trust, redirect validation, or a generic HTTP client.
45. Trusted local cover locators persist stable logical asset IDs/versions, not raw Android resource integers.
46. Remote cover locators are typed/source-scoped; HTTPS host/redirect/timeout/media-type/encoded-size validation is enforced in remote integration.
47. Remote cover revision does not generically discard URI query data; a source-specific stable artwork token is used only when its reviewed contract proves equivalence.
48. Image memory/disk/concurrency have explicit ceilings; encoded response is ≤8 MiB; source width/height is ≤8,192 px; source pixel surface is ≤32,000,000 pixels; original-resolution decode for bounded UI display is rejected.
49. Discover pull-to-refresh delegates source acquisition to one foreground owner, is disabled for initial `Absent` bootstrap/no-content failure, suppresses duplicate dispatch while refreshing, retains durable cached/empty content on failure, and does not coexist with duplicate feature-local refresh progress/manual-refresh chrome; failure Retry remains a distinct recovery intent, is never a decorative no-op for retryable activation/storage failures, and cannot create a second acquisition owner.
50. Root `HikariTheme` introduces no process-start Catalog, Room, image, plugin, worker, scheduler, network, runtime-font/resource-I/O, effect/collector/coroutine, service-locator, or mutable process-registry work; no new startup initializer/provider is introduced.
51. The main/release manifest does not gain production `INTERNET` solely for the isolated Step 2 plugin gate.
52. Persistent/runtime/image state is bounded under repeated refresh, navigation, and aged datasets.
53. Deterministic returning Discover performs no acquisition/network work for both `Published(content)` and `Published(empty)`.
54. Memory-hit cover path performs no disk/network fetch; disk-hit cover path performs no network fetch.
55. Database/source/network/large-decode/importer work does not intentionally execute on Main.
56. A dedicated performance-validation task measures the final **Task 14 visually accepted, root-themed** post-Design-System/pull-refresh multi-section implementation, including startup delta and frame/jank behavior; it may simplify evidence-dependent presentation/query/image work but is not a cleanup bucket for structural debt that Task 13 could have rejected statically.
57. Before final startup comparison, the Step 2 baseline/startup profile is regenerated from the final deterministic returning Discover/Story journey and its artifact/hash is recorded.
58. Startup and meaningful-content deltas are recorded against the accepted Step 1 baseline under equivalent profile/benchmark methodology.
59. A same-device TTID median regression >10% is optimized or explicitly reviewed rather than silently accepted.
60. Frame/jank behavior has an explicit benchmark review threshold and cannot be waived solely because TTID remains fast.
61. Repeated navigation/activation does not create monotonic growth in observers, active-work maps, Story pins, orphan-retention ownership, or decoded asset ownership beyond configured bounds.
62. The final local path passes architecture, variant, Design System slice, package-SCC, storage, UI, image, lifecycle, input-bound, concurrency-race, screenshot, and performance gates before plugin integration begins.
63. The deterministic real-plugin gate uses the reviewed MangaUpdates reference behavior by default and proves all three Step 2 semantic sections without provider-title/ID guessing.
64. The real plugin executes in `:feature:catalog` `androidTest`/integration-only wiring against controlled deterministic transport and feeds the unchanged import/persistence/read/UI path.
65. Reference-plugin/harness provenance/hash is recorded; the harness adds no production app startup/runtime dependency on the full plugin system.
66. Live external plugin/network testing, if retained, is optional smoke evidence only and is not an acceptance/performance dependency.
67. Local/plugin acquisition failures or bounded-input rejections do not erase prior usable published content.
68. No placeholder, unresolved ownership rule, or deferred-decision marker remains for a load-bearing Step 2 design decision; Task 14 visual composition is explicitly frozen by the approved blueprint/R2.8 contract rather than ambiguously split with Task 13.

---

## 27. Recommended implementation decomposition after design approval

This section is sequencing guidance only, not an implementation plan.

A later implementation plan should decompose Step 2 along these ownership boundaries:

```text
A. build-surface + exact module/variant/package-SCC admission
B. pure domain CatalogSourceKey / frozen StoryId v1 / host-provenance / input-bound contracts
C. Room schema with explicit Absent vs Published(empty), coherent Discover state, atomic Story Detail, bounded orphan-retention index
D. importer + transactional Discover publication + delta pruning + mutation-gate/pin race tests
E. local debug/benchmark typed seed through production-shaped write boundary; prove release contains none
F. Discover multi-section presentation + both media selections + empty/bootstrap/content state contract
G. Story Detail explicit source route + keyed observation/acquisition + process-recreation/pin restoration
H. visual asset fast-path + typed locator/source policy + stable logical local assets + frozen encoded/decoded bounds
I. lifecycle/failure/manual-refresh/single-flight/quiescence hardening
J. minimal `:core:designsystem` admission + root `HikariTheme` + shared primitives + Discover pull-to-refresh + pre-benchmark V1-debt regression matrix
K. feature-local Discover/Story visual restoration + UX polish against the reviewed V1 quality floor, without broadening Design System/runtime ownership
L. local connected correctness + deterministic final screenshot evidence for the accepted visual composition
M. performance/scaling validation + aged-history bounded-work proof + evidence-driven UI/query/image simplification
N. regenerate Step 2 baseline/startup profile and run final deterministic benchmark comparison
O. isolated MangaUpdates real-plugin androidTest gate with controlled transport + provenance evidence
P. full Step 2 acceptance/freeze
```

The implementation plan must split these further into small TDD tasks and must stop after each approved task according to repository `AGENTS.md` execution rules.

The build-surface/variant/architecture task is first. DAO/UI work must not begin by temporarily disabling the Step 1 ratchets and promising to restore them later. Identity derivation, publication-state semantics, pruning complexity, mutation-gate ordering, image ceilings, and plugin harness ownership are already frozen by this design; the plan converts them into executable tasks rather than reopening them casually.

---

## 28. Design self-review

### 28.1 Scope leak review

- Chapters/Reader/Search/Library/Downloads remain outside the boundary.
- Plugin runtime is not smuggled into production under the final integration test.
- The plugin harness is explicitly `androidTest`/integration-only.
- Step 2 explicitly acknowledges that it is not yet a ship-ready remote-catalog release.
- No broad V1 design-system/DI/navigation migration is admitted; only the minimal root theme plus Step 2-consumed stateless primitives are added before benchmark.
- Local seed is explicitly non-release data, not a permanent default catalog.

Additional R2.8 scope check:

- `:core:designsystem` is presentation-only with zero production project dependencies; `:app` may import only root `HikariTheme`, while `:feature:catalog` consumes the reviewed primitives;
- `HikariBootTheme` is removed and root theming moves to `HikariStartupApp`, but Ready/first-frame gating and Unknown/FirstRun state/action ownership remain unchanged;
- V1 artwork/network/backdrop/Roborazzi/Robolectric surfaces are not re-admitted;
- only Discover gains pull-to-refresh; Story Detail keeps explicit Retry-only acquisition semantics;
- the image fast path and `CoverAssetKey` ownership remain entirely in `:feature:catalog.assets`.

Result: **PASS**.

### 28.2 Product-authority review

- Earlier `LIGHT_NOVEL visible but disabled` delivery scope is explicitly superseded.
- Manga and Light Novel are both enabled Step 2 read/presentation keys.
- Search remains approved future product scope rather than being silently deleted.

Result: **PASS, with required companion update to authoritative product-design docs during Step 2 admission**.

### 28.3 Build-surface/variant/structural review

- Step 1 zero-capability ratchets evolve into exact Step 2 allowlists rather than being removed.
- `:app` gains exactly one Catalog product edge.
- Room is confined to `:catalog:storage`; a generic Room convention is not restored.
- Image implementation is capability-private; presentation does not own network/source trust authority.
- `:catalog:domain` remains pure JVM.
- Quarantined Catalog engine/model remain outside the production graph.
- `debug`, benchmark/profile variants, release, and androidTest have explicit acquisition/harness wiring.
- release cannot accidentally inherit deterministic seed or integration support.
- package-level SCC checks close the same-module cycle hole that a clean module DAG alone would miss.

Result: **PASS**.

### 28.4 Identity/provenance review

- Stable Step 2 `StoryId` no longer depends on the quarantined canonical engine.
- `CatalogSourceKey` authority is host-owned and named separately from older fusion semantics.
- Exact UTF-8 length-prefixed SHA-256 v1 derivation is frozen, full-width, and deterministic.
- Collision handling fails closed rather than introducing DB/order-dependent suffixes.
- Story Detail route carries explicit source identity rather than relying on global source inference.
- Discover/detail provenance is host-stamped from the executed source/session and cannot be impersonated by payload data.

Result: **PASS**.

### 28.5 Publication-state/bootstrap review

- `Absent` and `Published(empty)` are distinct durable states.
- A successful empty acquisition remains published after reopen and cannot cause bootstrap loops.
- Only `Absent` has automatic bootstrap authority, and only when an admitted acquisition source exists.
- A release build with no source exposes that fact rather than silently injecting seed behavior.

Result: **PASS**.

### 28.6 Ownership/lifecycle/concurrency review

- App shell owns only destination handoff, not Catalog runtime internals.
- Catalog runtime is demand activated.
- Bootstrap/manual refresh/detail acquisition have explicit single owners.
- Quiescence stops observation/work and launches no background substitute.
- Active Story pins are lifetime-bounded.
- Pin registration/removal and pruning publication share one short mutation ordering so the open-vs-prune race is closed.
- Slow source/image/UI work never holds that mutation gate.
- Process recreation reconstructs demand from narrow route state and persistence rather than retained bitmaps/DTOs.

Result: **PASS**.

### 28.7 Persistence/reactive/retention review

- Discover uses one coherent materialized published-generation **state** rather than independently settling streams.
- An empty successful generation remains representable through a left/publication-state read.
- Discover membership has database-level duplicate/order invariants without redundant `section_position` authority.
- Story Detail is keyed and writes scalars/children/provenance atomically.
- Detail enrichment cannot rewrite the published Discover-card snapshot.
- Orphan retention has an explicit bounded index/set and publication pruning is delta-driven rather than a history-wide sweep.
- Access aging does not require write-on-every-observation behavior.

Result: **PASS**.

### 28.8 Image/security/resource review

- Stable asset identity preserves visual continuity within bounded caches.
- Local and remote cover locators are distinct typed authorities.
- Trusted local persistence uses logical asset identity, not raw resource integers.
- Remote host policy is source-scoped and recoverable after recreation.
- Remote revision semantics do not generically strip query data.
- Encoded response, source dimensions/pixel surface, cache bytes, viewport prefetch, and concurrency are concretely bounded.
- Presentation does not become an HTTP/security authority.

Result: **PASS**.

### 28.9 Performance/scaling review

- Discover work is bounded by the published snapshot, not historical database size.
- Story Detail is keyed and input-bounded.
- Import is batch/transactional, not per-card.
- Retention work is bounded by current/removed deltas and bounded orphan overflow, not just bounded final cardinality.
- Persistent generations and runtime maps are bounded.
- Aged-state and multi-section macrobenchmarks are mandatory before plugin integration.
- Final startup comparison requires regenerated Step 2 baseline/startup profiles.
- Performance has hard ownership/query/cache/aging/frame/concurrency gates in addition to TTID.

Result: **PASS**.

### 28.10 Plugin-gate review

- MangaUpdates is a stronger Step 2 default than MyAnimeList because the reviewed reference behavior exposes the three required semantic section kinds rather than forcing adapter string inference.
- The real plugin executes under deterministic controlled transport for acceptance.
- Live external network remains optional smoke evidence.
- Plugin executor/support provenance is recorded and remains outside production variants.

Result: **PASS**.

### 28.11 V1 regression/debt review

The design explicitly avoids the known V1 failure shapes: broad canonical reads, canonical settlement on Discover critical path, multi-stream readiness ambiguity, empty/bootstrap feedback loops, wide models, N+1 detail work, duplicate execution ownership, history-wide foreground maintenance, package cycles, unbounded cache/history, hidden retained semantic work, and framework/runtime re-entry through convenience build logic. Task 13 must record the later Big Update **33/33 root-cause + 7 risk-gate** matrix, while preserving explicit baseline `L2`/`RISK-PROVIDER` coverage; `OWNED` rows require a Task 13 gate, `PROTECTED` rows name the existing Step 2 owner, and `N/A` rows explain why no new path exists. Known structural debt detectable by this matrix is fixed in Task 13 before Task 14 visual restoration; visual-composition debt is fixed in Task 14 before Task 15 correctness/screenshot evidence and Task 16 benchmark/profile work.

Result: **PASS**.

### 28.12 Contradiction review

Potential contradiction: a successful Discover acquisition may legitimately return zero cards while bootstrap historically used emptiness as a trigger. Resolution: `Absent` and `Published(empty)` are now different durable states; only `Absent` auto-bootstraps.

Potential contradiction: retention is capped at 64 orphan details but a naïve implementation could still scan all historical details on every refresh. Resolution: the design now requires a bounded orphan-retention index and delta-driven pruning whose foreground work depends only on bounded current/removed sets and bounded overflow.

Potential contradiction: active pins protect Stories, but taking an unsynchronized pin snapshot can race with navigation. Resolution: pin transitions and publication pruning share a short mutation gate; slow work remains outside it.

Potential contradiction: Story Detail is keyed but its child tables could still be observed in half-updated combinations. Resolution: complete bounded Story Detail enrichment is one atomic Room transaction and failed enrichment preserves the prior coherent state.

Potential contradiction: deterministic Story identity was called “frozen later” while also being load-bearing. Resolution: R2.5 preserves the exact domain-separated, length-prefixed full SHA-256 v1 algorithm and fail-closed collision policy frozen by R2.4.

Potential contradiction: untrusted plugin data carries source metadata while image trust is source-scoped. Resolution: Catalog source identity/version/time and asset-policy authority are host/session-stamped; payloads cannot self-select authority.

Potential contradiction: deterministic benchmark seed is required while release must contain no seed and the app already has custom benchmark build types. Resolution: R2.5 preserves the explicit variant/source-set matrix frozen by R2.4 and requires compatible Android-library variants plus release-artifact leakage tests.

Potential contradiction: exact module DAG checks could still allow same-module structural spaghetti. Resolution: package-level SCC ratchets are required for every Step 2 production module.

Potential contradiction: Step 2 needs a real plugin proof but production plugin runtime remains forbidden. Resolution: the real MangaUpdates reference code runs from `:feature:catalog` androidTest/integration-only wiring against controlled transport; production graph remains unchanged.

Potential contradiction: deterministic acceptance versus unstable external services. Resolution: controlled transport owns acceptance; live network is optional smoke only.

Potential contradiction: Step 1 baseline uses profile-guided startup while Step 2 changes the product path. Resolution: the final Step 2 baseline/startup profile is regenerated from the final deterministic journey before recording comparison numbers; pre-demand gates prevent profile generation from hiding early work.

Potential contradiction: installing `HikariTheme` at the app root means presentation code is now composed before the Catalog Ready gate. Resolution: the root Design System is deliberately stateless/effect-free and may import no runtime/data owner; Unknown/FirstRun remain the only pre-Ready surfaces, Catalog composition timing is unchanged, root background matches the existing window background, and Task 16 measures the real startup delta rather than assuming theming is free.

Potential contradiction: stable local asset continuity versus Android resource IDs not being a durable cross-build identity. Resolution: persistence stores logical local asset IDs/versions and non-release fixture wiring resolves them to resources.

Potential contradiction: remote URL revision stability versus signed/volatile URLs. Resolution: the generic safe default hashes the complete validated URI; only a reviewed source-specific stable artwork token may intentionally decouple revision from a volatile URL.

Result: **PASS**.

---

## 29. Approval gate

### R2.8 Task 14 adversarial closure

R2.8 preserves the approved Dantotsu-inspired IA/visual direction but re-audits it against the **actual Task 13 repository state** and the actual Story projection before implementation. The following conflicts are resolved explicitly:

- **Blueprint concept UI versus available data:** `Read`, `Add to Library`, `Chapters`, `See all`, `Year`, `R15+`, fake Search/bookmark and similar concept controls remain forbidden. The Story media eyebrow is now deliberately **allowed** because `StorySummaryProjection.contentType` already exists and is trusted; `latestUpdateEpochMs` is likewise existing user-safe summary data and is mapped to a stable update label. No lower-layer model is widened for either field.
- **Story field loss versus presentation completeness:** pre-Task-14 `toSummaryUi()` drops `contentType` and `latestUpdateEpochMs`. Task 14 repairs only this feature presentation mapping. `sourceVersion`, raw source identity, `detailProvenance`, and detail acquisition time remain internal authority and must never be turned into friendly labels by inference.
- **Task 13 segmented API versus Task 14 navigation:** Manga/Light Novel are Catalog media destinations. Retirement is an atomic Task 13 contract migration: remove the two production segmented APIs, their two segmented Design System contract tests/imports, the slice-script expected sources/symbols, and the stale segmented `CONTROL` validation block. Every unrelated Task 13 contract remains and must compile/run.
- **Discover spacing contract versus existing root spacing:** the Task 13 `LazyColumn(verticalArrangement = spacedBy(20.dp))` cannot satisfy the approved 12.dp title-to-content and 32.dp section rhythm. Task 14 removes that global spacing and owns explicit gaps; leaving `spacedBy(20.dp)` is visual-contract failure.
- **New geometry versus existing Design System tokens:** Task 14 changes composition geometry, not the Design System scale. Any local constants that merely restate `space4/8/12/16/20/24/32` or 12/20/28/36.dp Material shape roles are duplicate sources of truth and are rejected; only dimensions with no shared role remain in `DiscoverVisualMetrics`/`StoryVisualMetrics`.
- **Blueprint token panel versus R2.8 written token authority:** the concept image contains illustrative palette/type measurements and is not copied literally. Task 14 is allowed to change `HikariPalette.kt` and `HikariTypography.kt` **only** to the exact §2.6 R2.8 values. `HikariSpacing.kt`, Material shape radii, root White/Black backgrounds, platform Serif/Sans ownership, and `HikariTheme` architecture remain locked; drifting any of those to imitate the image is a regression stop.
- **Edge-to-edge versus safe geometry:** Discover/Story top content consumes status-bar inset. Floating-nav navigation-bar inset is consumed by an outer wrapper, not inside the fixed 64.dp pill, preventing its measured geometry from growing or double-padding.
- **Floating navigation versus final-content reachability:** the final Top Rated row must be scrollable fully above the nav. A connected test compares row/nav root bounds; merely finding both nodes is insufficient.
- **Media switching versus navigation continuity:** switching Manga/Light Novel resets the newly selected Discover feed to item 0 only from the explicit media-nav user event. Discover -> Story -> Back preserves the surviving `LazyListState`; no selected-media `LaunchedEffect` reset is allowed.
- **Story summary-null transition versus layout stability:** before a summary projection arrives, Story Detail reserves the final portrait hero/identity geometry with static skeletons and no fake visible title/type/status. Route cover identity may still use the existing continuity path.
- **Feature geometry ownership versus package coupling:** Discover-specific measurements live in `DiscoverVisualMetrics`; Story-specific measurements live in `StoryVisualMetrics`. Story code must not import a metrics object from the `discover` package merely to share constants.
- **Human review versus screenshot freeze:** Task 14 owns the first visual-composition review and may stop only at `READY FOR USER VISUAL ACCEPTANCE` until the user explicitly passes/fails it. Task 15 owns the reproducible compact/wider/API26/API37 screenshot correctness harness/freeze; Codex cannot self-author a human PASS.
- **Image geometry versus content semantics:** artwork remains feature-owned and uses the existing locator/key/cache pipeline. Task 14 changes display geometry only; it does not move image loading into Design System or add transport/cache ownership.
- **Horizontal rails versus one-scroll-owner rule:** Popular and Latest may own bounded horizontal `LazyRow` presentation while Discover retains exactly one vertical scroll owner.
- **Reference tooling drift:** historical `tools/ui-target` surfaces and V1 `Discover/Home/Library` IA are evidence only. Actual Task 14 acceptance is against the R2.8 spec, R1 written blueprint, approved PNG, real connected surface, and retained V1 quality floor.
- **Icon/dependency creep:** icon artwork is not acceptance-critical. Already available vectors/assets may be used, but no dependency is added solely to reproduce decorative concept icons.
- **Responsive ambiguity:** 360.dp compact and >=600.dp wide behavior are explicitly dimensioned. Discover keeps bounded artwork/card dimensions while wider width increases visible density; Story reflows around the 144x216.dp wide cover instead of proportionally scaling every token.

No Task 14 visual decision is allowed to reopen persistence, acquisition, source trust, routing identity, Room schema/queries, cache ownership, lifecycle ownership, or the project graph.

This **R2.8** design is **approved as the corrected Task 14 authority** on top of already accepted Tasks 0-13. It authorizes only Task 14 as the next Step 2 implementation boundary; Task 15+ remain blocked until Task 14 is separately accepted.

For the current repository state:

- Tasks 0-13 remain accepted and are not replayed merely because the Task 14 plan/spec was corrected;
- Task 14 is the **only** authorized execution boundary;
- Task 14 begins with direct visual-reference + Story projection-field intake before production edits;
- Task 14 must preserve all lower-layer Story/data/runtime contracts while completing `contentType`/`latestUpdateEpochMs` presentation;
- the Design-System segmented migration primitive must be retired with its exact androidTest/script contracts so Task 13 remains green;
- focused compile/unit/slice checks precede user-owned connected + architecture/Detekt verification;
- the implementing agent may mark visual evidence only `READY FOR USER VISUAL ACCEPTANCE`; explicit user acceptance is required before Task 14 closes;
- Task 15 owns final cross-device correctness/screenshot freeze, Task 16 owns performance/profile evidence, and Task 17 owns plugin integration;
- repository `AGENTS.md` stop-after-task behavior remains authoritative.

Task 14 production work is permitted only through the canonical R2.8 implementation-plan boundary; this design file by itself does not authorize skipping its RED tests, regression gates, user visual acceptance, or stop-before-Task-15 rule.
