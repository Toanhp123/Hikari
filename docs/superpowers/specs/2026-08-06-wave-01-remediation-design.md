# Wave 01 Remediation Design

Date: 2026-08-06
Status: Approved for implementation

## Goal

Restore Wave 01 as a reliable architecture and verification gate before any later-wave remediation continues. The work fixes the stale module-boundary check, standardizes the Android application identity on `app.openstory`, adds min/target SDK instrumentation gates, and makes future module additions fail closed until their architecture policy and documentation are updated.

## Decisions

- `namespace` and `applicationId` become `app.openstory`.
- Existing `com.example.hikari` installations are not migrated and may coexist as a separate application.
- Repository/root project name remains `Hikari`.
- Direct project dependencies are controlled by one versioned policy file.
- Gradle reads actual dependency declarations; Bash remains only a stable entry point.
- Fast verification runs on every change; Wave checkpoint verification additionally requires API 26 and API 37 instrumentation.
- A wave cannot be marked complete when any required evidence is `NOT RUN`.

## Architecture

### Versioned module policy

`config/architecture/module-boundaries.json` is the source of truth for:

- every included Gradle module;
- each module directory;
- platform classification;
- allowed direct production project dependencies;
- allowed direct test project dependencies;
- forbidden production import prefixes.

The policy has `schemaVersion = 1`. Unknown policy versions fail closed.

### Build-logic enforcement

The root applies `openstory.architecture`. It registers:

- `verifyModuleBoundaries`: compares the policy with the actual Gradle project/dependency model and scans restricted production imports;
- `verifyApplicationIdentity`: verifies `app.openstory` namespace/application ID and rejects the legacy production token;
- `verifyArchitecture`: aggregate architecture gate.

The verifier reports stable machine codes:

- `module_policy.missing_module`
- `module_policy.stale_module`
- `module_policy.path_mismatch`
- `module_policy.platform_mismatch`
- `module_policy.production_dependency_denied`
- `module_policy.production_dependency_allowance_stale`
- `module_policy.test_dependency_denied`
- `module_policy.test_dependency_allowance_stale`
- `module_policy.unknown_dependency_configuration`
- `module_policy.platform_import_denied`
- `application_identity.namespace_mismatch`
- `application_identity.application_id_mismatch`
- `application_identity.legacy_token`

### Verification tiers

`scripts/verify.sh` runs the fast gate:

1. architecture policy;
2. build-logic tests;
3. all JVM tests;
4. all Android local unit tests;
5. Android lint;
6. Detekt;
7. debug APK assembly;
8. strict dependency verification.

`scripts/verify-instrumentation.sh <api>` validates the connected device API, runs app instrumentation, installs the debug APK, launches the launcher activity, and verifies the process starts.

`scripts/verify-wave-checkpoint.sh` runs the fast gate and then API 26 and API 37 instrumentation using explicitly supplied device serials.

CI has independent verify, API 26, and API 37 jobs. A final checkpoint job succeeds only when all three complete successfully.

## Instrumentation scope

Wave 01 instrumentation proves:

- the app installs and launches;
- Hilt/Application initialization does not crash;
- Home is the initial destination;
- navigation reaches Library and Plugins;
- the selected top-level route survives activity recreation.

No live network or plugin execution is used.

## Module onboarding rule

A commit that adds a module must update together:

- `settings.gradle.kts`;
- `config/architecture/module-boundaries.json`;
- module build script and tests;
- README module map;
- CI report coverage when applicable;
- the current wave checkpoint evidence.

An included module without policy is a hard failure.

## Completion gate

Wave 01 remediation is complete only when:

- policy and actual graph agree;
- `:core:plugin-host -> :core:network` is explicitly allowed;
- a denied dependency regression test passes;
- app identity is `app.openstory`;
- no production reference to `com.example.hikari` remains;
- fast verification passes under JDK 17;
- API 26 instrumentation passes;
- API 37 instrumentation passes;
- CI uses the same entry points;
- checkpoint evidence records exact commands and results.
