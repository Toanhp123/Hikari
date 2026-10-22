# 07 — Legacy Removal, Final Guards & Release Acceptance Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Remove every superseded shared UI API, enforce the final dependency/overlay contract, synchronize docs, and verify the rebuilt UI end-to-end on Android and Windows.

**Architecture:** Use the inventory and migration matrix as a deletion oracle; strict guard rejects forbidden imports/exports, feature-owned modal route chrome and duplicate token sources; release gates require real tool evidence.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** All plans 00–06 complete and all features migrated. No final deletion if a consumer remains.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.

## Review Focus

- An untracked legacy import survives a barrel export → guard parses export directives and relative paths.
- A raw showDialog appears in a new feature → first-party surface rule with reviewed OS exception.
- User device regression masked by passing unit tests → manual Android and Windows matrices mandatory.
- Frame raster/image decode regresses despite clean architecture → matched profile before/after gate.
- Old ADR/doc contradicts new API after refactor → link checker and explicit supersession review.


---
### Task 01: Generate last-consumer report and remove legacy components/patterns

**Files:**
- Delete: `lib/core/ui/components/` (after inventory proves no consumers)
- Delete: superseded `lib/core/ui/patterns/` files only (retain/migrate genuine feature-owned patterns)
- Delete: old `lib/app/theme/hikari_spacing.dart` etc. if no caller remains
- Modify: `lib/core/ui/hikari_ui.dart`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: MIGRATION_MATRIX from all waves and stable core UI exports
- Produces: single canonical public type/API per concern and zero unsupported forwarding aliases or duplicate tokens

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: existing imports are rejected by the guard before deleting files, then compilation succeeds after old imports/files are absent; library/class exports have no dead refs; all supported features call canonical UI only
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Scripted search of imports+exports, compare to 00 inventory, delete ONLY after last reference resolution. Re-home any truly reusable old pattern into approved category with doc+test rather than deleting functionality.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/hikari_ui.dart test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run `fvm flutter analyze --no-pub` and broad tests immediately after each deletion cluster; do not silently preserve old folders as aliases. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Turn on final one-way import/export architecture guard

**Files:**
- Modify: `test/architecture_guard_test.dart`
- Modify: `test/fixtures/ui_architecture/core_imports_domain.dart.txt`
- Modify: `test/fixtures/ui_architecture/relative_barrel_export.dart.txt`
- Modify: `test/fixtures/ui_architecture/comment_false_positive.dart.txt`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: ADR new dependency graph and migrated final paths
- Produces: strict zero-temporary-allowlist guard for core/ui/app/theme/features across imports/exports/relative and conditional directives

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: core/ui→domain/application/infrastructure/features/app banned; app/theme→core/ui foundation allowed, never reverse; feature→all app/** banned; forbidden URI via relative/export caught; intentional platform exceptions listed by owner and expiry
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Retire staged allows; extend the named `.dart.txt` fixtures with conditional exports, comment/string false positives and transitive reexport cases when parser supports. Keep intentionally invalid Dart snippets out of live `.dart` compilation inputs. Fail fast with actionable file/edge messages.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Check guard does not forbid framework-owned tooltip/IME/internal Overlay required by a documented exception. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Enforce feature surface usage without regex-only false positives

**Files:**
- Modify: `test/architecture_guard_test.dart`
- Create: `test/ui_surface_architecture_test.dart`
- Test: `test/ui_surface_architecture_test.dart`

**Interfaces:**
- Consumes: canonical presenter/dialog/sheet/drawer with approved exception catalog
- Produces: feature raw showDialog/showModalBottomSheet/OverlayEntry route ownership flagged; OS-owned permission/installer excluded only by reviewed whitelist

- [ ] **Step 1 — Write a failing test:** In `test/ui_surface_architecture_test.dart`, add tests named for these required assertions: feature code calling raw route helpers fails; approved static shared host methods pass; comments/test literals do not trigger; Drawer navigation remains in shell, not modal host
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/ui_surface_architecture_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Reuse/analyze AST where feasible; scan constructors/invocations conservatively and explicitly record any tooling limitation. Do not ban legitimate Flutter pop/overlay mechanics inside Foundation itself.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart test/ui_surface_architecture_test.dart`, `fvm flutter test -r expanded test/ui_surface_architecture_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run negative fixtures for tricky namespace aliases and relative imports; guard against easy bypasses and false positives. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Finalize component documentation, gallery and deprecation map

**Files:**
- Modify: `docs/ui/FOUNDATIONS.md`
- Modify: `docs/ui/COMPONENTS.md`
- Modify: `docs/ui/PATTERNS.md`
- Modify: `docs/ui/RESPONSIVE.md`
- Modify: `docs/ui/CONTRIBUTING.md`
- Modify: `docs/ui/MIGRATION_MATRIX.md`
- Modify: `tool/ui_gallery/main.dart`
- Test: `test/ui_documentation_contract_test.dart`

**Interfaces:**
- Consumes: final public APIs and reference examples; docs/architecture legacy pages and ADR supersession
- Produces: one linkable catalog each Stable component with API/states/owner/tests/examples; gallery covers compact/expanded/OLED/keyboard; migrated route inventory 100%

- [ ] **Step 1 — Write a failing test:** In `test/ui_documentation_contract_test.dart`, add tests named for these required assertions: all documented imports compile and point to actual files; no stale MediaPoster/legacy modal snippets; ADR supersession paths valid; exceptions have owner/reason/trigger/tests
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/ui_documentation_contract_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Use a script to validate Markdown code path links and simple import examples; update canonical `DESIGN_SYSTEM.md`, `UI_ARCHITECTURE.md`, `PRESENTATION.md` links or merge content rather than duplicate authority.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format tool/ui_gallery/main.dart test/ui_documentation_contract_test.dart`, `fvm flutter test -r expanded test/ui_documentation_contract_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Perform documentation reading exercise: a new contributor can locate one accepted button, modal and page template unambiguously. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Complete Flutter tests/builds and release smoke for Android+Windows

**Files:**
- Create: `docs/ui/FINAL_ACCEPTANCE_REPORT.md`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: all migrated routes, published theme/system and regression matrix
- Produces: verified command log with host/SDK/revision, exit codes and artifacts; full analyzer/tests, Android debug+release and Windows debug build where supported

- [ ] **Step 1 — Define executable gate evidence:** In `FINAL_ACCEPTANCE_REPORT.md`, record the actual checkout revision, FVM/Flutter/Dart versions, host OS, available build targets, required artifact names and command log template. This is a release gate, **not** a fake TDD assertion of external process success.
- [ ] **Step 2 — Check source and tests:** Run `fvm dart format --output=none --set-exit-if-changed lib test`, `fvm flutter analyze --no-pub`, `fvm flutter test -r expanded` and `fvm flutter test -r expanded test/architecture_guard_test.dart`; capture each exit code and failure count. Formatting verification is read-only; format only changed files in implementation tasks.
- [ ] **Step 3 — Build on capable hosts:** Run `fvm flutter build apk --debug`, `fvm flutter build apk --release` on an Android-capable host, and `fvm flutter build windows --debug` on Windows with required desktop toolchain. Record each real command result and artifact. Do not infer success from a different target or machine.
- [ ] **Step 4 — Evaluate gate:** Confirm every mandatory command completed with exit code 0, required artifacts exist, and no unsupported target was silently treated as PASS. If a host/tool is missing, mark that platform `BLOCKED` and the overall gate not passed; do not add a widget test that pretends to verify a native build.
- [ ] **Step 5 — Self-review checkpoint:** Attach failure logs, skipped test explanations, affected revision and next action to the report. Release build success does not replace 07/06 device E2E or 07/07 matched performance evidence. **Do not commit automatically.**

### Task 06: Perform Android/Windows accessibility and full E2E matrix

**Files:**
- Modify: `docs/ui/FINAL_ACCEPTANCE_REPORT.md`
- Test: `test/features/catalog/catalog_source_picker_adaptive_test.dart`

**Interfaces:**
- Consumes: hardware Redmi Note 9S LineageOS and Windows host; source/plugin/reader/appearance E2E workflows
- Produces: recorded E2E evidence with screenshot/video/log reference for touch/keyboard/TalkBack, Back/IME, window resize and consent

- [ ] **Step 1 — Define machine vs manual evidence:** Extend picker widget tests only for widget-testable widths/text scaling/theme/keyboard. Put TalkBack, native installer consent, hardware Back/IME and Reader-first on-device outcomes into a dated Android/Windows manual E2E matrix; do not claim widget tests verify native OS behavior.
- [ ] **Step 2 — Verify:** Run `fvm flutter test -r expanded test/features/catalog/catalog_source_picker_adaptive_test.dart` and record exit status; perform and record manual E2E separately. A missing device means the hardware gate remains BLOCKED.
- [ ] **Step 3 — Implement:** Manually exercise system dialogs and actual provider plugin in non-production-safe fixtures as permitted; collect stable test identifiers and document precise failure step/log if a gate fails.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/features/catalog/catalog_source_picker_adaptive_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_source_picker_adaptive_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Flag existing offline Continue caching behavior as PASS only with reproduced cache-success, otherwise known issue or blocked acceptance per product decision. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Compare performance against matched baseline; final architecture sign-off

**Files:**
- Modify: `docs/quality/UI_PERFORMANCE_BASELINE.md`
- Modify: `docs/ui/FINAL_ACCEPTANCE_REPORT.md`
- Test: `test/core/ui/media/hikari_media_stress_test.dart`

**Interfaces:**
- Consumes: 00 baseline measured on same device/build/input and postmigration measurements
- Produces: before/after frame build/raster distribution, jank, memory/image decode, first load and navigation latency with reproducible traces

- [ ] **Step 1 — Define matched measurement:** Keep widget stress assertions for lazy rendering/overflow only. Write a reproducible profile procedure recording same device/OS, build mode, dataset, cache warmth and input sequence; profile metrics and signoff belong in `UI_PERFORMANCE_BASELINE.md`/`FINAL_ACCEPTANCE_REPORT.md`, not unit-test assertions.
- [ ] **Step 2 — Verify:** Rerun `fvm flutter test -r expanded test/core/ui/media/hikari_media_stress_test.dart`; collect actual DevTools/profile trace separately. Missing comparable baseline/post-run is BLOCKED, not PASS.
- [ ] **Step 3 — Implement:** Run matched Flutter DevTools/profile captures on Redmi Note 9S and Windows where possible; calculate deltas and discuss any regression. Close initiative only when architecture, docs, E2E and profile acceptance all pass.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/core/ui/media/hikari_media_stress_test.dart`, `fvm flutter test -r expanded test/core/ui/media/hikari_media_stress_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Final self-review: single active UI Foundation, zero unowned paths, one ADR authority and no claimed test result without logs. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

