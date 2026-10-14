# Phase 4.6 — Reader Image Decode & Memory Validation

## Baseline and scope

- Baseline: `Hikari-feat-cache-management(9).zip`.
- Manga `MangaReaderPage` already bounds decoded pages using `ResizeImage`
  with `fit`, a 2048 × 4096 ceiling, and `allowUpscaling: false`.
  Do not lower this cap without testing its effect on 4× pinch zoom and
  panel lettering. `MemoryImage` keys also depend on the byte-buffer identity;
  a new disk read can produce a new decode key. A separate decoded-image
  session cache is **not** introduced without evidence of repeated decoding.
- Novel/EPUB `NovelContentView` previously rendered resources with unconstrained
  `Image.memory`. Phase 4.6 bounds decoded illustrations by their available
  logical reader width × DPR (64-physical-pixel buckets, max width 2048) and
  max height 4096 using `ResizeImagePolicy.fit`. Aspect ratio is maintained,
  source bytes stay full-resolution and are still cached independently of
  decoded Flutter `ImageCache` entries. A zoomable illustration feature would
  need to revisit the decode ceiling.
- Resource refresh and retry still create fresh byte-buffer/image identities;
  do not key decoded images only by stable `SourceMediaRef`, as that would risk
  showing stale artwork after explicit refresh.

## Profile procedure (Redmi Note 9S)

1. Use `fvm flutter run --profile`. `HIKARI_CACHE_TRACE=true` is available
   only if the separate Phase 4.5 instrumentation patch is integrated; it is
   unnecessary for decoded-image profiling. Do not compare debug with profile.
2. In DevTools **Memory**, capture a baseline after settling on a text-only
   Novel chapter. Then read a chapter containing several large illustrations,
   navigate away, and observe decoded-image memory and GC behavior.
3. Repeat with the same chapters and viewport after applying Phase 4.6.
   Compare retained image memory and image decode activity, rather than only
   total Dart heap (decoded images may occupy external/native memory).
4. In **Performance**, compare raster p50/p95 and slow frames while scrolling
   illustrations. Note whether the slow frames overlap network reads, image
   decoding, or drawing; temporal overlap alone is not proof of causation.
5. Check Manga page changes, reload, next/previous, back navigation, and zoom
   at up to 4×. Do not add a Manga provider cache or modify the cap without
   reproducing a measurable decode-reuse bottleneck.
6. Verify Novel resource retry, chapter reload, repeated `<img>` references,
   and local EPUB illustrations. Confirm the full source bytes remain usable
   after a decoded-image entry is evicted.

## Safety criteria

- No new image/network fetch occurs solely from a theme or parent rebuild.
- No image distortion (especially tall illustrated pages).
- No stale resource shown after refresh or retry.
- No progress/continuation changes; disk namespace/budgets unchanged.
- A reduction in large-image decoded memory should not be presented as a
  measured improvement until validated in profile mode on hardware.

## Sources

- [Flutter Image memory usage](https://api.flutter.dev/flutter/widgets/Image-class.html)
- [Flutter `ResizeImagePolicy.fit`](https://api.flutter.dev/flutter/painting/ResizeImagePolicy.html)
- [Flutter image cache semantics](https://api.flutter.dev/flutter/painting/ImageCache-class.html)
- [Flutter oversized image diagnostics](https://docs.flutter.dev/tools/devtools/inspector#highlight-oversized-images)
