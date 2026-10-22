# Hikari UI Foundation — Rebuild Architecture & Migration Specification

> **Version:** 4.2 — Final handoff contradiction review, 2026-10-22  
> **Status:** Design baseline for implementation planning; **not yet implemented or accepted as an ADR**  
> **Baseline reviewed:** `Hikari-dev(20261021-163525).zip` (Flutter `3.47.5` per `.fvmrc`), original proposal, v2 and v3 specs. Source audit is static; no live Git/CI status is inferred.  
> **Project scope:** Flutter UI presentation across Android and Windows, including app chrome, common surfaces, layouts, components, feature composition, accessibility and migration.  
> **Primary decision maker:** Hikari product owner. This design follows the owner's deliberate rebuild intent. It can supersede conflicting *historical UI decisions* **after** its target rules are accepted and recorded in a superseding ADR.
> **Normative interpretation:** §§0–20 define the target contract; illustrative pseudocode is not a claim of compiled implementation. Current-runtime behavior and cited external references are distinguished below.

---

## 0. Executive decision — a deliberate rebuild, not a minimal refactor

**Hikari will rebuild its presentation foundation as a coherent, maintained system.** The goal is not to reduce diff size, preserve old widget APIs, or keep today's UI architecture intact. The desired end state is one discoverable design system, one canonical implementation for each shared interaction, strict ownership and dependency rules, consistent behavior across features, and predictable responsive layouts.

### 0.1 Non-negotiable principles

1. **Standards over historical exceptions.** Current UI implementations and UI-focused ADRs are evidence, not permanent constraints. Replace or supersede them when they conflict with the new design. Never silently ignore an ADR: record the new decision, link the superseded decision, update canonical docs and tests.
2. **Shared interaction contracts first.** Dialog, bottom sheet, navigation drawer, adaptive presentation, page shell, headers, search, card families, list/grid/shelf layouts and common feedback are foundation-level concerns and are designed *before* migrating screens. Do not wait for duplicated code to spread across features.
3. **No feature-owned reinvention of common chrome.** A feature may provide custom **content** and workflow, but must not create independent modal containers, size policy, barrier/focus semantics, navigation drawer chrome, or arbitrary responsive cutoffs.
4. **Separation by reason to change, not mechanical line counts.** A feature can be decomposed into view, state, presentation model, view components and route coordinator without multiplying tiny forwarding files. The resulting system must be simpler to *understand*, not merely shorter per file.
5. **One owner per concern.** Theme owns semantic visuals; surface owns presentation mechanics; feature ViewModel/application workflow owns data and commands; route coordinator owns feature navigation and permission to apply results; visual content owns composition only.
6. **No permanent legacy compatibility layer.** Transitional adapters are allowed inside an explicitly bounded migration phase, with removal checklist and guardrails. Once migration is complete, delete obsolete components, unused exports, duplicate names and old import paths.
7. **Improve architecture without changing domain truth.** Existing source identity, cache semantics, reading progress, native plugin trust and system approval must remain correct. They can be **rewired** through cleaner UI boundaries, not casually rewritten or bypassed.
8. **Build to last.** Prefer explicit, typed, documented contracts; testable variants; consistency; accessibility; performance; and clear file ownership over shortcuts that defer complexity to future changes.

### 0.2 What the rebuild IS allowed to replace

- The current `core/ui/components` / `core/ui/patterns` organization and public APIs.
- Existing route-specific dialogs, sheets, drawer implementations, sizing helpers and bespoke card variants.
- UI-only assumptions in ADR-013, ADR-014, ADR-015, ADR-017 and the current design-system/presentation documentation, if a new accepted decision supersedes them.
- Historical `MediaPoster` / `ChapterRow` APIs where they leak feature/domain concerns.
- Current page compositions and UI state wiring, provided application/domain contracts and user outcomes remain sound.
- Design-system values and UI styling if intentionally selected as part of the rebuild; current dark/Catppuccin/OLED identity remains the initial product baseline, **not** a justification for scattered hardcoded colors.

### 0.3 What this rebuild does NOT authorize by itself

- Changing Drift schemas, cache correctness, library/progress persistence or stable provider/source identities.
- Removing authorization, user consent, app lifecycle/foreground checks or system-level APK installation approval.
- Replacing manga/novel plugin execution, reader prefetch, or player engines without a separate technical decision and tests.
- Installing a state-management/UI package simply to make the folder layout look fashionable. Package additions must have documented value, cost and migration impact.

**Priority order for the new target architecture:** this approved rebuild specification + its superseding ADR → updated canonical UI/design documentation → retained non-UI/domain/security ADRs → legacy UI implementation/ADR as migration evidence.

---

## 1. Existing-state audit (facts from supplied repository snapshot)

| Evidence | Present state | Target implication |
|---|---|---|
| `lib/app/theme/hikari_theme.dart`, `hikari_spacing.dart`, `hikari_radius.dart`, `hikari_breakpoints.dart`, etc. | Root M3 theme and dimension tokens currently coexist under `app/theme`. | Move token authority to `core/ui/foundation`; app/theme only assembles ThemeData. No redundant token APIs. |
| `lib/core/ui/components/` and `lib/core/ui/patterns/` | Shared controls and several mixed-responsibility patterns | **Replace** with coherent target taxonomy; migration compatibility is temporary. |
| `hikari_modal_sheet.dart` | Thin `showModalBottomSheet` wrapper; no dialog/drawer strategy | Build full surface families with shared policies, not just a helper. |
| `hikari_confirmation_dialog.dart` | Shared confirmation exists; native `showDialog` route | Fit it into new Dialog family, typed result/dismiss/focus policy. |
| `catalog_detail_page.dart` | ~847 lines; chooses `context.isExpanded ? _showSourceDialog : _showSourceSheet` | Move surface selection and mechanical route behavior out of the feature. |
| `CatalogSourcePicker` flow | `CatalogDetailPage` currently creates two route variants, and their callbacks call `Navigator.pop`; the picker widget also calls `Navigator.of(context).pop()` in `_close()`; page callbacks own other route closures | Move route choice, geometry and completion to shared presenter; retain picker feature state/content. |
| `MediaPoster` | Shared artwork/decode/overlay/title/geometry concerns; grid/shelf math | Split visual anatomy, layout constraints and media loading responsibilities at explicit boundaries. |
| `ChapterRow` | Generic-looking shared widget accepts `Object`, pattern-matches `MangaChapter` / `NovelChapter` | Move model formatting to feature/presentation mapper and expose typed UI data. |
| `test/architecture_guard_test.dart` | Explicitly allows `core/ui -> app/theme`, `features -> app/theme`, and `core/ui/patterns -> domain`; restricts `app/theme` to itself. | Replace these legacy exceptions with the target one-way graph: `core/ui -> core/ui/foundation`, `app/theme -> core/ui/foundation`, `features -> core/ui`; check imports **and exports**. |
| `DESIGN_SYSTEM.md`, `UI_ARCHITECTURE.md`, `PRESENTATION.md` | Documents assume several current widgets and contracts | Revise as part of rebuild; documentation must describe *final reality*, not past phases. |
| Current routes | ~19 `*page.dart` files in `features/` in the snapshot | Inventory **all** UI entry points, not just Home and Catalog. |

**Evidence boundary:** static archive inspection only. Exact behavior/performance on Android/Windows, test counts and release builds are **not** asserted as verified by this document.

---

## 2. Final architecture: directories, naming and allowed dependencies

The proposed final layout is **the destination**, not a commitment to maintain old paths. **Dependency correction from the v3 self-review:** UI tokens and ThemeExtensions live in `core/ui/foundation` and are consumed by both the generic UI components and the app theme assembler. `app/theme` is the **composition owner of app-wide `ThemeData`**, NOT an upstream dependency of `core/ui`. This establishes a one-way dependency direction instead of preserving today's guard exceptions.

```text
lib/
├── core/
│   └── ui/
│       ├── foundation/                   # canonical semantic design decisions
│       │   ├── hikari_color_roles.dart    # ThemeExtension roles absent from M3 ColorScheme
│       │   ├── hikari_typography.dart     # semantic type ramp / font spec
│       │   ├── hikari_spacing.dart
│       │   ├── hikari_radius.dart
│       │   ├── hikari_size.dart
│       │   ├── hikari_breakpoints.dart
│       │   ├── hikari_motion.dart
│       │   └── hikari_aspect_ratios.dart
│       ├── controls/                     # buttons, choice, search, chip, segmented control
│       ├── feedback/                     # notices, progress, loading/empty/error visual
│       ├── surfaces/
│       │   ├── dialog/                    # HikariDialog + confirmation/form content pattern
│       │   ├── bottom_sheet/              # HikariBottomSheet
│       │   ├── drawer/                    # HikariDrawer (navigation-only semantics)
│       │   ├── adaptive/                  # transient presenter, purpose/policy, results
│       ├── navigation/                   # bar, rail, drawer destination visuals
│       ├── layout/                       # page shell, content region, adaptive grid/shelf
│       ├── media/                        # artwork, poster/card variants, progress overlay
│       ├── content/                      # section header, domain-free row and text patterns
│       └── hikari_ui.dart                # curated public API, no indiscriminate exports
├── app/
│   ├── theme/
│   │   └── hikari_theme.dart             # builds ThemeData from core/ui/foundation values
│   └── navigation/                       # application routes, destinations and root wiring
└── features/<feature>/
    ├── <feature>_page.dart                # route coordination, owned controllers
    ├── <feature>_view_model.dart          # commands and feature UI state
    ├── <feature>_ui_state.dart            # optional immutable UI snapshot
    ├── presentation/                     # typed display models and pure formatting
    └── widgets/                          # feature views composed from core/ui
```

**Directory decisions:**

- `controls`, `feedback`, `surfaces`, `navigation`, `layout`, `media`, `content` correspond to things developers search for. Do not impose `atoms/molecules/organisms` or rename files simply to follow atomic-design terminology.
- Group complex components into a small folder; a simple public component can be one file. Avoid forced `index.dart` files everywhere, gigantic barrel exports, vague `utils.dart`, `helpers.dart`, `common_widget.dart`, `base_*` and `manager_manager.dart` naming.
- Use `Hikari*` for public shared components (`HikariDialog`, `HikariAdaptiveGrid`). Use concrete feature prefixes (`CatalogSourcePickerContent`) for feature widgets. Prefer noun names for widgets/types and verb phrases for commands (`selectSource`, `openReader`).
- Preserve the `hikari_*` token names that are useful; permit changing token names **once** if needed, with all callers migrated. Never maintain `HikariColors` and `ColorScheme` mirrors or parallel old/new themes. Move the tokens from `app/theme` to `core/ui/foundation` as part of Phase 1; remove old exports when callers have migrated.
- `core/ui/**` may import Flutter and modules from `core/ui/**` (plus any deliberately permitted generic core utilities), **never** `app/theme`, `features/`, `domain/`, `application/` or `infrastructure/`. A token module never imports a component module. `app/theme/hikari_theme.dart` may import Flutter and `core/ui/foundation/**` to compose ThemeData; it must not import feature/UI screens or control widgets.
- `features/<feature>` may depend on domain/application abstractions, feature UI state, `core/ui` and Flutter UI APIs. It must **not** import `app/**` (including `app/theme` or `app/navigation`) or infrastructure implementations; read app theme values through `Theme.of(context)`/ThemeExtensions defined by `core/ui/foundation`. App's composition root depends **downward** on foundation and feature entrypoints, never the inverse.
- ViewModels do not depend on Flutter widgets, `BuildContext`, `Navigator`, route presentation or provider-specific platform UI.
- No file in `core/ui` decides which source to read, what a chapter ID means, or whether native permission is granted. Use typed immutable presentation data; never accept `Object` and pattern-match media domain classes in shared controls.
- A generic control **can** be stateful for intrinsic interaction (animation, focus, menu open, drag) without ceasing to be a presentation component. Stateful does not mean business-aware.

### 2.1 Component admission and lifecycle rules

- **Mandatory shared family** when the interaction is system-wide (dialog/sheet/drawer/presenter, buttons, field, navigation, section, standard cards). No duplication threshold applies.
- **Feature content** remains local where the semantics are genuinely feature-specific, even when it uses generic surfaces.
- **Variants** are documented alternatives of one component only when layout/data/interaction contracts are the same. Prefer composition or named variants to a component with 20 independent booleans.
- Each shared API includes purpose, props, ownership, accessibility and tests. Exports must be deliberate; internal helper classes private.
- End state: no duplicated `MediaPoster`/`MediaPosterCard` trees, no `showDialog`/`showModalBottomSheet` scattered in feature code, no undocumented exceptions to surface ownership.

---

## 3. Design-system foundation (single authority)

### 3.1 Tokens and semantics

1. **Color:** the app composes Material 3 `ColorScheme` using the canonical `core/ui/foundation` definitions; components consume it through `Theme.of(context)`. Material 3 `ColorScheme` for primary, surface, error, on-colors, outline and scrim. `ThemeExtension` only for true extra Hikari semantics (e.g. warning/info pairs), not aliases that restate ColorScheme. Dark and OLED must retain readable tonal hierarchy; no fixed hex in feature cards.
2. **Typography:** a documented semantic map (`display`, `headline`, `title`, `body`, `label`) from `TextTheme`, with line count/truncation/expansion rules per component. Titles remain available in full through semantics where visually ellipsized. Scaling must not be globally clamped to hide overflow.
3. **Spacing, radius, size, motion:** define one token vocabulary including touch targets, gutters, section rhythm, card geometry, adaptive content widths and motion; component-specific intrinsic/constraint-derived dimensions do **not** become arbitrary global tokens.
4. **Elevation:** map semantic surface roles to canvas, containers, dialog/sheet and interactive states. Hover/pressed/disabled use Material state theming, not arbitrary background paints.
5. **Brand:** artwork-first, restrained dark/Catppuccin-inspired chrome and OLED remain the *initial* visual direction; if the rebuild changes brand primitives, revise snapshot/tests and document why. Reader prose canvases/video black are explicitly separate surface categories.
6. **Media aspect ratios:** define named ratios (poster 2:3, landscape 16:9, square 1:1, feature hero as specified) with override only where the media class calls for it; `BoxFit` must not silently crop critical cover art.
7. **Geometry:** forbid unexplained literals in **shared** layout code; permit intrinsic pixels, responsive constraints, aspect ratios and measured dimensions where documented. Replace competing local card-height guesses with one sizing algorithm per card family.

### 3.2 Components & canonical states

Each interactive component handles enabled/disabled/loading/focus/hover/pressed/error, correct labels and keyboard behavior. Feature code must not duplicate these visuals. Core set:

| Family | Canonical API target | Responsibility |
|---|---|---|
| Buttons | `HikariButton`, `HikariIconButton` | semantic variants, size, loading, touch and focus |
| Choice/search | `HikariChoiceMenu<T>`, `HikariSearchField`, `HikariSegmentedControl` | controlled value, keyboard, clear/submit behavior, presentation only |
| Chips | `HikariFilterChip`, `HikariChipRow` | shared spacing and horizontal overflow policy |
| Feedback | `HikariAsyncStateView`, `HikariNoticeBanner`, progress indicator | loading/empty/error/retry visuals only |
| Content | `HikariSectionHeader`, `HikariListRow`, `HikariChapterRow` | shared spacing/text/action patterns and domain-free chapter presentation |
| Media | `HikariArtwork`, `HikariPosterCard`, `HikariLandscapeCard`, `HikariMediaProgress` | composable anatomy without domain imports |
| Layout | `HikariPageScaffold`, `HikariContentRegion`, `HikariAdaptiveGrid`, `HikariHorizontalShelf` | constraints, max content width, lazy layout, nav/safe-area slots |
| Surfaces | `HikariDialog`, `HikariBottomSheet`, `HikariDrawer`, `HikariAdaptivePresenter` | presentation mechanics and routing policy |

**Avoid wrapper theater:** if a `HikariButton` adds no Hikari responsibility beyond aliasing a Material widget, prefer the Material component plus centralized theme instead. *Mandatory common behavior/policy* is the reason for a named wrapper, not branding of every native widget.

### 3.3 Artwork architecture

Separate (a) feature-specific selection of artwork references and asynchronous loading, (b) generic cache/decode/placeholder/scrim/semantics rendering, and (c) card layout and interactions. There must be one authoritative decode sizing policy in the media pipeline; moving artwork logic out of `MediaPoster` must **not** accidentally reload identical bytes, lose source identity or break Hero tags. Progress and badges are composable slots with typed presentation props.

### 3.4 Text and geometry

Use native layout constraints/`Flexible`/`Expanded`/`AspectRatio`/slivers as the default. Use `TextPainter` only when a measured text metric is genuinely required and scoped/cached correctly; do not replace every `TextPainter` with unbounded layout or prohibit all metrics. A grid item must reserve correct text space under nonlinear scaling; if text is too large, fallback to a less dense layout instead of clipping important actions.

---

## 4. Transient surface system — mandatory foundation

**This is a key new architectural requirement.** Hikari provides distinct, first-class components and a common *presentation policy*, rather than forcing each feature to implement variants independently.

### 4.1 Components are NOT interchangeable aliases

| Component | Owns | Appropriate use | Must not be used as |
|---|---|---|---|
| `HikariDialog` | focus scope/trap, semantics, barrier, alignment, width/height constraints, scrolling, header/actions, result | confirmation, focused choice, form, permissions explanation | navigation drawer or arbitrarily unbounded list |
| `HikariBottomSheet` | compact anchored modal layout, safe-area, keyboard inset, drag/handle, scroll/height limits, result | source/option picker and contextual actions on narrow/compact layouts | substitute for large route/page or generic desktop modal |
| `HikariDrawer` | navigation destinations, selected route, header/footer, close and focus, drawer gesture | temporary app navigation when window class warrants a navigation drawer | generic side sheet for editing a form or selecting a source |
| `HikariSidePanel` *(only if needed)* | contextual side sheet/panel aligned to content, bounded width, persistent/modal behavior | desktop inspector/filter/source selection if a panel is a better interaction than dialog | `NavigationDrawer` |
| `HikariAdaptivePresenter` | choosing a **transient** mode by declared purpose + environment, mounting it and returning a typed result | one selection/form/confirmation workflow across layouts | global navigation state, business rule engine or a wrapper hiding material interaction differences |

**Three mandatory first-class components are Dialog, Bottom Sheet and Drawer.** They share design tokens, interaction/accessibility conventions and centralized adaptive rules, **but not a single route-result API**. `HikariAdaptivePresenter` hosts transient Dialog/Sheet. A contextual SidePanel requires a separate reviewed implementation and explicit API extension before it can be resolved; `HikariNavigationShell` selects bottom navigation/Rail/`HikariDrawer` using its navigation policy. A contextual side panel is a *distinct optional component*, not a second meaning silently assigned to Drawer. This split prevents two different responsibilities from being mislabeled "responsive modal".

### 4.2 Presenter input and typed result contracts

The policy is explicit and intentionally narrow. Illustrative API (architectural pseudocode, **not** a claim that it compiles now):

```dart
enum HikariPresentationPurpose { confirmation, selection, form }
enum HikariPresentationMode { dialog, bottomSheet } // sidePanel not shipped in v4.2
enum HikariDismissReason { cancelAction, closeButton, back, barrier, drag, routeDismissed, routeDisposed }

sealed class HikariPresentationResult<T> {
  const HikariPresentationResult();
}
final class HikariCompleted<T> extends HikariPresentationResult<T> {
  const HikariCompleted(this.value);
  final T value;
}
final class HikariCancelled<T> extends HikariPresentationResult<T> {
  const HikariCancelled(this.reason);
  final HikariDismissReason reason;
}

// The route host determines mode; the feature supplies a single content tree.
Future<HikariPresentationResult<T>> showHikariAdaptive<T>({
  required BuildContext context,
  required HikariPresentationPurpose purpose,
  required bool useRootNavigator, // same Navigator for sheet AND dialog
  required Widget Function(BuildContext, HikariPresentationController<T>) contentBuilder,
  HikariPresentationPolicy? policy,
});
```

Controller/interface owns `complete(T value)`, `cancel(reason)` and dismiss restrictions. It does not own feature ViewModel operations. A `null` selection may be valid (e.g. an "All" filter), so do not conflate nullable values with user cancellation. **All implemented transient modes** expose the same typed result and cancellation semantics. Actions that might complete twice are guarded once by the host/route coordinator, not by copy-pasted flags across two modal builders. **Drawer selection instead updates app navigation state; it does not complete this Future.**

The shipped v4.2 `HikariPresentationMode` has only `dialog` and `bottomSheet`. A `HikariSidePanel` is a future, separately reviewed opt-in; don't add an enum member or resolver branch without a functional host and tests. `HikariDrawer` belongs exclusively to navigation and is never returned as a transient mode. Avoid inferring a dismissal reason solely from a null return from a framework route: distinct Back/barrier/drag reasons require real route-event interception; route disposal must resolve pending futures exactly once.

**Navigator invariant:** `useRootNavigator` is required for both low-level route helpers and the adaptive entrypoint. The caller/coordinator chooses the owning Navigator explicitly; both modes use that exact Navigator. Do not inherit mismatched framework defaults (`showDialog`: root; `showModalBottomSheet`: nearest), and never choose Navigator ownership from viewport width. Include nested Navigator and resize tests before freezing API.

**Dismiss-reason truthfulness:** Explicit actions can produce `cancelAction` or `closeButton`; `back`, `barrier`, or `drag` are reported only when an event was actually observed by the host. If Flutter closes the route without a distinguishable event, report `routeDismissed` rather than guessing an input cause. Reserve `routeDisposed` for host/route teardown where the controller resolves a pending Future. Cancellation vs completion and exactly-once completion remain mandatory, even if the precise gesture cannot be determined.

The API above is a **design sketch**. During implementation validate generic typing, route result delivery, `Navigator` ownership, `PopScope` and restoration against Flutter 3.47.5, then publish the tested final API in documentation.

### 4.3 Responsive purpose matrix (defaults, not blind breakpoints)

| Purpose | Compact constrained window | Medium | Expanded/wide |
|---|---|---|---|
| Confirmation / destructive consent | constrained dialog or accessibility-friendly full-screen dialog when necessary | dialog | bounded dialog |
| Source / item selection | modal bottom sheet | sheet or dialog depending on available height and content | bounded dialog; optional genuine side panel where interaction benefits |
| Long validated form | full-screen form route or adaptive dialog according to complexity | dialog/route | dialog or dedicated page |
| Global navigation **(shell policy, not presenter)** | bottom navigation / drawer if overflow | navigation rail or temporary drawer | rail / persistent navigation; Drawer only for navigation semantics |
| Contextual inspector/filter | inline/temporary sheet | dialog or contextual panel | contextual side panel **if implemented**, otherwise dialog |
| Irreversible native installation | Flutter explanation/confirmation per safety contract + native system consent | same | only platform-supported native operation |

**Important:** short screen height, software keyboard, pointer modality and actual local width can override a window-width default. Breakpoints are centrally named starting hypotheses (current `600/840/1200` logical px classification); use feature/widget constraints where appropriate. Do not duplicate `context.isExpanded` conditions in each screen.

### 4.4 Mechanical route behavior contract

All shared **transient** surface components and their host specify and test the following; `HikariDrawer` additionally follows the app-shell destination, focus and Back contracts in §5:

1. **Dismissal:** explicit Cancel, X, Back/Escape, drag, barrier and host disposal; distinguish cancel from submitted result. Distinct reasons are emitted only for observed events; ambiguous framework dismissals are `routeDismissed`. Confirmation must never treat barrier/back as consent.
2. **Navigation ownership:** presenter closes its own route, feature does not `Navigator.pop` from the picker content. App router owns top-level navigation. Use the correct root/nested navigator consistently.
3. **Focus/keyboard:** move focus into dialog; predictable Tab/Shift+Tab; Escape/Back; restore previous focus if still mounted; provide screen-reader title/semantics and barrier label.
4. **Insets and constraints:** safe areas, keyboard `viewInsets`, short landscape, drag affordances, max width/height, scrollable content and persistent action footer all owned by the surface or standardized slots, not repeated manually by features.
5. **Concurrency:** prevent double submit/pop, ignore stale async completions using the feature's generation/cancellation policy, and never route after disposal.
6. **Resize during open:** default policy is **do not recreate an active surface solely because its breakpoint changed**. Adapt its content/constraints in place and preserve typed state; define a different explicit strategy only if mode migration is proven safe and tested.
7. **Restoration:** identify which forms/selection sheets need state restoration. Standard `showDialog` is not automatically restorable; when required, provide route factories compatible with `Navigator.restorablePush`/`DialogRoute` and serializable arguments, or document a different valid restoration strategy.
8. **Theme and motion:** semantic Material surfaces, consistent shape/elevation, high contrast, reduced-motion, RTL, large-font behavior. No widget paints its own arbitrary shadow, radius or overlay without a named style variant.
9. **Instrumentation:** optional stable route/purpose analytics identifiers separate from business ID; avoid leaking sensitive selected values in logs.

### 4.5 Source Picker as the reference migration

**Target flow:**

```text
Catalog Detail CTA
  -> Catalog feature coordinator creates/owns SourcePickerViewModel
  -> HikariAdaptivePresenter(purpose: selection, content: CatalogSourcePickerContent)
       compact: HikariBottomSheet
       expanded: HikariDialog (unless explicit side-panel policy approved)
  -> HikariCompleted<CatalogSourceSelection> / HikariCancelled
  -> coordinator validates current series/source generation
  -> application command / navigation to Reader or manual search
```

**Must delete after migration:** feature-local `_showSourceSheet`, `_showSourceDialog`, the `context.isExpanded` modal branch, direct `Navigator.pop` inside **the page-owned callbacks and `CatalogSourcePicker._close()`**, repeated barrier/keyboard/modal constraint logic and obsolete `_CatalogSourcePickerResult` shape if replaced with named immutable presentation types.

**Must retain as behavior:** language selection, candidate list, metadata/artwork, retry, Search manually, source identity, cancel/Back semantics and the existing navigation checks. Validation and result authorization remain outside `core/ui`.

**Why this is not "genericizing" the feature:** Source Picker remains a **feature-specific workflow/content**. Only its universal UI infrastructure is shared, and its selection/result contract is typed.

### 4.6 Feature escape hatch

A feature may request a new mode/style **only** by proposing a supported variant in the shared surface family, documenting the semantic reason and adding tests. No permanent direct `showDialog`, `showModalBottomSheet`, manual `OverlayEntry`, bespoke drawer or copy-pasted responsive route implementation inside feature presentation code. Truly platform/system-owned dialogs remain documented external-system exceptions.

---

## 5. App shell, navigation, responsive layout and modality

### 5.1 One navigation source of truth

- App root defines destination IDs, labels/icons, selected destination and shell layout strategy once. `HikariNavigationShell` resolves the navigation mode and shows Bottom Bar, Rail or `HikariDrawer` as *views of the same destinations and selected state*, not separate lists. The Drawer dispatches navigation events, not a transient typed modal result.
- Keep primary navigation state stable when resizing; do not push a new destination just because bar becomes rail. Support deep links/back according to the application's actual route mechanism.
- Route-specific page header/action patterns are standardized in `HikariPageScaffold` and `HikariPageHeader`, including navigation clearance, system insets and content width; feature authors supply title/actions/content, not custom header geometry per route.
- Audit current custom docked navigation rather than treating it as untouchable. Rebuild it or switch to Material navigation if the new global contract and product design support that choice. Record the decision and test focus, semantics and scroll obstruction.

### 5.2 Layout classes and measurement

- **Window classification** at shell level for broad navigation selection; **local `LayoutBuilder` constraints** for component/grid/card/surface rendering. Never use physical-device identity ("tablet", "Windows") as a substitute for available UI space.
- Centralize adaptive policy in named testable resolvers; avoid every page defining a different `600/800/900` split.
- Fixed content-width bounds and gutters must be documented; section content should align between Home, Catalog, Library, Local, Search and Settings.
- `HikariAdaptiveGrid` preserves lazy item construction and item identity. Determine columns from available width + min/max poster extent rather than desktop/mobile flags; support scaled text and adaptive tile extents.
- `HikariHorizontalShelf` owns axis, spacing, accessible scroll behavior and constraints; section header is a composable slot. Distinguish shelves from generic scrolling lists and avoid nested unbounded scrollables.
- Preserve tab selection, filter state, list position and active reader progress across resize/rotation/window minimization; `PageStorageKey` is useful for compatible list layouts, but not a substitute for keyed view-model identity or deliberate restoration.
- Foldable/window display features are optional for first production scope but app must not assume a single fixed physical screen.

### 5.3 Input and interaction contract

All shared controls and routes are usable with touch, mouse, keyboard, D-pad where relevant and screen reader. Define hover/pressed/focus visuals; predictable tab order; Escape/Back semantics; labels and hints; disabled reason; appropriate touch targets; semantic announcements on loading/errors; reduced motion; and scroll wheel/trackpad. On Android, TalkBack/system font/back gesture are release checks. On Windows, desktop resize and keyboard navigation are release checks.

---

## 6. Feature presentation architecture and state ownership

```text
USER ACTION
  -> Shared control callback (presentation-only)
  -> Feature coordinator or ViewModel command
  -> Application/domain workflow and repository
  -> Immutable feature UI state or typed action result
  -> Feature content composition using core/ui
  -> Host surface/navigation (if required)
```

**Feature Page (route):** owns route parameters, `TabController`/`ScrollController` where appropriate, route lifecycle, navigation permissions, async generation/ownership of transient flows, and delegates data operations. It must not build 200 lines of dialog chrome or format every row inline.

**Feature ViewModel:** owns domain/application reads, loading/error snapshots, filtering, source search, durable selection, retry, stale-response protection and commands; it exposes immutable UI state. No `BuildContext` or popup implementation. Feature ViewModel lifecycle belongs to a deliberate scope (route/session), with a single disposal policy.

**Feature `presentation/`:** maps domain values to immutable display data and action models (e.g. `CatalogChapterRowData`, `MediaPosterData`); mapping is pure, with no API call or navigation. May include a tightly scoped formatter. Do not use `Object`/runtime `switch` in core widgets to handle different domain classes.

**Feature widgets:** arrange and render state via shared components, emitting typed callbacks. They may own ephemeral interaction state (expanded synopsis, local focus, animation), but must not open arbitrary routes from inside generic content.

**Shared `core/ui`:** Flutter presentation mechanics and visual composition only. It never accepts an application workflow object simply to save the feature one line of callback wiring.

**Asynchronous safety:** command locks, stale results, active source/series identity, disposal and post-await route permission are protected wherever the actual ownership lives. Migrating UI is not a reason to erase these protections.

### 6.1 Page decomposition criteria

- Separate route/shell, ViewModel and content when responsibilities have different lifecycle/abstraction.
- Split named coherent sections (Hero, metadata, chapter list, source selection) where each can be reviewed/tested independently.
- Extract typed shared rows/cards; keep domain adapters in the relevant feature.
- A file over ~250–350 lines warrants a review, **not** automatic rejection; 20 small forwarding widgets can be worse than a cohesive 300-line widget.
- No giant mutable `State` classes containing network orchestration, dialogs, scroll geometry, and row rendering in one method.
- No new file solely to hide a 10-line method without a distinct responsibility.

---

## 7. Screen-by-screen migration coverage

| Screen/feature | Final component contracts | Special regression requirements |
|---|---|---|
| Root Home / navigation | app shell, standardized page header, content region, card/shelf/hero variants | Continue prioritized, discovery, header alignment, navigation state on resize |
| Library | standard grid, card, filters, confirmation surface | saved identity, artwork, confirmation on add/remove from **all origins** |
| Local media | grid/cards, search/filter, async feedback, page shell | local scanning/navigation untouched, artwork and cache retained |
| Catalog Search / Source Search | shared search field, choice/chip/segmented controls, result grid/rows | explicit submit semantics, stale-result prevention, facets, provider data |
| Catalog Detail | composed header/info/chapters, standardized rows, adaptive picker | tab scroll preservation; catalog-first source -> Reader; source identity |
| Catalog Source Picker | shared adaptive host + feature content + typed selection | compact sheet/expanded dialog, cancel, language, candidate, retry/manual search |
| Source-native Detail | shared header/row/presentation; no bespoke page geometry | source-first detail remains distinct from catalog-first |
| Manga reader | reader-specific canvas and overlay controls; shared generic modal surfaces where suitable | previous/next, reading order, progress, offline/cache and gestures |
| Novel reader | reader canvas/typography, generic choices/sheets where applicable | pagination/scroll, font preferences, chapter progress, prefetch |
| Plugin Manager | standard responsive content, chips/menus, common dialogs/forms/panels | installed/available/update facets; inventory refresh |
| APK and Novel action routes | shared visual dialogs/form containers; feature-owned consent and operation workflow | explicit trust approval, native system consent, no stale callback |
| Settings | shared page shell/section/control/expansion/form surfaces | theme changes, OLED, cache accordion, validation |
| Player | shared page shell where appropriate; dedicated immersive video canvas | player lifecycle, controls, progress; no theme intrusion |

**Inventory requirement:** During Phase 0 enumerate every UI route, overlay, page-level `Scaffold`, and direct `showDialog`/`showModalBottomSheet`/drawer use. This table is a starting map, not permission to skip less visible screens.

---

## 8. Rebuild implementation phases (ordered; complete migration required)

### Phase 0 — Baseline, inventory and superseding architecture decision

**Deliverables:**

- Snapshot of all routes, shared widget usages, inline overlays, layouts, colors, spacing, breakpoints, state owners, duplicate helpers and existing accessibility/test coverage.
- Existing behavior and risk matrix for key flows (Source Picker, Library confirmation, plugin consent, Reader-first Continue, catalog/source-first detail, Home shelves).
- **New superseding ADR** named `ADR-0XX-ui-foundation-rebuild.md` (number assigned against real repo) that explicitly supersedes conflicting UI clauses of historical ADRs, with exact affected documents. Amend ADR-003 guards as needed; retain domain/security ADRs unless separate decisions authorize changes.
- Updated target dependency graph, component catalog and migration map; baseline screenshots and test/analysis/build/profile data collected when execution tools are available.

**Gate:** every affected UI concern has target owner and every critical workflow has a regression specification. No implementation shortcuts justified by "old spec says preserve".

### Phase 1 — Build the canonical theme/components/surface infrastructure

- Normalize theme tokens and visual rules once; choose component names/variants and document their APIs.
- Build/test mandatory `HikariDialog`, `HikariBottomSheet` and `HikariAdaptivePresenter` with typed result/dismiss contracts; build/test `HikariDrawer` under `HikariNavigationShell` with shared destination-selection and Back/focus contracts.
- Standardize page shell/header and app navigation destinations/policy; define shared controls and feedback.
- Verify constraints, safe area, keyboard, focus, Back/Escape, barrier, double completion and resizing across modes.
- Create a **component gallery/dev playground** (debug-only or tests) for visual states/variants, preventing ad-hoc component creation during later feature migrations.

**Gate:** component tests and use-case demo pass on compact, medium and expanded windows. Feature migrations cannot add a new bespoke surface without changing the shared contract first.

### Phase 2 — Media anatomy and layout primitives

- Implement typed generic artwork, card variants, badges/progress, section header, grid and shelf contracts.
- Separate decode/cache optimization from layout and domain binding. Preserve Hero/semantics and avoid duplicate artwork fetching.
- Build stress fixtures with long multilingual titles, unknown/missing artwork, 1×–3× text scaling, short/long windows and large item counts.
- Verify laziness, constraints and cost of layout/texture decode in representative screens.

**Gate:** no duplicate media-card foundation remains after migrating a representative vertical slice; new APIs are stable enough for all consumers.

### Phase 3 — Migrate feature screens in deliberate waves

**Wave A:** Home, Library, Local, Catalog Search, Source Search (shared layout and cards).  
**Wave B:** Catalog Detail + Source Picker + Source Detail (complex state and adaptive surfaces).  
**Wave C:** Settings, Plugin Manager, APK/Novel action pages (forms, feedback, native consent).  
**Wave D:** Manga/Novel readers and Player surrounding chrome (preserve specialized immersive rendering).

For **each** feature: replace old calls, migrate presentation models, delete its bespoke shared-pattern duplicate, update focused tests, self-review ownership, then integrate. Do not leave one screen as an indefinite special-case exception.

**Gate:** all entry points audited and migrated, except *explicitly documented external OS surfaces* and specialized reading/video canvases that correctly obey their own documented visual contract.

### Phase 4 — Cleanup and architectural enforcement

- Remove legacy `components/`/`patterns/` paths and transitional adapters once their last consumers are migrated. No simultaneous public `MediaPoster` and `HikariPosterCard`, no legacy inline source-picker route helpers.
- Remove the `core/ui/patterns` → `domain` exception and enforce the new directory-boundary rules, imports **and** exports, direct and relative paths, including negative test fixtures.
- Enforce no direct feature calls to surface route functions except narrowly documented platform/system integration points. Prefer rule-based static checks over brittle plain-text grep alone; maintain a reviewed allowlist of justified exceptions with owner and expiry.
- Run analyzer, formatting, full unit/widget/integration suites and Android/Windows builds where applicable; update canonical docs to final source reality.

**Gate:** one active API per common concern, no orphan modules or compatibility bridges, tests and architecture guard pass.

### Phase 5 — System-level verification and acceptance

- UX consistency review across **all** features (headers, filters, actions, modal placement, density, loading/error, disabled states).
- Android Redmi Note 9S device test: screen rotations, compact height, TalkBack, keyboard, dark/OLED, source reader, plugin consent and offline/continue flows.
- Windows desktop test: interactive resizing, Tab/Shift+Tab, Escape/Enter, mouse/hover/wheel, modal focus/return, text scaling and window minimization.
- Profile before/after on matched data, settings and device: frame build/raster distribution, slow frames, memory/artwork decode, initial load and interaction latency. No measured regression accepted merely because architecture looks cleaner.
- Final self-review: no hidden bespoke surfaces, no duplicate UI state owners, obsolete UI ADRs superseded, source of truth docs current.

**Gate:** the rebuilt UI becomes the only production UI foundation. Any unfinished feature migration keeps the initiative **open** rather than claiming the architecture is standardized.

---

## 9. Quality and automated test contract

| Dimension | Required checks |
|---|---|
| **Static architecture** | dependency/import/export violations; forbidden feature-layer modal helpers; no domain refs in core/ui; token duplication; stale exports |
| **Shared component states** | normal, focused, hover, disabled, loading, error, selected, destructive, OLED/dark |
| **Surfaces** | dialog/sheet/drawer layout; typed result; null-as-value; Cancel/X/Back/Escape/barrier/drag; nested navigator; double-close; route disposal |
| **Adaptive behavior** | sample widths around 320, 375, 599/600/601, 839/840/841, 1199/1200/1201, 1440 logical px; short height; keyboard open; resize while modal open |
| **Text and semantics** | text scales 1, 2, 3; long titles; multiple languages/RTL fixtures; semantics labels; contrast; keyboard traversal; touch target sizes |
| **Card and lists** | 2:3/16:9 variants, missing/slow/corrupt art, Hero, stable identity, scroll restoration, lazy 5k+ items test fixtures |
| **State and workflow** | source/series switches, stale async outcomes, tab position, repeated open/close, selection cancel without side effects, continuation/reading order |
| **Native consent** | APK/Novel exact package identities, dialog cancel, system installer denial, runtime permission, foreground/generation guards |
| **Performance** | matched real-device profile runs; no excessive layout passes, unnecessary clipping/rebuilds/image decoding |
| **Build gates** | `fvm dart format` relevant sources; `fvm flutter analyze --no-pub`; full `fvm flutter test`; platform debug/release smoke checks as appropriate |

**Testing hierarchy:** pure layout/policy unit tests → widget/semantics tests → route integration tests → full cross-feature tests → physical-device verification. Goldens can guard intentional visual contracts but are not proof of behavior/accessibility. The repo's actual test count must be measured anew; older counts are not a baseline promise.

### 9.1 Formal definition of done for a migrated feature

- [ ] Uses canonical page/header/navigation/surface/layout/card contracts wherever applicable.
- [ ] Has no locally reinvented Dialog/BottomSheet/Drawer or modal geometry.
- [ ] Has typed UI data/callbacks; UI components do not import domain/application/infrastructure.
- [ ] State ownership and disposal are explicit; async permissions still enforced.
- [ ] Narrow and expanded, short height/keyboard, scaled text and keyboard/TalkBack tested.
- [ ] All previous valid user outcomes (including cancellation/failure) preserved or intentionally changed in an accepted product decision.
- [ ] Obsolete paths/adapters removed; architecture docs and tests updated.
- [ ] Self-review includes named risks, measured results and unverified limitations.

---

## 10. Architectural conflict decisions (v2 self-review corrections)

| v2 position | v3 decision | Reason |
|---|---|---|
| "Standardize what exists; do not create a new UI framework" | **Supersede**: intentionally create/restructure a disciplined shared UI foundation | New product requirement is a clean, future-maintainable UI architecture. |
| "Accepted UI ADRs outrank spec" | **Supersede after approving a new ADR**: new decisions override conflicting *UI* clauses | History should be traceable, not immutable. |
| "Keep components/patterns directories initially" | **Replace with target taxonomy and full migration** | Final discoverability and ownership matter more than import churn. |
| "No generic modal router" | **Replace with a typed adaptive surface presenter**, not a generic business router | All feature flows should share presentation mechanics/policy. |
| "Source Picker owns explicit dialog and sheet variants" | **Reject**: one content workflow through shared presenter | The former layout choice was a local exception, now prohibited. |
| "Only extract reusable patterns after duplicate consumers" | **Reject for system-wide infrastructure** | Modal, navigation, fields, cards and layout contracts should be established proactively. |
| "Native Material first; shared controls only when necessary" | **Refine**: Material remains implementation substrate; Hikari wraps where real common policy/semantics exist | Avoid pointless wrappers, but do not let Material defaults fragment Hikari behavior. |
| "Avoid all broad visual rewrites" | **Supersede**: visual changes are authorized within the new design contract | Large coordinated change is intentional, with quality gates. |
| "Prefer compatibility with old MediaPoster" | **Reject indefinite compatibility**: build canonical media component and remove old consumers | One maintainable implementation at the finish line. |
| "Preserve business and native trust correctness" | **Keep** | UI authority does not override data correctness, security or consent. |
| "No blanket ban on TextPainter or numeric values" | **Keep with tighter guidance** | Some measurements are necessary; undocumented magic layout isn't. |
| "No hard <250-line gate" | **Keep with complexity review** | Correct ownership matters more than arbitrary length. |

### 10.1 Superseded documentation workflow

Do not delete ADR history. Create a superseding UI ADR with sections **Context / Previous constraint / New decision / Impacted APIs / Migration / Consequences**. Mark old UI decisions `Superseded by ADR-0XX` **only in the conflicting scope**; retain their domain/runtime decisions when unaffected. Reconcile the current `docs/architecture/{DESIGN_SYSTEM,UI_ARCHITECTURE,PRESENTATION}.md`, architecture guard and component docs with the code at each major integration gate. This spec does not automatically edit those files.

---

## 11. Research-derived design-system lessons: what is borrowed, what is not

**Evidence classification:** `External` means documented guidance from an external design system; `Hikari choice` means an intentional project policy, not a claim that the external source mandates this exact architecture.

| External guidance (verified 2026-10-22) | What the source actually emphasizes | Hikari architectural choice / caution |
|---|---|---|
| [Flutter architecture guide](https://docs.flutter.dev/app-architecture/guide), [recommendations](https://docs.flutter.dev/app-architecture/recommendations) | Separate View, ViewModel, repository and service; unidirectional data flow; immutable UI state. Guidance is adaptable, not mandatory class-per-widget. | Feature route has an explicit lifecycle owner; ViewModel performs feature commands; local ephemeral controls stay with widgets. Do not create one ViewModel for every tiny row. |
| [Material Design 3](https://m3.material.io/), [adaptive layouts](https://m3.material.io/foundations/layout/layout-overview), [Flutter adaptive guidance](https://docs.flutter.dev/ui/adaptive-responsive/best-practices) | A design system spans tokens, components, layout, navigation and adaptive behavior. Available space and platform capabilities matter. | Shared shell, typed surfaces, bounded content regions and layout policies. Do not decide UI from OS name alone. |
| [IBM Carbon component checklist](https://www.carbondesignsystem.com/getting-started/contributing/component-checklist) | A component's definition of done spans design specification, code, states, tests, accessibility, documentation and design kit; lifecycle Draft/Preview/Stable. | Each shared Hikari component has a maturity stage, owner, examples, test matrix and removal/deprecation policy. Hikari does not need Carbon's full organizational process. |
| [Microsoft Fluent tokens](https://fluent2.microsoft.design/design-tokens), [layout](https://fluent2.microsoft.design/layout), [principles](https://fluent2.microsoft.design/design-principles) | Semantic aliases over raw design values, purposeful spacing rhythm, platform-natural behavior. | One semantic token vocabulary with central theme ownership; preserve Windows keyboard/pointer behavior without forking feature business flows. |
| [Adobe Spectrum design tokens](https://spectrum.adobe.com/foundations/design-data) | Use ready components when possible; tokens govern consistent extensions rather than rebuilding controls ad hoc. | Core visual controls built on Flutter Material where appropriate; do not build a second inaccessible render framework. |
| [Shopify Polaris](https://polaris.shopify.com/foundations) | Shared UI language across app surfaces with platform/surface-specific component availability. | Same product semantics across Android/Windows, not necessarily identical layout or interaction for every surface. |
| [Effective Dart](https://dart.dev/effective-dart), [design](https://dart.dev/effective-dart/design), [style](https://dart.dev/effective-dart/style) | Consistent naming, intent-revealing APIs, economical rather than dense code, deliberate public interface. | Component APIs must be discoverable, typed and documented. No forced folder splitting, proxy widgets or giant `common.dart`. |
| [Flutter accessibility testing](https://docs.flutter.dev/ui/accessibility/accessibility-testing), [WCAG 2.2](https://www.w3.org/TR/WCAG22/) | Automated tap-target/label/contrast checks plus manual accessibility and focus review. | Screen-reader, touch and keyboard acceptance, with WCAG used as relevant design guidance rather than assuming CSS-pixel criteria directly equal Flutter logical dp. |
| [Flutter performance best practices](https://docs.flutter.dev/perf/best-practices) | Keep widget builds cheap, use lazy lists, avoid unnecessary intrinsic passes, opacity/clipping layers. | Measure representative expensive screens and compare on the same profile device/build; avoid broad numeric promises without baseline. |

**Do not copy blindly:** React component folder structures, Storybook, Figma workflows, web-specific WCAG CSS units, proprietary visual palettes and framework-specific state containers are **not** prerequisites for Hikari. Borrow their quality concepts and implement in Flutter/Dart.

### 11.1 Design-system deliverables created before broad page work

The project should create these **maintained** assets (paths are targets, not current files):

1. `docs/ui/FOUNDATIONS.md`: visual principles; semantic color, type, spacing, radii, size, motion; reader/video exemptions.
2. `docs/ui/COMPONENTS.md` + `docs/ui/components/<name>.md`: single catalog, maturity, anatomy, API, states, keyboard/semantics, do/don't, examples and owning folder.
3. `docs/ui/PATTERNS.md`: interaction recipes (search/filter, confirmation, picker, permission explanation, reader controls, error/empty, refresh, progress).
4. `docs/ui/RESPONSIVE.md`: shell/navigation/window and local layout classes, constraints, modal policy, density and resize semantics.
5. `docs/ui/CONTRIBUTING.md`: review checklist, component proposals, exceptions, deprecation and changelog protocol.
6. `docs/ui/MIGRATION_MATRIX.md`: every screen/modal/component; owner, old->new mapping, test gate, last legacy consumer.
7. A **debug-only component gallery** exercising variants and stress cases, with golden/widget tests located outside release UI.

If canonical documents already exist elsewhere, **edit/move/replace them rather than maintaining duplicate authorities**. Each doc points to the actual implementation and its tests.

---

## 12. Component design and API contracts — developer rules

### 12.1 Layers of UI vocabulary

```text
Foundation decisions: semantic ColorScheme/ThemeExtension, type, spacing, shape, motion
    ↓
Controls & surfaces: button, field, menu, chips, dialog, bottom sheet, drawer
    ↓
Patterns: picker flow, search+filters, confirmation flow, poster shelf, detail layout
    ↓
Page templates: browse/search/detail/library/settings/reader shell
    ↓
Feature compositions: Catalog Source Picker, Plugin Manager, Library etc.
```

This is **conceptual composition**, not a demand for one Dart package/folder per line. Page templates should exist where they define repeatable layout/behavior (e.g. detail scroll and header alignment), not be an all-purpose `GenericPage<T>`.

### 12.2 Required contract for EVERY stable shared component

| Contract field | Required answer |
|---|---|
| Purpose and non-goals | Why it exists; which other component to choose instead |
| Inputs | Semantic, typed, named parameters; default behavior and validated ranges |
| Controlled/uncontrolled model | Who owns selected value/text/controller; how external changes propagate; disposal rules |
| Output | Event callbacks or typed result; distinction between cancel, empty value, error and success |
| Variants | A finite named set by behavior/importance; no mutually contradictory booleans |
| Layout | Min/max constraints; overflow/reflow; content density; short height, keyboard, RTL and large text |
| Interaction | Tap, pointer/hover, focus, keyboard, D-pad if relevant, gesture, Back/Escape and disabled state |
| Accessibility | Semantics, label/hint, content order, focus return, announcements, large text, contrast/target size |
| Theming | Token roles and any intentionally supported override; dark/OLED tests |
| Performance | Rebuild boundaries, laziness, resources/controller disposal, cost for long lists, image decoding |
| Support | Component owner; docs/gallery examples, tests, stability stage, migration and deprecation process |

### 12.3 API rules that prevent future maintenance problems

- **Composition is preferred over prop explosion.** Use slots such as `header`, `leading`, `trailing`, `content`, `footer` when variability is visual and structural. Use named constructors or sealed configuration types if variants change behavior.
- **Separate mechanism from semantics:** `HikariDialog` is the container. `HikariConfirmationDialog` defines confirm/cancel semantics. A `SourcePickerContent` remains feature content. Never name a generic presenter after a single consuming feature.
- **Data props over dynamic domain objects:** immutable display data is appropriate for a card family where it yields meaningful reuse; callbacks carry typed IDs/actions, never `Object` or provider-specific runtime classes.
- **Explicit ownership for controller props:** if `TextEditingController`, `FocusNode`, `ScrollController` or animation controllers can be externally provided, document who creates/disposes them. The component disposes only resources that it owns.
- **No ambiguous asynchronous events:** a button that starts work has a declared owner for pending/error/retry and duplicate-tap protection. `isLoading` is a rendering prop, not an excuse for reimplementing the command in a shared button.
- **No arbitrary `BuildContext` retention** across asynchronous gaps. Check lifecycle and route identity before navigating; do not use `mounted` alone to validate whether the source/series changed.
- **No global `GlobalKey` hacks** for ordinary layout or cross-feature business communication. Keys express stable identity and state restoration intent.
- **No wrapper for every Material class:** Hikari **does** own its Dialog/Bottom Sheet/Drawer/shell families and critical consistent controls, but can use `Text`, `Icon`, `Expanded`, `Padding`, `Flexible`, `Material` directly when named wrappers would add no usable contract.
- **No `utils.dart` gravity well:** a helper belongs beside its owner or in a narrowly named pure module. No generic `BaseWidget`, `CommonMixin`, or giant shared parameter objects.
- **Public shared APIs** have `///` docs, examples and a stable import surface; implementation helpers are private. Follow Effective Dart import ordering and naming. `dart format`, lints, static analysis and test assertions are baseline, not the design review itself.

### 12.4 Reuse decision tree

```text
New UI requirement
  ├─ Existing canonical component with correct semantics? → use/extend variant
  ├─ Universal interaction pattern (surface, field, nav, async feedback)? → core/ui owner
  ├─ Reusable screen layout with shared constraints/scroll policy? → page template/layout
  ├─ Feature-only semantics/content? → features/<feature>/widgets or presentation
  └─ Platform-native/engine-owned UI (install permission, video surfaces)?
     → documented integration boundary, never counterfeit generic UI
```

Before adding a shared component, identify at least one real reference consumer and a small runnable usage example. **A single consumer is fine** for foundational behavior if the semantic contract is universally reusable. No “reuse after three copies” prerequisite.

---

## 13. Responsive / navigation / surface interaction standard

### 13.1 Policy layers and priority

1. **Semantics first:** confirmation, selection, navigation, contextual inspector and full-screen workflow are different purposes. No breakpoint turns a dangerous consent flow into an incidental dismissible choice.
2. **Available size:** root shell uses window constraints for global navigation; nested cards/surfaces use **local** constraints for layout. Check usable height after keyboard/safe-area insets.
3. **Input and platform conventions:** Windows keyboard, pointer, scrolling, window resize; Android touch, Back gesture, IME, TalkBack. Support hybrid input without branching solely on OS name.
4. **Content complexity:** large selection sets require search/lazy list; short menus use anchored menus where appropriate; long forms may become a route rather than an oversized modal.
5. **State continuity:** changes in width must not reset active source, route, form edits, list position, picker selection, focus or in-flight operation.

### 13.2 UI surface selection matrix (normative default)

| Intent | Narrow/touch-first | Wide/pointer-rich | Stable owner |
|---|---|---|---|
| Confirm/destructive action | `HikariDialog` (full-screen variant only when needed) | Bounded `HikariDialog` | Dialog + feature coordinator decides consent |
| Small menu attached to a control | Anchored accessible menu when usable | Anchored menu | Shared menu control (NOT adaptive presenter) |
| Multi-item source/filter/item picker | `HikariBottomSheet`, scrollable | Bounded `HikariDialog`; optionally contextual side panel if semantically justified | Adaptive transient presenter + feature content |
| Contextual temporary actions | Bottom sheet if anchored menu insufficient | Menu/dialog/side panel by interaction needs | Shared pattern, documented policy |
| App destinations | Bottom Navigation and/or temporary navigation Drawer per destination count | Rail/persistent navigation; drawer remains **navigation** | Root Navigation Shell |
| Persistent contextual inspector | Dedicated adaptive panel when adopted; otherwise route | Contextual side panel | Feature view + panel shell; NOT navigation drawer |
| Immersive reader/player | Dedicated full-screen route with reader controls | Dedicated reader/player layout | Feature canvas; shared settings controls only |

The surface choice must be represented by a **pure resolver** `(purpose, availableSize, inputCapabilities, contentTraits) → mode` that is straightforward to unit-test. Not all inputs must be part of the first implementation; unknown capabilities have safe defaults. A low-level `HikariDialog` remains directly usable for fixed semantic cases. Feature developers **do not** call raw `showDialog` / `showModalBottomSheet` to circumvent the approved surface system.

**Important practical split:** `HikariDrawer` is a first-class reusable navigation drawer. A contextual desktop drawer is a separate `HikariSidePanel` / contextual sheet, with its **own** contract even if it visually enters from the screen edge. The user-requested three named surface families exist independently; the presenter is the integration layer, not their replacement.

### 13.3 Additional modal/route rules missing from v3

- **Pending forms:** decide whether dirty-state dismissal should ask for confirmation; the presenter should respect a typed dismissal policy rather than duplicating `PopScope` in individual pages. Do not accidentally break the platform back gesture or native system dialogs.
- **Result ownership:** `Completed(null)` can be a legal value; `Cancelled(reason)` is different. A typed *failure* should be explicit if failures are part of the presenter's contract; ordinary search/network errors stay within feature state while modal remains open.
- **Atomic completion:** route must complete at most once even if double tapped, barrier/back races with a callback or async operation completes after disposal.
- **Nested navigation:** presenter must resolve the intended navigator/root overlay; tests include presenting from nested tabs and restoring focus to the invoking element.
- **Screen transitions:** full-screen route vs modal chosen by semantics and content, not by one global 600px threshold.
- **Resize while open:** preserve the existing route and reflow **content** by default; avoid swapping sheet ↔ dialog live unless restoration of draft, focus and route result is explicitly proven.
- **System dialogs:** Android installer/permission prompts are owned by the OS/plugin runtime. Hikari's explanation UI cannot replace or auto-approve their consent.
- **Overlay exceptions:** tooltip/menu/selection handles/reader system overlay may use framework overlay primitives **inside an approved shared host** when technically required; any exception has one owner and tests. Blanket grep bans without semantic analysis produce false positives.

### 13.4 Page shell contract

A shared page shell specifies: app bar/header slot, breadcrumb/back policy where applicable, safe areas, shell nav clearance, max reading/content width, gutters, scroll owner, loading/error region, refresh affordance and restoration key. Feature pages never each subtract custom navbar sizes. Nested slivers, pinned headers and tab layout are supported via composition (not one `ListView` wrapped around everything).

**Named page templates for actual Hikari needs:** browse/grid, collection/library, search/result, media detail/tabs, settings/sections, management/list, immersive reader/player. They share shell behaviors but do not pretend their scroll/gesture requirements are identical.

---

## 14. State, flows, lifecycle and failure handling

### 14.1 Ownership matrix

| State/concern | Default owner | Lifetime and invalidation |
|---|---|---|
| Theme, selected destination, root navigation | app layer | app/session; responsive shell doesn't reset it |
| Search query, selected source/filter, page loading/error/data | feature ViewModel / feature page state contract | route or explicitly defined scope; version/generation on series/source changes |
| Repository data, progress, cache, plugins | existing application/domain/infrastructure | existing transaction/identity semantics preserved |
| FocusNode, text edit controller, implicit animation, transient expansion | specific control/feature view that creates it | widget; always dispose owned resources |
| Scroll offset, tab index, draft form state | owning feature/view state with restoration where required | must survive supported reflow; persist only when product intent demands |
| Modal presence/result/dismiss mechanics | shared surface host | overlay route; completable once |
| Permission/trust/install operation | feature/application/native guard | never transferred into generic modal UI |
| Snackbar/notice/toast policy | shared feedback host; feature supplies message/action | avoid unrelated overlay stacking and stale navigation context |

**All page states must specify:** initial/loading/success-empty/success-data/partial-data/error/offline where relevant; refresh, retry, stale response and transitions while data is already visible. Avoid replacing loaded content with a full-screen spinner for every refresh.

### 14.2 Standard workflow for a new page

1. Write a short **user journey** (entry points, main action, cancel/back, empty/error/offline, deep link).
2. Specify **state shape and owner** before `build()` implementation; list all async operations and invalidation conditions.
3. Choose a **page template** and canonical components; sketch compact / medium / wide arrangements.
4. Identify feature display models, domain/application calls and navigation results.
5. Implement **one vertical slice** from route → VM → shared component → result, test it on two very different window sizes.
6. Expand variants, error cases and other consumers only after the pattern is verified; write gallery examples as part of the component task.
7. Compare UI against a reference matrix, run accessibility/interaction checks, and document any new shared component or exception.

This workflow is a **Hikari policy synthesized from sources**; the external references do not prescribe exactly these seven steps.

### 14.3 Async and side effects

- Rendering is state-driven; `build()` does not start network requests, installs or route changes.
- Distinguish search editing/debounce from explicit query submission; the contract must be defined per search use case. Search fields must support external controller updates without stale `clear`/`submit` affordances.
- Clear ownership of cancellation: delayed future completion cannot navigate a replaced series/source or an unmounted route; command and generation checks remain in feature/controller scope.
- Error UI has recoverable vs destructive semantics; retry actions have stable names; partial results are not discarded unnecessarily.
- A source picker result is a **selection**, not proof that a provider is still available or authorized; coordinator revalidates identity and invokes application command.

---

## 15. Accessibility, internationalization and visual/interaction quality

### 15.1 Accessibility acceptance

- **Touch:** on Android, target a minimum actionable region of **48×48 logical px** for primary controls where applicable; exact Flutter guideline check covers semantics nodes. On Windows, visual controls can be denser with usable hit boxes; avoid confusing the WCAG 2.2 web minimum target rule (24 CSS px and exceptions) with Android dp.
- **Focus:** visible keyboard focus, rational order, initial focus within temporary surface, focus restoration on dismissal, Tab/Shift+Tab, Enter/Space, Escape/Back and keyboard search submit.
- **Semantics:** meaningful labels for icon controls, title/order of modal, media artwork role, progress value semantics and reduced duplicate announcements; deliberately hide decorative imagery. Do not rely on color alone for status.
- **Text scaling:** test Flutter `TextScaler` at normal, large and accessibility sizes (examples 1×, 2×, 3×) and check **nonlinear** scaling. When layout cannot accommodate it, reflow or switch density; do not silently cap globally.
- **Colors:** audit contrast of common foreground/background pairs, dynamic overlay opacity and states under dark and OLED separately; adapt the actual palette rather than assuming tonal `surfaceContainer` guarantees WCAG contrast.
- **Motion:** honor `MediaQuery.disableAnimations` and platform reduced-motion preferences where applicable. Hero/reader transitions cannot block access to content or important action.
- **Screen reader:** actual TalkBack pass on Android; Windows screen-reader/focus sanity pass when available. Automated semantics assertions do **not** prove audible labels are good.

### 15.2 Localization-ready, not localization-overengineering

- Interactive labels, errors and tooltips should not be new hardcoded English UI strings. A central localization/message abstraction is the destination; actual supported locales are a **separate product decision**. Start by inventorying current literals and planning the migration.
- Use direction-aware padding/position (`EdgeInsetsDirectional`, `AlignmentDirectional`) for content that should mirror; test representative RTL rendering rather than making false claims of completed RTL translation.
- Preserve media title language and provider/source language codes; distinguish app locale from catalog content-language filter.
- Dates, counts, percentages and progress metadata use locale-aware presentation formatting at feature adapter boundaries.

### 15.3 Visual system catalog

For each component and reference page, maintain visual specimens for: default, hover, focused, pressed, selected, disabled, loading, empty, error, offline (if relevant), dark/OLED, long strings, missing artwork, compact/wide and high scaling. A gallery item includes **behavioral knobs** and a reference screenshot where useful, not just static UI.

---

## 16. Quality gates, performance budgets and tooling

### 16.1 Enforcement tiers (no false precision)

| Tier | Mechanism | Hard gate? |
|---|---|---|
| Syntax & style | `fvm dart format`, `flutter analyze`, selected Dart lints | Yes |
| Import graph | Existing `test/architecture_guard_test.dart` extended to check imports/exports, relative/package references and allowed owners | Yes after affected phase migration |
| UI practice | Named API docs and reviewer checklist; AST-based/custom lint only for rules that can be enforced reliably | Review gate; avoid fragile regex-only bans |
| Shared components | Unit, widget, semantics, keyboard, safe-area, focus, result/dismiss tests | Yes for Stable APIs |
| Responsive | Boundary matrix, small height, IME, resizing active flow, rotation | Yes for each migrated reference screen |
| Visual | Deterministic golden fixtures with pinned fonts/locale/devicePixelRatio and controlled host OS | Gate on supported golden environment; visual review needed for intentional changes |
| Integration | Source Picker, Library, plugin trust/install cancel, readers, navigation | Yes when workflow affected |
| Hardware | Android device baseline (Redmi Note 9S) and Windows desktop | Release/acceptance gate; measure same build mode/fixture |

**Architecture guard caveat:** existing guard has constraints like one route widget declaration in a `_page.dart` file. **Reassess whether it reflects the *new* ownership contract**; do not preserve an arbitrary structural rule merely because tests currently enforce it. Prohibited import paths are mechanically checkable. “A UI is too complex” and “modal behavior should be shared” require a mixture of code review and targeted tests; regex alone is not a rigorous proof.

### 16.2 Performance protocol

- Baseline real journeys: open Home, swipe poster shelf, scroll dense Library/Local grid, switch Catalog tabs, open/change Source Picker, open reader, resize Windows.
- Record cold/warm operation, device, Android/Windows build, profile/release mode, item count, artwork/cache state, display refresh capability, font scale, animation state, and CPU/frame timeline.
- Track frame build/raster duration distributions, missed frame deadlines, memory/image allocation and repeated decode/network counts where measurable. **Do not invent universal numeric pass/fail budgets** before an actual baseline or compare debug against profile.
- Diagnose only after measurement: avoid unnecessary `IntrinsicHeight`, `saveLayer`, broad `Opacity`, deep clipping, non-lazy children and full-tree rebuild from a small local state change. `const` can help but is not a performance architecture.
- Prefer product-important regression thresholds defined after Phase 0 baseline. If device is already smooth, preserve smoothness while simplifying the code; don't optimize to the detriment of maintainability without evidence.

### 16.3 Deterministic tests

- Unit test **pure layout policy resolvers** with numerical dimensions and state classes, not `MediaQuery` mocks everywhere.
- Widget tests use controlled text scale, theme, `MediaQuery`, locale, data source fakes and deterministic delays.
- Route tests cover nested Navigator, `PopScope`, barrier/drag, keyboard, dirty form, double completion, pending network result after dismissal and selected result even when `T?` is null.
- Goldens are sensitive to fonts and host platform: pin test fonts/surface sizes and do not mix Windows and Linux reference outputs as if pixel identical. [Flutter golden API](https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html).
- Use UI smoke tests for one **representative consumer** of each shared family before migrating every screen.

---

## 17. Component lifecycle, exceptions and ongoing governance

### 17.1 Component lifecycle (adapted from Carbon, deliberately lightweight)

| Stage | Entry | Allowed consumers | Exit gate |
|---|---|---|---|
| **Draft** | Named problem, owner, alternatives and provisional props | gallery or isolated spike | reviewed anatomy, responsibility, responsive/semantics design |
| **Preview** | Runnable Flutter implementation + one genuine reference consumer | deliberately scoped migration | API and interaction tests; initial docs; decision on defaults |
| **Stable** | Known accessible behavior, variants, docs, tokens, tests | all first-party features | approved checklist and curated public export |
| **Deprecated** | Replacement exists, explicit deadline/consumer map | only tracked legacy consumers | zero consumers; remove old code, exports, tests and stale docs |

Hikari is an app rather than a distributed design-system SDK. A migration can **replace an unstable API outright** when all app consumers are updated in the same integration wave. Long third-party deprecation windows are unnecessary unless Hikari actually publishes a public UI package. No indefinite dual API.

### 17.2 Proposal and change rules

Before creating/changing a shared component, a developer/agent must provide a short record:

```text
Problem / interaction purpose:
Current component or nearest alternative:
Proposed owner and API:
Semantics + focus + back/escape:
Compact / expanded + short-height behavior:
Loading/error/disabled and state ownership:
One real usage fixture:
Test scenarios:
Affected consumers / migration-removal plan:
Tradeoffs and reason a new variant is justified:
```

This may be an issue or short PR note; do not create ten redundant design docs for a button. The important property is **one decision trail**. Code reviewers may reject feature-specific modal chrome even if it visually matches tokens, because it fragments interaction behavior.

### 17.3 Exceptional UI

Exceptions exist for technically different interaction models (immersive manga renderer, platform video control surface, OS-owned installer, drag-and-drop file picker, native IME), **not** for feature-team preference. Each exception must have a named owner, rationale, dependency diagram, product behavior, test and revisit trigger. If the same exception becomes common, promote the mechanism into foundation instead of cloning it.

### 17.4 Documentation as code

- Canonical docs and gallery names follow implementation; stale pages/exports are failing acceptance, not just cosmetic debt.
- Public control signature changes require example/test migration in the same change; obsolete classes must be deleted at the documented gate.
- No ADR supersession by vague blanket statement: record exact clauses/UI contract superseded and retain unrelated domain/security decisions.
- ADRs explain **why**; component docs explain **how to use**; tests enforce **what must remain true**. No conflicting source-of-truth pages.

---

## 18. Hikari-specific pilot migration and execution plan

### 18.1 Recommended vertical slices before mass migration

**Pilot A — Adaptive Source Picker**

- Proves `HikariDialog`, `HikariBottomSheet`, resolver, typed result, one content tree, feature VM lifecycle, cancel/back/focus/IME, and no route-specific modal geometry.
- Source evidence: `CatalogDetailPage._showSourceSheet` and `_showSourceDialog` currently build two presentation routes and call `Navigator.pop` in page callbacks; content is `CatalogSourcePicker`. **Additionally `CatalogSourcePicker._close()` directly pops its route; migrate all closure paths to the shared presenter.**
- Regression: language filter, candidate metadata/artwork, manual search, stale source/series, Reader navigation and cancel without side effect.

**Pilot B — Library/Search Card and Layout**

- Proves poster/card anatomy, artwork identity, section/header alignment, responsive grid/shelf geometry, text scaling, controlled search/filter, and lazy rendering.
- Avoid blanket replacement of reader canvas/Chapter controls before the basic visual system is stable.

**Pilot C — Plugin Management Surfaces**

- Proves dialogs/forms/feedback/page shell and multi-step operations under asynchronous native authority.
- Never let a new confirmation UI silently skip package-trust checks or Android system install consent.

Only after these pilots pass integration gates should migration spread to every page. The target architecture is **comprehensive** even though execution is **sequenced**; sequencing is not a decision to tolerate permanent legacy exceptions.

### 18.2 End-to-end sequencing contract

**Numbering convention:** §§8.0–8.5 are **conceptual architecture stages**, not the literal execution-plan IDs. The implementation plans are **00 baseline/ADR → 01 foundation/controls → 02 surfaces/navigation/picker → 03 media/layouts → 04 browse/detail → 05 settings/plugins → 06 reader/player → 07 cleanup/acceptance**. The master plan's G0–G7 are the execution gates. Where descriptions appear to differ, the spec defines the target behavior while the master plus phase plans define execution order; any new design decision requires updating all three before implementation.


1. **Foundation audit + superseding ADR + baseline fixtures** (Phase 0), explicitly superseding the old theme import exceptions.
2. **Move canonical tokens into `core/ui/foundation`, change `app/theme` to ThemeData composition, and establish component API catalog** (first part of Phase 1).
3. **Surfaces + root shell/navigation + controlled input contracts** and Pilot A.
4. **Artwork/MediaCard/Grid/Shelf + layout templates** and Pilot B.
5. **Browse/detail feature migrations**: Home, Library, Local, Search and Catalog/remote series, tracking every legacy caller.
6. **Settings + plugin management**, including complex native workflows and Pilot C.
7. **Reader and Player chrome** after the shared surfaces/layouts are stable.
8. **Remove legacy implementation**, strengthen import/overlay/theme guards, synchronize docs and gallery.
9. **Release-quality proof**: Android/Windows UX, accessibility and profile performance, full regression.

For each phase publish: affected paths, old->new map, acceptance matrix, known exceptions, test results, risk and rollback strategy. Rollback is a **temporary implementation safety net**, not a reason to preserve the old UI permanently.

---

## 19. Final adversarial self-review — failure modes and preventive controls

| Possible failure after rebuilding | Design mistake causing it | Required countermeasure |
|---|---|---|
| Still have 3 separate modal implementations per feature | Only standardized colors, not route/dismiss/focus/result contracts | Presenter + actual Dialog/Sheet families + modal migration inventory |
| A shared component takes 25 boolean props | Trying to generalize every use case into one widget | Named semantic variants, slots and separate patterns; API review |
| `core/ui` can import domain models or `app/theme` | Reused `MediaPoster` assumptions or old theme layering leaking through | One-way architecture guard for imports/exports; typed display props and foundation token authority |
| Duplicated view models and layout wrappers | Mechanical “one class per file/layer” rule | Ownership-based decomposition, code review of forwarding classes |
| Desktop feels like enlarged phone | Width-only tests, no input/scroll/focus policy | Native-like navigation and pointer/keyboard fixtures |
| Drawer is used for arbitrary editing | Visual “slide from side” confused with navigation semantics | Navigation Drawer vs Contextual Panel contracts |
| Large text clips cards | Fixed card heights copied to grid/shelf | Constraint-driven layout, density fallback, text scaling tests |
| Modal loses source/filter when resized | Recreate route at breakpoint transition | Preserve active route and VM state; reflow only content |
| Reader/plugin behavior regresses | “UI-only” refactor erased timing/trust/identity guards | Regression scenarios including stale completion and native consent |
| Golden tests are green but UI is unusable | Screenshots mistaken for interaction/a11y proof | Keyboard/screen reader/integration/manual device checks |
| New UI changes look clean but regress performance | Non-lazy grids, image re-decodes, layout intrinsics | Real-device profile baselines, measured frame/decode analysis |
| New component taxonomy becomes confusing | Too many names, misplaced barrel exports | Design catalog, owner, minimal imports, naming and API review |
| Docs and code diverge in 3 months | No ongoing contribution process | Component maturity and docs-as-code acceptance |
| Team cannot safely clean legacy UI | No consumer inventory, long-lived adapters | Migration matrix and explicit deletion gate |
| Tests enforce bad historical architecture | Existing regex guards treated as permanent truth | Revisit guard invariants as part of superseding ADR |

**Self-review outcome:** v4 makes the original rebuild policy operational: who owns what; how components are admitted, documented, tested and retired; how responsive/surfaces behave; and how developers continue adding pages without bringing back exceptions. Some final API signatures and visual token values remain **implementation-stage design decisions**, because choosing them without executable prototypes and end-user verification would be false precision. This does **not** postpone or weaken the requirement to establish one canonical system.

---

## 20. Final acceptance checklist / review record

### Architecture and developer experience

- [ ] New UI ADR and canonical docs have one non-contradictory authority; legacy UI clauses explicitly superseded.
- [ ] `core/ui` contains no domain/application/feature imports **or `app/theme` imports**; `app/theme` only composes ThemeData from `core/ui/foundation`, with no reverse dependency.
- [ ] Surface system includes independently documented/tested Dialog, Bottom Sheet and Navigation Drawer; contextual panel never masquerades as a navigation drawer.
- [ ] No first-party feature maintains its own raw modal route/sizing/focus infrastructure without a documented technical exception.
- [ ] Shared page shell/navigation/refresh/search/filter/layout policy replaces feature-specific scaffolding geometry.
- [ ] Every Stable shared component has discoverable doc/gallery example, defined variant/state/keyboard/semantics and tests.
- [ ] Public API signatures are typed and concise; state/controller/disposal ownership is documented.
- [ ] Feature route, ViewModel, presentation mapper and visual components have clear reasons to change; no redundant forwarding layers.
- [ ] One legacy implementation remains for **zero** shared concern at completion: old UI classes/exports/paths removed.

### Product experience and safety

- [ ] Compact, medium, wide, short-height and IME tests pass for reference flows; resizing preserves state.
- [ ] Text scaling, RTL layout fixtures, TalkBack and Windows focus/keyboard work as intended.
- [ ] Dark/OLED semantics and contrast survive all interactive states.
- [ ] Home/Library/Catalog/Local/Search/Settings/Readers/Plugin Manager/Player are covered by the migration matrix.
- [ ] Source Picker, Library confirmations, reader progress/source identity and native plugin permission/installation flows pass end-to-end regressions.
- [ ] Android and Windows build/test reports are recorded from **this** branch/revision; profile performance is compared against a reproducible baseline.

### Reviewer decision

- **Pass only when:** every required target is delivered or explicitly classified as a technically legitimate exception with an owner, tests and documented alternative.
- **Do not pass simply because:** analyzer is clean, lines are fewer, only Android looks good, or old and new UI systems coexist behind compatibility wrappers.
- **Honesty boundary:** this specification and the supplied snapshot were reviewed; the architecture has not yet been implemented or verified on hardware.
