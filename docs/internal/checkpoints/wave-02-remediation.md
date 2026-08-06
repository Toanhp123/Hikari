# Wave 02 Domain and Local Storage Remediation Checkpoint

Date: 2026-08-06
Status: IMPLEMENTED — TARGET DEVICE VERIFICATION REQUIRED

## Scope

This checkpoint closes the approved Wave 02 ownership boundary:

- pure canonical domain and catalog metadata;
- Room schema version 3 and migrations 1→2→3;
- metadata-only library persistence;
- canonical chapter/release/progress durability;
- cancellation-safe repository writes;
- strict monotonic progress updates;
- plugin/source removal isolation;
- explicit canonical story purge with orphan cleanup;
- backup and schema policy checks.

Explicit download metadata is **not applicable** to Wave 02. Download
persistence begins in Wave 09 and must not be represented by placeholder tables
in the Wave 02 schema.

## Verification matrix

| Gate | Status | Evidence |
|---|---|---|
| Catalog domain metadata | PASS | Pure Kotlin harness retains source ID, URL, authors, genres, cover, publication status, and original score scale. |
| Migration 2→3 SQL | PASS | SQLite harness migrated a representative v2 row and preserved deterministic defaults without foreign-key violations. |
| Schema continuity | PASS | Policy test requires exactly schemas 1 through current database version 3. |
| Repository cancellation | PASS | Pure suspend harness proves `CancellationException` propagates while ordinary failures remain typed storage failures. |
| Lifecycle SQL | PASS | SQLite harness proves plugin-state deletion preserves data and canonical purge removes orphan source records safely. |
| Shell contracts | PASS | Database instrumentation and Wave 02 checkpoint runner contracts pass. |
| JDK 17 fast verification | NOT RUN | Must run on the target repository with the complete Gradle/Android toolchain. |
| API 26 database instrumentation | NOT RUN | Run `scripts/verify-wave-02-checkpoint.sh` with an API 26 serial. |
| API 37 database instrumentation | NOT RUN | Run `scripts/verify-wave-02-checkpoint.sh` with an API 37 serial. |
| GitHub Actions Wave 02 checkpoint | NOT RUN | Requires the target branch to be pushed. |

## Required target command

```bash
ANDROID_SERIAL_API_26=<api-26-serial> \
ANDROID_SERIAL_API_37=<api-37-serial> \
  ./scripts/verify-wave-02-checkpoint.sh
```

The checkpoint may be marked `PASS` only after the command above and the
GitHub Actions `Wave 02 Checkpoint` job both succeed.
