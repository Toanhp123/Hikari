# Architecture Baseline 2 R0 Checkpoint

Date: 2026-08-09
Status: **PASS - R0 accepted; R1 may begin**

## Environment

- Branch: `refactor/architecture-baseline-2`
- Verified implementation HEAD before acceptance record: `4404d3a`
- Operating system: Windows 11 10.0 amd64
- JDK: Eclipse Temurin 17.0.20
- Gradle: 9.5.0
- Android application identity: `app.openstory`

## Accepted boundary

- Wave 06 is frozen until Architecture Baseline 2 R6 acceptance.
- Wave 01-05 behavior is classified through an explicit KEEP/CHANGE/DELETE inventory.
- High-value legacy tests have REWRITE, KEEP_UNTIL_REPLACED, or DELETE_WITH_OWNER intent.
- Structural Detekt suppressions are denied unless an exact path/rule allowance records a
  reason and removal checkpoint.
- The only transition allowance is the Wave 05 `SearchScreen.kt` `TooManyFunctions`
  suppression, scheduled for removal in R4.
- Target package boundaries are checked safely before R1 creates the target modules.
- The Baseline 2 spec and R0-R6 plan set are committed as the active execution authority.

## Verification evidence

| Command | Result | Evidence |
|---|---|---|
| `bash ./scripts/verify.sh` | PASS | All shell contract tests passed; structural suppression and package boundary policies verified; source layout and baseline architecture verified; Gradle verification completed with `BUILD SUCCESSFUL`; Room schema export remained stable. |
| `bash ./scripts/check-module-dependencies.sh` | PASS | Gradle architecture verification completed with `BUILD SUCCESSFUL`; current transition graph remains 11 modules. |

The fixture test for structural suppressions intentionally exercised and observed both
unapproved and stale-allowance failures before accepting the exact transition row. No
required R0 command remains unrun.

## Decision

Architecture Baseline 2 R0 is accepted. Governance and anti-debt guardrails are active,
Wave 06 remains frozen, and the next implementation boundary is R1 - Foundation and
Module Graph.
