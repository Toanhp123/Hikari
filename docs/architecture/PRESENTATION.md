# Presentation architecture

## Purpose

`features/` owns Flutter widgets and presentation state. Widgets should render state and
forward user intent; non-trivial asynchronous state transitions belong in a feature-local
ViewModel/state holder. Application workflows remain pure Dart and infrastructure must not
own Flutter presentation widgets.

```text
Flutter View
   |
   v
feature ViewModel / state holder
   |
   +----> application workflow (when coordination/policy exists)
   |
   `----> simple domain repository operation (when no use case is justified)
```

This is a pragmatic boundary, not a requirement to create one ViewModel class for every
widget. Stateless display components and simple one-operation controls do not need an
extra layer solely for symmetry.

## Current state holders

### Local media

`LocalMediaViewModel` owns folder-selection and scan presentation state:

```text
initial -> loading -> ready
                  `-> failure
```

Picker cancellation restores the exact prior presentation state. Selecting a new root
starts from loading and never restores stale results if the new scan fails. The page
renders the state and owns only route/UI concerns such as refreshing Library buttons
after returning from another route.

### Remote manga search

`RemoteMangaSearchViewModel` owns selected source, loading/failure state and search
results. It calls the pure `SearchManga` application workflow rather than invoking a
concrete source directly. Switching sources clears stale results. Search input is
normalized before execution and failures remain presentation state instead of leaking
source exceptions into widgets.

## Player boundary

The native/player lifecycle remains infrastructure:

```text
MediaKitVideoSession
  -> MediaKitVideoPlayback
  -> Player / VideoController ownership
  -> progress tracker / serialized native commands
```

The Flutter surface is presentation:

```text
features/player/VideoSurface
  -> media_kit_video Video widget
  -> reads controller + loading/error snapshots supplied by composition
```

`MediaKitVideoPlayback` exposes a Dart stream for state-change notifications instead of
extending Flutter `ChangeNotifier`. The app composition root connects the infrastructure
session to `VideoSurface`. `features/` never imports Hikari infrastructure classes.

The architecture guard allows only Flutter `foundation.dart` and `services.dart` inside
`infrastructure/`; other Flutter libraries are rejected there. Platform adapters such as
the Android method-channel source legitimately require those two APIs.

## Dependency injection and state-management packages

Hikari currently uses explicit constructor injection and `ChangeNotifier` only inside
feature-local presentation state holders. No Provider/Riverpod/BLoC/GetIt dependency is
required for the current graph.

A state-management or DI package can be introduced later if dependency scope, state
sharing or lifecycle pressure proves that the explicit composition approach is becoming
costly. That change must not alter domain/application contracts merely to satisfy a UI
framework.

## Rules

- Keep widgets focused on rendering, navigation and forwarding user events.
- Move multi-step async state transitions out of widget `State` once they become hard to
  reason about or test.
- Do not introduce pass-through ViewModels/use cases for trivial display-only widgets.
- `features/` may depend on Flutter/UI packages, application workflows and domain
  contracts, but never Hikari infrastructure implementations.
- `infrastructure/` may use Flutter platform APIs when required by adapters, but must not
  contain presentation widgets.
- Application workflows return domain/application data, never `BuildContext`, routes or
  Flutter widgets.

## References

- [Flutter architecture guide](https://docs.flutter.dev/app-architecture/guide)
- [Flutter architecture recommendations](https://docs.flutter.dev/app-architecture/recommendations)
- [Flutter UI-layer case study](https://docs.flutter.dev/app-architecture/case-study/ui-layer)
- [Android UI-layer architecture](https://developer.android.com/topic/architecture/ui-layer)
