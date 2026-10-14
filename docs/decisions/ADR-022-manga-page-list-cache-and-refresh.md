# ADR-022: Manga page-list snapshots and explicit reconciliation

Status: Accepted (Phase 3A, pending runtime verification)

## Context

A page-byte disk cache alone cannot reopen a remote chapter offline after restart:
`source.pages(chapter)` provides the references required to address individual
cached image bytes. Previously the page list existed only in a reader session or
one speculative prefetch slot. Reload page refreshed the bytes but not a changed
page order, count or URL. Neither reading progress nor Library must become a
cache entry or be erased by cache maintenance.

## Decision

- `ReadMangaPageList` owns a versioned, bounded, reconstructible page-list JSON
  snapshot keyed by stable `[sourceId, chapter.itemId]`. It uses the existing
  `ByteCache`, a separate `manga-page-list-v1` namespace, and an 8 MiB LRU budget
  declared once in `AppDependencies` with the Settings label.
- Cache-first reuses nonempty valid page lists across sessions, including when a
  source cannot answer offline. A cache miss calls `source.pages`; valid network
  results are stored best-effort. Empty page lists are not reused from disk, so
  an empty speculative response cannot poison subsequent opens. Source
  ownership, schema version and nonempty IDs are checked on both input and
  decode. Local SAF sources are never persisted as remote page-list snapshots.
- `OpenMangaChapter` and next-chapter speculative prefetch use the same
  application reader when wired by the composition root. Speculative warming
  remains bounded and can hand off its in-flight list to foreground navigation.
- **Refresh page list** explicitly fetches a new source list without publishing
  it. The UI reconciles the *current* reference (or clamps current index when
  that reference changed), stages the replacement bytes without overwriting the
  existing image cache, verifies decoding, waits for pending progress saves,
  and only then commits the validated image and new page-list snapshot. Failed list/image fetches preserve
  the original visible list/image/position. A generation guard ignores late
  responses after disposal or a superseding reader operation.
- A refreshed page list invalidates queued prefetch and refreshes its snapshot;
  prefetch begins again from the new target only after the refreshed image is
  displayed. Progress continues to use `PagePosition` and the existing
  continuation identity; no database schema change is required.

## Deliberate limitations

Page-list cache and image-byte cache are **independently LRU-evicted**; therefore
this is best-effort partial offline reading, **not guaranteed chapter downloads**.
An offline reader can only display images whose bytes still exist locally. A
signed/expiring URL may also fail if its image bytes are missing. Opening a
series may require remote chapter-list metadata outside this ADR. No page-list
TTL, source revision tracking, extension migration, forced automatic full
chapter downloads, or Local SAF page-list persistence is introduced.

A complete offline download needs a separately owned, non-evictable chapter
manifest and all associated bytes (including a clear lifetime/removal policy).

## Verification

New tests cover disk reopen and Clear Cache, malformed snapshots, staged
refresh/commit, invalid source references, local-snapshot exclusion, changed
page order/length, staged image-byte persistence, network/decode refresh
failure, disposal race, and ViewModel
handoff. Flutter analyzer/tests/build must run in the user's SDK environment.

## References

- [Android offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Flutter offline-first support](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- [Android app-specific temporary cache](https://developer.android.com/training/data-storage/app-specific)
- [Mihon upstream source tree](https://github.com/mihonapp/mihon)
- [ADR-012](ADR-012-reconstructible-cache-foundation.md)
- [ADR-021](ADR-021-remote-reading-cache-freshness.md)
