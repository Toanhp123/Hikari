# 04 — Feature Migration Waves A/B Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Migrate browsing, search, detail and continuation pages to the canonical UI Foundation without altering reader or persistence business semantics.

**Architecture:** Each feature retains owned ViewModel/repositories, maps domain data into typed presentation models and composes shared shell/card/row/control/surface components. After each route passes, delete its bespoke shared UI wrapper.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** Plans 01–03 complete including Picker and Library/Search pilots.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.
- Preserve Home Continue-first and Catalog-first versus Source-first route policy.

## Review Focus

- Home continue artwork disappears when new card provider mapped wrongly → image identity test.
- Library confirmation differs by origin → shared cancel/no-side-effect assertions.
- Search field starts remote requests per keystroke → explicit submit test.
- Catalog detail tab scroll resets → independent tab restoration test.
- Source-first route accidentally bypasses native detail → route regression fixture.
- **Transferred Phase 02 known defect (owner accepted as pending fix, not visually PASS):**
  Catalog Detail whole-page hero/notice overflows at 375dp with 3x text scaling.
  Fix in Task 04/05, not Phase 03; add focused RED/GREEN and do not suppress
  overflow or cap text scale to hide it. See
  [owner handoff](../../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).

---
### Task 01: Migrate Home page shell, hero and shelves

**Files:**
- Modify: `lib/features/home/home_page.dart`
- Modify: `lib/features/home/widgets/continue_shelf.dart`
- Modify: `lib/features/home/widgets/catalog_discovery_sections.dart`
- Modify: `lib/features/home/widgets/home_header.dart`
- Test: `test/home_page_test.dart`

**Interfaces:**
- Consumes: HikariPageScaffold/Header, HikariHorizontalShelf, HikariPosterCard, existing HomeViewModel
- Produces: Home uses canonical shell/shelf/cards, feature-specific hero remains feature composition

- [ ] **Step 1 — Write a failing test:** In `test/home_page_test.dart`, add tests named for these required assertions: Continue Reading before discovery; no idle empty placeholder on success; offline/error only when appropriate; multiple shelves lazy; artwork and hero stable; narrow/wide header aligned
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/home_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Replace geometry/text sizing from feature shelves with shared contracts and pure presentation props; preserve VM fetching, refresh behavior and navigation actions.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/home/home_page.dart lib/features/home/widgets/continue_shelf.dart lib/features/home/widgets/catalog_discovery_sections.dart lib/features/home/widgets/home_header.dart test/home_page_test.dart`, `fvm flutter test -r expanded test/home_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Capture before/after visual fixture and profile scroll behavior; avoid moving catalog discovery logic into core UI. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Complete Library and cross-origin confirmation migration

**Files:**
- Modify: `lib/features/library/library_page.dart`
- Modify: `lib/features/library/widgets/library_content.dart`
- Modify: `lib/features/library/widgets/library_button.dart`
- Modify: `lib/features/library/widgets/library_removal_confirmation.dart`
- Test: `test/library_page_test.dart`

**Interfaces:**
- Consumes: Pilot B card/grid, canonical Dialog confirmation and LibraryButtonViewModel
- Produces: all library mutations from catalog/source/library routes use a single presentation workflow and feature/application command; shared cancel semantics

- [ ] **Step 1 — Write a failing test:** In `test/library_page_test.dart`, add tests named for these required assertions: remove from any entrypoint presents same confirmation; Cancel/Back retains saved identity; badge/artwork not lost; filter/sort/scroll preserved; tablet/desktop accessible focus
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/library_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Replace special-case UI-only confirmation helpers with canonical content while keeping mutation logic owned by existing Library Button VM/application layer. Update all call sites discovered by inventory.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/library/library_page.dart lib/features/library/widgets/library_content.dart lib/features/library/widgets/library_button.dart lib/features/library/widgets/library_removal_confirmation.dart test/library_page_test.dart`, `fvm flutter test -r expanded test/library_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** List all paths offering library action and prove each covered; do not accidentally introduce dialog in a route that had an intentionally different destructive safety contract without approval. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Migrate Local Media browse shell, search and gallery

**Files:**
- Modify: `lib/features/local_media/local_media_page.dart`
- Modify: `lib/features/local_media/widgets/local_media_content.dart`
- Modify: `lib/features/local_media/widgets/local_media_folder_summary.dart`
- Test: `test/local_media_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: HikariAdaptiveGrid, HikariPosterCard, HikariSearchField, LocalMediaViewModel and local file loader
- Produces: Local media page responsive; native folder/file identities, scanning and readers unchanged

- [ ] **Step 1 — Write a failing test:** In `test/local_media_page_test.dart`, add tests named for these required assertions: directory scan count and one-query-per-directory behavior preserved; no N+1 artwork loads; permission/cancellation/error UX; filters central alignment; item identity stable when resizing
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/local_media_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Only rewrite presentation composition; retain original local scanning strategy and IO owners. Adapt artwork providers at feature boundary, not core UI.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/local_media/local_media_page.dart lib/features/local_media/widgets/local_media_content.dart lib/features/local_media/widgets/local_media_folder_summary.dart test/local_media_page_test.dart`, `fvm flutter test -r expanded test/local_media_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Include physical-device large-folder performance scenario, not just a tiny fake list. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Migrate Catalog Search and Source Search controlled-input contracts

**Files:**
- Modify: `lib/features/catalog/catalog_search_page.dart`
- Modify: `lib/features/catalog/widgets/catalog_search_content.dart`
- Modify: `lib/features/source_search/source_search_page.dart`
- Modify: `lib/features/source_search/widgets/source_search_content.dart`
- Test: `test/features/catalog/catalog_search_page_test.dart`

**Interfaces:**
- Consumes: HikariSearchField/ChoiceMenu/ChipRow, HikariPageScaffold and respective search ViewModels
- Produces: both search pages share visual controls/layout and maintain feature-specific asynchronous request semantics

- [ ] **Step 1 — Write a failing test:** In `test/features/catalog/catalog_search_page_test.dart`, add tests named for these required assertions: typing alone does not query when explicit submit required; Enter/search action single request; stale async response ignored when query/source changes; external controller clear state correct; keyboard/overflow at 375dp
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/catalog/catalog_search_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Unify control props and responsive content regions; do not merge distinct catalog and extension/source identity services into one generic ViewModel.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/catalog/catalog_search_page.dart lib/features/catalog/widgets/catalog_search_content.dart lib/features/source_search/source_search_page.dart lib/features/source_search/widgets/source_search_content.dart test/features/catalog/catalog_search_page_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_search_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run source-search tests and accessibility focus tests; check search picker manual fallback still reaches source-first flow correctly. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Refactor Catalog Detail composition into owned sections

**Files:**
- Modify: `lib/features/catalog/catalog_detail_page.dart`
- Modify: `lib/features/catalog/widgets/catalog_detail_content.dart`
- Modify: `lib/features/catalog/widgets/catalog_detail_sections.dart`
- Modify: `lib/features/catalog/widgets/catalog_detail_hero.dart`
- Test: `test/features/catalog/catalog_detail_page_test.dart`

**Interfaces:**
- Consumes: Pilot A Picker, HikariChapterRow, HikariPageScaffold, existing detail+chapters ViewModels
- Produces: route-level coordinator stays responsible for params/generation/openReader; info and chapters sections render typed state; no duplicated surface or geometry code

- [ ] **Step 1 — Write a failing test:** In `test/features/catalog/catalog_detail_page_test.dart`, add tests named for these required assertions: independent Info/Chapters scroll offset; choose source loads expected cached chapters before network when available; catalog-first selects source then reader; one open command per activation; hero same source identity; **whole-page 375dp at 3x text scale hero + notice reflow without RenderFlex overflow, clipping or hidden actions**, including both tabs and preserved state/scroll after switching.
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Decompose by state/lifecycle (not line count). Keep NestedScrollView overlap/TabController ownership stable, route callbacks typed and no builder-initiated side effects. Fix the previously observed Catalog hero/notice 3x whole-page overflow with responsive wrapping/scroll where appropriate; **do not clamp text scale, ignore Flutter exceptions, crop screenshots or hide actions** to pass.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/catalog/catalog_detail_page.dart lib/features/catalog/widgets/catalog_detail_content.dart lib/features/catalog/widgets/catalog_detail_sections.dart lib/features/catalog/widgets/catalog_detail_hero.dart test/features/catalog/catalog_detail_page_test.dart`, `fvm flutter test -r expanded test/features/catalog/catalog_detail_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Check test suite for 50k chapters/order reversal and stale async outcomes; minimize accidental scroll behavior changes. Review 375/599/600/840/1440dp at 1x/2x/3x with actual parent scaling; inspect hero/notice/action availability and both tab positions. Do not reuse Phase 02 unscaled-caller picker stress fixture as proof of full-page scaling. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Migrate source-native manga and novel details

**Files:**
- Modify: `lib/features/remote_manga/manga_series_page.dart`
- Modify: `lib/features/remote_manga/widgets/manga_series_content.dart`
- Modify: `lib/features/remote_novel/novel_series_page.dart`
- Modify: `lib/features/remote_novel/widgets/novel_series_content.dart`
- Test: `test/features/remote_manga/manga_series_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: HikariChapterRow, media cards, shared header/shell, source-first routing and existing remote-series ViewModels
- Produces: source-native detail pages render canonically while preserving exact provider/series identity

- [ ] **Step 1 — Write a failing test:** In `test/features/remote_manga/manga_series_page_test.dart`, add tests named for these required assertions: standalone Source Search → native Source Detail → Reader; Catalog-first does NOT route via native Detail; chapter list stale/error/disabled states intact; reader selected source never changes on resize
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/features/remote_manga/manga_series_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Use feature-specific typed chapter mappers; do not erase reader/progress resolution or alter cache identity.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/remote_manga/manga_series_page.dart lib/features/remote_manga/widgets/manga_series_content.dart lib/features/remote_novel/novel_series_page.dart lib/features/remote_novel/widgets/novel_series_content.dart test/features/remote_manga/manga_series_page_test.dart`, `fvm flutter test -r expanded test/features/remote_manga/manga_series_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Add symmetrical novel integration test and verify source-first chapters ordering. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Migrate Continue Reading entry and navigation-independent chrome

**Files:**
- Modify: `lib/features/reading/continue_reading_page.dart`
- Modify: `lib/app/navigation/app_navigation_shell.dart` (if needed for content insets)
- Test: `test/continue_reading_page_test.dart` (create if absent)

**Interfaces:**
- Consumes: ADR-023 Reader-first, existing progress/continuation repository and shared feedback/page shell
- Produces: Continue Reading route uses canonical loading/errors/shell while navigating directly to exact Reader/source

- [ ] **Step 1 — Write a failing test:** In `test/continue_reading_page_test.dart`, add tests named for these required assertions: saved source chapter identity used; offline attempt does not detour through Catalog Detail; fallback for missing chapter preserves ordering and explicit error; Back and reopening state safe
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/continue_reading_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Refactor UI only; reuse existing continuation resolver. Keep known offline-cached-image defect separately tracked; do not silently change cache semantics.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/reading/continue_reading_page.dart lib/app/navigation/app_navigation_shell.dart test/continue_reading_page_test.dart`, `fvm flutter test -r expanded test/continue_reading_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Document any offline scenario not fully verifiable in widget tests for Android integration acceptance. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 08: Wave A/B cross-feature gate and retire first legacy consumers

**Files:**
- Modify: `docs/ui/MIGRATION_MATRIX.md`
- Modify: `docs/ui/PATTERNS.md`
- Test: `test/architecture_guard_test.dart`

**Interfaces:**
- Consumes: migrated Home/Library/Local/Catalog/Source pages and interim guard
- Produces: migration matrix lists zero bespoke surface routes for wave A/B, remaining legacy media/controls only for unmigrated routes

- [ ] **Step 1 — Write a failing test:** In `test/architecture_guard_test.dart`, add tests named for these required assertions: all migrated feature files have no app/theme or core legacy patterns imports; no direct showDialog/showModalBottomSheet unless documented platform exception; Home/Library/Catalog/Source tests pass
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/architecture_guard_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Re-run inventory tool and advance staged guard per migrated path. Remove now-unreferenced adapters where safe, but do not delete shared old class while other waves import it.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format test/architecture_guard_test.dart`, `fvm flutter test -r expanded test/architecture_guard_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run full `fvm flutter test` for gate. Any red is a blocker; list remaining paths and owners rather than marking finished. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

