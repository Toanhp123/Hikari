# ADR-005: Application workflows and a capability-based source registry

- Status: **Accepted; direct-provider transport decision superseded by [ADR-008](ADR-008-external-remote-provider-ownership.md)**
- Date: **2026-09-27**

Follow-up: [ADR-007](ADR-007-android-manga-extension-runtime.md) now realizes the extension seam anticipated here while keeping the registry immutable after composition.

## Context

The local-media, persisted Library/Progress and MangaDex verticals proved the domain
source contracts, but their integration accumulated in `app/app.dart`. The app root
resolved source IDs, checked capabilities, loaded progress, prepared remote chapters,
selected reader/player routes and owned concrete dependencies. Adding another source
with an existing capability would therefore require touching central presentation
orchestration even when the domain contract already described everything required.

This is the concrete pressure anticipated by ADR-001 for an `application/` boundary.
The project now needs one place for cross-domain workflows and one source index, while
still avoiding a speculative plugin runtime, generic provider base class or DI
container.

## Decision

Introduce a small pure-Dart application layer:

- `SourceRegistry` owns the immutable app-lifetime `SourceId -> MediaSource` index,
  rejects duplicate stable IDs and resolves capability interfaces.
- `OpenMedia` resolves a `Media` through the registry, validates source availability,
  loads source-scoped progress when appropriate and returns a provider-neutral open
  target for video, direct manga, manga series or novel content.
- `OpenMangaChapter` resolves the page capability, loads chapter progress and resolves
  the page list before navigation so stale remote chapters fail before a reader route
  is pushed.
- `ProgressSession` binds one `SourceMediaRef` to its initial progress and save
  operation so presentation does not recreate persistence workflow logic.

Introduce `AppDependencies` as the composition root object. It creates and owns default
infrastructure, constructs repositories, registers sources and injects application
workflows. Tests can replace the database or sources through constructor/factory
arguments. Resource ownership remains explicit: only resources created by the
composition root are closed by it.

Add the pure domain capability `MediaSourceAvailability` for a source whose ability to
operate depends on the current platform/device. Local SAF implements it; application
workflows reject unavailable registered sources without knowing Android details.

Follow-up (2026-09-30): archive-backed CBZ/EPUB proved one additional lifecycle need.
`MediaOpenLeaseSource` lets a source retain an opaque resource while opened media is in
use. `OpenMedia` acquires the optional lease only after the requested open capability is
validated, and the provider-neutral `MediaOpenTarget` owns its release. The app therefore
does not branch on `LocalMediaSource` or archive formats, while `SourceRegistry` remains
an immutable index rather than a source lifecycle manager.

Add `DirectVideoSource` for the already-proven direct-player path. Local SAF turns its
source-scoped reference into the locator consumed by `MediaKitVideoSession`. `OpenMedia`
requires that capability for `MediaType.anime`, so a future remote anime source cannot
accidentally be treated as a local/direct locator. Rich episode/stream playback remains
a separate future design problem.

At this decision's adoption, MangaDex kept implementing the same domain capabilities. Its HTTP policy was moved to
an infrastructure-local `MangaDexClient`; this is source-specific transport
separation, not a generic network/provider framework.

The registry is intentionally immutable during the current app lifetime. A future
extension runtime may discover/create `MediaSource` instances and compose a registry,
but hot loading, package discovery, permissions, sandboxing, version negotiation and
extension lifecycle are not designed here.

## Extension rule

Adding another implementation of **existing** capabilities should not require changing
`OpenMedia` or `OpenMangaChapter`. The implementation is registered at composition.
The current remote-manga search flow is extended by `SearchManga` (ADR-006), which
enumerates registered searchable sources that also have a readable-page path. An
additional source satisfying those existing capabilities becomes selectable without a
provider-specific branch in app orchestration.

A genuinely new workflow is allowed to change application code. For example, remote
anime that requires episode discovery and stream selection would first justify new
domain capabilities and an application open target; Hikari does not invent those
contracts in advance.

## Alternatives considered

### Keep orchestration in `app.dart`

Rejected. It had become the shared source/progress/navigation workflow layer and would
continue to grow a source-specific conditional surface.

### Add Provider/Riverpod/GetIt or another DI package now

Rejected for the current graph. Constructor injection plus one explicit composition
root provides replacement and ownership semantics without another runtime mechanism.
A UI DI package can be introduced later without changing domain/application contracts
if dependency scope or presentation state makes it useful.

### Build a generic provider/plugin engine now

Rejected. Dynamic installation, discovery, sandboxing, compatibility and extension
lifecycle have no current consumer. `SourceRegistry` solves the proven source
resolution problem without claiming to solve those future requirements.

### One use-case class per repository operation

Rejected. Simple Library/Progress CRUD remains callable through domain repositories.
Application workflows are introduced only where coordination and policy actually
exist.

## Consequences

- `app/` returns to composition, lifecycle and Flutter navigation rather than owning
  content-opening rules.
- `application/` remains pure Dart and is covered by the architecture guard.
- Source implementations remain capability-oriented; no media-type-specific base
  provider hierarchy is introduced.
- Existing source-scoped identity and persistence schema do not change.
- Future same-capability sources have a stable registration seam.
- A future extension runtime can sit before composition instead of forcing provider
  discovery into domain or presentation.
- Adding a new kind of media workflow still requires an explicit domain/application
  design decision rather than being hidden behind generic abstractions.

## Follow-up

ADR-007 now realizes the pre-composition discovery seam on Android for trusted manga
extensions. `SourceRegistry` itself remains immutable; package discovery and ABI
compatibility stay in infrastructure before composition.
