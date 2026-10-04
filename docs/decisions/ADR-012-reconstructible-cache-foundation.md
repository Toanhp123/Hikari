# ADR-012: Reconstructible byte-cache foundation

Status: Accepted

## Decision

Hikari stores reconstructible source-owned artwork bytes in an independent, versioned cache subtree under the operating system's application cache directory. An independent Drift index records explicit namespaces, hashes of composite namespace/key identities, immutable blob digests, byte sizes, and LRU access times. Blob files use opaque names and are published only after a complete flushed temporary write and same-directory rename; metadata publishes afterward. Startup recovery removes abandoned temporary and unindexed blobs. Cache loss means refetch, never user-data loss.

The pure technical `core/cache` layer declares the generic `ByteCache` contract; the application consumes it and maps a source artwork reference to a stable JSON tuple `[sourceId.value, itemId]`. Cache format/namespace version remains explicit. The infrastructure stores only hashes, not raw identifiers, titles, URLs, provider payloads, or bytes in SQLite. The source artwork namespace has a 64 MiB payload-byte LRU budget. Namespace budgets are independent. Oversize values are skipped. Failed index/cleanup disables writes for the running instance; no unbounded orphan accumulation or background scheduler.

`AppDependencies` owns its default cache. Injected caches remain caller-owned unless ownership transfers explicitly. Source artwork remains optional: registered `ArtworkSource` capability is checked before cache access. Cache failures fail open; provider failures propagate. Same-reference concurrent calls share one load.

## Boundaries

This cache is not durable user state (Library, Progress, or Mihon continuation), private plugin continuation/preferences storage, downloaded content, opened-media archive materialization, Flutter's decoded in-memory `ImageCache`, or native/extension HTTP response caching. Downloads and cache entries do not share paths: finalized exact opaque filenames prevent readers from observing partial download materialization. No title-derived filenames. Retention is byte LRU, not freshness or TTL.

Catalog, manga pages/prefetch, LNReader resources, downloads, UI/settings, generic freshness, and automatic background maintenance remain out of scope. The settings placeholder stays hidden.

## Alternatives

- `flutter_cache_manager` provides HTTP-aware cache machinery and cross-platform filesystem/storage behavior, but its object-count/stale policies do not enforce Hikari's namespace byte budgets and duplicate policy not needed by source-owned byte reads.
- Filesystem-only LRU requires directory-wide scans and timestamp policy; indexed Drift accounting avoids recurring scans.
- Reusing `UserDatabase` would couple expendable bytes and index cleanup to durable state and migrations.

## Evidence

- [Drift `drift_flutter` native options](https://pub.dev/documentation/drift_flutter/latest/drift_flutter/DriftNativeOptions-class.html) supports explicit database paths. [path_provider application cache](https://pub.dev/documentation/path_provider/latest/path_provider/getApplicationCacheDirectory.html) maps to OS cache locations across Hikari targets; those locations may be purged by the OS.
- Flutter [ImageCache](https://api.flutter.dev/flutter/painting/ImageCache-class.html) stores decoded in-memory images, not persistent source bytes.
- Mihon `ChapterCache` uses bounded disk LRU and opaque cache keys; its cover cache remains a separate concern: [ChapterCache.kt](https://github.com/mihonapp/mihon/blob/4c88f02646aa1a358611e5b3b37ef7a62909b8d9/app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt), [CoverCache.kt](https://github.com/mihonapp/mihon/blob/4c88f02646aa1a358611e5b3b37ef7a62909b8d9/app/src/main/java/eu/kanade/tachiyomi/data/cache/CoverCache.kt).
- LNReader coalesces chapter fetch promises and removes failed/blank cached entries: [useChapter.ts](https://github.com/LNReader/LNReader/blob/ae8d055689b5c68dcc51117254dcba56018fbc7d/src/screens/reader/hooks/useChapter.ts).
- Suwayomi issues describe thumbnail growth without downloads ([#1237](https://github.com/Suwayomi/Suwayomi-Server/issues/1237)) and reports a temporary-file/live-read race ([#2289](https://github.com/Suwayomi/Suwayomi-Server/issues/2289)); these are issue reports, not claims about current implementation. This decision avoids shared download paths and relies only on finalized opaque cache blobs.
