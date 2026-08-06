# Wave 01 Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a fail-closed architecture gate, standardize the application identity, and require API 26/API 37 launch evidence before Wave 01 can be closed.

**Architecture:** A versioned JSON policy describes every direct project dependency. A root build-logic plugin adapts the Gradle project model into a pure verifier, while stable shell entry points invoke fast and checkpoint verification. Android instrumentation proves launch/navigation behavior on min and target SDKs.

**Tech Stack:** Kotlin/Gradle build logic, AGP 9.3.0, JDK 17, Kotlin test/JUnit, AndroidX test/Compose UI test, Bash, GitHub Actions.

## Global Constraints

- Package namespace and application ID are `app.openstory`.
- JDK 17 is mandatory.
- Min SDK is 26; compile/target SDK is 37.
- Direct dependencies only are governed by the policy.
- Unknown modules, configurations, policy versions, and legacy production tokens fail closed.
- No live website is used by tests.
- TDD is mandatory for behavior changes.
- No Wave 02-04 product behavior is changed.

---

### Task 1: Add the module policy model and pure verifier

**Files:**
- Create: `config/architecture/module-boundaries.json`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryModels.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryPolicyLoader.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifier.kt`
- Test: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Test: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryPolicyLoaderTest.kt`

**Interfaces:**
- Consumes: module policy JSON and normalized actual module/dependency records.
- Produces: `ModuleBoundaryVerifier.verify(policy, actualModules): List<ArchitectureViolation>`.

- [ ] Write failing tests for missing/stale modules, denied dependencies, and stale production/test allowlist permissions.
- [ ] Run focused tests and confirm they fail because the verifier is absent.
- [ ] Implement policy models, JSON loader, and pure verifier.
- [ ] Run focused build-logic tests.

### Task 2: Enforce the policy from the actual Gradle model

**Files:**
- Create: `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyModuleBoundariesTask.kt`
- Modify: `build-logic/build.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `scripts/check-module-dependencies.sh`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`

**Interfaces:**
- Consumes: `ModuleBoundaryVerifier`, Gradle subprojects/configurations, production source trees.
- Produces: root tasks `verifyModuleBoundaries` and `verifyArchitecture`.

- [ ] Add failing static registration tests.
- [ ] Register `openstory.architecture` and adapt Gradle project dependencies.
- [ ] Replace Bash allowlist logic with a call to `verifyArchitecture`.
- [ ] Verify the current graph accepts `:core:plugin-host -> :core:network` and rejects unlisted dependencies.

### Task 3: Standardize the Android application identity

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/app/openstory/build/architecture/VerifyApplicationIdentityTask.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`

**Interfaces:**
- Produces: `verifyApplicationIdentity`, included by `verifyArchitecture`.

- [ ] Add a failing test proving `com.example.hikari` remains.
- [ ] Change namespace/application ID to `app.openstory`.
- [ ] Enforce the identity through the Android application extension and production token scan.
- [ ] Re-run focused checks.

### Task 4: Add API 26/API 37 launch and navigation gates

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt`
- Create: `scripts/verify-instrumentation.sh`
- Create: `scripts/verify-wave-checkpoint.sh`
- Create: `scripts/tests/verify-instrumentation-test.sh`
- Create: `scripts/tests/verify-wave-checkpoint-test.sh`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: connected Android device/emulator and app instrumentation runner.
- Produces: reproducible min/target SDK checkpoint jobs.

- [ ] Write instrumentation tests for initial Home, top-level navigation, and recreation.
- [ ] Add device API validation and launcher process smoke checks.
- [ ] Add shell contract tests for API matching, Gradle invocation, launcher checks, serial propagation, and missing-serial failure.
- [ ] Add independent API 26 and API 37 CI jobs and final aggregation.

### Task 5: Update verification governance and evidence

**Files:**
- Modify: `scripts/verify.sh`
- Modify: `README.md`
- Create: `docs/contributing/adding-a-module.md`
- Create: `docs/internal/checkpoints/wave-01-remediation.md`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`

**Interfaces:**
- Produces: documented fast/checkpoint commands and auditable evidence states.

- [ ] Extend architecture smoke tests for policy, scripts, CI jobs, report paths, and module documentation.
- [ ] Update README to the current eight-module graph.
- [ ] Add module onboarding procedure and checkpoint evidence template.
- [ ] Run repository/static checks; run full Gradle and instrumentation gates where JDK 17/Android emulators are available.
