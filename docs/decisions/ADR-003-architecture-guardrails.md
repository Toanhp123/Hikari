# ADR-003: Enforce architecture guardrails

- Status: **Accepted**
- Date: **2026-09-25**

## Context

ADR-001 defines Hikari's dependency direction, but folders and review guidance alone do not stop accidental cross-layer dependencies. Standard Flutter/Dart lints also do not know that `domain/` must not depend on `infrastructure/`, or that `features/` must not reach concrete infrastructure directly.

The guard must be deterministic with the pinned Flutter/Dart SDK, run locally and in CI, and avoid adding a package/monorepo split or analyzer-plugin dependency before Hikari has real domain code.

## Decision

Hikari keeps architecture enforcement inside the existing test gate with `test/architecture_guard_test.dart`. The guard uses only Dart SDK APIs plus the already-installed `flutter_test` package; Foundation v1.1 adds no lint/runtime dependency.

Allowed internal dependencies are:

```text
root entrypoints
  -> app

core
  -> core

domain
  -> core, domain

application
  -> core, domain, application

infrastructure
  -> core, domain, infrastructure

features
  -> core, domain, application, features

app
  -> composition root; may wire all layers
```

Additional rules:

- Root entrypoints stay thin: they may enter Hikari through `app/` and use Flutter bootstrap APIs, but not implementation packages or platform APIs directly.
- `core/`, `domain/`, and `application/` default-deny external packages. A pure-Dart package can be allowed only by an explicit guard + ADR change; framework/data/player dependencies do not belong there.
- Those pure layers must not use platform-specific Dart libraries such as `dart:io`, `dart:ffi`, `dart:ui`, JavaScript interop, or isolates directly.
- `part` / URI-based `part of` must remain inside the same architecture layer.
- A new top-level directory under `lib/` is rejected until its boundary is deliberately added to ADR-001 and the guard.
- Both package URIs and relative URIs are normalized before the dependency matrix is checked.
- Conditional import/export URIs are checked individually.

The architecture guard is a structural dependency check, not a semantic proof. Code review still decides whether responsibilities are placed in the correct layer.

## Static type hardening

The project continues to use `flutter_lints` and enables only three extra Dart checks:

- `strict-inference` analyzer mode;
- `no_dynamic_casts`;
- `no_raw_types`.

`no_dynamic_casts` and `no_raw_types` are the Dart 3.13 replacements for the older `strict-casts` and `strict-raw-types` analysis options.

## Dependency and CI hardening

Foundation validation uses `pub get --enforce-lockfile` so CI fails when `pubspec.lock` is stale or a hosted package content hash no longer matches.

Every external GitHub Action used by Hikari CI is pinned to a full commit SHA, with the reviewed release version retained as a comment. Dependabot checks GitHub Actions weekly against `dev` so updates arrive as reviewable pull requests instead of mutable action tags changing underneath CI.

## Merge enforcement

Repository settings must protect `dev` and `main` with pull requests and the `Quality gates` status check. These settings are documented in `docs/GIT_WORKFLOW.md` because GitHub rulesets/branch protection are repository state rather than files in the Git tree.

## Consequences

### Positive

- Cross-layer `import`, `export`, and `part` dependencies fail the normal test gate locally and in CI.
- The guard itself has representative allow/deny regression cases.
- No new analyzer plugin, dev dependency, package manifest, or architecture framework is introduced.
- SDK/tool upgrades cannot silently make a third-party architecture plugin incompatible.
- CI action code is immutable between reviewed dependency updates.

### Trade-offs

- Architecture violations appear during tests rather than as live IDE diagnostics.
- The guard lexically recognizes Dart dependency directives; it intentionally does not attempt to become a full Dart parser.
- Semantic leaks (for example business logic placed in a widget without a forbidden import) still require review.
- A future package split can provide stronger compiler-level boundaries if project scale eventually justifies the extra manifests and workspace tooling.

## Rejected alternatives

### Analyzer architecture plugins now

Rejected for Foundation v1.1 because the analyzer-plugin API moves in lockstep with Dart's analyzer, plugin resolution happens in a synthetic package, and compatibility can lag a newly pinned SDK. Hikari needs a stable boundary guard more than IDE diagnostics at this stage.

### Separate Dart/Flutter package per layer now

Rejected because package boundaries are stronger but would add multiple manifests, workspace wiring, exports, and cross-package dependency/test management before Hikari has enough implementation to justify that cost.

### Grep-only checker

Rejected because raw textual matching is too easy to bypass with comments, strings, relative URIs, conditional directives, or re-exports.
