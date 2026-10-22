# 01 — Foundation Tokens, Controls & Gallery Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Establish a single theme/token authority and canonical primitive component set with stable documented APIs.

**Architecture:** Move semantic design constants to core/ui/foundation; keep `app/theme/hikari_theme.dart` as pure ThemeData composer; migrate import graph and controls in focused slices. Provide debug-only gallery and public catalog.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** 00 baseline/ADR signed off. Staged import guard available.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.
- Do not add another state-management package or font dependency; use Material 3 and existing Flutter assets.

## Review Focus

- OLED tokens lose contrast under overlays → theme state tests including actual surface levels.
- Imported old token aliases remain as permanent double authority → one-at-a-time caller map + deletion gate.
- Search field breaks external controller updates → controlled-controller widget tests.
- Choice menu loses Escape/focus semantics → keyboard tests.
- Gallery is accidentally shipped in release navigation → debug-only entrypoint/build isolation test.

---
### Task 01: Relocate spacing, radius, motion, size and breakpoints

**Files:**
- Create: `lib/core/ui/foundation/hikari_spacing.dart`
- Create: `lib/core/ui/foundation/hikari_radius.dart`
- Create: `lib/core/ui/foundation/hikari_motion.dart`
- Create: `lib/core/ui/foundation/hikari_size.dart`
- Create: `lib/core/ui/foundation/hikari_breakpoints.dart`
- Modify: `lib/app/theme/hikari_theme.dart`
- Test: `test/theme_architecture_test.dart`

**Interfaces:**
- Consumes: existing app/theme token exact named values and `.fvmrc` Flutter 3.47.5
- Produces: `HikariSpacing`, `HikariRadius`, `HikariMotion`, `HikariSize`, `HikariBreakpoints.classify(double)` now owned by `core/ui/foundation`

- [ ] **Step 1 — Write a failing test:** In `test/theme_architecture_test.dart`, add tests named for these required assertions: values/classification 599→compact, 600→medium, 840→expanded, 1200→wide match approved starting thresholds; pure tokens do not import app/features; rejects NaN/negative width
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/theme_architecture_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Move token declarations into foundation and update the theme composer. To keep each intermediate commit-less task compilable, EITHER migrate **all** old token imports in the same task, OR keep narrowly scoped `app/theme/hikari_*.dart` deprecated **export-forwarders** with exactly one authoritative implementation (not copied constants). Record each shim in MIGRATION_MATRIX with owner and mandatory removal by Task 07/01; migrate all outstanding consumers in Task 01/07. No duplicate token values or implementations.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/foundation/hikari_spacing.dart lib/core/ui/foundation/hikari_radius.dart lib/core/ui/foundation/hikari_motion.dart lib/core/ui/foundation/hikari_size.dart lib/core/ui/foundation/hikari_breakpoints.dart lib/app/theme/hikari_theme.dart test/theme_architecture_test.dart`, `fvm flutter test -r expanded test/theme_architecture_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Compare current values before/after move; do not change layout visual values implicitly. List every legacy import still pending. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Make semantic color roles, typography and ThemeData single authority

**Files:**
- Create: `lib/core/ui/foundation/hikari_color_roles.dart`
- Create: `lib/core/ui/foundation/hikari_typography.dart`
- Modify: `lib/app/theme/hikari_theme.dart`
- Test: `test/theme_test.dart`

**Interfaces:**
- Consumes: foundation size/spacing/radius; current Material 3 ColorScheme, HikariStatusColors and dark/OLED preferences
- Produces: ThemeData factory `HikariTheme.darkTheme({bool oled = false, Color? accentColor})`; color roles only for semantics absent from M3

- [ ] **Step 1 — Write a failing test:** In `test/theme_test.dart`, add tests named for these required assertions: OLED canvas #000000 but legible dialogue, labels, outline, selection/error; accent choices preserved; ThemeExtension interpolates without conflicting ColorScheme aliases; contrast snapshots dark/OLED
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/theme_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Move status colors into true extension roles (can retain tested semantic API name if suitable) and typography into foundation; `app/theme` only assembles ThemeData and owns user appearance settings wiring.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/foundation/hikari_color_roles.dart lib/core/ui/foundation/hikari_typography.dart lib/app/theme/hikari_theme.dart test/theme_test.dart`, `fvm flutter test -r expanded test/theme_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Examine every former `app/theme/hikari_status_colors.dart` consumer and update call sites once; no two active status color APIs. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Define canonical buttons, icon actions, chips and sizes

**Files:**
- Create: `lib/core/ui/controls/hikari_button.dart`
- Create: `lib/core/ui/controls/hikari_icon_button.dart`
- Create: `lib/core/ui/controls/hikari_filter_chip.dart`
- Create: `lib/core/ui/controls/hikari_chip_row.dart`
- Create/Test: `test/core/ui/controls/hikari_actions_test.dart`

**Interfaces:**
- Consumes: old `lib/core/ui/components/hikari_button.dart`, `hikari_icon_button.dart`, `hikari_chip.dart`; foundation tokens
- Produces: `HikariButton`, `HikariIconButton`, `HikariFilterChip`, `HikariChipRow` with finite semantic variants and explicit callbacks

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/controls/hikari_actions_test.dart`, add tests named for these required assertions: 48 logical-pixel touch target Android; loading prevents duplicate activation; disabled actions not focusable as enabled; focus/hover visuals; chip row density/overflow consistent
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/controls/hikari_actions_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Rebuild only where the wrapper enforces Hikari semantics; otherwise centralize Material theme behavior and document direct Material use. Track consumers for import migration.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/controls/hikari_button.dart lib/core/ui/controls/hikari_icon_button.dart lib/core/ui/controls/hikari_filter_chip.dart lib/core/ui/controls/hikari_chip_row.dart test/core/ui/controls/hikari_actions_test.dart`, `fvm flutter test -r expanded test/core/ui/controls/hikari_actions_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Self-review for boolean explosion, child ownership and accessibility; compare filter spacing to existing user-approved density. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Build controlled search field, choice menu and segmented control

**Files:**
- Create: `lib/core/ui/controls/hikari_search_field.dart`
- Create: `lib/core/ui/controls/hikari_choice_menu.dart`
- Create: `lib/core/ui/controls/hikari_segmented_control.dart`
- Create/Test: `test/core/ui/controls/hikari_inputs_test.dart`

**Interfaces:**
- Consumes: existing `hikari_search_bar.dart`, `hikari_choice_menu.dart` and explicit submit search behavior
- Produces: `HikariSearchField({TextEditingController? controller, ValueChanged<String>? onChanged, ValueChanged<String>? onSubmitted, ...})`; `HikariChoiceMenu<T>` typed value/options; segmented selection event typed

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/controls/hikari_inputs_test.dart`, add tests named for these required assertions: external controller text update redraws clear/submit; Enter submits once, typing does not request remote search unless feature opts in; Escape returns focus in anchored menu; empty options disabled safely; keyboard and large text no overflow
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/controls/hikari_inputs_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Keep feature-owned debounce/query semantics outside UI field; document internal vs external controller disposal and focus-node ownership; use tested Material MenuAnchor mechanics.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/controls/hikari_search_field.dart lib/core/ui/controls/hikari_choice_menu.dart lib/core/ui/controls/hikari_segmented_control.dart test/core/ui/controls/hikari_inputs_test.dart`, `fvm flutter test -r expanded test/core/ui/controls/hikari_inputs_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run existing `test/core/ui/hikari_search_bar_test.dart` and choice-menu tests during migration; inspect focus return on Windows. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Create feedback/async states and shared section content

**Files:**
- Create: `lib/core/ui/feedback/hikari_async_state_view.dart`
- Create: `lib/core/ui/feedback/hikari_notice_banner.dart`
- Create: `lib/core/ui/content/hikari_section_header.dart`
- Create: `lib/core/ui/content/hikari_list_row.dart`
- Create/Test: `test/core/ui/feedback/hikari_feedback_test.dart`

**Interfaces:**
- Consumes: old async_state_view.dart and hikari_section_header.dart; UI state contract in spec §14
- Produces: typed/presentation-only empty/loading/partial/error/refresh visuals; retry callbacks, section title/subtitle/action slots, and `HikariListRow` with leading/title/subtitle/trailing/onTap slots and clear enabled semantics

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/feedback/hikari_feedback_test.dart`, add tests named for these required assertions: loaded content remains on refresh; recoverable error preserves old data; retry button available and accessible; notice contrast meets semantic dark/OLED; long section actions reflow; `HikariListRow` handles disabled state/keyboard/3x text without clipping
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/feedback/hikari_feedback_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Implement presentation variants without fetching data; add the shared domain-free list row only when it encapsulates Hikari-specific layout/semantics, not a trivial `ListTile` alias. Retain feature state owners. Do not generalize state to Object/domain runtime unions.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/feedback/hikari_async_state_view.dart lib/core/ui/feedback/hikari_notice_banner.dart lib/core/ui/content/hikari_section_header.dart lib/core/ui/content/hikari_list_row.dart test/core/ui/feedback/hikari_feedback_test.dart`, `fvm flutter test -r expanded test/core/ui/feedback/hikari_feedback_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Ensure empty message only on true zero results, not when network fails. Avoid forced spinner replacing existing content. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Curate public API, component docs and debug-only gallery

**Files:**
- Create: `lib/core/ui/hikari_ui.dart`
- Create: `tool/ui_gallery/main.dart`
- Create: `docs/ui/CONTRIBUTING.md`
- Create: `docs/ui/components/controls.md`
- Modify: `docs/ui/COMPONENTS.md`
- Create/Test: `test/core/ui/hikari_public_api_test.dart`

**Interfaces:**
- Consumes: canonical controls/feedback/theme API; spec §§11,12,17
- Produces: curated exports with no import cycles; debug gallery includes buttons, input, feedback dark/OLED, keyboard, scaling and stress text

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/hikari_public_api_test.dart`, add tests named for these required assertions: public API imports without legacy path; tool/ui_gallery is not imported by production app; gallery can switch states and theme; docs contain controlled/uncontrolled contract, named owner and lifecycle state Draft/Preview/Stable/Deprecated with documented promotion checklist
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/hikari_public_api_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Create an independent `tool/ui_gallery/main.dart` application entrypoint; do not add a production navigation tab. Add compile/widget smoke for exported symbols and dark/OLED.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/hikari_ui.dart tool/ui_gallery/main.dart test/core/ui/hikari_public_api_test.dart`, `fvm flutter test -r expanded test/core/ui/hikari_public_api_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Compare export list with admitted Stable components. Check gallery cannot cause release runtime dependency. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Migrate remaining core and feature token imports (no duplicate theme)

**Files:**
- Modify: concrete files reported by `tool/ui_audit_inventory.dart` with token imports (replace this inventory placeholder with exact paths at G0)
- Modify: concrete `lib/core/ui/` files reported by the same inventory (replace placeholder with exact paths at G0)
- Modify: `lib/app/navigation/app_navigation_shell.dart`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: foundation tokens + `HikariTheme` composer; inventory all `package:hikari/app/theme/hikari_theme.dart` imports
- Produces: all `core/ui` and feature token consumers import foundation directly; features read active Material theme via Theme.of(context)

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: guard reports zero core/ui→app/theme and features→app/theme imports/exports; dark/OLED and accent controls still work; no duplicate alias files in final-state after all consumers moved
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Replace every remaining old `app/theme` import/indirect re-export in all feature/core consumers using the G0 path manifest; preserve exact symbol meanings. Remove the temporary export-forwarders only once their final import has migrated; check all `lib/` imports before turning the strict guard on.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format tool/ui_audit_inventory.dart lib/app/navigation/app_navigation_shell.dart test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run `test/theme_architecture_test.dart`, `test/theme_test.dart` and selected page tests; record old files awaiting deletion in 07 cleanup. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

