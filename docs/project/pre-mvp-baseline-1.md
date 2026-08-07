# Pre-MVP Baseline 1 Decision

Date: 2026-08-07
Status: Canonical architecture decision

## Decision

Hikari/OpenStory adopts a single pre-public Baseline 1 across the repository. The
approved Android MVP, canonical story/chapter/release model, local-first behavior,
and plugin security boundaries remain unchanged. Development-only compatibility
history may be discarded before the first public baseline.

## Version Spaces

| Version space | Baseline decision |
|---|---|
| Android application | Keep `versionCode = 1`, `versionName = "1.0"` |
| Room database | Rebase the complete current development schema 3 as initial schema 1 |
| Plugin API | Keep major 1 compatibility rules |
| Repository index | Keep `schemaVersion = 1` |
| Declarative selector | Rebase the typed endpoint/binding contract as the only schema 1 |
| Package format | Do not add a schema field; none exists in the source contract |

No migration is provided for development Room schemas 1, 2, or 3. The old linear
selector contract and runtime are removed rather than adapted. Developers testing
against an older build must clear app data or reinstall, and must regenerate or
replace old selector/package fixtures.

Active selector code, tests, samples, and SDK documentation use canonical names, not
`V1`, `V2`, `Legacy`, or `Compat` generation names. Historical documents remain in
the archive as evidence and are not rewritten to hide development history.

The detailed design is
[`../superpowers/specs/2026-08-07-pre-mvp-baseline-1-design.md`](../superpowers/specs/2026-08-07-pre-mvp-baseline-1-design.md).
After this checkpoint, implementation resumes at Wave 04 Task 03 for typed selector
binding evaluation and DTO mapping.
