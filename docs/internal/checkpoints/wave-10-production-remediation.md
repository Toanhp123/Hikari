# Wave 10 Production Remediation Checkpoint

Date: 2026-08-25

Status: **VERIFIED/CLOSED; REQUIRED API 26/API 37 DEVICE MATRIX PASS; FINAL HOST ACCEPTANCE PASS**

## Scope

This checkpoint tracks Phase 7 of
`../../superpowers/plans/2026-08-24-wave-10-production-remediation-implementation-plan.md` on
`feat/wave-10-production-remediation`. The source baseline before Phase 7 is `078f1b7`, which
contains remediation Phases 0-6.

The Phase 7 source additions cover:

- Android Keystore session persistence through Settings observation and runtime `auth.getState`.
- Cookie delivery restricted to the declared plugin, HTTPS host, normalized path, allowlisted cookie,
  enabled package, live TTL, and current authentication-policy fingerprint.
- Stable 20-item chapter-dispatch pages across more than 40 due stories, enqueue-failure isolation,
  and cancellation propagation.
- Durable notification recovery after an abandoned claim, terminal `IN_APP_ONLY` permission denial,
  no historical publish after a later grant, and collision-safe persisted notification IDs.

## Phase 7 Coverage

| Plan task | Source evidence | State |
|---|---|---|
| Task 24 - production auth and redirects | `PluginSessionRuntimeIntegrationTest`, `PluginCredentialRedirectIntegrationTest`, authenticated-content manifest fixture | Focused host/API 35 PASS |
| Task 25 - bounded scheduling and notification recovery | `PeriodicChapterDispatchIntegrationTest`, `NotificationRecoveryIntegrationTest`, `NotificationClaimRecoveryTest` | Focused API 35 PASS |
| Task 26 - host/device gates and checkpoint | This checkpoint plus Wave 10 roadmap/status updates | PASS; device matrix and final host acceptance GREEN |

Focused execution on the available Redmi Note 9S (Android API 35) changed Tasks 24 and 25 to
passing. The required API 26 and API 37 connected matrix is developer-confirmed GREEN. HES M7.1 restored
standalone Detekt and the canonical combined host command, so Task 26 is complete.

## Discover Clean-Install Remediation

A clean-data launch exposed an auth-composition regression shared by both bundled catalog plugins.
`PluginSessionManagedCredentialProvider` asked `DefaultPluginSessionService` for a session on every
validated HTTP request. Plugins without an authentication capability caused policy lookup to throw,
and `CompositeManagedCredentialProvider` correctly redacted that exception to
`plugin.http_credentials_failed`, preventing the request from reaching the network.

The remediation makes an absent authentication policy mean "no session-managed credentials" and
adds a regression test for that contract. A clean-data device verification then produced persisted
home snapshots for both `org.openstory.catalog.mangaupdates` and
`org.openstory.catalog.myanimelist`, with zero rows in `plugin_diagnostics` and visible Discover
content.

## Required Acceptance Commands

The Wave 10 acceptance contract itself is unchanged:

```bash
bash scripts/check-wave-10-production-policy.sh
./gradlew verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
```

R0 initially could not start the Gradle command in the supplied sandbox. Developer-host evidence on
2026-08-26 supersedes that environment-only state for the final HES tree: focused M7 Gradle suites,
Reader/Feature/Downloads/App/build-logic regression, app compile, architecture, and structural policy
all run successfully. The first canonical combined host run exposed 74 blocking Detekt issues.

HES M7.1 repaired those findings without Detekt suppression/config weakening/baseline growth. Standalone
Detekt is GREEN, and the original command above reran **unchanged** to `BUILD SUCCESSFUL in 3m 34s`.

Required device gates are now developer-confirmed **PASS** on both API 26 and API 37:

- Schema `10 -> 11` migration and notification claim/recovery instrumentation.
- Android Keystore session-store and guarded WebView authentication instrumentation.
- Notification delivery, permission/channel, deep-link, and navigation instrumentation.
- Discover, Home, Library, Reader, and Downloads regression gates.

The closure confirmation did not include the raw device-model/test-count transcript, so this checkpoint
records PASS without inventing counts.

## Evidence Ledger

| Evidence | API/environment | Result | Counts/retries |
|---|---|---|---|
| Wave 10 structural production policy | Developer host / final HES tree | PASS | zero policy violations |
| Focused M7 engine + Reader/runtime Gradle suites | Developer host / final HES tree | PASS | `BUILD SUCCESSFUL` in 30s and 48s |
| Reader/Feature/Downloads/App/build-logic regression | Developer host / final HES tree | PASS | `BUILD SUCCESSFUL` in 1m 47s |
| App Kotlin compile | Developer host / final HES tree | PASS | `BUILD SUCCESSFUL` in 8s |
| `verifyArchitecture` + package/current-architecture contracts | Developer host / final HES tree | PASS | 18 modules; 17 production + 1 android-test; Room 1..11 |
| Standalone Detekt after M7.1 | Developer host / final HES tree | **PASS** | `BUILD SUCCESSFUL in 16s`; zero blocking issues |
| Canonical combined host acceptance | Developer host / final HES tree | **PASS** | unchanged command; `BUILD SUCCESSFUL in 3m 34s`; 743 actionable tasks |
| Standalone tests + lint + assembly after Detekt split | Developer host | PASS | `BUILD SUCCESSFUL in 2m 42s` |
| Instrumentation compile | Developer host | PASS | storage Room + app androidTest Kotlin compile; `BUILD SUCCESSFUL in 9s` |
| Exploratory connected aggregate | Redmi Note 9S, API 35 | **PASS AFTER TEST-ONLY REPAIR** | MyAnimeList catalog contract now selects provider by plugin id; developer-confirmed rerun |
| Missing-auth-policy credential regression | Host | RED then PASS | 1 failing before fix; runtime HTTP/auth suite PASS |
| Redirect credential integration | Host | PASS | 2/2 after replacing virtual-time HTTP execution |
| Phase 7 app integration | Redmi Note 9S, API 35 | PASS | 6/6 after correcting test-asset context; one rerun |
| Notification claim recovery | Redmi Note 9S, API 35 | PASS | 2/2; zero retries |
| Clean-data Discover refresh | Redmi Note 9S, API 35 | PASS | Both catalog snapshots present; zero plugin diagnostics |
| Session/auth/notification app instrumentation | API 26 | PASS | developer-confirmed final matrix; exact count not reproduced |
| Room migration/claim recovery instrumentation | API 26 | PASS | developer-confirmed final matrix; exact count not reproduced |
| Session/auth/notification app instrumentation | API 37 | PASS | developer-confirmed final matrix; exact count not reproduced |
| Room migration/claim recovery instrumentation | API 37 | PASS | developer-confirmed final matrix; exact count not reproduced |
| Full Discover/Home/Library/Reader/Downloads device regression matrix | API 26/API 37 | PASS | developer-confirmed final matrix; exact count not reproduced |

## Acceptance Decision

Wave 10 is **VERIFIED/CLOSED**. The earlier sandbox wrapper blocker was superseded by developer-host
execution, and HES M7.1 closed the Detekt debt as a separate patch without weakening Wave 10 acceptance.

The required API 26/API 37 connected matrix is satisfied. Both final host conditions are true on the final tree:

1. M7.1 makes standalone Detekt GREEN without blanket suppressions/config weakening/baseline growth — **PASS**;
2. the original combined host command reruns unchanged and is GREEN — **PASS**.

**Wave 11 is unblocked from this accepted checkpoint.**

## Final HES-tree rebase evidence — M7 source tree

Adaptive Reader/HES R0 selected the acceptance-rebase path. Fresh developer-host verification supplied
on 2026-08-26 now supersedes the earlier sandbox-only `NOT RUN` status for a substantial part of the host
matrix:

```text
focused M7 engine Gradle suite:                         BUILD SUCCESSFUL
focused M7 Reader/runtime Gradle suite:                BUILD SUCCESSFUL
Reader/Feature/Downloads/App/build-logic regression:   BUILD SUCCESSFUL
:app:compileDebugKotlin:                                BUILD SUCCESSFUL
verifyArchitecture:                                    BUILD SUCCESSFUL
package/current-architecture mutation contracts:       PASS
performance lifecycle policy:                          PASS
Wave 10 production policy:                             PASS
```

The original broad repository host command was also run, so this is no longer an environment blocker:

```bash
./gradlew test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
# FAIL at :detekt
# Analysis failed with 74 issues.
```

The Detekt debt is explicitly deferred to **HES M7.1 — Detekt Debt Closure** so it can be repaired as a
small, reviewable quality patch instead of being mixed into the HES routing freeze. This deferral does
**not** weaken Wave 10's final command. M7.1 made standalone Detekt GREEN, then the original combined host
acceptance command above reran unchanged and passed.

```text
./gradlew detekt --no-daemon
BUILD SUCCESSFUL in 16s

./gradlew verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon
BUILD SUCCESSFUL in 3m 34s
743 actionable tasks: 198 executed, 545 up-to-date
```

Standalone test/lint/assembly after excluding Detekt and instrumentation compile are GREEN on the
developer host. The pre-M7 MyAnimeList catalog contract defect was repaired test-only by selecting the
provider by plugin id, and the connected rerun was developer-confirmed GREEN. The required API 26/API 37
matrix is also developer-confirmed GREEN for schema 10->11/notification claim-recovery, guarded
auth/Keystore, notification delivery/navigation, and Discover/Home/Library/Reader/Downloads regression
requirements.

Wave 10 decision is **VERIFIED/CLOSED; Wave 11 unblocked**. M7.1 source cleanup and the final unchanged
combined host gate are developer-host verified GREEN. Detailed M7/M7.1 evidence is recorded in
`adaptive-reader-continuity-hes-v1.md`.
