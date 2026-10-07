# Hikari UI Foundation Target Architecture

Status: Target state after incremental migration  
Scope: theme ownership, shared UI, responsive layout, navigation shell, Home presentation

## 1. Core principle

Hikari should end with **one app-owned application theme**, composed primarily from Flutter Material semantics, plus a small number of Hikari-specific policies that solve demonstrated product needs.

The final system should be smaller than the migration system.

## 2. Appearance source of truth

Appearance state must remain explicit configuration owned by application composition.

Conceptually:

```dart
final class HikariAppearance {
  final bool oled;
  final Color? accentSeed;
}
```

A dedicated class is optional; the important contract is explicit raw values.

Do not infer appearance by reading a generated `ThemeData.colorScheme.primary` and feeding it back into `ColorScheme.fromSeed`.

Final theme factory:

```dart
HikariTheme.dark({
  bool oled = false,
  Color? accentSeed,
})
```

During incremental migration, scoped target themes may temporarily receive the same explicit `{oled, accentSeed}` inputs. They should not reconstruct them from a parent theme.

## 3. Final consumption rules

Migrated application widgets should prefer:

- `Theme.of(context).colorScheme`;
- `Theme.of(context).textTheme`;
- standard Material component themes;
- `HikariStatusColors` only for warning/info foreground-container pairs that Material does not provide;
- shared layout/spacing/shape/motion policies where the concept is truly cross-feature.

Do not retain a permanent fallback accessor that silently fabricates legacy colors when an extension is missing.

## 4. Design-system vocabulary

### Keep

- semantic `ColorScheme` construction;
- target `TextTheme` policy;
- `HikariStatusColors`;
- `HikariSpace`;
- repeated `HikariShape` roles;
- selectively useful `HikariSize` constants;
- `HikariLayout` breakpoints/caps, but classify using the correct available width;
- reduced-motion-aware motion policy;
- reader-specific canvas/prose policies where multiple reader surfaces share them.

### Remove or retire after migration

- legacy `HikariColors` as a parallel app-theme authority;
- legacy static typography/color APIs that duplicate live Material theme roles;
- local Home/navigation/SearchBar recreation of the whole app target theme;
- `bottomBarClearance` as a universal design-system token;
- migration-only adopter allowlists after full adoption.

### Rename only after collisions disappear

`HikariDesignTheme` / `HikariDesignMotion` may eventually become `HikariTheme` / `HikariMotion`. Do not perform naming cleanup while legacy and target implementations still coexist.

## 5. Shared UI boundary

`core/ui` should contain shared presentation contracts with proven multi-feature use.

Good candidates:

- media poster;
- media progress presentation;
- async state presentation;
- narrowly behavior-bearing controls such as search debounce wrappers when behavior is genuinely shared.

Do not create wrappers merely to hide Material widgets.

Canonical migrated actions should use native `FilledButton`, `OutlinedButton`, `TextButton`, and `IconButton` plus Hikari component themes/styles. A custom button must justify what native controls cannot express.

## 6. Responsive ownership

### Window-level decisions

The app/navigation shell may classify using full app/window width because it owns the primary navigation mode.

### Component-level decisions

Feature widgets choose variants from their **local bounded constraints**.

Use `LayoutBuilder` where a component can be placed inside:

- a navigation rail-reduced body;
- a max-width content container;
- a supporting/detail pane;
- a resizable desktop window;
- another bounded parent.

Do not let children subtract known parent chrome widths manually.

## 7. Bottom obstruction contract

Navigation avoidance is runtime layout information.

One shell/layout owner should expose or naturally propagate the effective bottom obstruction from:

- compact navigation height;
- safe-area inset;
- keyboard/system UI where applicable.

Scrollable features consume this obstruction once.

Intentional visual breathing room at the end of content is a separate spacing decision.

Rail layouts should not inherit compact bottom-bar clearance.

## 8. Navigation architecture

Preserve:

- `AppTab` identity;
- `AppNavigationController`;
- lazy first mounting;
- visited-page preservation;
- explicit tab callbacks;
- detail navigation as separate route flow.

Correct the shell so the page content host stays structurally stable across compact/wide changes.

### Presentation decision gate

The final destination rendering remains open between:

**Option A - Native**

`NavigationBar` + `NavigationRail` themed to Hikari.

**Option B - Corrected custom**

Current visual direction retained, with complete semantics/focus/scaling tests and reduced duplicated rendering.

Decision must be based on a small comparison spike, not taste.

Evaluate:

- visual fidelity;
- code size/complexity;
- selected semantics;
- keyboard/focus behavior;
- tooltip/label behavior;
- text scaling;
- platform consistency;
- responsive composition;
- performance of translucency/blur;
- test burden.

Native components are preferred if they deliver the target with materially less custom behavior. They are not mandatory if visual requirements would force equivalent custom complexity back in through wrappers.

## 9. Home architecture

Keep Home as feature composition, not as a new shared UI framework.

```text
HomePage
└─ HomeViewModel
   ├─ library snapshot/subscription
   ├─ continue-reading operation
   └─ catalog discovery operation

HomePage composition
├─ HomeHeader
├─ HeroCarousel
├─ ContinueShelf
├─ CatalogDiscoverySections
├─ warning/partial-failure presentation
└─ HomeRecentShelf
```

Home widgets stay feature-local until a second stable consumer demonstrates the same contract.

Do not promote `HomeBoundedSliverBox`, shelf models, or hero-specific styling to global abstractions speculatively.

## 10. Reader/player boundary

Application theme controls app chrome. Reader/player content surfaces may intentionally be different:

- OLED/charcoal/paper prose canvas;
- immersive black video/manga surface;
- reader-specific font/line-height/progression preferences.

Do not force reader content palettes to derive from application surface colors.

Where multiple reader features share a real presentation policy, keep it shared; otherwise keep measurements local to the reader feature.

## 11. Suggested long-term tree

Folder flattening is optional. A conservative target is:

```text
lib/app/
├─ app.dart
├─ navigation/
│  └─ app_navigation_shell.dart
└─ theme/
   ├─ hikari_theme.dart
   └─ design_system/
      ├─ geometry.dart
      ├─ layout.dart
      ├─ motion.dart
      ├─ status_colors.dart
      ├─ typography.dart
      └─ reading.dart   # only if shared reader ownership remains justified

lib/core/ui/
├─ components/
│  └─ behavior-bearing shared controls only
└─ patterns/
   ├─ async_state_view.dart
   ├─ media_poster.dart
   ├─ media_progress_bar.dart
   └─ media_type_presentation.dart

lib/features/home/
├─ home_page.dart
├─ home_view_model.dart
└─ widgets/
   ├─ home_header.dart
   ├─ hero_carousel.dart
   ├─ continue_shelf.dart
   ├─ catalog_discovery_sections.dart
   ├─ home_recent_shelf.dart
   ├─ home_warning_notice.dart
   └─ home_section_link.dart
```

If removing `design_system/` later clearly simplifies imports and naming, do it in final consolidation. It is not an architectural requirement.

## 12. Architecture invariants

The migration is complete only when:

- one root app theme controls migrated application chrome;
- appearance uses explicit raw input state;
- no migrated subtree depends on legacy theme fallback;
- standard Material semantics are not reimplemented without product need;
- local component variants use local constraints;
- runtime navigation obstruction is not encoded as a global spacing constant;
- Home remains feature composition rather than a generic UI framework;
- reader/player content policy remains independent where product behavior requires it.
