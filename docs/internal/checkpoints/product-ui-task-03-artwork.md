# Product UI Task 03 — Shared Artwork Checkpoint

Date: 2026-08-13
Status: **VERIFIED**
Scope: ReDantotsu-inspired Product UI Task 3 — shared artwork request/state, deterministic fallbacks, and Roborazzi geometry coverage in `:core:designsystem`.

## Accepted boundary

- Production graph remains exactly 14 modules; Room schema remains 6 with schemas 1 through 6 stable.
- `:core:designsystem` owns domain-neutral `HikariArtworkModel`, `HikariArtworkState`, `rememberHikariArtwork`, `HikariArtwork`, and `HikariArtworkBackdrop`; no domain/capability module dependency is introduced.
- Cover and backdrop consume the same remembered `HikariArtworkState`, so one Coil `ImageRequest` and one memory/disk cache identity are created per artwork model instead of one request per presentation surface.
- The cache identity is stable for the model's `stableKey` plus URL and is shared by cover/backdrop rendering.
- Missing/error artwork falls back deterministically from SHA-256 of `stableKey` into a fixed Hikari palette; monograms use the first trimmed title character or `?` when no character exists.
- Loaded, loading, and fallback/error states retain stable artwork geometry under Roborazzi coverage.
- The screenshot test class pins Robolectric to SDK 35 so the pinned Robolectric 4.16.1 can run under the repository's current JVM/toolchain while the application continues targeting SDK 37.

## Regression corrections verified during acceptance

The first focused host run failed during `HikariArtworkScreenshotTest` initialization in Robolectric's `DefaultSdkPicker`, before screenshot assertions executed. The application targets SDK 37 while the pinned Robolectric 4.16.1 does not support selecting that target. The accepted test therefore uses class-level `@Config(sdk = [35])`; production target/compile SDK values are unchanged. The focused Task 3 gate then completed successfully.

The first full `./scripts/verify.sh` run reached Detekt and failed on one Task 3 `MagicNumber` issue for the unsigned-byte `0xFF` mask in `HikariArtworkFallback.kt`. The accepted implementation names that value `UNSIGNED_BYTE_MASK`; behavior is unchanged. The next full repository run passed. Existing `LongMethod` findings in `AppNavHost.kt` and `AndroidxJavaScriptEngine.kt` remained warnings and did not fail the gate.

## Verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Focused artwork/Roborazzi gate | PASS | Windows PowerShell: `./gradlew :core:designsystem:testDebugUnitTest recordRoborazziDebug --stacktrace`; `BUILD SUCCESSFUL in 20s`, 278 actionable tasks (23 executed, 255 up-to-date) |
| Robolectric SDK regression | PASS | Initial `DefaultSdkPicker` initialization failure reproduced; `HikariArtworkScreenshotTest` pinned to SDK 35; focused gate rerun completed successfully |
| Full repository host gate | PASS | Git Bash: `./scripts/verify.sh`; repository contracts, architecture/source/package policies, tests, Detekt, Android lint, and `:app:assembleDebug` completed with `BUILD SUCCESSFUL in 40s`, 623 actionable tasks (75 executed, 548 up-to-date) |
| Detekt Task 3 regression | PASS | Initial full gate rejected the `0xFF` unsigned-byte mask as `MagicNumber`; named constant fix applied; full gate rerun passed with only pre-existing non-blocking `LongMethod` warnings |
| Architecture/module boundary | PASS | Full gate reported `Current architecture verified: 14 modules, Room schema 1..6.` and `Module architecture verified for 14 modules.` |
| Room schema stability | PASS | Full verification ended with `Room schema export remained stable during verification.` |

## Result

Product UI Task 3 is complete and verified. Product UI Task 4 — glass, responsive, and shared content primitives in `:core:designsystem` — is the next implementation task. Wave 10 remains deferred until the between-wave Product UI checkpoint completes.
