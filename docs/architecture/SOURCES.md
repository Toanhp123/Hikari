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

`MangaSeriesDetails` and `NovelDetails` keep immutable `chapters` exactly in source-provided structural order. `ChapterListOrder` declares whether that sequence begins with first-read chapter; absent declaration defaults to reading order for existing callers. `chaptersInReadingOrder` provides a separate reversed immutable view when requested, without sorting on optional chapter numbers or dates.

Mihon declares reverse reading order because its chapter contract supplies descending source order. LNReader declares reading order: its detail adapter appends paginated chapter lists unchanged, preserving the plugin's structural sequence. Do not infer sequence from metadata; providers may omit or repeat chapter numbers. LNReader pagination limits and duplicate-path rejection remain documented under [EXTENSIONS](EXTENSIONS.md).

Mihon collapses exact stable chapter identities (derived from URLs) before saving continuation state, retaining the first occurrence and its metadata. Different URLs remain distinct even when chapter numbers match. Chapter numbers, titles, dates and scanlators are metadata, never sequence authority.

Remote chapter-list UI continues to render `chapters` in structural source order and passes selected chapter through unchanged. This contract adds no navigation or reader lifecycle behavior.

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
