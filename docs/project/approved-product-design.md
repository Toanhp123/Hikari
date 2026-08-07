<!--
CANONICAL PRODUCT BASELINE
Source: approved planning package dated 2026-08-03.
The text below is preserved verbatim. Repository-specific remediation and current
implementation status are documented separately and do not silently rewrite this
approved product scope.
-->

# Android Unified Novel Library Design

Date: 2026-08-03
Status: Approved design baseline

## Goal

Build an open-source Android application that acts as a unified discovery,
library, chapter-tracking, and reading tool for light novels and web novels.
The application separates catalog metadata from readable chapter sources,
combines releases from multiple content plugins into one chapter list, works
without an account or cloud service, and remains useful when individual
websites or plugins fail.

The MVP is complete when a user can discover a novel from a catalog, add it to
a local library, let the application find matching content through installed
plugins, see releases from multiple sources grouped under canonical chapters,
read a selected release, retain reading progress and offline content, and
receive a local notification when a genuinely new chapter or preferred-language
release appears.

## Product Thesis

The application is not a fixed-site reader and does not treat one website as
the permanent source for an entire novel.

It has three independent responsibilities:

1. Catalog providers explain what a work is and help users discover it.
2. Content plugins expose readable releases from websites.
3. The Android host normalizes works and chapters, groups equivalent releases,
   stores local state, selects sensible defaults, and preserves user control.

The core product model is therefore:

```text
Catalog entries
    -> CanonicalStory
        -> ContentMappings from installed plugins
            -> ChapterRelease records
                -> CanonicalChapter groups
```

A canonical chapter may contain several releases in different languages, from
different websites or translation groups. The user can choose any available
release without changing the source of the whole novel.

## Approved Product Decisions

### Platform and distribution

- The first application is Android-only.
- The native stack is Kotlin with Jetpack Compose.
- The first public builds are APK releases distributed through the project
  website or GitHub releases.
- Google Play distribution is considered only after the plugin system and
  policy surface are stable.
- The complete application, plugin SDK, bundled plugins, examples, and
  documentation are open source.

### Local-first operation

- The application requires no account.
- Library state, progress, plugin configuration, sessions, cache, and downloads
  are stored on the device.
- There is no cloud synchronization, central chapter-checking service, or push
  notification backend in the MVP.
- Android background work and manual refresh are the only chapter-update
  mechanisms.

### Content scope

- The domain model is extensible to anime, manga, light novels, and web novels.
- The MVP exposes and reads only light novels and web novels.
- Manga image reading, anime tracking, and media playback are outside the MVP.
- Catalog language and readable-source language are configured independently.
- Users may set an ordered source-language preference, for example Vietnamese,
  then English, then any other language.

### Discovery

- The application ships with at least one bundled catalog plugin so a clean
  install has a usable Home experience.
- Users may install additional community catalog plugins.
- Home has a combined default view and a separate page for every enabled
  catalog.
- The combined Home retains source-owned sections such as trending and top
  rated, and may also expose a deterministic aggregate ranking.
- Duplicate appearances of the same work are represented by one
  `CanonicalStory`, while catalog-specific scores, rankings, descriptions, and
  identifiers remain visible separately.
- Personalized recommendation algorithms are not part of the MVP.

### Plugin ecosystem

- Catalog providers and content sources are both plugins.
- Simple plugins use a declarative configuration and selectors.
- Complex plugins execute JavaScript inside a restricted sandbox.
- Native Android plugins are not supported.
- There is no mandatory central plugin store or central approval service.
- Users can install plugins by manifest/repository URL, local plugin package,
  or a community repository index.
- Each plugin has one of three update modes: manual, ask before updating, or
  automatic. The default is ask before updating.
- The application displays version changes, new permissions, and new domains
  before an update is accepted.
- At least one previous working plugin version is retained for rollback.

### Reading and releases

- A story has no permanent primary content source.
- Equivalent releases are grouped at chapter level, similar to a release list:
  one canonical chapter with multiple selectable releases.
- Reading progress belongs to the canonical chapter, while the exact release
  and scroll position used are also retained.
- The default release is selected from the user's language preferences and
  recent choices, but every available release remains visible.
- A user can filter releases by language, plugin, or translation/uploader group
  and can pin a preferred language.

## MVP Scope

### Included

1. Combined Home and catalog-specific pages.
2. Catalog search, filters, rankings, and story detail metadata.
3. Local library with reading status and metadata-only entries.
4. Catalog and content plugin installation, updates, rollback, diagnostics,
   enable/disable, and removal.
5. Story matching between catalog entries and installed content plugins.
6. Fast recent-chapter fetch followed by full background synchronization.
7. Chapter aggregation into canonical chapters with multiple releases.
8. A text reader with typography, theme, navigation, source switching, and
   exact progress restoration.
9. Automatic cache and explicit offline downloads.
10. Manual refresh and scheduled local background checks.
11. Local notifications for canonical new chapters and preferred-language
    releases.
12. WebView-based login for sources that require a user session.
13. Import by URL and manual correction of story/chapter mappings.

### Excluded

- Accounts and cloud synchronization.
- Centralized plugin moderation or remote plugin disable infrastructure.
- Manga image reader and anime functionality.
- TTS, audiobook generation, translation, and AI summaries.
- Social features, comments, or an application-owned rating system.
- Native-code plugins or unrestricted JavaScript execution.
- Automatic bypass of paywalls, DRM, CAPTCHAs, or access controls.
- Cross-device download transfer.
- Complex personalized Home recommendations.

## Primary User Journeys

### Discover and add a story

1. The user opens the combined Home or a catalog-specific page.
2. The user opens a catalog entry.
3. The host finds or creates its `CanonicalStory`.
4. The story is added to the local library immediately, even when no readable
   source has been found.
5. The host queries a small ordered set of preferred content plugins for fast
   results.
6. High-confidence matches are linked automatically; uncertain matches are
   presented for confirmation.
7. Recent releases are fetched and displayed first.
8. Remaining enabled plugins and the complete release history are synchronized
   in background work.
9. Later plugin installations or updates cause metadata-only library entries to
   be searched again.

### Browse and read a chapter

1. The story page displays one canonical chapter list.
2. The user may pin a language or filter by language, plugin, or group.
3. Expanding a canonical chapter displays every known release.
4. Opening the chapter invokes deterministic release selection.
5. The reader loads an offline copy, cache entry, or plugin content in that
   order.
6. The host records canonical progress, release identity, paragraph/scroll
   position, and timestamp.
7. Moving to the next chapter prefers a compatible release from the same source
   or group, but falls back without blocking navigation.
8. The user may switch releases from inside the reader.

### Receive an update

1. Scheduled Android work selects due library mappings under network, battery,
   and rate-limit constraints.
2. Each plugin is synchronized independently.
3. New `ChapterRelease` records are normalized and grouped.
4. A new `CanonicalChapter` produces a new-chapter event.
5. A new release for an existing chapter produces a release event only when it
   matches a user-notification rule, such as the first Vietnamese release.
6. Duplicate releases never increase the unread canonical chapter count.
7. Local notifications deep-link to the relevant story and chapter.

## Architecture

### Android application layers

```text
Compose UI
    -> Presentation state and navigation
        -> Application use cases
            -> Canonical domain and aggregation engine
                -> Plugin host, repositories, Room, files, WorkManager
```

The boundaries are intentionally one-directional:

- Compose screens depend on use cases, not Room entities or plugin payloads.
- Plugin payloads are converted into domain values before they enter the
  library or reader.
- Aggregation logic is pure Kotlin and does not depend on Android framework
  types.
- Room and filesystem adapters implement storage interfaces owned by the
  domain/application layer.
- Background jobs invoke the same synchronization use cases as manual refresh.

### Recommended Android components

- Jetpack Compose for UI.
- Navigation Compose for application routes and deep links.
- Room for normalized local metadata and state.
- WorkManager for scheduled and deferred synchronization.
- Android WebView for source authentication flows.
- Android Keystore-backed encrypted storage for sensitive session material.
- A bounded application-owned directory for cache, plugin packages, retained
  versions, and offline content.

No cloud SDK is required for the MVP.

## Plugin Model

### Plugin kinds

A manifest declares one or both capabilities:

```text
CATALOG
CONTENT
```

A catalog plugin may provide:

- Home sections;
- search and filters;
- catalog story details;
- rankings and ratings;
- stable catalog identifiers;
- optional direct mappings to content plugins.

A content plugin may provide:

- story search;
- story details required for matching;
- recent release listing;
- complete release listing;
- incremental synchronization when available;
- chapter text retrieval;
- optional authenticated sessions.

A single package may expose both capabilities, but the contracts remain
separate.

### Plugin execution forms

#### Declarative plugin

A declarative plugin contains manifest data, request definitions, selectors,
normalization rules, and pagination rules. It cannot execute arbitrary code.
This is the preferred form for simple websites.

#### JavaScript sandbox plugin

A JavaScript plugin runs in an isolated runtime that exposes a small host API.
It receives plain values and returns contract payloads. It has no direct access
to Android classes, Java reflection, process execution, arbitrary files,
application databases, other plugins, or unrestricted networking.

### Required manifest fields

```text
id
name
version
apiVersion
capabilities[]
languages[]
allowedDomains[]
permissions[]
entrypoints
updateUrl or repository metadata
package checksum
optional publisher signature
minimumHostVersion
```

Every stable external identifier returned by a plugin is namespaced by the
plugin ID.

### Host API and permissions

The sandbox host exposes only capability-oriented operations:

- HTTP requests to declared domains;
- bounded cookie/session access for those domains;
- parsing helpers;
- logging with secret redaction;
- current locale and plugin settings;
- cancellation and timeout signals.

The host enforces:

- domain allowlists including redirect destinations;
- request, response-size, CPU-time, memory, and concurrency limits;
- cancellation when the user disables or updates a plugin;
- no access to contacts, SMS, location, arbitrary device files, clipboard, or
  unrelated WebView data;
- independent storage and cookie partitions per plugin;
- redacted diagnostics that do not expose passwords, cookies, tokens, or full
  private URLs.

### Authentication

For a source requiring login:

1. The host opens a plugin-scoped WebView limited to declared domains.
2. The user authenticates directly with the source website.
3. The host stores only the resulting session cookies or tokens needed by the
   source.
4. Plugin JavaScript never receives the user's password.
5. The user can inspect session state, sign out, or clear the plugin session.
6. Session data is encrypted at rest and deleted when the plugin is removed,
   unless the user explicitly retains its settings for reinstall.

## Canonical Domain Model

### Story

```text
CanonicalStory
- id
- mediaType
- canonicalTitle
- normalizedTitles[]
- authors[]
- languageHints[]
- status
- coverReference
- createdAt
- updatedAt
```

`CanonicalStory` is application-owned and does not use a catalog or website ID
as its primary key.

### Catalog entry

```text
CatalogEntry
- id
- canonicalStoryId
- pluginId
- externalStoryId
- sourceUrl
- title
- aliases[]
- authors[]
- description
- coverUrl
- genres[]
- mediaType
- publicationStatus
- ratingValue
- ratingScale
- ratingCount
- popularityRank
- rawMetadataVersion
- fetchedAt
```

Multiple catalog entries can belong to one canonical story. Scores are never
silently converted into one authoritative score; an aggregate is a derived
view and the originals remain available.

### Content mapping

```text
ContentMapping
- id
- canonicalStoryId
- pluginId
- externalStoryId
- sourceUrl
- language
- matchOrigin
- matchConfidence
- userDecision
- enabled
- lastSuccessfulSyncAt
- nextEligibleSyncAt
- failureState
```

`matchOrigin` distinguishes direct plugin mapping, automatic matching, URL
import, and user selection. A user-confirmed mapping cannot be replaced by a
later automatic result.

### Canonical chapter

```text
CanonicalChapter
- id
- canonicalStoryId
- chapterKind
- volumeNumber
- chapterNumber
- partNumber
- normalizedTitle
- sortKey
- firstKnownPublishedAt
- readState
```

`chapterKind` includes regular, prologue, epilogue, side story, extra, special,
and unknown. Decimal chapter numbers are preserved. No chapter is forced into
an integer sequence.

### Chapter release

```text
ChapterRelease
- id
- canonicalChapterId
- contentMappingId
- pluginId
- externalReleaseId
- sourceUrl
- language
- title
- volumeNumber
- chapterNumber
- partNumber
- translatorOrUploader
- publishedAt
- updatedAt
- contentFingerprint
- availability
- fetchedAt
```

Several releases may belong to one canonical chapter. A release remains
individually addressable for reading, cache invalidation, health, and offline
storage.

### Reading state

```text
ReadingProgress
- canonicalStoryId
- canonicalChapterId
- chapterReleaseId
- paragraphAnchor
- characterOffset
- scrollRatio
- updatedAt
```

Canonical chapter read state and exact release position are stored together.
Changing release does not mark the chapter unread.

### User aggregation override

```text
AggregationOverride
- canonicalStoryId
- leftReleaseId
- rightReleaseId
- decision
- createdAt
```

A decision is `FORCE_SAME_CHAPTER` or `FORCE_SEPARATE`. Overrides are applied
before automatic aggregation and survive all future synchronizations.

## Story Matching

The host links a catalog story to content results using evidence, not only the
first search result.

Evidence includes:

- normalized primary title and aliases;
- author names;
- media type;
- language;
- cover similarity when available;
- description tokens;
- publication year or status;
- chapter-count and title hints;
- direct mapping supplied by a trusted plugin contract.

The result has one of three outcomes:

- High confidence: link automatically and record the evidence.
- Medium confidence: show a suggested match for user confirmation.
- Low confidence: keep results available for manual selection without linking.

A direct mapping is a strong signal but is still validated for plugin identity,
external ID shape, and media type. User-confirmed links always win.

## Chapter Normalization and Aggregation

### Normalized identity

Each release is parsed into a normalized chapter candidate:

```text
kind + volume + chapter + part + normalized title + sequence evidence
```

Normalization handles common forms such as:

```text
Chapter 010       -> chapter 10
Ch. 10.5          -> chapter 10.5
Volume 2 Ch. 20   -> volume 2, chapter 20
Prologue          -> kind PROLOGUE
Epilogue          -> kind EPILOGUE
Side Story 1      -> kind SIDE_STORY, chapter 1
```

The original title and raw fields are never discarded.

### Aggregation evidence

The engine applies evidence in this order:

1. Explicit mapping supplied by the plugin contract.
2. Persisted user override.
3. Exact normalized kind, volume, chapter, and part.
4. Strong agreement between numeric identity and normalized title.
5. Sequence, publication date, neighboring chapters, and optional text
   fingerprint evidence.

The engine produces a confidence score and explanation.

- High confidence releases are grouped automatically.
- Medium confidence releases remain separate but are offered as a merge
  suggestion.
- Low confidence releases remain separate.

The engine must prefer false negatives over false positives. Two separate rows
are recoverable; a silently merged wrong chapter corrupts reading progress and
notifications.

### Deletions and source changes

A release missing from one synchronization is not immediately deleted. It moves
through availability states such as available, temporarily missing, and
removed after source-specific confirmation. Cached and downloaded content is
not deleted merely because the remote source is unavailable.

## Release Selection

When a user opens a canonical chapter without selecting a release, the host
uses this deterministic priority:

1. Releases matching the pinned or highest-priority language.
2. A release from the same plugin and translation/uploader group used for the
   previous canonical chapter.
3. A release from a user-favored plugin or group.
4. A healthy, available release with complete content.
5. The most recently updated release.
6. A stable plugin-ID and release-ID tie breaker.

If the previous source has no release for the chapter, selection falls through
to the next rule. The UI always exposes the complete release list and explains
the selected source.

## Home and Catalog Aggregation

### Combined Home

The combined Home contains:

- source-owned catalog sections labeled with their provider;
- a deterministic aggregate top section derived from enabled catalog data;
- recent library updates;
- direct navigation to each catalog page.

The aggregate ranking normalizes only for ordering and is never displayed as a
replacement for source ratings. Its inputs and contributing catalogs are
visible.

### Canonical story deduplication

Catalog entries are deduplicated using the same evidence-driven approach as
content matching. High-confidence entries merge automatically; uncertain
entries remain separate until confirmed. A merge preserves all source-owned
metadata and can be reversed.

## Library

A library entry may exist with no content mapping. It supports:

- want to read;
- reading;
- paused;
- completed;
- dropped;
- optional user notes;
- last-read canonical chapter;
- unread canonical chapter count;
- pinned language and release filters;
- per-story background update preference.

When plugins are installed or updated, metadata-only entries become eligible
for matching. The user can also paste a content URL; the host chooses a plugin
only when its domain and URL matcher accept that URL.

## Synchronization

### Fast path and full path

On first link:

1. Fetch recent releases from preferred plugins.
2. Render recent canonical chapters as soon as usable results arrive.
3. Schedule complete history synchronization for all enabled mappings.

On later checks:

- Use plugin cursor, ETag, last-modified token, or stable checkpoint when the
  plugin supports incremental synchronization.
- Otherwise compare a stable release-list fingerprint and fetch only changed
  details when possible.
- A full resynchronization remains available as an explicit recovery action.

### Work scheduling

WorkManager schedules local periodic checks subject to Android's background
constraints. The design does not promise exact wall-clock execution.

The scheduler:

- batches due mappings;
- limits global and per-domain concurrency;
- applies plugin and domain backoff independently;
- respects rate-limit responses;
- stops cleanly when a plugin is disabled or updated;
- records last attempted, last successful, and next eligible times;
- allows manual refresh to reuse the same pipeline with visible progress.

A failure in one plugin never fails unrelated plugins or removes previous data.

## Notifications

The host compares canonical state before and after aggregation.

Notification types are:

- New canonical chapter.
- First release in a user-preferred language for an existing chapter.
- Optional source/group-specific release when explicitly enabled by the user.

A second release of an already-known chapter does not increment the unread
chapter count. Updates to an existing release may show an in-app badge but do
not produce a new-chapter notification by default.

Notifications are grouped per story and deep-link to the canonical chapter or
release list.

## Reader

The MVP text reader supports:

- font family from a bundled safe set;
- font size;
- line height and paragraph spacing;
- light, dark, and reader background modes;
- previous and next canonical chapter;
- release switching;
- exact position restoration;
- read-state marking;
- offline and cache status;
- retry and alternative-release actions.

Remote HTML is sanitized into an application-owned chapter document model
before rendering. Script, style, iframe, form, tracking, and executable content
are not rendered in the reader.

If the active release fails, the reader offers other releases of the same
canonical chapter before treating the chapter as unavailable.

## Cache and Offline Storage

### Cache

- Successfully read chapter documents are cached automatically.
- Cache is bounded by configurable storage size and recency.
- Cache eviction never deletes explicit offline downloads.
- Cache entries are keyed by release identity and content fingerprint.

### Downloads

The user can download:

- one release;
- a selected chapter range;
- all currently known chapters matching a language/source filter.

Downloads retain plugin ID, release ID, content fingerprint, and canonical
chapter linkage. A plugin removal does not delete downloads unless the user
chooses that option.

The storage screen reports usage by story, cache, downloads, covers, and plugin
packages and supports scoped deletion.

## Error Handling and Recovery

Every plugin operation returns a typed outcome:

```text
success
network unavailable
authentication required
rate limited
timeout
contract violation
parsing failure
content unavailable
plugin disabled
cancelled
```

The host records concise user-visible diagnostics and detailed redacted logs.
It never prints cookies, authorization headers, passwords, or unredacted
session URLs.

Recovery behavior includes:

- retry with bounded backoff;
- authenticate again;
- switch to another release;
- roll back a plugin update;
- resynchronize one mapping;
- rebuild aggregation for one story while preserving user overrides;
- clear only one plugin's session or cache.

## Privacy, Safety, and Source Boundaries

- No analytics or telemetry is required by the MVP design.
- No personal account is required.
- Plugin repositories and packages are treated as untrusted input.
- The install UI shows publisher information when available, checksum,
  signature status, domains, capabilities, and permissions.
- Unsigned plugins may be installed only after an explicit warning.
- The application does not claim that a plugin or source is legal merely
  because it is technically compatible.
- The host must not provide mechanisms intended to defeat paywalls, DRM,
  CAPTCHAs, or authentication controls.
- Plugin authors and users remain responsible for source terms, copyright, and
  applicable law; the host provides controls for removal and disabling.

## Testing Strategy

### Domain unit tests

Pure Kotlin tests cover:

- title and author normalization;
- story matching evidence and thresholds;
- chapter-number parsing;
- special chapter kinds;
- aggregation confidence;
- user override precedence;
- deterministic release selection;
- unread-count and notification classification;
- cache and download retention rules.

### Plugin contract tests

Every plugin package is testable against a common harness covering:

```text
manifest validation
catalog home/search/details
content search/details
recent releases
complete releases
incremental synchronization
chapter content
authentication declarations
allowed-domain enforcement
stable external identifiers
cancellation, timeout, and resource limits
```

The harness rejects empty content, unstable IDs, duplicate releases, invalid
chapter fields, undeclared redirects, leaked secrets, and unsupported host API
calls.

### Storage tests

Room migration and repository tests cover:

- a clean database;
- canonical story/catalog/content relationships;
- transactionally applying sync results;
- user overrides surviving resynchronization;
- deletion state transitions;
- progress restoration;
- cache and download separation.

### Android integration tests

Instrumentation tests cover:

- plugin-scoped WebView login and logout;
- encrypted session storage;
- WorkManager constraints and cancellation;
- process death during reading and synchronization;
- notification deep links;
- offline reader behavior;
- plugin update and rollback;
- storage cleanup boundaries.

### End-to-end acceptance scenarios

1. Install the app with a bundled catalog and two test content plugins.
2. Discover one novel and add it to the library.
3. Observe fast recent releases before complete history finishes.
4. Observe equivalent releases grouped under one canonical chapter.
5. Select a Vietnamese release and read it.
6. Continue to the next chapter with source continuity.
7. Switch to another release and retain canonical read state.
8. Download a range and read it with networking disabled.
9. Introduce a new canonical chapter and receive one notification.
10. Introduce a second-source release for the same chapter and confirm unread
    count does not increase.
11. Introduce the first preferred-language release and receive the appropriate
    release notification.
12. Break one plugin and confirm other plugins, old metadata, cache, and
    downloads remain usable.

## Performance and Resource Constraints

The MVP must remain responsive with a library of at least 500 stories and
100,000 stored releases on a typical supported Android device.

Design constraints:

- Chapter lists are paged from Room.
- Aggregation runs incrementally for changed stories, not globally after every
  sync.
- Plugin requests and parsing are bounded and cancellable.
- Full history sync runs off the main thread.
- Reader rendering uses sanitized structured content rather than a live remote
  page.
- Database indexes cover story, mapping, canonical chapter sort order, release
  external identity, language, and synchronization checkpoints.

Exact benchmark thresholds belong in the implementation plan, but no design
component may require loading an entire large library or novel history into
memory.

## Acceptance Criteria

The design is implemented successfully only when all of the following are
true:

- A clean install is useful with no account and no cloud service.
- Home can display one bundled catalog and additional installed catalogs.
- One work represented by several catalogs appears as one canonical story while
  retaining each catalog's metadata.
- Adding a story to the library succeeds immediately, including metadata-only
  state.
- Installed content plugins can find and link several readable sources.
- Recent releases appear before full history synchronization completes.
- Equivalent releases from different sources are grouped under one canonical
  chapter with visible source, language, group, and date information.
- Users can filter, pin a language, and select any release.
- Release selection follows language preferences and prior source/group choice
  without hiding alternatives.
- Progress is canonical-chapter based and restores the exact last release and
  position.
- Cache and explicit downloads remain distinct and manageable.
- Scheduled local checks survive individual plugin failure and Android process
  recreation.
- Notifications distinguish a new canonical chapter from another release of an
  existing chapter.
- Plugin installation, permission display, domain isolation, updates, and
  rollback work without a central plugin service.
- Source login occurs in a plugin-scoped WebView without exposing the password
  to plugin code.
- The full primary journey works offline after content is downloaded.

## Non-Goals and Architectural Guardrails

The following changes require a separate approved design rather than being
silently added to this MVP:

- cloud accounts or device synchronization;
- a central plugin moderation backend;
- native executable plugins;
- manga page/image storage and reading;
- social or community features;
- AI-generated translations or summaries;
- DRM, paywall, CAPTCHA, or access-control circumvention;
- replacing canonical chapter aggregation with a single primary source model.

## Design Review Result

This document captures the decisions approved in the product brainstorming
session. It contains no cloud dependency, no primary story-level content
source, and no centralized plugin-management requirement. The defining product
behavior is chapter-level aggregation: one canonical chapter exposes all known
releases and lets the user choose.
