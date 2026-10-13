# Source and application architecture

## Purpose

Hikari sources expose small domain capabilities. The application layer resolves those
capabilities and coordinates user workflows; infrastructure implements them; Flutter
features render state and routes. This boundary exists so a new source that already
fits a capability can be registered instead of adding provider-specific branches to
central UI code.

```text
Flutter feature / route
        |
        v
application workflow
        |
        v
   SourceRegistry --------> ProgressRepository
        |
        v
 domain capability
        ^
        |
infrastructure source
```

`app/` is the composition root around this graph. It may know concrete infrastructure
classes, but workflow policy does not live there.

## Terminology

Hikari uses **source** as the code-level term. A `MediaSource` is one registered
implementation that exposes one or more domain capabilities. **Provider** is reserved
for prose about an external service (for example MangaDex), not a second abstraction in
the code. **Extension** is the Android packaging/runtime mechanism that can create manga source implementations before registry composition. Extension ABI, trust and class loading remain infrastructure concerns; see [EXTENSIONS](EXTENSIONS.md). Do not introduce parallel `Provider`/`Source` base
types for the same responsibility.

## Domain capabilities

The current source surface is deliberately small:

| Capability | Responsibility |
| --- | --- |
| `MediaSource` | Stable opaque `SourceId` and user-facing source name |
| `MediaSourceAvailability` | Whether a registered source can operate on this device/platform |
| `MediaOpenLeaseSource` | Optionally retain source-owned resources for the lifetime of one opened media target |
| `MangaSearchSource` | Search and return normalized `Media` |
| `DirectVideoSource` | Turn a source-scoped video reference into a locator accepted by the current direct player path |
| `MangaSeriesSource` | Resolve normalized series metadata and provider-neutral chapters |
| `MangaPageSource` | Resolve a readable reference into pages and read page bytes |
| `ArtworkSource` | Read source-owned cover bytes without ambient network loading |
| `NovelTextSource` | Read plain text for a source-scoped media reference |
| `NovelSearchSource` | Return normalized novel previews and pagination information |
| `NovelSeriesSource` | Resolve novel metadata and the complete chapter list |
| `NovelChapterSource` | Read rich chapter HTML and registered source-owned resources |
| `PublicationSource` | Load a publication spine/TOC, sections and registered resources |

Capabilities compose. Android extension-backed manga sources implement search + series + pages + artwork. Local SAF implements direct video + pages + text + publications + platform availability + an optional open lease for archive-backed media. Remote novels use search + series + rich chapters. Domain does not know HTTP, SAF, SQLite, extension APKs, plugin JavaScript or Flutter widgets.

### Chapter list order

`MangaSeriesDetails` and `NovelDetails` keep immutable `chapters` exactly in source-provided structural order. Every details producer must declare a `ChapterListOrder`, which states whether that sequence begins with the first-read or last-read chapter. `chaptersInReadingOrder` exposes the corresponding immutable reading-order list without sorting on optional chapter numbers or dates.

Mihon declares reverse reading order because its chapter contract supplies descending source order. Hikari's current LNReader compatibility treats `parseNovel` page 1 followed by `parsePage` pages 2..N as reading order and declares that explicitly. Descending LNReader page-plugin semantics are not modeled yet; if Hikari adopts that upstream contract, the adapter must normalize or declare it explicitly rather than infer sequence from metadata. Providers may omit or repeat chapter numbers. LNReader pagination limits and duplicate-path rejection remain documented under [EXTENSIONS](EXTENSIONS.md).

Mihon collapses exact stable chapter identities (derived from URLs) before saving continuation state, retaining the first occurrence and its metadata. Different URLs remain distinct even when chapter numbers match. Chapter numbers, titles, dates and scanlators are metadata, never sequence authority.

Remote chapter-list UI continues to render `chapters` in structural source order and passes selected chapter with immutable `chaptersInReadingOrder` snapshot. Feature-local manga and novel reader sessions validate unique stable chapter references, filter unreadable manga targets, and publish adjacent targets only after successful open. See [Presentation architecture](PRESENTATION.md) for reader route lifetime, fresh progress sessions and handoff behavior. Direct/local readers remain single-chapter sessions.

Do not add a capability for a hypothetical future. Add one when a real vertical needs
an operation that cannot be expressed by the current contracts.

## Source registry

`application/sources/SourceRegistry` is an immutable app-lifetime index of concrete
`MediaSource` instances.

It provides three behaviors only:

1. resolve by stable `SourceId`;
2. require a capability from that source;
3. enumerate registered sources that implement a capability.

Duplicate IDs fail during composition instead of silently replacing an implementation.
The registry does not discover/install extensions, construct sources, persist configuration, manage authentication or own source lifecycles. Android extension discovery/construction happens before composition; installation/update UI and hot lifecycle management remain separate concerns.

## Application workflows

`OpenMedia` owns the proven cross-cutting open flow:

```text
Media
  -> resolve registered source
  -> reject unavailable source
  -> select behavior from MediaType + source capabilities
  -> load Progress when opening a concrete readable
  -> acquire an optional source-owned open lease
  -> return provider-neutral MediaOpenTarget that releases the lease after navigation
```

A manga source that exposes `MangaSeriesSource` produces a series target; progress
belongs to the selected chapter. A direct manga source produces a reader target.
Light novels are capability-routed: `PublicationSource` supplies a local EPUB,
`NovelSeriesSource` supplies a remote chapter-based series, and `NovelTextSource`
supplies plain text. Rich remote chapters do not require a plain-text capability.
Video requires `DirectVideoSource`, which makes the existing local locator/player
assumption explicit instead of treating every future anime source as directly playable.
Episode discovery, stream selection, headers and DRM remain separate future requirements.

`OpenMangaChapter` resolves `MangaPageSource`, loads chapter progress and resolves pages
before navigation. `OpenNovelChapter` does the equivalent for `NovelChapterSource`,
returning rich HTML with registered resources rather than coercing it to plain text.
Explicit Manga/Novel reader refreshes bypass cached source bytes while preserving older cache
entries on failure; see [ADR-021](../decisions/ADR-021-remote-reading-cache-freshness.md).
Remote novel content/resources use application cache workflows only after adapter
normalization; local `NovelTextSource` and `PublicationSource` paths remain uncached
by these workflows. See [ADR-012](../decisions/ADR-012-reconstructible-cache-foundation.md).
This keeps invalid chapter references from creating a reader route that cannot load.

`SearchManga` and `SearchNovels` own search-and-open invariants. They expose only
registered, available sources with the capabilities needed to open returned content,
normalize empty queries and reject malformed results whose media type or `SourceId`
does not match the selected source. A search-only capability is valid but is not
presented as openable content by these workflows. Search pages retain source pagination
information; presentation owns the current query, selected source and in-flight generation,
so an old response cannot append to a new search.

`ProgressSession` packages the initial persisted record and save operation for one
`SourceMediaRef`. It is application glue, not a reading-history model.

A stable source reference is **not** canonical cross-provider content identity. Its key
must not contain mutable display metadata or private continuation payloads. Sources own
any state required to call their provider again; UI and application workflows do not
interpret that state. Library snapshots survive source absence and remain removable.
Canonical identity, cross-provider reconciliation and SAF rename/move reconciliation
remain outside this scope.

Application targets may carry domain capability interfaces to presentation. They must
not carry `MihonMangaSource`, SAF adapters, Drift records, HTTP DTOs or other concrete
infrastructure types.

Opened-media resource ownership follows the same rule. `MediaOpenLeaseSource` is a
small capability for the proven case where an opened item needs a source-owned resource
to stay alive. `OpenMedia` acquires the lease only after the requested open capability
has been validated; the returned target owns release. Presentation never checks a
specific source ID to retain or release provider/local resources.

## Source preview handoff (Phase 5.2)

Catalog resolution maps `CatalogSourceCandidate` into the existing presentation
`CatalogSourcePickerCandidate` at the view-model boundary for exact-match and explicit
candidate selection; widgets do not depend on application workflows. Source Search forwards its selected result's own optional
`MediaMetadata`. Both routes call `OpenMedia.execute(media, previewMetadata: ...)`;
AniList metadata is never converted into source preview metadata. Entry points without
source metadata, including Library and Continue, pass none. A preview cover with a
foreign `SourceId` is rejected with `ArgumentError` by `OpenMedia` and by series view
models when a series reference is available.

Series targets carry preview metadata without requesting details. Manga/Novel series
view models own that initial preview separately from complete details. Effective
metadata is **full details metadata, otherwise preview**: the first successful full
response replaces the entire preview, including null cover/summary and empty lists.
No field merging or fake chapter details is permitted. Initial loading and initial
failure remain chapter-based states; preview can display during either, with progress
or an announced error and Retry, but no reading CTA, chapter controls, or empty-list
claim until actual details arrive. The same metadata header remains mounted across
preview-to-full transitions; unchanged cover/owner retains its artwork future.

Ordinary rebuilds do not reload details or restore discarded preview. A changed series
reference creates a new view model with its new initial preview. Provider-owner changes
clear old preview and details before loading and retain generation/continuation guards.
Library save can use the effective cover while details are pending; artwork-only
updates preserve pending membership mutations. Stored media title, progress, added time,
and non-null saved artwork are not refreshed from source details. Existing SQL
fill-null-only artwork policy is unchanged. Preview is route-local, not offline Detail
persistence, and does not replace fresh complete chapter loading (ADR-024).

### Request audit

No same-identity/same-semantics duplicate request was established; no request removal,
new dedupe coordinator, cache, alias fanout, or dependency was added.

| Path | Retained work / handoff |
| --- | --- |
| Catalog discovery/search | Existing AniList cache and in-flight request dedupe; discovery identity stays catalog-only |
| Catalog details | Existing cached detail workflow; catalog artwork never becomes source artwork |
| Catalog exact source match | Existing bounded title/alias search; selected candidate keeps its source metadata |
| Catalog ambiguous candidate | Explicit confirmation remains; chosen candidate keeps its own metadata |
| Manual Source Search | Manga/Novel result metadata follows selected source media |
| Source Detail entry | Preview renders immediately; one fresh complete detail load still required |
| Detail refresh | Mihon `loadSeries` refreshes metadata, memo and chapters; LNReader `parseNovel` plus pages 2..N yields complete chapters |
| Library save/reopen | Effective artwork may fill missing saved artwork; reopen does not manufacture preview or update saved title/progress |
| Continue / reader | Reader-first open and independent cached-sequence/fresh reconciliation unchanged; artwork uses existing source artwork workflow |

Upstream evidence supports distinguishing previews from full details, not copying
upstream persistence or merge behavior: Mihon `SMangaImpl.initialized=false`
([pinned source](https://github.com/mihonapp/mihon/blob/1ca9b0efb817b54948436e19906de9a5a2dca6b2/source-api/src/main/kotlin/eu/kanade/tachiyomi/source/model/SMangaImpl.kt));
LNReader `NovelItem` versus `SourceNovel` and distinct `parseNovel`/`parsePage`
([pinned types](https://github.com/LNReader/lnreader/blob/14d87add614960b5d1f300092339fa191399c393/src/plugins/types/index.ts),
[plugin contract](https://github.com/LNReader/lnreader-plugins/blob/master/docs/docs.md));
Kotatsu emits provisional metadata with `isLoaded=false`
([pinned use case](https://github.com/KotatsuApp/Kotatsu/blob/34f6e5232bf7f0bcdf8f3fbddb986697518e82a4/app/src/main/kotlin/org/koitharu/kotatsu/details/domain/DetailsLoadUseCase.kt)).
Whole-response precedence and route-local ownership above are Hikari design choices,
not claims of identical upstream behavior or measured performance improvements.

## Local SAF discovery (Phase 5.3)

`LocalMediaSource` owns one in-flight traversal and one complete session snapshot,
keyed by opaque root ID. `scanSelectedRoot()` validates the persisted grant and
resolves the selected root on every call, then joins same-root work or reuses the
completed snapshot. Source Search has no second completed-catalog cache.
`scanSelectedRootSnapshot()` is explicit refresh: it traverses afresh unless a
same-root traversal is already running. Root display names are not identities;
validated reuse updates the display label without rewriting Library or Progress.

Picker opening cancels obsolete traversal before native replacement can release
the old grant. Searches wait for the picker outcome and revalidate afterward.
Picker cancellation preserves the completed snapshot. Root replacement, absent
root, access loss and source close invalidate it; transient same-root errors keep
the prior complete snapshot and Local's stale-result warning (ADR-018). Cancelled
work never publishes partial data and is not shown as provider failure. Page
disposal does not cancel app-lifetime source work that another consumer may join.
Source close cancels discovery without changing archive lease/copy ownership.
Reader folder pages still query on demand, never from the discovery snapshot.

Android uses one serialized discovery worker for root description, picker commit
and scan-only child queries; existing serialized reader worker handles folder
pages, reads, archive materialization/deletion and startup cleanup. Old grant
release passes a barrier behind already submitted reader work, then checks and
releases on the discovery worker, serialized with picker grant/commit mutations. Cancellation control is
handled outside both queues, with local atomic checks before/after each query and
through cursor iteration. No remote `CancellationSignal.cancel()` is invoked on
the UI thread: provider Binder cancellation may itself block. Cancellation is
cooperative, not a deadline or guaranteed provider interruption. A blocked old
query can delay replacement discovery, but does not occupy Hikari's reader
executor. Providers may still serialize their own operations. Each accepted
MethodChannel call owns exactly one completion, including queued work on close.

Traversal is iterative and bounded to **50,000 received entries including root**.
Raw rows count even when duplicates are skipped. Each directory is queried at
most once; repeated rows within a directory are collapsed, shared file-parent
edges retained, and classified media deduplicated by stable source reference.
Malformed identifiers/types, null cursors, cursor exceptions and `EXTRA_LOADING`
fail the scan instead of caching partial results. Child queries project ID,
display name and MIME together: baseline already had no per-file query N+1.
Classification/natural sorting stay off the UI isolate via existing `compute`.
Folder artwork uses a single-pass minimum with unchanged cover/natural-name/ID
priority. No eager file reads or archive materialization are added.

No persistent index is introduced. Nullable size/modification metadata and
ContentObserver notifications do not establish recursive subtree freshness or a
complete change journal; explicit refresh remains authority, and even a full
walk is not an atomic provider snapshot. Upstream evidence and profiling limits
are recorded in [ADR-018](../decisions/ADR-018-local-media-scan-presentation.md).

Opt-in `--dart-define=HIKARI_LOCAL_SCAN_TRACE=true` emits numeric-only `LocalScan`
DevTools timeline events: directory/raw-entry/query counts, total/traversal/
classification microseconds, summed native queue/query microseconds and
reuse/cancelled/failed flags. Native timings accompany scan rows only; there are
no URI, name, title, document ID or error-content trace fields, persistent logs,
or uploads. Native timings are available only for completed query responses;
cancelled/failed queries do not contribute them. Root validation is separate
from traversal timing. Reuse events contain status flags only. Do not ship
instrumentation enabled or infer Android speedups from desktop fixtures.

## Composition and ownership

`AppDependencies.create()` is the explicit composition root:

```text
UserDatabase
  -> SqliteLibraryRepository
  -> SqliteProgressRepository

LocalMediaSource --------\
                            -> SourceRegistry -> OpenMedia / chapter-open / search workflows
installed extension sources -/

MediaKitVideoSession ------------------------> presentation composition
```

Platform bootstrap discovers compatible external sources before composition. `additionalSources` is the single generic registration seam beyond the local source. No remote provider is constructed by `AppDependencies`; an empty discovery result is valid and hides remote search through existing capability checks.

Default resources created there are disposed there. Injected resources remain owned by
the caller where ownership is externally supplied. The default byte cache is
reconstructible and isolated from `UserDatabase`; see [ADR-012](../decisions/ADR-012-reconstructible-cache-foundation.md).
No service locator or runtime DI container is required for the current graph.

## Adding a source

For a new source that fits existing contracts:

1. adapt the external runtime source to the required domain capabilities in `infrastructure/`; keep provider-specific networking and website behavior in the extension;
2. give it a stable, unique `SourceId`;
3. register the instance at composition;
4. reuse the existing application open workflow and readers;
5. add new discovery UI only when the capability is not already surfaced. Registered
   readable `MangaSearchSource` implementations appear through `SearchManga` without a
   provider-specific branch in app orchestration.

The open workflow should not gain `if (source.id == ...)` branches for such a source.
Provider-specific parsing, throttling, auth and transport stay with the installed
extension; Hikari infrastructure owns host/runtime adaptation.

If the new source needs a new product workflow, design that requirement explicitly.
For example, remote anime may eventually require episode and stream capabilities. That
would be a legitimate domain/application extension, not evidence that the registry
failed.

## Future feature fit

The current boundary is intended to support, without predicting their detailed APIs:

- additional manga sources implementing search/chapters/pages;
- local manga evolving from direct folders to series/chapter capability;
- search across registered readable `MangaSearchSource` implementations through `SearchManga`;
- Continue Reading/Watching resolving persisted `SourceMediaRef` through the same
  registry/application workflows;
- additional extension ABI families/adapters that still create normal source instances before composition.

The following remain deliberately outside this foundation: canonical content identity,
dedup/metadata reconciliation, extension repository/install/update UI, process sandboxing,
account/auth framework, remote video episode/stream contracts, downloads and sync.

See [ADR-005](../decisions/ADR-005-application-source-registry.md) for the registry/open
workflow decision and [ADR-006](../decisions/ADR-006-presentation-state-and-player-boundary.md)
for the search/presentation boundary refinement, and [ADR-007](../decisions/ADR-007-android-manga-extension-runtime.md) for the Android extension host.

## Research references

These are design references, not dependencies or APIs Hikari promises to copy:

- [Flutter architecture recommendations](https://docs.flutter.dev/app-architecture/recommendations) — separate responsibilities, constructor/DI wiring, use application/domain logic when complexity or reuse justifies it.
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) — keep UI/data responsibilities explicit and introduce use cases for reused or complex coordination.
- [Keiyoushi extension contributor guidance](https://github.com/keiyoushi/extensions-source/blob/main/AGENTS.md) — current source ecosystems favor explicit source contracts and warn against speculative provider abstractions.
- [Mihon module map](https://github.com/mihonapp/mihon/blob/main/settings.gradle.kts) — source API/local source, domain, data and presentation remain distinct modules in a mature reader codebase.
- [Aniyomi extension contributor guide](https://github.com/aniyomiorg/aniyomi-extensions/blob/master/CONTRIBUTING.md) — a mature extension ecosystem exposes explicit source contracts and a separate packaging/runtime model.
- [Mangayomi Dart extension guide](https://github.com/kodjodevf/mangayomi-extensions/blob/main/CONTRIBUTING-DART.md) — another Flutter reader keeps extension metadata/runtime concerns outside the app's normalized media model.
