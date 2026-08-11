# Hikari Design System Architecture Design

Date: 2026-08-11
Status: APPROVED FOR IMPLEMENTATION

## Goal

Introduce one explicit application-wide UI foundation before Wave 10 so Hikari has a
single, enforceable source of truth for theme, visual tokens, generic shared Compose
primitives, and domain-neutral UX feedback/state conventions.

This architecture step is **not a screen UI/UX redesign**. Its purpose is to normalize the
presentation foundation and common UX behavior first, migrate existing presentation code
onto that foundation without intentionally changing screen layout, information hierarchy,
or product flows, and prepare the repository for a separate future screen-design pass.

The design system must improve consistency without weakening the capability ownership
rules established by Architecture Baseline 2 and the approved Wave 06-11 evolution.

## Decision

Add one production Android library module:

```text
:core:designsystem
```

This is a dedicated architecture decision that extends the approved post-Baseline module
graph. It is intentionally introduced between the verified Wave 09 boundary and Wave 10.
Wave 10 and Wave 11 otherwise retain their approved capability ownership and sequencing.

`:core:designsystem` owns Hikari's application-wide visual foundation and reusable generic
Compose primitives. It does not own feature state, navigation, business behavior, domain
models, persistence, networking, plugin execution, Android background work, or the visual
design of individual product screens.

## Scope

This architecture change includes exactly these concerns:

1. add and register `:core:designsystem` as an approved production module;
2. centralize the root Hikari Material 3 theme;
3. define application-wide visual tokens;
4. establish a minimal set of domain-neutral shared Compose primitives based on existing
   presentation needs;
5. standardize common UX presentation conventions for loading, empty, error, offline,
   transient feedback, confirmation, and destructive actions;
6. connect existing presentation modules to the new design-system foundation;
7. migrate existing UI usages where appropriate without intentionally redesigning Home,
   Story, Library, Reader, or any other screen;
8. update architecture policy, module graphs, documentation, and verification evidence.

Explicitly out of scope:

- redesigning Home;
- redesigning Story;
- redesigning Library;
- changing information hierarchy, navigation flows, or screen composition for visual
  reasons;
- introducing new feature behavior or domain capability;
- changing business rules in order to support a presentation convention;
- choosing a final premium visual direction for Hikari;
- broad UI polish unrelated to establishing the shared foundation.

Those concerns belong to a separate future UI/UX design specification after this
foundation is accepted and implemented.

## Ownership Boundary

### `:core:designsystem` owns

- `HikariTheme` and the root Material 3 theme configuration;
- application color roles and light/dark schemes;
- typography scale and text styles;
- shape/radius tokens;
- spacing and sizing tokens when they encode an application-wide rule;
- elevation/surface tokens when they represent a shared application rule;
- motion tokens only when a stable application-wide convention exists;
- application icon accessors when icons are truly shared;
- generic reusable controls that encode a stable Hikari presentation rule;
- generic reusable loading, empty, error, offline, and destructive-state presentation;
- application-wide transient-feedback presentation conventions, centered on Compose
  snackbar feedback rather than ad-hoc per-screen Toast usage;
- generic confirmation/destructive-confirmation presentation primitives;
- reusable preview helpers that are presentation-only and contain no domain models.

Examples of appropriate design-system primitives include:

```text
HikariButton
HikariIconButton
HikariChip
HikariTopBar
HikariNavigationBar
HikariDialog
HikariSheet
HikariLoadingState
HikariEmptyState
HikariErrorState
HikariOfflineState
HikariSnackbarHost
HikariConfirmDialog
```

These names are illustrative, not a requirement to wrap every Material component. An
abstraction exists only when it captures a stable Hikari-wide rule or removes repeated
presentation policy from feature code.

### `:core:designsystem` does not own

- `CanonicalStory`, Library, chapter, reader, download, settings, or plugin models;
- screen-specific UI state or ViewModels;
- domain/application error classification or exception mapping;
- retry policy, network policy, repository behavior, or operation orchestration;
- Home section composition or discovery policy;
- Story metadata behavior or chapter/release behavior;
- Library filtering, sorting, membership, or update behavior;
- reader document behavior;
- navigation routes or deep-link behavior;
- image repositories, catalog repositories, persistence, HTTP, plugin runtime, or workers;
- feature-specific components merely because multiple screens inside the same feature use
  them;
- screen-level visual concepts such as `StoryHero`, `LibraryStoryItem`, or `ChapterRow`.

The core rule is:

> `:core:designsystem` knows how Hikari expresses shared visual rules, but it does not know
> what a Story, Chapter, Library entry, Download, Setting, or Plugin means.

## Feature-Owned Presentation

`:feature:catalog` retains ownership of Home, Search, Story, Library, mapping, and chapter
presentation. Domain-aware reusable UI remains feature-owned.

Examples:

```text
HomeHero
HomeSection
StoryCard
StoryHero
StoryMetadata
ChapterRow
LibraryStoryItem
LibraryFilterBar
```

These components may consume design-system tokens and primitives, but their APIs and
behavior remain expressed in catalog/library/chapter semantics.

`:feature:reader` remains responsible for immersive reader presentation and may consume
`:core:designsystem` only for genuinely shared visual primitives. Reader-specific controls
and reader UX stay locally owned.

Wave 10 `:feature:settings` and Wave 11 `:feature:plugins` will consume the same design
system when introduced, without moving their feature-specific presentation into
`:core:designsystem`.

## Dependency Direction

The design system is a leaf UI foundation from the perspective of feature modules:

```text
                         :core:designsystem
                          ^      ^      ^
                          |      |      |
                :feature:catalog | :feature:reader
                                 |
                         future presentation
                       :feature:settings
                       :feature:plugins

:app -> :core:designsystem + feature/capability modules needed for composition
```

Allowed direct project dependencies for `:core:designsystem` should start at **none**.
The module should rely only on Android, Compose, Material 3, and resource/tooling libraries
unless a later reviewed requirement proves that a project dependency is necessary.

In particular, the design system must not depend on:

```text
:catalog
:library
:chapters
:reader
:downloads
:settings
:storage:room
:storage:files
:plugins:api
:plugins:runtime
:feature:*
:app
```

Feature presentation modules may depend on `:core:designsystem`; capability modules may
not.

The initial production dependency updates are expected to be:

```text
:app             -> + :core:designsystem
:feature:catalog -> + :core:designsystem
:feature:reader  -> + :core:designsystem only when current UI actually consumes it
```

Future presentation modules add the same dependency when needed:

```text
:feature:settings -> + :core:designsystem
:feature:plugins  -> + :core:designsystem
```

## Theme Ownership

Root theme ownership moves from ad-hoc `MaterialTheme` setup in `:app` to
`:core:designsystem`:

```kotlin
HikariTheme {
    AppNavHost(...)
}
```

`:app` owns application composition and chooses application-level theme inputs such as
light/dark mode. `:core:designsystem` owns how those inputs map to Hikari color roles,
typography, shapes, and other visual tokens.

Feature modules consume the theme through Compose composition locals and public
`:core:designsystem` APIs. They never depend back on `:app`.

The initial theme should normalize the existing product rather than invent a new screen
visual direction. A later UI/UX specification may revise token values while preserving the
same architecture and public ownership boundary.

## Token Strategy

Start small and encode only rules that should be consistent application-wide.

Required first-class token groups:

- color roles;
- typography;
- shapes;
- spacing.

Add other token groups only when current or future presentation code demonstrates a stable
shared rule:

- elevation/surface levels;
- motion durations/easing;
- standard content widths or responsive breakpoints;
- opacity/state-layer conventions;
- shared sizing/aspect-ratio rules.

Avoid tokenizing every literal merely to remove raw numbers. A token exists because the
value expresses a Hikari-wide design decision.

## Shared Component Strategy

Use three levels of presentation reuse:

```text
Compose / Material primitives
        -> Hikari generic design-system primitives
            -> feature-owned domain-aware components
                -> screens
```

Do not introduce wrappers such as `HikariRow`, `HikariColumn`, `HikariBox`, or
`HikariText` simply to create a project prefix. Standard Compose layout primitives remain
direct dependencies of feature presentation code.

A component belongs in `:core:designsystem` only when all of the following are true:

1. its contract is domain-neutral;
2. it expresses an intentionally shared visual or interaction rule;
3. moving it does not require importing capability/domain models;
4. the component remains meaningful without knowing which feature renders it.

The initial shared set must be derived from **existing repeated presentation needs**, not
from speculative future screens. If an existing Material primitive is already adequate and
there is no Hikari-specific rule to encode, continue using the Material primitive directly.


## Common UX System

This foundation also standardizes **domain-neutral UX presentation policy**. The goal is
not to redesign individual screens; it is to prevent each feature from inventing a
different loading, failure, empty-state, confirmation, or transient-feedback language.

The design system may define reusable presentation contracts/components for these classes:

```text
Loading
Empty
Error
Offline
Transient feedback
Confirmation
Destructive confirmation
Progress feedback
```

### Loading convention

Shared primitives should support consistent presentation patterns such as:

- initial content loading;
- refresh while preserving already-rendered content;
- pagination/progressive loading;
- action-local progress such as a button becoming busy;
- unknown-duration blocking progress only when the operation genuinely blocks the surface.

The design system owns **how** those states are presented generically. The feature owns
**when** a state applies and what operation is in progress.

### Empty-state convention

The common empty-state primitive should support, without encoding feature semantics:

- title;
- supporting message;
- optional icon/illustration slot;
- optional primary action;
- optional secondary action where justified.

Features remain responsible for distinguishing meanings such as true-empty, filtered-empty,
search-empty, setup-required, or offline-without-cache. `:core:designsystem` must not know
what a Library, Story, Search result, Plugin, or Setting is.

### Error convention

The design system standardizes presentation choices, not exception handling.

A feature may map its domain/application failure into a presentation semantic such as:

```text
full-surface failure
non-blocking failure
retryable action failure
offline-with-cache
offline-without-content
```

and then render the appropriate generic design-system surface or feedback primitive.

The dependency direction is:

```text
domain/application failure
        -> feature presentation mapping
            -> generic UX semantic
                -> :core:designsystem component
```

`:core:designsystem` must never import domain exceptions, repository errors, network
exceptions, plugin failures, persistence failures, or feature-specific error models.

### Transient feedback convention

Compose snackbar feedback is the default in-app transient-feedback mechanism when a message
belongs to the active application surface.

Examples include:

```text
operation succeeded
operation failed without invalidating the screen
retry is available
undo is available
background action started/completed
```

The foundation may standardize severity/presentation roles such as informational, success,
warning, and error where they create real shared behavior. It must avoid turning every
message into a custom visual variant without evidence.

Android `Toast` is not the primary Hikari in-app feedback system. Toast remains available
only for cases where platform integration, lifecycle constraints, or a reviewed use case
makes snackbar-style feedback inappropriate.

### Confirmation and destructive-action convention

The foundation should make common action safety consistent:

- avoid confirmation for harmless actions;
- prefer undo for reversible actions where practical;
- use confirmation for destructive actions when accidental activation has meaningful cost;
- use explicit destructive confirmation for irreversible/high-impact actions;
- make destructive styling and action placement consistent.

The feature owns the consequence, copy, eligibility, and operation. The design system owns
only the reusable presentation pattern.

### Behavioral boundary

This UX normalization may remove accidental inconsistency in existing screens, but it must
not introduce new product flows, gestures, navigation structure, domain behavior, or
screen-specific interaction models. Those require their own feature/UI-UX design decision.

## Existing UI Migration

After the module and theme exist, current presentation code should be connected to the
foundation with a behavior-preserving migration.

Allowed migration work includes:

- replacing the root ad-hoc `MaterialTheme` with `HikariTheme`;
- replacing duplicated application-wide color, typography, shape, or spacing rules with
  design-system tokens;
- replacing duplicated domain-neutral shared controls with approved design-system
  primitives;
- standardizing duplicated loading, empty, error, offline, snackbar/feedback, and
  confirmation presentation through domain-neutral primitives;
- removing ad-hoc in-app Toast usage where the same feedback belongs to the active Compose
  surface and is better represented by the shared snackbar system;
- moving truly global UI state presentation into domain-neutral shared components;
- removing feature-to-feature or feature-to-`:app` presentation sharing that the new module
  makes unnecessary.

Migration must preserve, as closely as practical:

- current screen structure;
- current navigation behavior;
- current information hierarchy;
- current feature actions and state flow;
- current domain ownership;
- current product behavior.

Visual or micro-interaction differences caused solely by normalized theme values,
consistent feedback/state presentation, accessibility defaults, or removal of accidental
inconsistency are acceptable. Deliberate screen redesign, information-hierarchy changes,
and new product flows are not part of this change.

## Accessibility Baseline

The foundation should make accessible defaults easier without turning this architecture
step into the final Wave 11 accessibility pass.

Shared primitives should establish sane defaults for:

- semantic labels/roles when the component can provide them generically;
- practical touch targets;
- light/dark contrast expectations;
- font-scale tolerance;
- non-color-only state communication where applicable;
- consistent loading/error/empty/offline-state semantics;
- consistent feedback semantics for status/error communication;
- consistent destructive-action affordances where the primitive can provide them
  generically.

Feature-specific accessibility behavior remains with the owning feature. Wave 11 remains
the final whole-app accessibility, localization, privacy, and destructive-UX hardening
gate.

## Verification Strategy

The module introduction and behavior-preserving migration should be reviewable separately.

### Architecture verification

When implementation begins, the module-addition change must follow
`docs/contributing/adding-a-module.md` and update at least:

1. `settings.gradle.kts`;
2. `core/designsystem/build.gradle.kts`;
3. `config/architecture/module-boundaries.json`;
4. README/current module graph documentation;
5. focused design-system tests;
6. architecture verification policy/checkpoint evidence.

The approved post-Baseline architecture design and roadmap must be updated in the same
reviewed architecture change so the new module is no longer an unapproved graph node.

### Foundation verification

Use focused tests appropriate to the shared layer:

- unit tests only for non-trivial token/helper behavior;
- Compose tests for shared components whose semantics or interaction behavior matters;
- focused tests for loading/empty/error/offline, snackbar action, and confirmation
  semantics where behavior is non-trivial;
- theme smoke tests for light/dark composition where useful;
- compile/test checks for every migrated presentation consumer;
- architecture checks proving no forbidden dependency was introduced.

A full screenshot/golden suite and visual redesign validation are outside this architecture
step unless the repository already requires them for regression protection.

## Migration Order

Implement in this order:

```text
architecture policy + empty :core:designsystem boundary
    -> HikariTheme + foundational tokens
    -> minimal shared visual primitives proven by existing UI needs
    -> common UX primitives/conventions for state, feedback, and confirmation
    -> :app root consumes HikariTheme and shared feedback host where appropriate
    -> migrate existing presentation consumers to shared tokens/primitives/conventions
    -> verify product behavior and dependency boundaries remain unchanged
    -> foundation complete
    -> Wave 10 / separate screen UI-UX design work according to project sequencing
```

No Home, Story, Library, Reader, Settings, or Plugin screen redesign is approved by this
specification.

## Rejected Alternatives

### Keep all global UI in `:app`

Rejected because feature modules cannot depend on `:app`; reusable components would either
be unavailable to features or create an invalid reverse/cyclic dependency direction.

### Put all shared UI in `:feature:catalog`

Rejected as the application-wide solution because Reader, Settings, and Plugin Management
have independent presentation boundaries. It would make those features depend on catalog
presentation semantics merely to share visual primitives.

### Introduce a broad `:core:ui`

Rejected because a generic UI bucket invites navigation, ViewModels, domain mappers,
repositories, and unrelated feature components to accumulate in one catch-all module.
The approved module is explicitly a **design system**, not a shared presentation layer.

### Add separate theme, icons, components, and resources modules now

Rejected as premature modularization. One focused `:core:designsystem` boundary is enough
until build performance, ownership, or independent reuse creates evidence for extraction.

### Redesign screens while introducing the foundation

Rejected for this architecture step because it mixes two review problems: whether the
shared UI boundary is correct and whether the new product design is correct. Keeping them
separate makes regressions, ownership changes, and visual decisions easier to reason about.

## Architecture Invariant

After this decision, the cross-wave architecture gains exactly one new shared presentation
foundation:

```text
:core:designsystem
```

No capability ownership moves into it. No capability module gains a Compose dependency.
Home/Story/Library remain `:feature:catalog` surfaces. Existing presentation modules may
consume the design system, but no screen-level product design is changed by architectural
fiat.

This module exists solely to make Hikari-wide visual rules and domain-neutral common UX
presentation conventions reusable, enforceable, and independent of `:app` or any unrelated
feature.
