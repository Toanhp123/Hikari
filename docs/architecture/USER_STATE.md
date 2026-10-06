# User state: Progress and Library

## Identity and independent state

`SourceMediaRef(sourceId, itemId)` compares by both opaque values. This locator is
not a canonical content identity. Progress is one current record per reference;
absence means unread/unwatched. Library membership is a separate saved snapshot
of `Media` (reference, title, media type) plus `addedAt`. Opening or saving progress
never adds membership; removing membership never deletes progress, and deleting
progress never removes membership. No history/session log is stored.

Canonical identity remains deferred until rename reconciliation or deduplication
has a demonstrated requirement. Moved/renamed/deleted documents and revoked SAF
grants can orphan references. Library retains snapshots so users can see/remove
them; opening reports unavailable content rather than silently deleting data.
Future canonical migration can associate existing source references with a new
identity without conflating these two independent state records. No migration
framework or reconciliation algorithm is introduced now.

## Domain contracts

- `ProgressPosition` is sealed: `VideoPosition` uses nonnegative `Duration`s;
  `PagePosition` is zero-based, bounded by page count (empty content uses 0/0);
  `TextPosition` requires a finite normalized value in [0, 1].
- `MediaProgress` stores reference, typed position, explicit `completed`, and UTC
  `updatedAt`. Completion is not inferred from position by persistence.
- `LibraryEntry` stores an immutable `Media` snapshot and UTC `addedAt`.
- Progress repository: load/save/delete. Library repository: upsert/remove/contains/
  loadAll. No watch stream is needed for current explicit refresh UI.
- `MediaSource` exposes id/name. `DirectVideoSource` exposes the current direct
  playback locator contract; `MangaPageSource` exposes pages/readPage;
  `NovelTextSource` exposes plain text; `PublicationSource` exposes local publication
  sections/resources; `NovelChapterSource` exposes rich remote chapter content.
  `MediaOpenLeaseSource` optionally retains source-owned route resources without
  exposing the concrete source to presentation. `MangaSearchSource`,
  `MangaSeriesSource`, `NovelSearchSource` and `NovelSeriesSource` add normalized
  discovery. See [SOURCES](SOURCES.md) for the complete capability boundary.
  Picker, tree selection and scan mechanics stay infrastructure-specific.

## Storage and composition

[ADR-004](../decisions/ADR-004-user-state-persistence.md) selects Drift/SQLite.
`UserDatabase` schema version 4 stores independent progress, Library, and remote
series-continuation state, each keyed by source identity without cascading ownership:

- `progress_records`: kind, nullable position_ms/duration_ms/page_index/page_count/
  text_progression, document_resource/document_progression/document_total_progression/
  document_locator, completed integer, updated_at epoch milliseconds.
- `library_records`: title, media_type, added_at epoch milliseconds.
- `mihon_continuation_records`: provider-private encoded continuation payload; not
  Library membership, progress, or the source-neutral relationship below.
- `series_continuation_records`: one remote series-to-last-read-chapter reference.
  It stores no metadata, progress, foreign key, or cascading ownership; deleting
  Library membership or chapter progress leaves the relationship intact.

Version 4 adds bounded remote-series continuation keyed by series source reference.
The selected chapter must still resolve exactly once in a fresh source sequence at
resume time. This state is distinct from provider-private continuation data.

Remote chapter saves first persist meaningful child progress, then update series
continuation. Opens and prefetch do not write it; failed progress writes never advance
it. The relationship has no timestamp: Home sorts using child progress `updatedAt`.
Missing chapter progress omits the relationship from Continue; there is no parent
progress fallback. A completed child remains eligible, and reopening it preserves the
reader's existing completed-reopen behavior until meaningful rereading. The two writes
are intentionally not atomic. No historical parent mapping is inferred and no
first-unread chapter is selected.

Home Continue is derived only from saved parent Library entries. Direct-media rows join by
parent source reference and completed direct items are excluded. Manga and light-novel
series rows load an exact child reference from `series_continuation_records`, then join
that child's progress; a missing child row never falls back to parent progress. Child
progress timestamp orders Continue items, including completed chapter continuations.
The relationship and child progress persist independently from Library membership;
removing membership hides the item without deleting either record. See
[ADR-011](../decisions/ADR-011-catalog-content-boundary.md).

`OpenSeriesContinuation` resolves the saved parent through `OpenMedia`, reloads a fresh
source sequence, and validates the exact child reference occurs once and belongs to the
same source. Unavailable sources, missing children, and malformed/duplicate sequences
fail before chapter navigation. A partially acquired media lease is released on failure.

Version 3 migrates version 2 by adding `mihon_continuation_records` and migrating
valid `mihon-v1:` manga/chapter references to stable kind-plus-URL `mihon-v2:` keys,
retaining their continuation. Duplicate old keys coalesce to latest progress and
earliest Library membership; item ID breaks timestamp ties deterministically.
Malformed/unrecognized references and unrelated local/provider rows remain unchanged.
See [ADR-009](../decisions/ADR-009-stable-source-identities.md).

Version 2 migrates version 1 by adding the four nullable document columns. Existing
video/page/text progress and Library rows remain unchanged; new document positions use
format-neutral resource, optional normalized progression values and optional opaque
locator data. Infrastructure
  maps media types explicitly to stable `anime`, `manga`, `light_novel` values;
  unknown values are rejected, never resolved through Dart enum names.

Generated records remain infrastructure types. Explicit mapping rejects unknown
kinds/types, missing required or populated irrelevant payload fields, invalid
completion flags and invalid domain values with `FormatException`. Upsert clears
nullable fields belonging to a prior position kind. UTC timestamps use epoch
milliseconds; domain values never import Drift or Flutter.

Drift Flutter opens `hikari_user_state.sqlite` in application documents and uses a
native background connection. `AppDependencies` creates the database, repositories,
local source, discovered external sources, immutable `SourceRegistry`, application workflows and
video session once, then closes only resources it owns. `main` remains thin. Duplicate
source IDs fail during composition instead of silently replacing an implementation.
`OpenMedia`, `OpenMangaChapter`, `OpenNovelChapter`, `SearchManga`, `SearchNovels` and
`ProgressSession` keep source/progress/search coordination out of Flutter widgets while
remaining pure Dart.
Dart composition stays explicit; the Android extension host uses native Injekt only as
an ABI-compatibility service locator for loaded extension code. Scan, remote search and
Library reuse the same open route;
Library opens persisted snapshots without rescanning or repeating the original search.
See [SOURCES](SOURCES.md) for the source/application boundary.

## Resume and writes

Video loads saved state before native open. Playback opens paused, waits for a
known duration, clamps incomplete position to current duration, then seeks before
playback. Completed entries start at zero. Only the engine completion event marks
completion; meaningful nonterminal playback clears it. Changed samples write at
most roughly every five seconds, plus pause/background/close flushes. One app-owned
MediaKitVideoSession owns the reusable Player, VideoController, timer and subscriptions.
Each route gets a separate identity and tracker; async continuations check that identity
and explicit lifecycle phase. Opening/seek events cannot update progress. Restore failure
shows a playback error without writing the saved record; reopening gets fresh tracking
state. A restore target alone is not meaningful playback.

Position/duration events maintain a Dart-side snapshot; flushes never query native state.
PlayerPage uses `PopScope` to block exit until a frozen final sample is queued after
previous writes, awaited, and media stopped/unloaded. Closing starts synchronously;
stop/open reset zeros and completed=false never overwrite progress or imply replay.
AppBar and Android system back share this path; repeated attempts are ignored. Save
failure still attempts stop and permits exit. Player destruction happens only at owner
shutdown, before its owned database closes. Forced route removal has a best-effort
finish fallback, not the normal persistence path. Predictive route preview remains
unavailable while asynchronous finalization is required.

The infrastructure-local fake driver tests native command ordering, reset streams,
sequential sessions and 60 repeated navigation cycles without introducing a domain
engine framework. Native streams have no media IDs: identity guards protect Dart
continuations, while serialized stop/open and duration readiness gate native samples.
An arbitrarily delayed old native event after new playback becomes active cannot be
identified by a Dart token; physical-device stress remains necessary.

Manga clamps saved index against the newly loaded page list; completed content
starts at page zero. Only a successfully decoded frame presented by Image schedules
its post-frame save. Byte-read success and decode failures do not count as reading.
An untouched completed reopen preserves completion, including immediate leave.
Explicit page navigation starts rereading: a successfully decoded earlier page clears
completion and the final page completes it again. Initial rendering alone does not
clear a completed record.
One bounded decoded image remains the cache ceiling from LOCAL_MEDIA.

Plain-text and rich remote novel chapters restore normalized scroll after content and
layout are available. Scroll saves are debounced 500 ms, with background and final
leave flushes. Completion requires actual scroll end with 0.5 logical-pixel tolerance;
no scroll extent does not imply completion. A completed reopen starts at the top.

EPUB publications persist `DocumentPosition`: the current spine resource plus an
approximate normalized progression (and optional format-neutral locator fields).
Section changes are serialized before navigation/exit, and completion is reached only
at the end of the last spine section. This is deliberately not a CFI or
layout-independent pagination model. Empty content never auto-completes.

Persistence errors surface in UI; unreadable content does not overwrite progress.
Lifecycle flush is best-effort: an abrupt process kill can lose the latest unsaved
five-second video sample or debounce interval. There is no background scheduler.

## Verification boundary

User confirmed the prior Android local-media scan/open walking skeleton on a real
device. That evidence does not verify this new persistence/resume slice. Automated
coverage exercises domain invariants, independent repository CRUD, file close/reopen,
malformed storage, Library actions, manga decode-aware saves and text restoration/
debounce/final flush, plus video calculations. The version-one migration path also runs
Drift's native schema verifier so the migrated SQLite schema must semantically match a
fresh database at the current schema version. Native player/SAF lifecycle and new
restart/resume behavior still require device verification.

Device follow-up: add each type; read/watch partway; background and close/restart;
open from Library without scanning; verify all three restore. Complete each type,
reopen at start, reread and check active progress. Remove Library membership and
confirm progress survives. Revoke/move content and confirm graceful recovery.
