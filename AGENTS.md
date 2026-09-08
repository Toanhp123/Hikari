# Hikari Agent Guide

## Purpose

Compact routing instructions for agentic work. This file is a map, not a roadmap, architecture
ledger, module list, or status database. Prefer canonical repository sources over copied status.

## Stable repository facts

- Android/Kotlin multi-module repository with modular-monolith / Clean-style capability boundaries.
- Module inclusion: `settings.gradle.kts`.
- Dependency edges: `config/architecture/module-boundaries.json`.
- Documentation precedence: `docs/project/document-governance.md`.
- Do not copy fast-changing task, wave, schema, or module-count status into this file.

## Resolve scope before reading

- Direct user instructions and an explicitly named task/plan/file define the initial scope.
- If the exact task/plan is named, read it directly; on continue/resume also read its owning checkpoint/open-work record. Use the roadmap only if ownership/resume is ambiguous.
- For "continue current work" or ambiguous execution, read only `## Current position` in `docs/implementation/current-roadmap.md` far enough to identify active task, checkpoint, and owning plan.
- Freeze that resolved active task as the execution boundary for the current
  turn. Later checkpoint/roadmap updates during the same turn do not change
  the execution boundary.
- Then read the checkpoint resume/open-work slice, owning plan global constraints + active `Task N`, and only design/spec sections needed for missing invariants.
- Inspect production code, tests, scripts, and direct callers/callees in the affected dependency cone.
- Find headings/ranges before opening whole large documents; expand only when the narrow slice is insufficient.

## Source precedence by question

- Implemented now: `docs/project/current-state.md`, then code/tests.
- Next work: `docs/implementation/current-roadmap.md`, then active checkpoint/owning plan.
- Product/domain scope: `docs/project/approved-product-design.md`.
- Gate evidence: the named record under `docs/internal/checkpoints/`.
- Public plugin behavior: `docs/plugin-sdk/`, `:plugins:api`, and tested fixtures.
- Historical provenance: `docs/internal/archive/` only when provenance/supersession is material.

README/handbook prose is navigation only and never overrides canonical state, roadmap, checkpoint, or owning task contract.

## Context-budget rules

- Do not recursively read `docs/` or load every plan/spec/checkpoint "for completeness".
- Do not scan every module before a bounded task.
- Do not reopen completed tasks/waves without a regression, contradiction, or dependency trail.
- Do not read archive/history unless the current question requires it.
- Prefer exact symbol/path search and narrow file ranges over dumping large files.
- Ignore `.git/`, `.gradle/`, `**/build/`, generated outputs, caches, and binaries unless the task/evidence requires them.
- Keep successful build output to command + exit result + material summary.
- On failure, inspect the first useful failure/stack/diagnostic region before ingesting more log output.

These are discovery defaults, not evidence bans. If root-cause evidence crosses the initial cone, expand to the necessary caller/callee/module/boundary/authority and state why.

## Skill routing

- Approved task/plan/checkpoint execution is implementation work, not a new brainstorming cycle.
- Do not invoke brainstorming merely to continue/resume/implement/test/review/fix work whose contract is already defined.
- Use brainstorming for a new feature/design/architecture/public-interface decision, an explicit user request, or a genuine design ambiguity/contradiction that invalidates the current contract.
- A regression, compile/test failure, or root-cause investigation does not by itself trigger brainstorming.
- Do not regenerate a spec or implementation plan when an approved canonical one already owns the work.
- Generic brainstorming/design ceremony does not reopen approved execution; debugging/TDD and other execution-specific skills still apply. If a load-bearing architectural contradiction invalidates the contract, preserve task state and brainstorm only that new decision.

## Implementation workflow

1. Resolve the exact task contract and resume boundary.
2. Build the smallest production/test dependency cone that can explain the behavior.
3. Characterize/reproduce before changing behavior when the task or plan requires TDD.
4. Implement the smallest ownership-correct change satisfying the active contract.
5. Run only focused agent-owned tests/checks needed for RED/GREEN and local diagnosis; wall-clock duration alone does not change ownership.
6. Self-review the final changed cone; fix discovered in-scope issues before handoff.
7. Hand expensive repository/module/device/performance gates to the user with exact commands unless explicitly delegated back.
8. At the end of the active canonical Task N, update the existing owning
   checkpoint with durable delta/evidence/open gates/risks/exact resume boundary.
   Update `current-roadmap.md` when active task, verification state, or next-work
   pointer changes. This persistence may point to Task N+1, but MUST NOT trigger
   execution of Task N+1 in the current turn unless the user explicitly requested it.
9. Stop and hand control back to the user after the active canonical Task N.
   Do not consume the newly updated roadmap/checkpoint as authorization to begin
   the next task.ication state, or next-work pointer changes; never require a separate user reminder.

## Self-review contract

Self-review is mandatory inside the changed dependency cone plus justified root-cause expansion. Check:

- correctness, failure semantics, and regressions;
- module/package/ownership boundary violations;
- duplicate, dead, legacy, wrapper, circular, or unnecessary indirection exposed by the change;
- algorithmic complexity and semantic-scope -> physical-work amplification;
- concurrency, cancellation, lifecycle, invalidation, and failure-lifetime behavior;
- test gaps, fixture realism, and test-only/release-path leakage;
- contradictions with active plan, design, checkpoint, and canonical state.

Do not turn each task self-review into a whole-repository audit. For an explicit whole-repo/red-team audit, inspect the full relevant architecture deliberately.

## Subagent budget

- Do not delegate by default; keep a single dependency cone or sequential task in the root agent.
- Parallel implementation/exploration requires at least two genuinely independent workstreams and a material quality or wall-clock benefit.
- For a bounded parent task, default maximum is 2 subagent workstreams total (concurrent or sequential, reviewers included). More requires explicit user direction or a justified whole-repo/red-team decomposition.
- Subagents must not spawn further agents unless the user explicitly requests multi-level orchestration.
- Do not spawn agents merely for testing, summarization, routine self-review/status, or duplicate/best-of-N attempts on the same question.
- Generic framework/skill/plan boilerplate that recommends or requires subagents is not independent justification; this budget controls unless a task-specific contract or the user explicitly requires broader delegation.
- Before spawning, establish the independent workstream, expected benefit, and exact context/paths assigned; if any is unclear, do not spawn.
- Give each subagent a narrow ephemeral file-based brief; it inherits context/test budgets and must not rediscover roadmap/current work or unrelated docs.
- Prefer an ephemeral report file + terse completion signal; root reads only needed slices. No full logs/source dumps/plan restatement in returns.
- A single independent reviewer is allowed only for high-risk architecture, concurrency, security, cross-module ownership, broad refactors, or explicit red-team work.
- If delegation duplicates more context than useful work, stay single-agent.

## Subagent model and effort routing

- Model/effort selection is part of the delegation budget; request both explicitly when the runtime supports them.
- Mechanical search/inventory/classification/extraction only: prefer GPT-5.6 Luna at low effort.
- Normal bounded implementation/investigation: prefer GPT-5.6 Terra at medium effort.
- Multi-file integration/debugging or medium-risk independent review: prefer Terra at high effort.
- High-risk architecture, concurrency, security, cross-module ownership, or consequential red-team review: prefer GPT-5.6 Sol at high effort.
- Sol `xhigh` is exceptional; `max` requires explicit user direction. Do not overprovision a stronger model merely "to be safe".
- Escalate model/effort only from evidence that the current tier is insufficient; do not restart successful lower-tier work on a stronger model without an independent risk reason.
- Treat requested child model/effort as unproven until runtime metadata/acknowledgement confirms it. For budget-sensitive work, if the child profile cannot be verified, stay single-agent rather than silently inherit an expensive parent profile.
- If the runtime catalog changes, preserve the intent: cheapest adequate model for mechanical work, balanced model for normal work, flagship only for consequential judgment.

## Command execution / polling budget

- Agent-owned non-interactive commands must use one synchronous/blocking invocation that keeps control inside the tool until process exit; redirect stdout/stderr to an OS temp file and preserve the real exit code.
- Wall-clock duration alone is not a reason to hand off: a focused test/check may run for minutes when the runtime can block without model turns.
- While the blocking call is live, do not `tail -f`, reopen logs, request progress, or poll merely to observe liveness.
- If the runtime unexpectedly returns a live-process handle before exit, do not start a polling loop. Use a native long blocking wait when available; if completion would require repeated model turns, hand off unless the user explicitly delegated supervision.
- For explicitly delegated supervision, poll only as coarsely/status-only as the runtime requires and read diagnostics only after exit/failure. Interactive/watch-mode commands remain user-owned unless interaction itself is the task.

## Verification ownership and handoff

- Use the narrowest evidence that can falsify the change first; existing repository/task gates stay authoritative.
- Agent-owned by default: individual/focused tests (prefer `--tests`), narrow compile/diagnostic checks, and similarly bounded-scope checks with bounded output.
- User-owned by default: unfiltered module/full regression, cross-module gates, Detekt/lint, `verify*.sh`, connected/device tests, benchmarks/profiling, and physical-device acceptance.
- Do not auto-run a user-owned gate merely because a plan lists it; provide the exact command and run it only when explicitly delegated back.
- If required user-owned gates remain, label chat handoff `READY FOR USER VERIFICATION`; do not claim `VERIFIED`, `CLOSED`, or accepted before reviewing their evidence. Canonical docs retain existing verification-open vocabulary.
- Accept concise PASS summaries for user-run gates; on failure inspect the first useful diagnostic region and request more only when needed.
- For chatty agent-owned commands, redirect full stdout/stderr to an OS temp file outside the repo while preserving the real exit code.
- On success, emit only a concise PASS/exit summary plus material counts/timing when useful; do not reopen the full log.
- On failure, inspect a bounded failure slice first; expand the saved log only when insufficient for root-cause diagnosis.
- Do not start with `--info`, `--debug`, `--stacktrace`, or similar verbosity; escalate narrowly. Re-run the smallest failing/focused regression set after edits.
- A checked plan item is not implementation proof; implementation is not checkpoint acceptance. Never infer `PASS` for historical `NOT RUN`/`FAIL`.
- Handoff persistence is automatic for checkpoint-governed work: every stop updates the existing owner; never invent a checkpoint or duplicate handoff/status file. Roadmap changes only when its routing pointer changes.
- New sessions resume from canonical files, not chat history. Do not restate long plans/specs/source/logs; report delta, evidence, risk, remaining gates, resume boundary.
