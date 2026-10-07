# Hikari UI Foundation Decisions

Status: Consolidated decision register for the `08a091e` review baseline

## Accepted now

### Keep the design-system direction

The target design system is worth continuing. Do not replace it with another architecture or styling framework.

### Correct migration seams before wider visual migration

Home/navbar currently look good, but correctness and ownership must be stabilized before copying the pattern into more screens.

### Keep feature state architecture

Retain `ChangeNotifier` ViewModels, constructor injection, and current application/domain boundaries. No global state rewrite is justified.

### One final application theme authority

The final app should have one `HikariTheme`-level authority. Legacy and target themes may coexist only as a bounded migration state.

### Explicit appearance inputs

OLED and accent seed are configuration state. Do not reconstruct them from generated theme output.

### Material semantics first

Use `ColorScheme`, `TextTheme`, Material component themes, and native action controls by default. Add Hikari-specific primitives only for demonstrated gaps.

### Preserve Home product hierarchy

Keep the current hierarchy unless product evidence later says otherwise:

1. featured hero;
2. continue shelf;
3. discovery sections;
4. warnings/partial failures;
5. recently added preview.

### Performance requires measurement

Blur, clipping, gradients, repaint boundaries, and image decode policy are not to be rewritten on theoretical cost alone.

## Deferred decisions

### Native versus custom navigation presentation

Not decided.

A native `NavigationBar` / `NavigationRail` implementation is the leading simplification candidate, but must be compared after shell correctness and accessibility fixes.

### Folder flattening

Whether `app/theme/design_system/` should eventually flatten into `app/theme/` is optional. Decide during final consolidation only if it improves ownership/import clarity.

### Reading policy file ownership

Keep reader content independent from app chrome. Exact file location remains dependent on real multi-reader reuse.

### Additional design tokens

Do not add media-category colors, component-specific sizes, or generic card/shelf tokens until stable reuse proves a need.

## Rejected directions

- app-wide UI rewrite;
- replacing ViewModels with another state package;
- nested navigators for every root tab by default;
- routing package solely for current root navigation;
- universal media-card/shelf DSL;
- permanent dual target/legacy theme extensions;
- deriving selected accent from generated `ColorScheme.primary`;
- clamping accessibility text scale to preserve card dimensions;
- adding image-cache dependencies before profiling;
- deleting glass/blur merely because it can be expensive;
- rewriting historical ADRs to pretend migration was always wired.

## Explicit non-goals for the next correction pass

- redesigning Home;
- changing Home content priority;
- adding carousel autoplay;
- changing media domain/application logic;
- changing catalog/source identity contracts;
- changing reader/player lifecycle architecture;
- migrating every feature at once;
- mass renaming theme files/classes;
- forcing pixel-identical native navigation before the comparison spike.

## Decision checkpoints

### After Phase 1

Question: Does the shell preserve visited feature state across live resize?

If no, stop and fix before theme work expands.

### After Phase 2

Question: Can migrated Home render all visible states from explicit target appearance without legacy fallback?

If no, do not switch root theme.

### After Phase 3

Question: Does the foundation survive local-width seams, large/nonlinear text, keyboard/focus, and semantics checks?

If no, do not migrate more screens.

### After Phase 4

Question: Which navigation presentation gives the required Hikari identity with the smallest reliable behavior surface?

Record the answer and delete the losing production path.

### Before root theme switch

Question: Are remaining legacy extension consumers either migrated or intentionally isolated from target application chrome?

If no, postpone root switch.
