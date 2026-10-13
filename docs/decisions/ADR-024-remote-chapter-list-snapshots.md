# ADR-024: Evictable remote chapter-list snapshots for Reader-first Continue

Status: Implemented in code; Flutter analysis, tests, and device verification pending.

## Context

Reader-first Continue (ADR-023) opens the exact saved chapter without waiting for
`loadDetails()`. It starts with a provisional one-chapter sequence, then requests
remote details to enable Previous/Next. Once the process restarts offline,
SQLite continuation and cached chapter content can still exist, but the complete
series chapter order is unavailable. Manga **page-list** cache (ADR-022) is a
separate concern; it stores the page references *inside* one chapter.

## Decision

- `ReadRemoteChapterList` stores a versioned, bounded representation of remote
  Manga and Novel **chapter sequences in canonical reading order**. It shares
  the existing `DiskByteCache`, with its own `remote-chapter-list-v1`
  namespace (8 MiB). Existing cache namespace budgets are unchanged.
- A single snapshot is at most 2 MiB and 50,000 chapters; larger responses are
  usable normally but not persisted. Cache keys include media type, source ID,
  and series item ID. Source-owned chapter refs, titles, and navigation-relevant
  metadata are retained. No raw source URL or content is logged.
- Normal `MangaSeriesOpenTarget` / `NovelSeriesOpenTarget.loadDetails()` always
  fetch *fresh* details from the source, validate the chapter sequence, and then
  best-effort publish a snapshot. This also warms the cache on Detail visits.
  LNReader pagination must finish before `loadDetails()` can return, so failed
  intermediate pages do not publish partial snapshots. Source-private Mihon
  continuation state remains in SQLite; snapshot references are not replacements.
- Continue opens the saved chapter as before. After chapter opening it reads a
  cached sequence if available and containing that chapter, then independently
  refreshes from the source. Reader reconciliation never replaces the active
  target or progress. Failed source refresh leaves the cached sequence in use.
  The source lease stays acquired until both cache reconciliation and remote
  refresh settle, including route dismissal.
- Each source load has a request token so a late older response cannot overwrite
  a newer request's snapshot. Invalid, cross-source, duplicate, mismatched,
  corrupt, oversized, or incompatible snapshots are cache misses.
- Catalog Chapters additionally consumes an atomic validated snapshot containing
  canonical chapters, `fetchedAt`, and structural-order declaration. Its injected
  clock considers ages in `[0, 1 hour)` fresh; future timestamps are stale.
  Fresh hits skip source details, stale hits display before revalidation, and
  explicit refresh always attempts the source. This is a Catalog-only policy:
  Continue still revalidates on every entry and snapshots have no eviction TTL.
- Schema v1 adds optional boolean `reverseSourceOrder`; canonical chapter storage
  and existing `readManga` / `readNovel` callers are unchanged. Missing fields in
  legacy snapshots default to canonical display order until refreshed. Source
  structural order cannot be recovered from optional numbers or dates. Invalid
  field types are cache misses. Chapters and timestamp are decoded/validated
  together in the application layer, never in widgets.
- Sequence validation also runs when no cache is composed. DiskByteCache enqueues
  writes synchronously in invocation order; request-token checking has no async
  gap before enqueue, so delayed writes cannot replace a later accepted write.
- User `Library`, `Progress`, series continuation, and provider-private state
  are unchanged. Snapshots are disposable and do not guarantee downloaded pages
  or text. Source/extension availability remains required to open a chapter.

## Not in scope

This does not add a second cache manager, migrate the user database, persist
source metadata for offline Detail, implement Extension Revision Contract
(Phase 3B), or claim that cache is a durable download. There is no mandatory TTL:
Continue opportunistically revalidates on each entry; explicit Detail refresh
always contacts its source. Any independent request deduplication must be
justified by observed network duplication before being added.

## Verification

`test/remote_chapter_list_cache_test.dart` covers metadata round-trip,
canonical order, cross-type/source isolation, malformed snapshots, invalid
source results, stale request completions, disk-cache restart, and offline
Manga/Novel Continue sequence. Also rerun reader-first, continuation, and
remote reader suites. Manual test on an Android device should read a series
online, restart offline, verify Previous/Next and chapter content availability,
then reconnect and verify changed chapter order without progress reset.

References:

- https://docs.flutter.dev/app-architecture/design-patterns/offline-first
- https://developer.android.com/topic/architecture/data-layer/offline-first
- ADR-022 and ADR-023
