# Hikari Module-Local Cleanup Design

Date: 2026-09-15
Status: **APPROVED CLEANUP GOVERNANCE; NO MODULE AUTHORIZED BY THIS DOCUMENT ALONE**

## Goal

Clean Hikari one module at a time so files, packages, tests, and internal responsibilities reflect the
already-accepted Step 3 Task 10 architecture without changing product behavior or redesigning the
cross-module graph.

The canonical placement rules are `../../project/file-package-ownership-policy.md`. This design defines
how the cleanup campaign uses those rules; it does not duplicate them.

## Why module-local

The repository-wide audit found no structural production dependency cycle or cross-module ownership
failure requiring a graph rewrite. Cleanup therefore optimizes the inside of existing module boundaries:

- clearer responsibility-bearing packages;
- files named for stable responsibilities;
- test support in the correct source set/package;
- removal of proven dead/orphan residue;
- splitting mixed-responsibility hotspots where focused behavior tests make the change safe;
- moving application-composition adapters only when their true ownership is demonstrably app-wide.

Cross-module changes remain exceptional and require explicit design authority rather than being smuggled
into a module cleanup.

## Campaign constraints

1. Production behavior through Step 3 Task 10 is frozen.
2. Step 3 Task 11 Reading Source work remains not started during cleanup.
3. No module addition/removal or dependency-edge change is authorized by this campaign.
4. No UI redesign, feature work, performance tuning by intuition, DB semantic change, or retained-module
   admission is authorized.
5. Every cleanup turn names exactly one primary module. Adjacent consumer edits are allowed only when a
   move/rename requires them and must remain inside the smallest dependency cone.
6. Do not start the next module in the same turn unless the user explicitly authorizes it.
7. `:catalog:model`, `:catalog:engine`, `:reader:engine`, and `:plugins:api` remain retained/quarantined;
   cleanup does not delete or activate them by default.

## Per-module design sequence

Before the first source edit, the implementing agent must produce a bounded audit for the selected module:

1. state the module's responsibility and direct dependencies;
2. inventory production/test packages and public entry points;
3. identify objective policy violations separately from review-only smells;
4. identify dead/orphan declarations using repository references, not naming guesses;
5. identify mixed-responsibility files and composition ownership questions;
6. propose the target tree and exact move/rename/split list;
7. state behavior/contracts that must remain unchanged;
8. state focused tests/compile checks that can falsify the cleanup;
9. obtain explicit approval for that module's target tree before implementation.

A module with no meaningful cleanup findings may be left unchanged. The campaign is not a requirement to
move files everywhere.

## Implementation strategy

Within one approved module cleanup:

1. characterize behavior first where a responsibility split can affect logic;
2. apply mechanical path/package/filename moves before semantic extraction when practical;
3. keep public API changes at zero unless the old API is proven internal and all consumers are in scope;
4. remove dead code only with a reference/behavior argument and focused verification;
5. split files by responsibility, not arbitrary line chunks;
6. do not create wrappers/aliases for internal moves merely to reduce call-site edits;
7. keep architecture/source-layout gates unchanged unless the cleanup exposes a defect in the gate itself;
8. update the active checkpoint with the exact changed tree, evidence, remaining debt, and next resume boundary.

## Documentation and plan model

There is intentionally **no single all-module implementation plan**. A generic plan would either contain
placeholders or pre-authorize work in modules that have not yet been audited.

Instead:

- this design owns campaign-wide cleanup constraints;
- `docs/project/file-package-ownership-policy.md` owns placement rules;
- `docs/implementation/current-roadmap.md` names the currently authorized cleanup boundary;
- each selected module receives one bounded module-specific plan after its audit/target tree is approved;
- completed module plans are evidence, not permission to auto-advance to the next module.

## Verification model

### Agent-owned focused evidence

Use the narrowest tests/compiles capable of catching the cleanup's mistakes: affected unit tests, focused
Android-test compilation, exact module compilation, and targeted static diagnostics where appropriate.

### Repository/user-owned broad evidence

Architecture-wide, Detekt/lint, full `verify*.sh`, connected/device, and performance gates retain the
ownership rules in `AGENTS.md`. Do not claim closure from focused checks alone when the module plan
requires broader returned evidence.

### Structural review

`scripts/structural-review-report.sh` is a diagnostic input, not a to-do list. Review findings must be
interpreted against actual responsibility before moving or splitting code.

## Exit criteria for one module

A module cleanup closes only when:

- target package/file layout matches the canonical policy;
- no new module dependency edge or capability semantic move was introduced accidentally;
- objective source-layout violations in scope are resolved;
- focused behavior/compile evidence is green;
- dead code removal and responsibility splits are reviewable from the diff;
- documentation/checkpoint records exact remaining debt rather than implying the module is “perfect”;
- the agent stops at the module boundary.

## Campaign stop condition

The cleanup campaign stops when the live Step 3 modules have no high-value ownership/layout debt worth
changing before Task 11. It does not continue merely to normalize style or eliminate every structural
review signal.
