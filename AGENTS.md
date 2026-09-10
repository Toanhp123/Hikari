# Hikari Agent Guide

## Purpose

Compact routing rules for agentic work. This file is a map, not a roadmap, architecture ledger, module list, or status database. Canonical repository sources outrank copied status.

## Stable repository facts

- Android/Kotlin multi-module repository with modular-monolith / Clean-style capability boundaries.
- Modules: `settings.gradle.kts`; dependency edges: `config/architecture/module-boundaries.json`; documentation precedence: `docs/project/document-governance.md`.
- Do not copy fast-changing task, wave, schema, module-count, or verification status here.

## Resolve scope

- Direct user instructions and an explicitly named task/plan/file define initial scope.
- If `Task N` is named, use it directly. On continue/resume also read its owning checkpoint/open-work slice.
- Use `docs/implementation/current-roadmap.md` only when routing is ambiguous; then read only `## Current position` far enough to resolve active task, checkpoint, and owning plan.
- Freeze the resolved canonical `Task N` for this turn. Later checkpoint/roadmap updates never authorize `Task N+1` in the same turn.

## Hard bootstrap context budget

Before the first edit or focused RED/diagnostic command, read only:

1. this `AGENTS.md`;
2. active checkpoint resume/open-work slice;
3. materially relevant owning-plan global constraints;
4. active `Task N` section only;
5. affected production/tests/scripts plus immediate callers/callees.

Rules:

- Locate headings/symbols first (`rg -n`, exact path/symbol search), then read bounded ranges. Do not open whole large plan/spec/checkpoint files when a slice is enough.
- On `continue` within the same `Task N`, the checkpoint's exact resume boundary is authoritative. Do not reconstruct the task from the beginning unless it conflicts with the working tree or omits a required invariant.
- Do not reread completed tasks/waves for confidence; recursively read `docs/`; scan every module; inspect history/archive; perform broad architecture re-audits; or spawn speculative subagents before first edit/RED.
- If the contract is sufficient, begin implementation/RED immediately. Expand context only for a concrete missing invariant, failure, dependency trail, ownership question, or contradiction.
- Expansion is allowed when evidence requires it, but only to the necessary authority; state why.
- Ignore `.git/`, `.gradle/`, `**/build/`, generated outputs, caches, and binaries unless required by evidence.
- Never paste/restate long plans, specs, sources, or logs into chat/subagent prompts. Carry only active invariants, delta, evidence, risks, and resume boundary.

## Canonical source precedence

- Implemented now: `docs/project/current-state.md`, then code/tests.
- Next work: active checkpoint/owning plan; roadmap is routing only.
- Product/domain scope: `docs/project/approved-product-design.md`.
- Gate evidence: named record under `docs/internal/checkpoints/`.
- Public plugin behavior: `docs/plugin-sdk/`, `:plugins:api`, tested fixtures.
- Historical provenance: `docs/internal/archive/` only when materially required.
- README/handbook prose is navigation only and never overrides canonical state/task authority.

## Skill routing

- Approved task/plan/checkpoint execution is implementation, not a new design cycle.
- Do not brainstorm merely to continue/resume/implement/test/review/fix an already-defined contract.
- Brainstorm only for a new feature/design/architecture/public-interface decision, explicit user request, or load-bearing ambiguity/contradiction that invalidates the contract.
- Compile/test failures and root-cause work do not by themselves reopen design. Do not regenerate an existing approved spec/plan.
- Relevant execution skills such as debugging/TDD/review still apply.

## Implementation workflow

1. Resolve exact `Task N` + resume boundary.
2. Build the smallest dependency cone that can explain the behavior.
3. Characterize/reproduce first when TDD/regression evidence is required.
4. Implement the smallest ownership-correct change.
5. Run focused agent-owned RED/GREEN/compile/diagnostic checks only.
6. Self-review the changed cone; fix in-scope findings.
7. Hand user-owned broad/device/performance gates back with exact commands unless explicitly delegated.
8. At `Task N` completion, update the existing checkpoint with delta, evidence, open gates, risks, and exact resume boundary. Update roadmap only when its routing pointer/verification state changes.
9. Stop. A persisted `Task N+1` pointer is not authorization to start it.

## Self-review contract

Inside the changed cone plus justified root-cause expansion, check:

- correctness, failure semantics, regressions;
- module/package/ownership boundaries;
- duplicate/dead/legacy/wrapper/circular/unnecessary indirection exposed by the change;
- complexity and semantic-scope -> physical-work amplification;
- concurrency, cancellation, lifecycle, invalidation, failure lifetime;
- test gaps, fixture realism, test-only/release leakage;
- contradictions with active plan/design/checkpoint/canonical state.

Do not turn normal task self-review into a whole-repo audit unless explicitly requested or evidence requires it.

## Subagent budget

- Default: no delegation; keep one sequential cone in the root agent.
- Parallel work requires at least two genuinely independent workstreams with material benefit. Bounded parent default maximum: 2 total subagent workstreams, reviewers included.
- No nested agents unless explicitly requested. Do not delegate routine testing, summarization, status, ordinary self-review, or duplicate/best-of-N attempts.
- Before spawning, define independent scope, expected benefit, exact paths/context, and output contract; otherwise stay single-agent.
- Give subagents narrow ephemeral file-based briefs. They inherit this context/test budget and must not rediscover roadmap/current work. Prefer a terse report file + completion signal; root reads only needed slices.
- Use the cheapest adequate child profile; reserve strongest reasoning for consequential architecture/concurrency/security/cross-module review. If child profile cannot be verified and budget matters, stay single-agent.

## Command / polling budget

- Agent-owned non-interactive commands use one synchronous/blocking invocation until exit, with stdout/stderr redirected to an OS temp file and real exit code preserved.
- Wall-clock duration alone does not change ownership. While blocked, do not `tail -f`, reopen logs, request progress, or poll liveness.
- If a live-process handle returns unexpectedly, use one native long blocking wait when available. Never create repeated model-turn polling loops; hand off if repeated polling would be required unless supervision was explicitly delegated.
- Interactive/watch-mode commands remain user-owned unless interaction itself is the task.
- Successful command handling: command + exit/PASS + material counts/timing only; do not reopen full logs. Failure handling: inspect the first useful bounded diagnostic slice, expanding only when insufficient.
- Do not begin with `--info`, `--debug`, `--stacktrace`, or equivalent verbosity; escalate narrowly.

## Verification ownership / handoff

- Use the narrowest evidence that can falsify the change first; repository/task gates remain authoritative.
- Agent-owned by default: individual/focused tests (`--tests` where practical), narrow compile/diagnostic checks, and similarly bounded checks.
- User-owned by default: unfiltered module/full regression, cross-module gates, Detekt/lint, `verify*.sh`, connected/device tests, benchmarks/profiling, physical-device acceptance.
- Never auto-run a user-owned gate merely because a plan lists it; run only when explicitly delegated back.
- If required user-owned gates remain, hand off as `READY FOR USER VERIFICATION`. Do not claim `VERIFIED`, `CLOSED`, or accepted before reviewing returned evidence.
- Accept concise PASS summaries for user-run gates. On failure request/inspect only the first useful diagnostic region, expanding as needed.
- Re-run the smallest affected regression set after edits. A checked plan item is not implementation proof; implementation is not checkpoint acceptance; never infer PASS for historical `NOT RUN`/`FAIL`.
- Checkpoint-governed handoff always updates the existing owner; never invent duplicate status/checkpoint files.
- New sessions resume from canonical files, not chat history. Report only delta, evidence, risks, remaining gates, and exact resume boundary.
