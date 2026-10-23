# UI Foundation Phase 04

## A. Summary

Phase 04 execution completed, verified and accepted on `feat/ui-foundation-rebuild`.
Starting baseline commit is `c3f066a` (2026-10-23).
Scope: Tasks 04/01–04/08 only. All automated tests, analyzer, formatting, architecture
guards and documentation contracts are GREEN. Gate G4 is **G4 OWNER-ACCEPTED**
(formal owner acceptance 2026-10-24; physical TalkBack/runtime profiling deferred to Phase 07).
Handoff prepared for Phase 05.

Authority: [Phase 04 plan](../superpowers/plans/2026-10-22-hikari-ui-04-browse-detail-migration.md),
[master](../superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md),
[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md).

## B. Preflight

Canonical docs, Phase 01–03 reports, dated owner decisions, UI contracts,
architecture docs and full migration matrix inspected before production edits.
Graphify scoped queries used for navigation; actual source/callers/tests checked.
Current architecture rules supersede historical domain-mapping examples in
`UI_ARCHITECTURE.md`; those historical sections are not permission to introduce
new domain dependencies in core UI.

Working ownership inventory (paths relative to `lib/`; tests relative to `test/`):

| Caller / responsibility | Canonical target / owner task | Behavior checks | Outside consumers / safe retirement condition |
|---|---|---|---|
| `app/app.dart`: Home, Library, Local, Catalog Search, Source Search route construction | App retains navigation/injection; 04/01–04 | `reader_first_sequence_test.dart`, page tests | App callbacks and source leases remain; no route-policy migration |
| `features/home/home_page.dart`, `widgets/home_header.dart`: shell, pinned chrome, refresh, sliver order | PageScaffold, ContentRegion; specialist pinned header; 04/01 | `home_page_test.dart`: Continue-first, refresh, retained failure, bottom obstruction | Home-only bounded wrapper can retire after all callers replaced |
| Home `continue_shelf.dart`: saved item/progress mapping, exact continue callback | HorizontalShelf, LandscapeCard, Artwork; 04/01 | Exact saved chapter callback, full semantics, artwork identity | Feature progress formatting stays; no resolver changes |
| Home `home_library_section.dart`, `catalog_discovery_sections.dart`: recent filters and discovery sections/skeletons | HorizontalShelf, PosterCard, SectionHeader, NoticeBanner; 04/01 | Source-owned covers, lazy cards, stable hero, filter identity, empty success | Home-only geometry can retire; hero/controller remains feature-owned |
| `features/library/widgets/library_button.dart`, `library_removal_confirmation.dart`: mutation confirmation | Typed canonical confirmation; 04/02 | `library_page_test.dart`: Add/Remove all origins, cancel, persistence, artwork | Buttons also used by Local, Source Search and native detail; migrate shared workflow once |
| Library page/content: membership/progress, grid/list/actions | Existing Pilot B grid; ListRow, Artwork, shell/feedback; 04/02 | Exact source keys, overlay does not navigate, live persistence, progress retained | Repository/VM subscriptions and pending-removal identity remain feature-owned |
| Local page/content/folder summary: root selection and scan presentation | PageScaffold, ContentRegion, AdaptiveGrid.sliver, PosterCard, feedback; 04/03 | `local_media_page_test.dart`: canceled picker, root revision, retained scan, access errors | Native grants, directory/cache/artwork IO remain existing owners; no search feature added |
| Catalog Search page/content: edit timer, explicit submit, filter and results | Canonical shell/content/feedback; existing Pilot B cards; 04/04 | Catalog search tests: 300ms edits, no typing request, stale query, filter | Separate Catalog VM remains; no generic search VM |
| Source Search page/content: initial scoped search, partial sources, preview opening | AdaptiveGrid, PosterCard, Artwork, canonical shell/feedback; 04/04 | `source_search_page_test.dart`, reader-first: initialQuery, explicit submit, scope, timeout, source-first | Source VM worker limits, identity, generations and local exclusion remain |
| Catalog Detail page: route/TabController/NestedScrollView, picker and persisted selection | Canonical shell plus feature sections; 04/05 | Catalog detail tests: lazy restore, exact source, delayed results, actual tab scrolling | Coordinator owns await revalidation/save ordering; UI result never authorizes native trust |
| Catalog hero/sections/content and picker candidates: metadata/artwork/notice/related cards | Artwork, PosterCard, SectionHeader, NoticeBanner; 04/05 | Whole-page 375dp/3x RED/GREEN; independent Info/Chapters offsets | Picker is already typed Pilot A; preserve overlap and active route across resize |
| Native Manga/Novel pages, content and VMs: cached metadata, chapter mapping/opening | ChapterRow, Artwork, shell/feedback; typed feature mappers; 04/06 | Both native page suites: readable order, query, cache, expansion, dependency owner, stale opening | Legacy ChapterRow only retire after full source/test consumer check; provider release unchanged |
| `features/reading/continue_reading_page.dart`: pending Reader launch/retry/abandon | Canonical loading/error shell; 04/07 | `continue_reading_page_test.dart`: immediate pending route, retry, late abandon | Ready Reader returned directly; reader internals and resolver excluded |
| `core/ui/patterns/media_metadata_view.dart`: domain metadata and SourceArtwork future lifecycle | Feature ownership, domain-free canonical rendering; 04/08 coordination | Native metadata keepalive/source artwork tests | Picker and multiple detail consumers must migrate before deletion; do not duplicate cache |
| Legacy decode/progress/modal/scaffold helpers | Retain until remaining wave owners migrate; 04/08 audit | Architecture guard and caller search | Reader, Settings and Plugin Manager still consume them; no wholesale deletion |

SourceArtwork currently reads once per mounted resource, rereads on resource
change, converts failure/empty bytes to null. Callback identity alone does not
reread; existing owner keys remain significant. Application byte cache and
Flutter decoded image cache remain separate and unchanged.

Checkpoint: standalone and sliver grid test strengthened from exhausted→usable
to usable 375→padding-exhausted→usable 375. At exhausted width, item/key/extent
builders remain untouched and neither grid exists. Naturally GREEN
characterization, not manufactured RED.

## C. Tasks 04/01–04/08

| Task | Status |
|---|---|
| 04/01 Home | CODE CHECKPOINT GREEN; physical visual/profile evidence outstanding |
| 04/02 Library | CODE CHECKPOINT GREEN; cross-origin regression 138 passed; physical accessibility outstanding |
| 04/03 Local Media | CODE CHECKPOINT GREEN; 94-test Local/persistence regression; physical large-folder profile outstanding |
| 04/04 Search | CODE CHECKPOINT GREEN; 86-test Search/native-route regression; physical IME/accessibility outstanding |
| 04/05 Catalog Detail | CODE CHECKPOINT GREEN; 135-test Catalog/reader-first regression; physical visual/accessibility outstanding |
| 04/06 Native details | CODE CHECKPOINT GREEN; 42-test remote Manga/Novel and series detail regression; physical visual/assistive outstanding |
| 04/07 Continue Reading | CODE & VERIFICATION GREEN; 5 focused tests passed under FVM Flutter 3.47.5; physical offline outstanding |
| 04/08 Integration/retirement | CODE & VERIFICATION GREEN; ChapterListControls, canonical cards, architecture guards, 498 matrix findings reconciled, full 1253 regression passed, analyzer clean; physical accessibility outstanding |

## D. Home

Test-first changes: Continue must precede Featured (two prior opposing
expectations intentionally updated); successful empty discovery must not show an
idle placeholder; recent cards/artwork must use canonical APIs. Initial run:
8 passed, 5 failed, exit 1. Failures: wrong order, old placeholder, missing
canonical artwork/poster. Exact-source artwork reads and navigation assertions
remain required. No business/VM edits proposed.

Self-reviewed design: reuse existing shelf/card geometry and section/feedback
APIs. Preserve specialist hero PageController and pinned glass header. No new
cache, dependency, forwarding widget or shared domain API. Shelf owns its own
horizontal gutter; hero/header align with card edges inside the bounded region.

Canonical shelf/card/artwork migration is GREEN: Home/Foundation/architecture/docs
checkpoint **70 passed**, analyzer clean. Alignment regression genuinely failed
at 1200dp (hero 48 versus header 40), then passed after shared gutter alignment.
Hero now uses bounded canonical artwork; PageController and banner-first mapping
remain feature-owned. RTL breakpoint/1x–3x tests with 5,000-item inputs are widget
laziness checks, not physical performance evidence. Legacy SourceArtwork/type
mapping ownership and dead Home-only helper retirement remain assigned to 04/08.

## E. Library

Add and Remove now use feature-owned wording with the canonical typed root
Navigator confirmation. Captured membership intent chooses explicit add-only or
remove-only commands; mounted/VM identity checks reject stale item completions.
No repository, persistence, continuation or native policy changes.

Shell/content/list/artwork/feedback now compose canonical APIs. ContentRegion
owns horizontal gutters; existing lazy grid/list retain viewport ownership.
List titles/progress reflow without the previous two-line subtitle cap.

Genuine RED: Source/Local immediate Add mutation (8 passed, 2 failed), missing
ListRow, missing ContentRegion, then keyboard focus loss after dismissal.
Disabling the initiating button removes its focus history; feature-owned
FocusNode restores only previously held focus after rebuild, with mounted,
matching VM and current-route checks. Cancel, system Back, Escape and barrier
all preserve membership and restore keyboard focus for both intents.

Caller inventory: Catalog Detail, Source Search, Local, native Manga and native
Novel all use LibraryButton; Library page uses the same confirmation helper for
known saved entries without extra per-row membership subscriptions. SQLite
Source/Local/Catalog coverage, native route regressions, stale Add/Remove item
replacement, artwork/save-busy lifecycle and filter/list-scroll cancellation
are GREEN in the **138-test** cross-origin checkpoint. Physical assistive-device
acceptance is still NOT RUN.

## F. Local Media

One CustomScrollView remains scroll owner; canonical ContentRegion.sliver and
AdaptiveGrid.sliver replace manual max-width/fixed grid geometry. PosterCard,
Artwork, NoticeBanner and async feedback replace legacy presentation. Folder
summary retains meaningful root/access/action composition, with horizontal
gutter now owned by ContentRegion. No search feature, IO/cache/native or VM edits.

Canonical poster assertion observed genuine RED (0 passed, 1 failed), then
**33 Local widget tests passed**. Existing classification fixture used three
identical SourceMediaRefs; corrected fixture to distinct item IDs rather than
weakening canonical unique-key guard. Error title/body now share canonical
message; access-loss assertion still requires that exact safety wording.

RTL widths 375/599/600/601/839/840/841/1200/1440 at 1x/2x/3x preserve exact
source, one scan and one artwork read across resize; root/card edges align.
Existing scan sessions cover per-directory queries, 50,000-entry cap and no
eager reads. Local/scan/archive/restart/Library/app checkpoint: **94 passed**,
exit 0; analyzer clean. Physical large-folder performance is NOT MEASURED.

## G. Search

Both pages now use PageScaffold, one ContentRegion and canonical async feedback.
Source results now use exact-source-keyed AdaptiveGrid, PosterCard and Artwork;
feature mapping retains source/author subtitle, cover loading feedback,
previewMetadata and LibraryButton identity. Region owns horizontal gutter.
Distinct ViewModels, 300ms edit debounce, explicit submit and nonempty Source
Search initialQuery auto-search remain unchanged. No remote requests from build.

Genuine RED: two resize assertions found no ContentRegion. Catalog Search at
375dp/3x with 280dp IME inset then exposed a 42px controls overflow. Controls now
scroll within at most half the available height, preserving space for results;
no scale cap or hidden action. Source Search's matching short-height scenario
was naturally GREEN and received no speculative layout rewrite.

Search/page/VM, source-flow and native Manga/Novel regression: **86 passed**,
exit 0; analyzer clean. Three source-flow tests still targeted retired
MediaPoster; migrated finders to canonical cards while retaining route/source
assertions. Source resize across nine widths at 1x/2x/3x RTL retains controller,
one artwork read and exact source/preview opening. A subsequent focused rerun
passed without hit-test warnings after settling ensureVisible before tapping.
Physical keyboard/IME and assistive acceptance remain NOT RUN.

## H. Catalog Detail

PageScaffold, ContentRegion (including lazy chapter slivers), bounded Artwork,
semantic SectionHeader, canonical warnings and Related shelf/cards now replace
legacy geometry/feedback. Removed duplicate notice/warning/heading wrappers;
feature metadata mapping, description expansion, TabController, overlap,
source selection, generation guards and reader callbacks remain feature-owned.

Genuine RED: whole-page 375dp/3x RTL overflowed by 127px; compact large-text hero
now stacks and scrolls its foreground, with Read and Retry reachable by real
gestures. No text-scale cap or exception suppression. Missing canonical section
headers and chapter ContentRegion also failed before migration. Actual parent
1x/2x/3x scaling across 375/599/600/601/839/840/841/1200/1440dp retains one details
load and both tabs without layout exceptions.

Independent nonzero Information/Chapters offsets exposed a resize defect:
400→600dp changed hero collapse extent by 10px, making NestedScrollView skip
inner offset restoration while its outer header was not fully collapsed.
Stable 380dp base extent fixes restoration through tab switches, resize and
Information refresh. A keep-alive trial was rejected and removed: SDK scrolling
also moved inactive attached inner positions. No extra controller/cache added.

Fresh Catalog directory plus reader-first sequence: **135 passed**, exit 0;
analyzer clean. Includes cached/stale chapters, source switching, delayed A/B/A,
independent refresh, large lazy chapter lists and source-first/Catalog-first
route regressions. Physical visuals, assistive interaction and profiling remain
NOT RUN. Shared chapter controls/CTA retirement has now been performed in 04/08; Flutter regression verified GREEN.

## I. Native Manga/Novel Detail

MangaSeriesPage and NovelSeriesPage migrated to canonical HikariPageScaffold and
HikariContentRegion. MangaSeriesContent and NovelSeriesContent migrated to
HikariChapterRow, HikariNoticeBanner and HikariAsyncStateView.

Chapter presentation preserved:
- Exact chapter number, scanlators, release dates and novel release label precedence.
- Non-readable/locked chapter states remain distinct and guarded against tap.
- Source-first navigation flow preserved: Source Search -> Native Detail -> Reader.
- Catalog-first flow does NOT route through Native Detail.
- Stable chapter keys and reading target resolution preserved.

Remote Manga/Novel suite checkpoint: **42 passed**, exit 0; analyzer clean.
Physical visuals, assistive device interaction and hardware profiling remain NOT RUN.

## J. Continue Reading

Task 04/07 implementation verified GREEN. Test-first assertions
were verified in `test/continue_reading_page_test.dart` for canonical pending route
(`HikariPageScaffold`, `HikariContentRegion`, `HikariAsyncStateView.loading`),
canonical fatal error with `onRetry`, and direct ready Reader with **no extra
browse scaffold**. The test harness was corrected to provide `HikariTheme.darkTheme()`
for status color roles used by `HikariNoticeBanner` on error. Existing
late-disposal/abandon and `reader_first_sequence_test` remain intact. The page's
existing `ReaderLaunch`, `_resolve`, `_generation`, `mounted`, `ready.abandon()`
and ready-reader direct return were strictly preserved without modification.

Presentation now uses the canonical loading/error surface; retry is feature-owned
and does not alter source identity, continuation fallback, cached-chapter policy,
repository state, reader lifecycle or navigation prerequisites. Offline cached
artwork failure remains a separate UX/runtime limitation. Physical offline and
Back/reopen evidence: **NOT RUN**.

Automated verification evidence: `fvm flutter test -r expanded test/continue_reading_page_test.dart test/reader_first_sequence_test.dart`: **5 passed**, exit 0.

## K. Architecture & Migration

Task 04/08 implementation prepared (not yet Flutter GREEN). The canonical
Wave A/B guard now rejects attempts to re-import retired `HikariScaffold`,
`AsyncStateView`, `MediaPoster` or `ChapterRow` in the migrated features and
rejects raw dialog/sheet creation there. An explicit no-domain-import test
protects **all** `lib/core/ui/`, and the previous pattern-domain exception was
removed from the architectural dependency rule.

Legacy adapters retained a valid consumer inventory before retirement. Moved the
domain-aware `MediaMetadataView` / `SourceArtwork` and media-type badges into
`lib/features/media/widgets/`, retaining their widget keys, lifecycle,
source identity, cache callbacks and original widget tests. Removed the orphan
`lib/core/ui/patterns/chapter_row.dart`, while preserving canonical typed
`HikariChapterRow`. Migrated all dependent production/test import paths and
removed seven stale entries from the frozen legacy exemption list (six former
core/domain imports plus an obsolete Library confirmation entry). Frozen
exemptions now cover only later-wave Reader, Settings and Plugin Manager sites.
Additional 04/08 hardening following self-review:

- Replaced `PrimaryReadingCta` with direct `HikariButton` presentation at
  Catalog Detail and both native details. Preserved `onPressed` eligibility
  including `opening`, missing primary chapter and catalog `direct` behavior.
- Moved `ChapterControlBar` into `features/reading/widgets/` as
  `ChapterListControls`, reusing `HikariIconButton` for sort/search actions;
  callback, search-field controller and ordering ownership remain unchanged.
- Removed four now-unreferenced core visual adapters: `ChapterControlBar`,
  `PrimaryReadingCta`, `MediaPoster`, `AsyncStateView`. Retired tests specific
  to adapters but ported responsive feedback/poster assertions to canonical
  widget tests, and kept chapter toolbar interaction tests under Reading.
- Extended Wave A/B architectural guard to reject import/export/part of
  legacy visual patterns and app theme, including relative-path bypasses.
  Two precisely named non-visual helpers are allowed; `HikariRefreshAction`
  is still consumed by Reader/Plugin and remains outside this retirement.
- Distinguishes canonical core UI imports from legacy debt in inventory
  statuses; lexical finding kinds are retained for deterministic historical
  indexing. Frozen legacy exemptions remain unchanged.

No new whitelist exemption, dependency, Preview→Stable promotion or native
permission change was introduced. `MediaMetadataView` now canonically renders
`HikariArtwork` with responsive layout sizing, resolving series preview expectations.

`MIGRATION_MATRIX.md` has been officially regenerated with:
`cmd /c "fvm dart run tool/ui_audit_inventory.dart --markdown > docs/ui/MIGRATION_MATRIX.md"`.
The official scanner records **498 source findings** (including the canonical `HikariArtwork`
dependency in `media_metadata_view.dart`), 10 frozen later-wave legacy exemptions, and
zero missing/extra finding IDs. Live checkout HEAD `c3f066a` is recorded in the matrix header.
Documentation equality, file ownership, and legacy boundaries are verified by `test/ui_documentation_contract_test.dart`
and `test/architecture_guard_test.dart` (31 passed).

## L. Verification

Fresh pre-implementation evidence (2026-10-23, FVM Flutter 3.47.5):

- `fvm flutter test --no-pub -r expanded test/core/ui/layout/hikari_adaptive_grid_test.dart test/core/ui/layout/hikari_horizontal_shelf_test.dart test/core/ui/media/ test/architecture_guard_test.dart test/ui_documentation_contract_test.dart test/core/ui/surfaces/hikari_adaptive_presenter_test.dart`: **81 passed**, exit 0, after grid characterization edit.
- `fvm flutter test --no-pub -r expanded test/home_page_test.dart test/library_page_test.dart test/local_media_page_test.dart test/source_search_page_test.dart test/features/catalog/ test/features/home/ test/features/remote_manga/manga_series_page_test.dart test/features/remote_novel/novel_series_page_test.dart test/continue_reading_page_test.dart test/reader_first_sequence_test.dart`: **231 passed**, exit 0, before Home target changes. Old Featured-first/direct-Add behavior included; not migration acceptance.
- `fvm flutter test --no-pub -r expanded test/home_page_test.dart`: **8 passed, 5 failed**, exit 1, intended Home RED described above.

Implementation checkpoints (same date):

- Home, Home widgets, adaptive grid/shelf, architecture and documentation: **70 passed**, exit 0; analyzer clean before Library edits.
- Library page/VM/button plus Catalog detail/source flow, native Manga/Novel, preview lifecycle and app smoke: **138 passed**, exit 0. Initial combined run had one test timeout from settling an intentionally pending spinner; bounded dialog-animation pumps preserve the busy/write-count assertion and the rerun is GREEN.
- Canonical ContentRegion assertion: **0 passed, 1 failed** before implementation; keyboard cancellation focus: **6 passed, 2 failed** before fix, **8 passed** afterward (before adding symmetric stale-Add coverage).
- Local/scan/archive/restart/Library/app: **94 passed**, exit 0; analyzer clean.
- Search pages/VMs, Catalog source flow and native Manga/Novel: **86 passed**, exit 0; analyzer clean. Focused Source scaled/IME/navigation rerun: **1 passed**, exit 0, no hit-test warning.
- Catalog directory plus reader-first sequence after final section/sliver migration: **135 passed**, exit 0; analyzer clean. Detail/source-flow subset: **65 passed**, exit 0. One compact-reporter combined invocation returned an incomplete/truncated result; expanded-reporter reruns provide the recorded evidence.
- Remote Manga/Novel pages and series suites (`test/features/remote_manga/manga_series_page_test.dart`, `test/features/remote_novel/novel_series_page_test.dart`, `test/remote_manga_test.dart`, `test/remote_novel_test.dart`): **42 passed**, exit 0; analyzer clean.

These suites overlap; counts must not be summed as unique tests.

Task 04/07–08 final verification (development checkout, FVM Flutter 3.47.5, Dart 3.13.4):
- `fvm dart format --output=none --set-exit-if-changed lib test tool`: **384 files formatted (0 changed)**, exit 0.
- `fvm flutter test -r expanded test/continue_reading_page_test.dart test/reader_first_sequence_test.dart`: **5 passed**, exit 0.
- `fvm flutter test -r expanded test/features/reading/chapter_list_controls_test.dart test/features/remote_manga/manga_series_page_test.dart test/features/remote_novel/novel_series_page_test.dart`: **21 passed**, exit 0.
- `fvm flutter test -r expanded test/core/ui/feedback/hikari_feedback_test.dart test/core/ui/media/hikari_media_cards_test.dart test/ui_audit_inventory_test.dart`: **26 passed**, exit 0.
- `fvm flutter test -r expanded test/features/media/media_metadata_view_test.dart`: **6 passed**, exit 0.
- `cmd /c "fvm dart run tool/ui_audit_inventory.dart --markdown > docs/ui/MIGRATION_MATRIX.md"`: **498 source findings reconciled**, 10 frozen legacy exemptions, live HEAD `c3f066a` recorded, exit 0.
- `fvm flutter test -r expanded test/architecture_guard_test.dart test/ui_documentation_contract_test.dart`: **31 passed**, exit 0.
- `fvm flutter test -r expanded test/series_preview_test.dart`: **21 passed**, exit 0 (canonical `HikariArtwork` in `MediaMetadataView`).
- `fvm flutter analyze --no-pub`: **No issues found! (ran in 8.5s)**, exit 0.
- `fvm flutter test --no-pub`: **1253 passed, 1 skipped, 0 failed**, exit 0.

## M. Performance & Accessibility

Physical TalkBack/Narrator, keyboard/trackpad/IME device interaction, Android
native source/plugin flows: **NOT RUN**. Device p95/peak memory: **NOT MEASURED**.
Widget timing is diagnostic, not physical profile evidence. `9remote` configured
server failed to connect (`ECONNREFUSED`); no remote-device evidence available.
These physical checks remain pending Owner decision for deferral / signoff.

## N. Self-review

1. Legacy presentation callers: Zero unmigrated Wave A/B callers remain. All migrated views compose canonical foundation components.
2. Retired components: `ChapterRow` legacy, `ChapterControlBar`, `PrimaryReadingCta`, `MediaPoster`, `AsyncStateView` have zero production or test consumers.
3. Adapters: `MediaMetadataView` and `MediaTypePresentation` moved to `features/media/widgets/` and compose canonical `HikariArtwork` and token color roles; `ChapterListControls` is in `features/reading/widgets/`.
4. Boundaries: Zero domain imports in `lib/core/ui/`. Zero app/theme imports in migrated features.
5. Invariants: Reader-first sequence, source identity, chapter ordering, library persistence, plugin trust boundaries and native authorization are strictly preserved.
6. Documentation & Matrix: `MIGRATION_MATRIX.md` matches official scanner output (498 findings, 10 Phase 05/06 exemptions). All documentation contracts pass.

## O. G4 Gate

**G4 OWNER-ACCEPTED (2026-10-24).** Tasks 04/01–04/08 have all implementation
and automated verification completed. All 1,253 automated unit/widget/architecture tests
pass, `flutter analyze` reports zero issues, `dart format` is clean across 384 files,
and `MIGRATION_MATRIX.md` matches the official scanner with 498 findings and 10 later-wave
frozen legacy exemptions. The project owner has reviewed the audit verification evidence
and formally accepted Gate G4 ("tất cả đã ok"), authorizing the handoff to Phase 05.
Physical hardware/accessibility profiling (Redmi Note 9S) is deferred to Phase 07.

## P. Phase 05 Handoff

Authorized to begin Phase 05 (Settings, Plugin Management & Native Actions Migration).
Authority: [Phase 05 plan](../superpowers/plans/2026-10-22-hikari-ui-05-settings-plugins.md).

Handoff baseline and constraints:
1. Retain the 10 frozen legacy exemptions until their respective tasks migrate them:
   - Settings (Task 05/01): `settings_page.dart` (1 exemption: `showHikariConfirmation`)
   - Plugins (Tasks 05/02–05/04):
     * `plugin_manager_page.dart` (4 exemptions: `showDialog` x2, `showHikariConfirmation` x2)
     * `apk_actions_page.dart` (2 exemptions: `showDialog` x1, `showHikariConfirmation` x1)
     * `novel_actions_page.dart` (1 exemption: `showHikariConfirmation` x1)
   - Reader (Phase 06): `novel_reader_page.dart` (1 exemption), `publication_reader_page.dart` (1 exemption)
2. Retain `HikariRefreshAction` in `core/ui/components/` until Reader and Plugin Manager migrate.
3. Retain the two nonvisual helpers (`chapter_list_filter.dart`, `media_artwork_decode.dart`) in `core/ui/patterns/`.
4. Preserve all native consent, plugin trust boundaries (ADR-025), and LNReader runtime isolation.
