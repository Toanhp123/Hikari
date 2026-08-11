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
  already accepted by that package. It returns one content-story candidate. Packages may
  omit this operation; unsupported URL resolution is a bounded source failure.
- `content.story`, `content.chapters`, and `content.chapter` remain reserved for the later
  content-reading waves.

## Host capabilities

The runtime freezes the global `host` object. Calls cross a bounded serialized bridge;
unknown methods are denied.

### `host.http(request)`

Accepts `{url, method, headers, body}` and returns `{status, body}`. The host enforces HTTPS,
the exact manifest host allowlist, redirect/request/body/response/time budgets, strips
script-provided authorization and cookie headers, and injects managed credentials itself.

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
