# Repository Current State

Date: 2026-08-07  
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: approved Android-only, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current Gradle modules: 8.
- Wave 01-03 implementation is present.
- Wave 04 Tasks 01-02 implementation is present.
- Pre-MVP Baseline 1 project-wide refactor is complete.
- Current active boundary: **Wave 04 Task 03 - Selector Schema 1 runtime execution**.
- Wave 05 must not begin until the Wave 04 checkpoint is accepted.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 1, rebased current complete durable model |
| Declarative selector | schema 1, typed endpoint/binding contract |
| Plugin API | major/minor compatibility, baseline major 1 |
| Repository index | schema 1 |
| Package layout | no separate schema-version field in the current contract |

These versions are independent. A change in one does not imply a change in another.

## What is present

### Wave 01 boundary

The repository contains the JDK 17 build gate, `app.openstory` identity,
Compose/Navigation shell, dependency-boundary policy, shared verification, CI, and
module governance.

### Wave 02 boundary

The repository contains canonical story/chapter/release models and the complete current
Room database/repository implementation rebased as initial schema 1. Pre-baseline local
database migrations are intentionally unsupported; historical migration evidence remains
in documentation archives only.

### Wave 03 boundary

The repository contains:

- Plugin API major/minor compatibility rules;
- Catalog and Content wire contracts;
- package and repository-index formats;
- Selector Schema 1 typed endpoint and closed binding contracts;
- all four Catalog and all six Content endpoint declarations;
- install-time request, binding, output-shape, and manifest validation;
- deterministic complete contract fixtures;
- package inspection that rejects unsupported or malformed selectors before activation.

### Wave 04 boundary

Implementation present:

- allowlisted network gateway with resource, redirect, rate, and cancellation budgets;
- shared validation-only `PluginUrlPolicy` and isolated `BoundedResponseReader`;
- transactional plugin verification, staging, activation, registry, and rollback;
- neutral `PluginActivation` registry records implemented by `RoomPluginRegistry`;
- `SelectorDocumentLoader` for bounded HTTP document acquisition and DOM cleanup;
- `HtmlDocumentAdapter` as the opaque host DOM boundary;
- no development-generation selector runtime or compatibility pipeline.

Still required for Wave 04 Task 03:

- endpoint-wide selector evaluation budgets;
- typed `SelectorBindingEvaluator`;
- Catalog and Content mappers;
- shared final wire DTO validation;
- Selector Catalog/Content adapters and factory dispatch;
- cancellation, redaction, and deterministic runtime checkpoint evidence.

Later Wave 04 work remains after Task 03: JavaScript isolation, the full update
lifecycle, redacted diagnostics, and the unified host facade.

## Verification status

Implementation presence is not checkpoint acceptance. Evidence under
`../internal/checkpoints/` records commands actually run and keeps unexecuted gates as
`NOT RUN`. The Baseline 1 checkpoint is `../internal/checkpoints/pre-mvp-baseline-1.md`.

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.
