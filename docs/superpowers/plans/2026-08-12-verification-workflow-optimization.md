# Hikari Verification Workflow Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Hikari's full acceptance gate while reducing repeated Gradle startup work and adding a faster development feedback loop.

**Architecture:** Keep `scripts/verify.sh` as the canonical full gate, but run architecture and quality tasks in one Gradle invocation. Add `scripts/verify-fast.sh` for development iterations, share repository/static checks through a small shell helper, and enable Gradle's local build cache without weakening strict dependency verification.

**Tech Stack:** Bash, Gradle 9.5, Kotlin/Android Gradle build, existing repository shell contract tests.

## Global Constraints

- `scripts/verify.sh` remains the CI/full acceptance entry point.
- Strict dependency verification remains enabled.
- Full verification still runs architecture, all local tests, Android lint, Detekt, app debug assembly, and Room schema stability.
- Fast verification does not replace the full gate and intentionally omits Android lint and app assembly.
- Do not change the approved 14-module graph or Room schemas 1 through 6.

---

### Task 1: Lock the verification entry-point contract

**Files:**
- Create: `scripts/tests/verification-entrypoints-test.sh`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`

**Interfaces:**
- Consumes: existing `scripts/verify.sh`, `scripts/check-module-dependencies.sh`, and `gradle.properties`.
- Produces: executable contract assertions for full/fast verification behavior.

- [x] **Step 1: Write a failing shell contract test**

Assert that `verify-fast.sh` and the shared helper exist, full verification contains `verifyArchitecture` directly, neither full nor fast verification forces `--no-daemon`, fast verification omits `lintDebug`/`:app:assembleDebug`, and `org.gradle.caching=true` is present.

- [x] **Step 2: Run the shell test and confirm RED**

Run: `bash scripts/tests/verification-entrypoints-test.sh`
Expected: FAIL because `scripts/verify-fast.sh` and the new optimized contract do not exist yet.

- [x] **Step 3: Update ArchitectureSmokeTest expectations**

Require the two new verification scripts and assert the full gate owns `verifyArchitecture` directly rather than spawning the standalone architecture script.

### Task 2: Implement shared, fast, and full verification paths

**Files:**
- Create: `scripts/verification-common.sh`
- Create: `scripts/verify-fast.sh`
- Modify: `scripts/verify.sh`
- Modify: `scripts/check-module-dependencies.sh`
- Modify: `gradle.properties`

**Interfaces:**
- Consumes: existing repository shell gates and Gradle tasks.
- Produces: `run_repository_static_gates`, full verification entry point, and fast verification entry point.

- [x] **Step 1: Extract common repository/static gates**

Move the shell contract tests, structural policy checks, source-layout review, and current architecture checks into `run_repository_static_gates` in `scripts/verification-common.sh`.

- [x] **Step 2: Make full verification a single Gradle invocation**

Run `verifyArchitecture`, `:build-logic:test`, `test`, `testDebugUnitTest`, `lintDebug`, `detekt`, and `:app:assembleDebug` together with strict dependency verification and no `--no-daemon` flag.

- [x] **Step 3: Add fast verification**

Run the same static gates plus `verifyArchitecture`, `:build-logic:test`, `test`, `testDebugUnitTest`, and `detekt`; preserve Room schema fingerprint checks while omitting `lintDebug` and `:app:assembleDebug`.

- [x] **Step 4: Enable local build cache**

Add `org.gradle.caching=true` to `gradle.properties`.

- [x] **Step 5: Run the shell contract test and confirm GREEN**

Run: `bash scripts/tests/verification-entrypoints-test.sh`
Expected: PASS.

### Task 3: Record Task 1 acceptance and the optimized workflow

**Files:**
- Create: `docs/internal/checkpoints/product-ui-task-01-toolchain.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/superpowers/plans/2026-08-12-redantotsu-inspired-product-ui-implementation-plan.md`

**Interfaces:**
- Consumes: user-provided successful Task 1 verification evidence.
- Produces: canonical documentation stating Task 1 COMPLETE, Task 2 NEXT, and `verify-fast.sh` as the development loop while `verify.sh` remains the acceptance gate.

- [x] **Step 1: Add Task 1 checkpoint evidence**

Record dependency metadata bootstrap PASS, focused Task 1 suite PASS, full `scripts/verify.sh` PASS, 14 modules, Room schemas 1..6 stable, and non-blocking existing warnings.

- [x] **Step 2: Update canonical current docs**

Replace Task 1 active wording with Task 1 COMPLETE / Task 2 NEXT and document the fast/full verification split.

- [x] **Step 3: Mark Product UI Task 1 complete**

Add explicit Task 1 completion status and Task 2 next status in the active implementation plan without rewriting historical checkpoint documents.

### Task 4: Verify and package

**Files:**
- Verify all modified files.
- Create follow-up patch outside the repository tree.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: an apply-clean patch for the user's post-Task-1 working tree.

- [x] **Step 1: Run shell verification contracts**

Run: `bash scripts/tests/verification-entrypoints-test.sh` and all repository shell contract tests.
Expected: PASS.

- [x] **Step 2: Perform static script syntax checks**

Run: `bash -n scripts/verification-common.sh scripts/verify-fast.sh scripts/verify.sh scripts/check-module-dependencies.sh`.
Expected: PASS.

- [x] **Step 3: Apply-check the generated patch against a clean post-Task-1 tree**

Expected: `git apply --check` exits 0.

- [x] **Step 4: Leave full Gradle verification for the target JDK 17 host**

Because the artifact sandbox cannot reproduce the user's Android/JDK 17 environment, do not claim full Gradle PASS from the sandbox. The user's existing Task 1 full verification remains the acceptance evidence before this workflow-only patch; after applying the patch, run `./scripts/verify-fast.sh` during development and `./scripts/verify.sh` once before closing Task 2.
