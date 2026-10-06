# ADR-011: Catalog metadata is not playable content

Status: Accepted

AniList discovery and search provide external metadata, not a `MediaSource`, source reference, or reading/playback capability. Catalog identity remains provider-qualified and opaque. For manga and light novels, opening from Catalog Detail requires an explicit compatible source choice, then resolves the catalog title/aliases only inside that source. Hikari may open one normalized exact match automatically; ambiguous or missing matches stay user-confirmed and can fall back to source-scoped manual search. Anime retains the existing title-seeded source-search path until a remote anime source capability exists. Catalog results never enter the library or progress store directly.

Progress, Library membership, and remote series continuation are independent. Continue displays only saved Library entries, joining direct progress by media reference and remote series progress through one exact series-to-chapter relationship. Completed direct-media progress is excluded; completed remote chapters remain resumable. Resume resolves the saved series, loads a fresh complete sequence, and requires the exact chapter reference once. Missing/unreadable chapters fail recoverably; title/number matching is never used.

AniList is composed as its own provider outside `SourceRegistry`. Default-composed provider resources close with app dependencies; caller-injected provider ownership remains caller-controlled unless explicitly transferred.

Consequences: catalog metadata cannot promise stream availability or direct reading. Direct-media Continue excludes progress refs absent from Library. Remote chapter continuation and child progress remain independently persisted, but Home displays them only while parent series remains in Library. See [User state](../architecture/USER_STATE.md) for canonical Continue and resume semantics.

Rationale: `SourceMediaRef` identifies one source-owned content item and drives existing source capability checks. Reusing it for external metadata would falsely claim that a selected source can reopen AniList results.

Alternatives rejected: putting AniList in `SourceRegistry` would equate metadata lookup with content access; auto-saving catalog results or translating AniList IDs to source IDs would invent durable source mappings and alter Library semantics. Fuzzy matching is not sufficient for automatic opening; only a unique normalized exact title/alias match may bypass confirmation.

Implementation and UI layout details live in [Catalog architecture](../architecture/CATALOG.md).
Chapter continuation uses the bounded series-to-chapter contract documented in [User state](../architecture/USER_STATE.md); it does not introduce canonical content identity or fuzzy resolution.
