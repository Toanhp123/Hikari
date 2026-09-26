# Hikari Documentation

This directory is the project knowledge base. It is intentionally **progressive**: read the smallest set of documents that can answer the current task instead of loading everything.

`CLAUDE.md` at the repository root contains agent routing and completion rules. It does not replace the documents below.

## Documentation map

| Document | Canonical responsibility | Read when |
| --- | --- | --- |
| `PROJECT_OVERVIEW.md` | What Hikari is, product scope, capabilities, high-level architecture principles, phase-level direction, decided vs undecided areas | Product, architecture, feature-scope, or high-level direction discussion |
| `GIT_WORKFLOW.md` | Branches, commits, merge policy, verification, patch/diff rules | Git, branch, commit, PR, merge, or task closure |
| `architecture/LOCAL_MEDIA.md` | Android local scan, classification, playback/readers and device checks | Working on the local-media walking skeleton |
| `architecture/REMOTE_MANGA.md` | Remote manga hierarchy, MangaDex transport and stable identity | Working on search, chapter selection or remote page delivery |
| `architecture/USER_STATE.md` | Progress, independent Library snapshots, SQLite schema and reader resume | Working on persisted user state |
| `decisions/ADR-*.md` | An accepted decision and why it was chosen | A task touches or questions that decision |
| `roadmap/*.md` | Detailed execution status, milestones, and implementation sequencing once active tracking needs its own document | Planning or tracking implementation |

Architecture and roadmap documents are created only when real content requires them. Accepted cross-cutting decisions already live under `decisions/`.

## Reading strategy

Do not read the entire `docs/` tree at the start of every task.

Use this sequence:

```text
task
 ↓
identify scope
 ↓
read the canonical doc for that scope
 ↓
read related ADR/subsystem docs if they exist
 ↓
inspect targeted code/tests
 ↓
expand context only when evidence requires it
```

Examples:

```text
"Should Hikari support another media type?"
→ PROJECT_OVERVIEW.md

"How should provider identity work?"
→ PROJECT_OVERVIEW.md
→ relevant domain/provider architecture docs
→ related ADRs

"Fix a player regression"
→ relevant player docs/ADRs
→ player code/tests

"Create a feature branch"
→ GIT_WORKFLOW.md
```

## Where knowledge belongs

Use **one fact, one canonical home**.

### Product overview

`PROJECT_OVERVIEW.md` owns:

- product identity and goals;
- platform targets;
- major capability groups;
- high-level architecture invariants;
- phase-level direction;
- decided vs undecided areas.

It should not become an API reference or implementation plan.

### Architecture docs

`architecture/` owns stable subsystem design:

```text
architecture/
├── DOMAIN.md
├── PERSISTENCE.md
├── PROVIDERS.md
├── PLAYER.md
└── READERS.md
```

Create a file only when that subsystem reaches a phase where its architecture is real enough to document.

Architecture docs answer **how the subsystem works now**.

### ADRs

`decisions/` owns important decisions that are expensive to rediscover or reverse.

An ADR should capture:

```text
context
decision
alternatives considered
consequences
status
```

ADRs answer **why this choice was made**. They should not duplicate the full subsystem design.

### Roadmap docs

`roadmap/` owns implementation sequencing and current execution state.

Roadmap documents may change frequently. They should not become architectural authority. Keep stable product intent in `PROJECT_OVERVIEW.md`; move detailed sequencing and live execution status here once that information becomes substantial enough to justify a separate document.

### Workflow docs

Workflow documents describe **how contributors/agents work**, not how Hikari's product behaves.

`GIT_WORKFLOW.md` is currently the canonical workflow document.

## Documentation hygiene

When editing docs:

- prefer links/references over duplicated paragraphs;
- keep current-state statements separate from future ideas;
- mark undecided choices explicitly instead of presenting candidates as decisions;
- update stale documentation in the same change that makes it stale;
- do not create empty directories or placeholder documents “for later”;
- do not move implementation details into `PROJECT_OVERVIEW.md`;
- record major accepted technical choices as ADRs;
- delete obsolete guidance rather than keeping contradictory versions.

If two documents appear to own the same fact, choose one canonical location and make the other point to it.

## Agent and skill workflow

Repository instructions should stay small. Task procedures belong to installed skills when those skills already solve the problem.

Preferred routing when available:

```text
development process     → Superpowers
simplicity/YAGNI        → Ponytail
large codebase mapping  → Graphify
UI/UX/design system     → UI UX Pro Max
current web research    → Firecrawl
```

Do not load every skill for every task. Invoke only relevant skills, and do not recreate installed skills inside the repository.

See root `CLAUDE.md` for the complete routing and Definition of Done.

## Current structure

```text
Hikari/
├── CLAUDE.md
└── docs/
    ├── README.md
    ├── PROJECT_OVERVIEW.md
    ├── GIT_WORKFLOW.md
    ├── architecture/
    │   ├── LOCAL_MEDIA.md
    │   ├── USER_STATE.md
    │   └── REMOTE_MANGA.md
    ├── roadmap/
    │   └── REMOTE_MANGA_PLAN.md
    └── decisions/
        ├── ADR-001-hybrid-layered-architecture.md
        ├── ADR-002-foundation-v1.md
        ├── ADR-003-architecture-guardrails.md
        └── ADR-004-user-state-persistence.md
```

Future `architecture/` and `roadmap/` documents are still created incrementally, only when the project has real knowledge to store.
