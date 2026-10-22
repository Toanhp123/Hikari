# 00 — Baseline, Decisions & Regression Harness Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Create a complete, auditable baseline and superseding UI architectural decision before implementation.

**Architecture:** Capture route/component/overlay inventory and acceptance fixtures; write the authoritative UI ADR and migration matrix; introduce staged rule-based guards so the existing application stays compilable during migration.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** None. First plan; must pass before foundation migration.

Execution record: [baseline runs and task self-review](../../ui/BASELINE_RUNS.md),
[live matrix](../../ui/MIGRATION_MATRIX.md),
[accepted ADR-026](../../decisions/ADR-026-ui-foundation-rebuild.md).
Owner acceptance of G0 with deferred device evidence is recorded in
[baseline runs](../../ui/BASELINE_RUNS.md). Checklist below remains the original
execution contract, not a claim that missing checks passed.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.
- Canonical spec was accepted through ADR-026 on 2026-10-22; the plan is not a substitute for an approved ADR. Any later cross-phase contract changes still require explicit review.
- All 19 baseline `lib/features/**/*page.dart` files must be represented; enumerate additional live routes, overlays and nested navigators rather than assuming there are exactly 19 routes.

## Review Focus

- A route or modal omitted from initial inventory later bypasses migration → inventory tool cross-check plus source scan.
- A regression test changes expected user behavior rather than capturing it → baseline review against existing product outcomes.
- An ADR mistakenly supersedes security/domain decisions → clause-level supersession review.
- Architecture guard blocks migration halfway → staged enforcement with explicit expiration, never a permanent exception.
- Baseline performance from unmatched devices/builds → record environment and label unavailable measurements.

---
### Task 01: Record route/overlay/component inventory

**Files:**
- Create: `tool/ui_audit_inventory.dart`
- Create: `docs/ui/MIGRATION_MATRIX.md`
- Create/Test: `test/ui_audit_inventory_test.dart`

**Interfaces:**
- Consumes: `lib/features/**`, `lib/core/ui/**`, `lib/app/navigation/**` in the supplied snapshot
- Produces: deterministic inventory JSON/Markdown with route path, modal entrypoint, old shared UI import, target owner and migration status

- [ ] **Step 1 — Write a failing test:** In `test/ui_audit_inventory_test.dart`, add tests named for these required assertions: find all 19 baseline page files plus any added live checkout page files; locate calls of showDialog/showModalBottomSheet/showHikariModalSheet, ModalRoute/OverlayEntry, drawer and legacy media patterns; the inventory is sorted and has no duplicate path keys
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/ui_audit_inventory_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Scan Dart source with a conservative lexer or analyzer AST if available; include exports/imports, root navigation and page widgets. Mark uncertain dynamic usages `manual-audit-required` rather than silently dropping them.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format tool/ui_audit_inventory.dart test/ui_audit_inventory_test.dart`, `fvm flutter test -r expanded test/ui_audit_inventory_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Check coverage manually against actual shell/app routes and declared exceptions; record exact snapshot date, do not assume current repo matches zip. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Capture current behavior fixtures: Source Picker and Catalog Detail

**Files:**
- Modify: `test/features/catalog/catalog_detail_page_test.dart`
- Modify: `test/features/catalog/catalog_source_picker_view_model_test.dart`
- Test: `test/features/catalog/catalog_detail_page_test.dart`

**Interfaces:**
- Consumes: existing picker ViewModel, `_chooseSourceOnce`, `_showSourceSheet`, `_showSourceDialog`
- Produces: baseline tests for cancel, language, candidate identity, stale resolution, manual search and information/chapters tab restoration

- [ ] **Step 1 — Characterize actual behavior:** In `test/features/catalog/catalog_detail_page_test.dart`, add tests named for these required assertions: cancel never starts source reader nor mutates remembered preference; manual search source IDs/language preserved; stale result after series swap ignored; picker success selects exact source; per-tab scroll offset persists
- [ ] **Step 2 — Verify baseline:** Run `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`; expect a factual GREEN where behavior exists; record any RED caused by a *known defect* as a later migration acceptance target, without fixing UI here.
- [ ] **Step 3 — Implement:** Add assertions characterizing intended outcomes rather than exact Dialog/Sheet widget types (mechanics change later); fix only invalid fixtures, not source logic, in this task.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/features/catalog/catalog_detail_page_test.dart test/features/catalog/catalog_source_picker_view_model_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Capture RED only where behavior is actually missing; for already-passing characterization tests record GREEN baseline and explicitly exempt RED step as non-feature scaffolding. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Capture current behavior fixtures: library, continuation and reading

**Files:**
- Modify: `test/library_page_test.dart`
- Modify: `test/home_page_test.dart`
- Test: `test/library_page_test.dart`

**Interfaces:**
- Consumes: Library button, Home continue shelf, reader-first and saved source identity
- Produces: regression cases named add/remove-from-every-origin, cached continue with exact source, poster identity and no accidental detail route

- [ ] **Step 1 — Write a failing test:** In `test/library_page_test.dart`, add tests named for these required assertions: confirmation cancel retains library state; add/remove from catalog/source/library uses one semantics; Continue action does not switch source; artwork identity stable after refreshing content
- [ ] **Step 2 — Verify baseline:** Run `fvm flutter test -r expanded test/library_page_test.dart`; record honest GREEN/RED status and existing defects. Do not force RED for characterization.
- [ ] **Step 3 — Implement:** Create portable fakes matching existing test helpers and document cross-file test entrypoints in MIGRATION_MATRIX; do not force network/device access in widget tests.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/library_page_test.dart test/home_page_test.dart`, `fvm flutter test -r expanded test/library_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Include extra verification commands for `test/home_page_test.dart` and existing reader tests; characterize known offline failure separately rather than falsely claiming solved. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Capture plugin safety, native consent and desktop navigation fixtures

**Files:**
- Modify: `test/plugins/apk_actions_page_test.dart`
- Modify: `test/plugins/novel_actions_page_test.dart`
- Modify: `test/navigation_shell_test.dart`
- Test: `test/plugins/apk_actions_page_test.dart`

**Interfaces:**
- Consumes: existing plugin action ViewModels and `AppNavigationShell` state/controller
- Produces: tests that consent cancellation never authorizes install, native denial remains denied, stale callback ignored, navigation selection and tab state survive resize

- [ ] **Step 1 — Write a failing test:** In `test/plugins/apk_actions_page_test.dart`, add tests named for these required assertions: incorrect source/package/trust identity cannot be accepted by UI; back/cancel deny; selected tab remains after resizing compact↔expanded; tab children not disposed solely because of shell width
- [ ] **Step 2 — Verify baseline:** Run `fvm flutter test -r expanded test/plugins/apk_actions_page_test.dart`; record honest GREEN/RED status and existing defects. Do not force RED for characterization.
- [ ] **Step 3 — Implement:** Use existing fake plugin gateways and navigation test fixtures. Document true native-only checks for future device gate, not counterfeit widget tests.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/plugins/apk_actions_page_test.dart test/plugins/novel_actions_page_test.dart test/navigation_shell_test.dart`, `fvm flutter test -r expanded test/plugins/apk_actions_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run novel action and navigation tests separately, record simulated versus device-level evidence. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Write superseding UI ADR and dependency map

**Files:**
- Create: `docs/decisions/ADR-026-ui-foundation-rebuild.md` (use first vacant number in actual checkout)
- Create: `docs/ui/FOUNDATIONS.md`
- Create: `docs/ui/RESPONSIVE.md`
- Create: `docs/ui/COMPONENTS.md`
- Modify: `docs/architecture/DESIGN_SYSTEM.md`
- Modify: `docs/architecture/UI_ARCHITECTURE.md`
- Modify: `docs/architecture/PRESENTATION.md`
- Create/Test: `test/ui_documentation_contract_test.dart`

**Interfaces:**
- Consumes: spec §§0–7, 10–14, decisions ADR-003/013/014/015/017/025
- Produces: one canonical UI decision ledger with clause-level supersession; token authority `core/ui/foundation` and app theme composition; component and feature ownership map

- [ ] **Step 1 — Write a failing contract test:** Create `test/ui_documentation_contract_test.dart` to check canonical Markdown files exist, relative links resolve, and the migration matrix covers inventoried page file paths; review ADR clause supersession and product semantics manually.
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/ui_documentation_contract_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Write target docs before moving code; link legacy docs to canonical material or update in place, never create contradictory authorities. Add doc contract test for path/links/owner table.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/ui_documentation_contract_test.dart`, `fvm flutter test -r expanded test/ui_documentation_contract_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Do not assume ADR-026 available on active branch. Explicitly preserve ADR-009 source IDs, ADR-023 continuation and ADR-025 plugin management/security ownership. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Build staged import/export and raw-modal guard fixtures

**Files:**
- Modify: `test/architecture_guard_test.dart`
- Create: `test/fixtures/ui_architecture/core_imports_domain.dart.txt`
- Create: `test/fixtures/ui_architecture/feature_show_dialog.dart.txt`
- Create: `test/fixtures/ui_architecture/relative_barrel_export.dart.txt`
- Create: `test/fixtures/ui_architecture/comment_false_positive.dart.txt`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: current `_dependencyDirectives` parser and legacy exceptions
- Produces: negative fixtures verifying app/theme inversion, core/ui→domain and feature raw modal are invalid; staged migration allowlist with per-path owner and expiry

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: reject `export` and relative import bypass; reject `core/ui/foundation` importing widgets; detect feature direct showDialog/showModalBottomSheet after route migration; allow only documented OS-owned exceptions
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Extend existing parser with fixture-level unit tests first. Keep deliberately forbidden code in `.dart.txt` fixture files so Flutter analyzer does not treat them as active Dart libraries; the architecture tests must read and parse their contents. Enforce strict final rules on migrated paths only until 07 cleanup. Avoid grep-only false positives in comments/strings.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Guard migration must not require old source to instantly satisfy its final-state rules; record expiration and removal task for each temporary allowlist. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Capture reproducible visual and performance baseline

**Files:**
- Modify: `docs/quality/UI_PERFORMANCE_BASELINE.md`
- Create: `docs/ui/BASELINE_RUNS.md`
- Test: `test/theme_test.dart`

**Interfaces:**
- Consumes: current theme, component, UI performance docs and device capability
- Produces: reproducible baseline sheet: Flutter/Dart versions, build/profile mode, device/OS/viewport/text scale, fixtures, known visual mismatches

- [ ] **Step 1 — Collect baseline evidence:** Record dark/OLED theme expectations in existing `test/theme_test.dart`; create reproducible viewport screenshot instructions (375/840/1440) and a measurement metadata table in `BASELINE_RUNS.md`. **Do not encode screenshot availability or hardware measurements as assertions in a theme widget test.**
- [ ] **Step 2 — Verify baseline:** Run `fvm flutter test -r expanded test/theme_test.dart`; record actual result. Validate Markdown evidence by manual checklist; missing measurements are `NOT MEASURED`, never a fabricated test failure or PASS.
- [ ] **Step 3 — Implement:** Execute baseline tests/build/profile on actual dev environment where supported; in document include command template and device protocol, not invented performance numbers.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/theme_test.dart`, `fvm flutter test -r expanded test/theme_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** If current environment lacks FVM/ADB/Windows, record pending gate; do not claim pass. Include native user-device confirmation requirement. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

