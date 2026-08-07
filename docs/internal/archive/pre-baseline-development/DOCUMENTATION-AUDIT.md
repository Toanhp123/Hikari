<!--
DOCUMENT LIFECYCLE
Status: HISTORICAL AUDIT
Canonical execution status: ../project/current-state.md
Findings below describe the pre-baseline documentation state and are not instructions.
-->

# Documentation Audit Report

Date: 2026-08-07  
Reviewed basis: source snapshot plus the supplied 2026-08-03 planning package and
2026-08-06 Selector V2 review package.

## Verdict

The product architecture is broadly consistent, but documentation had become
operationally unsafe because **historical greenfield plans, remediation evidence,
and current Selector V2 work were presented without lifecycle/precedence metadata**.
The main risk was not missing requirements; it was an agent choosing the wrong plan
as the next executable instruction.

## High-impact findings

### A1 — No single documentation entry point — HIGH

The source had README/build instructions, plugin SDK docs, remediation specs/plans,
and checkpoint evidence, while the approved 11-wave planning pack remained outside
the source. No document said which source was authoritative for scope, status,
execution order, or checkpoint truth.

**Resolution:** added `docs/README.md`, `PROJECT-HANDBOOK.md`, `project/current-state.md`
and the precedence policy.

### A2 — Greenfield bootstrap instruction is stale for the current repository — HIGH

The original planning package explicitly says the Android source tree was unavailable
when it was written and tells the reader to start Wave 01. The current snapshot already
contains eight modules and implementation through Wave 04 work.

**Resolution:** preserved the original package under archive; execution now begins from
`implementation/current-roadmap.md`.

### A3 — Original Wave 04 Task 03 scope is too small for its own acceptance criteria — BLOCKING if followed literally

The 2026-08-03 Wave 04 plan sketches five selector-runtime files, while its acceptance
requires plugin wire DTO output. The reviewed Selector V2 package correctly identifies
that V1 TEXT output cannot satisfy Catalog/Content DTO contracts without a versioned,
typed output binding layer.

**Resolution:** the active Selector V2 runtime plan is explicit and retains the reviewed
remaining Tasks 4–9.

### A4 — Selector V2 wave ownership changed but old plan remained executable-looking — HIGH

The newer source-local Wave 03 design explicitly assigns V2 serialized contracts and
install-time validation to Wave 03, while networking/DOM/evaluator/mapping/runtime
validation stay in Wave 04. The older 2026-08-06 plan still starts by adding those
contracts, which would duplicate already-present source.

**Resolution:** archived the full older plan, and created a current runtime continuation
that starts after the already-absorbed contract work.

### A5 — “Implementation exists” and “checkpoint passed” were conflated — HIGH

Wave 02/03 implementation is present in the source while embedded checkpoint records
still contain required `NOT RUN` gates. Conversely, treating those old checkpoint lines
as the only status would make the repository look as though later implementation did not
exist.

**Resolution:** `current-state.md` uses separate terms: implementation present,
verification open, in progress, planned. Historical evidence is kept unchanged.

### A6 — Target module graph looked like current module graph — MEDIUM

The original roadmap lists future feature/core modules that do not yet exist. The current
source contains eight modules.

**Resolution:** current roadmap now shows **current graph** and **target graph** separately.

### A7 — Repeated global constraints create drift risk — MEDIUM

Every wave repeats the same SDK/toolchain/product constraints. They are useful in isolated
agent tasks but costly to maintain globally.

**Resolution:** original plans remain intact for self-contained execution; the handbook
summarizes the canonical constraints once, and later changes should update both the
handbook and affected isolated plans deliberately.

### A8 — Product design terminology and implementation pin differ around navigation — LOW/MEDIUM

The approved product design refers to Navigation Compose in its recommended Android
components, while the implementation plans consistently pin Navigation 3. This is best
read as an implementation-baseline refinement, not a product-scope change.

**Resolution:** handbook records Navigation 3 as the repository implementation baseline
without editing the approved product design text.

## Coverage review

The supplied requirement coverage matrix already maps every included MVP requirement to
at least one wave and keeps excluded scope out of implementation. The consolidation does
not change those ownership decisions. It adds only status and supersession metadata.

## Files intentionally not rewritten

- approved product design body;
- historical remediation specs/plans;
- historical checkpoint evidence;
- plugin SDK contracts;
- archived planning/Selector V2 packages.

This preserves auditability and avoids fabricating a cleaner history than the supplied
sources support.
