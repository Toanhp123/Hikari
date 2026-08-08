# Wave 05 Catalog Home and Discovery Design

Date: 2026-08-08
Status: Approved design baseline for the rebased Wave 05 plan

## Goal

Define the source-preserving catalog ingestion and cached-Home boundary that Wave 05
needs before product UI work begins. The design keeps canonical identity owned by the
host, keeps catalog metadata reversible and source-specific, and makes later matching,
refresh, search, and story-detail tasks consume one stable repository contract instead
of reopening persistence decisions task by task.

## Context

Wave 04 is complete and the secure plugin host can execute catalog plugins. The first
Wave 05 implementation task is therefore the first place where catalog Home payloads
must become durable application state rather than transient plugin DTOs.

The original Wave 05 plan named atomic ingestion, source identity, cached Home,
provenance, and a resolver boundary, but it did not fully specify the domain read model,
Home-section persistence, source-identity database invariant, resolver port, or how later
Home tasks would observe cached snapshots. That ambiguity belongs to the Wave 05 plan
boundary; it is not treated as a defect in the completed Wave 02/03/04 implementation.

The pre-Wave-05 catalog contract remediation is an entry dependency: every
`CatalogCard` now carries explicit `ContentType` through Kotlin, Selector Schema 1, and
JavaScript decoding. Wave 05 must never guess a content type or fetch `details()` for
all Home cards merely to construct canonical identity.

## Normative Decisions

### DECISION-W05-001 Normalized snapshot boundary

Plugin DTOs are converted to platform-neutral Wave 05 models before persistence.
`core:model` and `core:database` do not own or expose `CatalogCard`, `CatalogSection`,
or other plugin API wire DTOs.

The normalized Home input is:

```text
CatalogSnapshot
- pluginId
- pluginVersion
- sections[]

CatalogSnapshotSection
- sourceId
- title
- items[]

CatalogSnapshotItem
- sourceId
- title
- contentType
- authors[]
- coverReference
- ratingValue
- ratingScale
```

List order is semantic. The repository persists explicit section/item positions rather
than asking callers to provide duplicate numeric position fields.

The same boundary also defines a rich `CatalogSourceMetadata` value matching the fields
that the current `CatalogDetails` contract can provide. Task 06 maps detail DTOs into
that value and calls the Task 01 repository to enrich an existing/new source entry
without changing Home membership.

### DECISION-W05-002 Source identity and stable IDs

A catalog source entry is identified by `(catalogPluginId, sourceId)`. Room enforces the
equivalent unique key on `(catalog_plugin_id, external_story_id)` even though
`catalog_entry_id` remains the storage primary key.

For newly observed source entries, the host may derive stable local IDs from the
namespaced source identity. The external source ID is never used by itself as canonical
story identity.

### DECISION-W05-003 Source-owned metadata and provenance

`CatalogEntry` remains source-owned metadata attached to a canonical story. Wave 05
completes the durable entry shape needed by the current Catalog API with:

- explicit `contentType`;
- `aliases`;
- `languageTags`;
- `popularityRank`;
- `pluginVersion`;
- `fetchedAtEpochMillis`.

Existing fields such as source URL, title, authors, description, cover reference,
genres, publication status, raw score, and score scale remain source-specific. Fields
from the approved product design that the current plugin contract does not expose, such
as rating count or a raw metadata version, are not invented.

One repository clock value is captured for a successful ingest operation and is used as
the fetch/provenance timestamp for card-owned fields written by that operation.

### DECISION-W05-004 Sparse Home cards never erase richer details

Home and search cards are intentionally sparse. A successful Home refresh may update
only card-owned fields:

```text
title
contentType
authors
coverReference
ratingValue
ratingScale
pluginVersion
fetchedAtEpochMillis
```

It must not replace existing rich metadata with `null`/empty placeholders merely
because `CatalogCard` does not carry source URL, aliases, description, genres,
language tags, popularity rank, or publication status.

Task 06 may later enrich those detail-owned fields in the same `catalog_entries` rows
without another schema-shape decision.

### DECISION-W05-005 Cached Home has independent lifecycle tables

Home membership is not canonical ownership. Room stores the latest successful Home
snapshot for each catalog using three focused structures:

```text
catalog_home_snapshots
- catalog_plugin_id (primary key)
- plugin_version
- refreshed_at_epoch_millis

catalog_home_sections
- catalog_plugin_id
- section_source_id
- title
- section_position

catalog_home_items
- catalog_plugin_id
- section_source_id
- catalog_entry_id
- item_position
```

A source entry may appear in multiple sections. Removing a card from a refreshed
section removes only the Home membership row. It does not delete the `CatalogEntry` or
`CanonicalStory`.

### DECISION-W05-006 Atomic plugin-local refresh

`CatalogRepository.ingest(snapshot)` performs one Room transaction for one plugin:

1. capture one repository timestamp;
2. upsert card-owned source metadata by `(pluginId, sourceId)`;
3. preserve detail-owned metadata already present;
4. retain or create the canonical link through the resolver boundary;
5. replace only that plugin's Home sections and item memberships;
6. store plugin version and successful refresh timestamp;
7. commit as one unit.

If any step fails, the previous successful Home snapshot and metadata remain visible.
Refreshing catalog A never modifies catalog B's snapshot or source metadata.

### DECISION-W05-007 Canonical resolver is a pure injected port

Wave 05 owns a small platform-neutral resolver contract. The repository resolves only
new source identities; a previously linked `(pluginId, sourceId)` keeps its existing
canonical link.

The port receives the incoming normalized source item and host-owned canonical
candidates, then returns either an existing `StoryId` or a stable ID for a new
`CanonicalStory`. Task 01 uses a source-isolated implementation that creates one
canonical story per new source entry. Task 03 replaces that implementation with the
deterministic cross-catalog matcher without changing `CatalogRepository`.

Because the project is pre-MVP and Task 03 lands before Wave 05 UI is checkpointed,
development data created by the temporary Task 01 resolver does not require a runtime
merge migration. Developers may clear app data while moving between incomplete Wave 05
tasks.

### DECISION-W05-008 Repository exposes cached source views, not combined business logic

Task 01 provides source-preserving write/read operations:

```text
ingest(snapshot)
upsertSourceMetadata(pluginId, pluginVersion, metadata)
catalogEntry(pluginId, sourceId)
observeCatalogHome(pluginId)
observeCatalogHomes()
```

The read model includes canonical story identity, full source-owned `CatalogEntry`
metadata, plugin version, refresh timestamp, source section labels, and stable ordering.

Task 01 does not compute combined ranking, deduplicate cards across catalogs, call
plugins, or decide partial-refresh orchestration. Task 03 owns matching/ranking and Task
04 owns multi-plugin refresh/use-case composition.

### DECISION-W05-009 One pre-MVP schema-one completion exception

The Room database remains `version = 1`. Wave 05 Task 01 is allowed one explicit
pre-MVP planning-correction update to the current schema-1 structure and committed
`1.json`; no `MIGRATION_1_2` is introduced.

This exception exists because the active Wave 05 plan previously omitted persistence it
already required semantically. Historical Baseline 1 and completed-wave documents are
not rewritten and no fault is attributed to their implementers.

Verification for this one change is fresh-schema integrity rather than a version
migration:

- `OpenStoryDatabase(version = 1)` remains true;
- exactly `1.json` remains committed;
- fresh DB creation contains the new Home tables and catalog-entry unique index;
- foreign-key checks pass;
- repository round trips pass;
- `scripts/verify-room-schema-stability.sh` reports no build-time drift after the
  committed schema is regenerated.

After Task 01 is accepted, further structural persistence changes again follow the
normal migration rule unless another explicit project-level baseline decision is made.

### DECISION-W05-010 Module additions follow current repository governance

Wave 05 was originally written before the current module-governance gate existed. Any
new `:core:matching`, `:feature:home`, or `:feature:story` module must be added through
`docs/contributing/adding-a-module.md` in the same task that introduces it: settings,
build file, architecture policy, focused tests, README module graph, and affected
checkpoint/report wiring all change atomically.

## Domain Read Model

The persistence adapter exposes a source-preserving cached Home projection equivalent
to:

```text
CatalogEntryWithStory
- storyId
- entry: CatalogEntry

CatalogHomeSnapshot
- pluginId
- pluginVersion
- refreshedAtEpochMillis
- sections[]

CatalogHomeSection
- sourceId
- title
- items[]: CatalogEntryWithStory
```

This is intentionally not a UI model. Task 04/05 may derive combined sections, stale
labels, aggregate ranks, and presentation state without Room entities escaping the
storage module.

## Failure and Lifecycle Semantics

- Plugin failure before `ingest()` leaves the last successful cached snapshot intact.
- Repository validation/storage failure rolls back the entire plugin-local ingest.
- Duplicate `(pluginId, sourceId)` updates the same source entry instead of creating a
  duplicate row.
- The same external `sourceId` from different plugins remains independent.
- Removing a Home membership never implies source-record deletion.
- Discovery ingestion never creates `library_entries`.
- Disabling/removing a plugin may hide/remove its Home memberships through later
  lifecycle actions, but canonical stories and user-owned state are not cascaded from
  plugin lifecycle.
- Sparse Home data never downgrades richer detail metadata.

## Task Ownership After Rebase

### Task 01

Owns normalized snapshot/read models, resolver port + temporary source-isolated
implementation, source identity/provenance, schema-1 Home persistence, atomic ingest,
and cached source-specific reads.

### Task 02

Owns the bundled default catalog only. It consumes the repaired Catalog contract and
the existing secure installer/runtime.

### Task 03

Adds `:core:matching`, implements the deterministic resolver port, and derives aggregate
ranking. It does not redesign Room Home persistence.

### Task 04

Adds `:feature:home`, maps `HostedPlugin<CatalogPlugin>` results into `CatalogSnapshot`
including the pinned hosted plugin version, refreshes catalogs independently, and
combines cached repository streams.

### Task 05

Renders combined and source-specific Home from Task 04 models. UI never calls Room or
plugins directly.

### Task 06

Uses the same canonical resolver and catalog-entry storage shape for search/details.
Rich detail fetches map to `CatalogSourceMetadata` and call
`upsertSourceMetadata(...)`, updating detail-owned metadata and provenance without
changing Home membership or schema shape.

## Verification

Task 01 is not accepted until deterministic tests prove at least:

- plugin A refresh cannot mutate plugin B rows;
- same `(pluginId, sourceId)` updates one row;
- same `sourceId` under different plugins remains distinct;
- section and item order round-trip exactly;
- removed card disappears from Home while its canonical story remains;
- plugin version and one operation timestamp are retained;
- sparse Home refresh preserves richer stored details;
- rich source-metadata upsert does not add/remove Home membership;
- discovery ingest creates no Library membership;
- mid-transaction failure preserves the previous complete snapshot;
- fresh schema 1 contains the required tables/indexes with no foreign-key violations;
- the repaired Catalog card `contentType` survives the plugin -> snapshot -> Room ->
  cached-read path.

The wave checkpoint additionally runs the current architecture/module gates, affected
module tests, database checkpoint on API 26/API 37 when available, and the repository
fast verification script.

## Non-Goals

This design does not add readable chapter data, Library membership, personalized Home
recommendations, image-byte storage, background scheduling, migration 1->2, or a
compatibility layer for incomplete pre-MVP Wave 05 development databases.
