# Hikari Codex Context Harness — Phase 1 Design

Date: 2026-09-07
Status: **APPROVED — PHASE 1 IMPLEMENTATION AUTHORIZED**
Scope: repository instructions and documentation routing only; no production behavior changes.

## 1. Problem

Hikari has strong documentation governance but no repository-level agent instruction file. The
repository currently contains 227 files under `docs/` (~5.9 MiB), including large historical plans,
checkpoints, audit provenance, and superseded architecture records. A generic instruction such as
"read the project and the plan" therefore gives an agent no cheap way to distinguish the small
active execution cone from the historical documentation corpus.

The current active program is the Whole-App Performance Big Update v3. The canonical roadmap says
Wave 0 Task 2 has implementation present and its AGED physical-device baseline is still open. Some
entry documents still describe Wave 10 as the next work, so an agent that discovers documents by
recency or apparent prominence can consume unnecessary context and select stale execution state.

The goal is to reduce repeated context consumption without weakening Hikari's existing deep audit,
TDD, architectural boundary, or self-review standards.

## 2. Design principles

1. **Map, not manual.** Root `AGENTS.md` is a short router to authoritative files and workflows. It
   must not duplicate specs, roadmaps, architecture tables, or historical status.
2. **Question-based precedence.** Reuse `docs/project/document-governance.md`: current state,
   next work, product scope, gate evidence, and public plugin contracts each have a different source
   of truth.
3. **Narrow first, expand on evidence.** Start from the active checkpoint/task and inspect its direct
   production/test dependency cone. Broaden only when a concrete unresolved dependency,
   contradiction, or root-cause trail requires it.
4. **Historical evidence stays historical.** Never recursively scan archives, completed plans, old
   checkpoints, or superseded specs as default context. Read them only for a named provenance or
   contradiction question.
5. **Quality is not traded for tokens.** Root-cause analysis and self-review remain mandatory. The
   optimization is to constrain their semantic scope, not to skip them.
6. **Durable handoff beats long chats.** Resume from canonical roadmap + active checkpoint instead
   of relying on a long Codex thread as the project memory.

## 3. Phase 1 repository changes

Phase 1 contains only documentation/instruction changes:

- Create root `AGENTS.md` of approximately 80–120 lines.
- Update the stale current-work routing prose in:
  - `README.md`
  - `docs/README.md`
  - `docs/PROJECT-HANDBOOK.md` (current execution position plus stale current-status labels in the adjacent capability roadmap table only)
  - `docs/project/document-governance.md` (`"What is implemented next?"` plus its directly relevant Wave 10 supersession row only)
- Do not modify production Kotlin, Gradle architecture, benchmark behavior, tests, or historical
  evidence records.
- Do not add module-local `AGENTS.md` files in Phase 1.

## 4. Root `AGENTS.md` contract

### 4.1 Stable repository identity

The file may contain only compact, slow-changing facts needed before discovery:

- Android/Kotlin multi-module repository.
- Modular-monolith/Clean-style capability boundaries are enforced by repository policy/scripts.
- Current module truth comes from `settings.gradle.kts` and
  `config/architecture/module-boundaries.json`, not a duplicated module list in `AGENTS.md`.
- Documentation governance is authoritative for source precedence.

### 4.2 Documentation routing

For a normal task, an agent resolves documents in this order:

1. `AGENTS.md`.
2. The `Current position` section of `docs/implementation/current-roadmap.md` to identify the active
   program/task; do not read the entire roadmap by default.
3. The active checkpoint named by that section.
4. The owning task/wave implementation plan named by the checkpoint/roadmap: read its global
   constraints plus the active `Task N` section first, not the entire plan.
5. Only the relevant section(s) of the owning design/spec when the task needs an invariant,
   ownership decision, or acceptance criterion not already present in the plan/checkpoint.
6. Production code, tests, and scripts in the affected dependency cone.

Other sources are conditional:

- `docs/project/current-state.md` for "what is implemented now?" questions.
- `docs/project/approved-product-design.md` for product-scope questions.
- `docs/internal/checkpoints/` for named gate evidence only.
- `docs/plugin-sdk/` for public plugin contract questions.
- `docs/internal/archive/` only when explicitly required for provenance/supersession analysis.

### 4.3 Context-budget rules

Default agent behavior must explicitly forbid the main token-waste patterns:

- Do not recursively read `docs/`.
- Do not read every plan/spec/checkpoint "for completeness".
- Do not reopen completed waves/tasks unless a concrete regression or contradiction crosses that
  boundary.
- Do not reread the full roadmap, plan, or master design when a heading/range is enough; locate the
  active heading first and read a narrow slice, then expand only if the contract is incomplete.
- Do not enumerate or inspect every module before a bounded task.
- Do not paste or retain large successful build logs; retain command + result and inspect
  failure-relevant excerpts when a command fails.
- Do not treat README/handbook status prose as newer than canonical roadmap/current-state.

These are **default discovery limits**, not hard evidence bans. If a root-cause trail crosses the
initial cone, the agent must expand to the necessary callers, callees, storage/runtime boundary, or
historical authority and state why.

### 4.4 Implementation workflow

For an implementation task:

1. Resolve exact task and resume boundary from current roadmap/checkpoint.
2. Build an affected-file/dependency cone from the task contract and current code.
3. Prefer focused RED test or characterization evidence before changing behavior when the plan
   requires TDD.
4. Implement the smallest ownership-correct change that satisfies the task contract.
5. Run focused verification first; then affected module/architecture checks required by the task.
6. Run repository-wide/full/device gates only when the owning plan/checkpoint requires them.
7. Self-review the final changed dependency cone and fix discovered in-scope issues before closure.
8. Update the existing checkpoint/current roadmap according to governance; do not create ad-hoc
   duplicate status files.

### 4.5 Self-review contract

Self-review remains deep but bounded to the changed dependency cone plus any root-cause expansion:

- correctness and regression behavior;
- module/package/ownership boundary violations;
- duplicate, dead, legacy, wrapper, or unnecessary indirection introduced or exposed by the task;
- algorithmic/complexity regressions and semantic-scope -> physical-work expansion;
- concurrency, cancellation, lifecycle, invalidation, and failure-lifetime problems;
- test gaps and accidental test-only/release-path leakage;
- contradictions with the active plan/design/checkpoint.

An agent must not turn every per-task self-review into a fresh whole-repository audit. Whole-repo
red-team audit is a separate explicitly requested activity.

### 4.6 Verification/log discipline

Use the narrowest evidence that can falsify the current change first. Successful command reporting
should keep only command, exit result, and material summary. On failure, inspect the first useful
failure/stack/diagnostic region rather than ingesting the entire Gradle log. Existing canonical
verification scripts remain authoritative; `AGENTS.md` does not invent replacement gates.

## 5. Active-program resolution example

With the repository snapshot dated 2026-09-07, a "continue current performance task" session should
resolve to:

```text
AGENTS.md
  -> docs/implementation/current-roadmap.md
  -> docs/internal/checkpoints/whole-app-performance-big-update-wave-0-2026-09-07.md
  -> docs/superpowers/plans/2026-09-07-hikari-perf-wave-0-contracts-and-fixtures.md
  -> Wave 0 Task 2 production/test/benchmark dependency cone
  -> relevant design section only if Task 2/checkpoint leaves a contract unresolved
```

It must not automatically read Wave 1–8 plans, the entire master roadmap, all HES/CCE checkpoints,
or the whole performance audit bundle merely to resume the AGED Search fixture failure.

## 6. Entry-point stale-state repair

Current execution state must have one canonical route instead of being copied into multiple
prominent files.

- `README.md`: keep a short repository description and link to `current-state.md` and
  `current-roadmap.md`; remove/replace stale "Wave 10 ready to start" execution prose.
- `docs/README.md`: keep navigation links; route current execution only through `current-roadmap.md`
  rather than naming the active program/plan in this entry point.
- `docs/PROJECT-HANDBOOK.md`: keep stable orientation/architecture content; make the current
  execution section a concise canonical pointer rather than another long status ledger, and strip
  stale current-status labels from the adjacent capability roadmap table so it remains orientation only.
- `docs/project/document-governance.md`: preserve precedence policy without duplicating the active
  program/Wave/Task. Correct any current-interpretation/supersession row that presents historical work
  as future next-work, while preserving historical source labels and accepted evidence facts.

## 7. Non-goals

Phase 1 does not:

- optimize production performance;
- change any Wave 0 task implementation or acceptance requirement;
- alter architecture boundaries or module ownership;
- rewrite or delete historical docs/checkpoints;
- introduce an agent-specific status database;
- add module-local agent instructions;
- require a permanently short investigation when evidence proves the task is cross-cutting.

## 8. Phase 2 decision gate

Module-local `AGENTS.md` files are considered only after real usage of the root router shows one of
these problems:

- repeated module-specific instructions consume material context;
- agents repeatedly violate a module boundary despite the root contract;
- verification commands differ enough by subtree to justify scoped instructions.

If none is observed, keep one root file. Avoid instruction duplication by default.

## 9. Acceptance criteria

Phase 1 is accepted when:

1. Root `AGENTS.md` is concise and contains no duplicated roadmap/module ledger.
2. A fresh agent can identify the active Wave 0 Task 2 resume boundary by reading only the router,
   current roadmap, checkpoint, and owning Wave 0 plan.
3. The router explicitly prevents recursive docs/archive/history scans by default while allowing
   evidence-driven expansion.
4. TDD, architecture checks, root-cause analysis, and deep self-review remain mandatory where the
   owning task requires them.
5. Prominent entry points do not duplicate a fast-changing active program/Wave/Task and no longer
   claim historical Wave 10 work is the current next work.
6. Historical checkpoints/specs are not rewritten to make history look current.
7. No production or test code changes are present in the Phase 1 diff.
8. A final instruction self-review finds no contradictory source precedence, impossible gate, or
   rule that could hide a cross-boundary root cause.

## 10. Expected token-saving mechanism

No fixed percentage is promised without measuring Codex usage. The expected reduction comes from
eliminating repeated discovery of historical docs, full designs, completed waves, unrelated modules,
and verbose successful logs. Quality-critical expansion remains demand-driven, so a difficult root
cause may still require broad context when the evidence justifies it.

## 11. Phase 1.1 hardening amendment

Post-implementation red-team review found two remaining context hazards: `Current position` had grown
into a long mixed historical ledger with contradictory old Wave 10 status, and prominent entry points
still copied the active performance program/Wave. Phase 1.1 therefore tightens the same architecture:

- `Current position` must remain a compact current-only resume record; historical execution prose lives
  under a separately headed non-current section.
- Entry READMEs/handbook/governance must point to the current-work authority without copying the active
  program/Wave/Task.
- When a prompt directly names a task/plan and says continue/resume, the agent reads that task plus its
  owning checkpoint/resume record directly; it does not detour through the roadmap unless ownership is
  ambiguous.
- `.git/`, `.gradle/`, build outputs, generated artifacts, caches, and binaries are excluded from default
  discovery unless the task/evidence requires them.
- Handoffs summarize delta/evidence/open work instead of restating large source documents or logs.
- The earlier unreferenced proposed duplicate harness design is removed so there is one owned Phase 1
  design rather than two competing discovery targets.

These are context-routing refinements only; root-cause expansion, deep self-review, and explicit
whole-repository audits remain fully allowed.

## 12. Phase 1.2 verification-ownership amendment

Real Hikari verification commands can be slow and produce large Gradle/Android logs that add little value
during edit loops. Phase 1.2 therefore separates execution ownership without weakening acceptance:

- The agent owns narrow RED/GREEN tests, targeted test filters, and small compile/diagnostic checks needed to
  validate the edit while reasoning is still local.
- The user owns expensive gates by default: unfiltered module/full regression, cross-module gates, Detekt/lint
  sweeps, `verify*.sh`, connected/device tests, benchmarks/profiling, and physical-device acceptance.
- A plan may require those gates, but that requirement does not authorize the agent to run them automatically.
  At handoff the agent emits the exact remaining commands; the user may explicitly delegate any command back.
- `READY FOR USER VERIFICATION` is a chat/handoff label used only when required user-owned gates remain. Tasks
  with no such remaining gate do not need the label. Canonical roadmap/checkpoint vocabulary remains
  `Patched / verification open` / `Verification open` until required evidence is supplied and reviewed.
- Successful user-run gates may be reported concisely (command/result is enough). Failed gates should provide the
  first useful failure/stack/diagnostic region; full logs are requested only when diagnosis needs more context.
- The task cannot be represented as `VERIFIED`, `CLOSED`, or accepted until required user-owned evidence has been
  reviewed. This preserves Hikari's evidence discipline while removing repeated expensive/noisy agent runs.

This amendment changes only who executes expensive verification by default; it does not remove or relax any
required gate from the owning plan/checkpoint.

## 13. Phase 1.3 test-output sink amendment

Phase 1.2 moved expensive acceptance gates to the user, but focused Gradle/compile commands can still waste
context when their complete console output is streamed into an agent session. Phase 1.3 therefore makes noisy
agent-owned command output sink-first without weakening diagnostic evidence:

- Chatty agent-owned commands redirect complete stdout/stderr to an OS temporary file outside the repository and
  preserve the command's real exit status.
- A successful run reports only a concise PASS/exit summary plus material counts or timing when they matter; the saved
  successful log is not re-opened merely for completeness.
- A failed run reads the first bounded useful diagnostic slice (failure markers, compiler errors, or a short tail/context
  window), then expands the saved log only if root-cause diagnosis needs more evidence.
- Verbose modes such as `--info`, `--debug`, and `--stacktrace` are diagnostic escalation, not first-run defaults.
- After an edit, the agent re-runs the narrowest failing test plus directly relevant focused regressions rather than
  broadening automatically to a module/full suite.
- Temporary logs are ephemeral evidence, not repository artifacts, checkpoint evidence, or files to commit.

This amendment changes output transport only. It does not hide failures, alter required test ownership, relax any gate,
or prevent the agent from expanding diagnostics when the bounded failure slice is insufficient.

## 14. Phase 1.4 subagent-budget amendment

Multi-agent execution can improve throughput on genuinely independent work, but automatic delegation can
multiply context, duplicate repository discovery, and add review/test agents whose output gives little value
for a single Hikari dependency cone. Phase 1.4 therefore makes delegation explicitly budgeted:

- The root agent stays single-agent by default for a bounded or sequential task.
- Parallel implementation/exploration delegation requires at least two genuinely independent workstreams and a material quality or wall-clock benefit.
- For a bounded parent task, the default budget is two delegated workstreams total, whether concurrent or sequential.
  Higher fan-out requires explicit user direction or a justified whole-repository/red-team decomposition.
- Subagents may not recursively spawn further agents unless the user explicitly requests multi-level orchestration.
- Testing, summarization, routine self-review/status, and duplicate/best-of-N attempts on the same question are not standalone delegation reasons.
- Generic framework/skill/plan boilerplate that recommends or requires subagents is not independent justification;
  the repository budget controls unless a task-specific contract or the user explicitly requires broader delegation.
- Before spawning, the root agent must establish the independent workstream, expected benefit, and exact assigned
  context/paths; uncertainty in any of these defaults back to single-agent execution.
- A subagent receives a narrow ephemeral file-based brief, inherits the root context/test budgets, and must not
  rediscover current work from roadmap/history or read unrelated documentation merely for completeness.
- Prefer an ephemeral subagent report file plus a terse completion signal; the root reads only the required slices.
  Full logs, source dumps, and long plan restatements stay out of root context.
- A single independent reviewer is allowed only for high-risk architecture, concurrency, security, cross-module
  ownership, broad refactors, or explicit red-team requests; it still counts against the bounded-task budget.
- If delegation would duplicate more context than useful work, the root agent must not delegate.

This amendment changes orchestration cost policy only. It does not prohibit evidence-driven scope expansion,
explicit user-requested multi-agent work, or independent review where risk justifies the additional context.

## 15. Phase 1.5 subagent model/effort-routing amendment

Phase 1.4 limits when Hikari may delegate, but an allowed subagent can still waste compute if it silently
inherits an unnecessarily expensive parent profile or uses excessive reasoning for mechanical work. Phase 1.5
therefore budgets child capability as well as child count:

- Mechanical search, inventory, classification, and deterministic extraction prefer GPT-5.6 Luna at low effort.
- Normal bounded implementation/investigation prefers GPT-5.6 Terra at medium effort; multi-file integration,
  debugging, or medium-risk independent review may escalate to Terra high.
- Consequential architecture, concurrency, security, cross-module ownership, or red-team review prefers GPT-5.6
  Sol high. Sol `xhigh` is exceptional and `max` requires explicit user direction.
- A stronger model is not selected merely for reassurance. Escalation requires evidence that the current tier is
  insufficient or an independently high-risk review class.
- Dispatches request both child model and reasoning effort when supported. Requested settings are not treated as
  fact until runtime metadata/acknowledgement confirms them.
- For budget-sensitive delegation, if the runtime cannot prove the requested child profile, the root stays
  single-agent rather than silently inheriting a more expensive parent profile.
- Model names may evolve; the durable policy is cheapest adequate for mechanical work, balanced for ordinary
  implementation/investigation, and flagship only for consequential judgment.

This amendment changes delegation compute policy only. It does not increase the Phase 1.4 subagent-count budget,
relax context/test/verification ownership, or require delegation where single-agent execution is cheaper.

## 16. Phase 1.6 skill-routing amendment

Phase 1.5 budgets delegation cost, but a generic brainstorming hard-gate can still restart design discovery for
work whose contract, checkpoint, and implementation plan are already approved. That wastes context and can create
competing specs/plans. Phase 1.6 therefore routes skills by work state rather than by generic wording alone:

- Continuing, resuming, implementing, testing, reviewing, or fixing an approved task/plan/checkpoint is execution,
  not a new brainstorming cycle.
- Brainstorming is appropriate for a genuinely new feature/design/architecture/public-interface decision, an
  explicit user request, or a load-bearing ambiguity/contradiction that invalidates the existing contract.
- Ordinary regressions, compile/test failures, debugging, and root-cause investigation do not independently trigger
  brainstorming; they stay in the execution/debugging workflow unless they expose a new design decision.
- An approved canonical spec/plan is not regenerated merely because generic brainstorming/design ceremony is available.
  Repository skill routing controls that design trigger for already-owned work; execution-specific debugging/TDD skills
  remain applicable when their conditions are met.
- If implementation exposes an architectural contradiction that cannot be resolved from current authorities, preserve
  the existing task/resume state and enter brainstorming only for that newly discovered decision boundary.

This amendment changes skill dispatch only. It does not weaken design review for genuinely new architecture, suppress
root-cause expansion, or bypass explicit user requests to brainstorm/redesign.


## 17. Phase 1.7 durable-handoff enforcement amendment

Phase 1.6 routes execution correctly across skills, but thread-to-thread recovery is still vulnerable if an agent
interprets checkpoint/roadmap updates as optional housekeeping. Phase 1.7 makes stopping-boundary persistence
deterministic without adding another status artifact:

- Every stopping boundary for checkpoint-governed work updates the existing owning checkpoint with only durable facts:
  material delta, focused evidence, user-owned gates still open or supplied, unresolved risks/debt, and the exact resume boundary.
- `docs/implementation/current-roadmap.md` is updated only when its routing pointer materially changes: active task,
  verification state, or next-work pointer. Ordinary mid-task detail remains in the owning checkpoint.
- A task patched but awaiting required user-owned verification remains verification-open; it is not marked closed.
- After required supplied evidence is reviewed, closure/advance is persisted before the session ends when the active
  task or next-work pointer changes.
- The agent performs these updates automatically before final handoff; the user does not need to request a
  checkpoint/roadmap update. If no owning checkpoint exists, do not invent one unless governance/task contract requires it.
- No per-thread/session `handoff`, `summary`, or duplicate status document is created. New threads resume from the
  owning checkpoint plus the canonical roadmap pointer when the work is checkpoint-governed.

This amendment changes persistence discipline only. It does not broaden what is recorded, relax verification evidence,
or make the roadmap a transcript; checkpoint content remains concise and durable.

## 18. Phase 1.8 command-execution/polling-budget amendment

Phase 1.3 sinks command output, but token waste can still come from returning control to the model while a process is
alive and repeatedly polling/watching it. A 2026-09-07 runtime probe confirmed that the active Codex terminal can keep a
single blocking invocation open through process completion without periodic model turns, so Phase 1.8 treats **model-turn
supervision**, not wall-clock duration, as the controlled cost:

- Agent-owned non-interactive commands use one synchronous/blocking invocation that does not return control to the model
  until process exit, with stdout/stderr redirected to an OS temp file and the real exit code preserved.
- Command duration alone does not change verification ownership. Focused RED/GREEN tests or narrow diagnostics may run for
  minutes when the runtime remains blocked without model turns.
- While a blocking call is live, progress watching is forbidden: no `tail -f`, periodic log reopening, progress requests,
  or liveness polling.
- If the runtime unexpectedly returns a live-process handle before exit, the agent does not enter a polling loop. It uses
  a native long/blocking wait when available; if completion would require repeated model turns, the command is handed to
  the user unless the user explicitly delegated supervision.
- Explicitly delegated supervision uses the coarsest status-only polling the runtime requires and defers diagnostic log
  reading until exit/failure. Interactive/watch-mode commands remain user-owned unless interaction itself is the task.
- Phase 1.2 ownership remains authoritative: full regression, cross-module, device, benchmark, profiling, and acceptance
  gates stay user-owned by default for acceptance/governance reasons, not merely because they take a long time.

This amendment changes command-observation behavior only. It does not weaken focused RED/GREEN verification, alter gate
acceptance semantics, or hide diagnostics needed for root-cause analysis.
