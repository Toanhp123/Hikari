# ADR-009: Catalog metadata is not playable content

Status: Accepted

AniList discovery provides external metadata, not a `MediaSource`, source reference, or reading/playback capability. Catalog identity remains provider-qualified and opaque. Opening a catalog item starts explicit title-seeded search through configured sources; selecting a source result remains required before opening actual content. Catalog results never enter the library or progress store directly.

Progress and Library remain independent. Home Continue can only resume incomplete records by joining their `SourceMediaRef` against saved Library media. Chapter-parent resume is deferred until progress records can resolve a series-level parent without changing current persistence semantics.

AniList is composed as its own provider outside `SourceRegistry`. Default-composed provider resources close with app dependencies; caller-injected provider ownership remains caller-controlled unless explicitly transferred.

Consequences: catalog metadata cannot promise stream availability or direct reading. Current Continue support excludes progress refs absent from Library and chapter-level records without a saved series entry.

Rationale: `SourceMediaRef` identifies one source-owned content item and drives existing source capability checks. Reusing it for external metadata would falsely claim that a selected source can reopen AniList results.

Alternatives rejected: putting AniList in `SourceRegistry` would equate metadata lookup with content access; auto-saving results or translating AniList IDs to source IDs would invent source mappings and alter Library semantics.

Implementation and UI layout details live in [Catalog architecture](../architecture/CATALOG.md).
Revisit chapter resume when an existing source-neutral parent-resolution contract is designed.
Եnd