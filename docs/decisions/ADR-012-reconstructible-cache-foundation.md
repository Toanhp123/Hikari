# ADR-012: Reconstructible byte-cache foundation

Status: Accepted

## Decision

Hikari stores reconstructible source-owned bytes in an independent, versioned cache subtree under the operating system's application cache directory. An independent Drift index records explicit namespaces, hashes of composite namespace/key identities, immutable blob digests, byte sizes, and LRU access times. Blob files use opaque names and are published only after a complete flushed temporary write and same-directory rename; metadata publishes afterward. Startup recovery removes abandoned temporary files, unindexed blobs, and metadata whose blob has disappeared before applying LRU eviction. A corrupt/unopenable cache index is discarded and recreated once; if recreation also fails, the cache fails open for that process. Cache loss means refetch, never user-data loss.

The pure technical `core/cache` layer declares the generic `ByteCache` contract. Application workflows own their logical namespaces and stable resource keys; the current source-artwork and remote-manga-page consumers both use the JSON tuple `[sourceId.value, itemId]`. `AppDependencies` composes independent production byte budgets (64 MiB for source artwork and 128 MiB for remote manga pages); `DiskByteCache` remains provider/product agnostic and only enforces the namespace budgets it is given. Cache format/namespace version remains explicit. The infrastructure stores only hashes, not raw identifiers, titles, URLs, provider payloads, or bytes in SQLite. Namespace budgets are independent and byte-based. Oversize values are skipped. Runtime index/cleanup failures disable writes for the running instance; no unbounded orphan accumulation or background scheduler.

`AppDependencies` owns its default cache. Injected caches remain caller-owned unless ownership transfers explicitly. Source artwork remains optional: registered `ArtworkSource` capability is checked before cache access. Remote manga page caching is applied only to the remote chapter workflow; direct/local folder and CBZ page reads keep their existing source-owned path. Cache failures fail open; provider failures propagate. Same-reference concurrent calls share one load. An explicit remote-page reload bypasses the cache read and replaces the entry only after a successful source read so decode-error retry cannot be trapped behind cached bad bytes.

## Boundaries

This cache is not durable user state (Library, Progress, or Mihon continuation), private plugin continuation/preferences storage, downloaded content, opened-media archive materialization, Flutter's decoded in-memory `ImageCache`, or native/extension HTTP response caching. Downloads and cache entries do not share paths: finalized exact opaque filenames prevent readers from observing partial download materialization. No title-derived filenames. Retention is byte LRU, not freshness or TTL.

Catalog response caching, manga page-list caching and prefetch, LNReader resources, downloads, UI/settings, generic freshness, and automatic background maintenance remain out of scope. The settings placeholder stays hidden.

Remote manga page-byte caching is the second cache consumer and uses its own namespace, documented in [REMOTE_MANGA](../architecture/REMOTE_MANGA.md). Its 128 MiB initial budget and boundaries are starting production policy, not a permanent product promise. Local folder/CBZ page bytes stay outside the cache.

Cache writes currently remain awaited on the read path, providing deterministic immediate-repeat reuse and same-page request coalescing; profiling must establish serialization/throughput impact before adding prefetch. Awaiting writes is not required for index/blob consistency, which is handled by publishing the blob before its index entry.

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
