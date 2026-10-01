# External catalog

Catalog discovery and search are read-only. Each `CatalogEntry` has an opaque, provider-qualified `CatalogEntryId`; it is normalized metadata, not `Media`, `SourceMediaRef`, a source registry entry, library content, or playable/readable capability. AniList supplies Home discovery, the primary Search destination, and details. Watch/Read starts explicit title-seeded source search against registered/local sources; user must select a real source result.

Progress and Library remain independent. Home Continue joins progress records only to saved Library entries by their exact source reference; source availability is checked when opening an item. It omits completed records; chapter-parent resume remains deferred because current progress identifies only concrete source media.

Catalog providers are composed separately from `SourceRegistry`. AniList provider owns its HTTP client when default-composed and is closed by `AppDependencies`; injected providers are caller-owned unless ownership is explicitly transferred.

GraphQL discovery batches bounded sections in one request to respect AniList rate limits. Featured is a balanced spotlight built from separate trending Anime, Manga, and Light Novel candidate pools, capped equally per media type and interleaved without a custom cross-type score. Partial sections and details remain visible with warnings when valid response data exists. A detail response with no media and GraphQL errors is a retryable failure, not a missing entry. Discovery queries exclude adult media.

The primary Search destination searches catalog metadata, not extension/local content. Source search is a secondary resolution step entered from Catalog Detail; this keeps discovery identity separate from content-source identity.
