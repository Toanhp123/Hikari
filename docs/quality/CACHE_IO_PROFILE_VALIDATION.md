# Cache I/O profile validation — Phase 4.5

`DiskByteCache` deliberately serializes blob reads, index changes, eviction,
Clear Cache and close. This protects index/blob consistency and keeps the
existing race/close contracts. Phase 4.5 adds **opt-in instrumentation**, not
parallel I/O or a new scheduler.

## Capture on Android

Use a physical device (preferably the Redmi Note 9S) and profile mode, not
debug mode. Launch from the project root:

```powershell
fvm flutter run --profile --dart-define=HIKARI_CACHE_TRACE=true
```

Open the Flutter DevTools **Performance** view and record while repeating these
scenarios separately:

1. Cold cache: open a remote Manga chapter and read 20 pages.
2. Warm cache: reopen the same chapter and navigate 20 pages.
3. Background competition: reach the last five pages, trigger next-chapter
   warming, then open another page or chapter immediately.
4. Novel: open a chapter with illustrations while the next-chapter resource
   prefetch is running.
5. Settings: Clear Cache while content is loading, then repeat a read.

Export the DevTools trace for comparison. Filter by `Hikari.Cache.*` async
events. Each event spans **queue admission through operation completion** and
contains these numeric arguments (microseconds unless otherwise noted):

| Field | Definition |
| --- | --- |
| `queueWaitUs` | Time between enqueue and beginning of serialized operation |
| `executionUs` | Time spent running the operation, including awaits |
| `hashUs` | SHA-256 digest time for blob bytes inside read/write |
| `trimUs` | Time in namespace size query and LRU eviction after a write |
| `bytes` | Returned hit bytes, or submitted write bytes; zero for other calls |
| `hit` | Read hit (`true`) or miss (`false`); absent for non-reads |
| `failed` | Internal error or a propagated exception, not an ordinary miss |

Event names identify only operation types (`read`, `write`, `clearNamespaces`,
`clear`, `remove`, `sizes`, `sizeBytes`, `close`). No source reference, title,
URL, cache key, media content or user data is emitted. Trace data stays in
DevTools; there is no local log or network upload. The feature is **off unless
explicitly enabled** with the dart-define. Do not ship profile tracing enabled
in a release build.

A write `bytes` argument means **submitted** bytes; it does not distinguish a
skipped oversized write, reuse of an existing blob, or committed bytes. For a
read with a corrupt blob, `hit=false` reflects fail-open cache behavior.
`hashUs` and `trimUs` are subsets of `executionUs`, not additional costs to
sum. Initialization, recovery, Drift queries, file open and flush times are
included in `executionUs` but not individually attributed. A cold first read
may therefore be dominated by initialization; always compare cold and warm
runs separately.

Timeline/Stopwatch instrumentation has nonzero overhead. Do not infer
production release latency directly from an instrumented run. Repeat each
scenario, calculate read queue-wait p50/p95 and hit ratio, and correlate with
frame jank in DevTools before changing storage concurrency. A single slow
request is not sufficient evidence to allow parallel blob reads.

## Follow-up decision gate

Keep `_serialized()` unchanged if foreground cache hits are consistently fast
and queue wait does not materially delay page display. If queue wait dominates
foreground reads under actual load, benchmark a scoped immutable-blob-read
prototype against the current serialized baseline. Before adopting it, prove
correctness for concurrent read/eviction, read/Clear Cache, index repair,
write replacement and close. Do not introduce unbounded parallel reads or
alter cache namespace budgets on profiling guesses.

## Verification

```powershell
fvm dart format lib/infrastructure/cache/disk_byte_cache.dart test/disk_byte_cache_test.dart
fvm flutter analyze
fvm flutter test test/disk_byte_cache_test.dart
fvm flutter test test/cache_management_test.dart
```

Relevant upstream guidance: [Flutter DevTools Performance](https://docs.flutter.dev/tools/devtools/performance), [Dart TimelineTask](https://api.dart.dev/dart-developer/TimelineTask-class.html), [Flutter concurrency and isolates](https://docs.flutter.dev/perf/isolates).
