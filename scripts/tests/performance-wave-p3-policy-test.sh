#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
runtime="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt"
provisioner="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisioner.kt"
engine="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/AndroidxJavaScriptEngine.kt"
script_builder="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/InvocationScriptBuilder.kt"
plugin_dao="$root/storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginStateDao.kt"
plugin_store="$root/storage/room/src/main/kotlin/app/openstory/storage/room/plugins/RoomPluginStateStore.kt"
cache_repo="$root/downloads/src/main/kotlin/app/openstory/downloads/cache/CacheRepository.kt"
cache_service="$root/downloads/src/main/kotlin/app/openstory/downloads/cache/CacheService.kt"
download_dao="$root/storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt"
download_repo="$root/storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt"
registry="$root/library/src/main/kotlin/app/openstory/library/content/PluginContentSourceRegistry.kt"

fail() { echo "Performance Wave P3 policy violation: $1" >&2; exit 1; }

[[ -f "$script_builder" ]] || fail "InvocationScriptBuilder source is missing"
grep -q 'provisioningSucceeded' "$provisioner" || fail "successful bundled provisioning is not memoized"
grep -q 'provisionInFlight' "$provisioner" || fail "bundled provisioning is not single-flight"
grep -q 'inFlight.await()' "$provisioner" || fail "concurrent provisioning callers do not share the in-flight result"
grep -q 'NonCancellable' "$provisioner" || fail "provisioning single-flight cleanup is cancellation-vulnerable"
grep -q 'PluginPackageIdentity' "$runtime" || fail "runtime has no immutable active-package cache key"
grep -q 'ConcurrentHashMap<PluginId, CachedPluginPackage>' "$runtime" || fail "runtime package cache is not O(1) by plugin id"
! grep -q 'loadedPackages.keys' "$runtime" || fail "runtime package invalidation scans all cached plugin identities"
grep -q 'stateVersions()' "$plugin_dao" || fail "plugin state DAO lacks scoped bulk version query"
grep -q 'INNER JOIN plugin_state' "$plugin_dao" || fail "plugin state version snapshot is not scoped through active state rows"
grep -q 'state.active_version' "$plugin_dao" || fail "plugin state snapshot does not include active versions"
grep -q 'state.previous_version' "$plugin_dao" || fail "plugin state snapshot does not include previous versions"
grep -q 'dao.snapshot()' "$plugin_store" || fail "plugin state all() still resolves state/version rows outside one bulk snapshot"
grep -q 'InvocationScriptBuilder' "$engine" || fail "JavaScript engine does not use cached invocation preparation"
grep -q 'maxCachedSources' "$script_builder" || fail "JavaScript source-literal cache is not bounded"
grep -q 'sourceLiterals' "$script_builder" || fail "JavaScript source literals are not cached"
grep -q 'cachedSource ?: sourceEncoder' "$script_builder" || fail "JavaScript source encoding still runs inside the cache monitor"
grep -q 'operationSegments' "$script_builder" || fail "plugin operation paths are rebuilt on every invocation"
grep -q 'quotaSnapshot' "$cache_repo" || fail "cache repository lacks quota snapshot contract"
grep -q 'val automaticEntries = entries().filter' "$cache_repo" || fail "cache quota fallback no longer materializes entries once"
! grep -q 'automaticCacheUsageBytes' "$cache_repo" || fail "Room-only cache query leaked into the public cache repository contract"
grep -q 'quotaSnapshot(quotaBytes)' "$cache_service" || fail "cache service does not use transactional quota fast path"
grep -q 'automaticCacheUsageBytes' "$download_dao" || fail "Room cache DAO lacks scalar SUM query"
grep -q 'automaticCacheEntriesByLru' "$download_dao" || fail "Room cache DAO lacks SQL-ordered LRU query"
grep -q 'deleteAutomaticCache' "$download_dao" || fail "Room cache eviction still needs read-before-delete"
grep -q 'database.withTransaction' "$download_repo" || fail "Room quota snapshot is not transactionally consistent"
grep -q 'if (usage > quotaBytes)' "$download_repo" || fail "Room cache candidates are materialized even when usage is within quota"
grep -q 'dao.deleteAutomaticCache' "$download_repo" || fail "Room cache repository does not use direct automatic-cache deletion"

runtime_line=$(grep -n 'runtime.enabled(PluginService.CONTENT)' "$registry" | cut -d: -f1 | head -1)
lock_line=$(grep -n 'cacheMutex.withLock' "$registry" | cut -d: -f1 | head -1)
[[ -n "$runtime_line" && -n "$lock_line" && "$runtime_line" -lt "$lock_line" ]] || \
    fail "content source registry still holds cache mutex across runtime.enabled()"
grep -q 'requestSequence' "$registry" || fail "registry runtime lookups are not generation-ordered"
grep -q 'latestAppliedRequest' "$registry" || fail "out-of-order runtime completions can overwrite newer source cache state"

echo "Performance Wave P3 policy verified."
