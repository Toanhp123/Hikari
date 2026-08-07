# Pre-MVP Baseline 1 Design

Date: 2026-08-07
Status: Approved implementation baseline

## Goal

Establish one clean pre-public Baseline 1 for Hikari/OpenStory without changing
the approved Android MVP, domain model, or plugin security boundaries. Development
history that has not shipped publicly may be discarded where retaining it would
force permanent compatibility layers or unclear source ownership.

## Scope

This baseline resets repository mechanics around Room and declarative selectors,
then removes development-generation naming, stale dependency and IDE metadata,
oversized mixed-responsibility boundaries, historical verification names, and
Detekt baseline debt. It does not add Wave 05+ product behavior or implement the
remaining typed selector evaluator, DTO mappers, or JavaScript sandbox.

## Normative Decisions

### DECISION-BASELINE-001 Database

The complete Room structure currently represented by development schema 3 becomes
the initial database schema version 1. No migration is provided from development
schema versions 1, 2, or 3. Developers must clear app data or reinstall when moving
from an older development build.

### DECISION-BASELINE-002 Selector

The typed Catalog/Content endpoint and binding contract formerly called Selector
V2 becomes the only declarative selector schema version 1. The old linear
`operations -> SelectorValue` contract and its runtime are removed rather than
retained through legacy, compatibility, or version adapters.

### DECISION-BASELINE-003 Versions

Version spaces remain independent:

- Android application `versionCode = 1` and `versionName = "1.0"` remain unchanged.
- Repository index `schemaVersion = 1` remains unchanged.
- Plugin API remains major version 1 with its existing major-compatibility policy.
- Selector schema becomes the single supported schema version 1.
- Room becomes the single supported database schema version 1.
- The package format has no independent schema-version field, so none is introduced.

### DECISION-BASELINE-004 Source Architecture

Active selector production code, tests, samples, and SDK documentation use canonical
capability names. Architectural types and files must not end in `V1`, `V2`,
`Legacy`, or `Compat`. Validation implementation lives under focused validation
packages, while Catalog and Content contracts retain domain ownership.

### DECISION-BASELINE-005 Compatibility

Pre-baseline developer database files, selector JSON, sample packages, emulator
installs, and other development-only fixtures may be discarded. Historical archive
documents remain unchanged as evidence of how the repository evolved; they are not
active implementation instructions.

## Architecture

- Room exposes the current logical schema as a fresh schema 1 with no migration chain.
- Plugin API exposes one `SelectorDefinition` envelope, a bounded request plan, a
  closed binding AST, domain endpoint contracts, and focused validators.
- Plugin host retains only bounded document acquisition after the old linear runtime
  is removed; typed binding evaluation remains the next Wave 04 Task 03 feature.
- Database persistence depends on neutral registry records instead of installer
  implementation types.
- Network URL validation and bounded response reading become focused components while
  preserving allowlist, redirect, size-limit, and error semantics.
- Installer orchestration delegates semantic-version decisions to a focused policy.
- Architecture and source-layout gates permanently enforce the clean baseline.

## Error and Compatibility Handling

Existing typed failures, network error codes, security checks, transaction behavior,
and domain invariants remain stable. The only intentional compatibility break affects
development-only Room databases and selector/package fixtures created before this
baseline. The repository documents the required clear-data/reinstall action rather
than adding migration or compatibility code.

## Verification

Each implementation task follows TDD where behavior changes: focused failing test,
minimal implementation, focused passing test, affected module suite, and an isolated
commit. The final checkpoint runs architecture scans, module suites, shared
verification, Detekt without a baseline, database instrumentation, and application
smoke tests on the supported API endpoints when local emulators are available.

## Handoff

After the Baseline 1 checkpoint, the next product work remains Wave 04 Task 03: typed
selector binding evaluation and Catalog/Content DTO mapping. This cleanup must not
pre-create empty runtime packages or implement that feature early.
