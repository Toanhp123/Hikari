# Hikari UI/UX Consolidated Audit

Status: Final reviewed audit for the `08a091e` baseline  
Branch: `feat/ui-ux-hardening`  
Merge base: `8498679` (`dev`)  
Branch delta: 13 commits

## 1. Final verdict

The UI/UX direction is fundamentally sound.

Strong foundations already exist:

- Material 3 semantic `ColorScheme` usage in the target design system;
- a narrow warning/info `ThemeExtension` instead of duplicating standard Material roles;
- explicit spacing, geometry, typography, layout, reading, and motion contracts;
- reduced-motion-aware app-authored animation timing;
- sensible Home ownership through `HomeViewModel` rather than UI-local data logic;
- a clear Home hierarchy built around artwork, continuation, discovery, and recent content;
- shared poster/title separation that improves information hierarchy;
- targeted tests and design-system guards.

The branch is **not yet one coherent app-wide design system**. The current implementation is a migration state: the app root still owns the legacy theme while Home, SearchBar, and navigation reconstruct target themes locally. Shared descendants can silently fall back to legacy colors, appearance state is partially reconstructed from rendered theme output, and some responsive/accessibility contracts are still geometry-driven.

**Decision:** pause additional visual expansion. Correct the foundation seams first, then continue migration.

## 2. Evidence basis

The original audit verified:

- `fvm flutter analyze --no-pub` - no issues;
- focused design-system/navigation/Home/component/architecture tests - 63 passed;
- `fvm flutter test --no-pub` - 516 passed;
- `git diff --check dev...HEAD` - clean;
- main checkout remained clean.

Those results confirm tested behavior only. They do not certify OLED propagation, live resize state retention, screen-reader output, nonlinear text scaling, or frame performance.

A second source review re-checked the important findings against:

- `lib/app/app.dart`;
- `lib/app/navigation/app_navigation_shell.dart`;
- `lib/app/theme/hikari_theme.dart`;
- `lib/app/theme/design_system/*`;
- Home widgets and `HomePage`;
- `MediaPoster`, `MediaProgressBar`, `AsyncStateView`, `HikariButton`;
- Settings appearance UI;
- navigation/design-system tests;
- current design-system/ADR documentation.

## 3. Current architecture

```text
HikariApp
└─ MaterialApp
   └─ legacy HikariTheme.darkTheme(oled, accentColor)
      └─ AppNavigationShell
         ├─ locally reconstructed HikariDesignTheme for navigation chrome
         └─ lazily mounted tab pages
            ├─ HomePage
            │  └─ locally reconstructed HikariDesignTheme
            │     └─ migrated Home widgets + shared legacy-dependent primitives
            ├─ Catalog
            │  └─ target-scoped HikariSearchBar inside otherwise legacy UI
            ├─ Local Media - legacy presentation
            ├─ Library - legacy presentation
            └─ Settings - legacy presentation / appearance controls
```

The feature/application ownership underneath this presentation split remains appropriate. No global state rewrite is justified.

## 4. Priority findings

| Priority | Finding | Classification | Action |
|---|---|---|---|
| P0 | Responsive shell can remount visited tab content when crossing 600dp | Confirmed defect | Fix before more UI migration |
| P0/P1 | Appearance configuration is reconstructed from rendered theme output; OLED is dropped | Confirmed defect / architecture debt | Establish explicit appearance inputs |
| P1 | Migrated Home can silently consume legacy fallback colors through shared primitives | Architectural debt | Migrate shared consumers |
| P1 | Legacy `HikariButton` lacks canonical native focus/keyboard/state behavior | Accessibility / interaction debt | Replace or narrow its role during shared-primitives migration |
| P1 | Media badge foreground/background roles are inverted in some Home paths | Confirmed accessibility defect | Fix paired colors |
| P1 | Child Home components classify layout using app window width instead of local constraints | Consistency/responsive defect | Use local constraints |
| P1 | Fixed `bottomBarClearance` represents runtime obstruction as a design token | Architectural debt | Give obstruction one runtime owner |
| P1/P2 | Large/nonlinear text contracts are partly fixed-geometry-driven | Accessibility risk | Add targeted tests, then correct measured failures |
| P2 | Wide custom rail does not expose selected state as clearly as compact navigation | Accessibility risk | Fix immediately; later evaluate native rail |
| P2 | Blur/compositing and hero decode sizing may be costly | Performance risk - needs profiling | Measure before changing visuals |
| P2 | Settings accent controls are undersized and selection logic is tied to rendered primary | Migration/accessibility debt | Fix when Settings migrates |
| P3 | Documentation overstates adoption completeness | Documentation drift | Update after correction scope is agreed |
| P3 | Repeated local typography/poster sizing decisions weaken single authority | Consistency debt | Clean up during affected migrations only |

## 5. Confirmed defects and high-value debts

### 5.1 Responsive shell state preservation

Compact layout places the `IndexedStack` directly under `Scaffold.body`; wide layout inserts a `Row` and `Expanded` above it. Crossing the compact/medium boundary therefore changes element ancestry around the page host.

The existing tests instantiate narrow and wide shells independently. They do not resize a single mounted shell and verify that already visited page state, scroll position, text input, ViewModel lifetime, or subscription lifetime survive.

**Required correction:** keep one stable content-host structure while changing only surrounding navigation chrome.

Do not introduce `GlobalKey` reparenting unless a stable layout structure proves impossible.

### 5.2 Appearance source of truth: OLED and accent seed

`HikariApp` owns `_isOled` and `_accentColor`, but migrated subtrees do not receive those values directly. They read `Theme.of(context).colorScheme.primary` and use that rendered primary as a new `ColorScheme.fromSeed` input.

This creates two problems:

1. `oled` is not forwarded, so Home/navigation can remain non-OLED when the root toggles OLED.
2. the selected/raw seed is lost and replaced with an already generated primary tone.

There is also a default-seed mismatch:

- legacy default primary: `#8B5CF6`;
- target design-system default seed: `#B4BEFE`.

Because Home/navigation always derive a seed from the legacy root primary, the target default palette can be bypassed even when the user has never selected a custom accent.

**Architectural rule:** appearance configuration must be explicit input state, not inferred by reverse-engineering the current rendered `ThemeData`.

Temporary scoped migration may accept explicit `{oled, accentSeed}`. Final state should use one root target theme and eliminate local reconstruction.

### 5.3 Legacy fallback inside migrated Home

`MediaProgressBar`, `AsyncStateView`, and `HikariButton` still consume `context.hikariColors`. The target theme does not provide `HikariColors`; the accessor silently falls back to `const HikariColors.dark()`.

Therefore migrated Home can show target colors in primary content while progress, error, retry, or shared states silently render using legacy/default colors.

**Required correction:** migrate shared descendants to standard `ColorScheme`, `TextTheme`, native component themes, and only the narrow target extensions that are actually needed.

Do not make permanent dual-theme compatibility by installing legacy extensions into the target theme.

### 5.4 `HikariButton` is also an interaction/accessibility seam

The existing custom button is implemented with `GestureDetector`, container decoration, and a custom press animation. It does not inherit the complete native Material button contract for keyboard activation, focus behavior, state overlays, and semantics in the same way as `FilledButton`, `OutlinedButton`, and `TextButton`.

This matters because migrated Home error states can still reach `HikariButton` through `AsyncStateView`.

The design-system contract already declares native Material actions as canonical. Migration should therefore prefer native actions unless a real Hikari-specific behavior remains that cannot be expressed by component theming/style.

This is not a call to replace every custom control immediately. It is a call to remove `HikariButton` from the canonical migrated action path.

### 5.5 Badge color semantics

Some Home category paths use `onWarningContainer` / `onInfoContainer` as badge fill colors, while `_MediaBadge` forces white text. That uses foreground semantic roles as backgrounds and produces insufficient contrast for small text.

**Required correction:** consume paired roles:

```text
warningContainer + onWarningContainer
infoContainer + onInfoContainer
```

If a reusable badge accepts arbitrary fill colors, its foreground contract must be explicit rather than hardcoded white.

Do not create a media-category `ThemeExtension` yet; category labels already carry meaning and current reuse does not justify another global color vocabulary.

### 5.6 Local constraints versus full window width

The navigation shell may classify based on app/window width. Child components should not.

Hero, Continue shelf, recent shelf, and catalog shelf decisions currently use `MediaQuery.sizeOf(context).width` even after a navigation rail, content max width, or parent padding has consumed space.

This can choose a noncompact component variant even when the component itself receives compact-like width.

**Required correction:** use local bounded constraints (`LayoutBuilder`) for component variants. Do not teach child widgets to subtract rail width manually.

### 5.7 Bottom obstruction ownership

`HikariLayout.bottomBarClearance = 96` is a global design token, while the actual compact bar is 72dp plus runtime safe-area behavior. Home consumes the 96dp spacer even in rail mode.

Navigation obstruction is runtime layout state, not spacing vocabulary.

**Required correction:** define one obstruction owner and let scrollable content consume the effective inset once. Preserve intentional end-of-list breathing room separately from navigation avoidance.

### 5.8 Text scaling

Several visual components still reserve space through fixed heights or hand-derived aggregate text heights. The clearest example is poster text reserve using `textScaler.scale(32.0)` as a proxy for multiple lines.

The concern is valid, but not every fixed shelf height is already proven to overflow: some artwork can shrink first. Therefore the correct classification is **high accessibility risk, requiring targeted reproduction** rather than universal confirmed overflow.

Test at 100%, 200%, Android nonlinear scaling, long titles, and stress scales. Correct actual failures without clamping user text scale.

### 5.9 Wide rail selected semantics

Compact navigation exposes selected semantics explicitly. The wide rail changes visual state but does not provide an equally explicit selected-state contract.

Fix the selected semantics regardless of whether custom navigation ultimately survives.

### 5.10 Performance

Two mechanisms deserve measurement:

- backdrop blur in the pinned Home header and compact navigation;
- hero images without explicit decode-size hints.

No frame-drop or memory regression has been demonstrated. Do not delete glass effects or add an image-cache dependency from source inspection alone.

Profile cold/warm Home, sustained scrolling under blur, hero swiping, tab switching, image memory, and Windows resizing. Compare current glass against an opaque control variant before making a visual tradeoff.

## 6. Findings intentionally deferred

### Settings

Settings accent swatches are 36x36 and share the same semantic label. Selection compares the rendered legacy primary against seed colors. This becomes especially fragile once `ColorScheme.fromSeed` is authoritative.

Fix when Settings joins target-theme migration; it should not block the initial shell/Home correction pass.

### Typography/poster duplication

Some Home widgets locally override weight/tracking and poster sizing while the design system already defines semantic typography/layout rules. This is real consistency debt but low-value as a standalone cleanup. Resolve only while touching affected components for higher-priority work.

### Documentation

The design-system docs and ADR-era language no longer match current adoption. Update them once the next implementation boundary is agreed so documentation describes the corrected architecture, not another transient state.

## 7. Corrections to the original audit recommendation

### Native navigation is not yet a mandatory decision

The original audit recommended replacing the custom destinations with `NavigationBar` / `NavigationRail`.

That is a strong candidate, not a proven requirement.

The confirmed problems are:

- responsive page-host structure;
- selected semantics on wide navigation;
- scaling/focus/interaction burden in custom chrome;
- duplicated custom rendering;
- unmeasured blur cost.

Those problems can exist independently of the visual implementation choice.

After correctness work, run a focused native-navigation spike. Adopt native navigation only if it preserves Hikari's desired visual hierarchy with less custom code and better platform behavior. If reproducing the design requires extensive wrappers/custom drawing that erase the benefit, a corrected custom implementation remains defensible.

### Flattening `design_system/` is optional

The final architecture requires **one theme authority**, not a particular folder shape. Both are valid if ownership is clear:

```text
app/theme/hikari_theme.dart
app/theme/design_system/*
```

or a flatter `app/theme/*` structure.

Do not rename/move files merely to remove the word `design_system`.

### Reading policy ownership remains open

Reader prose/canvas policy must remain independent from application chrome. Whether shared reading metrics/palettes live under app theme or reader presentation should be decided from actual multi-reader ownership, not from a desire to make the folder tree look pure.

## 8. What should remain unchanged

- `HomeViewModel` ownership and current state-management style.
- Constructor injection and application-layer operations.
- Catalog metadata versus playable/readable source separation.
- Manual Home hero; no autoplay.
- Artwork/title separation.
- Recently added as a Home preview shelf rather than a second full Library view.
- Stale-data and partial-failure behavior.
- Reader/player domain and lifecycle separation.
- No speculative global card/shelf/component DSL.
- No performance rewrite without profile evidence.

## 9. Final priority order

1. Stabilize the responsive page host.
2. Make appearance inputs explicit and correct OLED/seed propagation.
3. Migrate the shared legacy consumers reached by Home.
4. Correct badge pairing and migrated action behavior.
5. Make Home components classify from local constraints and consume one bottom-obstruction contract.
6. Add targeted large/nonlinear-text, semantics, keyboard/focus, and breakpoint tests.
7. Compare native versus corrected custom navigation.
8. Complete root-theme migration only after shared consumers are ready.
9. Profile blur/image behavior and tune only measured problems.
10. Consolidate names/docs after the architecture is no longer transitional.

## 10. Reference sources

Primary framework references used by the audit include Flutter `ThemeData`, `ColorScheme`, `ThemeExtension`, `NavigationBar`, `NavigationRail`, `LayoutBuilder`, widget key/element reconciliation, `Scaffold.extendBody`, nonlinear text scaling, accessibility, `BackdropFilter`, network image decode sizing, performance best practices, and DevTools performance guidance.

The audit also inspected pinned Mihon and ReDantotsu source only for narrow ownership lessons; those projects are not treated as architecture authorities for Hikari.
