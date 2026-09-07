# Hikari Whole-App Performance Big Update — Source-of-Truth Bundle v3

**Original snapshot audited:** `Hikari-perf-discover-end-to-end(1).zip`  
**Revalidated against:** `Hikari-master.zip` (`c5b1102387f5a310fc00b926964751212e900051`)  
**Revalidated master archive SHA-256:** `a4b7b17a5f59dde6ff5d7ae1884dae043f638f9d36c35b8b1b601a98feb1f9b1`  
**Supersedes baseline v2 SHA-256:** `d3813d2a3a1c4216ccfd2c1d33e1593949ac2fc59a7d3412da87a49d3139ad0d`  
**Audit/design/revalidation date:** 2026-09-07  
**Scope:** whole application — Catalog/Canonical, Chapters, Reader, Downloads/cache, Room/storage, plugin runtime/control plane/authentication, background work, navigation/UI lifecycle, startup, memory/allocation and shared-resource behavior.

## Status

This bundle is the revised **analysis/design/planning baseline** for the Big Performance Update after a second source-level red-team/self-review against the current `Hikari-master.zip`. It supersedes v2 because the v2 closure census was still too Room/data-observer-centric and missed three independent plugin-runtime root-cause families.

It does **not** claim implementation has started or that any root cause is already fixed.

- Confirmed structural root-cause families: **33**
- Structural/scaling risks requiring evidence gates: **7**
- Planned implementation waves: **9** (`Wave 0` through `Wave 8`)
- Room schema target while unreleased: **v13 / MIGRATION_12_13**
- New confirmed families added by v3: **X16–X18**
- New evidence-gated risk added by v3: **RISK-PLUGIN-AUTH-CACHE**

The central audit rule is now deliberately storage-agnostic:

> For every operation, compare **semantic scope** with **physical work scope** across Room, filesystem/blob/package storage, Android assets, secure session/Keystore operations, process memory maps, work queues and network/plugin control plane. A bounded/control-plane operation must not silently materialize unrelated global/executable/package state.

A second closure rule applies to degraded states:

> Expensive retry ownership must distinguish **success/satisfied**, **terminal or user-action-required**, **transient retryable**, and **cancelled** outcomes. Stable failure state must not become repeated hot-path work.

## Read in this order

1. `Hikari-performance-architecture-audit-whole-app-big-update-2026-09-07.md` — 33-family evidence census, 7 risks, causal themes and measurement gaps.
2. `REVALIDATION-2026-09-07.md` — v2→v3 red-team corrections and source evidence.
3. `2026-09-07-hikari-whole-app-performance-big-update-design.md` — target architecture, invariants, complexity contracts and conflict resolutions.
4. `2026-09-07-hikari-whole-app-performance-big-update-master-roadmap.md` — dependency graph, Room-v13 ownership ledger and 33-row closure-criteria matrix.
5. `2026-09-07-hikari-perf-wave-0-contracts-and-fixtures.md`
6. `2026-09-07-hikari-perf-wave-1-bounded-storage-foundations.md`
7. `2026-09-07-hikari-perf-wave-2-catalog-evidence-and-lifetime.md`
8. `2026-09-07-hikari-perf-wave-3-canonical-execution.md`
9. `2026-09-07-hikari-perf-wave-4-plugin-runtime-control-plane.md`
10. `2026-09-07-hikari-perf-wave-5-reader-cache-memory.md`
11. `2026-09-07-hikari-perf-wave-6-chapter-delta-and-background.md`
12. `2026-09-07-hikari-perf-wave-7-ui-demand-and-lifecycle.md`
13. `2026-09-07-hikari-perf-wave-8-risk-acceptance-and-freeze.md`

## Program invariants

- Optimize ownership/data scope, not isolated screens or hot methods.
- No foreground point/bounded operation may silently depend on unrelated historical/global state.
- No metadata/control-plane plugin query may load executable/package payload merely to answer capability/version/policy state.
- Terminal/user-action-required plugin outcomes may be memoized only under explicit immutable identity/state invalidation; transient retry/cancellation remains recoverable.
- Do not weaken canonical identity/reconciliation semantics for speed.
- Do not weaken Reader asset integrity/security invalidation semantics.
- Do not weaken plugin capability/network/authentication validation or auto-approve capability expansion for performance.
- Do not introduce long-lived plaintext credential caching before `RISK-PLUGIN-AUTH-CACHE` promotion and threat review.
- Do not undo P6 retained-navigation warm-state behavior without measured evidence.
- Do not run expensive normalization/hash/backfill loops during Room migration/open.
- Risks are benchmarked first; speculative global managers, resource arbiters, JS-isolate pools or decrypted credential caches are forbidden without promotion evidence.
- A root cause is closed only when its complexity/invalidation/ownership/failure-lifetime contract is verified, not when one call site becomes faster.

## Important v3 self-review corrections

- `L2` remains a risk, not a defect: retained composition itself is not condemned; inactive semantic work must be measured.
- A6 still covers every bounded redirect decision, including merge-reversal nested-lineage checks.
- A8 still includes one-Story Library/progress and current-Story release download-status demand; standalone Library/Downloads fixes are insufficient.
- X12 still includes Reader asset touch memoization and `CatalogMetadataCoordinator` suppression retention.
- Compact reconciliation evidence/postings still move transactionally with forward merge and controlled reversal ownership.
- **X16:** `DefaultPluginRuntime.enabled(operation)` currently loads `manifest.json` **and `main.js`** for candidate plugins; request-time authentication policy enumerates all installed manifests; bundled provisioning reads `.osp` bytes before installed-version filtering.
- **X17:** `BundledPluginProvisioner` memoizes only all-success; unchanged `NEEDS_REVIEW`/terminal state can trigger repeat provisioning on every runtime entry. V3 fixes retry ownership without blanket negative caching.
- **X18:** authenticated plugin HTTP request construction currently rereads/decrypts session state to refresh summary, and Keystore key lookup is repeated per credential record. V3 closes duplicate work without requiring plaintext credential caching.
- `RISK-PLUGIN-ISOLATE` is measured **after** X16–X18 so isolate timing is not blamed for control-plane/package/auth overhead.
- `RISK-PLUGIN-AUTH-CACHE` is a separate post-X18 decision because keeping decrypted credentials longer has a real security trade-off.
- Final closure audit is no longer Room-centric: it performs semantic-scope→physical-work and stable-failure→retry-work census across all major storage/execution boundaries.

## Execution rule

Start with Wave 0 and execute each detailed plan task in order using TDD plus per-task self-review. Wave 4 plugin-runtime work can proceed in parallel with Room-centric Waves 1–3 after Wave 0 because it has no v13 schema dependency. Waves 5 and 6 may proceed in parallel after Room-v13 ownership is coordinated. Wave 7 consumes bounded repository APIs; Wave 8 is the final evidence/freeze gate.

`MANIFEST.sha256` records the exact bytes of this v3 bundle after verification.
