# Discover Performance Recovery Design

**Date:** 2026-09-05

## Goal

Make the first Discover visit, manual refresh, vertical scrolling, and scroll-to-top interaction responsive on representative physical devices without weakening canonical catalog correctness or changing Discover's semantic ranking.

## Scope

This change covers five connected performance boundaries:

1. Discover refresh completion and foreground canonical convergence;
2. canonical settlement scheduling and UI publication cadence;
3. stable composition identity for incrementally settling Discover sections;
4. top-level backdrop capture used by Discover, Home, and Library;
5. reliable scroll-to-top behavior shared by Discover, Home, Search, and Library.

It also updates deterministic benchmark coverage so the measured Discover path contains real local artwork and explicitly exercises scroll-to-top.

## Non-goals

- No change to reconciliation, fusion, ranking, or source-preference policy.
- No raw provider card may become a second presentation authority.
- No Room schema or migration change.
- No pagination or redesign of the visible Discover sections.
- No network-dependent performance benchmark.
- No new user setting for blur, animation, or image loading.

## Current Problems

### Refresh latency

Discover passes all 19 visible semantic Story IDs to `CatalogRefreshService`. The service commits provider results, then waits while `CanonicalEngineOrchestrator` processes every immediate evidence change sequentially. The refresh indicator therefore represents provider fetch, Room commit, reconciliation, fusion, and canonical persistence as one long operation.

### First-content latency

After Home rows become visible, `DiscoverCanonicalBootstrapPipeline` settles missing canonical Stories one at a time. Each result is emitted independently. A cold canonical cache therefore pays serial Room/fusion work while the screen remains pending or repeatedly republishes growing prefixes.

### Composition churn

Latest row keys include every Story ID currently in the row. Progressive settlement changes the row key while it grows, recreating the row and its artwork painters. The Popular pager also performs page correction whenever its growing ID list changes.

### Frame cost

All top-level destinations capture a full-screen backdrop for the floating navigation. Discover simultaneously decodes/crossfades artwork and draws a large hero image. Existing physical-device Reader evidence shows that the same backdrop primitive materially increases frame CPU and overrun rate.

### Scroll-to-top reliability

Screens launch an untracked long-distance `animateScrollToItem(0)`. A competing scroll mutation can cancel it. The action disappears below item index 3, so a cancellation at index 1 or 2 removes the recovery control even though the list is not at the exact top. Existing tests only assert that the action disappears.

## Design

### 1. Separate refresh completion from canonical convergence

Discover refresh will use a deferred foreground policy when calling `CatalogRefreshService`: provider fetches and Home commits remain awaited, while all resulting canonical work is represented by the existing durable outbox/work queue and scheduled for background draining.

The refresh indicator completes after provider results have been committed and reported. Cached content remains visible throughout. On a clean install, Discover still waits for an authoritative Home emission before classifying content, but it no longer keeps the refresh operation open while the canonical engine converges the entire visible set.

This behavior is Discover-specific. Search and explicit correctness-sensitive operations keep their existing bounded foreground convergence contracts.

### 2. Settle visible Stories with bounded concurrency

`DiscoverCanonicalBootstrapPipeline` will preserve its seed read and canonical-only output, then settle missing Story IDs with a small fixed concurrency limit. Each Story remains isolated: cancellation propagates, while a failure becomes that Story's typed settlement failure.

Results will be published in bounded batches rather than one UI emission per Story. Publication must preserve deterministic Story ordering and the stable-prefix rule used by each semantic section. The final emission contains a terminal settlement for every expected Story ID.

The concurrency limit is four. This is large enough to remove serial latency while keeping Room write contention bounded on lower-end devices.

### 3. Stabilize section composition

Latest rows will use position-based outer keys and Story-based child keys. Growing a partially settled row will preserve the existing cards and painters instead of replacing the entire row.

The Popular pager will retain the visible Story when possible, reset only when content type changes, and correct the page only when the current Story disappears. Appending or filling neighboring pages must not call `scrollToPage`.

Artwork remains canonical, size-aware, memory/disk cached, and shared between the Discover hero cover and backdrop. Crossfade behavior is unchanged in this patch; benchmark evidence will determine any future image-transition policy.

### 4. Remove full-screen top-level backdrop capture

The app shell will keep one stable `HikariBackdropHost` composition but set top-level capture off. Floating navigation will use the existing token-driven fallback glass surface when no backdrop token is present, matching the already accepted Reader strategy.

This applies consistently to Discover, Home, and Library. Focused routes already avoid shell capture because they do not show floating navigation. No component API or visual token is removed.

### 5. Use a shared bounded scroll-to-top operation

The design system will own shared suspend helpers for `LazyListState` and `LazyGridState`.

- If the current index is far from the top, jump directly to a small staging index.
- Animate only the short remaining distance to item 0.
- Use one tracked Job per screen; a repeated request replaces the previous request.
- Tests inject the state and assert `firstVisibleItemIndex == 0` and `firstVisibleItemScrollOffset == 0`.
- User input may still cancel an active animation; because the animation starts near the top, cancellation cannot strand the screen deep in the collection. The action remains derived from exact top state rather than an item-index threshold.

The shared behavior is adopted by Discover, Home, Search, and both Library display modes.

## Data Flow

```text
Pull refresh
  -> parallel provider fetch
  -> sequential deterministic provider commits
  -> durable outbox/work representation
  -> refresh report completes
  -> Home observation publishes committed snapshots
  -> Discover bounded settlement (max concurrency 4)
  -> coalesced ordered settlement emissions
  -> stable semantic section projection
  -> UI updates without replacing existing row identities
```

The background canonical worker remains the recovery authority if the process dies after commit or if foreground settlement is interrupted.

## Error and Cancellation Semantics

- Provider failures continue to appear in `DiscoverRefreshReport` and do not discard cached content.
- Coroutine cancellation is always rethrown.
- One Story settlement failure does not cancel other Story settlements.
- Canonical settlement never falls back to a raw provider card.
- Background scheduling failure leaves durable outbox/work state recoverable.
- Scroll-to-top replacement cancels only the previous scroll-to-top Job, not unrelated screen work.

## Testing

### Unit and Compose tests

- Discover refresh reports completion without foreground canonical processing.
- Deferred refresh still creates durable engine work for every changed Story.
- Settlement uses at most four concurrent rebuilds and publishes fewer emissions than the number of missing Stories.
- Settlement preserves deterministic final ordering and per-Story failures.
- Latest row growth retains stable outer identity.
- Popular pager does not reset when pages are appended.
- Discover, Home, Search, and Library reach exact index 0/offset 0.
- Repeated scroll-to-top requests do not leave competing jobs.
- Top-level shell supplies no live backdrop token to floating navigation.

### Macrobenchmark

- Benchmark browse fixtures use deterministic local artwork rather than null covers.
- `discoverScroll` waits for ready Discover content before measuring.
- A `discoverBackToTop` journey scrolls down, invokes the action, and verifies the first Discover content node is visible.
- Existing `homeDiscoverWarm`, Library scroll, and backdrop comparison journeys remain buildable for historical comparison.

Physical-device FrameTiming/Perfetto data is the final authority for jank improvement. Unit tests prove behavioral correctness, not frame smoothness.

## Acceptance Criteria

1. Manual Discover refresh no longer waits for synchronous canonical convergence of 19 visible Stories.
2. Missing visible canonical Stories settle with a maximum concurrency of four and deterministic final results.
3. Progressive settlement does not recreate already-present Latest cards or reset a valid Popular page.
4. Discover, Home, and Library no longer capture a live full-screen backdrop for floating navigation.
5. Scroll-to-top reaches exact item 0/offset 0 in Discover, Home, Search, Library list, and Library grid tests.
6. Discover performance fixtures include local artwork and a dedicated back-to-top Macrobenchmark journey.
7. Focused tests, repository verification, debug assembly, and benchmark assembly pass; physical-device numbers are reported separately when a device is available.
