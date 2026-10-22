# 03 — Media Anatomy, Responsive Layouts & Pilot B Implementation Plan

> **For Gravity:** Use `superpowers:executing-plans` to work through this plan sequentially with explicit review gates; do not start agents, worktrees, commits or branches without separate user authorization. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build one canonical media/card/list/layout system independent of domain classes, then validate on real Library and Search consumers.

**Architecture:** Artwork providers and domain identity are selected in features; core/ui/media owns generic decode/placeholder/render contracts; card anatomy and grid/shelf layout are separate. Chapter formatting remains in feature presentation adapters.

**Tech Stack:** Flutter 3.47.5 via FVM; Dart >=3.13.4; Material 3; existing ChangeNotifier/ViewModel and flutter_test; Android + Windows. No new runtime dependency without separate review.

**Spec:** `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` (normative); orchestration: `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md`.

**Prerequisites:** 01 token/control foundations and 02 page shell/presenter available; 00 baseline fixtures recorded.

## Global Constraints

- Deliberate UI rebuild: target architecture takes priority over superseded historical UI decisions, but must preserve domain, persistence, plugin trust, native consent, cache/source identity and reader progress.
- The snapshot is `Hikari-dev(20261021-163525).zip`; validate paths against the actual working branch before modifying. This plan does **not** assert a live Git revision.
- No new branches, no worktrees, no automated commit/push/merge. Work on the user's current checkout; checkpoint through test reports and diff self-review.
- Respect Flutter's existing Material primitives; do not create trivial forwarding widgets. One canonical UI implementation at the end, no permanent compatibility façade.
- Test-first for changed behavior/API: add a focused failing assertion, confirm the expected failure, implement, then rerun. For characterization/baseline or manual evidence, record a truthful GREEN baseline/NOT RUN instead; never invent a RED or weaken tests.
- Review layout at narrow 375dp, breakpoint boundaries 599/600/601 and 839/840/841, wide 1200/1440dp; 1x/2x/3x text scales; short-height/IME as relevant.
- Prefer pure testable policy/resolvers; all feature-specific business authorization stays with its feature/application owner.
- Preserve existing cache and artwork decode correctness without requiring storage/database changes.

## Review Focus

- Missing or corrupt artwork breaks cards → placeholder, identity and error tests.
- Massive grids eagerly build all posters → lazy 5k item fixture.
- 2x/3x text scaling overflows fixed tiles → dynamic reserve/reflow tests.
- Chapter presentation leaks MangaChapter/NovelChapter into core UI → negative dependency fixture.
- Hero duplicate tags and source identity mismatch → paired thumbnail/detail tests.

---
### Task 01: Establish HikariArtwork with bounded decoding and fallback

**Files:**
- Create: `lib/core/ui/media/hikari_artwork.dart`
- Create: `lib/core/ui/media/hikari_artwork_decode_policy.dart`
- Create/Test: `test/core/ui/media/hikari_artwork_test.dart`

**Interfaces:**
- Consumes: `media_artwork_decode.dart` current behavior; provider identity and byte/image loading remain in feature/application layer
- Produces: `HikariArtwork({ImageProvider? image, String? semanticLabel, BoxFit fit = BoxFit.cover, Widget? placeholder, ...})`; when `image == null`, render the documented placeholder-only state without triggering a load and pure `decodeTargetSize({required Size logicalSize, required double devicePixelRatio})`

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/media/hikari_artwork_test.dart`, add tests named for these required assertions: missing/slow/corrupt image renders fallback; decode dimensions bounded to rendered logical constraints and DPR; no redundant fetch on same provider identity; decorative artwork avoids duplicate semantics
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/media/hikari_artwork_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Extract rendering/decode-sizing mechanics only; keep source-specific bytes/provider mapping outside core and preserve existing cache loaders and hero tags.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/media/hikari_artwork.dart lib/core/ui/media/hikari_artwork_decode_policy.dart test/core/ui/media/hikari_artwork_test.dart`, `fvm flutter test -r expanded test/core/ui/media/hikari_artwork_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Compare memory decode policy to existing `media_artwork_decode` tests; document which provider cache owns repeated images. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 02: Define canonical poster/landscape cards and semantic progress

**Files:**
- Create: `lib/core/ui/media/hikari_poster_card.dart`
- Create: `lib/core/ui/media/hikari_landscape_card.dart`
- Create: `lib/core/ui/media/hikari_media_progress.dart`
- Create/Test: `test/core/ui/media/hikari_media_cards_test.dart`

**Interfaces:**
- Consumes: HikariArtwork and Hikari foundation tokens
- Produces: `HikariPosterCard({required String title, required Widget artwork, String? subtitle, Widget? badge, double? progress, VoidCallback? onTap, String? heroTag})`; landscape same conceptual slots with 16:9 default; progress 0..1 normalized

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/media/hikari_media_cards_test.dart`, add tests named for these required assertions: poster ratio 2:3 and landscape ratio 16:9; title maxLines 2 with semantic full text; progress correctly clamps/validates; no domain import; tap/focus/long title semantics
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/media/hikari_media_cards_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Compose a constrained artwork region and text/overlay slots; make validated variants rather than many independent booleans; keep Hero tag caller-owned to prevent collision.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/media/hikari_poster_card.dart lib/core/ui/media/hikari_landscape_card.dart lib/core/ui/media/hikari_media_progress.dart test/core/ui/media/hikari_media_cards_test.dart`, `fvm flutter test -r expanded test/core/ui/media/hikari_media_cards_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run `test/media_poster_test.dart` baseline and compare badge color/progress behavior; avoid premature removal of old MediaPoster. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 03: Document and test adaptive title sizing & aspect ratios

**Files:**
- Create: `lib/core/ui/foundation/hikari_aspect_ratios.dart`
- Create: `lib/core/ui/media/hikari_card_metrics.dart` (only if pure measurements are needed)
- Create/Test: `test/core/ui/media/hikari_card_geometry_test.dart`

**Interfaces:**
- Consumes: poster/landscape card API and text ramp
- Produces: named ratio constants `poster=2/3`, `landscape=16/9`, `square=1`; bounded card geometry or pure metric function if non-native measurement remains necessary

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/media/hikari_card_geometry_test.dart`, add tests named for these required assertions: 2-line title at 1x/2x/3x does not clip; long Vietnamese/RTL fixtures; 375dp compact fallback; media artwork ratio remains independent of title region; arbitrary TextPainter per item removed
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/media/hikari_card_geometry_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Prefer constraints/AspectRatio; if cached TextPainter is demonstrably needed, scope cache and justify it with tests; never blanket-ban measurement.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/foundation/hikari_aspect_ratios.dart lib/core/ui/media/hikari_card_metrics.dart test/core/ui/media/hikari_card_geometry_test.dart`, `fvm flutter test -r expanded test/core/ui/media/hikari_card_geometry_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Stress line-break variations and font fallback. Avoid global mutable cache growing unbounded. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 04: Implement lazy HikariAdaptiveGrid with stable item identity

**Files:**
- Create: `lib/core/ui/layout/hikari_adaptive_grid.dart`
- Create/Test: `test/core/ui/layout/hikari_adaptive_grid_test.dart`

**Interfaces:**
- Consumes: foundation poster min/max extents/gutters; `SliverGrid` and LayoutBuilder
- Produces: `HikariAdaptiveGrid.builder({required int itemCount, required IndexedWidgetBuilder itemBuilder, Key Function(int)? itemKeyBuilder})` (plus named layout bounds) preserving keys and lazy build

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/layout/hikari_adaptive_grid_test.dart`, add tests named for these required assertions: 375/599/600/840/1440 widths select sane columns without overflow; 5,000 items do not all build; item keys/scroll positions stable across filter/resize; 3x text reflows instead of clipped buttons
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/layout/hikari_adaptive_grid_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Use layout-driven SliverGridDelegate, no platform checks; contract must work within CustomScrollView and as a standalone grid where needed without nested unbounded scrollables.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/layout/hikari_adaptive_grid.dart test/core/ui/layout/hikari_adaptive_grid_test.dart`, `fvm flutter test -r expanded test/core/ui/layout/hikari_adaptive_grid_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Validate API signature with actual Sliver/ScrollView consumers before publishing; avoid requiring both a grid ScrollController and its parent to own scrolling. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 05: Implement accessible HikariHorizontalShelf and section slots

**Files:**
- Create: `lib/core/ui/layout/hikari_horizontal_shelf.dart`
- Create/Test: `test/core/ui/layout/hikari_horizontal_shelf_test.dart`

**Interfaces:**
- Consumes: poster card, section header, TextScaler, Material pointer scroll policies
- Produces: `HikariHorizontalShelf({required int itemCount, required IndexedWidgetBuilder itemBuilder, Widget? header, double? itemExtent, ...})` lazy builder with keyboard-scroll affordances

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/layout/hikari_horizontal_shelf_test.dart`, add tests named for these required assertions: 5k items lazily built; wheel/trackpad scroll works Windows; focus traversal not trapped; titles 3x scale/height reflow; nested vertical scroll remains smooth; section padding aligns with content region
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/layout/hikari_horizontal_shelf_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Implement horizontal Sliver/ListView.builder with named geometry policy, optional explicit itemExtent only if card family guarantees size.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/layout/hikari_horizontal_shelf.dart test/core/ui/layout/hikari_horizontal_shelf_test.dart`, `fvm flutter test -r expanded test/core/ui/layout/hikari_horizontal_shelf_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Measure relayouts in test harness; avoid implicit TextPainter per shelf item. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 06: Replace domain-aware ChapterRow with typed visual row

**Files:**
- Create: `lib/core/ui/content/hikari_chapter_row.dart`
- Create: `lib/features/catalog/presentation/catalog_chapter_row_data.dart`
- Create/Test: `test/core/ui/content/hikari_chapter_row_test.dart`

**Interfaces:**
- Consumes: `ChapterRow` currently `Object chapter` switches on MangaChapter/NovelChapter and imports domain
- Produces: `HikariChapterRow({super.key, required String title, required String subtitle, required bool enabled, VoidCallback? onTap, bool isContinuation = false})`; feature presentation maps domain typed fields to display props

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/content/hikari_chapter_row_test.dart`, add tests named for these required assertions: core ui has no domain imports; manga/novel chapter subtitle/date/scanlator labels preserved by feature mapper; unreadable row cannot activate; continuation status announced; feature caller supplies stable source/chapter `ValueKey`, not a core-derived domain key
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/content/hikari_chapter_row_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Extract visual row into generic content; implement mapper in catalog feature. Other source detail/readers get own mappers only when their migration tasks run.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/core/ui/content/hikari_chapter_row.dart lib/features/catalog/presentation/catalog_chapter_row_data.dart test/core/ui/content/hikari_chapter_row_test.dart`, `fvm flutter test -r expanded test/core/ui/content/hikari_chapter_row_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Validate all MangaChapter/NovelChapter branches and chapter ID semantics against existing `test/core/ui/patterns/chapter_list_patterns_test.dart`. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 07: Pilot B: migrate Library content and Catalog Search media anatomy

**Files:**
- Modify: `lib/features/library/widgets/library_content.dart`
- Modify: `lib/features/catalog/widgets/catalog_search_content.dart`
- Modify: `lib/features/library/library_page.dart`
- Test: `test/library_page_test.dart`

**Interfaces:**
- Consumes: `HikariPosterCard`, `HikariAdaptiveGrid`, feature artwork provider/identity
- Produces: two real consumers demonstrate canonical card+grid API and route callbacks; no feature-to-core circular dependency

- [ ] **Step 1 — Write a failing test:** In `test/library_page_test.dart`, add tests named for these required assertions: same title, subtitle, badges, reading progress, image identity, library saved state and selected filter; Catalog search search-on-submit unchanged; compact/wide layouts predictable
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/library_page_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Map domain data to immutable props in feature presentation (create `lib/features/library/presentation/library_media_item.dart` if needed); replace visual card+grid only after tests, retain existing source load.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format lib/features/library/widgets/library_content.dart lib/features/catalog/widgets/catalog_search_content.dart lib/features/library/library_page.dart test/library_page_test.dart`, `fvm flutter test -r expanded test/library_page_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Run catalog search tests, compare artwork keys and hero tags. Record remaining MediaPoster consumers and list their scheduled removal tasks, not a permanent adapter. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

### Task 08: Complete media stress/gallery and performance micro-bench

**Files:**
- Modify: `tool/ui_gallery/main.dart`
- Create: `test/core/ui/media/hikari_media_stress_test.dart`
- Create: `docs/ui/components/media.md`
- Test: `test/core/ui/media/hikari_media_stress_test.dart`

**Interfaces:**
- Consumes: all media/layout APIs plus two pilot consumers
- Produces: gallery stress fixtures: missing/corrupt covers, long Vietnamese, RTL, text scale 3, 5k items; replayable layout/decode benchmark protocol

- [ ] **Step 1 — Write a failing test:** In `test/core/ui/media/hikari_media_stress_test.dart`, add tests named for these required assertions: no overflow/errors under stress; lazy builder count stays bounded; no double decode for same cached image provider; public component API documented
- [ ] **Step 2 — Verify RED:** Run `fvm flutter test -r expanded test/core/ui/media/hikari_media_stress_test.dart`; expect at least one *new* assertion FAILS for the intended missing behavior/API (not an unrelated environment failure).
- [ ] **Step 3 — Implement:** Add reusable gallery pages and micro-bench instrumentation. Keep real performance gate for physical Redmi Note 9S profile (07 plan) rather than treating widget pump times as device proof.
- [ ] **Step 4 — Verify GREEN:** Run `fvm dart format tool/ui_gallery/main.dart test/core/ui/media/hikari_media_stress_test.dart`, `fvm flutter test -r expanded test/core/ui/media/hikari_media_stress_test.dart`, then `fvm flutter analyze --no-pub`. Expect no errors; preserve existing callers until their migration task owns deletion.
- [ ] **Step 5 — Self-review checkpoint:** Review duplicated artifacts/image cache lifecycles and geometric measurements. Mark performance numbers provisional until profile gate. Record changed paths, the red/green evidence, remaining callers, and verified vs unverified platform behaviors. **Do not commit automatically.**

