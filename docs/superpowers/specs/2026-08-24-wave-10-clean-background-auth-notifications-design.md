# Wave 10 Clean Background Work, Authentication, and Notifications Design

Status: **APPROVED FOR PLAN REBASELINE**

## Purpose

Wave 10 adds local background work, plugin-scoped authentication, and local notifications while
cleaning the capability boundaries that the current code does not yet expose. The implementation
must not pretend that a missing port already exists or preserve a hard-coded policy merely to keep
the diff small. When Wave 10 needs a policy, the consuming capability declares a narrow port, `:app`
adapts `:settings` to it, and the existing consumer migrates to that port in the same task.

## Goals

- Persist typed user policy for languages, background work, notifications, Reader behavior, and
  storage limits.
- Make existing Reader and automatic-cache behavior consume those policies through capability-owned
  ports.
- Schedule bounded, unique, resumable local work without duplicating existing capability engines.
- Add guarded WebView login with encrypted, plugin- and request-target-scoped sessions.
- Persist chapter-change evidence atomically with chapter graph mutations and deliver notifications
  through a recoverable local drain.
- Expose settings, session, permission, delivery, and storage status without adding a top-level
  destination.

## Non-goals

- Cloud accounts, remote synchronization, push notifications, or a backend.
- A generic `:sync` module.
- Full plugin installation/update/rollback UI; Wave 11 still owns plugin management presentation.
- Keeping obsolete SavedState-only preferences or hard-coded cache quota behavior after their ports
  are connected.
- Supporting arbitrary WebView browsing, arbitrary credential headers, or plaintext session files.

## Module Boundary

Wave 10 enters with 14 production modules and exits with 16 by adding only `:settings` and
`:feature:settings`.

```text
:settings -> :core:common

:reader declares ReaderPreferencesPort
:downloads declares CachePolicyPort
:settings declares BackgroundWorkSchedulePort
:settings declares PluginSessionControlPort
:settings declares NotificationControlPort

:feature:reader -> existing Reader boundary; it never imports :settings
:feature:settings -> :core:designsystem + :settings + :downloads

:app adapts :settings to ReaderPreferencesPort and CachePolicyPort
:app implements WorkManager, WebView, permission, notification, and navigation adapters
:plugins:runtime owns encrypted session state and credential composition
:storage:room owns chapter-change outbox and notification-delivery persistence
```

`ReaderPreferencesPort` and `CachePolicyPort` are declared by their consumers. This keeps
`:reader` and `:downloads` independent of a later policy module and makes the dependency inversion
visible in code rather than prose.

## Typed Settings

`AppSettings` is immutable and contains explicit defaults and validation for:

- ordered content-language preferences;
- periodic chapter-check cadence selected from `1`, `3`, `6`, `12`, or `24` hours;
- unmetered-network and battery-not-low requirements;
- notification categories for new canonical chapters and preferred-language releases;
- Reader font scale and preferred-language selection;
- automatic-cache quota bytes;
- whether protected-source background refresh is enabled.

Preferences DataStore is the device-local persistence adapter. Keys remain private to the adapter.
Each field is decoded independently so one malformed value falls back to that field's default while
valid fields survive. A corrupt DataStore file falls back to the complete safe default set and emits
redacted diagnostics. Credentials never enter DataStore.

Repository updates are atomic transformations rather than one setter per storage key:

```kotlin
interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
```

## Reader And Cache Policy Cleanup

The current Reader stores font scale only in `SavedStateHandle` and does not supply the existing
`ReleaseSelectionPolicy.languageOrder`. Wave 10 replaces that behavior with:

```kotlin
interface ReaderPreferencesPort {
    val preferences: Flow<ReaderPreferences>
    suspend fun setFontScale(value: Float)
}

data class ReaderPreferences(
    val fontScale: Float,
    val languageOrder: List<String>,
)
```

`SavedStateHandle` remains responsible only for navigation/restoration state such as chapter and
release IDs. Reader font scale is user policy and moves to the port. The Reader applies live policy
updates and uses language order when constructing `ReleaseSelectionPolicy`.

The automatic Reader cache currently uses a hard-coded quota. `:downloads` introduces:

```kotlin
fun interface CachePolicyPort {
    suspend fun current(): CachePolicy
}

data class CachePolicy(val quotaBytes: Long)
```

`DownloadAwareReaderDocumentStore` reads the current policy before quota enforcement. The old
constructor default remains only as the adapter's safe fallback, not as the normal production
policy source.

## Background Work Model

Existing capability engines remain authoritative:

- `ContentMappingService.automate` owns mapping behavior.
- `ChapterSyncService.sync` owns source isolation, paging, aggregation, and sync state.
- `DownloadService` owns queue, cancellation, retry classification, and storage admission.
- the canonical engine keeps its durable queue/outbox/lease path.

WorkManager workers deserialize stable identifiers, call one capability service, and translate the
result. They do not contain matching, chapter, download, notification, or quota policy.

The existing unique names remain unchanged:

```text
library-mapping:<storyId>
initial-chapter-sync:<storyId>
chapter-download:<releaseId>
canonical-engine-drain
canonical-engine-safety
```

Wave 10 adds stable names for periodic dispatch and notification delivery:

```text
library-chapter-periodic
library-chapter-continuation
chapter-notification-drain
```

The periodic worker is a bounded dispatcher. It snapshots eligible Library stories, joins their
chapter-sync timestamps, sorts by oldest successful check and stable Story ID, and schedules at most
20 story-specific sync items per invocation. A compact cursor containing the last selected
timestamp/null bucket and Story ID is passed to one unique continuation chain; the full candidate
list is never serialized into WorkManager `Data`. Story-specific unique work and WorkManager backoff
isolate failures, while cursor advancement prevents a failing early story from starving the rest of
the Library during the same periodic cycle.

Changing settings updates the one periodic registration exactly once. Disabling background work
cancels only the periodic dispatcher and continuation; it does not cancel explicit downloads,
manual refresh, the canonical-engine drain, or canonical safety work.

Manual chapter refresh continues to call `ChapterSyncService.sync` directly for immediate UI
feedback. Initial and periodic work call the same service through workers. Sharing an engine does not
require routing every user action through WorkManager.

## Authentication Contract

Authentication is an optional manifest capability with a safe default of absent. It declares:

- one HTTPS login start URL;
- exact lowercase HTTPS navigation hosts;
- an exact completion host and normalized path prefix;
- exact credential hosts and normalized path prefixes;
- an allowlist of cookie names captured for each credential target;
- a bounded local session TTL.

Authentication navigation hosts may include an identity provider that is not a runtime HTTP target.
Credential targets must be a subset of the plugin's declared network hosts. Wildcards, user-info,
cleartext URLs, arbitrary ports, file/content URLs, and unbounded path rules are rejected.

The existing host-only credential callback is insufficient for cookie scoping and is replaced with
a request-target contract:

```kotlin
data class ManagedCredentialRequest(
    val pluginId: PluginId,
    val url: String,
)

fun interface ManagedCredentialProvider {
    suspend fun headers(request: ManagedCredentialRequest): Map<String, String>
}
```

The runtime validates the URL before asking for credentials. The session provider returns only the
`Cookie` header and only when plugin ID, HTTPS host, normalized path, cookie allowlist, session TTL,
and authentication-policy fingerprint match. Script-owned `Authorization`, `Cookie`, and
`Proxy-Authorization` headers remain forbidden.

`CompositeManagedCredentialProvider` merges providers in fixed order and rejects duplicate
case-insensitive header ownership. This preserves the existing app-owned MyAnimeList client ID while
preventing a session provider from silently replacing another managed credential.

## WebView And Encrypted Session Lifecycle

Only one dedicated login capture activity may run at a time because Android WebView cookies are
process-global. Before and after each login it clears cookies, WebStorage, cache, history, form data,
and transient WebView state. It disables file/content access, mixed content, popups, downloads,
geolocation, media capture, debugging in release, and JavaScript interfaces. Navigation is blocked
unless it matches the manifest declaration.

After the completion URL is reached, the activity reads only the declared cookie names for declared
credential target URLs. Persisted records are encrypted with Android Keystore AES-GCM in
`noBackupFilesDir`, written atomically, and bind plugin ID, target host/path, cookie name, and the auth
policy fingerprint as authenticated data. Key invalidation or unreadable ciphertext clears the
affected session and returns a logged-out summary without exposing secrets.

Logout deletes every record for the plugin. A plugin update or rollback whose authentication-policy
fingerprint differs also invalidates the old session. Package disablement immediately prevents
credential delivery. Full plugin removal in Wave 11 must call the same runtime-owned clear command.

Adding the optional manifest field keeps old packages valid for the new host, but old fail-closed
hosts cannot parse new authentication declarations. SDK versioning documentation must state this
forward-host compatibility limit explicitly instead of describing the change as universally
backward-compatible.

## Transactional Chapter-Change Outbox

Notification evidence must be created atomically with chapter graph mutations. `ChapterMutation`
continues to describe the intended graph change. The Room adapter compares the pre-commit graph with
the committed graph and inserts raw outbox rows in the same Room transaction for:

- a newly created non-tombstoned canonical chapter;
- a newly linked release on an existing canonical chapter;
- restoration of a tombstoned canonical chapter.

Raw events are facts, not user-facing notification decisions. Their deterministic event key is
derived from story ID, canonical chapter ID, release ID when applicable, change kind, and the chapter
commit fingerprint. A unique index makes commit retries idempotent.

`MIGRATION_10_11` creates both the raw chapter-change outbox and notification-delivery state. No
second Wave 10 migration is introduced. The migration preserves every schema-10 canonical queue,
lease, catalog-change outbox, Library, Chapter, Reader, and Download row.

`:chapters` declares a best-effort `NotificationDrainScheduler` wake port. A successful chapter
commit asks the port to schedule delivery, while durable pending rows and application-start
registration recover a lost wakeup. Scheduling failure never rolls back the chapter transaction.

## Classification And Delivery

The pure classifier consumes raw change facts, current notification settings, language order,
chapter progress, and current chapter graph state. It produces at most one new-chapter notification
per canonical chapter and optionally one preferred-language-release notification when the chapter
was not newly created in the same classification group.

Tombstoned, deleted, already-read, stale, or no-longer-resolvable targets are consumed without a
platform notification. Story redirects are resolved before deep-link construction.

Delivery uses a unique `chapter-notification-drain` worker. Claiming a batch, publishing a
notification, and recording the result use durable claim tokens so process death can retry safely.
Android notification IDs are persisted or collision-resolved; they are not an unchecked 32-bit hash.

Permission denial or a disabled channel transitions the event to `IN_APP_ONLY`. It does not remain
`PENDING`, consume an attempt, or later create a burst of historical notifications when permission is
granted. In-app status can still show the event.

Notification PendingIntents are explicit and immutable. Route values are treated as untrusted input,
parsed into stable ID types, resolved through current repositories, and rejected if the story/chapter
target no longer exists. Signing identifiers is not treated as authorization because these IDs are
not secrets.

## Settings And Status UI

`:feature:settings` consumes only public settings, downloads, session-control, notification-control,
and storage-summary contracts. It never imports WorkManager, Room, WebView, notification manager, or
plugin runtime internals.

The screen includes:

- background cadence and network/battery policy;
- preferred content languages and Reader font scale;
- notification categories, permission/channel status, and recent in-app-only events;
- storage usage and automatic-cache quota;
- plugin session summaries with login/logout actions.

Settings is `AppRoute.Settings`, entered from `HikariUtilitySheet`. It is never added to
`TopLevelDestination`. The route is pushed onto the active top-level stack, so Back returns to the
screen from which the utility sheet was opened while preserving that top-level tab's history.

## Error And Recovery Rules

- A DataStore field error falls back only that field; total corruption falls back all settings.
- WorkManager scheduling failure never rolls back an already committed capability action.
- A plugin/source failure is recorded and isolated; only retryable global conditions retry a batch.
- Session decryption/key failure logs out the affected plugin rather than exposing or retaining
  unreadable credentials.
- Chapter commit and raw change evidence succeed or fail in one transaction.
- Notification delivery may be retried after process death, but permission denial never creates a
  retry loop.

## Verification Requirements

Implementation evidence must cover:

- exact 16-production-module graph and Room schema 11;
- Reader preference and cache quota migration from current hard-coded/local state;
- stable existing WorkManager names plus bounded periodic continuation;
- plugin/host/path/cookie scope and auth-policy update invalidation;
- schema `10 -> 11` preservation and atomic chapter-change outbox insertion;
- process-death-safe notification drain, permission denial, stable IDs, and validated deep links;
- API 26 and API 37 device behavior;
- unchanged Discover/Home/Library top-level navigation.
