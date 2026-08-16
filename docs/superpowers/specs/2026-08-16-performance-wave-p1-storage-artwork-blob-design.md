# Performance Wave P1 Storage + Artwork + Blob Design

## Goal

Remove the highest-risk runtime performance problems found by the performance audit without changing user-visible behavior: blocking chapter filesystem work must not execute on Main, unrelated blob keys must not be serialized by one JVM monitor, artwork must request decode sizes derived from Compose constraints, and chapter blob handling must avoid redundant full-payload copies/hash work.

## Scope

Wave P1 owns four tightly related changes:

1. `AtomicFileChapterBlobStore`, `FileBlobInventory`, and `TransactionalPluginPackageStorage` move blocking filesystem work to an injected I/O coroutine context so callers remain dispatcher-agnostic.
2. Chapter blob coordination replaces the global `operationLock` monitor with coroutine-aware locking scoped to the narrowest safe unit. Store operations for the same blob key remain serialized; unrelated keys may proceed concurrently. Inventory mutation coordinates with store mutation without holding a blocking JVM monitor across suspend-facing I/O.
3. `HikariArtwork` supplies Coil with a constraints-aware size resolver while preserving the existing fallback, painter-state, cache keys, crossfade policy, content scale, and public component API.
4. `ChapterBlob` exposes size without copying, hashes owned input only once on normal construction, keeps defensive copies at public ownership boundaries, and exposes a read-only `InputStream` so storage/codec paths can stream/read payloads without repeated full-array copies. Atomic file encoding writes checksum/header and payload separately instead of concatenating another full-sized array.

## Constraints

- Preserve all Wave 1-4 behavior and UI output.
- No Room schema migration.
- Do not move dispatcher responsibility into Reader/UI callers; concrete blocking storage adapters own their execution context.
- Same-key read/write/delete operations must remain race-safe.
- A corrupt read must never delete a replacement blob committed after that read began.
- Public `ChapterBlob.bytes()` remains defensive so callers cannot mutate owned content.
- No catalog matching, Room-query, plugin-runtime caching/provisioning, chapter-pagination, or cache-eviction P2 work in this patch. Plugin package filesystem dispatching is included because it is part of the P1 blocking-I/O finding.
- Keep production artwork cache-key semantics stable unless correctness requires change.

## Storage dispatcher and locking

`AtomicFileChapterBlobStore` accepts a `CoroutineDispatcher` dependency with `Dispatchers.IO` as the Android production default and uses `withContext(ioDispatcher)` around concrete file operations. `FileBlobInventory` does the same for scan/delete, and `TransactionalPluginPackageStorage` owns `Dispatchers.IO` for package store/read/remove. Tests can inject deterministic dispatchers.

A lock coordinator owns coroutine `Mutex` instances per blob identity. Store `read`, `write`, and `delete` acquire the mutex for their key. This preserves the existing same-key corruption/write race guarantee while allowing different keys to make progress independently. The coordinator must remove idle locks when safe so the map does not grow without bound.

Inventory scans are read-only directory traversal and run on I/O without taking every blob-key lock. Inventory deletion resolves artifact ids and, for blob artifacts, uses the same blob-identity lock before deletion. Temporary artifacts are unique staging files and may be deleted directly on I/O. This removes the global monitor while keeping committed blob replacement/delete coordination safe.

## Blob allocation model

`ChapterBlob.fromBytes(bytes)` copies once into owned storage, computes SHA-256 once over that owned content, and stores the checksum. `ChapterBlob.verified(bytes, checksum)` likewise takes one ownership copy and validates that owned content once. `sizeBytes` reads `content.size` without exposing the array.

`ChapterBlob.inputStream()` returns a read-only stream over owned bytes without exposing the mutable array. Reader codec decode consumes that stream; write-admission/cache metadata uses `sizeBytes`. Atomic file storage writes the ASCII checksum and newline as small header writes, then copies the stream to the file output in bounded chunks. Decode validates the checksum text in-place and creates exactly one payload ownership copy instead of multiple `copyOfRange` arrays.

The checksum hex encoder uses a fixed hex table instead of `String.format` per byte.

## Artwork sizing

`rememberHikariArtwork` creates a `rememberConstraintsSizeResolver()` and attaches it to the `ImageRequest` and the displayed `Image` modifier according to Coil's Compose contract. The same resolver must be used for request sizing and layout constraints. Existing `HikariArtworkState` remains reusable by card and backdrop callers.

Because one state can be rendered in multiple places, the resolver is owned by the remembered artwork state and carried through to `ArtworkLayer`. The API remains source-compatible for normal callers.

## Error handling

Filesystem exceptions retain current propagation/best-effort behavior at existing call sites. Cancellation must propagate through `withContext` and mutex acquisition. Temporary write cleanup still runs in `finally`. Corrupt blobs remain quarantined/deleted only while holding the same-key mutex, preventing deletion of a concurrently committed replacement.

## Verification

Required focused gates:

- storage test proves blocking file callbacks execute on the injected I/O dispatcher rather than the caller dispatcher;
- plugin package storage test proves package filesystem work enters through the injected I/O dispatcher;
- storage test proves a blocked write for key A does not stop a write for key B;
- existing same-key corrupt-read/replacement race test stays green;
- blob tests prove `fromBytes`/`verified` retain defensive ownership and `sizeBytes` requires no public payload copy;
- design-system test/static contract proves constraints-aware Coil sizing is wired to both request and image modifier;
- existing performance lifecycle/Wave 4, package-boundary, source-layout, Room-schema and relevant unit tests remain green;
- `git diff --check` and patch apply-check must pass on the pristine baseline.

## Alternatives considered

### A. Caller-owned `Dispatchers.IO`

Rejected. It scatters correctness responsibility through Reader/download/plugin callers and allows future Main-thread regressions whenever the storage adapter is reused.

### B. One global coroutine `Mutex`

Safer than `synchronized` for coroutines but still serializes unrelated Reader/cache work and inventory activity. It fixes thread blocking without fixing contention.

### C. Per-key coroutine locking + adapter-owned I/O (chosen)

Provides the required same-key atomicity while allowing unrelated keys to proceed. It is the smallest change that addresses both Main-thread blocking and global-lock contention without redesigning storage metadata.
