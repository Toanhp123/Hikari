# ADR-019: User-controlled reconstructible cache maintenance

Status: Proposed for review (2026-10-12).

## Context

ADR-012 established `DiskByteCache` as a separate, reconstructible disk cache
with a private Drift index and per-namespace byte budgets. Its `clear` and
`sizeBytes` methods were not exposed to production Settings, and they swallowed
failures or returned zero when the index could not be accessed. `SettingsPage`
had a dormant clear callback but no production wiring. An in-flight reader can
be accessing an archive materialized by `ArchiveCopyPool`, while another reader
is writing to the remote cache.

The user needs a reliable size report and clear action that **never erases**
Library, Progress, continuation, plugin settings or active local media.

## Decision

1. Add an optional `CacheMaintenance` interface separate from `ByteCache`.
   Read/write-only fake caches need not implement management. `DiskByteCache`
   implements `sizes(namespaces)` and `clearNamespaces(namespaces)` using one
   `_serialized` queue operation each, and **propagates errors**. Do not route
   user-visible success through legacy fail-open `clear`/`sizeBytes` helpers.
2. Application's `CacheManagement` is an explicit allowlist of four namespaces:
   source artwork (including local thumbnails), remote manga pages, remote
   novel chapter content and remote novel resources. Reports sum indexed blob
   payload sizes. Do not pretend this is all app storage or exact on-disk bytes:
   SQLite journal/index and momentary orphan/temp files are not included.
3. `AppDependencies` exposes management only if its current injected/default
   cache supports the optional capability. Its ownership/dispose behavior is
   unchanged. Settings loads usage on entry, displays total and category
   breakdown, allows retry on a failed stat, and confirms clear. After clear,
   request a new usage snapshot. Failure or partial deletion must not be
   reported as success. User reads and writes can populate entries **after**
   clear completes; a post-clear nonzero size does not imply a clear failure.
4. Restrict deletion to opaque blob entries in the explicitly named namespaces
   in the `hikari/cache-v1` index. The clear operation never recursively deletes
   `cacheDir` or the app-owned root, never opens `UserDatabase` or touches
   persisted SAF URI grants. It does **not** call `ArchiveCopyPool.close`,
   release held archive owners, or remove source-owned local originals.
5. Do not clear Flutter's decoded `ImageCache`, HTTP/OkHttp cache, download
   files, local temp archive materializations or cached catalog HTTP responses
   in this phase. The UI explicitly labels this as Hikari's reading/artwork
   disk cache. Future additional cache stores need their own scoped management
   and dedicated testing; do not assume the four-namespace total is global.

## Concurrency and failure guarantees

- The `DiskByteCache` queue ensures that already-enqueued reads/writes complete
  before clear, and later-enqueued reads/writes execute afterwards. There is no
  between-namespace interleaving within one explicit clear. The user may still
  be reading bytes already returned to a reader widget; those are immutable
  byte arrays, not live filesystem handles. Background network activity can
  repopulate a cleared cache in subsequent requests.
- Missing blobs remain recoverable through cache reads and startup reconciliation.
  A deletion/index failure stops the operation, disables new cache admissions
  for the instance, and propagates the failure. Already-deleted entries stay
  deleted; *clear is not a database transaction or filesystem rollback*.
  Cached reads can still fail open through the existing reading workflow.
- `ArchiveCopyPool` is a separate source-owned lease system: no part of the new
  management capability scans or clears its files. Local SAF originals are
  external documents selected by the user, not part of disk cache ownership.

## Verification

Automated tests cover category accounting, namespace isolation, serialized
clear after in-progress write, surfaced filesystem failure, preservation of
Library/Progress, a held local archive lease, Settings confirmation/cancel,
post-clear refresh, stale usage response and failure UI. On device, verify
clear during a Manga/Novel page load, a local CBZ/EPUB session, disk-full,
reboot with OS-purged cache, and a long-running extension prefetch.

Do not claim tests have passed until Flutter tooling runs them. Run:

```sh
fvm dart format lib test
fvm flutter analyze
fvm flutter test -r expanded
fvm flutter build apk --debug
```

## References

- Android app-specific cache: OS may clear it, apps must manage it themselves:
  https://developer.android.com/training/data-storage/app-specific
- Android SAF URI ownership and persistable grants:
  https://developer.android.com/training/data-storage/shared/documents-files
- Flutter `ImageCache.clear`: live image references are not forcibly freed and
  in-flight images can repopulate the cache:
  https://api.flutter.dev/flutter/painting/ImageCache/clear.html
- Flutter `ImageCache.evict`: `includeLive=false` is preferable for memory
  pressure so active images are not unnecessarily re-decoded:
  https://api.flutter.dev/flutter/painting/ImageCache/evict.html
- `flutter_cache_manager` exposes `emptyCache` separately from `dispose`:
  https://pub.dev/documentation/flutter_cache_manager/latest/flutter_cache_manager/CacheManager-class.html
- OkHttp: `evictAll` prunes a live cache, while `delete` discards its files; its
  store has its own single-owner lifecycle and must not be removed by an
  unrelated filesystem sweep:
  https://github.com/square/okhttp/blob/master/docs/features/caching.md
- Existing Hikari cache/lease design: ADR-012, ADR-018, and
  `infrastructure/local_media/archive_copy_pool.dart`.

## Follow-up

ADR-020 subsequently extends the explicit allowlist with on-demand AniList
metadata snapshots. The four-namespace scope above describes the original
Phase 1 decision and is preserved as historical context.
