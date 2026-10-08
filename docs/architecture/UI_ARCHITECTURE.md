# UI Architecture

> Status: Accepted foundation for the UX/UI phase
> Scope: Flutter presentation layer only
> Applies to: Android, Windows, and future iOS UI

## 1. Purpose

This document defines the UI architecture for Hikari.

The goal is to keep the presentation layer as clean and evolvable as the current
domain/application/infrastructure architecture while Hikari moves into its UX/UI
phase.

Hikari supports three different consumption modes:

- Anime/video: player-centric and cinematic.
- Manga: image-centric and reader-centric.
- Light novel/publication: typography-centric and reader-centric.

The UI architecture must provide a consistent product language without forcing
those experiences into one oversized generic component system.

This document is the default rule for new UI code. Deviations are allowed when a
real requirement justifies them, but they should be deliberate rather than
accidental.

---

## 2. Design principles

### 2.1 UX before visual polish

Navigation, hierarchy, loading, error recovery, empty states, resume behavior,
and content flow are product behavior.

Visual styling should reinforce those behaviors, not hide unclear UX behind
decoration.

### 2.2 Shared foundations, feature-owned experiences

Global visual rules belong outside features.

Feature-specific compositions remain inside the feature that owns their UX.

A shared component should exist because multiple real use cases need the same
abstraction, not because it might be reusable later.

### 2.3 Prefer composition over configurable mega-widgets

Do not create one `MediaCard` with many booleans and mode switches for anime,
manga, novel, library, search, and history.

Prefer small stable primitives that features compose into their own product UI.

### 2.4 Semantic styling over raw values

Features should consume semantic roles such as:

- `ColorScheme.onSurface` for primary text
- `ColorScheme.onSurfaceVariant` for secondary text
- `ColorScheme.surface` / `surfaceContainer` for backgrounds
- `ColorScheme.outlineVariant` for subtle borders
- `ColorScheme.error` for errors

Features should not depend directly on arbitrary palette values such as
`grey700`, `blue500`, or one-off `Color(...)` literals.

This keeps dark mode, future OLED themes, accent customization, and visual
redesigns local to the theme layer.

### 2.5 Promote shared UI only after reuse is proven

A widget begins inside its feature unless it is clearly an app-wide primitive.

When a feature widget is reused by multiple features and its responsibilities are
stable, promote the reusable part into `core/ui`.

Do not build a large design-system package before the product requires one.

### 2.6 Keep presentation copy localization-ready

Domain and application state must stay language-neutral. Do not store user-facing
English labels in domain enums, and do not render enum `.name` values as product
copy.

Keep single-use copy close to the feature that owns it. When the same domain value
has proven shared presentation semantics across multiple features, centralize that
mapping in `core/ui/patterns` rather than duplicating switches across screens.
Visual variants that mean something different in one feature may remain local.

Hikari does not add a localization framework before a second locale is an active
requirement. When localization work starts, prefer Flutter's generated `gen_l10n`
ARB workflow and locale-aware formatting instead of a custom string service.
Replace English presentation mappings at the UI edge; domain contracts stay
unchanged. App UI locale is separate from source/content language metadata; do
not use one as the other.

---

## 3. Layer model

Hikari UI uses four presentation levels:

```text
app/theme
    ↓
core/ui/components
    ↓
core/ui/patterns
    ↓
features/*/widgets
    ↓
features/*/*_page.dart
```

Dependencies flow downward only.

Feature-specific UI may depend on the theme and shared UI.

`app/theme` and `core/ui/components` must not depend on feature code.

---

## 4. Directory structure

Recommended structure:

```text
lib/
├─ app/
│  ├─ theme/
│  │  ├─ hikari_theme.dart
│  │  ├─ hikari_status_colors.dart
│  │  ├─ hikari_size.dart
│  │  ├─ hikari_typography.dart
│  │  ├─ hikari_spacing.dart
│  │  ├─ hikari_radius.dart
│  │  ├─ hikari_motion.dart
│  │  └─ hikari_breakpoints.dart
│  │
│  └─ app.dart
│
├─ core/
│  └─ ui/
│     ├─ components/
│     │  ├─ hikari_button.dart
│     │  ├─ hikari_chip.dart
│     │  ├─ hikari_icon_button.dart
│     │  ├─ hikari_refresh_action.dart
│     │  ├─ hikari_scaffold.dart
│     │  └─ hikari_search_bar.dart
│     │
│     └─ patterns/
│        ├─ async_state_view.dart
│        ├─ media_metadata_view.dart
│        ├─ media_artwork_decode.dart
│        ├─ media_poster.dart
│        ├─ media_progress_bar.dart
│        └─ media_type_presentation.dart
│
└─ features/
   ├─ library/
   │  ├─ library_page.dart
   │  ├─ library_view_model.dart
   │  ├─ library_button_view_model.dart
   │  └─ widgets/
   │     ├─ library_content.dart
   │     └─ library_button.dart
   │
   ├─ remote_manga/
   ├─ novel_reader/
   ├─ player/
   └─ ...
```

This is a guideline, not a requirement to create every file immediately.

Only add a token or component when the product actually uses it.

---

## 5. `app/theme`: design foundations

`app/theme` owns global visual language.

The canonical application theme is `HikariTheme.darkTheme()` in
`app/theme/hikari_theme.dart`. App chrome uses the Material 3 `ColorScheme`
and `TextTheme` directly. Only non-Material warning/info pairs require the
`HikariStatusColors` extension.
[Design System](DESIGN_SYSTEM.md) owns the current token and component contracts.
[ADR-014](../decisions/ADR-014-canonical-theme-consolidation.md) explains
why the formerly separate `design_system/` implementation was consolidated.

It may contain:

- app `ThemeData`
- semantic colors
- typography scale
- spacing scale
- corner radii
- motion durations and curves
- responsive breakpoints
- shared content-width rules

It must not contain domain or feature behavior.

### 5.1 Theme entry point

`hikari_theme.dart` is the public composition point.

It should build the app's Flutter `ThemeData` and expose Hikari-specific theme
extensions when Flutter's built-in theme model does not represent a token.

Feature code should normally obtain theme values from `BuildContext`, not import
implementation palette files directly.

---

## 6. Token model

Use a small token system.

Do not create a complete enterprise design-token taxonomy before Hikari needs it.

### 6.1 Primitive values

Primitive palette values are implementation details of the theme.

Example:

```text
neutral50
neutral100
neutral900
accent500
red600
```

Feature UI should not consume these directly.

### 6.2 Semantic colors

Common chrome uses `Theme.of(context).colorScheme` roles, not a parallel color
adapter. Use `onSurface`, `onSurfaceVariant`, tonal `surfaceContainer*`,
`outlineVariant`, and `error` according to meaning; do not create local hex
alternatives. Warning/info have dedicated theme-extension pairs, and
`mediaTypeBadgeColors` resolves media identity badges.

Prefer:

```dart
color: Theme.of(context).colorScheme.onSurfaceVariant,
```

over literal gray palettes or adding a redundant adapter.

### 6.3 Spacing

Start with a small scale.

Example:

```dart
abstract final class HikariSpacing {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}
```

Do not force every physical measurement into the spacing scale.

Values that are intrinsic to a component or media format may remain component
constants when they are not general layout spacing.

### 6.4 Radius

Use a small shared radius scale for repeated surfaces and controls.

Avoid unrelated one-off radii across feature screens.

### 6.5 Typography

Typography should represent semantic roles, not individual screens.

Examples:

```text
display
headline
title
body
bodySmall
label
caption
```

Reader typography is a separate concern.

Manga and light-novel readers may expose user-controlled reading preferences and
should not be constrained to the exact typography choices of normal app chrome.

### 6.6 Motion

Centralize repeated durations and curves used by app chrome.

Examples:

```text
fast
normal
slow
pageTransition
```

Reader gestures and player transitions may use feature-specific motion when
their interaction requires it.

Respect reduced-motion/accessibility settings where possible.

### 6.7 Breakpoints and content width

Hikari targets phone, tablet, and desktop.

Responsive behavior should be expressed through shared breakpoint/content-width
rules instead of independent width checks scattered across screens.

Prefer adaptive layout decisions such as:

- bottom navigation on compact screens
- navigation rail/sidebar on wider layouts
- bounded content width on desktop
- adaptive poster grid column count

Do not fork entire feature implementations by platform unless platform behavior
truly differs.

---

## 7. `core/ui/components`: visual primitives

A component belongs here when it is:

- reusable across unrelated features;
- mostly visual/interaction infrastructure;
- independent of Hikari domain entities;
- stable enough to have one shared contract.

Examples:

```text
HikariButton
HikariIconButton
HikariChip
HikariChipRow
HikariScaffold
HikariSearchBar
```

A primitive should not know about:

```text
Media
MangaSeries
NovelChapter
SourceId
ProgressSession
```

If it needs those concepts, it is probably a product pattern or feature widget.

Do not wrap every Material widget.

A Hikari wrapper is useful only when Hikari owns meaningful styling,
interaction, accessibility, or API behavior beyond the stock widget.

---

## 8. `core/ui/patterns`: reusable product UI

Patterns are reusable Hikari-specific compositions.

Unlike primitives, a pattern may represent product concepts.

Examples:

```text
MediaPoster
MediaProgressBar
MediaMetadataView
AsyncStateView
media type presentation mapping
```

Patterns should still avoid feature workflow ownership.

For example, `MediaPoster` may render artwork consistently, but it should not
decide whether tapping a manga result opens series details or a chapter reader.

Navigation and workflow decisions remain with the feature/application boundary.

---

## 9. Feature UI

Feature UI owns product-specific composition and behavior.

Examples:

```text
LibraryMediaCard
ContinueReadingSection
MangaSearchResultCard
NovelSearchResultCard
ChapterListTile
ReaderToolbar
PlayerControls
```

These widgets may combine shared tokens, primitives, patterns, and feature state.

They stay inside the feature until reuse proves that a lower-level abstraction
is valuable.

### 9.1 Feature anatomy and ownership

Feature UI follows one structural rule regardless of screen size. Do not wait for
a file to become long before applying the boundary.

```text
features/<feature>/
├─ <feature>_page.dart          # route/lifecycle/composition boundary
├─ <feature>_view_model.dart    # feature UI state + commands, when state exists
├─ <feature>_labels.dart        # optional feature-owned presentation mapping
└─ widgets/
   ├─ <feature>_content.dart    # screen composition when the page would render it
   └─ ...                       # feature-owned visual components
```

The names may vary when one feature owns several routes, but the responsibilities
do not.

**Page / route boundary**

- creates and disposes view models and Flutter controllers;
- reacts to widget lifecycle (`initState`, `didUpdateWidget`, `dispose`);
- receives navigation callbacks and composes the route scaffold;
- may own truly visual ephemeral state such as expansion, focus, scroll,
  animation or text-controller state;
- must not become the home of reusable cards, sections, grids, toolbars or
  other non-route visual widgets. Those live under `widgets/`.

**View model / state holder**

Use a view model whenever a route owns asynchronous loading, filtering, retry,
selection that changes visible data, pagination, or data-derived UI state. It
owns state transitions and commands, not widgets. It must not depend on
`BuildContext` or emit localized/user-facing copy when a language-neutral value
can be exposed instead.

Do not create a view model merely to move one `bool` out of a page. Pure visual
ephemeral state stays with the view. A short one-shot UI command may also keep its
`busy` flag and snackbar handling in the page when its result does not become displayed
feature data; once a command participates in retryable/data-derived screen state, it
belongs in the ViewModel. This is a semantic rule, not a line-count threshold.

**Naming contract**

For route-style features, use predictable names so ownership can be found without
searching the whole tree:

- route widget: `<Feature>Page`;
- state holder: `<Feature>ViewModel`;
- immutable snapshot: `<Feature>UiState`;
- async phase enum when needed: `<Feature>Status`;
- extracted screen composition: `<Feature>Content`;
- page-owned state-holder field: `_viewModel`.

Specialized reader/player controls may use semantic names such as `PlayerControls` or
`MangaReaderTopBar`; do not rename domain-specific components to generic `Content` merely
for symmetry.

**UI state shape**

A state holder exposes one immutable feature state snapshot. Do not scatter loading,
error, results and selection across independent fields in the page. Choose the state
representation from the actual state topology:

- use a feature-local `Status` enum inside one state snapshot when controls, filters,
  stale content or retry state must survive an async transition; this is the default for
  browse/search/detail/library routes;
- use sealed states only when the states are genuinely mutually exclusive and do not need
  to preserve shared/stale payload across transitions;
- a composite screen such as Home may carry separate status/error slices for independent
  data regions instead of forcing unrelated requests into one global status;
- reader/player controller state remains lifecycle state at the view boundary when it is
  coupled to Flutter/platform controllers rather than application data.

The goal is one predictable snapshot per state holder, not one universal state class for
every feature. A different representation requires a different state topology, not file
size or author preference.

**Feature widgets**

Every non-page visual component owned by a feature lives under that feature's
`widgets/` directory, including components reused by sibling routes in the same
feature. A component moves to `core/ui` only after cross-feature reuse proves a
stable shared responsibility.

**Reusable behavior components**

A feature-owned widget reused across routes may be a small view in its own right. If it
loads or mutates domain/application state, give that view a paired feature ViewModel and
keep repository/use-case operations out of the widget state. The widget may own only the
paired ViewModel lifecycle plus visual feedback such as snackbars. `LibraryButton` is the
canonical example.

Pure visual state such as carousel position, slider drag position, reader preference sheet
selection or animation state does not require a ViewModel. Feature widgets must not call
application workflows or repository operations directly.

**Presentation mappings**

Feature-specific labels/formatters may live beside the page when they are not
widgets. Cross-feature presentation semantics belong in `core/ui/patterns`.
Neither location changes domain identity or stores locale-specific product copy in
domain/application models.

**Reader/player session exception**

Readers and the video player may keep session state in their page only when that
state is inseparable from a Flutter/platform controller lifetime: playback,
transform/gesture state, scroll position, HTML-anchor navigation, debounce/flush
of the active reading session, or content bytes currently being decoded for that
session. This is a topology exception, not a size exception.

Browse/filter/source selection, reusable metadata loading, or any state that can
be represented independently of those controllers still belongs in a ViewModel.
If a reader accumulates enough controller-independent state that this distinction
becomes unclear, introduce a reader-session ViewModel instead of expanding the
exception. Reusable visual controls and surfaces always follow the `widgets/`
rule.

This structure is enforced for feature-root visual components by the architecture
guard. Existing feature code should converge to it as touched; new code must not
introduce another layout convention.

### 9.2 Promotion rule

Use this flow:

```text
feature widget
    │
    │ reused by multiple real features
    ▼
core/ui pattern
    │
    │ becomes domain-independent visual primitive
    ▼
core/ui component
```

Do not promote code only because a second hypothetical use can be imagined.

---

## 10. Media-specific UI policy

Hikari must preserve the different interaction models of its media types.

### Anime/video

Optimize for:

- playback
- resume
- timeline/state
- cinematic presentation
- distraction-free controls

### Manga

Optimize for:

- artwork
- chapter navigation
- image reading
- reading direction/zoom behavior
- minimal reader chrome

### Light novel/publication

Optimize for:

- typography
- long-form reading comfort
- section/chapter navigation
- reading position
- configurable reading preferences

Shared visual primitives are encouraged.

Shared workflow widgets that erase meaningful differences between media types
are not.

---

## 11. Navigation shell

App-level navigation belongs at the app/presentation composition level, not
inside a source implementation.

The shell provides these destinations in the same order on compact and wide layouts:

```text
Home
Search
Local
Library
Settings
```

Local restores and scans the saved folder without opening a picker. Folder
selection is explicit in Local or Settings; Home does not own folder selection.
Folder selection from Settings refreshes Local's external-root revision; selection
from Local scans directly, avoiding duplicate scans. Source search is opened from
Catalog Detail as a fresh route, so its session-local Local catalog cache starts
fresh on each entry instead of sharing app-level invalidation state. Cancellation
keeps the current catalog. Destination pages are keyed by `AppTab`, with runtime
completeness validation rather than positional ordering. Local uses the shared
media-opening and Library contracts; classification and archive handling remain in
the existing source layer.

Features should request navigation through normal presentation composition rather
than reaching into infrastructure or source-specific objects.

Responsive navigation may use different presentation controls on compact and
wide layouts while keeping the same destinations and state model.

---

## 12. Loading, empty, error, and unavailable states

These are first-class UX states.

Every async screen should deliberately cover:

```text
initial
loading
content
empty
recoverable error
unavailable capability/source
```

Shared patterns may provide consistent visual treatment, but feature code owns
the recovery action.

Examples:

- retry source query
- reselect local folder
- install/enable an extension
- return to library
- reload a chapter

Do not reduce all failures to one generic error screen.

### 12.1 Refresh policy

Refresh is a content behavior, not a decoration to add to every route. A route is
manually refreshable only when the user can reasonably expect its already displayed
data to become stale without changing the query or navigation target.

Use pull-to-refresh as a convenience on scrollable collections where new or updated
content is naturally discovered from the top, and keep an explicit refresh action for
keyboard/desktop use and accessibility. Use Flutter's adaptive refresh indicator rather
than implementing a custom drag gesture.

Current policy:

```text
Home discovery/resume feed       pull + explicit refresh
Local media collection           pull + explicit rescan
Remote manga/novel series        pull + explicit refresh
Catalog detail metadata          explicit refresh only
Library                          automatic via observable repository
Catalog/source search            query submit/retry, no pull refresh
Reader/player/settings           no generic refresh gesture
```

Initial load and refresh are distinct states:

```text
no data + load        -> blocking loading state
content + refresh     -> keep stale content visible + refresh progress
refresh succeeds      -> replace with fresh content
refresh fails         -> keep stale content + recoverable refresh notice
no data + load fails  -> blocking error state
```

Do not replace usable content with a full-screen spinner or error solely because a
refresh is in progress or failed. A newly selected data root is different: when the
identity of the underlying collection changes (for example choosing a different Local
folder), stale content from the old root must not be shown as if it belonged to the new
one.

Search is deliberately excluded from pull-to-refresh because changing/submitting the
query is the user's data request. Library is deliberately excluded while it observes its
local source of truth and therefore updates without manual intervention.

Refresh interaction references:

- [Flutter `RefreshIndicator`](https://api.flutter.dev/flutter/material/RefreshIndicator-class.html)
- [Android pull-to-refresh guidance](https://developer.android.com/develop/ui/compose/components/pull-to-refresh)
- [Apple Human Interface Guidelines: progress and refresh indicators](https://developer.apple.com/design/human-interface-guidelines/progress-indicators)

---

## 13. Accessibility

Shared UI should make accessibility the default rather than a feature-specific
afterthought.

At minimum:

- preserve adequate touch targets;
- provide semantic labels for icon-only controls;
- avoid conveying state by color alone;
- support text scaling where practical;
- maintain readable contrast;
- respect reduced-motion preferences where supported;
- maintain keyboard/focus behavior for desktop UI.

Reader accessibility may require dedicated preferences beyond the global app
theme.

---

## 14. Preview and visual development

Shared UI should be inspectable without navigating through the full application.

Use Flutter widget preview tooling or lightweight preview entry points for
important shared components and patterns.

Prioritize previews for:

```text
theme surfaces
buttons
chips
navigation controls
media poster/card patterns
loading/empty/error states
```

Do not require a preview file for every trivial widget.

A standalone `hikari_ui` package or catalog application is not required now.

Consider extracting one only when `core/ui` becomes large enough or multiple
clients need to consume the same design system.

---

## 15. Testing strategy

UI tests should focus on behavior and stable semantics.

### Theme and primitive tests

Test important contracts such as:

- light/dark theme construction;
- required semantic colors;
- accessibility labels;
- critical interaction behavior.

Avoid brittle pixel-level assertions for ordinary layout.

### Feature widget tests

Test:

- user-visible states;
- navigation triggers;
- retry actions;
- state transitions;
- important responsive branches.

### Golden tests

Golden tests are useful for a small set of visually important, stable surfaces.

Do not make every widget a golden test.

Use them where visual regression has meaningful product cost.

---

## 16. Dependency rules

The UI layer must preserve the existing architecture boundaries.

Allowed:

```text
feature UI
  → application/domain contracts
  → core/ui
  → app/theme
```

Not allowed:

```text
app/theme → feature
core/ui/components → feature
core/ui/components → infrastructure
feature UI → concrete persistence implementation
feature UI → Android channel implementation
```

A presentation widget may depend on a domain/application abstraction when it
represents a real product concept.

A visual primitive must remain domain-independent.

---

## 17. Naming rules

Prefer names that describe role rather than implementation.

Good:

```text
MediaPoster
ContinueReadingSection
ReaderToolbar
HikariSectionHeader
```

Avoid generic buckets:

```text
CommonWidget
Utils
Helpers
SharedThing
BaseCard
```

Avoid naming a shared abstraction after the feature from which it was first
extracted unless the concept is genuinely feature-specific.

---

## 18. Anti-patterns

Do not introduce the following without a demonstrated need.

### 18.1 Mega configurable media widget

Avoid:

```dart
MediaCard(
  mangaMode: true,
  showEpisode: false,
  showChapter: true,
  compact: true,
  searchMode: false,
  ...
)
```

Prefer feature composition from smaller primitives.

### 18.2 Hard-coded design values throughout features

Avoid repeated:

```dart
EdgeInsets.all(17)
Color(0xFF...)
BorderRadius.circular(13)
```

when they represent a shared product rule.

### 18.3 Premature shared abstractions

Do not move a widget to `core/ui` simply because reuse seems possible.

### 18.4 Design-system package too early

Do not create a separate Flutter package only to make the folder structure look
more modular.

### 18.5 Feature logic in theme/components

Theme and primitive components must not know how to:

- query sources;
- load chapters;
- persist progress;
- select local storage;
- install extensions;
- navigate media workflows.

### 18.6 Styling by inheritance trees

Prefer composition and Flutter theme mechanisms over deep custom base-widget
inheritance hierarchies.

---

## 19. Foundation status and active rollout

The initial UI foundation is now present in the repository: semantic theme
foundations, shared primitives/patterns, responsive navigation and real feature
screens all exercise this architecture.

The project is therefore no longer in the “design the foundation first” stage.
The active priority is to complete the existing product journeys before adding
the next major feature set.

Detailed implementation sequencing, pass-level acceptance criteria and the
phase exit gate live in
[`../roadmap/UI_UX_COMPLETION.md`](../roadmap/UI_UX_COMPLETION.md).

Keep this document as the stable architectural authority. Do not copy live
roadmap status here; update the roadmap as screen-by-screen work progresses.

---

## 20. Definition of done for shared UI

Before promoting a component or pattern into shared UI, verify:

- it has at least one clear stable responsibility;
- the abstraction is used by real screens;
- feature-specific workflow logic has been removed;
- styling comes from semantic theme/tokens where appropriate;
- naming describes the UI role;
- Android/desktop behavior is considered;
- accessibility is reasonable;
- tests cover important behavior;
- no unnecessary dependency is added.

If these conditions are not met, keep the widget inside its feature.

---

## 21. Current architectural decision

For the current Hikari scale:

```text
app/theme
+
core/ui
+
feature-owned widgets
```

is preferred over a standalone design-system package.

This keeps the UI architecture explicit without introducing package boundaries,
code generation, token tooling, or abstraction layers before they provide real
value.

The architecture may be revisited when one of these becomes true:

- `core/ui` becomes difficult to navigate or maintain;
- multiple Hikari clients need the same UI system;
- token generation from an external design source becomes necessary;
- independent design-system versioning becomes useful.

Until then, keep the system local, small, semantic, and feature-driven.

---

## 22. Reference patterns

This architecture intentionally adopts ideas proven in larger projects while
keeping only the parts that fit Hikari's current scale:

- Flutter architecture samples: shared theme/UI outside feature-specific screens.
- Flutter internationalization: generated ARB-based localization and locale-aware
  formatting for user-facing copy.
- Now in Android: clear separation between design-system primitives and reusable
  product UI.
- Wonderous: explicit app-wide spacing, radius, typography, motion, and size
  foundations.
- AppFlowy: semantic tokens layered over primitive palette values.
- Immich: reusable UI catalog/preview workflow once shared components become
  significant.

These are reference patterns, not templates to copy mechanically.

Hikari should continue to prefer the simplest architecture that preserves real
product boundaries.
