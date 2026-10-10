# ADR-018: Local scan snapshot and presentation

Status: Accepted for the Local UI/UX pass (2026-10-10).

## Context

Local's Android SAF bridge already returns a selected tree's display name, but the
Flutter scan API discarded it. Local presentation displayed a flat media grid
without folder context and placeholder artwork only. Media identities (including
encoded CBZ and EPUB references), reader leases, and persisted library entries
must remain stable while polishing the UI.

## Decision

- Preserve `LocalMediaSource.scanSelectedRoot(): Future<List<Media>?>` for
  Source Search callers. Add `scanSelectedRootSnapshot()` as a **separate**
  typed UI entry point, with an immutable `LocalMediaScanResult`
  (display-only root name, unchanged media identities and optional preview refs).
  The shared value contract lives beside `domain/media/` types (not in a
  one-file `domain/local_media/` subtree). This is not a persisted domain
  entity; the architecture guard prohibits infrastructure importing application.
- Keep `Media` and `SourceMediaRef` unchanged. Do not persist root display names
  as document identities and do not introduce a mandatory folder structure.
- Derive Manga folder thumbnails only from **already scanned direct image
  children**. Prefer `cover.*`, otherwise the first natural-order page. Make
  preview refs a separate, validated `hikari-local-art` namespace; the native
  read limit is 8 MiB. The prefix distinguishes resource kinds; it does not
  attest provenance or grant SAF access. Invalid metadata must never fail a scan.
- Request artwork only for visible SliverGrid items through the established
  `SourceArtwork` + `MediaPoster` composition and the existing
  `ArtworkSource`/`ReadSourceArtwork` path, rather than a local-only stateful
  poster wrapper. Leverage existing disk cache and
  bounded `Image.memory(cacheWidth)`. Do not eagerly materialize CBZ/EPUB
  archives to render thumbnails; those formats keep the fallback poster until
  safe cached extraction is designed independently.
- Represent revoked/absent SAF grants explicitly as the `access` platform
  error code, separate from transient provider/storage errors. When a refresh
  fails, keep the previous (possibly stale) results and root label, with a
  warning; when the root is changed, never show items from the previous root.
- Keep filtering in presentation state. Do not change classifier, reader
  navigation or persistence logic. Center and width-cap Local content using
  the existing responsive design token, preserving lazy grid construction.
- Do not show fictional scan percentages: the existing SAF traversal exposes
  no total work count. A busy indicator and stale-result banner are accurate.

## References

- Android Storage Access Framework and directory restrictions:
  https://developer.android.com/training/data-storage/shared/documents-files
- Flutter lazy SliverGrid creation and child lifecycle:
  https://api.flutter.dev/flutter/widgets/SliverGrid-class.html
- Flutter responsive layout constraints:
  https://docs.flutter.dev/ui/layout/constraints
- Android reduced-size thumbnail decoding guidance:
  https://developer.android.com/topic/performance/graphics/load-bitmap
- Mihon local folder/cover conventions (reference, not an app contract):
  https://mihon.app/docs/guides/local-source/
  https://mihon.app/docs/guides/local-source/advanced

## Regression verification

Run `fvm dart format .`, `fvm flutter analyze`, `fvm flutter test`, and
`fvm flutter build apk --debug`. Specifically verify:
- no root, picker cancel, changed root and in-flight generation guards;
- transient scan failures versus revoked tree permissions;
- source search's original list API, unchanged CBZ/EPUB reader identities;
- screen widths including compact + landscape at 1.6x text scale;
- preview reads confined to displayed images and no archive materialization;
- real Android permission revocation/provider loading behavior.

Native video `content://` playback and CBZ/EPUB thumbnail extraction are not
changed by this ADR and require a separate validation pass.
