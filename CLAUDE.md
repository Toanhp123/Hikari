# Hikari — Claude Code Instructions

Hikari is a Flutter media hub for movies, series, anime, manga/comics/webtoon, and novels.

Primary targets: Android, Windows, iOS. Android is the first-priority platform.

This file is a **small always-on routing layer**, not the project specification or a copy of installed skill documentation. Keep it concise. Read only the project documents and skills needed for the current task.

## Source of truth

Use these files by scope:

- `docs/PROJECT_OVERVIEW.md` — product identity, scope, capabilities, architecture principles, roadmap direction, decided vs undecided items.
- `docs/AGENT_WORKFLOW.md` — how Hikari composes Superpowers, Ponytail, Graphify, Firecrawl, and UI UX Pro Max for non-trivial work.
- `docs/GIT_WORKFLOW.md` — branches, commits, merge policy, verification, patch/diff rules.
- `docs/README.md` — documentation map and ownership rules.
- `docs/architecture/` — current subsystem architecture.
- `docs/decisions/` — accepted architectural decisions and rationale.
- `docs/roadmap/` — active execution/status documents when they exist.

Do **not** read every document by default.

If documentation and implementation disagree, identify the conflict instead of silently choosing one. Determine whether the document describes intended state or current state before changing either side.

## Core operating rule

For non-trivial work, use this loop:

```text
understand → define success → choose workflow → self-review design/plan → execute E2E → verify → review diff
```

### Autonomous E2E default

A user request to implement, fix, refactor, or otherwise change the project is standing authorization to carry that requested scope through investigation, design, planning, implementation, review, and verification in the same task. Do **not** stop merely to ask the user to approve a design, spec, implementation plan, or execution method.

When an installed skill requires an intermediate human approval/checkpoint, replace that checkpoint with an internal self-review against the request, task contract, repository evidence, and project constraints, then continue immediately. This is the user's project-level preference and overrides Superpowers' default approval pauses for Hikari. Do not claim that the user approved an artifact they did not review; record it only as an autonomous/self-review gate.

Pause for the user only when:

- the user explicitly requested plan/design/review only or explicitly asked to approve before implementation;
- required information or credentials cannot be derived from the repository or available tools;
- an operation is destructive/irreversible or changes external state beyond the requested scope;
- project policy requires a Git mutation or other action that the user has not authorized.

Before editing:

1. Resolve uncertainty from repository evidence first: relevant docs, code, tests, history, and existing graph data.
2. Ask the user only when ambiguity materially changes behavior and cannot be resolved from available evidence.
3. State or infer concrete success criteria that can be checked after the change.
4. Keep the requested scope as the boundary. Do not add adjacent cleanup, features, abstractions, or dependencies unless required by the task.

Every changed line should trace to the requested outcome or to cleanup directly caused by that change.

## Skill orchestration

Hikari intentionally uses five installed skill families:

- **Superpowers** — development process and task workflow;
- **Ponytail** — YAGNI, scope control, dependency restraint, and simplification;
- **Graphify** — internal repository structure, dependency/call-flow, and blast-radius navigation;
- **Firecrawl** — external technical, upstream, documentation, and multi-source research;
- **UI UX Pro Max** — UI/UX design evidence and interface review.

Do **not** invoke all five by default. Invoke only the skills that materially apply, and let the active skill own its internal procedure instead of duplicating it here.

For non-trivial code, design, refactor, or debugging tasks, read `docs/AGENT_WORKFLOW.md` before making edits.

### Precedence

When multiple skills apply, use this order:

1. **Superpowers process skill** chooses how the task is approached (`brainstorming`, `systematic-debugging`, planning, etc.).
2. **Graphify, Firecrawl, and/or UI UX Pro Max** provide specialized repository, external, or interface evidence when the task needs them.
3. **Ponytail** constrains the proposed solution before implementation.
4. **Superpowers execution/TDD** carries out the self-reviewed change without an approval pause.
5. **Review + verification** close the task; Ponytail may run again as a simplification pass after correctness is established.

### Evidence routing gates

Use the specialized evidence route before generic fallback tools when its trigger is met:

- when a current Graphify graph exists and the task involves cross-cutting ownership, dependency/call-flow, architecture, removal of a shared concept, or blast-radius analysis, run a scoped Graphify query before repeated broad `Grep`/`Glob`/raw-file exploration; then verify consequential conclusions in source and tests;
- when the task needs current external technical/upstream evidence or multi-source web research, use Firecrawl first; prefer developer search for developer evidence, search for discovery, scrape for a known URL, map + scrape for a known site with an unknown page, and crawl only when multiple pages are actually needed;
- direct targeted `Read`/`Grep` is preferred when the exact owning file/symbol is already known and the task is localized;
- native web search/fetch is a fallback when Firecrawl is unavailable, fails, or a trivial known-page read is materially simpler. Do not duplicate retrieval when Firecrawl already returned sufficient evidence.

Project/user constraints override optional skill advice. In particular, Hikari's autonomous E2E policy overrides Superpowers approval/checkpoint pauses, while Git and destructive-action restrictions remain binding. Do not let a skill silently create branches, worktrees, commits, pushes, merges, or persistent skill-generated artifacts that are not part of the requested deliverable. A new dependency may be added autonomously only when a verified requirement needs it, Ponytail finds no simpler existing option, and the change is documented and verified.

## Context loading

Use progressive disclosure:

1. Read the user request and identify the exact scope.
2. Read `docs/README.md` only when document routing is unclear.
3. Read the canonical document for the affected subsystem or decision.
4. Inspect the smallest relevant code/tests.
5. Expand context only when evidence requires it.

Examples:

- product scope or architecture discussion → `docs/PROJECT_OVERVIEW.md` + relevant architecture/ADR docs;
- Git/branch/commit/PR work → `docs/GIT_WORKFLOW.md`;
- source/extension work → relevant source/extension architecture docs + ADRs;
- UI work → relevant product/presentation docs + UI UX Pro Max;
- broad dependency/call-flow or blast-radius question → Graphify first when a usable graph exists, then targeted source files;
- external technical/upstream or multi-source research → Firecrawl first, then inspect only the sources needed for the decision;
- localized change with obvious ownership → targeted files/tests only; do not build a graph or load unrelated docs.

Repository source and tests remain authoritative for current behavior. Graphs, summaries, generated recommendations, and chat history are navigation aids, not substitutes for verifying the actual files.

## Implementation rules

- Follow existing project boundaries before introducing new ones.
- Do not add speculative abstractions, dependencies, configuration, compatibility layers, or scaffolding.
- Prefer existing project code/patterns, then Dart/Flutter/platform capabilities, then already-installed dependencies; add a new mechanism only when a verified requirement needs it.
- Do not make unrelated refactors inside a focused task.
- Keep domain/business logic independent from UI, storage, network, and platform implementations.
- Treat architecture guard failures as boundary failures; fix the dependency or update the accepted ADR and guard together when an exception is real.
- Keep provider-specific details out of UI and normalized domain contracts.
- Keep database models separate from domain models.
- Isolate platform-specific code behind explicit boundaries.
- When a major technical choice becomes accepted, record it in an ADR rather than burying the rationale in code comments or chat.

Do not simplify away correctness, validation, security, accessibility, data safety, or an explicit requirement.

## Documentation rules

Use **one fact, one canonical home**.

- product purpose/scope → `PROJECT_OVERVIEW.md`;
- agent orchestration → `AGENT_WORKFLOW.md`;
- Git/process policy → `GIT_WORKFLOW.md`;
- subsystem design → `docs/architecture/`;
- important accepted choice + rationale → `docs/decisions/`;
- execution/status → `docs/roadmap/`.

Link to canonical information instead of duplicating it. Update documentation in the same change when the change invalidates an existing statement. Do not create placeholder documents or empty documentation trees for future work.

## Definition of Done

Before saying a code/file task is complete:

1. Self-review the final diff for scope, correctness, duplication, unnecessary complexity, and accidental behavior changes.
2. For a substantial code/refactor diff, run a Ponytail simplification review **after** correctness review; accept cuts only when behavior and required safeguards remain intact.
3. Invoke Superpowers `verification-before-completion` when available and run the smallest fresh verification that proves the claim.
4. For Flutter code, use the project-pinned SDK (`fvm flutter ...` / `fvm dart ...`) and normally include analyze plus relevant tests; add platform build/run checks when the changed scope requires them.
5. Prefer `tool/check.ps1` on Windows for the Foundation v1.1 baseline, and run `git diff --check` when reviewing the final change.
6. Update affected docs/ADR when behavior, architecture, or a recorded decision changed.
7. Confirm no secrets, generated build output, local caches, or unrelated edits entered the change.
8. Produce a patch/diff for every code or file change.
9. Report verification evidence and any remaining unverified platform/constraint explicitly.

Never claim “done”, “fixed”, “passes”, or “ready to merge” without fresh verification evidence.

## Git

Follow `docs/GIT_WORKFLOW.md`.

Do not commit directly to `main` or `dev` for normal feature work.

Do not create/switch/delete branches, create worktrees, commit, push, merge, or rewrite history unless the user has asked for that action.

Every code/file modification must be reviewable as a diff/patch.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
