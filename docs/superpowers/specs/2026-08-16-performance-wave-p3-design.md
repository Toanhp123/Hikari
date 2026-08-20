# Performance Wave P3 Design

## Goal

Reduce repeated plugin-runtime filesystem/parsing work, JavaScript invocation string-building work, cache-quota full scans, and registry lock contention without changing plugin protocol behavior, security boundaries, Room schema, or public feature APIs.

## Plugin runtime

- `BundledPluginProvisioner` becomes process-idempotent after a fully successful provisioning pass. Failed passes remain retryable; overlapping callers share one `CompletableDeferred` single-flight result. Publication/cleanup runs non-cancellably so owner cancellation cannot strand the in-flight marker.
- `DefaultPluginRuntime` caches successfully loaded plugin package data by immutable active-package identity (`pluginId`, version, package location, SHA-256). It still reads current `PluginStateStore` state for every invocation, so disable/version changes take effect immediately. Failed package reads/manifest decodes are not cached.
- `RoomPluginStateStore.all()` bulk-loads plugin versions once and resolves active/previous versions in memory, removing per-plugin `findVersion()` N+1 reads.

## JavaScript invocation

- Keep one fresh isolate per invocation to preserve the current isolation/cleanup contract.
- Extract a bounded `InvocationScriptBuilder` that caches the JSON-escaped plugin source literal and operation path for repeated calls. The final invocation string still embeds the current input and uses the same bridge/bootstrap and `eval` semantics.
- The cache is bounded to avoid retaining an unbounded number of plugin scripts.

## Cache quota

- `CacheRepository` gains one `quotaSnapshot(quotaBytes)` optimization contract. Its compatibility default materializes `entries()` once, so non-Room implementations do not regress to duplicate scans.
- Room overrides `quotaSnapshot()` transactionally: a scalar `SUM(size_bytes)` handles the common under-quota path and a SQL-ordered automatic-cache query runs only when eviction is required.
- `CacheService.enforceQuota()` avoids loading/sorting all metadata when current usage fits. Eviction behavior remains pinned/current/progress-safe.
- Room eviction deletes each automatic-cache key directly rather than `find` then `delete`, eliminating the read-before-delete N+1.

## Registry contention

- `PluginContentSourceRegistry.enabled()` performs suspend `runtime.enabled()` outside its cache mutex. A monotonically increasing request generation prevents an older suspended lookup from overwriting cache state produced by a newer completion.

## Verification

- Counter-based tests verify one successful bundled provisioning pass per process, shared failed in-flight provisioning with retry, one package load/manifest parse per immutable active package, bulk plugin-state resolution, under-quota cache usage without entry-list scan, single-materialization fallback quota snapshots, and registry mutex/generation behavior.
- Existing plugin runtime, cache, storage Room, library, and project policy tests remain green.
- Add a Performance Wave P3 static policy and checkpoint; the existing verification glob auto-discovers the new policy.
