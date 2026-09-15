# Core Artwork Module Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize `:core:artwork` into responsibility-bearing packages, split mixed-responsibility files, and migrate all repository consumers without changing Step 3 Task 10 artwork behavior.

**Architecture:** Keep the existing `:core:artwork` module boundary and dependency graph unchanged. Move stable types into `request`, `policy`, `remote`, `preflight`, `cache`, `pipeline`, and `runtime` packages while leaving only cross-cutting `ArtworkFailure` and `ArtworkLimits` at the root. Consumer imports migrate atomically; no compatibility aliases or behavior redesigns are allowed.

**Tech Stack:** Kotlin, Android, Coil 3, coroutines, Gradle 9.5.0, Bash repository gates.

**Spec:** `docs/superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md`

## Global Constraints

- Production behavior accepted through Step 3 Task 10 is frozen.
- Step 3 Task 11 remains not started.
- No module addition/removal or dependency-edge change.
- No compatibility aliases for old `app.openstory.artwork.*` package locations.
- Keep all public type names, constructor contracts, limits, failure reasons, redirect/media/container behavior, cache/coalescing semantics, runtime lifecycle, and cancellation behavior unchanged.
- Target package tree is the user-approved `request`, `policy`, `remote`, `preflight`, `cache`, `pipeline`, and `runtime` layout with only `ArtworkFailure.kt` and `ArtworkLimits.kt` at root.

---

### Task 1: Ratchet the approved artwork layout

**Files:**
- Create: `scripts/tests/v2-core-artwork-layout-test.sh`
- Modify: `scripts/verification-common.sh`
- Modify: `scripts/tests/v2-verification-entrypoints-test.sh`

**Interfaces:**
- Consumes: canonical cleanup policy in `docs/project/file-package-ownership-policy.md`.
- Produces: a current-static regression that fails until the approved `:core:artwork` package tree exists and old mixed files are gone.

- [x] **Step 1: Write the failing structural regression**

The test must require the approved responsibility-bearing source paths, reject the old mixed files (`ArtworkCaches.kt`, flat `ArtworkInFlight.kt`, flat `ArtworkPolicy.kt`, flat `ArtworkRuntime.kt`, flat `ArtworkTransport.kt`), verify package declarations for every target file, and keep the root package restricted to the two cross-cutting primitives without freezing the total future file count.

- [x] **Step 2: Run the regression and verify RED**

Run: `bash scripts/tests/v2-core-artwork-layout-test.sh`

Expected: FAIL because the target package tree does not exist yet.

- [x] **Step 3: Classify the regression as current-static**

Add the script to `run_repository_static_contract_tests()` and to `current_static_tests` in `v2-verification-entrypoints-test.sh` so verification remains fail-closed.

### Task 2: Split and move `:core:artwork` production source

**Files:**
- Keep root: `core/artwork/src/main/kotlin/app/openstory/artwork/ArtworkFailure.kt`
- Keep root: `core/artwork/src/main/kotlin/app/openstory/artwork/ArtworkLimits.kt`
- Create/move under `request/`: `ArtworkRequest.kt`, `ArtworkRequestIdentity.kt`
- Create/move under `policy/`: `ArtworkPolicy.kt`, `ArtworkPolicyResolver.kt`
- Create/move under `remote/`: `ArtworkRemotePolicy.kt`, `ArtworkTransport.kt`
- Create/move under `preflight/`: `ArtworkImagePreflight.kt`, `ArtworkImageContainer.kt`
- Create/move under `cache/`: `ArtworkEncodedCache.kt`, `ArtworkDecodedMemoryCache.kt`, `ArtworkMemoryPressure.kt`
- Create/move under `pipeline/`: `ArtworkInFlight.kt`, `ArtworkPipelineCoordinator.kt`
- Create/move under `runtime/`: `ArtworkRuntime.kt`, `ArtworkFetcher.kt`
- Delete old mixed source files once declarations have moved.

**Interfaces:**
- Consumes: existing public declarations and behavior from the flat `app.openstory.artwork` package.
- Produces: the same declarations under responsibility-bearing packages, with no compatibility aliases.

- [x] **Step 1: Split cross-cutting failures/limits and cache responsibilities**
- [x] **Step 2: Move request/policy/remote/preflight responsibilities**
- [x] **Step 3: Split pipeline primitive from coordinator**
- [x] **Step 4: Split runtime lifecycle from fetcher implementation**
- [x] **Step 5: Run `bash scripts/tests/v2-core-artwork-layout-test.sh` and verify GREEN**

### Task 3: Migrate all consumers and tests atomically

**Files:**
- Modify every repository source importing moved `app.openstory.artwork.*` types, including `:app`, `:feature:catalog`, benchmark sources, `:core:artwork` tests, and `:feature:catalog` Android tests.
- Split: `core/artwork/src/test/kotlin/app/openstory/artwork/ArtworkPolicyTest.kt`
- Create: `core/artwork/src/test/kotlin/app/openstory/artwork/remote/ArtworkRemotePolicyTest.kt`

**Interfaces:**
- Consumes: new package paths from Task 2.
- Produces: a repository source tree with zero imports of removed flat artwork package locations.

- [x] **Step 1: Search all old artwork imports and migrate them to new packages**
- [x] **Step 2: Move/split core tests to mirror production ownership**
- [x] **Step 3: Search again and require zero stale imports or references to deleted mixed files**

### Task 4: Verify and record the cleanup checkpoint

**Files:**
- Modify: `docs/internal/checkpoints/hikari-repository-module-cleanup.md`
- Modify: `docs/implementation/current-roadmap.md` only if needed to name the completed `:core:artwork` boundary without authorizing the next module.

**Interfaces:**
- Consumes: focused compile/test/static evidence from Tasks 1–3.
- Produces: exact evidence and remaining debt, with Task 11 still frozen and no automatic authorization for another module.

- [x] **Step 1: Run focused static gates**

Run:
- `bash scripts/tests/v2-core-artwork-layout-test.sh`
- `bash scripts/tests/v2-source-layout-policy-test.sh`
- `bash scripts/tests/v2-verification-entrypoints-test.sh`
- `bash scripts/verify-source-layout.sh`
- `bash scripts/structural-review-report.sh`

- [x] **Step 2: Run focused Gradle verification when the local distribution is available**

Run:
`./gradlew :core:artwork:testDebugUnitTest :core:artwork:compileDebugKotlin :feature:catalog:compileDebugKotlin :feature:catalog:compileDebugAndroidTestKotlin :app:compileDebugKotlin --no-daemon`

User-owned host result on 2026-09-15: **PASS** — `BUILD SUCCESSFUL in 1m 2s`; `105 actionable tasks: 11 executed, 94 up-to-date`; configuration-cache entry stored.

- [x] **Step 3: Self-review changed paths and contracts**

Confirm no change to `settings.gradle.kts`, `config/architecture/module-boundaries.json`, build dependency declarations, numeric artwork limits, failure enum members, or production behavior contracts.

- [x] **Step 4: Update the checkpoint and stop at the module boundary**
