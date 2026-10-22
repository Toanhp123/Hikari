# 05 — Settings, Plugin Management & Native Actions Migration Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Migrate administrative and settings UI to standard forms, dialogs, lists and feedback without weakening native installation/trust protections.

**Architecture:** Controls and surfaces are presentation-only; repository trust, identity validation, install concurrency and OS user consent remain in existing plugin/application/native gateways.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** Plans 01, 02 and Wave A/B gate complete; plugin workflow baseline from plan 00.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.

## Review Focus

- Confirmation dialog dismiss accidentally interpreted as consent → explicit true-only test.
- User switches plugin/package while request in flight → generation/source identity guard.
- Windows tool/platform availability differs from Android APK runtime → feature-gate and informative UI.
- Large repository list/filter breaks performance → lazy loading and stabilized controllers.
- Settings OLED/cache accordion loses persisted preferences → reopen/settings tests.


---
### Task 01: Migrate Settings controls, sections and cache expansion

**Files:**
- Modify: `lib/features/settings/settings_page.dart`
- Modify: `lib/features/settings/widgets/settings_content.dart`
- Test: `test/settings_page_test.dart`

**Interfaces:**
- Consumes: HikariPageScaffold/SectionHeader/controls, appearance settings repository and cache UI
- Produces: canonical settings composition with persisted dark/OLED/accent and compressed/expandable cache section

- [ ] **Step 1 — Write a failing test:** In `test/settings_page_test.dart`, add tests named for these required assertions: cache section collapsed by default (or preserves user-established choice); expand shows correct per-cache totals; theme persists across page recreate; short-height/large font no lost actions; keyboard controls accessible
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/settings_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Keep existing cache maintenance application rules untouched; separate settings presentation data and persistent repository state, document accordion state lifetime.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/settings/settings_page.dart lib/features/settings/widgets/settings_content.dart test/settings_page_test.dart`, `fvm flutter test -r expanded test/settings_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run appearance settings flow and cache maintenance tests; verify short desktop height and mouse wheel. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Migrate Plugin Manager repository forms and list navigation

**Files:**
- Modify: `lib/features/plugins/plugin_manager_page.dart`
- Modify: `lib/features/plugins/widgets/plugin_repository_section.dart`
- Modify: `lib/features/plugins/widgets/plugin_browse_filters.dart`
- Modify: `lib/features/plugins/widgets/plugin_list_tile.dart`
- Test: `test/plugins/plugin_manager_page_test.dart`

**Interfaces:**
- Consumes: HikariDialog/Presenter, controls, page shell and PluginManagerViewModel
- Produces: repository add/edit/select forms use canonical transient presentation, filters and selected ecosystem states typed

- [ ] **Step 1 — Write a failing test:** In `test/plugins/plugin_manager_page_test.dart`, add tests named for these required assertions: Cancel leaves repository state unchanged; URL/ecosystem validation remains in VM; installed/available/update facets stable under resize; keyboard Enter works; no feature-level raw showDialog
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/plugins/plugin_manager_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Move existing inline repository add/select dialog chrome to feature-only content widgets with canonical surface; do not route install approval through generic button code.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/plugins/plugin_manager_page.dart lib/features/plugins/widgets/plugin_repository_section.dart lib/features/plugins/widgets/plugin_browse_filters.dart lib/features/plugins/widgets/plugin_list_tile.dart test/plugins/plugin_manager_page_test.dart`, `fvm flutter test -r expanded test/plugins/plugin_manager_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run plugin manager corrections and ViewModel tests; ensure no network calls from build(). Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Decompose APK actions page and preserve consent/native safeguards

**Files:**
- Modify: `lib/features/plugins/apk_actions_page.dart`
- Modify: `lib/features/plugins/apk_actions_view_model.dart` (UI orchestration only if necessary)
- Modify: `lib/features/plugins/widgets/apk_operation_card.dart`
- Test: `test/plugins/apk_actions_page_test.dart`

**Interfaces:**
- Consumes: canonical confirmation/feedback page shell and existing trust/native install gateway
- Produces: APK action route becomes coordinator + coherent content widgets; multi-step operation/approval remains ViewModel/native owned

- [ ] **Step 1 — Write a failing test:** In `test/plugins/apk_actions_page_test.dart`, add tests named for these required assertions: package signature/source trust guards preserved; cancel/Back never starts install; duplicate tap/async stale result ignored; installer denial displayed and source identity consistent
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/plugins/apk_actions_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Extract named sections and operation controls without moving actual security checks into generic core UI. Refuse to merge update/install/remove states into ambiguous bool flags.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/plugins/apk_actions_page.dart lib/features/plugins/apk_actions_view_model.dart lib/features/plugins/widgets/apk_operation_card.dart test/plugins/apk_actions_page_test.dart`, `fvm flutter test -r expanded test/plugins/apk_actions_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run plugin runtime and acquisition tests; distinguish Flutter simulated gateway tests from actual Android installer acceptance. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Migrate Novel plugin actions and cross-ecosystem feedback

**Files:**
- Modify: `lib/features/plugins/novel_actions_page.dart`
- Modify: `lib/features/plugins/novel_actions_view_model.dart` (UI state only if necessary)
- Modify: `lib/features/plugins/widgets/novel_artifact_details.dart`
- Test: `test/plugins/novel_actions_page_test.dart`

**Interfaces:**
- Consumes: canonical button/dialog/feedback, existing bounded LNReader runtime and NovelActionsViewModel
- Produces: LN plugin UI uses same visual/status/action contract but preserves ecosystem-specific installation semantics

- [ ] **Step 1 — Write a failing test:** In `test/plugins/novel_actions_page_test.dart`, add tests named for these required assertions: unsupported/failed plugin acquisition and language unspecified produce actionable error; cancel/timeout/trust selection does not mutate registry; progress/error messages accessible
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/plugins/novel_actions_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Use feature presentation adapters for statuses; do not pretend APK and LN plugin execution have identical native behavior beyond generic UI actions.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/plugins/novel_actions_page.dart lib/features/plugins/novel_actions_view_model.dart lib/features/plugins/widgets/novel_artifact_details.dart test/plugins/novel_actions_page_test.dart`, `fvm flutter test -r expanded test/plugins/novel_actions_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run upstream acquisition and plugin management tests; clarify any runtime behavior covered only by device E2E. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: End-to-end plugin surfaces, foreground lifecycle and desktop behavior

**Files:**
- Create: `test/plugins/hikari_plugin_surface_flow_test.dart`
- Modify: `docs/ui/PATTERNS.md`
- Test: `test/plugins/hikari_plugin_surface_flow_test.dart`

**Interfaces:**
- Consumes: Plugin Manager→APK/Novel action routing, typed modal result and native guard
- Produces: repeatable plugin surface integration tests and documented confirmation/install UI sequence

- [ ] **Step 1 — Write a failing test:** In `test/plugins/hikari_plugin_surface_flow_test.dart`, add tests named for these required assertions: compact and expanded present correct surface; changing package before approval makes old result ineffective; dialog focus returns after Cancel; platform install denial never appears as success; Windows unsupported operations disabled with reason
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/plugins/hikari_plugin_surface_flow_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Build fake gateway and stateful navigator fixture. Document OS-native consent boundary and manual Android validation script.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/plugins/hikari_plugin_surface_flow_test.dart`, `fvm flutter test -r expanded test/plugins/hikari_plugin_surface_flow_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Verify route disposal, text scale 3x, short screen and keyboard actions; no exceptions hiding trust checks. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Wave C gate: no feature-specific modal ownership remains

**Files:**
- Modify: `docs/ui/MIGRATION_MATRIX.md`
- Modify: `test/architecture_guard_test.dart`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: migrated Settings/Plugin Manager/APK/Novel files
- Produces: strict guard active on all management and settings UI files; old feature raw-modal builder paths retired

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: inventory shows zero raw showDialog/showModalBottomSheet under migrated plugin/settings paths; trust/approval regression suites pass; docs declare remaining unmigrated reader/player scope only
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Update inventory/status and staged guard, remove now-unused route-specific chrome. Leave platform/OS-owned install UI as documented exception.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run full `fvm flutter test` and analyze; stop release plans if APK/Novel plugin flow regresses. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

