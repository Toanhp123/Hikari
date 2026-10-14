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

## Phase 5.3: bounded session reuse and discovery isolation

Accepted extension: source-owned session reuse and a single separate Android
scan worker replace duplicate search traversals and reader-executor head-of-line
blocking. Explicit full refresh remains authority; no persistent index or reader
page cache. Detailed ownership/cancellation/limits have one canonical home in
[SOURCES](../architecture/SOURCES.md#local-saf-discovery-phase-53).

Why no persistent index: SAF requires size and modification columns but their
values may be null, and neither they nor ContentObserver establish recursive
versions or a complete journal. `CancellationSignal` is best effort; AOSP's
DocumentsProvider receives a signal but child-query dispatch calls the legacy
child overload without it. Local cooperative checks guarantee obsolete results
are not published, not that a blocked provider returns by a deadline.

Deterministic query accounting, **not device benchmarks**: for D distinct
directories, each fresh traversal uses D child queries plus one root-description
query. Two sequential consumers previously used 2D children + 2 descriptions;
now they use D children + 2 descriptions. A later explicit refresh adds D + 1.
A 1,501-directory deep fixture confirms one child query per directory; exact
50,000-entry fixture includes synthetic root and performs no eager byte reads.

Artwork selection needs only the minimum, so use linear comparisons rather than
sorting every page. Classification cost depends on recognized-media distribution;
keep existing compute and natural comparator instead of assuming isolate overhead
or sorting is universally negligible. Synthetic desktop comparison counts are not
Android/SAF speedup evidence; device latency still needs profile-mode capture.

Built-in native instrumentation fixture uses real MethodChannel codec and fake
ContentResolver/MatrixCursor without user grants. Compile and run only isolated
fixture application (no new test dependency):

```powershell
# From android/, use installed Gradle / project wrapper when present.
gradle -PlocalMediaTestApp=true :app:assembleDebug :app:assembleDebugAndroidTest
# Install ONLY io.github.toanhp123.hikari.localmediatest and its .test APK.
adb shell am instrument -w -e class io.github.toanhp123.hikari.localmedia.LocalMediaChannelTest io.github.toanhp123.hikari.localmediatest.test/android.test.InstrumentationTestRunner
```

Default production app ID and release build remain unchanged. Do not install
fixture builds over `io.github.toanhp123.hikari`, launch its activity, change its
selected tree or claim Dart mocks prove native scheduling. Native fixture covers
blocked scan versus read, cancellation/late token, close/queued/rejected result
completion, cursor close, null/loading/malformed/type/limit errors. Compilation
alone does not prove device/provider behavior; integration owner records actual
instrumentation result and remaining real-provider/platform gaps.

### Evidence provenance

- Android DocumentsProvider child query:
  https://developer.android.com/reference/android/provider/DocumentsProvider#queryChildDocuments(java.lang.String,%20java.lang.String[],%20java.lang.String)
- Required columns with nullable values:
  https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_LAST_MODIFIED
  https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_SIZE
- AOSP signal/child dispatch (moving upstream reference, not device guarantee):
  https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/provider/DocumentsProvider.java
- Mihon local root lists use UniFile, not a persistent generic SAF subtree index:
  https://github.com/mihonapp/mihon/blob/446f6df82c390ab760802c2fce3b677bce3f31c1/source-local/src/main/kotlin/tachiyomi/source/local/LocalSource.kt#L85-L152
  https://github.com/mihonapp/mihon/blob/446f6df82c390ab760802c2fce3b677bce3f31c1/source-local/src/main/kotlin/tachiyomi/source/local/io/LocalSourceFileSystem.kt#L14-L29
- Pinned UniFile batches ID + name; separate MIME checks can still query per file.
  Hikari already projects ID + name + MIME. Do not copy suppressed errors or
  absent loading validation:
  https://github.com/mihon/unifile/blob/08f224c8f9d6498d6c4d0d4021c0d41e2ad8863c/library/src/main/java/com/hippo/unifile/DocumentsContractApi21.java#L112-L141
- Kotatsu's local File approach is not generic SAF freshness/cancellation proof:
  https://github.com/KotatsuApp/Kotatsu/tree/06a0b5829b8d5f214d60d5011d66ec1ccbd5630e
- Flutter compute explains isolate mechanics, not measured Hikari speedups:
  https://api.flutter.dev/flutter/foundation/compute.html

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
