# Hikari UI Foundation Migration Plan

Status: Recommended implementation order after consolidated review  
Goal: Make the new UI foundation correct before expanding visual migration

## Guiding rule

Behavior corrections, theme integration, accessibility hardening, navigation presentation experiments, and final cleanup should remain separable and reviewable.

Do not combine all UI migration into one branch/patch.

## Phase 1 - Shell correctness

### Goal

Preserve visited tab state across responsive navigation changes without changing the current visual design.

### Primary files

- `lib/app/navigation/app_navigation_shell.dart`
- `test/navigation_shell_test.dart`

### Work

- keep one stable page-content host across compact and wide layouts;
- maintain current `AppNavigationController` and `AppTab` identity;
- preserve lazy first mounting and visited-page retention;
- add a regression test that resizes one mounted shell repeatedly across the 600dp boundary;
- verify selected tab, local state, text input/scroll probes, and initialization/disposal counts.

### Must not change

- destination set/order;
- root tab callbacks;
- current bar/rail appearance except where structure requires invisible composition changes;
- feature state architecture.

### Exit criteria

- no visited page remount across compact/rail transitions;
- ordinary tab switching remains unchanged;
- full existing navigation tests still pass.

## Phase 2 - Design-system integration correctness

### Goal

Make migrated Home/navigation/shared states consume one coherent target appearance contract.

### Primary areas

- application appearance inputs in `app.dart`;
- temporary Home/navigation/SearchBar theme bridges;
- `MediaProgressBar`;
- `AsyncStateView`;
- `HikariButton` call paths or replacement with native actions;
- `MediaPoster` badge presentation;
- Home shelves/hero/layout tokens.

### Work

- stop deriving target seed from rendered `colorScheme.primary`;
- forward explicit OLED + raw accent seed while scoped themes still exist;
- migrate shared primitives reached by Home away from `context.hikariColors` fallback;
- prefer native Material action widgets/styles in migrated paths;
- correct warning/info badge background/foreground pairing;
- use local `LayoutBuilder` constraints for Hero/shelf variant decisions;
- remove universal `bottomBarClearance` usage and define one effective obstruction owner;
- keep current Home visual hierarchy and product semantics.

### Tests

- default target seed versus custom accent seed;
- OLED toggle without accent change;
- non-default accent + OLED across Home loading/error/progress/content states;
- actual descendant color behavior, not just import/adopter checks;
- badge contrast after alpha compositing;
- 599/600/601 and 839/840/841 local-width scenarios;
- zero/nonzero bottom inset and rail mode.

### Exit criteria

- migrated Home states no longer silently consume default legacy colors;
- target appearance is driven by explicit state;
- bottom content avoidance is correct in compact and rail layouts;
- no new generic UI abstraction introduced.

## Phase 3 - Accessibility and responsive hardening

### Goal

Validate the corrected foundation against real accessibility/layout contracts before app-wide migration.

### Work

- fix wide navigation selected semantics regardless of future native/custom decision;
- test 100%, 200%, and Android nonlinear text scaling;
- use long anime/manga/novel titles and short-height windows;
- correct measured fixed-height failures without clamping text scale;
- validate keyboard-only navigation/focus on Windows;
- validate TalkBack semantics on Android;
- ensure actionable targets meet the project 48dp minimum where applicable;
- ensure truncated visual titles keep meaningful accessible labels;
- verify reduced-motion behavior for app-authored decorative transitions.

### Validation matrix

Widths:

- 320
- 375
- 599
- 600
- 601
- 839
- 840
- 1200+

Other dimensions:

- portrait and short landscape;
- default accent and each offered accent;
- OLED on/off;
- touch, mouse, keyboard-only;
- loading, empty, stale snapshot, partial failure, image failure.

### Exit criteria

- no confirmed clipping/obscured required action in the matrix;
- one selected root destination is exposed correctly to semantics;
- keyboard focus remains visible and reachable;
- no accessibility fix depends on suppressing user text scale.

## Phase 4 - Navigation presentation decision spike

### Goal

Decide whether native `NavigationBar` / `NavigationRail` materially simplify Hikari after the correctness work.

### Scope

Create the smallest reversible comparison. Do not redesign routing or feature state.

### Compare

Current corrected custom presentation versus native Material presentation using the same:

- controller;
- `AppTab` model;
- stable content host;
- lazy/visited-page policy;
- callbacks.

Score:

- visual fidelity to Hikari's docked tonal/glass identity;
- code/branch complexity;
- semantics and selected state;
- keyboard/focus behavior;
- tooltip/label behavior;
- large-text behavior;
- responsive implementation;
- blur/translucency feasibility;
- test burden;
- Android/Windows consistency.

### Decision rule

Adopt native navigation if it provides the required product appearance with materially less custom behavior.

Keep corrected custom navigation if matching the intended design would require enough wrappers, custom layout, custom indicators, and custom effects that the native implementation no longer simplifies the system.

### Exit criteria

A written decision with evidence. Do not leave two production implementations.

## Phase 5 - Root migration, cleanup, documentation, and measured tuning

### Goal

Finish the transition from dual theme authorities to one application theme.

### Work

- migrate remaining shared primitives and pages that still require legacy `HikariColors` semantics;
- correct Settings appearance state so selection is based on raw seed rather than generated primary;
- migrate Catalog/Library/Local/Settings and remaining app chrome incrementally;
- switch `MaterialApp` to the final target `HikariTheme` only when legacy fallback is no longer required by migrated application chrome;
- remove scoped target-theme reconstruction from Home/navigation/SearchBar;
- retire legacy theme tokens/extensions after reference checks;
- rename `HikariDesign*` only after collisions are gone;
- update design-system/UI architecture docs and preserve ADR history;
- retire migration-only adopter allowlists;
- profile glass/image behavior and tune only measured issues.

### Performance profile scenarios

- Home cold load;
- Home warm load;
- vertical scroll through pinned header;
- sustained scrolling under compact bottom chrome;
- horizontal shelves;
- hero swipe;
- tab switching and return to Home;
- repeated desktop resize;
- OLED mode;
- memory after repeated hero/image navigation.

### Exit criteria

- one root application theme;
- no required legacy fallback in application chrome;
- docs describe current truth rather than migration intent;
- performance changes are backed by reproducible traces or an explicit decision that current effects are acceptable.

## Quality gates for every implementation phase

Follow project workflow. At minimum:

```text
fvm flutter analyze
relevant targeted flutter tests
full flutter test when the change surface warrants it
git diff --check
project check.ps1 / debug build according to repository workflow
```

Do not mass-format unrelated files. Do not auto-commit/push. Keep patches reviewable and bisectable.
