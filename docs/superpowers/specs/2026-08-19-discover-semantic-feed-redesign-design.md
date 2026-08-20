# Discover Semantic Feed Redesign Design

Date: 2026-08-19
Status: APPROVED — IMPLEMENTED AND VERIFIED
Scope: Discover only; generic catalog contract, cached feed metadata, projection, UI state, Compose presentation, and focused verification
Acceptance: `docs/internal/checkpoints/discover-semantic-feed-redesign.md`

## Goal

Replace the current source/category-driven Discover composition with a user-facing media discovery experience built around three semantic content groups:

```text
Popular        -> full-width manual hero pager, up to 5 stories
Latest Updates -> compact 3-column grid, up to 9 stories
Top Rated      -> vertical ranked list, up to 5 stories
```

Discover must remain generic across catalog plugins. It must not know or branch on MyAnimeList, MangaUpdates, MangaDex, or any future provider ID. Catalog-specific adapters will be implemented separately after this redesign.

The primary user-facing filter becomes media type rather than catalog source:

```text
Manga | Light Novel
```

Manga is initially selected and enabled. Light Novel remains visible but disabled for this delivery. The state and component boundaries must allow Light Novel to be enabled later without redesigning the screen.

## Relationship To Existing Product Design

This spec supersedes only the Discover composition and Discover-specific data requirements described by `2026-08-12-redantotsu-inspired-product-ui-design.md`.

The following existing boundaries remain authoritative and unchanged:

- Discover remains a top-level destination.
- Search remains a focused destination opened from Discover.
- Story navigation remains the existing Story flow.
- Pull-to-refresh keeps cached content visible during refresh and on refresh failure.
- Catalog Home remains cached host-owned state populated through plugin contracts.
- Canonical `StoryId` remains host-owned identity.
- Top-level retained composition, app navigation, reader, library, source mapping, artwork infrastructure, backdrop infrastructure, and theme primitives are outside this redesign.

## Current-State Problems Addressed

The current Discover projection exposes catalog implementation details directly to the user through:

```text
featured
quickCategories
selectedCatalogId
selectedSourceId
Across catalogs shelf
source-owned shelves
```

With a small number of catalog providers, the same logical data can appear repeatedly as featured content, a category shortcut, a catalog selection, a combined shelf, and a source shelf. This makes the information hierarchy source-centric and visually repetitive.

The current `CatalogHomeSection` also has only `sourceId`, `title`, and `items`. Discover therefore has no generic, explicit way to know whether a source-provided section represents Popular, Latest Updates, Top Rated, or an unrelated feed. The redesign must add semantic feed identity instead of inferring meaning from section titles or plugin IDs.

## Normative Decisions

### DECISION-DISCOVER-001 User-facing hierarchy is semantic, not source-centric

The production Discover order is fixed as:

```text
Search header
Popular hero pager
Media-type segmented control
Latest Updates
Top Rated
```

Catalog/provider selectors, quick category cards, `Across catalogs`, and provider-named shelves are removed from the primary Discover composition.

Catalogs remain a data-source concern behind the projection boundary. A future optional source filter may be designed later, but it is not part of this delivery.

### DECISION-DISCOVER-002 Media type is the primary Discover selection

Discover uses the existing domain `ContentType` values rather than inventing a duplicate UI enum.

The supported presentation set for this design is:

```text
ContentType.MANGA
ContentType.LIGHT_NOVEL
```

Initial policy:

```text
MANGA       -> enabled and selected by default
LIGHT_NOVEL -> visible but disabled
```

The selected media type is ViewModel-owned session state so it survives normal top-level navigation retention and refresh. It is not required to persist across process death for this delivery.

Changing media type eventually filters all three semantic sections together:

```text
Popular
Latest Updates
Top Rated
```

The Light Novel segment must be structurally ready for later enablement without changing the layout or public UI contract.

### DECISION-DISCOVER-003 The media selector is a full-width segmented control

The selector is one full-width control, not two independent buttons. Each segment occupies equal horizontal space.

```text
+--------------------+--------------------+
|       MANGA        |    LIGHT NOVEL     |
+--------------------+--------------------+
```

There must be no trailing empty area caused by content-sized buttons.

If the implementation is genuinely generic, the reusable visual primitive belongs in `core:designsystem` under a neutral `Hikari...` name. Discover-specific media semantics stay in `feature:catalog`.

The control must use existing Hikari theme/design tokens and shared interaction patterns. No Discover-local colors, radii, shadows, typography values, or arbitrary dimensions are introduced.

### DECISION-DISCOVER-004 Popular is a manual full-width hero pager

The existing hero visual structure is retained as the baseline for each slide: artwork/backdrop, poster, title, score/metadata where available, and the current Story action behavior.

The container changes from one featured story to a pager with these rules:

- at most 5 stories;
- horizontal swipe only;
- no automatic paging;
- no next-card peek;
- full-width hero geometry;
- page indicator dots overlay the lower hero area;
- dots render only when there are at least 2 pages;
- dots are visual status, not individual accessibility buttons;
- changing media type resets to page 0;
- refresh should retain the currently visible story when that `StoryId` still exists, otherwise clamp to a valid page;
- current page is local Compose UI state, not ViewModel business state;
- 0 stories hides the Popular section;
- 1 story renders the hero without dots.

Provider/catalog branding is not shown in the new hero. The user should see story metadata, not the implementation source selected by aggregation.

### DECISION-DISCOVER-005 Latest Updates is a compact 3-column scan grid

Latest Updates shows at most 9 stories in three rows of three on the primary compact presentation.

Each item contains only:

```text
cover
story title, at most 2 lines
latest release/chapter label, 1 line
```

It must not show rating, genres, publication status, provider name, source badge, or decorative metadata chips.

The section prioritizes visual scanning and density. Item chrome must remain light; artwork is the primary visual element.

The outer Discover `LazyColumn` remains the only vertical scroll owner. The implementation must not nest a vertically scrolling `LazyVerticalGrid` inside it. The nine items are grouped into fixed rows inside the parent lazy list.

Nine is a maximum, not a minimum. Fewer valid items render as-is and are never duplicated to fill the grid. A partial final row keeps normal item width; remaining slots stay empty rather than stretching the final item.

A `See all` affordance is part of the eventual product design, but its destination and interaction are deliberately deferred. This delivery must not ship a dead clickable control. The section-header component may reserve an optional action contract, but production UI renders no actionable `See all` until a destination is designed.

### DECISION-DISCOVER-006 Latest ordering uses source update time, never cache fetch time

`Latest Updates` means the source-reported time of the latest chapter/release/update for a story.

It must not use:

```text
fetchedAtEpochMillis
Home refreshedAtEpochMillis
Room insertion/update time
```

as a substitute for story update recency.

An entry without a trustworthy source-provided latest-update timestamp is ineligible for Latest Updates. Missing data results in omission, not fabricated recency.

Latest items are sorted by latest-update timestamp descending with deterministic stable tie-breakers.

### DECISION-DISCOVER-007 Top Rated is a vertical full-width ranking list

Top Rated shows at most 5 stories. Each story occupies one full-width row and exposes the ranking visually.

Conceptual composition:

```text
01  [cover]  title
             rating
             genre 1 · genre 2 · genre 3
             status

02  [cover]  ...
```

Each row contains only:

- rank `01` through `05`;
- cover;
- title, at most 2 lines;
- normalized/display score;
- genres, one line with ellipsis;
- publication status, one line when available.

Author, provider name, source name, language tags, and other secondary metadata are not shown.

Genre presentation uses compact text separated by a simple delimiter; it does not become a row of chips. The design should prefer 2-3 useful genre values and ellipsize rather than wrap the row taller.

Missing genre or status collapses naturally. The UI does not render `Unknown` placeholders.

The rank is a structural visual element rather than a badge chip. Top 1-3 may receive emphasis through existing typography/emphasis tokens, but no new gold/silver/bronze colors or arbitrary ranking theme are introduced in this delivery.

As with Latest Updates, a future `See all` action is deferred and must not be shipped as a dead button.

### DECISION-DISCOVER-008 Feed meaning is explicit in the generic catalog contract

Introduce a generic semantic feed kind equivalent to:

```kotlin
enum class CatalogFeedKind {
    POPULAR,
    LATEST_UPDATES,
    TOP_RATED,
    OTHER,
}
```

`CatalogHomeSection` carries the feed kind in addition to source identity and display title.

The default is `OTHER` for backward compatibility.

Discover must never infer semantic meaning using:

- section-title substring matching;
- catalog/provider IDs;
- source IDs with provider-specific conventions.

A plugin may provide any subset of semantic feed kinds. Multiple plugins may provide the same feed kind.

### DECISION-DISCOVER-009 Home item metadata is expanded generically and remains optional

The current domain `CatalogEntry` already contains:

```text
contentType
genres
score
popularityRank
```

The Home/plugin path does not currently carry all of those fields, and `CatalogEntry` does not yet contain publication status or latest-update data.

The generic catalog item contract must be expanded so Home feeds can provide the metadata Discover needs without issuing one `details()` request per card.

Add domain concepts equivalent to:

```kotlin
data class CatalogLatestUpdate(
    val atEpochMillis: Long,
    val releaseLabel: String?,
)

enum class PublicationStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    CANCELLED,
    UPCOMING,
}
```

The exact wire names may differ to remain consistent with existing protocol naming, but the semantics are normative.

The Home item path must be able to carry, as optional metadata:

```text
genres
popularityRank
publicationStatus
latestUpdate
```

Existing fields remain available:

```text
story/source identity
title
contentType
authors
cover
score
```

All new provider metadata is optional unless existing validation already requires otherwise. `null` means the provider does not know or does not support the field. The UI never fabricates missing values.

`latestUpdate` is one coherent value object so timestamp and release label are not merged independently into an impossible combination.

### DECISION-DISCOVER-010 Details may enrich metadata, but Discover must not create N+1 detail requests

The catalog details protocol may also carry publication status/latest-update metadata so a later details fetch can enrich a stored entry.

However, Discover projection and rendering operate entirely from cached Home/read-model data. Discover must not call `details()` for 5 + 9 + 5 cards simply to populate the screen.

Plugin adapter work for specific websites is outside this delivery. Fixtures/tests may provide rich normalized data so the full UI can be verified before a real plugin supports every semantic feed.

### DECISION-DISCOVER-011 Persistence extends the existing cache; no cache wipe

The current Room database is version 6. This redesign requires a normal forward migration to version 7.

At minimum, persist semantic section kind on `catalog_home_sections` and new entry metadata on `catalog_entries`, equivalent to:

```text
catalog_home_sections
+ feed_kind TEXT NOT NULL DEFAULT 'OTHER'

catalog_entries
+ publication_status TEXT NULL
+ latest_update_at_epoch_millis INTEGER NULL
+ latest_update_release_label TEXT NULL
```

If protocol/Home-item expansion also makes currently detail-only metadata such as genres/popularity available during Home ingest, the existing catalog-entry columns are reused rather than duplicated.

Migration 6 -> 7 must:

- preserve all existing user and catalog data;
- default old Home sections to `OTHER`;
- default new nullable metadata to `NULL`;
- update the exported Room schema to version 7;
- be registered in `OpenStoryDatabase.open()`;
- pass the repository's migration/schema verification gates.

No destructive migration or app-data wipe is permitted for this change.

### DECISION-DISCOVER-012 Sparse refreshes preserve richer metadata

The existing Wave 05 principle that sparse Home cards must not erase richer details remains in force.

For the newly relevant metadata:

```text
publicationStatus
- incoming non-null -> may update existing
- incoming null     -> preserve existing

latestUpdate
- existing null     -> accept incoming valid value
- incoming null     -> preserve existing
- incoming newer    -> replace the entire latestUpdate object
- incoming older    -> preserve existing
```

Genres/popularity/score continue to follow the repository's source-metadata preservation rules. Any Home-ingest expansion must be implemented so a sparse provider response does not downgrade a richer cached entry.

Timestamp and release label must never be independently combined across two different updates.

### DECISION-DISCOVER-013 Aggregation happens before Compose

Multi-plugin complexity terminates at the catalog/projection boundary.

Discover UI receives presentation-ready semantic lists rather than raw plugin/source shelves.

Conceptually:

```text
Catalog Home snapshots from N plugins
              |
              v
filter selected ContentType
              |
              v
normalize semantic feed contributions
              |
              v
deduplicate by canonical StoryId
              |
       +------+------+------+
       |             |      |
       v             v      v
    Popular        Latest   Top Rated
     <= 5           <= 9     <= 5
       +-------------+------+
                     |
                     v
              DiscoverUiState
```

Compose does not merge plugin metadata, resolve canonical identity, choose source winners, or infer feed meaning.

### DECISION-DISCOVER-014 Each semantic feed has its own ordering semantics

Do not apply one generic ranking function to all three sections.

Popular:

- uses provider Popular feed membership/order and/or valid `popularityRank` signals;
- deterministic tie-breaking is required;
- absence of popularity data does not become Top Rated by accident.

Latest Updates:

- requires valid `latestUpdate.atEpochMillis`;
- newest timestamp first;
- deterministic tie-breaking after timestamp.

Top Rated:

- requires a valid score;
- compare scores normalized by scale;
- deterministic tie-breaking after normalized score.

The existing `AggregateRanking` may be reused where its semantics match Top Rated, but it must not define Popular or Latest simply because it already exists.

### DECISION-DISCOVER-015 Deduplication is canonical-story based

When multiple catalog plugins contribute the same canonical `StoryId`, a semantic section shows the story once.

Projection may choose/merge the best valid presentation metadata across contributions, for example:

```text
artwork          -> best usable representation
score            -> best/selected valid rating signal for Top Rated
latestUpdate     -> newest trustworthy update
publicationStatus-> best non-null current metadata
genres           -> best useful non-empty metadata
```

The exact source-precedence algorithm must be deterministic and test-covered. It must not depend on iteration/hash order.

Provider identity remains available in the lower-level source-preserving model for provenance, Story details, and future source controls, but it is not surfaced as primary Discover presentation metadata.

### DECISION-DISCOVER-016 Discover UI state becomes semantic and source-agnostic

`DiscoverUiState` should converge on a shape equivalent to:

```text
selectedContentType
availableContentTypes or selection availability policy
popular
latestUpdates
topRated
refreshing
refreshReport
observationFailure
refreshFailure
initialLoading/derived content readiness as needed
```

The following current Discover presentation state is removed from the redesigned UI contract:

```text
featured
quickCategories
shelves
selectedCatalogId
selectedSourceId
```

Raw cached catalogs/ranked contributions may remain internal to the projection pipeline if required for preparation, but they are not the primary Compose contract.

Provider-specific types must not leak into the new screen component APIs.

### DECISION-DISCOVER-017 Initial loading uses layout-shaped skeletons

When the app has no cached Discover data and an initial/bootstrap refresh is in flight, show loading placeholders matching the final geometry:

```text
hero skeleton
full-width media selector
Latest Updates skeleton grid
Top Rated skeleton rows
```

Do not show a lone center spinner and do not leave a mostly empty screen with only source controls.

Once any usable cached content exists, refreshes keep that content visible and use the existing pull-to-refresh progress behavior instead of replacing the screen with skeletons.

### DECISION-DISCOVER-018 Empty and partial data degrade by omission

Section limits are maxima:

```text
Popular        <= 5
Latest Updates <= 9
Top Rated      <= 5
```

The UI never duplicates entries to satisfy a visual count.

If one semantic section has no usable items but others do, omit that section entirely.

If all semantic sections for the selected media type are empty after loading completes, show a screen-level empty state.

Refresh/observation failures remain non-blocking when cached content exists. Existing cached content stays visible and the existing retry/failure presentation pattern remains available.

### DECISION-DISCOVER-019 One vertical scroll owner

The outer Discover `LazyColumn` remains the only vertical scroll owner.

Do not introduce a nested vertically scrolling grid/list for Latest or Top Rated.

This preserves:

- pull-to-refresh ownership;
- retained list state across top-level navigation;
- current back-to-top behavior;
- predictable focus traversal;
- simpler performance characteristics.

### DECISION-DISCOVER-020 Responsive design preserves hierarchy

Compact phones are the primary layout target. The hierarchy and semantic section types remain the same at medium/expanded widths.

The design does not automatically turn the three-column Latest grid into four or five columns simply because more width exists.

At wider windows, use the existing design-system adaptive/content-width primitives to prevent Hero, Latest cards, and Top Rated rows from stretching into visually poor proportions. Do not introduce Discover-specific magic maximum widths if an existing shared primitive serves the need.

### DECISION-DISCOVER-021 Accessibility semantics represent the new hierarchy

The current Category -> Catalog focus/semantics contract is replaced.

Expected navigation order is conceptually:

```text
Search
Popular hero
Manga segment
Latest item 1 ... n
Top Rated rank 1 ... n
```

The disabled Light Novel segment remains perceivable as disabled/unavailable but is not exposed as an actionable control.

Popular hero accessibility includes page position when multiple stories exist, equivalent to:

```text
Popular story 2 of 5: <title>
```

Page dots are not separate focus targets unless a future design makes them interactive.

A Top Rated row exposes its information as one coherent ranked item rather than a noisy sequence of independent labels, equivalent to:

```text
Rank 1, <title>, rating <score>, <genres>, <status>
```

Touch targets and focus targets continue to satisfy the project's shared accessibility/minimum-target policies.

## Target Presentation Model

The exact names may be adjusted during implementation to fit project conventions, but the presentation boundary should be equivalent to:

```text
DiscoverUiState
- selectedContentType: ContentType = MANGA
- mediaTypeOptions / availability
- popular: List<DiscoverStoryItem>
- latestUpdates: List<DiscoverStoryItem>
- topRated: List<DiscoverStoryItem>
- refreshing
- refreshReport
- observationFailure
- refreshFailure
- loading/content-readiness signal when needed

DiscoverStoryItem
- storyId
- title
- coverUrl
- contentType
- score?
- genres
- publicationStatus?
- latestUpdate?
```

Do not copy fields into the presentation model without a screen requirement. Provider/source identity stays out of the primary item presentation unless required for a future explicitly designed interaction.

## Target Compose Component Boundaries

Feature-specific components remain in `feature:catalog`:

```text
DiscoverPopularPager
DiscoverHero / DiscoverHeroSlide
DiscoverMediaTypeSelector wrapper
DiscoverLatestGrid
DiscoverLatestCard
DiscoverTopRatedList
DiscoverTopRatedRow
DiscoverSectionHeader
```

A generic segmented-control primitive may move to or be introduced in `core:designsystem` only if it is neutral and reusable. Repeated Hikari visual patterns must not be reimplemented with Discover-local styling.

`DiscoverScreen` remains the orchestration/composition owner and should not accumulate ranking, merging, source-selection, or metadata-formatting business logic.

## Data Flow

The desired production flow is:

```text
Catalog plugin wire DTOs
        |
        v
PluginCatalogSource mapping
        |
        v
source-neutral catalog Home models
        |
        v
CatalogRefreshService / repository ingest
        |
        v
Room cached Home snapshots + source metadata
        |
        v
DiscoverProjectionPipeline on Default dispatcher
        |
        +--> filter ContentType
        +--> semantic feed grouping
        +--> canonical StoryId dedupe
        +--> feed-specific ordering
        +--> project presentation metadata
        |
        v
DiscoverUiState
        |
        v
DiscoverScreen
```

Network/plugin execution never occurs from Compose.

## State And Lifecycle

### Bootstrap

The existing empty-cache bootstrap behavior remains: first observation of an empty cache triggers one refresh attempt.

During that attempt the new screen shows layout-shaped initial loading state.

### Cached refresh

When cached data exists:

```text
cached content remains visible
+ pull-to-refresh progress
+ successful projection replacement when new cache arrives
```

Refresh failure preserves cached content and exposes non-blocking failure/retry state.

### Media selection

Manga starts selected.

Changing the selected type later must not trigger arbitrary plugin-detail calls from UI. It projects already cached semantic data for the selected media type. A later plugin/capability design may decide whether changing media type also requests a targeted refresh; that behavior is outside this delivery unless required by the existing generic Home request flow.

### Pager state

Pager page is local UI state. Media-type change resets it to page 0. Data refresh preserves the visible `StoryId` when possible.

## Error And Missing-Metadata Policy

- Missing score -> story is ineligible for Top Rated.
- Missing latest-update timestamp -> story is ineligible for Latest Updates.
- Missing genre -> omit genre line/content; do not print `Unknown`.
- Missing publication status -> omit status line; do not print `Unknown`.
- Missing artwork -> use existing Hikari artwork fallback behavior; aggregation may prefer a contribution with usable artwork.
- Missing Popular semantic feed/signal -> do not fabricate Popular from rating.
- One failed plugin -> preserve successful/cached contributions from other plugins.
- One empty semantic section -> hide that section.
- All semantic sections empty after loading -> screen-level empty state.

## Out Of Scope

This delivery does not implement or redesign:

- MangaUpdates catalog adapter;
- MyAnimeList catalog adapter changes for the new feed contract;
- MangaDex conversion to a catalog provider;
- any other website/plugin-specific feed extraction;
- Light Novel enablement in production;
- `See all` destinations;
- source/catalog picker UI;
- Trending;
- new recommendation/social algorithms;
- Story, Reader, Library, Home, Downloads, Updates, Plugins, or Settings behavior;
- theme primitive redesign;
- app navigation architecture;
- top-level composition retention architecture.

Plugin adapters will be a later work item against the generic contract established here.

## Verification Strategy

### Catalog/domain tests

Add or update focused tests for:

- feed-kind protocol/source/domain mapping;
- backward-compatible defaults to `OTHER`;
- optional Home metadata mapping;
- latest-update value validation;
- sparse Home updates preserving rich cached metadata;
- latest-update merge accepting only a newer coherent value;
- semantic projection filtering by `ContentType`;
- Popular maximum 5 and deterministic ordering;
- Latest maximum 9, timestamp-descending ordering, and exclusion without valid timestamp;
- Top Rated maximum 5, normalized-score ordering, and exclusion without score;
- canonical `StoryId` dedupe across plugins;
- deterministic metadata/source precedence when contributions conflict;
- partial semantic feeds without fabricated fallback meaning.

### Room tests

Add migration/schema verification for version 6 -> 7:

- existing data survives;
- old sections become `OTHER`;
- new metadata columns are nullable/default correctly;
- repository round-trip includes feed kind/status/latest update;
- exported schema 7 is committed and stable;
- foreign-key/integrity checks remain green.

### ViewModel/projection tests

Replace current source-selection expectations with:

- Manga is the default selected media type;
- Light Novel is visible/disabled according to delivery policy;
- refresh and observation failure preserve latest content;
- bootstrap refresh still happens once for an empty cache;
- selection/refresh projection does not call plugins from the UI layer;
- partial sections are omitted;
- all-empty produces the intended empty presentation state.

### Compose semantics tests

Replace obsolete Category/Catalog semantics with coverage for:

- search remains first-class and actionable;
- Popular pager is swipeable manually;
- no hero peek geometry;
- page dots appear only for multiple pages;
- dots are not separate accessibility actions;
- full-width equal media segments;
- Light Novel is disabled/unavailable;
- Latest renders 3 items per row and at most 9;
- Top Rated renders one full-width ranked item per row and at most 5;
- ranking semantics expose rank/title/rating coherently;
- pull-to-refresh and safe top inset remain intact;
- back-to-top behavior remains intact.

### Screenshot tests

Regenerate Discover screenshots from the new production composition. Existing Discover PNGs are not a normative pixel target for this redesign.

At minimum cover the project's existing responsive targets:

```text
compact dark
medium dark
compact light
app-shell Discover state
```

Fixtures must include enough normalized generic data to exercise:

```text
5 Popular
9 Latest Updates
5 Top Rated
```

Fixtures are test data only; production must never fabricate content to fill sections.

### Performance/regression gates

Run existing repository gates plus relevant Discover/UI checks. The final implementation plan should preserve the existing Macrobenchmark coverage that exercises Discover navigation/scroll and rerun it when feasible after functional gates are green.

The redesign must not introduce nested vertical scrolling, UI-layer plugin calls, repeated detail fetches, or unnecessary recomputation on the main thread.

## Expected File Areas

The implementation is expected to touch focused files in these areas rather than unrelated modules:

```text
plugins/api/.../protocol/catalog
catalog/.../model
catalog/.../source
catalog/.../refresh or repository-facing normalization
storage/room/.../catalog
storage/room/RoomMigrations.kt
storage/room/OpenStoryDatabase.kt
storage/room/schemas/.../7.json
feature/catalog/.../ui/discover
core/designsystem only if a generic segmented control is needed
focused unit/android/screenshot tests
```

Exact file names are deferred to the implementation plan after this spec is approved.

## Acceptance Criteria

The redesign is complete when all of the following are true:

1. Discover no longer presents catalog/category selection as its primary information architecture.
2. Popular is a manual, full-width, non-peeking hero pager with at most 5 stories and overlay page dots.
3. Manga/Light Novel is a full-width equal segmented control below the hero; Manga is selected and Light Novel is visibly disabled for this delivery.
4. Latest Updates renders at most 9 compact items in a 3-column composition and orders only by trustworthy source update timestamps.
5. Top Rated renders at most 5 full-width ranked rows with rank, cover, title, rating, compact genre text, and status when available.
6. Semantic feed kind is explicit in the generic catalog contract; Discover does not infer feeds from titles or provider IDs.
7. Generic Home metadata can carry the fields needed by the three sections without N+1 detail requests.
8. Multiple plugins can contribute generically and duplicate canonical stories render once per semantic section.
9. Room upgrades from version 6 to 7 without destructive migration or user-data loss.
10. Initial empty-cache loading uses layout-shaped placeholders; cached refresh never replaces content with a full skeleton.
11. Empty individual sections are omitted; fully empty selected-media content shows a screen-level empty state.
12. The outer Discover `LazyColumn` remains the only vertical scroll owner.
13. Existing Hikari design tokens/shared-component rules are preserved; no new one-off visual styling is embedded in Discover.
14. Obsolete Category/Catalog focus and screenshot expectations are replaced with tests for the new hierarchy.
15. No website-specific catalog plugin implementation is included in this delivery.

## Implementation Boundary After Approval

After this design is approved, implementation planning should decompose the work so contract/storage changes land with tests before the new Compose UI depends on them. The plan must preserve a buildable/testable sequence and must not start plugin-specific MangaUpdates/MAL/MangaDex integration.
