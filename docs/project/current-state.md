# Repository Current State

Date: 2026-08-07  
Snapshot reviewed: `Hikari-wave-04-task-03-selector-runtime(2).zip`  
Purpose: single source of truth for **where implementation is now**.

## Executive state

- Product baseline: approved Android local-first unified novel library design.
- Current Gradle modules: 8.
- Current active wave: **Wave 04 — Plugin Host and Security**.
- Current active boundary: **Wave 04 Task 03 — Selector V2 runtime execution**.
- Wave 05 must not begin until Wave 04 checkpoint evidence is accepted.

## What is present

### Wave 01 boundary

The repository contains the build architecture policy, `app.openstory` identity,
Compose/Navigation shell, Hilt/dispatcher boundaries, verification scripts, CI
configuration and module governance introduced by remediation.

**Status:** implementation present. Historical checkpoint files remain evidence
records and are not rewritten to claim a later gate passed.

### Wave 02 boundary

The repository contains canonical models, Room database/repositories, schemas 1–3,
migrations, metadata-only library persistence and lifecycle/progress safety work.

**Status:** implementation present. The embedded Wave 02 checkpoint explicitly
retains target-device/CI gates, so this snapshot alone is not used to declare the
checkpoint accepted.

### Wave 03 boundary

The repository contains:

- versioned plugin manifest/API compatibility rules;
- Catalog and Content wire contracts;
- package/repository formats and deterministic contract fixtures;
- Selector V1 schema;
- Selector V2 version-aware decoding;
- closed non-executable binding AST;
- all four Catalog and six Content endpoint declarations;
- install-time V2 structural/output-shape validation;
- package inspection that rejects malformed V1/V2 selector definitions before activation.

**Status:** implementation present. Runtime V2 evaluation remains a Wave 04 responsibility.

### Wave 04 boundary

Implementation present:

- allowlisted network gateway and resource/rate budgets;
- transactional plugin package verification/staging/activation;
- registry and rollback primitive;
- bounded Selector V1 runtime.

Still required for Selector V2 Task 03:

- shared validation-only `PluginUrlPolicy` used by both network gateway and output validation;
- shared final Catalog/Content wire DTO validators;
- endpoint-wide binding/output budgets;
- richer opaque DOM adapter with ordered relative traversal and semantic text/span extraction;
- typed V2 binding evaluator;
- Catalog mapper for all 4 endpoints;
- Content mapper for all 6 endpoints;
- selector Catalog/Content adapters and factory/dispatch integration;
- V1 regression + V2 cancellation/redaction/security evidence.

Later Wave 04 work remains after Task 03: JavaScript sandbox, full update lifecycle,
redacted diagnostics and unified host facade.

## Source-of-truth rule

When documents disagree:

1. Approved product design owns product scope and invariants.
2. Actual repository code owns what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate is proven complete.
4. Newer source-local remediation/spec documents supersede older greenfield
   implementation instructions for repository ownership and sequencing.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the full precedence policy.
