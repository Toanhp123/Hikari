# File, Package, and Ownership Policy

Date: 2026-09-15
Status: **CANONICAL source-layout and module-local cleanup policy**

## Purpose

This document defines where production and test source belongs inside the Hikari repository. It is the
normative policy for file placement, package naming, module-local cleanup, and source ownership after
Step 3 Task 10.

This policy does **not** redesign the production module graph. Module inclusion and dependency edges
remain canonical in `../../settings.gradle.kts` and
`../../config/architecture/module-boundaries.json`.

When a cleanup task moves, renames, splits, or extracts source, follow this policy before applying
personal style preferences or generic architecture templates.

## Decision Order

Every source-placement decision is made in this order:

1. **Module ownership** — which capability/layer owns the semantics?
2. **Source set** — production, host test, or Android instrumentation?
3. **Responsibility/package** — what cohesive responsibility groups the file with its neighbors?
4. **File boundary** — what is the smallest stable primary responsibility worth naming?
5. **Filename** — does the name expose the primary declaration/responsibility without development-history labels?

A correct package inside the wrong module is still wrong. Do not use package rearrangement to hide an
ownership problem.

## Module Ownership Rules

### `:app`

Owns application composition and application-wide coordination: startup shell, route-stack ownership,
process/session wiring, and adapters whose purpose is to connect otherwise independent capabilities.

`:app` must not become a storage, domain, or feature-semantics owner. If a file primarily says “connect
A to B”, app composition is usually the first ownership candidate.

### `:core:common`

Owns only narrow, dependency-light primitives that are genuinely cross-capability. It is not a dumping
ground for convenience helpers, feature models, or code extracted merely because two files look similar.

### `:core:artwork`

Owns domain-neutral process artwork mechanics and policy: admission, cache/coalescing, bounded payload
handling, image/container validation, and related process-safe artwork behavior. It must not acquire
Catalog, Library, Story, Reading, or presentation semantics.

### `:core:designsystem`

Owns repeated **domain-neutral** visual policy and shared presentation primitives admitted by
`../ui/design-system.md`. It does not own feature state, runtime work, persistence, navigation decisions,
or capability-specific models.

### `*:domain`

Owns capability semantics, identities/value objects, domain models, typed failures, policies, and ports.
Domain source must remain framework-independent according to the architecture policy. Android, Compose,
Room, runtime orchestration, and persistence entities do not belong here.

### `*:storage`

Owns persistence mechanics that implement domain contracts: Room database/entities/DAO, migrations,
serialization/mapping, storage-level atomicity, indexed queries, and retention mechanics required by the
owning domain contract.

Storage does not invent feature behavior or capability policy. A storage implementation may enforce an
atomic invariant required by the domain, but the semantic rule must remain visible in the domain/runtime
contract rather than becoming storage-only truth.

### `*:runtime`

Owns runtime orchestration and lifecycle mechanics for its capability: activation, observation sessions,
mutation serialization, admission/concurrency, source execution, and coordination between domain ports
and storage implementations.

Runtime does not own Compose UI and must not become an alternate persistence layer.

### `:feature:*`

Owns presentation and user-flow orchestration for exactly its capability: ViewModels/presenters,
presentation reducers/state, Compose surfaces, accessibility/test tags, and feature-local adapters.

A feature must not own another feature's persistence/runtime semantics or become an application-wide
composition owner merely because it was the first consumer of a shared mechanism.

### Retained/quarantined modules

`:catalog:model`, `:catalog:engine`, `:reader:engine`, and `:plugins:api` retain their explicitly recorded
status in `current-state.md` and the salvage ledger. Module-local cleanup does not admit them into the
live app graph, delete them, or rename their public/versioned contracts without separate authority.

## Package Rules

### Name packages by cohesive responsibility

Prefer responsibility-bearing names that help a reader predict behavior, for example:

- `...catalog.runtime.acquisition`
- `...catalog.storage.retention`
- `...story.feature.presentation`
- `...designsystem.navigation`

Do not create packages merely to mirror generic architecture nouns such as `model`, `manager`, or
`component` when the name adds no ownership information.

### Generic buckets are forbidden as new cleanup destinations

Do not create new production packages named only:

- `utils` / `util`
- `helpers` / `helper`
- `misc`
- `stuff`
- `common` inside a capability merely to avoid deciding ownership
- `impl` when a responsibility-bearing implementation package can be named instead

Existing generic packages, if any, are cleanup candidates rather than precedents.

### Do not over-package small modules

A subpackage is justified when it creates a real responsibility boundary or groups multiple closely
related declarations. Do not create a directory solely to hide one file unless that file marks a hard,
meaningful boundary that would otherwise be ambiguous.

Package depth is not a quality metric. Prefer the shallowest tree that still communicates ownership.

### Package path and namespace must agree

Production and test files belong under the filesystem path matching their Kotlin package. Moves must
update package declarations and callers coherently; do not rely on misleading physical placement.

## File Rules

### One primary responsibility

A production file must have one primary responsibility that can be stated in one short sentence. Closely
coupled private/internal declarations may stay in the same file when separating them would make the
contract harder to understand.

Split a file when it contains independently nameable responsibilities that can change for different
reasons. Do not split merely to reduce line count or create `Part1`/`Part2` files.

### Filename reflects the primary declaration or responsibility

Prefer names such as `StoryScreen.kt`, `RoomLibraryStore.kt`, or `ArtworkRequestPolicy.kt`.

Avoid broad or history-shaped names such as:

- `StoryStuff.kt`
- `CommonModels.kt`
- `Extensions.kt` when unrelated extensions are mixed together
- `FooPart1.kt` / `FooPart2.kt`
- `LegacyFoo.kt`, `OldFoo.kt`, `NewFoo.kt`, or cleanup-generation labels

A file containing one public top-level type should normally match that type's name. Multi-declaration
files are acceptable when the declarations form one cohesive contract and the filename names that
contract.

### Version labels are semantic only

`V1`, `V2`, or similar suffixes are allowed only when they are part of a real versioned protocol/schema/
contract identity. They must not mean “old implementation” or “new rewrite”. Existing approved examples
include `RemoteHttpsUriV1` and `SourceStoryIdV1`.

The executable source-layout policy owns the exact current exception set.

### Structural size policy

Current executable thresholds remain:

- production Kotlin source over **500 lines**: hard failure unless repository policy is explicitly
  redesigned; the current allowlist is expected to have zero active debt rows;
- production Kotlin source over **300 lines**: structural review candidate, not an automatic split;
- test/Android-test Kotlin source over **750 lines**: hard failure.

Line count is a signal, never the ownership model. A 220-line file can still be badly mixed; a 340-line
cohesive protocol may remain one file after review.

## Source-Set and Test Placement

Production behavior belongs in `src/main`. Test-only fakes, builders, adapters, screenshot/evidence
helpers, and fixtures remain in the narrowest owning test source set.

Tests should mirror production responsibility/package where practical. Cross-cutting instrumentation
support may use a responsibility-bearing package such as `evidence`, `navigation`, `persistence`, or
`assets`; do not create a generic test-helper dump.

Do not move test utilities into production merely to share them across tests. If multiple test source
sets need the same fixture, first determine the true owning module/source set and use the project's
existing test-fixture mechanism only when the reuse is real.

## Composition Boundary Rule

Code whose primary responsibility is wiring two or more capabilities does not automatically belong to
one of those capabilities.

Ask whether the code:

1. contains capability semantics — keep it with that capability;
2. adapts one capability's public contract for its own presentation — keep it in that feature;
3. exists mainly to connect independent capabilities/process owners — prefer `:app` composition.

Do not create a new shared module merely to avoid making this decision.

## No Premature Shared Rule

Do not extract code into `:core:common`, `:core:designsystem`, or another shared surface solely because
two consumers contain similar code.

A shared extraction requires all of the following:

1. semantics are genuinely the same, not just structurally similar;
2. at least two real production consumers exist;
3. ownership is capability-neutral;
4. the shared API can be named without leaking the originating capability;
5. dependency direction remains valid without exceptions added merely to make the move compile.

Small local duplication is preferable to a false shared abstraction.

## Move, Rename, and Split Protocol

For module-local cleanup, one turn names **one primary module**. Adjacent consumer edits are allowed
only when a move/rename requires them and must remain inside the smallest dependency cone.

1. Freeze the current module/capability behavior and public boundary.
2. Inventory production/test files and immediate external consumers.
3. Classify each candidate by module ownership, source set, responsibility, and contract visibility.
4. Propose the target tree before moving files.
5. Add/adjust focused tests first when behavior-bearing code will be split or refactored.
6. Prefer mechanical move/rename before semantic refactor so failures remain attributable.
7. Do not add compatibility wrappers, aliases, forwarding classes, or duplicate old/new paths unless a
   real externally consumed ABI/API requires them.
8. Search for stale package/import/path references after every move batch.
9. Run the narrowest affected compile/tests, then architecture/source-layout gates according to
   repository verification ownership.
10. Self-review for misplaced ownership, new generic buckets, duplicate abstractions, and accidental
    cross-module dependency changes.
11. Update the active cleanup checkpoint/plan with the exact resume boundary; do not start the next
    module without explicit authorization.

## Module Cleanup Audit Template

Every module cleanup records the same questions in this order:

1. What is the module's current responsibility and public API?
2. What is the current production/test package tree?
3. Which files are misplaced by module ownership?
4. Which files are misplaced by package/source set?
5. Which names hide responsibility or encode development history?
6. Which files have more than one independently changeable responsibility?
7. Which code is dead/orphaned or duplicated without a valid contract?
8. Are composition adapters living in a capability only by historical accident?
9. Do tests mirror ownership and remain in test source sets?
10. What is the smallest target tree that improves clarity without redesigning behavior?
11. What focused evidence proves behavior and dependency direction are unchanged?
12. What debt remains intentionally untouched, and why?

The answer to these questions determines the cleanup. A module is not required to adopt the same
subpackages as another module.

## Hard Gates Versus Review Rules

### Executable/hard policy

The repository should fail closed for objective rules, including:

- forbidden module dependency/import direction;
- package cycles and production package-policy violations;
- unapproved generation-labelled active-source filenames;
- development-history test filenames covered by the source-layout verifier;
- source line ceilings;
- domain/framework bans already expressed by architecture policy;
- stale/invalid source-layout allowances;
- source/package path rules where they can be checked without heuristics.

Current executable authority includes:

- `../../scripts/verify-source-layout.sh`
- `../../scripts/tests/v2-source-layout-policy-test.sh`
- `../../scripts/structural-review-report.sh`
- `../../config/architecture/module-boundaries.json`
- Gradle architecture verification tasks referenced by `current-state.md`

### Human/agent review policy

Do **not** hard-code heuristic judgments such as:

- whether a 300-line file must split;
- whether two similar implementations deserve a shared abstraction;
- whether one-file subpackages are justified;
- whether a coordinator name is too broad;
- whether a composition adapter should move to `:app`.

These require responsibility/consumer analysis. Structural reports may flag them for review but must
not turn style preference into repository law.

## Exception Policy

Do not add an exception merely to make a cleanup patch pass.

- Dependency exceptions require an approved architecture change.
- Version-labelled filename exceptions require a real versioned contract and explicit executable-policy
  coverage.
- Line-limit debt is not accepted as a temporary cleanup mechanism; the current source-layout allowlist
  is expected to remain empty.
- If a policy rule conflicts with a necessary public/API contract, stop the cleanup and resolve the
  contract explicitly rather than hiding the conflict behind an alias or suppression.

## Authority and Change Control

This file is the canonical answer to **“where should this source live and how should module-local
cleanup organize it?”**

- `current-state.md` still owns what is implemented now.
- `current-roadmap.md` still owns which task/module may be worked on next.
- `module-boundaries.json` still owns dependency direction.
- `ui/design-system.md` still owns Design System presentation admission.
- Active cleanup specs/plans may narrow a module's work, but they may not silently override this policy.

Any intentional change to these rules must update this file, relevant executable gates, and
`document-governance.md` in one reviewed change.
