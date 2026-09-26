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
- `MediaSource` exposes id/name. `MangaPageSource` exposes pages/readPage;
  `NovelTextSource` exposes readText. Local source implements these capabilities.
  `MediaSearchSource` and `MangaChapterSource` add normalized search and chapter
  discovery; see [REMOTE_MANGA](REMOTE_MANGA.md) for series/chapter identity and
  the deliberately deferred series-level resume. Picker, tree selection and scan
  mechanics stay infrastructure-specific.

## Storage and composition

[ADR-004](../decisions/ADR-004-user-state-persistence.md) selects Drift/SQLite.
`UserDatabase` schema version 1 has two independent tables, each keyed by
`(source_id, item_id)`, without foreign keys or cascading ownership:

- `progress_records`: kind, nullable position_ms/duration_ms/page_index/page_count/
  text_progression, completed integer, updated_at epoch milliseconds.
- `library_records`: title, media_type, added_at epoch milliseconds. Infrastructure
  maps media types explicitly to stable `anime`, `manga`, `light_novel` values;
  unknown values are rejected, never resolved through Dart enum names.

Generated records remain infrastructure types. Explicit mapping rejects unknown
kinds/types, missing required or populated irrelevant payload fields, invalid
completion flags and invalid domain values with `FormatException`. Upsert clears
nullable fields belonging to a prior position kind. UTC timestamps use epoch
milliseconds; domain values never import Drift or Flutter.

Drift Flutter opens `hikari_user_state.sqlite` in application documents and uses a
native background connection. Root State creates database, repositories and local
source once, injects consumers, and closes its owned database on disposal. `main`
remains thin. Source resolution uses id equality and capability type checks; no
registry, DI framework or provider engine exists. Both scan and Library call the
same open route. Library opens persisted snapshots without rescanning.

## Resume and writes

Video loads saved state before native open. Playback opens paused, waits for a
known duration, clamps incomplete position to current duration, then seeks before
playback. Completed entries start at zero. Only the engine completion event marks
completion; meaningful nonterminal playback clears it. Changed samples write at
most roughly every five seconds, plus pause/background/close flushes. One app-owned
LocalVideoSession owns the reusable Player, VideoController, timer and subscriptions.
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

Novel restores normalized scroll after text and layout are available. Scroll saves
are debounced 500 ms, with background and final leave flushes. Completion requires
actual scroll end with 0.5 logical-pixel tolerance; no scroll extent does not imply
completion. A completed reopen starts at the top. This is a temporary plain-text
scroll locator, not EPUB/chapter/CFI support. Empty content never auto-completes.

Persistence errors surface in UI; unreadable content does not overwrite progress.
Lifecycle flush is best-effort: an abrupt process kill can lose the latest unsaved
five-second video sample or debounce interval. There is no background scheduler.

## Verification boundary

User confirmed the prior Android local-media scan/open walking skeleton on a real
device. That evidence does not verify this new persistence/resume slice. Automated
coverage exercises domain invariants, independent repository CRUD, file close/reopen,
malformed storage, Library actions, manga decode-aware saves and text restoration/
debounce/final flush, plus video calculations. Native player/SAF lifecycle and new
restart/resume behavior still require device verification.

Device follow-up: add each type; read/watch partway; background and close/restart;
open from Library without scanning; verify all three restore. Complete each type,
reopen at start, reread and check active progress. Remove Library membership and
confirm progress survives. Revoke/move content and confirm graceful recovery.
