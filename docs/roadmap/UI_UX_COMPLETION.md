# UI/UX Completion Roadmap

> Status: **Active execution roadmap**
>
> Scope: polish and complete the existing Hikari product experience before the next major feature-expansion phase.
>
> Architectural authority: [`../architecture/UI_ARCHITECTURE.md`](../architecture/UI_ARCHITECTURE.md). This document owns sequencing, acceptance criteria, and current implementation priorities; it must not redefine presentation-layer boundaries.

## 1. Why this phase exists

Hikari already has enough end-to-end product capability to validate the user experience instead of adding another large subsystem first:

- local video, manga/archive, text and EPUB paths;
- remote manga through the Mihon-compatible extension runtime;
- remote novel through the bounded LNReader-compatible runtime;
- unified search;
- Library and persisted progress/resume;
- Home, Local, Library, Search and Settings destinations;
- manga, novel/publication and video consumption surfaces;
- an existing semantic theme, shared UI primitives, product patterns and responsive foundations.

The next priority is therefore **UI/UX completion**, not feature breadth.

The purpose of this phase is to exercise the existing architecture through the full product journey, remove prototype-level inconsistencies, and establish stable interaction patterns that future features can reuse.

The target journey is:

```text
launch
  ↓
Home / Search / Local / Library
  ↓
find or resume content
  ↓
Details
  ↓
Watch / Read
  ↓
progress is persisted
  ↓
return and resume predictably
```

A new feature should not be added merely because it is easy to attach to the current architecture. During this phase, feature work is justified only when it is required to complete or repair one of the existing journeys above.

---

## 2. Phase goal

At the end of this roadmap, a first-time user should be able to:

1. understand the primary destinations without prior knowledge;
2. discover remote manga/novel or local content;
3. distinguish content type and source where that distinction matters;
4. open a useful details surface before consuming remote content;
5. start reading/watching with predictable navigation;
6. leave and return without losing important state or progress;
7. recover from loading, empty, unavailable and recoverable-error states;
8. use the same core flow comfortably on compact Android and wider desktop layouts.

The product should no longer feel like a set of working vertical demos joined together. It should feel like one application with several media experiences.

---

## 3. Product rules for this phase

### 3.1 UX behavior before decoration

For every screen, settle these before visual polish:

```text
entry path
information hierarchy
primary action
secondary actions
loading behavior
empty behavior
error/retry behavior
back behavior
state restoration
resume behavior
compact/wide adaptation
keyboard/focus behavior where relevant
```

Do not hide an unclear flow behind richer gradients, animation or artwork.

### 3.2 Reuse the existing UI architecture

New UI must follow [`../architecture/UI_ARCHITECTURE.md`](../architecture/UI_ARCHITECTURE.md):

```text
app/theme
    ↓
core/ui/components
    ↓
core/ui/patterns
    ↓
features/*/widgets
    ↓
feature pages
```

Feature work should consume semantic tokens and existing shared patterns first. Promote a feature widget only after real reuse proves a stable shared abstraction.

### 3.3 Keep media experiences distinct where they should be distinct

Consistency means matching concepts behave consistently; it does not mean making every media type look or interact identically.

- Video remains playback-centric.
- Manga remains image/gesture-centric.
- Novel/publication remains typography/long-form-reading-centric.

Global chrome, asynchronous states, navigation semantics, metadata presentation and Library/progress language should be consistent wherever the same concept exists.

### 3.4 Preserve architecture while polishing

UI work must not bypass the existing application/domain boundaries for convenience.

Do not:

- call concrete source/runtime implementations directly from feature UI;
- move workflow logic into shared visual components;
- add provider-specific branching to generic search/details/library UI;
- duplicate progress or Library rules inside pages;
- build a new generic UI framework in parallel with `app/theme` and `core/ui`.

### 3.5 Prefer vertical completion over broad restyling

Finish one user-visible journey at a time. A pass is complete only when behavior, states, responsive layout and tests are coherent enough to keep as a checkpoint.

Avoid a repository-wide “make everything prettier” change that leaves interaction problems unresolved.

---

## 4. Scope and deferred work

### In scope

- navigation shell and destination behavior;
- visual hierarchy and interaction clarity;
- Home, Search, remote Details, readers/player, Library, Local and Settings;
- semantic loading/empty/error/unavailable states;
- consistent progress and resume affordances;
- responsive behavior for current Android/Windows targets;
- accessibility and desktop focus/keyboard basics;
- reader comfort controls already supported by existing engines/contracts;
- visual/interaction cleanup of existing capabilities;
- small architecture-safe fixes required to make the current journeys coherent.

### Deferred unless required by an existing UX flow

- plugin/extension marketplace or manager;
- new remote anime/video provider system;
- accounts/cloud sync;
- tracker integrations;
- recommendation engine;
- download subsystem expansion;
- social/community features;
- new metadata aggregation layer;
- large identity/domain redesign without a demonstrated current requirement;
- a standalone design-system package.

Deferred does not mean rejected. It means the current product should first prove that its existing capabilities form a coherent experience.

---

## 5. Execution order

Use the order below as the default. A later pass may fix a blocker discovered earlier, but do not start several broad redesigns in parallel.

```text
0. Cross-app UX contract and baseline
        ↓
1. Home
        ↓
2. Search
        ↓
3. Details
        ↓
4. Readers / Player
        ↓
5. Library
        ↓
6. Local
        ↓
7. Settings
        ↓
8. Cross-app hardening
```

Each pass should end in a buildable/testable checkpoint before the next broad pass begins.

---

## 6. Pass 0 — Cross-app UX contract and baseline

This is not a redesign pass. It establishes the rules that later screens reuse.

### Required outcomes

- Confirm semantic color, typography, spacing, radius, motion and breakpoint usage already present in `app/theme`.
- Inventory existing `core/ui/components` and `core/ui/patterns` before adding another shared abstraction.
- Define one consistent treatment for:
  - page titles and top-level actions;
  - search input;
  - buttons and icon-only actions;
  - section headers;
  - media artwork/posters;
  - progress indication;
  - loading;
  - empty state;
  - recoverable error;
  - unavailable source/capability.
- Confirm compact vs wide navigation behavior keeps destination identity and state stable.
- Confirm tap targets, semantic labels, text scaling and desktop focus behavior for shared controls.

### Exit criteria

- No new feature pass needs to invent a second global visual language.
- Shared abstractions remain small and evidence-driven.
- Any inconsistency intentionally retained has a product reason, not accidental styling drift.

---

## 7. Pass 1 — Home

Home is the first product-level proof of hierarchy and resume behavior.

### UX goals

The user should immediately understand:

- what can be resumed;
- how to search;
- how to open local content;
- how to reach Library;
- which content or sections deserve attention now.

### Required checks

- “Continue” content has stronger priority than decorative discovery content when real progress exists.
- Empty Home remains useful rather than looking broken on a fresh install.
- Resume actions open the expected reader/player path through the existing shared open workflow.
- Hero/shelf presentation does not duplicate the same content without purpose.
- Artwork loading/failure states are stable and do not cause disruptive layout shifts.
- Compact and wide layouts preserve hierarchy rather than simply stretching rows.

### Exit criteria

A fresh user and a returning user both have an obvious next action from Home.

---

## 8. Pass 2 — Search

Search should make the source system understandable without exposing implementation detail unnecessarily.

### UX goals

- one clear search entry point;
- manga and novel results are distinguishable;
- source selection/filtering is visible when useful;
- partial source failure does not make successful results unusable;
- loading and retry are scoped to the work that failed whenever possible.

### Required checks

- Query state survives ordinary navigation expected by the current shell/state model.
- Search does not require users to understand “Mihon” or “LNReader” as architecture concepts.
- Source names may be shown as content provenance where useful, not as implementation leakage.
- Empty query, no results, source unavailable and source error are different states.
- Result cards expose enough metadata to choose a result without becoming oversized details screens.
- Keyboard submit/focus behavior works on desktop.
- Stale asynchronous results cannot replace a newer query.

### Exit criteria

A user can search across the currently supported remote content types, understand the returned result, recover from source-specific failure, and proceed to Details without ambiguity.

---

## 9. Pass 3 — Details

Details is the bridge between discovery and consumption. Manga and novel pages may remain feature-owned, but matching concepts should use matching interaction language.

### UX goals

Make these immediately understandable where available:

```text
identity/artwork
metadata/description
source/provenance
Library state
action to start/resume
chapter/content list
current progress
```

### Required checks

- The primary start/resume action is visually clear.
- Add/remove Library state is visible and predictable.
- Chapter rows communicate enough identity and progress to choose safely.
- Long lists remain usable and do not make the header/action region hard to recover.
- Loading metadata and loading chapter/content lists can be represented independently when the workflow supports it.
- Retry does not discard already usable data unnecessarily.
- Manga and novel Details share patterns only where semantics are genuinely shared.

### Exit criteria

The user can answer “what is this?”, “where did it come from?”, “what can I read next?”, and “am I already following/reading it?” without guessing.

---

## 10. Pass 4 — Readers and player

Consumption surfaces prioritize focus and continuity over app chrome.

### Manga reader

Verify:

- page order and reading flow are obvious;
- loading/failure of an individual page does not unnecessarily destroy the whole reader session;
- zoom/gesture behavior does not conflict with navigation;
- chrome can stay out of the way and be recovered predictably;
- back behavior persists progress before leaving;
- orientation/compact/wide behavior remains usable.

### Novel/publication reader

Verify:

- readable content width;
- comfortable typography and line height;
- reliable chapter/position restoration;
- existing reading preferences are accessible without dominating the page;
- EPUB/local publication and remote novel interaction language is as consistent as their capabilities allow;
- selectable text and system accessibility remain practical.

### Video player

Verify:

- play/pause/seek and current position are easy to understand;
- controls disappear/reappear predictably;
- back/gesture exit preserves progress consistently;
- loading, playback error and end-of-media states are distinct;
- app chrome does not compete with playback controls.

### Exit criteria

A user can enter, consume, leave and resume each currently supported media path without losing orientation or important progress.

---

## 11. Pass 5 — Library

Library should feel like persistent user-owned state, not another provider result list.

### UX goals

- clearly distinguish saved content from transient search results;
- expose useful resume/progress context;
- make empty Library actionable;
- keep source provenance secondary to the user's saved item unless it is needed to resolve/open it.

### Required checks

- Add/remove actions elsewhere are reflected predictably here.
- Opening a saved item uses shared application/open paths rather than duplicated feature logic.
- Missing/unavailable source behavior explains what can and cannot still be done.
- Sorting/filtering controls are not added until current content volume proves they are necessary.
- Cards/rows remain coherent across manga, novel and local media.

### Exit criteria

The user trusts Library as the stable place to return to saved content and resume it.

---

## 12. Pass 6 — Local

Local should make the persisted SAF-root model understandable without repeatedly forcing storage setup.

### UX goals

- saved root restores automatically;
- scan state and results are clear;
- choosing/reselecting a folder is an explicit action;
- cancellation does not destroy the current usable catalog;
- permission/root failure provides a direct recovery path;
- local manga/CBZ, text/EPUB and video entries communicate their media type clearly.

### Exit criteria

After the first successful folder selection, normal app reopen does not feel like setup repetition, and recovery from a lost permission/root is understandable.

---

## 13. Pass 7 — Settings

Settings should contain actual user-configurable product behavior, not become a bucket for unfinished features.

### Required checks

- Group settings by user intent, not internal subsystem names.
- Explain destructive/reselect actions before execution when necessary.
- Keep debug/developer information separate from normal user choices.
- Reader preferences live close to the reading experience when frequent adjustment is expected; Settings may provide defaults when a real requirement exists.
- Do not expose future toggles that have no implemented behavior.

### Exit criteria

Every visible setting has a clear effect the current product can demonstrate.

---

## 14. Pass 8 — Cross-app hardening

After the vertical passes are coherent, perform a final product-wide pass.

### Interaction consistency

Audit repeated concepts:

- start vs resume;
- add/remove Library;
- retry;
- dismiss/back;
- destructive actions;
- source unavailable;
- progress display;
- empty states;
- search/filter controls.

Matching concepts should use matching language, iconography and interaction expectations.

### Responsive/adaptive behavior

Verify at minimum:

- compact Android phone;
- wider Android/tablet-like width where practical;
- Windows desktop width;
- resize behavior on Windows.

Do not treat desktop as a stretched phone screen.

### Accessibility

Verify:

- minimum practical tap targets;
- semantics for icon-only controls and important artwork/actions;
- contrast and non-color state cues;
- text scaling on normal app surfaces;
- keyboard traversal/focus visibility on desktop;
- reduced-motion behavior where the current platform/framework path supports it.

### Performance perception

Focus on user-visible performance, not speculative micro-optimization:

- avoid unnecessary full-screen loading replacement when partial content can remain;
- avoid obvious repeated source work during rebuilds;
- avoid artwork/layout churn;
- preserve list/scroll state where users reasonably expect it;
- keep expensive reader/player work outside unrelated widget rebuild paths.

Measure/profile before introducing non-trivial optimization machinery.

### Visual regression

Use widget tests for behavior and a small number of golden tests only for stable, visually important surfaces where regression cost justifies them.

---

## 15. Per-screen definition of done

A screen/pass is not complete because its screenshot looks polished.

Before closing a pass, verify the relevant items:

```text
[ ] entry and exit paths are clear
[ ] one primary action is identifiable when the screen needs one
[ ] loading state is intentional
[ ] empty state is intentional
[ ] recoverable error has a real recovery action
[ ] unavailable capability/source is distinct from generic failure
[ ] back behavior is correct
[ ] important presentation state is restored as intended
[ ] progress/resume semantics use existing application/domain contracts
[ ] stale async results cannot overwrite newer state
[ ] semantic theme/tokens are used instead of repeated raw styling
[ ] shared components are reused only where responsibilities match
[ ] compact layout is checked
[ ] wide/desktop layout is checked when affected
[ ] accessibility basics are checked
[ ] important behavior has widget/unit coverage
[ ] architecture guard remains green
[ ] analyzer/test/diff gates pass
```

For platform-affecting UI work, also run the relevant build/run smoke check required by [`../GIT_WORKFLOW.md`](../GIT_WORKFLOW.md).

---

## 16. Working method for each pass

Use this loop:

```text
inspect current screen + state model
        ↓
identify concrete UX problems
        ↓
define behavior/state hierarchy
        ↓
reuse existing theme/components/patterns
        ↓
implement the smallest coherent vertical change
        ↓
widget/unit tests
        ↓
responsive + accessibility review
        ↓
quality gates + diff review
        ↓
checkpoint commit
```

For non-trivial UI work, follow [`../AGENT_WORKFLOW.md`](../AGENT_WORKFLOW.md), including UI UX Pro Max where relevant. External reference research should inform decisions, not override Hikari's established architecture.

Do not start by creating abstractions. Start from the user-visible problem and current code, then extract only what the implementation proves should be shared.

---

## 17. Exit gate for the UI/UX phase

This roadmap is complete when all of the following are true:

1. The primary journey works coherently:

   ```text
   Home/Search/Local/Library
       → Details when applicable
       → Reader/Player
       → progress saved
       → return/resume
   ```

2. No primary screen relies on placeholder/prototype interaction for its normal happy path.
3. Loading, empty, recoverable error and unavailable-source states are intentionally handled on the primary async surfaces.
4. Common concepts use consistent language and visual treatment across features.
5. Manga, novel and video retain appropriate specialized consumption UX.
6. Compact Android and Windows desktop layouts are both usable for the affected primary flows.
7. Shared UI remains small, semantic and architecture-compliant rather than becoming a second framework.
8. Current automated quality gates remain green and important UI behavior has regression coverage.

Only after this gate should the project deliberately choose the next major feature-expansion phase.

---

## 18. How to maintain this roadmap

This is an active roadmap, so update it as implementation reveals better sequencing or a requirement changes.

- Keep stable UI rules in [`../architecture/UI_ARCHITECTURE.md`](../architecture/UI_ARCHITECTURE.md), not here.
- Keep accepted expensive-to-reverse technical decisions in ADRs.
- Keep product scope and long-lived intent in [`../PROJECT_OVERVIEW.md`](../PROJECT_OVERVIEW.md).
- Record only active sequencing, completion criteria and phase-specific constraints here.
- Remove or archive this roadmap when the phase is complete and its durable knowledge has moved to the appropriate canonical docs.
