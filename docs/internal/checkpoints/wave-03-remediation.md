# Wave 03 Remediation Checkpoint

## Scope

Wave 03 freezes the public plugin ecosystem boundary before Wave 04 executes community plugin definitions.

The remediation adds:

- strict manifest/API/capability invariants;
- Selector V2 version-aware decoding;
- a closed, non-executable output-binding AST;
- all four Catalog and all six Content endpoint declarations;
- bounded install-time binding and output-shape validation;
- complete deterministic Selector V2 fixture coverage;
- package, signature, provenance, and repository-index hardening;
- pre-install V1/V2 selector decoding and validation in the ZIP inspector.

Runtime networking, HTML evaluation, DTO mapping, cancellation, and JavaScript execution remain owned by Wave 04.

## Required gates

| Gate | Status | Evidence |
|---|---|---|
| Wave 02 checkpoint | PASS | Required baseline supplied by the user. |
| Static source and JSON verification | PASS | Contract source compiles with local Kotlin stubs; fixture JSON parses; shell contracts pass. |
| `:core:plugin-api:test` on JDK 17 | NOT RUN | Must run on the target Windows checkout or CI. |
| `:core:plugin-host:test` on JDK 17 | NOT RUN | Must prove malformed selectors fail during package inspection. |
| `:test:fixtures:test` on JDK 17 | NOT RUN | Must run on the target Windows checkout or CI. |
| Full `scripts/verify.sh` | NOT RUN | Must run on the target Windows checkout or CI. |
| GitHub Actions Wave 03 checkpoint | NOT RUN | Pending branch push. |

## Selector compatibility

- Schema V1 remains a separate model and keeps its original encoding and operation semantics.
- Schema V2 uses endpoint-oriented Catalog/Content declarations.
- Unknown schema versions, unknown polymorphic binding types, unknown endpoint fields, excessive nesting, and excessive binding counts fail closed.
- Relative request templates require persisted `declarativeOrigin`; origin is never inferred from host-set order.

## Exit decision

`NOT RUN` until `./scripts/verify-wave-03-checkpoint.sh` and the GitHub Actions Wave 03 checkpoint pass. Wave 04 Task 03 may resume only after these gates are reviewed.
