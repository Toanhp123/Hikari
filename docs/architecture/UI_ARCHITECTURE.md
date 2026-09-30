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

- `textPrimary`
- `textSecondary`
- `surface`
- `surfaceContainer`
- `border`
- `error`

Features should not depend directly on arbitrary palette values such as
`grey700`, `blue500`, or one-off `Color(...)` literals.

This keeps dark mode, future OLED themes, accent customization, and visual
redesigns local to the theme layer.

### 2.5 Promote shared UI only after reuse is proven

A widget begins inside its feature unless it is clearly an app-wide primitive.

When a feature widget is reused by multiple features and its responsibilities are
stable, promote the reusable part into `core/ui`.

Do not build a large design-system package before the product requires one.

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
│  │  ├─ hikari_colors.dart
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
│     │  ├─ hikari_icon_button.dart
│     │  ├─ hikari_chip.dart
│     │  └─ hikari_scaffold.dart
│     │
│     └─ patterns/
│        ├─ media_poster.dart
│        ├─ media_progress.dart
│        ├─ empty_state.dart
│        ├─ error_state.dart
│        └─ loading_state.dart
│
└─ features/
   ├─ library/
   │  ├─ library_page.dart
   │  └─ widgets/
   │     ├─ continue_reading_section.dart
   │     └─ library_media_card.dart
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

### 6.2 Semantic tokens

Semantic tokens describe purpose:

```text
background
surface
surfaceContainer

textPrimary
textSecondary
textMuted

iconPrimary
iconSecondary

border

primary
onPrimary

success
warning
error
```

Prefer:

```dart
color: context.hikariColors.textSecondary
```

over:

```dart
color: Colors.grey.shade600
```

or:

```dart
color: HikariPrimitiveColors.grey600
```

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
HikariScaffold
HikariSectionHeader
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
MediaProgress
AsyncStateView
EmptyState
ErrorState
LoadingState
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

### 9.1 Promotion rule

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

The shell should eventually provide consistent access to major product areas
such as:

```text
Home / Continue
Library
Search / Browse
Settings
```

The exact information architecture may evolve during UX work.

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

## 19. Foundation rollout

The first UX/UI foundation pass should remain intentionally small.

Recommended order:

```text
1. semantic colors and ThemeData
2. typography
3. spacing and radius
4. motion and breakpoints
5. core primitives required by current screens
6. common async/empty/error patterns
7. media poster/progress patterns
8. navigation shell
9. feature-by-feature redesign
```

Do not redesign every screen before the foundation has been exercised by at
least one real feature.

A good first proving ground is Home/Library because it exercises navigation,
media artwork, metadata, progress, loading states, and responsive layout without
touching the specialized reader/player interaction model.

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
