# Hikari Codex Context Harness Phase 1 Implementation Plan

> **For agentic workers:** Execute this docs/harness change task-by-task. This snapshot has no `.git` directory, so artifact verification and a clean unified patch replace commit steps.

**Goal:** Add a compact repository-level Codex context router and remove stale next-work signals from Hikari's four prominent documentation entry points without changing production behavior.

**Architecture:** `AGENTS.md` is a short map, not a duplicate manual. It resolves current work from the canonical roadmap/checkpoint, starts from narrow heading/range reads, expands only on concrete root-cause evidence, and preserves deep self-review inside the affected dependency cone.

**Tech Stack:** Markdown repository instructions, existing Hikari documentation governance, Bash/Python verification.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`

## Global Constraints

- No Kotlin, Gradle, benchmark, test, module-boundary, or runtime behavior changes.
- No recursive rewrite/delete of historical docs, checkpoints, plans, or archives.
- Root `AGENTS.md` stays approximately 80–120 lines and does not duplicate the module graph or active roadmap.
- Narrow-first discovery must never become a hard evidence ban: root-cause trails may expand when concrete evidence requires it.
- Existing documentation precedence remains authoritative.
- No module-local `AGENTS.md` files in Phase 1.

---

### Task 1: Add the root context router

**Files:**
- Create: `AGENTS.md`

**Produces:** A root instruction contract that routes current work through `current-roadmap` -> active checkpoint -> owning plan -> relevant spec/code cone.

- [x] Create `AGENTS.md` with repository identity, precedence, narrow-read routing, context-budget limits, implementation workflow, self-review scope, verification/log discipline, and handoff rules.
- [x] Ensure it tells agents to locate headings/ranges before opening full large docs.
- [x] Ensure it explicitly allows evidence-driven dependency/root-cause expansion.
- [x] Ensure it does not duplicate current task numbers, schema, module list, or other fast-changing status.
- [x] Verify line count is within the Phase 1 budget and every referenced path exists.

### Task 2: Repair prominent current-work entry points

**Files:**
- Modify: `README.md`
- Modify: `docs/README.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/document-governance.md`

**Produces:** Prominent docs route readers to canonical current state/roadmap instead of presenting Wave 10 as future next-work.

- [x] Replace root README's copied status ledger with concise pointers to current state and current roadmap.
- [x] Replace docs README's stale current-execution ledger with a canonical current-roadmap route; do not duplicate active program/Wave/Task status.
- [x] Collapse handbook `Current execution position` to canonical pointers; make its roadmap table clearly capability-history oriented so it cannot override current-roadmap status.
- [x] Keep governance `What is implemented next?` generic and route fast-changing program/Wave/Task status to current-roadmap; keep future-Wave-10 wording historical.
- [x] Preserve historical source labels/evidence; do not rewrite checkpoint history.

### Task 3: Red-team and package the harness

**Files:**
- Review all Task 1–2 files plus the approved spec/plan.
- Create a unified patch and updated source ZIP outside the repository tree.

**Produces:** A self-reviewed docs-only patch with no hidden production changes.

- [x] Check that `AGENTS.md` does not instruct whole-repo scans for bounded tasks.
- [x] Check that bounded self-review still covers correctness, boundaries, dead/duplicate paths, algorithmic expansion, concurrency/lifecycle, and test gaps.
- [x] Check three routing scenarios: continue current performance Task 2, bounded Reader bug, and explicit whole-repo audit.
- [x] Check no stale `Wave 10 is ready to start` / `Wave 10 ... planned future` execution instruction remains in the four entry points.
- [x] Diff against the untouched snapshot and assert changes are docs/instruction-only.
- [x] Produce the clean patch and updated ZIP.


### Task 4: Phase 1.1 context-router hardening

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/README.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/document-governance.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`

**Produces:** A current-only resume route with no copied fast-changing status in prominent entry points.

- [x] Collapse `Current position` to the active task/status/checkpoint/plan/resume route and move long historical context behind an explicit non-current heading.
- [x] Remove active program/Wave/Task duplication from docs README, handbook, and governance.
- [x] Replace the handbook's stale CCE-oriented documentation map and `Start Wave 10` next action with generic canonical routing.
- [x] Teach direct `continue/resume Task N` prompts to read the named task plus owning checkpoint/resume record without roadmap rediscovery.
- [x] Exclude VCS/build/generated/cache/binary trees from default discovery and keep handoff output delta-focused.
- [x] Remove the unreferenced superseded proposed harness spec so discovery has one canonical Phase 1 design.
- [x] Re-run routing, stale-status, scope, patch-apply, and archive-integrity verification before packaging.

### Task 5: Phase 1.2 verification ownership

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Fast agent-owned RED/GREEN loops with expensive/noisy acceptance gates handed to the user by default.

- [x] Restrict automatic agent verification to focused tests and small diagnostic/compile checks.
- [x] Classify unfiltered module/full regression, cross-module, lint/Detekt, architecture verify scripts,
  connected/device, benchmark/profile, and physical-device gates as user-owned by default.
- [x] Keep every owning-plan/checkpoint gate mandatory; change execution ownership only.
- [x] Add chat-only `READY FOR USER VERIFICATION` semantics only when required user-owned gates remain, without
  adding a new canonical status.
- [x] Accept concise user PASS summaries and request failure-log expansion only when diagnosis requires it.
- [x] Require exact remaining verification commands at handoff and prohibit `VERIFIED/CLOSED` claims until
  required supplied evidence has been reviewed.

### Task 6: Phase 1.3 test-output sink policy

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Focused agent-owned verification that preserves full diagnostics off-context while emitting only bounded
success/failure evidence into the agent conversation.

- [x] Redirect chatty agent-owned command stdout/stderr to an OS temporary file outside the repository while preserving exit status.
- [x] Keep successful command output to concise PASS/exit/material summary and do not re-open successful logs by default.
- [x] Read a bounded failure slice first and expand the saved log only when root-cause evidence is insufficient.
- [x] Treat verbose Gradle diagnostics as escalation rather than first-run defaults.
- [x] Re-run the smallest failing/focused regression set after edits; do not broaden automatically to module/full suites.
- [x] Keep temporary logs ephemeral and outside repository governance/commit scope.

### Task 7: Phase 1.4 subagent budget

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Single-agent execution by default, with bounded multi-agent delegation only when independent work
justifies the extra context and review cost.

- [x] Keep a single dependency cone/sequential task in the root agent by default.
- [x] Require at least two independent workstreams plus a material benefit before parallel implementation/exploration delegation.
- [x] Cap a bounded parent task at two delegated workstreams total (concurrent or sequential) and prohibit nested delegation unless explicitly requested.
- [x] Do not spawn dedicated agents merely for tests, summaries, routine self-review/status, or duplicate/best-of-N attempts.
- [x] Keep generic framework/skill/plan boilerplate subordinate to the repository delegation budget unless a task-specific contract or user instruction explicitly requires broader delegation.
- [x] Require a pre-spawn workstream/benefit/context check and a narrow ephemeral file-based brief for every subagent.
- [x] Make subagents inherit root context/test budgets and prevent roadmap/current-work rediscovery or unrelated docs reads by default.
- [x] Prefer ephemeral report files plus terse completion signals so the root reads only needed result slices.
- [x] Permit a single independent reviewer only for high-risk architecture/concurrency/security/cross-module/refactor or explicit red-team work, counting it against the budget.
- [x] Preserve explicit user multi-agent requests and evidence-driven root-cause expansion as valid overrides.

### Task 8: Phase 1.5 subagent model/effort routing

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Delegated work uses the least expensive model/reasoning profile appropriate to its risk instead of
accidentally inheriting an expensive root profile.

- [x] Route mechanical search/inventory/classification/extraction to GPT-5.6 Luna low by default.
- [x] Route normal bounded implementation/investigation to GPT-5.6 Terra medium and allow Terra high for multi-file integration/debugging or medium-risk review.
- [x] Reserve GPT-5.6 Sol high for consequential architecture/concurrency/security/cross-module/red-team review; make Sol xhigh exceptional and max explicit-user-only.
- [x] Require evidence-driven escalation rather than stronger-model "just in case" retries.
- [x] Request both child model and reasoning effort when supported and treat them as unverified until runtime metadata/acknowledgement confirms the profile.
- [x] Keep budget-sensitive work single-agent if the requested child profile cannot be verified rather than silently inheriting a more expensive parent profile.
- [x] Preserve a durable tier-intent fallback if the runtime model catalog changes.
- [x] Keep Phase 1.4 count/nesting budget and Phase 1.2/1.3 verification/output policies unchanged.

### Task 9: Phase 1.6 skill routing

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Approved execution resumes directly without paying a new brainstorming/spec/plan cycle, while genuinely
new design decisions still enter brainstorming deliberately.

- [x] Classify continue/resume/implement/test/review/fix of an approved task/plan/checkpoint as execution rather than a new brainstorming cycle.
- [x] Keep brainstorming for explicit user requests, new feature/design/architecture/public-interface decisions, or load-bearing contradictions that invalidate the current contract.
- [x] Keep ordinary regression/debugging/root-cause work in the execution/debugging path unless it exposes a genuine new design decision.
- [x] Prevent generic brainstorming/design ceremony from regenerating a canonical spec/plan that already owns the work, without suppressing execution-specific debugging/TDD skills.
- [x] Preserve task/checkpoint resume state when a newly discovered architectural contradiction requires a bounded brainstorming detour.
- [x] Keep all Phase 1.1-1.5 context, verification, test-output, subagent-count, and model/effort budgets unchanged.


### Task 10: Phase 1.7 durable handoff enforcement

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Every stopping boundary is recoverable from canonical repository state without a separate user reminder
or per-session handoff document.

- [x] Require checkpoint-governed work to persist durable delta/evidence/open gates/risks/exact resume boundary in the existing owner at every stop.
- [x] Update `current-roadmap.md` only when active task, verification state, or next-work routing changes.
- [x] Keep verification-open tasks open until required supplied evidence is reviewed; never infer closure from implementation alone.
- [x] Persist closure/advance before session end when supplied evidence changes the active/next-work pointer.
- [x] Make checkpoint/roadmap persistence automatic before final handoff rather than dependent on a separate user request; never invent a checkpoint for ungoverned work.
- [x] Prohibit duplicate per-thread/session handoff/summary/status files.
- [x] Preserve all Phase 1.1-1.6 context, skill, test-output, verification, and subagent budgets.

### Task 11: Phase 1.8 command execution / polling budget

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-09-07-hikari-codex-context-harness-phase1-design.md`
- Modify: `docs/superpowers/plans/2026-09-07-hikari-codex-context-harness-phase1.md`

**Produces:** Long-running agent-owned commands do not consume repeated model turns merely to supervise a live process;
a focused command may run for minutes as one blocking tool invocation and only returns control to the model after exit.

- [x] Validate the active Codex runtime with a blocking sleep probe and use the observed single-invocation behavior as the command-execution baseline.
- [x] Require one synchronous/blocking invocation through process exit for agent-owned non-interactive commands, with stdout/stderr redirected outside the repository.
- [x] Make model-turn supervision rather than wall-clock duration the budgeted cost; do not hand off a focused command merely because it runs for minutes.
- [x] Prohibit `tail -f`, periodic log reopening, progress requests, and liveness polling while the blocking call remains active.
- [x] If the runtime returns a live-process handle early, prefer a native long blocking wait; hand off rather than enter repeated model-turn polling unless supervision was explicitly delegated.
- [x] For explicitly delegated supervision, keep unavoidable polling coarse/status-only and defer diagnostics until exit/failure.
- [x] Preserve Phase 1.2 gate ownership: full regression/cross-module/device/benchmark/profile/acceptance gates remain user-owned for acceptance/governance reasons, not simply duration.
- [x] Preserve Phase 1.3 output sinking, bounded failure slices, process identity, and real exit status.
