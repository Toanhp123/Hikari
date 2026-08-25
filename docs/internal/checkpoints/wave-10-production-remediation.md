# Wave 10 Production Remediation Checkpoint

Date: 2026-08-25

Status: **IMPLEMENTATION PRESENT, FOCUSED VERIFICATION PASSING, FINAL ACCEPTANCE OPEN**

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
| Task 26 - host/device gates and checkpoint | This checkpoint plus Wave 10 roadmap/status updates | Final host/API 26/API 37 matrix open |

Focused execution on the available Redmi Note 9S (Android API 35) changed Tasks 24 and 25 to
passing. Task 26 remains open because the plan requires the complete host matrix and both API 26 and
API 37 device gates.

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

The following complete host commands are recorded from the remediation plan and remain `NOT RUN`:

```bash
bash scripts/check-wave-10-production-policy.sh
./gradlew verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug
```

Required device gates also remain `NOT RUN` on both API 26 and API 37. Focused equivalents passed on
the available API 35 device, but that does not replace the acceptance matrix:

- Schema `10 -> 11` migration and notification claim/recovery instrumentation.
- Android Keystore session-store and guarded WebView authentication instrumentation.
- Notification delivery, permission/channel, deep-link, and navigation instrumentation.
- Discover, Home, Library, Reader, and Downloads regression gates.

## Evidence Ledger

| Evidence | API/environment | Result | Counts/retries |
|---|---|---|---|
| Wave 10 structural production policy | Host | NOT RUN | Not available |
| Architecture, unit, lint, Detekt, assembly | Host | NOT RUN | Not available |
| Missing-auth-policy credential regression | Host | RED then PASS | 1 failing before fix; runtime HTTP/auth suite PASS |
| Redirect credential integration | Host | PASS | 2/2 after replacing virtual-time HTTP execution |
| Phase 7 app integration | Redmi Note 9S, API 35 | PASS | 6/6 after correcting test-asset context; one rerun |
| Notification claim recovery | Redmi Note 9S, API 35 | PASS | 2/2; zero retries |
| Clean-data Discover refresh | Redmi Note 9S, API 35 | PASS | Both catalog snapshots present; zero plugin diagnostics |
| Session/auth/notification app instrumentation | API 26 | NOT RUN | Not available |
| Room migration/claim recovery instrumentation | API 26 | NOT RUN | Not available |
| Session/auth/notification app instrumentation | API 37 | NOT RUN | Not available |
| Room migration/claim recovery instrumentation | API 37 | NOT RUN | Not available |
| Existing feature regression matrix | Host/device | NOT RUN | Not available |

## Acceptance Decision

Wave 10 is **not yet accepted or closed**. Source and integration contracts are present, but the
plan explicitly requires successful host, API 26, and API 37 evidence. Replace the `NOT RUN` entries
with exact commands, device identifiers, test counts, failures, fixes, reruns, and the final accepted
SHA before marking the wave complete or advancing Wave 11 from this boundary.

**Wave 11 is blocked. Do not start or advance Wave 11 until every remaining Wave 10 acceptance gate
passes and this checkpoint is explicitly marked accepted and closed.**
