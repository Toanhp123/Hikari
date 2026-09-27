# Remote manga: MangaDex

## Scope and boundaries

The current remote slice is an architecture probe, not a generic provider or plugin
framework. The search screen enumerates registered `MangaSearchSource` implementations;
`MangaSearchSource` returns normalized `Media`; `MangaChapterSource`
returns provider-neutral chapter metadata; `MangaPageSource` resolves page references
and bytes for a selected readable. Domain stays pure Dart. HTTP, MangaDex JSON,
rate limits, UUID validation and MangaDex@Home handling stay in infrastructure.

Application owns an immutable `SourceRegistry` and resolves sources by stable ID plus
capability. Duplicate source IDs fail during composition. `OpenMedia` routes a remote
series to the generic chapter list and `OpenMangaChapter` resolves pages before the
reader; local manga still opens folder pages directly. Remote navigation is independent
of Android local scanning. There is no dynamic plugin/extension runtime or DI framework.
See [SOURCES](SOURCES.md) for the shared source/application boundary.

## Identity and user state

MangaDex owns `SourceId('mangadex')`.

- series reference: MangaDex manga UUID;
- chapter reference: MangaDex chapter UUID;
- page reference: opaque `chapterUUID/zeroBasedIndex` owned by the source.

These are source-local locators, not canonical content identities. MangaDex@Home
`baseUrl`, chapter hash, filenames and complete image URLs are transport state and are
never persisted as identity.

Library saves the top-level series snapshot. Progress for remote manga is currently
saved against the selected chapter reference with the existing `PagePosition`.
Reopening that same chapter resumes its page. Removing the series from Library does
not remove chapter progress. Series-level “resume last chapter” remains deliberately
deferred.

## Chapter availability

The MangaDex feed can contain both MangaDex-hosted chapters and chapters whose
`externalUrl` points to another service. Hikari keeps eligible external entries visible
in the chapter list for an accurate view of the feed, but marks them as not readable in
Hikari; it does not scrape or proxy another publisher/service to turn those links into
pages.

Unavailable, future-readable and invalid internal empty chapters are omitted. Distinct
chapter UUIDs remain distinct even when logical chapter numbers match; no scanlation-
group deduplication policy exists.

MangaDex feed metadata is not a guarantee that MangaDex@Home still has the chapter.
A chapter can remain in the feed while `GET /at-home/server/{chapterId}` returns 404.
Hikari therefore resolves page metadata before pushing the reader. A stale/removed
chapter fails on the chapter-selection screen instead of opening a reader that can only
show a page-load error. Hikari does not preflight every chapter because that would add
unnecessary MangaDex@Home requests and consume the provider's endpoint quota.

## Search and feed behavior

- Explicit title search returns the first 20 results with English chapter availability
  requested and safe/suggestive content ratings.
- English chapter feed requests use up to 500 rows, ascending volume/chapter and
  expanded scanlation-group relationships. `includeEmptyPages` is intentionally not
  forced because upstream behavior has changed and clients have seen valid chapters
  disappear when that parameter was used.
- Pagination advances by raw returned rows, including rows later omitted from reading.
  Totals above 10,000, no-progress pagination and malformed payloads fail explicitly.
- Scanlation-group credit is kept visible in the chapter list and reader.

## MangaDex@Home transport

`MangaDexClient` owns MangaDex-specific headers, throttling, abort/timeout behavior,
status handling and response-size caps; it is not a generic network abstraction.
`GET /at-home/server/{chapterId}` provides a dynamic HTTPS `baseUrl`, chapter hash and
ordered page filenames. Original `data` quality is used for this slice. One chapter
session is cached in memory for at most 15 minutes; page refs remain stable across
session refreshes.

A failed image invalidates the session. HTTP 403 refreshes the MangaDex@Home metadata
and retries once; there is no unbounded retry loop. API response bodies cap at 8 MiB,
page images at 32 MiB, and ordinary requests time out after 30 seconds.

MangaDex requires image retrieval reports for applicable MangaDex@Home hosts. Hikari
captures URL, success, `X-Cache` HIT status, byte count and complete retrieval duration,
then submits the report best-effort with a bounded timeout. Reporting is off the reader
critical path: a successfully downloaded image is returned without waiting for the
report response, and report failure cannot replace the image result. Source disposal
aborts owned in-flight requests.

## Current upstream constraints

Verified against current MangaDex documentation on 2026-09-27:

- requests require a truthful `User-Agent`;
- the documented global `api.mangadex.org` allowance is approximately 5 requests/s
  per IP;
- `GET /at-home/server/{id}` has an endpoint-specific limit of 40 requests/minute;
- MangaDex@Home base URLs are temporary and should be re-resolved after expiry/403;
- chapters with `externalUrl` are hosted externally and are not MangaDex@Home pages;
- consumers must credit MangaDex and scanlation groups when offering chapter reading.

The implementation serializes API starts with at least 250 ms spacing and at-home
starts with at least 1,500 ms spacing. HTTP 429 surfaces as an error rather than being
retried aggressively. No OAuth, account sync, cookies, guest-limit workaround or
browser proxy is implemented. Native Flutter clients are not subject to browser CORS in
the same way as a website, so no proxy architecture is added for this Android-first
slice.

## Verification boundary and deferred work

Automated tests use injected HTTP/fake sources and do not prove live MangaDex
availability. Physical Android verification still needs to cover search, chapter list,
MangaDex@Home reading, background/reopen, chapter progress, Library restart and slow or
failed networks.

Covers, downloads, disk image cache, auth/account features, series-level last-chapter
resume, canonical identity, chapter dedup policy, additional remote providers and a
provider/plugin runtime remain out of scope.

## Primary upstream references

- [MangaDex OpenAPI / Swagger](https://api.mangadex.org/docs/swagger.html)
- [API limitations](https://api.mangadex.org/docs/2-limitations/)
- [Manga search](https://api.mangadex.org/docs/03-manga/search/)
- [Chapter feed](https://api.mangadex.org/docs/04-chapter/feed/)
- [Chapter retrieval and MangaDex@Home reporting](https://api.mangadex.org/docs/04-chapter/retrieving-chapter/)
