# Hikari — Claude Code Instructions

Hikari is a Flutter media hub for movies, series, anime, manga/comics/webtoon, and novels.

Primary targets: Android, Windows, iOS. Android is the first-priority platform.

This file is a **small routing layer**, not the project specification. Keep it concise. Do not import long docs here with `@...`; read only the documents relevant to the current task.

## Source of truth

Use these files by scope:

- `docs/PROJECT_OVERVIEW.md` — product identity, scope, capabilities, architecture principles, roadmap, decided vs undecided items.
- `docs/GIT_WORKFLOW.md` — branches, commits, merge policy, verification, patch/diff rules.
- `docs/README.md` — documentation map and rules for where knowledge belongs.
- `docs/architecture/` — subsystem architecture, only when those documents exist.
- `docs/decisions/` — accepted ADRs, only when those documents exist.
- `docs/roadmap/` — execution/status documents, only when those documents exist.

Do **not** read every document by default.

If documentation and implementation disagree, identify the conflict instead of silently choosing one. Determine whether the document describes intended state or current state before changing either side.

## Skill-first policy

Prefer installed, maintained skills over inventing project-local procedures.

At the start of a task, identify the relevant installed skills and invoke only those that materially apply.

### Superpowers

Use Superpowers as the primary development-process framework when available.

Typical routing:

- feature, behavior, architecture, or substantial design change → `brainstorming`
- approved multi-step architectural work → `writing-plans`
- implementation of a feature or bugfix → `test-driven-development`
- bug, failure, or unexpected behavior → `systematic-debugging`
- substantial completed work → `requesting-code-review`
- before claiming completion → `verification-before-completion`
- completed branch / integration decision → `finishing-a-development-branch`

Follow the active Superpowers skill instead of reproducing its procedure in project docs.

### Ponytail

Use Ponytail for coding, refactoring, architecture choices, and dependency decisions.

Default principle:

> Choose the smallest solution that satisfies the verified requirement.

Prefer, in order:

1. no new mechanism when none is needed;
2. existing project code/patterns;
3. Dart/Flutter/platform standard capabilities;
4. already-installed dependencies;
5. a new dependency or abstraction only when justified.

Do not simplify away correctness, validation, security, accessibility, data safety, or an explicit requirement.

### Graphify

Use Graphify for **cross-cutting codebase understanding**, not for every small edit.

Use it when a task spans many files/subsystems, needs architecture/call-flow understanding, or raw file-by-file exploration would be wasteful.

If a current graph exists, query the graph before broad grep/file traversal. Rebuild/update the graph only when the task requires current structure.

Do not require Graphify for a small, already-localized change.

### UI UX Pro Max

Use UI UX Pro Max for UI/UX work when available:

- screen/layout design;
- design-system decisions;
- typography, spacing, visual hierarchy;
- accessibility and interaction review;
- Flutter-specific UI guidance.

Do not invoke it for domain, persistence, networking, or other non-UI work.

### Firecrawl

Default Hikari external technical research to Firecrawl when current or upstream evidence is needed. Search first, then read the important primary sources deeply before making architecture, dependency, or compatibility decisions.

Prefer:

- developer index for library/API behavior, errors, upstream issues, and fixes;
- search when the relevant URL is unknown;
- scrape when a specific URL is known;
- crawl only when information genuinely spans many pages.

Prefer primary sources and official documentation. Reuse fetched results instead of fetching the same source repeatedly.

Do not use web research to answer facts already established by the repository.

## Missing skills

If a named skill is unavailable:

- do not pretend it ran;
- do not silently recreate or vendor it into Hikari;
- state the limitation when it materially affects the task;
- use the closest available workflow only when safe.

Do not create `.claude/skills/` content unless the user explicitly asks for it or a genuinely Hikari-specific recurring workflow is proven to be missing from installed skills.

## Context loading

Before editing:

1. Read the user request and identify the exact scope.
2. Read `docs/README.md` when document routing is unclear.
3. Read only the relevant source-of-truth docs.
4. Inspect the smallest relevant part of the codebase.
5. Expand context only when evidence requires it.

Examples:

- product scope or architecture discussion → `docs/PROJECT_OVERVIEW.md`
- Git/branch/commit/PR work → `docs/GIT_WORKFLOW.md`
- domain work → overview + existing domain architecture/ADR files
- player work → overview + player architecture/ADRs
- UI work → overview + relevant UI/design docs + UI UX Pro Max
- upstream Flutter/package question → relevant local docs + Firecrawl
- broad dependency/call-flow question → Graphify, then targeted files

## Implementation rules

- Follow existing project boundaries before introducing new ones.
- Do not add speculative abstractions, dependencies, configuration, or scaffolding.
- Do not make unrelated refactors inside a focused task.
- Keep domain/business logic independent from UI, storage, network, and platform implementations.
- Treat architecture guard failures as boundary failures; fix the dependency or update the accepted ADR and guard together when an exception is real.
- Keep provider-specific details out of UI and normalized domain contracts.
- Keep database models separate from domain models.
- Isolate platform-specific code behind explicit boundaries.
- When a major technical choice becomes accepted, record it in an ADR rather than burying the rationale in code comments or chat.

## Documentation rules

Use **one fact, one canonical home**.

- product purpose/scope → `PROJECT_OVERVIEW.md`
- engineering process → workflow docs
- subsystem design → `docs/architecture/`
- important accepted choice + rationale → `docs/decisions/`
- execution/status → `docs/roadmap/`

Link to canonical information instead of duplicating it.

Update documentation in the same change when the change invalidates an existing statement.

Do not create placeholder documents or empty documentation trees for future work.

## Definition of Done

Before saying a code/file task is complete:

1. Self-review the final diff for scope, correctness, duplication, and unnecessary complexity.
2. Run the smallest relevant fresh verification that proves the claim.
3. For Flutter code, use the project-pinned SDK (`fvm flutter ...` / `fvm dart ...`) and normally include analyze plus relevant tests; add platform build/run checks when the changed scope requires them.
4. Prefer `tool/check.ps1` on Windows for the Foundation v1.1 baseline, and run `git diff --check` when reviewing the final change.
5. Update affected docs/ADR when behavior, architecture, or a recorded decision changed.
6. Confirm no secrets, generated build output, local caches, or unrelated edits entered the change.
7. Produce a patch/diff for every code or file change.
8. Report verification evidence and any remaining unverified platform/constraint explicitly.

Never claim “done”, “fixed”, “passes”, or “ready to merge” without fresh verification evidence.

## Git

Follow `docs/GIT_WORKFLOW.md`.

Do not commit directly to `main` or `dev` for normal feature work.

Do not commit, push, merge, delete branches, or rewrite history unless the user has asked for that action.

Every code/file modification must be reviewable as a diff/patch.
