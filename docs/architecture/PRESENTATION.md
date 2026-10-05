# Presentation architecture

## Purpose

`features/` owns Flutter routes, feature-local presentation state and feature-owned
visual composition. Pages are route/lifecycle boundaries; non-page visual components
live under the owning feature's `widgets/` directory. Data-driven state transitions
belong in a feature-local ViewModel/state holder. Application workflows remain pure Dart
and infrastructure must not own Flutter presentation widgets.

```text
feature page
   |
   +----> feature widgets (render state + forward intent)
   |
   v
feature ViewModel / state holder
   |
   +----> application workflow (when coordination/policy exists)
   |
   `----> simple domain repository operation (when no use case is justified)
```

The canonical file/ownership rules live in [UI Architecture](UI_ARCHITECTURE.md).
Hikari deliberately uses a hybrid organization: presentation is grouped by feature, while
application/domain/infrastructure stay grouped by architectural layer because those
contracts are shared across features. Do not create feature-local copies of repositories,
services or infrastructure merely to make every folder tree look identical.

Hikari also remains a single Flutter package for now. Promote a feature into its own
package only when a real boundary needs compiler/package enforcement, such as reuse by a
second app, materially different platform/dependency requirements, independent release
or build concerns, or sustained ownership conflicts. Folder symmetry alone is not a
reason to create package-per-feature overhead; the architecture guard enforces the
current in-package presentation rules.

This is a semantic boundary, not a line-count rule: a small async/filtering route uses
the same ownership model as a large one, while purely visual ephemeral state such as
focus, scroll, animation and expansion may remain in the view. State holders expose one
immutable UI-state snapshot; status enums are the default when a route preserves controls
or stale content, while sealed states are reserved for genuinely mutually-exclusive
flows.

## Active state holders

### Home

`HomeViewModel` joins Library media with incomplete Progress records, owns the selected
media filter and catalog-discovery loading/retry state, and exposes the effective filtered
feed. It exposes progress position data rather than producing English progress copy; the
Continue widget formats that presentation at the UI edge. Catalog discovery widgets render
data supplied by Home instead of executing `DiscoverCatalog` themselves.

### Catalog

`CatalogSearchViewModel` owns query/filter/search transitions, discards stale async
completions and exposes explicit idle/loading/ready/empty/error state.

`CatalogDetailViewModel` owns metadata loading/retry and preserves the last successful
detail when a refresh fails. Catalog Detail uses an explicit refresh action rather than
pull-to-refresh because it is a metadata detail route, not a top-updating collection.
Description expansion remains in the page because it is purely visual ephemeral state.

### Source search

`SourceSearchViewModel` owns the title-seeded search session, media filter, source
fan-out, source failures, de-duplication and local-catalog cache. The page owns only the
text controller/lifecycle and route callbacks; rendering lives under
`features/source_search/widgets/`.

### Library and local media

`LibraryViewModel` owns repository-backed entries, media filtering and grid/list mode.
`LibraryButtonViewModel` owns the reusable per-media membership load/toggle flow; the
button widget owns only its ViewModel lifecycle and user-facing failure feedback.
`LocalMediaViewModel` owns folder-selection and scan presentation state. A rescan keeps
the last successful media list visible while the scan runs and if that rescan fails; a
newly selected folder clears the old result identity before its first scan. Picker
cancellation restores the prior local-media presentation state. Route pages own
only lifecycle wiring; reusable grids/cards/content live in their feature `widgets/`
directories.

### Series detail

Remote manga/novel series routes keep feature-local state holders for chapter loading.
Their chapter collections support adaptive pull-to-refresh plus an explicit refresh action,
and retain the last usable chapter list if refresh fails. Chapter selection passes an immutable
snapshot in declared reading order; each reader route keeps that sequence fixed for its lifetime.
Feature-local chapter reader routes own adjacent-navigation ViewModels; those ViewModels use injected
open workflows and stable source references, publish a new chapter only after a successful open,
and ignore stale completions after route close. Each successful open supplies a fresh
`ProgressSession` for its chapter. Manga routes own one page-prefetch instance per current chapter target
and cancel stale pending work on navigation or foreground reads. Remote novel routes own one
chapter-prefetch instance per reader session: they warm only the next reading-order chapter through
the existing normalized content/resource cache workflows, fetch registered resources serially, and
cancel stale queued work on chapter handoff or route close. Low-level reader pages remain single-chapter
readers and reserve progress saves before chapter handoff. Catalog Detail keeps manga/light-novel
source choice in context through `CatalogSourcePickerViewModel`: a selected source is resolved
against the catalog title/aliases and an exact unique match opens the existing series route
directly. `SourceSearchPage` remains the unified discovery/recovery route for explicit
search-all and source-scoped manual correction; the obsolete source-specific remote
manga/novel search pages and their duplicate presentation state remain retired. Pagination
remains a source/application capability and should be added to unified source search only when
that production route needs it.

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
features/player/widgets/VideoSurface
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
sharing or lifecycle pressure proves that explicit composition is becoming costly. That
change must not alter domain/application contracts merely to satisfy a UI framework.

## Rules

- Pages own route composition, Flutter controller lifecycle and navigation wiring.
- Non-page feature visual components live under `features/<feature>/widgets/`.
- Feature widgets do not execute application workflows or repository operations directly;
  reusable behavior widgets use a paired feature-local ViewModel.
- Use a feature ViewModel when the route owns async loading, filtering, pagination,
  retry, selection that changes visible data, or data-derived UI state.
- Keep ViewModels independent from page/widget imports, `BuildContext`, Material view
  classes and localizable product copy.
- Keep pure visual ephemeral state in the view instead of creating ceremony solely for
  symmetry. One-shot command `busy` state may remain in the page only when the result
  does not become retryable/data-derived screen state.
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
- [Very Good Ventures feature-first architecture](https://engineering.verygood.ventures/architecture/ffca/overview/)
- [Immich mobile architecture](https://docs.immich.app/developer/architecture/)
- [AppFlowy frontend code map](https://docs.appflowy.io/docs/documentation/software-contributions/architecture/frontend/frontend/codemap)
