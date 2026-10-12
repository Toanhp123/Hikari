# ADR-020: Bounded on-demand AniList catalog metadata snapshots

Status: Proposed for review (2026-10-12).

## Context

Catalog discovery and details previously required AniList GraphQL even after the
same series had been opened before. Library, Progress and continuation persistence
are durable user-owned data and intentionally do not serve as AniList query
snapshots. Search results should not fill long-lived disk storage. AniList rate
limits requests and prohibits mass collection or hoarding of its data; snapshots
must be small, on-demand and disposable.

## Decision

1. Keep `CatalogProvider` stable. `CatalogSnapshotProvider` is an optional
   provider capability used by the application use cases to surface persisted
   snapshots and to request a forced network refresh. ViewModels do not access
   `ByteCache`, SQLite or GraphQL responses. Non-snapshot providers preserve
   existing behavior.
2. Reuse the existing `ByteCache` index and LRU storage with an additional
   `anilist-metadata` namespace and a **12 MiB maximum**. No new dependencies,
   Drift user tables, or independent cleanup mechanism. The application
   composition root owns one explicit registration of namespace IDs, Settings
   labels and byte budgets; it supplies budgets to `DiskByteCache` and the
   allowlist/labels to `CacheManagement`. Stable namespace IDs stay defined by
   their consumers (including `AniListMetadataCache`), not in the generic
   `ByteCache` interface. Clear Cache removes AniList snapshots but never
   touches Library/Progress or local archives.
3. Persist only the results of user-initiated, successful discovery and detail
   requests. Store a versioned JSON response and UTC timestamp. Parse all cached
   responses through the same existing normalization code as network responses.
   Reject malformed records and partial GraphQL failures; do not cache empty
   fallback responses caused by parsing errors. Increment cache version when
   the requested GraphQL shape or its parsing contract changes.
4. Discovery snapshots: 30-minute TTL with the current year/season in the key.
   Detail snapshots: 24-hour TTL keyed by AniList media ID. Search results:
   **RAM-only**, 5-minute TTL with a maximum of 40 queries per provider instance.
   No background crawling, pagination sweeps or long-term media archive.
5. On initial screen load, show available fresh metadata immediately with no
   request. When stale, show it immediately and attempt network revalidation.
   On network error, preserve the prior metadata and display refresh failure.
   Pull-to-refresh, Retry and the Detail Refresh button always request network
   even for fresh snapshots, without erasing displayed data while in flight.
6. Deduplicate overlapping GraphQL requests by query intent (discovery, detail
   ID, search query+type). Do not persist GraphQL responses containing errors.
   HTTP 429 is an error rather than a cacheable empty response; do not retry in
   a tight loop. Memory results and UI are revision-guarded to prevent late
   screen updates. If the clock moves backwards, consider entries stale.

## Tradeoffs and limitations

- This is **application-managed GraphQL snapshot caching**, not HTTP caching.
  AniList POST responses do not gain HTTP semantics such as ETag validation,
  Cache-Control or conditional requests merely because we persist their JSON.
  TTLs here are Hikari policy defaults, not server-provided freshness values.
- Season-scoped discovery intentionally does not reuse a previous season's
  snapshot as current discovery. During the first offline launch of a new
  season, fallback may be unavailable until an online request succeeds.
- Search RAM cache is intentionally omitted from Settings disk usage because it
  is bounded and dies with the provider. Images remain managed by their own
  loading/cache mechanisms; this phase only caches GraphQL metadata, including
  image URLs, **not downloaded AniList cover bytes**.
- Ongoing requests may repopulate the cache after Clear, as documented by
  ADR-019. Clear and cache writes remain coordinated by `DiskByteCache`.
- This feature does not modify persisted remote series/chapter data or the
  content-source identity rules in ADR-011.

## Review checklist

- Repeat load uses snapshot; explicit refresh bypasses it.
- Stale Home/Detail stays visible on HTTP/GraphQL errors and can retry.
- 429/partial/malformed responses cannot poison prior snapshots.
- No cross-ID cache hits; locale and seasonal query parameters are correctly
  represented in cache keys.
- Concurrent identical requests are deduplicated; late UI responses are ignored.
- Settings counts and clears only allowlisted disk namespaces, including the
  new metadata namespace, without affecting user-owned state.
- `fvm dart format`, `fvm flutter analyze`, `fvm flutter test -r expanded`
  and APK build must be run on a Flutter-enabled host before merge.

## Research references

- Flutter offline-first architecture / repository as single source of truth:
  https://docs.flutter.dev/app-architecture/design-patterns/offline-first
- Apollo fetch policies (`cache-first`, `cache-and-network`, `network-only`):
  https://www.apollographql.com/docs/react/data/queries
- RFC 9111 (HTTP caching constraints; do not claim Hikari implements it):
  https://www.rfc-editor.org/rfc/rfc9111.html
- RFC 5861 (stale-while-revalidate concept):
  https://www.rfc-editor.org/rfc/rfc5861.html
- AniList rate limits, including temporary degraded limit and `Retry-After`:
  https://anilist.gitbook.io/anilist-apiv2-docs/docs/guide/rate-limiting
- AniList API Terms of Use (no hoarding/mass collection):
  https://docs.anilist.co/guide/terms-of-use
