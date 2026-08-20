# Performance Wave P3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove repeated runtime/package/cache work identified by the performance audit while preserving current behavior and schemas.

**Architecture:** Add process-scoped memoization only where identity is immutable, keep current state reads authoritative, use SQL scalar/ordered queries for cache quota, and shorten coroutine mutex critical sections. Keep JavaScript isolate lifecycle unchanged and optimize only invocation-source preparation.

**Tech Stack:** Kotlin, coroutines, Room, AndroidX JavaScriptEngine, kotlinx.serialization, existing shell verification policies.

## Global Constraints

- Do not change Room schema versions.
- Do not weaken plugin capability/network/security validation.
- Do not reuse JavaScript isolates across invocations.
- Do not cache failed plugin package loads or failed provisioning passes.
- Preserve current plugin operation outputs and cache eviction semantics.

---

### Task 1: Plugin runtime package/provisioning cache

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisioner.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisionerTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`

**Interfaces:**
- Consumes: existing `PluginStateStore`, `PluginPackageStorage`, `PluginOperationRunner`.
- Produces: process-idempotent successful provisioning and active-package-keyed loaded package reuse.

- [x] Add tests proving two successful `ensureProvisioned()` calls load bundled packages once and a failed pass is retried.
- [x] Add tests proving repeated runtime invocation of the same active package reads `manifest.json`/`main.js` once, while a version change triggers a new load.
- [x] Run focused tests and observe RED.
- [x] Implement provisioning mutex/success memoization and loaded-package single-flight cache.
- [x] Run focused tests and observe GREEN.

### Task 2: Plugin state bulk loading and registry contention

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginStateDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/RoomPluginStateStore.kt`
- Modify: `library/src/main/kotlin/app/openstory/library/content/PluginContentSourceRegistry.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/plugins/RoomPluginStateStoreTest.kt`
- Test: `library/src/test/kotlin/app/openstory/library/content/ContentSourceRegistryTest.kt`

**Interfaces:**
- Produces: transactional `PluginStateDao.snapshot()` with scoped `stateVersions()` and generation-guarded registry cache updates.

- [x] Add/extend tests for bulk state resolution and concurrent registry lookup while `runtime.enabled()` is suspended.
- [x] Run focused tests and observe RED where applicable.
- [x] Implement one versions query for `all()` and move runtime call outside registry mutex.
- [x] Run focused/static tests and observe GREEN.

### Task 3: JavaScript invocation preparation

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/AndroidxJavaScriptEngine.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/InvocationScriptBuilder.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/execution/InvocationScriptBuilderTest.kt`

**Interfaces:**
- Produces: bounded `InvocationScriptBuilder.build(source, operation, input)` with unchanged invocation semantics.

- [x] Add tests proving repeated source encoding is reused, operation paths are stable, inputs remain per-call, and cache capacity is bounded.
- [x] Observe RED because the builder does not exist.
- [x] Extract builder and inject/use it from `AndroidxJavaScriptEngine` without changing isolate lifecycle.
- [x] Observe GREEN in focused tests/compile harness.

### Task 4: Cache quota fast path and direct eviction

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheService.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheEvictionPolicy.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/cache/CacheServiceTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`

**Interfaces:**
- Produces: `CacheRepository.quotaSnapshot(quotaBytes)` with a one-materialization compatibility default; Room implements the scalar SUM + conditional SQL-ordered LRU fast path internally.

- [x] Add test proving under-quota enforcement calls usage only and never requests entry list.
- [x] Add test proving over-quota LRU/protection behavior remains identical.
- [x] Observe RED.
- [x] Implement repository fast path, Room SUM/LRU queries, and direct automatic-cache delete.
- [x] Observe GREEN.

### Task 5: P3 policy/checkpoint and final verification

**Files:**
- Create: `scripts/tests/performance-wave-p3-policy-test.sh`
- Create: `docs/internal/checkpoints/performance-wave-p3.md`

**Interfaces:**
- Produces: static guardrails preventing regression to repeated provisioning/package reads, full quota scans, Room plugin-state N+1, and mutex-held suspend runtime calls.

- [x] Write policy checks against the pre-P3 tree and observe RED.
- [x] Complete policy (auto-discovered by the existing `scripts/tests/*.sh` verification loop) and observe GREEN.
- [x] Run focused compile/runtime harnesses, P1/P2/P3/lifecycle policies, package/source/architecture/schema gates, and `git diff --check`.
- [x] Export a patch from the verified P2 baseline and apply-check it on a pristine P2 tree.
