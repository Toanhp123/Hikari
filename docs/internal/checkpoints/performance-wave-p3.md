# Performance Wave P3 Checkpoint

Date: 2026-08-16
Status: **VERIFIED ON DEVELOPER MACHINE**

## Scope

Performance Wave P3 targets repeated plugin-runtime setup, suspend-lock contention, JavaScript invocation preparation, and automatic-cache quota work that still scaled with invocation/write count after P1/P2.

### Plugin provisioning and loaded-package cache

- `BundledPluginProvisioner` memoizes a fully successful bundled provisioning pass for the process.
- Overlapping callers share one `CompletableDeferred` single-flight result, including a failed/cancelled in-flight pass. A caller arriving after a failed pass may retry; failures are never permanently memoized.
- Single-flight publication/cleanup runs in `NonCancellable` so owner cancellation cannot leave a stale in-flight marker.
- `DefaultPluginRuntime` caches decoded manifest + decoded `main.js` by immutable active-package identity `(pluginId, version, packageLocation, sha256)`.
- Package-load failures are not cached. A successful new immutable identity replaces stale cached identities for the same plugin.
- Runtime enabled/disabled state is still read from `PluginStateStore` for each public runtime call; the package cache does not cache enablement decisions.

### Plugin-state and content-source lookup

- `RoomPluginStateStore.all()` uses one transactional DAO snapshot instead of one `findVersion()` query per plugin.
- The bulk version query joins `plugin_state` and reads only versions currently referenced as active/previous; historical plugin-version rows are not scanned into memory.
- `PluginContentSourceRegistry` performs suspend `runtime.enabled()` lookup outside its cache mutex.
- Request generation prevents an older runtime completion from evicting/replacing cache state produced by a newer completed request.

### JavaScript invocation preparation

- `AndroidxJavaScriptEngine` retains the existing sandbox/isolate lifecycle and still creates a fresh bounded isolate for each invocation.
- `InvocationScriptBuilder` precomputes encoded operation path segments and keeps a bounded LRU cache of escaped immutable plugin source literals.
- Per-invocation input remains embedded per call; only source/operation preparation is reused.
- Expensive source encoding happens outside the cache monitor; the monitor only protects bounded cache lookup/publication.

### Automatic-cache quota and eviction

- `CacheRepository` exposes one `quotaSnapshot()` performance contract with a one-materialization compatibility default for non-Room implementations.
- `RoomDownloadRepository.quotaSnapshot()` reads usage and conditional eviction candidates in one Room transaction.
- Under quota, Room executes the scalar `SUM(size_bytes)` query without materializing/sorting all cache rows and `CacheService` skips an empty eviction transaction.
- Over quota, Room returns automatic-cache candidates already ordered by LRU and `CacheEvictionPolicy.planOrdered()` walks them once without another sort.
- Room automatic-cache eviction deletes directly by blob key instead of `find()` followed by entity delete.
- No Room schema change is required.

## Required developer-machine verification

Run focused JVM tests:

```bash
./gradlew \
  :plugins:runtime:testDebugUnitTest \
  :library:testDebugUnitTest \
  :downloads:testDebugUnitTest \
  :storage:room:testDebugUnitTest \
  --stacktrace
```

Run the modified Room instrumentation contracts on a connected device/emulator:

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.plugins.RoomPluginStateStoreTest,app.openstory.storage.room.downloads.RoomDownloadRepositoryTest \
  --stacktrace
```

Then run the repository verification entry point:

```bash
./scripts/verify.sh
```

Expected checkpoint lines include:

- `Performance lifecycle policy verified.`
- `Performance Wave 4 policy verified.`
- `Performance Wave P1 policy verified.`
- `Performance Wave P2 policy verified.`
- `Performance Wave P3 policy verified.`
- `Room schema export remained stable during verification.`

P3 does not change Compose rendering, so a Roborazzi comparison is not required specifically for this wave.

## Sandbox verification performed while producing the patch

The Gradle wrapper distribution is not cached in the sandbox and outbound DNS cannot resolve `services.gradle.org`; full Gradle verification therefore remains a developer-machine gate.

Offline verification used the actual P3 production source where possible and passed:

- bundled provisioning success memoization, overlapping failed-pass single-flight sharing, retry-after-failure, and retry after owner cancellation;
- loaded plugin package manifest/script cache reuse, concurrent single package loading (one manifest + one script read), immutable-identity invalidation, and failure-not-cached behavior;
- JavaScript invocation source-literal reuse and bounded eviction; old-vs-new invocation source was byte-for-byte equivalent across 72 source/operation/input combinations;
- content-source registry out-of-order completion protection and no mutex held across a suspended runtime lookup;
- automatic-cache under-quota scalar fast path, over-quota ordered-candidate path, one-materialization fallback, legacy eviction-result compatibility across 10,000 randomized cases, and empty-eviction skip;
- modified Room plugin-state and download sources compiled against Room/domain API stubs;
- P1/P2/P3/lifecycle/Wave 4 performance policies, package boundaries, source layout, structural suppressions, current architecture, and Room schema-stability checks passed individually.

Before marking P3 verified, use the developer-machine commands above and address any compiler, Detekt, lint, or instrumentation findings they surface.

## Developer-machine verification result

Developer-machine verification completed on 2026-08-16: focused JVM tests, modified Room instrumentation, Detekt, and `./scripts/verify.sh` all passed; Room schema remained stable at versions 1..6.
