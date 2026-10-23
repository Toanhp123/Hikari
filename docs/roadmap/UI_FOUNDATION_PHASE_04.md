# UI Foundation Phase 04

## A. Summary

Execution in progress on `feat/ui-foundation-rebuild`, baseline
`41de07676731824bd62927bbf9518400e2ac3be6` (2026-10-23).
Scope: Tasks 04/01–04/08 only. No Phase 05, API promotion, Git-history mutation,
subagent or worktree. G4 remains **INCOMPLETE / NOT OWNER-ACCEPTED**.

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
| 04/07 Continue Reading | NOT STARTED |
| 04/08 Integration/retirement | NOT STARTED |

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
NOT RUN. Shared legacy chapter controls/CTA ownership audit remains 04/08.

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

Pending. Existing launch record owns reader/abandon/settled. Ready Reader must
not acquire browse-page shell. Offline-cached-image limitation remains separate.

## K. Architecture & Migration

Pending migrated-path strict guard and final inventory regeneration. No new
architecture exceptions or Preview→Stable changes allowed. Canonical UI never
receives domain models. Retain legacy helpers with valid later-wave consumers.

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
Analyzer, final full suite, Windows check script, builds and runtime acceptance:
**NOT RUN for final Phase 04 state**.

## M. Performance & Accessibility

Physical TalkBack/Narrator, keyboard/trackpad/IME device interaction, Android
native source/plugin flows: **NOT RUN**. Device p95/peak memory: **NOT MEASURED**.
Widget timing is diagnostic, not physical profile evidence. `9remote` configured
server failed to connect (`ECONNREFUSED`); no remote-device evidence available.

## N. Self-review

Initial ownership review complete; final correctness/diff and post-correctness
Ponytail simplification review pending. Every changed line must trace to scope.
No weakening of tests, source identity, trust, consent, cache or progress allowed.

## O. G4 Gate

**INCOMPLETE / NOT OWNER-ACCEPTED.** Implementation, final verification and
mandatory platform evidence remain outstanding. Only owner can accept G4.

## P. Phase 05 Handoff

Not authorized to begin. Retain valid Settings/Plugin/Reader legacy consumers.
Preview APIs stay Preview. Carry forward each NOT RUN/NOT MEASURED row rather
than relabeling it PASS.
