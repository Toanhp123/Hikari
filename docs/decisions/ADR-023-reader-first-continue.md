# ADR-023: Reader-first Continue with independent chapter-list reconciliation

Status: Accepted (pending Flutter analyzer, tests and on-device verification)

## Context

Continue previously required a remote `loadDetails()` call before opening the
reader, even when the saved chapter's content/page-list bytes were cached. A
previous **network-first then fallback** proposal would still wait for a network
timeout before using local content. It was not applied to the baseline of this
ADR. Chapter identity and progress already persist separately from source
chapter-list metadata, and `ReadMangaPageList` (ADR-022) and
`ReadNovelChapterContent` can read from disk first.

## Decision

- Continue opens a route for the **exact persisted chapter reference** as soon
  as the source capability is resolved. The chapter-opening task runs *inside*
  that route, using existing cache-first use cases. The route displays loading,
  error and Retry even if no cache/network is available. Library poster taps and
  detail chapter actions keep their existing semantics.
- Loading the full series chapter list is **not** a precondition for reading.
  Once the current chapter has been opened, a separate best-effort request
  validates the source-owned chapter sequence and enriches Previous/Next and
  the displayed chapter title. The refresh never changes the selected chapter,
  its opened content, saved position or `ProgressSession`. Source responses
  missing the active chapter, containing duplicates or foreign references are
  rejected, not used to silently jump to another chapter.
- A reader starts with a one-chapter provisional sequence. ViewModel
  `reconcileSequence` updates only the surrounding navigation metadata while
  preserving the current target identity and widget key. Failure or disposal
  leaves the current reader unchanged. A newly discovered next Novel chapter
  can be prefetched after sequence reconciliation.
- An in-flight chapter open keeps the source lease until it settles, even if
  the route was dismissed. An in-flight chapter-list request is also settled
  before lease release. Late reader construction disposes unused ViewModels.
- This does not create a second persistence system, add database migrations,
  or alter Library/Progress, cache keys, budgets or archive leases.

## Limits

- A fresh app install or LRU eviction can mean there is no cached page list or
  novel content. The reader route then waits for source data or displays an
  error; partial image bytes are not guaranteed to exist.
- A stale source chapter list can fail revalidation. In this case the saved
  chapter remains accessible, but Previous/Next stay unavailable until a valid
  sequence is received. Chapter identity is not inferred from a similar title.
- `SourceId` availability still requires an installed/registered extension;
  offline connectivity alone does not imply source unavailability.
- This is *best-effort cache reading*, not durable offline downloads.

## Verification

Tests cover route-before-content loading, reader-local Retry, navigating away
while opening, safe disposal, offline persisted page list reopening, sequence
reconciliation and progress/target identity preservation. The user's FVM SDK
must run Flutter analyzer, tests and debug build and the device must verify
background updates while reading.

## References

- Flutter offline-first reading strategies: https://docs.flutter.dev/app-architecture/design-patterns/offline-first
- Android offline-first data layer: https://developer.android.com/topic/architecture/data-layer/offline-first
- Mihon repository (history and reading flows): https://github.com/mihonapp/mihon
- ADR-022: `ADR-022-manga-page-list-cache-and-refresh.md`
