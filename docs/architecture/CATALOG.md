# External catalog

Catalog discovery is read-only. Each `CatalogEntry` has an opaque, provider-qualified `CatalogEntryId`; it is normalized metadata, not `Media`, `SourceMediaRef`, a source registry entry, library content, or playable/readable capability. AniList supplies Home discovery and details only. Watch/Read starts explicit title-seeded search against registered/local sources; user must select a real source result.

Progress and Library remain independent. Home Continue joins progress records only to saved Library entries by their exact source reference; source availability is checked when opening an item. It omits completed records; chapter-parent resume remains deferred because current progress identifies only concrete source media.

Catalog providers are composed separately from `SourceRegistry`. AniList provider owns its HTTP client when default-composed and is closed by `AppDependencies`; injected providers are caller-owned unless ownership is explicitly transferred.

GraphQL discovery batches bounded sections in one request to respect AniList rate limits. Partial sections and details remain visible with warnings when valid response data exists. A detail response with no media and GraphQL errors is a retryable failure, not a missing entry. Discovery queries exclude adult media.
