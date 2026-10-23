# Media and adaptive collections

Owner: core UI rendering/layout; feature presentation owns identity, providers,
formatting and business callbacks. Lifecycle: **Preview** under ADR-026; not
Stable until later owner gates. Public API: `lib/core/ui/hikari_ui.dart`.

## Contracts

- `HikariArtwork(image, semanticLabel, fit, placeholder)` renders a native
  `ImageProvider`, or placeholder only when absent. Loading/error states preserve
  explicit image semantics. Omit the label for decorative artwork; cards exclude
  artwork semantics and retain the full title once. **Layout contract:** the caller
  must supply finite, positive width and height constraints for image rendering
  (typically `AspectRatio` inside a bounded card, or an explicitly sized detail
  region). Do not place `HikariArtwork` directly in an unbounded scroll axis or
  unconstrained `Column`; `decodeTargetSize` returning null for invalid width is
  a decode fallback, not permission to lay out an infinite-sized `Image`.
- `decodeTargetSize(logicalSize, devicePixelRatio)` uses width-only physical
  decode buckets of 64 px, minimum 64, maximum 2048. Height remains unspecified
  to preserve source aspect ratio. Invalid/unbounded width or DPR returns null.
  Already-resized providers are not nested. This policy bounds requested width,
  not total decoded memory or height of pathological images.
- `HikariPosterCard` / `HikariLandscapeCard`: required title/artwork, optional
  subtitle, badge, progress, onTap, heroTag. Artwork ratios 2:3 / 16:9; title has
  two scaled lines, subtitle one. Full title remains accessible despite visual
  ellipsis. Features supply source-unique Hero tags; no title/index-derived tags.
- `HikariMediaProgress` clamps finite/infinite values to 0..1; NaN becomes zero.
  Native progress semantics announce percentage. Library suppresses completed
  overlays while retaining feature reading-status semantics.
- `HikariAspectRatios` names poster, landscape and square artwork geometry.
- `hikariCardExtent(context, width, hasSubtitle, aspectRatio)` reserves artwork
  plus scaled text. Bounded 24-entry typography/scaler/direction cache avoids
  TextPainter per item. This is Preview collection geometry, not a whole-card
  fixed aspect ratio. Text style helpers and implementation body remain internal.
- `HikariAdaptiveGrid.builder` owns one vertical viewport. `.sliver` borrows
  the caller's `CustomScrollView`; it accepts no independent controller. Required
  `itemExtentBuilder(context, itemWidth)` computes scaled row height. Default
  maximum width uses `HikariBreakpoints.posterGridMaxExtent`; gaps/padding use
  foundation spacing. Zero/unbounded local width **or positive width entirely
  consumed by horizontal padding** produces no collection and does not enumerate
  item keys/build widgets; resizing to usable width resumes lazy rendering.
- `HikariHorizontalShelf`: lazy horizontal viewport, optional header and external
  controller. `itemExtent` is card width; default height includes poster title
  and subtitle. Other families supply `itemHeightBuilder`. Native horizontal
  wheel/trackpad and directional keys scroll; Tab remains native traversal and
  vertical touch drag belongs to parent. Controller supplied by caller remains
  caller-owned; internally created controller is disposed with shelf. Directional
  keys respect RTL; reduced-motion mode jumps without scroll animation.
- Both collections accept `itemKeyBuilder` and remap live child state using
  stable keys. Key enumeration is O(n) per usable-width collection rebuild;
  widgets/providers remain viewport-lazy. Keys must be unique. No eager provider
  construction. This is a **known performance characteristic**, not a measured
  bottleneck: profile collection rebuild/reorder frequency and key-map cost on
  Redmi Note 9S (and Windows) at Phase 07 Task 07 with 5,000-item fixtures before
  changing the identity contract or adding a speculative cache.
- `HikariChapterRow` accepts strings, enabled, onTap and continuation status.
  Catalog's typed manga/novel mapper owns source identity, scanlator/date order
  and unreadability. Core never imports chapter domain classes. Remote detail
  pages and Catalog Detail use canonical HikariChapterRow via feature-owned
  chapter mapping after Phase 04/06. The old ChapterRow was retired in 04/08.

## Cache and identity

Feature chooses `MemoryImage` / `NetworkImage` and source resource. Existing
`SourceArtwork` and `ReadSourceArtwork` lifecycle remains unchanged. The
application byte cache namespace is `source-artwork-v1`, keyed by exact source
and item tuple; cache-first/offline reads and unavailable-plugin cached artwork
remain supported. No cache eviction/cancellation or parallel cache was added.
Flutter's decoded-image cache owns equal provider/resize-key reuse; it is not
the application byte cache. Different decode buckets may legitimately decode
again. Cache key equality alone is not proof of one decode.

`CountingArtworkProvider` instrumentation counts actual provider loads and codec
requests across rebuild/remount with a retained native cache entry. It does not
claim zero network requests for different providers, evicted cache entries,
Reader use, or unrelated image sizes.

## Replayable micro-benchmark

Pinned SDK: Flutter 3.47.5 / Dart 3.13.4, via FVM. From repository root:

```powershell
fvm flutter test test/core/ui/media/hikari_media_micro_benchmark_test.dart --reporter expanded
fvm flutter test test/core/ui/media test/core/ui/layout/hikari_adaptive_grid_test.dart test/core/ui/layout/hikari_horizontal_shelf_test.dart
fvm flutter run -t tool/ui_gallery/main.dart -d windows
```

Benchmark records elapsed host test time, initial/post-scroll builder calls,
provider loads and codec requests for 5000 synthetic cards. Repeat five times
under identical SDK, viewport, scaler and cache state; keep raw output. Host
widget timing is diagnostic only, not device frame timing. Gallery exposes
missing/corrupt/valid artwork, poster/landscape/progress/chapter states, local
width, RTL, 1x/2x/3x scaling and 5000-item stress mode.

Physical Android/Windows profile p95 frame time and peak memory: **NOT MEASURED**
unless a separately retained profile trace identifies device, build mode,
scenario, warm/cold cache and measurement window. Physical Redmi Note 9S profile
remains Phase 07's gate. Widget tests do not replace TalkBack, Narrator, touch,
trackpad or paired real-route Hero review.

## Migration boundary

Pilot B initially migrated Library grid and Catalog Search. Phase 04 then
migrated Home, Library list, Local Media, Catalog/Source Search, Catalog/native
Details and Continue Reading chrome. Reader canvases, Settings and Plugin
Manager remain separate Phase 05/06 ownership. Legacy nonvisual decode entrypoint
delegates to canonical policy until Phase 07 Task 01. No permanent compatibility
facade. See [G4 report](../../roadmap/UI_FOUNDATION_PHASE_04.md) and
[gallery samples](../GALLERY.md).
