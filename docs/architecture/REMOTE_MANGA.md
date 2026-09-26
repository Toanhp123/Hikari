# Remote manga: MangaDex

## Scope and boundaries

This vertical slice probes the existing boundaries, not a provider framework.
`MediaSearchSource` returns normalized `Media`; `MangaChapterSource` returns
`MangaChapter(title, source, scanlator?)`. `MangaPageSource.pages` accepts a readable
reference: a local folder or a selected remote chapter. Domain stays pure Dart;
provider parsing, HTTP and MangaDex identifiers stay in infrastructure.

App composition owns a `SourceId → MediaSource` map, injects search callbacks and
routes by capability. A series opens the generic chapter list before the reader;
no first/latest chapter is selected implicitly. Local manga still opens its folder
pages directly. Remote navigation does not require Android local scanning.
The app owns and disposes its default HTTP source; injected clients remain caller-owned.
No application service, registry, plugin engine or DI framework is introduced.

## Identity and user state

MangaDex owns `SourceId('mangadex')`. Series references use manga UUIDs, chapter
references use chapter UUIDs, and page references use `chapterUUID/zeroBasedIndex`.
These are source-local locators, not canonical identities. Temporary image hosts,
base URLs and full page URLs are never stored in references or SQLite.

Library saves series snapshots. Progress saves the selected chapter reference with
the existing `PagePosition`; schema version 1 is unchanged. Reopening that chapter
restores its page. Removing the series from Library leaves chapter progress intact.
Series-level “resume last chapter” is deliberately deferred: reopening a series
always shows chapter selection. Reader decode/completion semantics remain those in
[USER_STATE](USER_STATE.md). Source and scanlator credit stay visible.

## API and bounded resources

- Explicit title search: first 20 results, available English chapters, safe/suggestive.
  English title preferred; deterministic language-key fallback, then Untitled manga.
- English feed requests use 500 rows, ascending volume/chapter, expanded groups and
  no `includeEmptyPages`. Pagination advances by raw returned rows, including filtered
  rows. Distinct upload UUIDs remain distinct even when chapter numbers match.
- External, unavailable, empty and future-readable chapters are skipped. Empty feeds
  are valid. Invalid responses, no-progress pagination and totals above 10,000 fail
  explicitly; at most 20 feed requests are allowed rather than silently truncating.
- Original-quality at-home metadata is held only in memory: one chapter, 15-minute TTL,
  at most 10,000 page references. Images load individually, with no disk/download cache.
- API bodies cap at 8 MiB; images cap at 32 MiB. Requests time out after 30 seconds;
  reports after 5 seconds. Abortable HTTP requests cancel on timeout/source disposal.
- API requests serialize with at least 250 ms between starts; at-home starts are at
  least 1,500 ms apart. HTTP 429 surfaces an error, not an automatic retry loop.
- A failed image invalidates the session. HTTP 403 refreshes metadata and retries once;
  a second failure reaches UI. Base URL path prefixes and ports are preserved.

Eligible image requests report success/failure to MangaDex@Home with URL, actual
received response byte length (zero when no response completes), duration and
`X-Cache` HIT status. MangaDex-owned `mangadex.org` hosts are exempt per upstream
instructions. Report failures never discard successfully downloaded bytes.
The API abuse-report endpoint's 10/minute limit is not the image-report endpoint.

## Access and verification limits

No authentication, credentials, cookies, OAuth or guest restriction workarounds.
No authentication headers are sent to image/CDN hosts. Native clients do not need
a browser CORS proxy. Android release manifest declares INTERNET permission.
Guest/account policy can restrict service access; no unverified daily quota is
presented as a confirmed API contract. Unit/widget tests use injected offline HTTP
and fake source implementations, never live MangaDex.

Device/network behavior and live service compatibility need separate manual checks.
Windows/iOS local scanning remains unsupported; this is independent of remote routes.
Covers, downloads, reconciliation, canonical IDs and series resume are out of scope.

## Primary references

- [API limitations](https://api.mangadex.org/docs/2-limitations/)
- [Chapter retrieval and image reporting](https://api.mangadex.org/docs/04-chapter/retrieving-chapter/)
- [Manga search](https://api.mangadex.org/docs/03-manga/search/)
- [OpenAPI](https://api.mangadex.org/docs/swagger.html)

Contracts were checked against retained upstream documentation during this slice;
mocked tests establish local behavior, not upstream availability.
