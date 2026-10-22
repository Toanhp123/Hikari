# 06 — Reader & Player Chrome Migration Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Standardize reader/player surrounding controls, settings surfaces, feedback and route shell where appropriate while retaining immersive engines, gestures and progress truth.

**Architecture:** Distinguish a dedicated renderer/gesture canvas from generic UI chrome; shared controls own accessibility and transient routes, feature-owned reading operations remain authoritative.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** Plans 01–05 complete; ADR-023 continuation and cache/source snapshot contracts intact.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.

## Review Focus

- Reader swipe/gesture handlers become unresponsive under overlay → gesture priority integration tests.
- Chapter next/previous order reverses in UI migration → canonical sequence property tests.
- Prefetch/cache state disposed on viewport reflow → lifetime/identity tests.
- Video focus overlay blocks media controls → keyboard/gesture overlay tests.
- Offline continue misleadingly shown as success without cache → precise fallback/error fixture.


---
### Task 01: Migrate manga reader chrome, preserving page-canvas gestures

**Files:**
- Modify: `lib/features/manga_reader/manga_reader_page.dart`
- Modify: `lib/features/manga_reader/widgets/manga_reader_chrome.dart`
- Create/Test: `test/features/manga_reader/manga_reader_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: Hikari controls, feedback/surfaces; existing reading progress/controller and page rendering path
- Produces: renderer/gesture canvas remains feature-owned, chrome uses canonical button, progress, settings surface and focus patterns

- [ ] **Step 1 — Write a failing test:** In `test/features/manga_reader/manga_reader_page_test.dart`, add tests named for these required assertions: tap/swipe navigation unaffected; back/toggle chrome not doubled; saved page/progress persists; screen rotation/reflow preserves current chapter/page; no redundant image reload due to UI rebuild
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/manga_reader/manga_reader_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Adapt only surrounding chrome and dialogs, retain page renderer and prefetch services. Use explicit ReaderCanvas technical exception as documented in UI component catalog.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/manga_reader/manga_reader_page.dart lib/features/manga_reader/widgets/manga_reader_chrome.dart test/features/manga_reader/manga_reader_page_test.dart`, `fvm flutter test -r expanded test/features/manga_reader/manga_reader_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Test on Redmi Note 9S hardware after widget gate; avoid applying generic HikariPageScaffold padding to fullscreen manga images. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Migrate remote manga chapter reader UI route

**Files:**
- Modify: `lib/features/remote_manga/manga_chapter_reader_page.dart`
- Modify: `lib/features/remote_manga/manga_chapter_reader_view_model.dart` (UI state only if necessary)
- Create/Test: `test/features/remote_manga/manga_chapter_reader_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: canonical reader chrome and source identity/reading sequence services
- Produces: remote manga reader actions use shared surfaces/chapter controls while preserving source-specific loading/caching state

- [ ] **Step 1 — Write a failing test:** In `test/features/remote_manga/manga_chapter_reader_page_test.dart`, add tests named for these required assertions: next/previous follow canonical chapter order independent of view reversal; unreadable chapter ignored; stale prefetch cannot advance new series; offline cached pages show accurate recoverable error
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/remote_manga/manga_chapter_reader_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Do not change providers or reading sequence; only extract visual control responsibilities. Ensure new chrome does not eagerly decode next chapter.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/remote_manga/manga_chapter_reader_page.dart lib/features/remote_manga/manga_chapter_reader_view_model.dart test/features/remote_manga/manga_chapter_reader_page_test.dart`, `fvm flutter test -r expanded test/features/remote_manga/manga_chapter_reader_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Include large chapter sequence stress tests and cache reset while image in use if current tests cover. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Migrate novel reader chrome and preference sheet

**Files:**
- Modify: `lib/features/novel_reader/novel_reader_page.dart`
- Modify: `lib/features/novel_reader/widgets/novel_reader_preferences_sheet.dart`
- Modify: `lib/features/novel_reader/widgets/novel_reader_progress.dart`
- Create/Test: `test/features/novel_reader/novel_reader_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: typed transient presenter, Hikari controls, novel reader preferences and progress VM
- Produces: font/margin/theme preferences use shared selection/form surfaces; content pagination/reader text canvas remains owned by reader

- [ ] **Step 1 — Write a failing test:** In `test/features/novel_reader/novel_reader_page_test.dart`, add tests named for these required assertions: font size updates text without scroll/position corruption; Back/Escape cancels unsubmitted draft when required; 3x text scale/short-height usable; chapter progress and prefetch stable
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/novel_reader/novel_reader_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Replace custom sheet route chrome, not reader-specific typography/selection mechanics. Keep local text layout rules as documented justified exception.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/novel_reader/novel_reader_page.dart lib/features/novel_reader/widgets/novel_reader_preferences_sheet.dart lib/features/novel_reader/widgets/novel_reader_progress.dart test/features/novel_reader/novel_reader_page_test.dart`, `fvm flutter test -r expanded test/features/novel_reader/novel_reader_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Test keyboard and touch selection and document precise restoration strategy for font changes. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Migrate publication and remote novel chapter routes

**Files:**
- Modify: `lib/features/novel_reader/publication_reader_page.dart`
- Modify: `lib/features/novel_reader/widgets/publication_reader_content.dart`
- Modify: `lib/features/remote_novel/novel_chapter_reader_page.dart`
- Create/Test: `test/features/remote_novel/novel_chapter_reader_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: novel reader chrome and existing publication/remote chapter ViewModels
- Produces: shared UI chrome/feedback across publication and remote novel while keeping distinct content sources

- [ ] **Step 1 — Write a failing test:** In `test/features/remote_novel/novel_chapter_reader_page_test.dart`, add tests named for these required assertions: progress reopens from persisted chapter; next/prev guards unaffected; loaded chapter visible while refresh; offline invalid cache error not masked by false empty page; text scaling respects reading preference
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/remote_novel/novel_chapter_reader_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Move only transient UI/actions/common headers, keep chapter fetch, HTML/text processing and storage ownership outside core UI.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/novel_reader/publication_reader_page.dart lib/features/novel_reader/widgets/publication_reader_content.dart lib/features/remote_novel/novel_chapter_reader_page.dart test/features/remote_novel/novel_chapter_reader_page_test.dart`, `fvm flutter test -r expanded test/features/remote_novel/novel_chapter_reader_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run publication route tests and long-chapter stress cases; do not rewrite parser/renderer for visual standardization. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Migrate player chrome with separate immersive video surface

**Files:**
- Modify: `lib/features/player/player_page.dart`
- Modify: `lib/features/player/widgets/player_controls.dart`
- Modify: `lib/features/player/widgets/video_surface.dart` (only integration boundaries)
- Create/Test: `test/features/player/player_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: Hikari control states/surfaces, existing media_kit player backend and video lifecycle
- Produces: video surface owns playback and aspect presentation; shared controls implement menus, icon buttons and focus/hover

- [ ] **Step 1 — Write a failing test:** In `test/features/player/player_page_test.dart`, add tests named for these required assertions: fullscreen window retains playback and focus; pause/play shortcut works; subtitle/track selection cancellation preserves prior value; media engine is not recreated on viewport width change
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/player/player_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Keep renderer/native surface outside standard content-region constraints; migrate settings/menu chrome through canonical controls, documenting engine-owned overlays.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/player/player_page.dart lib/features/player/widgets/player_controls.dart lib/features/player/widgets/video_surface.dart test/features/player/player_page_test.dart`, `fvm flutter test -r expanded test/features/player/player_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Require physical Windows keyboard/resize smoke; do not claim video hardware performance from widget pump. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Wave D and whole-screen interaction gate

**Files:**
- Modify: `docs/ui/MIGRATION_MATRIX.md`
- Modify: `docs/ui/PATTERNS.md`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: all 19 route migrations including specialized immersive exceptions
- Produces: completed route mapping with every reader/player canvas exception named/owned; no route-specific shared modal machinery

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: all routes accounted for; Reader/Player continue/progress/device interaction regression matrix passed or marked hardware pending; no unowned legacy UI API remains in these feature paths
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Re-run inventory and strict per-path UI guard. Review how reading overlays interact with root nav/escape; run reader/page integration suites and analyzer.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Sign off exact untested native scenarios for final acceptance rather than calling the full rebuild done. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

