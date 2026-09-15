# Hikari Repository Module-Local Cleanup Checkpoint

Date: 2026-09-15
Status: **`:core:artwork` CLEANUP COMPLETED/ACCEPTED**

## Authority

- Source placement and package ownership: `../../project/file-package-ownership-policy.md`
- Cleanup campaign constraints: `../../superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md`
- Current execution routing: `../../implementation/current-roadmap.md`
- Production dependency direction: `../../../config/architecture/module-boundaries.json`
- Active module plan: `../../superpowers/plans/2026-09-15-core-artwork-module-cleanup.md`

## Accepted boundary before this patch

- Step 3 remains accepted through Task 10.
- Step 3 Task 11 remains not started during the cleanup interlude.
- Wave 0 repository-truth repair and cleanup governance are the prerequisite baseline.
- The cross-module production graph remains accepted; this patch does not add/remove modules or dependency edges.

## `:core:artwork` cleanup target

The approved cleanup keeps only module-wide primitives at the root and groups the remaining source by
responsibility:

```text
core/artwork/src/main/kotlin/app/openstory/artwork/
├── ArtworkFailure.kt
├── ArtworkLimits.kt
├── cache/
│   ├── ArtworkDecodedMemoryCache.kt
│   ├── ArtworkEncodedCache.kt
│   └── ArtworkMemoryPressure.kt
├── pipeline/
│   ├── ArtworkInFlight.kt
│   └── ArtworkPipelineCoordinator.kt
├── policy/
│   ├── ArtworkPolicy.kt
│   └── ArtworkPolicyResolver.kt
├── preflight/
│   ├── ArtworkImageContainer.kt
│   └── ArtworkImagePreflight.kt
├── remote/
│   ├── ArtworkRemotePolicy.kt
│   └── ArtworkTransport.kt
├── request/
│   ├── ArtworkRequest.kt
│   └── ArtworkRequestIdentity.kt
└── runtime/
    ├── ArtworkFetcher.kt
    └── ArtworkRuntime.kt
```

Core tests now mirror the same responsibility packages for cache, pipeline, policy, remote, and request
coverage. `ArtworkPolicyTest` was split so remote acquisition tests and helpers no longer live in the
policy-only test file.

## Behavior/architecture invariants preserved by the patch

- `settings.gradle.kts` and `config/architecture/module-boundaries.json` are unchanged.
- `:core:artwork` still depends only on its previously accepted dependency cone; no build dependency is added.
- Artwork limit values are unchanged: 32 MiB decoded memory, 128 MiB encoded disk, 8 MiB encoded payload,
  three redirects, 5/10/15 second connect/read/call timeouts, 8192 source dimension, 32M source pixels,
  and zero manual offscreen prefetch.
- Artwork failure reasons are unchanged and remain typed.
- HTTPS-only admission, canonical DNS host checks, redirect revalidation, bounded temporary payloads,
  media admission, JPEG/PNG/static-WebP validation, animated-image rejection, cache commit behavior,
  in-flight coalescing, last-consumer cancellation, decode/network admission, memory-pressure handling,
  and lazy runtime/session ownership are not intentionally changed.
- No compatibility aliases preserve the old flat artwork package; all repository consumers were migrated
  to the new responsibility-bearing package paths.

## Fresh agent-owned evidence

The cleanup workspace recorded the required RED before implementation:

- `bash scripts/tests/v2-core-artwork-layout-test.sh` — **FAIL as expected** because
  `ArtworkFailure.kt` and the approved package tree did not yet exist.

After implementation:

- `bash scripts/tests/v2-core-artwork-layout-test.sh` — **PASS**;
- `bash scripts/tests/v2-verification-entrypoints-test.sh` — **PASS**;
- `bash scripts/tests/v2-source-layout-policy-test.sh` — **PASS**;
- `source scripts/verification-common.sh && run_repository_static_contract_tests` — **PASS** for all
  current static contract tests, including the new artwork-layout ratchet;
- `bash scripts/structural-review-report.sh` — **PASS hard policies**; the former
  `ArtworkPolicy.kt` 628-line hard violation is gone;
- standalone `kotlinc` compile of the Android/Coil-independent artwork policy/remote/pipeline/request-
  identity cone with execution-contract stubs — **PASS**;
- declaration inventory comparison — **PASS**, 41 pre-cleanup declarations and 41 post-cleanup
  declarations with no missing/new named declarations;
- artwork limit-value comparison — **PASS**, exact values unchanged;
- `ArtworkFailureReason` member comparison — **PASS**, exact members/order unchanged;
- external-consumer normalization audit — **PASS**, all 18 changed Kotlin files outside `:core:artwork`
  normalize byte-for-byte to the baseline after reversing only the package-path migration.

## User-owned host Gradle verification — accepted

On 2026-09-15, the user ran the required focused Gradle cone from the real repository after applying the
cleanup patch:

```powershell
.\gradlew.bat :core:artwork:testDebugUnitTest `
  :core:artwork:compileDebugKotlin `
  :feature:catalog:compileDebugKotlin `
  :feature:catalog:compileDebugAndroidTestKotlin `
  :app:compileDebugKotlin `
  --no-daemon
```

Returned host evidence:

```text
BUILD SUCCESSFUL in 1m 2s
105 actionable tasks: 11 executed, 94 up-to-date
Configuration cache entry stored.
```

This closes the only open host Gradle verification item for the `:core:artwork` cleanup. The package
migration therefore compiles through `:core:artwork`, the direct `:feature:catalog` production and Android-
test consumers, and the app composition consumer while the focused artwork unit-test task is green.

The repository-wide `verify-source-layout.sh` still has the pre-existing, out-of-scope
`feature/catalog/src/androidTest/.../LegacyArtworkTestAdapters.kt` generation-labelled filename debt. That
adjacent Catalog test adapter is not part of the `:core:artwork` acceptance boundary and remains for the
`:feature:catalog` cleanup turn.

## Remaining review signals inside `:core:artwork`

`structural-review-report.sh` now reports only review-level signals in the module:

- `runtime/ArtworkRuntime.kt` has a broad import/public-method surface because it remains the intentional
  session/runtime integration boundary;
- `runtime/ArtworkFetcher.kt` and `remote/ArtworkRemotePolicy.kt` have more than 15 imports;
- `remote/ArtworkRemotePolicy.executeHop` remains a >50-line review signal;
- `pipeline/ArtworkPipelineCoordinator.kt` is flagged by the generic broad-name heuristic even though
  coordinator semantics are intentional.

These are not hard policy failures and are not authorization for further splitting in this turn.

## Exact resume boundary

**`:core:artwork` is completed/accepted. STOP before another module and do not begin Step 3 Task 11.**

The cleanup patch plus the user-returned host Gradle evidence close this module-local cleanup boundary.
Commit this module cleanup as its own checkpoint. A later turn must explicitly name and audit the next
primary module, present its target tree, and receive approval before any additional source reorganization
begins. Task 11 remains frozen until the cleanup interlude is explicitly ended.
