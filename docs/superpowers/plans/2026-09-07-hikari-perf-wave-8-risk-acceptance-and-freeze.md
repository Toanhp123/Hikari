# Hikari Performance Wave 8 — Risk Acceptance and Freeze Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evaluate the seven structural risks against the repaired architecture, implement only evidence-promoted fixes through focused addenda, remove obsolete compatibility paths, and freeze final whole-app performance contracts.

**Architecture:** Wave 8 is an evidence gate, not a speculative optimization bucket. Every risk gets a reproducible workload and explicit accept/promote result. Promoted risks require their own focused design note before code; unpromoted risks are documented and deliberately left simple.

**Tech Stack:** Macrobenchmark, Perfetto/Android tracing where available, Room query plans, plugin runtime tests/benchmarks, baseline profiles.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Do not create a cross-capability global resource arbiter unless `RISK-GLOBAL-RESOURCE` is promoted by traces.
- Do not pool JavaScript isolates unless `RISK-PLUGIN-ISOLATE` is promoted and security/isolation review approves the reuse model.
- Do not retain decrypted plugin credentials across requests unless `RISK-PLUGIN-AUTH-CACHE` is promoted and a threat review defines generation/TTL/invalidation bounds.
- Do not cap reconciliation candidates without identity-quality regression analysis.
- Do not remove migration/backfill compatibility paths until durable completion is proven on upgraded aged fixtures.

---

### Task 1: Decide RISK-A4 candidate fan-out with collision corpus

**Files:**
- Create: `catalog/engine/src/test/kotlin/app/openstory/catalog/engine/reconciliation/CatalogCandidateCollisionScalingTest.kt`
- Consume: Wave-2 compact evidence repository tests
- Document: Wave-8 checkpoint

- [ ] **Step 1: Build three deterministic corpora**: unique evidence, common title/author tokens, adversarial collision-heavy evidence while keeping valid reconciliation semantics.
- [ ] **Step 2: Measure candidate count `K`, ranking CPU and end-to-end Search slope** at increasing `N` after compact indexed lookup.
- [ ] **Step 3: Accept risk** if postings/selectivity keep practical `K` bounded and candidate evaluation is not dominant.
- [ ] **Step 4: If promoted**, stop and write a focused `candidate-selectivity` design addendum before adding stop words/caps/staging; include identity-quality differential tests.

---

### Task 2: Decide RISK-BIND with large bounded Story sets

**Files:**
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolverTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogStoryProjectionRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`

- [ ] **Step 1: Execute bounded APIs** at tens, hundreds and thousands of Story IDs and verify chunk logic never exceeds configured bind cardinality.
- [ ] **Step 2: Capture query plans/query count** and check for a new cliff caused by excessive chunk count or poor index plan.
- [ ] **Step 3: Accept risk** if cost grows approximately with requested cardinality and chunks; otherwise write a focused bulk-read strategy addendum.

---

### Task 3: Finalize RISK-LIFECYCLE

**Files:**
- Benchmark: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Consume checkpoint: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-retained-tab-risk.md`

- [ ] **Step 1: Re-run retained-tab diagnostics after all bounded UI work**; earlier global observers may have been the real amplifier.
- [ ] **Step 2: Record inactive Room/reducer work, warm navigation frames and repeated-cycle RSS**.
- [ ] **Step 3: If Wave 7 promoted the risk**, stop final freeze until a focused lifecycle-demand design + implementation plan is approved and implemented; its acceptance gate must preserve P6 warm navigation and bounded inactive semantic work.
- [ ] **Step 4: If Wave 7 accepted the risk as non-defect**, preserve retained composition explicitly in final docs and record the measured inactive-work threshold that justified acceptance.

---

### Task 4: Decide RISK-PLUGIN-ISOLATE

**Files:**
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/PluginInvocationScalingTest.kt`
- Inspect: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/AndroidxJavaScriptEngine.kt`

- [ ] **Step 1: Benchmark invocation after Wave 4 X16–X18 closure** with manifest/script source already resolved while varying operation count and payload size; isolate startup must be measured separately from plugin control-plane/package/auth work and plugin JS/network time.
- [ ] **Step 2: Accept fresh-isolate design** if isolate creation is not material to realistic foreground budgets.
- [ ] **Step 3: If promoted**, write a separate security/performance design comparing isolate pool, warm precreation and per-plugin/session reuse; do not implement pooling directly in this task.

---

### Task 5: Decide RISK-PLUGIN-AUTH-CACHE after X16/X18 structural cleanup

**Files:**
- Test/benchmark: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionManagedCredentialProviderTest.kt`
- Instrumentation: `app/src/androidTest/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStoreTest.kt`
- Inspect: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionService.kt`
- Inspect: `app/src/main/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStore.kt`

- [ ] **Step 1: Verify Wave-4 precondition**: request-time policy is point-scoped, one authenticated request performs one `PluginSessionStore.readAll()`, and one store operation acquires the Keystore key once. Do not benchmark the old duplicate path as justification for caching secrets.
- [ ] **Step 2: Measure real-device request-build/session cost** for `Cr = 0/1/4` and `Hr = 1/10/100` under cold/warm process state while keeping network/JavaScript execution outside the measured section.
- [ ] **Step 3: Accept the no-cache design** if the remaining one-snapshot secure read/decrypt cost is not material to realistic provider/request latency or user-visible budgets.
- [ ] **Step 4: If promoted**, stop and write a focused security/performance addendum before code. The design must define credential generation binding, max TTL/cardinality, logout/policy-change/security invalidation, process-death behavior, memory zeroization limitations, and threat model; no ad-hoc plaintext map is permitted.

---

### Task 6: Decide RISK-GLOBAL-RESOURCE through concurrent workload traces

**Files:**
- Benchmark/test harness: `benchmark` and benchmarkRelease fixtures
- No production arbiter file unless risk is later promoted through a new design

- [ ] **Step 1: Build concurrent scenarios**: visible Reader image fetch + Downloads/background automatic cache work; Reader + Chapter sync; Search providers + Chapter worker; plugin calls + DB-heavy maintenance.
- [ ] **Step 2: Trace CPU, network concurrency, Room latency, I/O waits and visible frame impact** rather than inferring contention from coroutine counts.
- [ ] **Step 3: Accept existing domain-local limiters** if user-critical latency remains within target budgets.
- [ ] **Step 4: If promoted**, write a neutral shared-capacity design with explicit priority/ownership and no feature dependency cycle before code.

---

### Task 7: Decide RISK-STARTUP-CONTENTION on upgraded aged state

**Files:**
- Benchmark: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Inspect/measure: `app/src/main/kotlin/app/openstory/cache/AutomaticCachePolicyCoordinator.kt`, backfill workers, application startup wiring

- [ ] **Step 1: Prepare fresh, aged-complete-backfill and aged-incomplete-backfill DB states**.
- [ ] **Step 2: Run cold startup and first navigation** while recording Room/backfill/policy work overlap.
- [ ] **Step 3: Verify catalog evidence and Chapter schedule backfills execute bounded background batches and do not synchronously block startup.**
- [ ] **Step 4: If contention is material**, write a startup-budget/prioritization addendum; do not hide the issue by arbitrarily delaying all background work.

---

### Task 8: Remove obsolete compatibility paths after backfill/upgrade proof

**Files:**
- Candidate removals across Catalog evidence legacy fallback, completed-backfill compatibility adapters, old storage union entities/APIs, and deprecated projection helpers
- Tests: migration/backfill/process-recreation suites

- [ ] **Step 1: Enumerate compatibility code introduced by Waves 1–6** and the durable state that proves each fallback is no longer needed.
- [ ] **Step 2: Keep compatibility required for users upgrading from v12**; only remove process-local or fully superseded runtime paths, not migration support needed by released installs.
- [ ] **Step 3: Delete unused global-runtime paths** such as old runtime ingest index initialization or full candidate planner only after production callers are zero.
- [ ] **Step 4: Run source search + architecture tests** to prove no dead duplicate performance architecture remains.
- [ ] **Step 5: Commit focused cleanup** `refactor(perf): retire superseded performance paths`.

---

### Task 9: Final whole-app scaling sweep and baseline-profile regeneration

**Files:**
- Benchmark: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt`
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-performance-big-update-final.md`
- Update: whole-app performance audit/design docs with final measured status

- [ ] **Step 1: Re-run the source-level closure audit against the final production tree** using semantic-scope→physical-work census across Room, filesystem/blob/package storage, Android assets, secure session/Keystore operations, process-lifetime maps, work queues and network/plugin control plane. Re-enumerate bounded UI global observers, bounded redirect materialization, process-lifetime mutable maps, compact-evidence ownership writers, plugin metadata queries that load executable/package payloads, and stable failures that trigger repeated expensive attempts. Classify legitimate global/maintenance work explicitly; any unexplained bounded→wide or terminal→repeated path reopens its family before freeze.
- [ ] **Step 2: Run all deterministic module suites and architecture/schema scripts**.
- [ ] **Step 3: Run scaling sweeps** for `N/B/R/G/Q/RA/AC/Y/J/Z/Pg/Lb/H/V/L/D/P/K/Pl/JsB/Bp/Cr/Hr` using the Wave-0 fixtures.
- [ ] **Step 4: Compare slopes as well as absolute medians/tails** and explicitly identify any dimension still leaking into unrelated point/bounded work, including Story-screen `L/D/G` aging with fixed target data.
- [ ] **Step 5: Run memory scenarios** for repeated Reader image navigation, tab cycling, Reader touch memoization and Catalog metadata suppression cardinality.
- [ ] **Step 6: Run plugin control-plane/failure/auth scenarios** and verify `enabled(operation)` is independent of `JsB`, unchanged terminal provisioning is independent of repeated `Bp`, point credential policy is independent of unrelated `Pl`, and X18 operation-count contracts remain closed.
- [ ] **Step 7: Run background/recovery scenarios** with nonzero durable backlog and upgraded incomplete backfill.
- [ ] **Step 8: Regenerate Baseline Profile only after final production paths are stable**, then rerun startup/navigation benchmarks with required profile mode.
- [ ] **Step 9: Write final checkpoint** containing before/after evidence, source-level closure audit results, accepted risks, promoted follow-up specs, and any consciously deferred non-root opportunities.
- [ ] **Step 10: Commit** `test(perf): freeze whole-app performance big update`.

---

## Wave 8 / program acceptance gate

- [ ] all 33 confirmed root causes satisfy their closure criteria or are explicitly reopened with evidence; closure requires the final source-level semantic-scope→physical-work source audit, not matrix row count alone.
- [ ] all 7 risks have an accept/promote decision backed by workload evidence.
- [ ] no speculative global manager/arbiter/isolate pool/decrypted credential cache was introduced without promotion.
- [ ] upgraded v12→v13 aged state remains correct and startup-safe.
- [ ] canonical, Reader security/integrity, Chapter differential and notification contracts are green.
- [ ] P6 warm-navigation behavior is preserved.
- [ ] final benchmark report records scaling slopes across all relevant historical/global/control-plane/failure-state dimensions.
- [ ] obsolete duplicate runtime paths are removed or documented as required compatibility.
