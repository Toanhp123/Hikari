# OpenStory JavaScript Runtime

`main.js` defines `globalThis.openstoryPlugin`. The host selects a versioned operation,
passes one JSON input object, awaits the handler, validates the returned JSON, and maps
failures to safe machine-readable codes.

```javascript
globalThis.openstoryPlugin = Object.freeze({
  catalog: Object.freeze({
    home: async input => ({sections: []}),
    search: async input => ({items: [], nextToken: null}),
    details: async input => ({
      sourceId: input.sourceId,
      sourceUrl: null,
      title: "Example",
      aliases: [],
      authors: [],
      description: null,
      genres: [],
      contentType: "LIGHT_NOVEL",
      languageTags: ["en"],
      coverUrl: null,
      score: null,
      popularityRank: null
    }),
    filters: async () => ({filters: []})
  })
});
```

## Catalog operations

- `catalog.home` receives `{languageTags, contentTypes}` and returns `{sections}`.
- `catalog.search` receives `{query, filterValues, nextToken}` and returns
  `{items, nextToken}`.
- `catalog.details` receives `{sourceId}` and returns one complete details object.
- `catalog.filters` receives an empty object and returns `{filters}`.

Catalog items contain `sourceId`, `title`, `contentType`, optional authors, optional HTTPS
cover URL, and an optional `{value, scale}` score. Content types are `LIGHT_NOVEL`,
`WEB_NOVEL`, `MANGA`, or `ANIME`. Filter records use the `option`, `range`, and `text`
serialized variants defined and tested in `:plugins:api`.

Packages that declare the `CONTENT` service may implement these operations:

- `content.search` receives `{query, nextToken}` and returns `{items, nextToken}`. Each item
  has a stable `sourceStoryId`, title, optional aliases/authors/content type, and an
  optional HTTPS `sourceUrl`. The host caps candidate counts and validates any returned
  URL against the package's accepted network hosts.
- `content.resolveUrl` receives `{url}` for a user-supplied HTTPS URL whose exact host is
  already accepted by that package. It returns one content-story candidate.
- `content.story` receives `{sourceStoryId}` and returns details for the mapped content story.
- `content.chapters` receives `{sourceStoryId, mode, checkpoint, nextToken}` and returns chapter
  release records plus paging/checkpoint state. Supporting chapter lists does not imply that a
  package can provide reader documents.
- `content.chapter` receives `{sourceReleaseId}` and returns a validated structured chapter
  document. Blocks may be sanitized text blocks or remote image blocks of the form
  `{type: "image", stableId, imageUrl}`. `stableId` must remain stable when an expiring delivery URL
  changes; `imageUrl` must be HTTPS and is fetched by the host without plugin authentication headers.
  Packages emitting image blocks must explicitly declare `capabilities.reader.remoteImages: true`; otherwise
  the Reader rejects those blocks before any image request is created.
  Read surfaces only treat releases from packages that support this operation as reader-capable.
  Packages whose chapter documents depend on remote media that is not fully persisted must declare
  `capabilities.reader.offlineDownload: false`; those releases remain readable online but are excluded
  from explicit offline-download actions.

Packages should declare their implemented wire operations in manifest `operations`. Protocol `1`
packages without that field keep legacy service-level discovery for compatibility. The host uses
operation declarations for capability discovery and returns `plugin.operation_unavailable` before
script execution when an explicitly declared package does not support the requested operation.
The bundled MangaDex package declares `content.search`, `content.resolveUrl`, `content.chapters`,
and `content.chapter`. Its chapter operation returns MangaDex@Home image-page descriptors and declares
`capabilities.reader.remoteImages: true` and `capabilities.reader.offlineDownload: false`, so it
participates in online Reader flows without advertising incomplete offline support.

## Host capabilities

The runtime freezes the global `host` object. Calls cross a bounded serialized bridge;
unknown methods are denied.

### `host.http(request)`

Accepts `{url, method, headers, body}` and returns `{status, body}`. The host enforces HTTPS,
the exact manifest host allowlist, redirect/request/body/response/time budgets, strips
script-provided authorization and cookie headers, and injects managed credentials itself.
The runtime validates the complete HTTPS request target, including every redirect, before managed
credentials are consulted. Providers receive the plugin ID and normalized URL; duplicate
case-insensitive managed-header ownership fails closed. Authentication cookies remain host-owned as
described in [authentication.md](authentication.md).

### `host.html.query(request)`

Accepts `{body, selector, attribute, limit}` and returns `{values}`. Document size, selector
length, result count, and result size are bounded by the host.

### `host.log(event)`

Accepts `{code, detail}` and writes a safe diagnostic through host-owned persistence.
Plugins must not log secrets, credentials, full response bodies, or private URLs.

## Failures and cancellation

Throw an `Error` with a stable `code` for an expected plugin failure. Uncoded failures map
to `plugin.execution_failed`. Host capability failures also use stable codes. Cancellation
terminates the invocation and is not converted into a plugin result.

The bundled MyAnimeList catalog and MangaDex content packages are production fixtures for
protocol `1`. The app may register multiple bundled packages; none has a privileged runtime path,
and all use the same manifest, bridge, validation, capabilities, and budgets as third-party packages.
