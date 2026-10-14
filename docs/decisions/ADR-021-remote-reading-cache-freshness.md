# ADR-021: Explicit freshness for source-owned reading bytes

Status: Accepted

## Context

Hikari caches normalized remote chapter HTML, page bytes, registered novel image resources,
and source-owned artwork under stable `[sourceId, itemId]` keys. Source identity deliberately
excludes mutable display metadata (ADR-009); neither Mihon nor LNReader integration provides
a reliable per-resource immutable revision. A global TTL on chapter images or HTML would
unnecessarily re-fetch content on reopen and can discard usable offline data.

## Decision

Keep cache-first ordinary reads and stable namespace/key formats. Explicit reload is the
freshness boundary: Manga page reload, Novel chapter reload, Novel resource reload, and
artwork reload bypass the byte cache. The completed source response replaces the old entry;
failed refreshes do not evict the last good entry. A reload requested during an ordinary
same-reference read waits for that read to finish before publishing its newer result. Normal
reads while reload is in progress join the refresh. No broad namespace invalidation occurs.

Manga reader exposes **Reload page** for the current page; if a network reload fails, it
retains the image already on screen and informs the user. Reloading one page does not
re-query the chapter page list or discard adjacent cached pages. Novel reader **Reload
chapter** refreshes the normalized HTML first, then causes registered illustrations
encountered in the resulting HTML to reload from their owning source, including illustrations
with unchanged resource references. A reader-scoped per-reference future map deduplicates
resources during that refresh; an explicit illustration retry replaces a failed
reader-scoped future. Repeated image elements may share one resource without sharing a
Flutter widget key. Failures remain retryable and old cached bytes survive.
Normal chapter navigation and prefetch remain cache-first.

Artwork obtains a matching application-layer `reload(ref)` operation so source artwork can
be refreshed without changing its stable key. UI artwork still remains cache-first unless an
explicit caller requests reload; this operation is not an automatic source-update scanner.

## Boundaries and remaining limitations

No automatic source/plugin revision invalidation or universal TTL is introduced. Stable IDs
must not change solely because an extension APK updated; changed provider URLs are naturally
new source references. In-place upstream page-list changes cannot be detected by refreshing
a single image; ADR-022 adds an explicit page-list refresh/reconciliation path. If a
source changes resource bytes without a new reference, a manual reload is the supported
revalidation path. No guarantee of automatic update while offline. Cache remains expendable;
Library, Progress, extension continuation and local archive leases are never modified.

DiskByteCache storage/eviction, Clear Cache and budget registration are unchanged. Source
and UI reload operations are explicitly scoped; no HTTP ETag is assumed because source
capabilities expose normalized bytes rather than origin HTTP response headers.

## Verification

Regression tests cover artwork reload ordering and failure preservation, current Manga page
refresh failure/success, and Novel chapter refresh with repeated illustration references.
Analyzer, Flutter test suite and Android build must pass on an installed Flutter SDK before
merge.

## References

- [RFC 9111 — HTTP Caching, freshness and validation](https://www.rfc-editor.org/rfc/rfc9111.html)
- [Mihon reader chapter cache (upstream)](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt)
- [LNReader releases and reader caching notes](https://github.com/lnreader/lnreader/releases)
- [ADR-012 — Cache foundation](ADR-012-reconstructible-cache-foundation.md)
- [ADR-009 — Stable source identity](ADR-009-stable-source-identities.md)
- [ADR-022 — Manga page-list cache and reconciliation](ADR-022-manga-page-list-cache-and-refresh.md)
