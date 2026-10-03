# ADR-006: Feature-local presentation state and a strict player UI boundary

- Status: **Accepted**
- Date: **2026-09-27**

## Context

After ADR-005 moved source/progress orchestration out of `app.dart`, the remaining
complexity was concentrated in presentation. `LocalMediaPage` and
`RemoteMangaSearchPage` each owned several coupled async flags/results/error values and
performed transition logic directly inside widget state. Separately,
`infrastructure/playback/local_video.dart` contained a Flutter `StatelessWidget`, so the
folder dependency direction was legal but the responsibility boundary from ADR-001 was
not.

Remote manga search also enumerated every `MangaSearchSource` directly. A source could
therefore satisfy the search contract while lacking any readable page path; the UI would
show results that `OpenMedia` could not complete.

## Decision

For presentation flows with non-trivial asynchronous state, use a feature-local
ViewModel/state holder. The current implementation introduces:

- `LocalMediaViewModel` with explicit initial/loading/ready/failure states;
- `RemoteMangaSearchViewModel` for source selection and search state.

These remain in `features/` because they are presentation logic. They may use Flutter's
lightweight `ChangeNotifier`; this decision does not introduce a global state-management
framework or DI container.

Add the pure application workflow `SearchManga`. It:

- enumerates registered `MangaSearchSource` implementations that also expose
  `MangaPageSource`, the minimum currently proven path to readable pages;
- excludes unavailable sources;
- trims empty queries;
- validates that returned items are manga and remain scoped to the selected source.

A search-only source remains a valid domain capability but is not surfaced in the
current search-and-open UI until Hikari has a real workflow for non-openable search
results.

Move Flutter video rendering to `features/player/VideoSurface`. Rename the app-owned
player lifecycle objects to `MediaKitVideoSession` and `MediaKitVideoPlayback` so their
implementation responsibility is explicit. Playback state change notification uses a
Dart stream rather than making the infrastructure object a Flutter `ChangeNotifier`.
The app composition root bridges that session/controller into the presentation widget.

Extend the architecture guard so `infrastructure/` may import only Flutter
`foundation.dart` and `services.dart`; other Flutter libraries are rejected there. Those
two platform-oriented APIs remain allowed for adapters.

## Alternatives considered

### Add Provider/Riverpod/BLoC during the refactor

Rejected. The current state is local to individual routes and constructor composition is
small. A package would add lifecycle/wiring policy without solving a demonstrated
cross-screen state problem.

### Put all UI state into application use cases

Rejected. Loading flags, selected search source, picker-cancellation rendering and UI
errors are presentation concerns. Application remains Flutter-independent and owns only
workflow policy that should be reusable outside one widget.

### Require every `MangaSearchSource` to also implement all manga reading capabilities

Rejected. Capability composition remains useful: search-only sources can exist for a
future workflow. `SearchManga` defines the stronger invariant only for the current
search-and-open use case.

### Introduce a generic player engine abstraction in domain now

Rejected. Hikari still has one proven direct-video engine path. Moving the Flutter
surface out of infrastructure fixes the real boundary leak without predicting episode,
stream, DRM or alternate-engine requirements.

## Consequences

- Stateful pages become easier to reason about and test without adding a global state
  framework.
- Remote search results shown by the current UI have a proven readable-page capability
  and valid source-scoped identity.
- Infrastructure no longer owns Flutter widgets, and the guard prevents regression.
- Player rendering still intentionally uses `media_kit_video` in the player feature;
  engine lifecycle/commands remain infrastructure.
- Simple presentation components may still call domain repositories directly when an
  additional use case/ViewModel would only be pass-through ceremony.
- Local folder discovery remains one concrete feature seam. A generic browse/import
  capability is deferred until a second real discovery source/platform proves its shape.
