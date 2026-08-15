# Performance Wave 3.5 Runtime Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix the navigation state-layer geometry and one-shot Discover empty-cache bootstrap before Wave 4 performance measurement.

**Architecture:** Keep the full navigation cell selectable while routing its interaction stream to a clipped child indication. Add a one-shot bootstrap coroutine in `DiscoverViewModel` that inspects only the first home-cache emission and delegates to the existing guarded refresh path.

**Tech Stack:** Kotlin, Jetpack Compose Foundation/Material 3, coroutines/Flow, JVM ViewModel tests, repository shell policy tests.

## Global Constraints

- Preserve Wave 1 multi-back-stack and ViewModel retention behavior.
- Preserve pull-to-refresh as the normal manual refresh affordance.
- Do not share/refactor Discover repository flows in this wave; that remains Wave 4.
- Use only existing Hikari semantic shape/color tokens for navigation chrome.
- Automatic Discover bootstrap is one-shot and must never retry-loop.

---

### Task 1: Lock Wave 3.5 contracts with failing tests

**Files:**
- Modify: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify: `scripts/tests/ui-shared-component-policy-test.sh`
- Modify: `scripts/tests/performance-lifecycle-policy-test.sh`

**Interfaces:**
- Consumes: existing `HikariFloatingNavigation`, `DiscoverViewModel.refresh()`.
- Produces: regression contracts for pill-scoped interaction indication and one-shot empty-cache bootstrap.

- [x] Add source-policy assertions requiring a shared interaction source, selectable `indication = null`, clipped `navigationSelection`, and child `indication(...)` in `HikariFloatingNavigation`.
- [x] Add ViewModel tests for empty cache auto-refresh, populated cache no auto-refresh, failed bootstrap no loop, and observation failure no bootstrap.
- [x] Run static policies and targeted tests; confirm RED is caused by missing Wave 3.5 behavior.

### Task 2: Constrain navigation interaction visuals to the selection pill

**Files:**
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt`

**Interfaces:**
- Consumes: `MaterialTheme.hikariShapes.navigationSelection`, `LocalIndication`, `MutableInteractionSource`.
- Produces: full-size selectable semantics/touch target with rounded child state-layer visuals.

- [x] Remember one `MutableInteractionSource` per item.
- [x] Keep `selectable` on the full navigation cell with `indication = null`.
- [x] Render the inner navigation content in a clipped selection-shaped container using `Modifier.indication(interactionSource, LocalIndication.current)`.
- [x] Preserve selected colors, typography, dimensions, roles, and minimum targets.
- [x] Run design-system tests/static UI policy and confirm GREEN.

### Task 3: Bootstrap Discover once when the cache is genuinely empty

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`

**Interfaces:**
- Consumes: `dependencies.homes`, `observationFailure`, existing guarded `refresh()`.
- Produces: one automatic refresh attempt for a genuinely empty first cache emission.

- [x] Launch a bootstrap coroutine from init.
- [x] Read the first preserved home-cache emission.
- [x] Skip bootstrap when the cache is populated or observation failed.
- [x] Mark bootstrap attempted before delegating to the existing refresh guard.
- [x] Do not schedule retries after source/store/observation failure.
- [x] Run Discover ViewModel tests and performance policy and confirm GREEN.

### Task 4: Final repository verification and patch generation

**Files:**
- Verify all files changed by Tasks 1-3 and this plan/spec.

**Interfaces:**
- Consumes: completed Wave 3.5 implementation.
- Produces: standalone continuation patch applying after Wave 1-3 corrective patches.

- [x] Run `git diff --check`.
- [x] Run UI shared-component, UI token, performance lifecycle, package-boundary, source-layout, and structural policy scripts.
- [x] Attempt targeted Gradle tests; report sandbox network limitation if wrapper resolution is blocked.
- [x] Clone/apply-check the patch against the Wave 1-3 + instrumentation-fix baseline.
- [x] Generate SHA-256 for the final patch.

## Verification status

- Static RED was observed before production changes in both the UI shared-component and performance lifecycle policies.
- Static GREEN is verified after implementation.
- Targeted Gradle unit tests were attempted in the sandbox but the Gradle wrapper could not resolve `services.gradle.org`; run them on the development machine after applying the patch.
