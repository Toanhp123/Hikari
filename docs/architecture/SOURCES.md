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
the code. **Extension** means a possible future packaging/distribution mechanism that
creates source implementations. Do not introduce parallel `Provider`/`Source` base
types for the same responsibility.

## Domain capabilities

The current source surface is deliberately small:

| Capability | Responsibility |
| --- | --- |
| `MediaSource` | Stable opaque `SourceId` and user-facing source name |
| `MediaSourceAvailability` | Whether a registered source can operate on this device/platform |
| `MangaSearchSource` | Search and return normalized `Media` |
| `DirectVideoSource` | Turn a source-scoped video reference into a locator accepted by the current direct player path |
| `MangaChapterSource` | Resolve a manga series into provider-neutral chapters |
| `MangaPageSource` | Resolve a readable reference into pages and read page bytes |
| `NovelTextSource` | Read text for a source-scoped media reference |

Capabilities compose. MangaDex implements search + chapters + pages. Local SAF
implements direct video + pages + text + platform availability. Domain does not know HTTP, SAF,
SQLite, MangaDex DTOs or Flutter widgets.

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
The registry does not install extensions, construct sources, persist configuration,
manage authentication or own source lifecycles. Those are separate future concerns.

## Application workflows

`OpenMedia` owns the proven cross-cutting open flow:

```text
Media
  -> resolve registered source
  -> reject unavailable source
  -> select behavior from MediaType + source capabilities
  -> load Progress when opening a concrete readable
  -> return provider-neutral MediaOpenTarget
```

A manga source that also exposes `MangaChapterSource` produces a series target; progress
is deliberately not loaded for the series because current remote progress belongs to a
selected chapter. A direct manga source produces a reader target. Novel content
requires `NovelTextSource`. Video requires `DirectVideoSource`, which makes the existing
local locator/player assumption explicit instead of treating every future anime source
as directly playable. Episode discovery, stream selection, headers and DRM remain
separate future requirements.

`OpenMangaChapter` resolves `MangaPageSource`, loads chapter progress and resolves pages
before navigation. This keeps stale remote references from creating a reader route that
cannot load.

`ProgressSession` packages the initial persisted record and save operation for one
`SourceMediaRef`. It is application glue, not a reading-history model.

Application targets may carry domain capability interfaces to presentation. They must
not carry `MangaDexSource`, SAF adapters, Drift records, HTTP DTOs or other concrete
infrastructure types.

## Composition and ownership

`AppDependencies.create()` is the explicit composition root:

```text
UserDatabase
  -> SqliteLibraryRepository
  -> SqliteProgressRepository

LocalMediaSource ----\
                      -> SourceRegistry -> OpenMedia / OpenMangaChapter
MangaDexSource ------/

LocalVideoSession -----------------------> presentation composition
```

Default resources created there are disposed there. Injected resources remain owned by
the caller where ownership is externally supplied. No service locator or runtime DI
container is required for the current graph.

## Adding a source

For a new source that fits existing contracts:

1. implement the required domain capability interfaces in `infrastructure/`;
2. give it a stable, unique `SourceId`;
3. register the instance at composition;
4. reuse the existing application open workflow and readers;
5. add new discovery UI only when the capability is not already surfaced. Registered
   `MangaSearchSource` implementations already appear in the current source selector.

The open workflow should not gain `if (source.id == ...)` branches for such a source.
Provider-specific parsing, throttling, auth and transport stay with that provider's
infrastructure.

If the new source needs a new product workflow, design that requirement explicitly.
For example, remote anime may eventually require episode and stream capabilities. That
would be a legitimate domain/application extension, not evidence that the registry
failed.

## Future feature fit

The current boundary is intended to support, without predicting their detailed APIs:

- additional manga sources implementing search/chapters/pages;
- local manga evolving from direct folders to series/chapter capability;
- search across registered `MangaSearchSource` implementations through the existing source selector;
- Continue Reading/Watching resolving persisted `SourceMediaRef` through the same
  registry/application workflows;
- an eventual extension runtime that creates source instances before composition.

The following remain deliberately outside this foundation: canonical content identity,
dedup/metadata reconciliation, dynamic extension installation, extension sandboxing,
account/auth framework, remote video episode/stream contracts, downloads and sync.

See [ADR-005](../decisions/ADR-005-application-source-registry.md) for the decision and
trade-offs.

## Research references

These are design references, not dependencies or APIs Hikari promises to copy:

- [Flutter architecture recommendations](https://docs.flutter.dev/app-architecture/recommendations) — separate responsibilities, constructor/DI wiring, use application/domain logic when complexity or reuse justifies it.
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) — keep UI/data responsibilities explicit and introduce use cases for reused or complex coordination.
- [Keiyoushi extension contributor guidance](https://github.com/keiyoushi/extensions-source/blob/main/AGENTS.md) — current source ecosystems favor explicit source contracts and warn against speculative provider abstractions.
- [Mihon module map](https://github.com/mihonapp/mihon/blob/main/settings.gradle.kts) — source API/local source, domain, data and presentation remain distinct modules in a mature reader codebase.
- [Aniyomi extension contributor guide](https://github.com/aniyomiorg/aniyomi-extensions/blob/master/CONTRIBUTING.md) — a mature extension ecosystem exposes explicit source contracts and a separate packaging/runtime model.
- [Mangayomi Dart extension guide](https://github.com/kodjodevf/mangayomi-extensions/blob/main/CONTRIBUTING-DART.md) — another Flutter reader keeps extension metadata/runtime concerns outside the app's normalized media model.
