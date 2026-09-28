# Hikari Agent Workflow

> Status: **Project standard**
>
> Purpose: define how Hikari composes its installed Claude Code skills without copying their internal instructions into project documentation.

Installed skills remain the authority for their internal procedures **except where this project document explicitly overrides a checkpoint or side effect**. This document defines routing, ordering, interaction, and Hikari-specific constraints.

## 1. Operating model

Hikari uses four complementary skill families:

| Skill family | Role in Hikari | Use when | Skip when |
| --- | --- | --- | --- |
| **Superpowers** | Primary process framework | feature design, debugging, planning, TDD, execution, review, verification, branch completion | no matching process skill materially applies |
| **Graphify** | Structural/codebase map | architecture, dependency/call-flow, broad refactors, unfamiliar cross-cutting code | small localized task already understood from targeted files |
| **UI UX Pro Max** | Design/UX evidence | screen/component design, visual hierarchy, interaction, accessibility, responsive/adaptive behavior | pure domain, persistence, networking, tooling, non-visual work |
| **Ponytail** | Complexity and scope gate | code changes, refactors, architecture/dependency choices, simplification review | a no-code task where complexity control is irrelevant |

The skills are **not peers to run in parallel by default**. Treat them as layers in one workflow.

```text
Superpowers process
        ↓
context/domain evidence
(Graphify / UI UX Pro Max when relevant)
        ↓
Ponytail scope + complexity gate
        ↓
implementation / execution
        ↓
correctness review
        ↓
Ponytail simplification review when useful
        ↓
Superpowers verification-before-completion
```

## 2. Autonomous E2E execution is the default

For Hikari, an implementation/fix/refactor request authorizes the full requested workflow in one run:

```text
investigate
    ↓
design / decide when needed
    ↓
self-review gate
    ↓
plan when useful
    ↓
self-review gate
    ↓
implement
    ↓
review / simplify
    ↓
verify
    ↓
report
```

Do not stop for routine design, spec, plan, or execution-mode approval. A concise status update or plan may be shown to keep the user informed, but it is **not** a request for permission and execution continues in the same task.

This is a deliberate Hikari override of Superpowers' default human-approval gates. When a Superpowers skill says `STOP`, wait for design/spec/plan approval, or ask the user to choose an execution method, translate that checkpoint into an **autonomous gate**:

1. check the artifact against the user's request and task contract;
2. check important assumptions against repository evidence;
3. apply Ponytail/YAGNI and project architecture constraints;
4. correct the artifact if necessary;
5. choose the execution method that best fits the task and continue immediately.

Do not fabricate user approval. If reporting the gate, call it `self-reviewed`, `autonomous gate passed`, or equivalent.

### When to pause

Pause only when at least one of these is true:

- the user explicitly asked for plan/design/review only or asked to approve before implementation;
- material requirements remain ambiguous after inspecting available docs, code, tests, history, and relevant tools;
- a required credential, secret, account permission, device interaction, or other human-only input is missing;
- the next action is destructive/irreversible or would change external state beyond the requested scope;
- proceeding requires a Git mutation that `docs/GIT_WORKFLOW.md` does not authorize for the current request.

Do not pause merely because the task is architectural, multi-file, or because a skill normally contains a human checkpoint. In those cases, make the smallest defensible decision from evidence, record consequential architecture decisions in the appropriate ADR, and continue.

## 3. Start every non-trivial task with a task contract

Before changing files, establish four things from the request and repository evidence:

1. **Outcome** — what observable behavior, structure, or document state should change?
2. **Scope** — which subsystem/files are expected to be involved, and what is explicitly out of scope?
3. **Constraints** — architecture rules, platform limits, existing decisions, Git restrictions, compatibility requirements.
4. **Proof** — what test, analyzer, build, inspection, or diff evidence will demonstrate success?

Do not turn this into a long ceremony. For a straightforward task, this may be a few internal checks. For architecture or multi-file work, make it explicit enough that implementation can be verified against it.

Investigate before asking the user to restate information already available in the repository. Ask only when unresolved ambiguity would change the result materially.

## 4. Superpowers is the process owner

Use Superpowers to select the task process before implementation. The active Superpowers skill controls its own steps; Hikari only adds project constraints.

Typical routing:

| Task | Primary Superpowers route |
| --- | --- |
| New behavior / substantial design | `brainstorming` → `writing-plans` when multi-step → appropriate execution skill |
| Bug / unexpected behavior | `systematic-debugging` → regression test / fix workflow |
| Multi-step implementation | `writing-plans` → autonomous plan review → `executing-plans` or `subagent-driven-development` as appropriate |
| Implementation | `test-driven-development` where behavior can be expressed by tests |
| Significant completed change | `requesting-code-review` |
| Before completion claim | `verification-before-completion` |
| Branch/integration decision explicitly requested by the user | `finishing-a-development-branch` |

### Hikari constraints on Superpowers

- **Approval gates are autonomous in Hikari.** Do not wait for the user after brainstorming design, written spec, implementation plan, or execution-choice handoff unless a pause condition from section 2 applies. Self-review and continue.
- When Superpowers asks the user to choose between execution modes, choose automatically. **Default to native/inline execution** in the current working tree because it preserves one coherent context and does not require extra Git lifecycle. Use subagent-driven implementation only when its isolation materially improves quality **and** it can obey Hikari's Git restrictions; read-only exploration/review subagents remain fine when useful.
- Do not create a branch, worktree, commit, push, merge, or other Git mutation unless the user asked for it. `docs/GIT_WORKFLOW.md` remains authoritative. If a Superpowers path assumes such a mutation, preserve its planning/TDD/review intent but execute without that mutation.
- Do not create a second plan merely because another planning surface exists. One current implementation plan is enough.
- Treat Superpowers design/spec/plan files as **working artifacts**, not automatically as Hikari documentation. Do not commit them automatically. Before finalizing, remove transient planning artifacts from the requested patch unless they have durable project value; durable architecture rationale belongs in the relevant canonical doc/ADR.
- Do not stop just because hidden complexity upgrades a Superpowers path. Reclassify, update the design/plan, self-review the new artifact, and keep going unless the new information triggers a real pause condition.
- A review skill does not replace verification. A verification skill does not replace review.

## 5. Graphify is a map, not evidence of behavior

Use Graphify before broad raw-file exploration when the task needs architectural or cross-cutting understanding.

### Use Graphify when

- ownership is unclear across several directories;
- tracing dependencies, call flow, or central nodes;
- planning a broad refactor or architecture change;
- understanding an unfamiliar subsystem where grep-by-keyword would produce noisy context;
- checking blast radius before moving/removing a shared abstraction.

### Graph-first behavior

If `graphify-out/GRAPH_REPORT.md` or an equivalent current graph exists and the task is cross-cutting:

1. read/query the graph first;
2. use it to identify the smallest set of likely source files;
3. verify important conclusions in the actual code/tests before editing.

If the graph is stale relative to relevant working-tree changes, update it before relying on it for an architecture conclusion. Do not rebuild a graph for a one-file/localized task solely to satisfy process.

Graphify output can contain inferred relationships. Treat it as navigation and hypothesis generation; source code, tests, and accepted docs remain authoritative.

Do not add Graphify hooks or generated graph artifacts to the project as a side effect of an unrelated task.

## 6. UI UX Pro Max is design evidence, not a replacement for Hikari context

Use UI UX Pro Max when the task changes how Hikari looks, feels, moves, or is operated.

Before invoking it for existing UI, inspect the current screen/component and relevant presentation/product docs so recommendations fit Hikari instead of producing a generic design.

### New screen or major redesign

Use the skill's design-system workflow to establish coherent direction, then combine it with:

- the product goal and media-consumption context;
- current Hikari visual patterns/components;
- Flutter/platform conventions;
- accessibility and touch interaction requirements.

Do **not** persist generated `design-system/` files or make them a new project source of truth unless the user explicitly wants Hikari to adopt that artifact.

### Existing screen/component

Prefer targeted searches/review for the actual problem (layout, typography, interaction, accessibility, navigation, etc.) over regenerating an entire design system.

After UI implementation, use UI UX Pro Max again when useful as a focused UX/accessibility/consistency review. Visual recommendations still require verification against the actual Flutter implementation and, when available, device/screenshot evidence.

## 7. Ponytail is the complexity gate

Ponytail protects Hikari from speculative code and architecture drift. Use it as a scope/complexity constraint, not as permission to weaken required behavior.

Before implementation, challenge the proposed solution in this order:

```text
required at all?
    ↓
already solved in Hikari?
    ↓
Dart / Flutter / platform already solves it?
    ↓
already-installed dependency solves it?
    ↓
minimum new code / abstraction that satisfies the verified requirement
```

Apply this to:

- new abstractions and interfaces;
- dependencies;
- configuration flags;
- compatibility/fallback layers;
- caching or background mechanisms;
- generalized frameworks for a single current use case.

Ponytail must not remove correctness, trust-boundary validation, error handling that prevents data loss, security, accessibility, required compatibility, or explicit product behavior.

### Ponytail review

For substantial diffs, refactors, or cleanup tasks, run a simplification review after correctness has been established. It answers only “what can be removed or made simpler?”

Do not use it as a substitute for normal code review. If simplification changes code after tests/review, rerun the relevant verification.

## 8. Task recipes

These recipes define orchestration only. Follow the invoked skill's current instructions for the detailed procedure.

### Feature or behavior change

```text
Superpowers brainstorming
        ↓
relevant docs + targeted code
        ↓
Graphify if cross-cutting
UI UX Pro Max if interface-facing
        ↓
Ponytail scope gate
        ↓
autonomous design/spec gate
        ↓
Superpowers writing-plans if multi-step
        ↓
autonomous plan review + execution-mode choice
        ↓
TDD / execution skill
        ↓
code review → optional Ponytail review
        ↓
verification-before-completion
```

### Bug or regression

```text
systematic-debugging
        ↓
Graphify only if root cause crosses boundaries
        ↓
reproduce / regression test
        ↓
Ponytail: smallest root-cause fix
        ↓
implement
        ↓
review + fresh verification
```

Do not brainstorm alternative features while a reproducible defect still lacks a root cause.

### Refactor / cleanup

```text
establish behavior + test baseline
        ↓
Graphify if broad/cross-cutting
        ↓
Ponytail: identify what should disappear, not what new framework to create
        ↓
plan if multi-step
        ↓
autonomous plan review
        ↓
small behavior-preserving changes
        ↓
review + Ponytail review
        ↓
verify before/after behavior
```

A refactor is not permission to clean unrelated code.

### UI / UX work

```text
brainstorming for non-trivial UX change
        ↓
inspect current Hikari UI + relevant docs
        ↓
UI UX Pro Max evidence
        ↓
Graphify only if state/navigation ownership is broad
        ↓
Ponytail scope gate
        ↓
plan when useful → autonomous review → implement
        ↓
UI UX Pro Max review when useful
        ↓
code review + verification
```

### Architecture / dependency decision

```text
brainstorming
        ↓
current docs + implementation evidence
        ↓
Graphify for structure/blast radius
        ↓
Ponytail for YAGNI/dependency challenge
        ↓
compare concrete tradeoffs
        ↓
record accepted decision in ADR if warranted
```

Do not implement a speculative architecture merely to make a future option easier.

### Documentation-only edit

For a localized documentation correction, use direct targeted inspection and self-review. Do not invoke code-oriented skills just to satisfy ceremony. Use the fuller workflow when documentation changes architecture, workflow policy, or project-wide agent behavior.

## 9. Keep context and tool use proportional

- Do not read the entire repository or `docs/` tree by default.
- Do not invoke Graphify when targeted code inspection is already sufficient.
- Do not run UI UX Pro Max for non-visual work.
- Do not create plans for obvious one-step edits unless a skill's active procedure requires one; if it does, keep the artifact minimal and do not turn it into an approval pause.
- Do not launch subagents merely because they are available.
- Do not repeat information already established in a current plan, ADR, or canonical document.
- Prefer fresh primary evidence over summaries when making a consequential claim.

The goal is not maximum process. The goal is the **smallest process that reliably produces a correct, reviewable result**.

## 10. Completion sequence

For a non-trivial file/code task, close in this order:

1. inspect the final diff against the task contract;
2. run correctness/code review appropriate to the scope;
3. run Ponytail simplification review when the diff is substantial or complexity was introduced;
4. if review changes files, rerun affected checks;
5. invoke Superpowers `verification-before-completion` and collect fresh evidence;
6. update stale docs/ADR caused by the change;
7. run `git diff --check` and check for unrelated/generated/secret files;
8. produce the required patch/diff;
9. report what was verified and what remains unverified.

A green test run does not excuse an out-of-scope diff, and a clean diff does not excuse missing verification.

## 11. When a skill is missing or fails

If a named skill is unavailable or cannot run:

- do not pretend it ran;
- do not silently vendor or recreate it inside Hikari;
- continue with repository evidence and the closest safe workflow when possible;
- state the limitation when it affects confidence or verification.

Create project-local `.claude/skills/`, agents, hooks, or commands only after a recurring Hikari-specific need is demonstrated and the installed skills do not already cover it.
