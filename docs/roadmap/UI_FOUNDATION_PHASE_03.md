# UI Foundation Phase 03

Date: 2026-10-23. Scope: v4.2 Phase 03 Tasks 01–08 only.
Audience: project owner and Phase 03/04 implementers/reviewers.
Current handoff status (owner decision 2026-10-23): **PHASE 03 COMPLETE; G3
OWNER-ACCEPTED FOR CONTINUATION**. [Dated approval](UI_FOUNDATION_G3_OWNER_ACCEPTANCE_2026-10-23.md).

The original execution assessment was **G3 INCOMPLETE / NOT OWNER-APPROVED**;
its unmeasured physical/assistive/profile evidence is retained below, not
silently upgraded to technical PASS. No Phase 04 implementation is recorded
here. Preview APIs are not promoted to Stable.

## A. Summary

Canonical artwork, poster/landscape cards, progress, artwork ratios, scaled
collection geometry, lazy grid/shelf and domain-free chapter row are present.
Library grid and Catalog Search use canonical media anatomy. Catalog Detail
uses a feature-owned typed chapter mapper. Legacy remote chapter rows remain.

Mandatory Phase 02 preflight reproduced an Escape regression at 375/1440dp.
The shared presentation session now retains its focus scope when feature steps
remove their focused child; no feature-specific Escape workaround was added.

Contracts and migration boundaries have one canonical home:
[media components](../ui/components/media.md). Accepted design authority:
[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md),
[v4.2 spec](../superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md).
Execution scope: [Phase 03 plan](../superpowers/plans/2026-10-22-hikari-ui-03-media-layouts.md).

## B. Preflight

- Checkout: `feat/ui-foundation-rebuild`, baseline HEAD
  `ecf793ecb660a67a04b18d78362d2a59626d58c1`; working-tree implementation remains
  uncommitted. No subagents, worktrees, branches, commits, pushes or merges.
- Host: Windows 11 Pro 10.0.26300. FVM Flutter 3.47.5 / Dart 3.13.4.
- G0–G2 continuation authority comes only from
  [dated owner decision](UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).
  That decision does not approve G3 or certify missing historical hardware data.
- Read design/spec/ADR/phase plan, relevant UI architecture/process and prior
  execution evidence. Graphify queries oriented cross-file ownership; source and
  tests verified consequential conclusions. Existing graph was not used as proof.
- Initial Escape/MenuAnchor regression checkpoint: 106 passed, two new Escape
  failures at 375/1440dp. Focus tree showed route scope outside CallbackShortcuts.
  A first `Focus` attempt stole explicit action autofocus; it was replaced by
  scoped `FocusScope(autofocus: true)`. Subsequent 110-test checkpoint passed,
  including Back, MenuAnchor, Tab, focus restoration and architecture assertions.
- Full historical migration matrix was not manually read line by line. Current
  deterministic inventory was regenerated and checked against live source.
  Scanner remains lexical, not a resolved call graph or full manual route audit.
- Documentation conflict: FOUNDATIONS retained nineteen exemptions after prior
  removals. Frozen fixture contains seventeen; current statement corrected to
  seventeen. Fixture/rules were not relaxed or regenerated.
- MCP `9remote` failed to connect (`ECONNREFUSED`); physical remote interaction
  evidence was not collected through that connection. It is configured, not absent.

## C. Tasks 01–08

Counts below are overlapping checkpoints, not distinct tests to sum.

| Task | Implementation and evidence | Remaining limitation |
|---|---|---|
| 03/01 | Canonical HikariArtwork and pure decode policy; legacy helper delegates. Earlier missing API/behavior RED, then policy/missing/corrupt/provider tests GREEN. Loading-label regression separately observed RED then fixed. Actual load/codec counters added. | Native cache fixture proves retained equal provider/bucket only, not every network or Reader lifecycle. |
| 03/02 | Poster 2:3, landscape 16:9, title two scaled lines, optional subtitle/badge/progress/callback/Hero. Required anatomy assertions observed RED then GREEN. Keyboard, semantics and paired-route source-unique Hero characterization added. | Real device Hero/assistive-technology review NOT RUN. |
| 03/03 | Named ratios and bounded shared typography metrics. Geometry RED then GREEN across 1x/2x/3x, nonlinear scaler and both directions. | Physical font fallback/glyph clipping inspection NOT RUN. |
| 03/04 | Standalone and sliver grid; local constraints, explicit scaled extent, stable key remapping. Lazy/stable-state RED then GREEN. Default width mismatch 180 vs foundation 200 caught RED and corrected. | Key enumeration is O(n) per rebuild; item widgets/providers are lazy. |
| 03/05 | Horizontal native ListView, header gutter, controller ownership, keys, directional scrolling. Compile-safe stub produced behavioral RED; implementation GREEN. Later RTL/reduced-motion regression RED then fixed. Reorder/controller replacement characterization GREEN. | Physical Windows wheel/trackpad/focus ring review NOT RUN. |
| 03/06 | Domain-free HikariChapterRow plus typed CatalogChapterRowData manga/novel factories. Subtitle/readability RED then GREEN; 64-test chapter/picker/architecture checkpoint passed. | Remote manga/novel legacy ChapterRow intentionally retained. |
| 03/07 | Library and Catalog Search canonical cards/grid; original callbacks, identity, source artwork and persistence unchanged. Library canonical progress assertion RED then GREEN; 31-test pilot/cache checkpoint passed. Catalog badge semantic-pair regression later RED then fixed. | Catalog migration initially lacked dedicated pre-edit RED; existing behavior tests were GREEN characterization, not invented TDD evidence. |
| 03/08 | Gallery media fixtures, 5000-card stress, actual provider/codec instrumentation, five-replay host benchmark and canonical docs. Documentation assertion RED before docs/exports. Gallery placement regression fixed without weakening existing test. | No device profile, memory or p95 claim. Full physical matrix remains unverified. |

New characterization tests do not claim a preceding RED where behavior already
existed. In particular network success/404, Hero route pairing, Gallery matrix,
controller replacement and additional pilot resize checks are post-implementation
coverage. This process gap is recorded rather than rewriting history.

## D. Media Architecture

- Core UI renders ImageProvider/placeholder only; it has no SourceRegistry,
  repository, extension API, ReadSourceArtwork or feature/domain imports.
- Features choose MemoryImage/NetworkImage and exact identity. Catalog keys use
  CatalogEntryId plus type, never fabricated SourceMediaRef. Library keys use
  exact SourceMediaRef, not title/index.
- Decode width: ceil to 64 physical-pixel buckets, min 64, max 2048; height null.
  Invalid/nonfinite width/DPR or overflow returns null. Existing ResizeImage is
  not nested. Width cap does not cap pathological original image height/memory.
- SourceArtwork/ReadSourceArtwork remain existing owners. Byte cache namespace
  `source-artwork-v1`, source/item key, offline/cache-first and unavailable-plugin
  cache reads are not modified. No cancellation, eviction or parallel cache added.
- Native Flutter image cache owns decoded provider/resize-key reuse; application
  byte cache remains separate. Tests count provider loadImage and real decoder
  callback requests, not merely widget rebuilds or equal keys.
- Material/InkWell overlay paints focus/hover/ink above opaque artwork. Card
  excludes decorative cover semantics and retains full title. No unconditional
  per-card RepaintBoundary was introduced.

## E. Adaptive Layout

- Grid geometry uses local LayoutBuilder/SliverLayoutBuilder constraints,
  foundation maximum poster extent 200dp and spacing/padding tokens; no platform
  or global-screen branch. No new minimum-width token was invented.
- Standalone owns one vertical GridView. Sliver variant borrows parent viewport
  and has no separate controller. Extent callback reserves scaled text below
  fixed artwork ratio; zero/unbounded cross width produces empty collection.
- Tests cover 375, 599/600/601, 839/840/841, 1200/1440dp, 3x RTL, 5000 items,
  standalone/sliver laziness, moved keyed state and filter removal.
- Shelf is one native horizontal ListView with aligned header gutter. Tab exits;
  arrows respect RTL; reduced motion jumps. Horizontal pointer scroll and vertical
  parent drag have widget coverage. Caller controllers survive replacement and
  unmount; internal controller is disposed by shelf.
- Shared metrics cache is bounded to 24 style/scaler/direction/line combinations.
  It reserves two title lines and optional subtitle, not per-item full text.
  Arbitrary installed-font changes and physical locale fallback are not certified.

## F. Chapter Presentation

Catalog mapper preserves manga scanlator/source fallback, chapter number, ISO
date and unreadable label. Novel mapper preserves scanlator order and releaseLabel
precedence over date. Exact source remains feature-owned and supplies ValueKey.
Core receives strings/readability/callback only; unreadable/disabled cannot open.
Continuation status remains visible and semantic. Existing chapter open paths,
owner.opening guard, continuation matching and scroll-controller ownership remain.

Negative architecture fixture is checked under both content and media paths.
Legacy ChapterRow still serves remote_manga and remote_novel detail content;
removing it now would break staged consumers and violate task boundary.

## G. Pilot B

Library grid retains feature progress label and completed-overlay suppression,
media-type semantic color pair, persisted membership and SourceArtwork resource.
List mode retains existing loader/decode entrypoint. Remove action remains separate
sibling above card; explicit test confirms removal does not invoke navigation.
Two same-title sources open their exact distinct source identities at 3x RTL
through resize boundaries. Repository watch, filter and list/grid tests remain.

Catalog Search retains submit/debounce/filter/ViewModel ownership and exact
CatalogEntry callback. Media-type subtitle/badge remain feature-selected. Default
badge uses primaryContainer/onPrimaryContainer, preserving prior semantic pair.
Existing search tests plus 3x RTL resize pass at focused checkpoint. No change to
source selection, persistence, plugin trust, authorization or offline policy.

Two older integration tests still searched for Library MediaPoster after migration.
Only those Library finders were updated to HikariPosterCard. Their source/native
lease and persisted-reader assertions remain; no blanket global reset was added
to conceal cascaded failure from an earlier stale finder.

## H. Performance

Reproduction protocol: [media benchmark](../ui/components/media.md#replayable-micro-benchmark).
Five fresh test-process replays on this host, 5000 synthetic cards, 800x600, 1x.
Each produced initialBuilds=12, totalBuilds=36 after scroll/return,
providerLoads=1, codecRequests=1.

| Replay | Initial host microseconds | Total host microseconds |
|---|---:|---:|
| 1 | 588380 | 685910 |
| 2 | 606243 | 701721 |
| 3 | 543807 | 641557 |
| 4 | 590127 | 682629 |
| 5 | 573638 | 683036 |

These are host widget-test elapsed times, **not frame timing or device profile**.
Raw output retained in session task `b786wsi4c.output`. An earlier parallel attempt
failed to delete build/native_assets/windows/sqlite3.dll while full tests used
native assets; sequential replay after suite completion passed. No permission
change, process kill or destructive build cleanup was used.

Physical Android/Windows p95 frame time: **NOT MEASURED**.
Peak memory: **NOT MEASURED**. Physical Redmi Note 9S profile remains Phase 07.
No real source/backend/network performance claims from synthetic fixtures.

## I. Architecture and Migration

Curated barrel exports only intended Preview media/layout/content API plus
hikariCardExtent; internal composition and text helpers are not exported.
Inventory maps new public APIs, internal media body and aspect-ratio foundation
to exact Phase 03 owners. Matrix is current dirty ecf793ec checkout, not an
approval. Frozen seventeen exemptions remain unchanged.

Remaining staged owners:

| Legacy consumer | Owner / boundary |
|---|---|
| Catalog Detail skeleton and related MediaPoster | 04/05 |
| Home discovery/recent MediaPoster | 04/01 |
| Local Media MediaPoster | 04/03 |
| Source Search MediaPoster | 04/04 |
| Remote manga/novel ChapterRow | 04/06; associated Reader work remains Phase 06 |
| Legacy metadata/media-type/decode helpers and remaining callers | owner migration first; final cleanup 07/01 |
| SourceArtwork / ReadSourceArtwork | retained feature/application lifecycle, not a legacy rendering adapter to delete |

LibraryPage required no production edit: existing page callbacks/state/loader
already provide required boundary. No redundant Library adapter, dependency,
state framework, image package or new cache layer created. FOUNDATIONS exemption
count corrected; COMPONENTS links media contracts and this report.

## J. Full Verification

Fresh `tool/check.ps1` completed successfully in task `bxr0ctbch` (exit 0):
locked dependencies, formatting, analyze, full tests and both whitespace checks.
**1253 passed, one skipped, zero failures**; analyzer reported no issues.
The skipped test remains skipped, not certified by this task.

- First full check: analyze clean; 1239 passed, one skipped, seven failures.
  Root failures: two stale Library MediaPoster finders and stale migration matrix;
  subsequent remote debug-variable failures cascaded from aborted test cleanup.
- After finder/inventory repairs: 61 focused inventory/integration/Hero tests
  passed; documentation contract separately three passed.
- Next full check: locked dependencies/format/analyze passed; 1251 passed,
  one skipped, one failure. Failure was COMPONENTS link to this not-yet-created
  report. No unrelated product failure was inferred or hidden.
- Focused additional checks: artwork/Library 18 passed; Catalog Search six passed;
  Gallery+shelf five passed; reduced-motion+architecture checkpoint 25 passed;
  same-title Library 3x RTL resize one passed. Checkpoint counts overlap.
- Final focused surfaces/picker/Catalog/public API replay: **115 passed** in
  task `bl3qtfe93` (exit 0), including Escape/MenuAnchor/focus coverage.
- Final Android debug/release APK and Windows debug app/Gallery builds succeeded
  in the same sequential task after all production corrections. Release APK
  115.9 MB. Compiler success is not interactive/native E2E.
- Build warnings: Gradle restricted native access; Kotlin Gradle plugin future
  Flutter incompatibility; SDK XML version mismatch. No toolchain upgrade in
  media scope. No new runtime dependency or lockfile change intended.
- Final tool/check.ps1 and unstaged/staged git diff --check passed as recorded
  above. Final focused replay/build refresh also passed. Complete patch is supplied
  beside this report's handoff; no staging/commit required.
- Graphify AST update completed (exit 0): 9105 nodes, 14662 edges, 465 communities.
  Four existing Kotlin files had parser warnings; community labels may be stale.
  No LLM relabel/doc semantic rebuild was run; graph remains a navigation aid.
- Android/iOS physical runtime, TalkBack/Narrator, physical Windows keyboard,
  hover/wheel/trackpad and device profile: **NOT RUN / NOT MEASURED**.
- Native installer/consent E2E not rerun for this media task; no related production
  regression identified. Historical native gate ownership remains unchanged.

## K. Self-review

Inline correctness review inspected all new production media/layout/content and
mapper files plus tracked production/test diff. No agents were dispatched.
Review found and corrected slow-placeholder semantics, foundation default width,
Catalog badge color pair, reduced-motion scrolling and Gallery initial-control
visibility. Tests were strengthened for non-null moved state, exact progress,
independent remove action, source identity, codec requests and negative boundaries.

Ponytail pass after correctness: existing Flutter Image, Material, InkWell,
ListView/GridView, Sliver delegates, Focus and native semantics cover mechanics.
Shared card body avoids duplicating two card families; bounded metrics retain
scaled collection correctness. No speculative provider/cache/navigation layer,
compatibility framework or dependency added. No safe required-behavior cut found;
net: 0 lines proposed for removal. This is self-review, not independent review.

Remaining risks are evidence gaps, not claims of physical correctness: arbitrary
font fallback, native pointer/focus/assistive behavior, physical performance and
full historical manual matrix audit. TDD process gaps are explicit in section C.

## L. G3 Gate

**Original execution assessment: G3 INCOMPLETE / NOT OWNER-APPROVED.**

Automated media/layout/pilot checks and builds are GREEN. Physical interaction
and assistive-technology evidence remains outstanding, as does complete historical
manual matrix audit. G3 stays INCOMPLETE rather than substituting compilation or
widget tests for those checks. No missing evidence is marked PASS.
**Subsequent owner decision (2026-10-23): G3 OWNER-ACCEPTED; Phase 03 COMPLETE
for continuation.** The owner reports satisfactory testing and authorizes
Phase 04 Task 01 after incorporating the focused grid guard and verifying its
new regression. See [dated approval](UI_FOUNDATION_G3_OWNER_ACCEPTANCE_2026-10-23.md).
This does not certify missing physical/assistive/profile traces as independently
verified PASS. Do not promote Preview APIs to Stable. Historical G0–G2 missing
measurements remain unchanged.

## M. Phase 04 Handoff

With explicit G3 owner acceptance now recorded, Phase 04 is authorized to
consume canonical media APIs directly; keep
source/provider/domain mapping within features. Migrate staged consumers at
listed task, then remove legacy entries only after final caller is gone.

Known Catalog Detail whole-page hero/notice overflow at 375dp/3x is recorded
Phase 04 Task 05 work, not silently reclassified as media completion. Preserve
reader-first continuation, exact source identity, cache-first/unavailable-source
artwork, progress/persistence, native consent and plugin trust throughout.

Phase 04 Task 01 is authorized by the separate dated owner decision, not by
this historical execution report alone.
