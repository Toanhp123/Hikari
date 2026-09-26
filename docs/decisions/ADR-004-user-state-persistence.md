# ADR-004: Source-keyed user state in Drift/SQLite

- Status: **Accepted**
- Date: **2026-09-26**

## Context

Local video, manga and plain-text readers need restart-safe progress. Users also
need explicit Library membership independent of scan results and reading activity.
The existing source-owned locator is sufficient; canonical content identity and
rename/deduplication rules are not yet proven requirements.

## Decision

Use pure domain progress/library contracts and two independent composite-key
SQLite tables through Drift. Pin `drift`/`drift_dev` 2.35.0, `drift_flutter` 0.3.1,
and `build_runner` 2.16.1 with the existing FVM toolchain. Keep generated Drift
code tracked and reproducible via `fvm dart run build_runner build
--delete-conflicting-outputs`. Schema begins at version 1. Drift Flutter's native
background connection keeps SQL work off the UI isolate.

Persist explicit completion separately from typed position and normalize timestamps
to UTC. Map generated storage records defensively into domain values. Library keeps
its own title/type snapshot and has no foreign-key dependency on progress.

Source capabilities are limited to proven id/name, manga page and novel text
operations. Video continues using the local locator/media-kit path; no speculative
playable-stream/quality/DRM contracts are added. Composition uses constructor
injection and direct capability checks.

## Alternatives

- SharedPreferences/JSON: rejected for typed multi-record upserts and independent
  durable state; native SAF selection remains its existing separate concern.
- Isar: not selected; SQLite/Drift provides explicit schema, testable mapping and
  straightforward file-backed reopen tests for this requirement.
- Full canonical media graph, history, sync, provider registry or player abstraction:
  deferred until concrete consumers need them, not constructed to predict them.

## Consequences

Code generation and SQLite native assets become build inputs. Schema changes need
explicit migrations from version 1; no future migration design is scaffolded now.
Source locators can become orphaned after rename/move/grant revocation. A future
canonical model can associate these references without redefining Library as
progress ownership. See [USER_STATE](../architecture/USER_STATE.md) for canonical
schema, resume behavior, limits and verification boundaries.
