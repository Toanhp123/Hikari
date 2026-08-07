# Declarative Selector Plugin Schema

OpenStory declarative plugins describe bounded document requests and typed Catalog or
Content output bindings. The definition is data, not executable code.

## Schema version

Selector Schema `1` is the initial and only supported declarative selector schema.

```json
{
  "schemaVersion": 1,
  "catalog": null,
  "content": null
}
```

The host rejects any other value with `UNSUPPORTED_SCHEMA_VERSION`. There is no
development-generation compatibility dispatch in the public contract.

Selector schema, Plugin API version, repository-index schema, Room schema, application
version, and package layout are independent version spaces. A plugin package does not
declare a separate package-layout schema-version field.

## Validation lifecycle

Before activation, the host performs this flow:

```text
read selector.json
  -> decode SelectorDefinition
  -> require schemaVersion = 1
  -> validate request plans, bindings, endpoint output shapes, and manifest capabilities
  -> accept or reject the package
```

Unknown fields and unknown polymorphic binding types fail closed. Validation occurs
before a selector runtime or plugin adapter is initialized.

## Root contract

`SelectorDefinition` may expose Catalog endpoints, Content endpoints, or both. At least
one capability must agree with the package manifest.

| Group | Endpoints |
|---|---|
| Catalog | `home`, `search`, `details`, `filters` |
| Content | `search`, `story`, `latest`, `allChapters`, `sync`, `chapter` |

Catalog metadata and readable Content contracts remain separate even when they share
one `selector.json`.

## Request plans

Each network-backed endpoint owns a `SelectorRequestPlan` with an ordered `operations`
list and optional requested output limits.

Supported request operations:

| Type | Fields | Behavior |
|---|---|---|
| `http_get` | `urlTemplate` | Fetch one document through the host-owned HTTP gateway |
| `remove_elements` | `css` | Remove matching elements from the current document |

`http_get` templates may use named inputs such as `{query}` or `{sourceStoryId}`.
Inputs are URL-encoded by the host. Relative templates require the package's persisted
declarative origin. Network allowlists, redirects, response limits, cancellation, and
diagnostic redaction remain host responsibilities.

Requested limits may narrow `maxOutputItems`, `maxChapterBlocks`, and
`maxChapterTextCharacters`; they never weaken host ceilings.

## Closed binding model

Bindings are a closed, non-executable AST. The serialized `type` values are:

| Category | Types |
|---|---|
| Text sources | `element_text`, `text`, `attribute`, `constant` |
| Wrappers/scalars | `optional`, `integer`, `long`, `double`, `boolean`, `enum`, `timestamp`, `url` |
| Collections/objects | `text_list`, `text_set`, `object`, `list` |

Text and attribute bindings may select relative to the current element and normalize
whitespace. Scalar bindings parse a text source. URL bindings resolve against the
fetched document base URL and must pass the same validation-only URL policy used by
plugin networking.

Objects declare a field-to-binding map. Lists select ordered elements and evaluate one
item binding relative to each element. Optional bindings permit absence; required
fields fail when missing or malformed.

Timestamp formats are `EPOCH_MILLIS`, `EPOCH_SECONDS`, `ISO_8601`, and
`HOST_PATTERN_ID`. Pagination tokens are classified as `OPAQUE` or `URL`.

## Catalog contracts

- `home` maps ordered sections containing story summaries.
- `search` maps story summaries and an optional next token.
- `details` maps one catalog story detail record.
- `filters` declares select, multi-select, range, text, and sort filters.

Install-time validation checks every endpoint binding against the corresponding public
Catalog wire DTO shape.

## Content contracts

- `search` maps content-source story summaries and an optional next token.
- `story` maps one content-source story detail record.
- `latest` and `allChapters` map chapter-release lists.
- `sync` maps upserts, tombstone source-release IDs, and an optional next cursor.
- `chapter` maps a title and ordered semantic chapter blocks.

Chapter blocks support paragraph, heading, divider, image, and note variants. Text spans
may use `NONE` or `SEMANTIC_HTML`. Unmatched elements use the endpoint's declared `SKIP`
or `ERROR` policy.

## Security and limits

The validator and host enforce bounded definition depth, binding counts, selector text,
request operations, document characters, DOM nodes, produced items, chapter blocks,
chapter text, and wall-clock work. A selector cannot access Android APIs, Room, files,
processes, reflection, cookies, headers, or arbitrary network hosts.

Errors expose stable codes and safe field paths. They do not expose raw HTML, chapter
text, credentials, private URLs, or cursor contents.

## Canonical complete fixture

The literal SDK example is the repository fixture
[`sample-plugins/selector-fixture/selector.json`](../../sample-plugins/selector-fixture/selector.json).
It is decoded by contract tests and covers all four Catalog and all six Content endpoint
shapes. Documentation links to that file instead of maintaining a second hand-written
copy that can drift from the tested contract.

Reusable plugin-author contract assertions and fixture builders are exported through
`:core:plugin-api` test fixtures. The separate `:test:fixtures` module owns only internal
deterministic fake implementations and data used by the host application test suite.
