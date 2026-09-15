# Hikari Repository Cleanup Wave 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore trustworthy current verification routing and canonical repository documentation before module-local cleanup.

**Architecture:** Keep production/module architecture unchanged. Make current static verification explicit rather than wildcard-driven, then align implemented-now and orientation documentation with the accepted Step 3 Task 10 tree.

**Tech Stack:** Bash verification scripts, Markdown repository documentation, Gradle architecture tasks (user-owned broad verification).

**Spec:** `docs/superpowers/specs/2026-09-15-hikari-repository-cleanup-wave-0-design.md`

## Global Constraints

- Do not change production Kotlin/Java code, module includes, or project dependency edges.
- Do not begin Step 3 Task 11.
- Keep historical tests/evidence available; only remove them from current automatic routing.
- Do not claim user-owned broad/device/performance gates passed without returned evidence.

---

### Task 1: Fail-close current verification routing

**Files:**
- Modify: `scripts/tests/v2-verification-entrypoints-test.sh`
- Modify: `scripts/verification-common.sh`

**Interfaces:**
- Consumes: current shell/static contract scripts under `scripts/tests/`.
- Produces: `run_repository_static_gates()` with an explicit current-static test list and no nested Gradle invocation.

- [x] **Step 1: Add regression assertions** that reject `scripts/tests/*.sh` wildcard discovery and reject `v2-retired-runtime-absence-test.sh`, `v2-step2-designsystem-slice-test.sh`, and `v2-step3-build-surface-test.sh` from the shared static runner.
- [x] **Step 2: Run** `bash scripts/tests/v2-verification-entrypoints-test.sh` and confirm RED against the wildcard runner.
- [x] **Step 3: Replace wildcard discovery** with an explicit list of current host-static contract scripts.
- [x] **Step 4: Re-run** `bash scripts/tests/v2-verification-entrypoints-test.sh` and confirm PASS.

### Task 2: Align canonical repository entry points

**Files:**
- Modify: `docs/project/current-state.md`
- Modify: `README.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/ui/design-system.md`

**Interfaces:**
- Consumes: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `docs/implementation/current-roadmap.md`, accepted Step 3 Task 10 plan/checkpoint evidence.
- Produces: consistent implemented-now/orientation documentation with Task 11 explicitly not started.

- [x] **Step 1: Rewrite current-state delta** to Step 3 Task 10, including Home/Library/Story ownership, process artwork ownership, and retained/quarantined boundaries.
- [x] **Step 2: Remove stale README graph/checkpoint guidance** and link exact graph/routing to canonical authorities.
- [x] **Step 3: Correct handbook baseline/topology language** so future product architecture is not described as current implementation.
- [x] **Step 4: Update Design System policy** to the accepted Task 10 public surface and ownership rules.

### Task 3: Self-review and package patch

**Files:**
- Review all Wave 0 changed files.
- Create external patch artifact only; do not add generated patch to repository source tree.

**Interfaces:**
- Consumes: Wave 0 diff and focused static verification output.
- Produces: one root-applicable unified diff patch.

- [x] **Step 1: Run focused current static contract scripts** and inspect first failure if any.
- [x] **Step 2: Run source-layout/structural checks only as diagnostics**; record pre-existing module-local debt instead of expanding Wave 0.
- [x] **Step 3: Review diff** for production/module changes, stale Step 2 claims, accidental Task 11 scope, and generated-path mistakes.
- [x] **Step 4: Generate patch** from the original ZIP tree to the Wave 0 tree using repository-relative paths.

## Post-Wave-0 handoff

Wave 0 closes repository-truth repair only. Before any module-local source cleanup, read:

1. `docs/project/file-package-ownership-policy.md`;
2. `docs/superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md`;
3. the current roadmap cleanup boundary.

Do not reuse this Wave 0 plan as authority to move production files. Each selected module requires its
own approved audit/target tree and bounded implementation plan.
