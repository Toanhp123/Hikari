# Declarative Selector Plugin Schema

The declarative selector schema defines a bounded, non-Turing-complete pipeline for extracting catalog and content data.

The host owns parsing, validation, networking, and execution. Plugin definitions contain data only and cannot execute scripts, reflection, filesystem access, Android APIs, replacement callbacks, or user-defined regular expressions.

## Schema version

The current schema version is `1`.

```json
{
  "schemaVersion": 1,
  "operations": []
}
```

The host rejects unknown schema versions with:

```text
UNSUPPORTED_SCHEMA_VERSION
```

Existing operation semantics must never be silently changed. Any incompatible addition or reinterpretation requires a new schema version.

## Validation lifecycle

`SelectorValidation.validate` must run before package installation or runtime initialization.

Validation checks:

- the pipeline is not empty;
- the schema version is supported;
- operation input and output types connect correctly;
- request URLs are relative or absolute HTTPS URLs on declared hosts;
- CSS selectors and attribute names are non-blank;
- no unsupported executable operation exists.

A successful validation returns `Result.success(Unit)`. A failure contains `SelectorValidationException` with a stable `SelectorValidationErrorCode`.

## Value types

| Type | Meaning |
|---|---|
| `NONE` | No prior pipeline value |
| `DOCUMENT` | Host-parsed document |
| `ELEMENTS` | Selected document elements |
| `TEXT` | Extracted text |

## Operations

### `http_get`

Fetches a document through the host-controlled network gateway.

Type:

```text
NONE -> DOCUMENT
```

Relative URL example:

```json
{
  "type": "http_get",
  "urlTemplate": "/search?q={query}"
}
```

Declared absolute URL example:

```json
{
  "type": "http_get",
  "urlTemplate": "https://allowed.example/search?q={query}"
}
```

Possible validation errors:

- `BLANK_URL_TEMPLATE`
- `PROTOCOL_RELATIVE_URL`
- `INSECURE_SCHEME`
- `INVALID_ABSOLUTE_URL`
- `UNDECLARED_HOST`
- `TYPE_MISMATCH`

Absolute URLs must use HTTPS and their normalized host must appear in the plugin manifest's `allowedHosts`. Relative URLs are resolved by the host against a declared plugin origin.

### `remove_elements`

Removes matching elements from the current document before extraction.

Type:

```text
DOCUMENT -> DOCUMENT
```

Example:

```json
{
  "type": "remove_elements",
  "css": ".advertisement"
}
```

Possible validation errors:

- `BLANK_CSS_SELECTOR`
- `TYPE_MISMATCH`

### `select_all`

Selects all elements matching a CSS selector.

Type:

```text
DOCUMENT -> ELEMENTS
```

Example:

```json
{
  "type": "select_all",
  "css": "article.chapter"
}
```

Possible validation errors:

- `BLANK_CSS_SELECTOR`
- `TYPE_MISMATCH`

### `select_text`

Extracts text from the current element selection.

Type:

```text
ELEMENTS -> TEXT
```

Example:

```json
{
  "type": "select_text",
  "css": ".chapter-title"
}
```

Possible validation errors:

- `BLANK_CSS_SELECTOR`
- `TYPE_MISMATCH`

### `select_attribute`

Extracts an attribute from the current element selection. It can be used to obtain links such as a next-page URL; the host must validate any resulting request before fetching it.

Type:

```text
ELEMENTS -> TEXT
```

Example:

```json
{
  "type": "select_attribute",
  "css": "a.next-page",
  "attribute": "href"
}
```

Possible validation errors:

- `BLANK_CSS_SELECTOR`
- `BLANK_ATTRIBUTE_NAME`
- `TYPE_MISMATCH`

### `normalize_whitespace`

Applies the host-provided whitespace normalization transform.

Type:

```text
TEXT -> TEXT
```

Example:

```json
{
  "type": "normalize_whitespace",
  "enabled": true
}
```

Possible validation errors:

- `TYPE_MISMATCH`

The operation invokes a fixed host transformation. Plugins cannot provide callbacks or executable replacement expressions.

## Complete pipeline example

```json
{
  "schemaVersion": 1,
  "operations": [
    {
      "type": "http_get",
      "urlTemplate": "/story/{sourceStoryId}"
    },
    {
      "type": "remove_elements",
      "css": ".advertisement"
    },
    {
      "type": "select_all",
      "css": "article.chapter"
    },
    {
      "type": "select_text",
      "css": ".chapter-body"
    },
    {
      "type": "normalize_whitespace",
      "enabled": true
    }
  ]
}
```

## Schema-level errors

| Error code | Meaning |
|---|---|
| `EMPTY_PIPELINE` | No operations were declared |
| `UNSUPPORTED_SCHEMA_VERSION` | The host does not support the schema version |
| `TYPE_MISMATCH` | An operation received an incompatible pipeline value |
| `BLANK_URL_TEMPLATE` | A request template was empty |
| `PROTOCOL_RELATIVE_URL` | A URL began with `//` |
| `INSECURE_SCHEME` | An absolute URL did not use HTTPS |
| `INVALID_ABSOLUTE_URL` | An absolute URL was malformed or contained user information |
| `UNDECLARED_HOST` | An absolute URL targeted a host absent from `allowedHosts` |
| `BLANK_CSS_SELECTOR` | A CSS selector was empty |
| `BLANK_ATTRIBUTE_NAME` | An attribute name was empty |

## Security boundary

The operation hierarchy is closed and serializable. Schema version 1 exposes no operation for:

- JavaScript or other scripts;
- reflection or dynamic class loading;
- filesystem paths or file reads;
- Android services or application context;
- arbitrary regular expressions;
- user-provided replacement callbacks;
- direct networking outside the host gateway.

Unknown operation discriminators must fail decoding or validation. They must never be ignored or interpreted as another operation.

---

# Selector Schema Version 2: Typed Output Bindings

OpenStory additionally supports a versioned endpoint-oriented selector schema that produces Catalog and Content wire DTOs through host-owned evaluation and validation. Version 1 remains unchanged and is never reinterpreted as Version 2.

## Version dispatch

```text
schemaVersion = 1 -> SelectorPluginDefinition
schemaVersion = 2 -> SelectorPluginDefinitionV2
otherwise         -> UNSUPPORTED_SCHEMA_VERSION
```

The host reads only `schemaVersion` before choosing the version-specific serializer.

## Endpoint matrix

Version 2 covers every current public plugin method:

```text
Catalog
  home       -> List<CatalogSection>
  search     -> Page<CatalogCard>
  details    -> CatalogDetails
  filters    -> List<CatalogFilterDefinition>

Content
  search       -> Page<ContentStoryCandidate>
  story        -> ContentStoryDetails
  latest       -> List<SourceChapterRelease>
  allChapters  -> List<SourceChapterRelease>
  sync         -> ChapterSyncDelta
  chapter      -> ChapterDocument
```

A definition may expose Catalog, Content, or both groups. Every present group must contain at least one endpoint, and the manifest must declare the matching plugin kind.

## Declarative origin

Relative request templates require the additive manifest field:

```json
{
  "declarativeOrigin": "https://source.example/"
}
```

The origin:

- is accepted only for `DECLARATIVE` plugins;
- must be an absolute HTTPS URI;
- cannot contain user information, a query, or a fragment;
- must use a host present in `allowedHosts`;
- is persisted with installed package state;
- is never inferred from iteration order of `allowedHosts`.

A declarative package that uses only absolute request templates may omit it.

## Closed binding AST

The `type` discriminator accepts only these serialized binding variants:

- `element_text`, `text`, `attribute`, `constant`;
- `optional`;
- `integer`, `long`, `double`, `boolean`, `enum`, `timestamp`, `url`;
- `text_list`, `text_set`;
- `object`, `list`.

Bindings contain data only. They cannot contain JavaScript, callbacks, class names, serializers, reflection targets, plugin-provided regular expressions, filesystem paths, Android references, or executable replacement expressions.

### Value rules

- Required output fields cannot use an optional binding.
- Optional fields may return `null`; missing values are not converted to empty strings.
- Lists preserve document order.
- Sets preserve first occurrence while removing duplicates.
- Numeric, boolean, enum, timestamp, and URL conversion is explicit.
- Opaque continuation tokens are never guessed to be URLs.
- URL bindings are resolved and validated by the host without fetching the output URL.

## Catalog selector contracts

`home`, `search`, and `details` combine one bounded request plan with typed output bindings. `filters` is static package data in the initial Version 2 contract.

Install-time validation checks required fields, optional fields, binding types, duplicate filter IDs/options, range bounds, and unknown output fields. Runtime wire validation still verifies stable IDs, collection limits, score ranges, language tags, and declared URL hosts.

## Content selector contracts

Content endpoints use the same request and binding core. Chapter bodies use a closed ordered block set:

- paragraph;
- heading;
- divider;
- image;
- note.

`SEMANTIC_HTML` permits only host-approved emphasis and strong semantics. Raw HTML, scripts, style declarations, and arbitrary tags are not returned as chapter wire data.

## Install-time limits

The contract validator applies these hard bounds:

- maximum operations in one request plan: 64;
- maximum URL template length: 2,048 characters;
- maximum CSS selector length: 1,024 characters;
- maximum attribute name length: 128 characters;
- maximum binding depth: 12;
- maximum bindings in one validated output tree: 512;
- maximum object fields: 128;
- maximum static filters: 64;
- maximum static options per filter: 200.

Plugin-requested output limits can only reduce host maxima. Operation, document, node, parser, regex, and wall-clock ceilings remain host-owned.

## Version 2 validation failures

In addition to Version 1 errors, Version 2 may fail with:

| Error code | Meaning |
|---|---|
| `EMPTY_DEFINITION` | No Catalog or Content endpoint group was declared |
| `EMPTY_ENDPOINT_GROUP` | A declared endpoint group contains no endpoint |
| `INVALID_DEFINITION` | The envelope, manifest relationship, or known schema field is invalid |
| `INVALID_DECLARATIVE_ORIGIN` | A relative request lacks a validated origin |
| `INVALID_REQUEST_LIMIT` | A plugin-requested limit is outside host bounds |
| `EXCESSIVE_OPERATION_COUNT` | A request declares too many operations |
| `EXCESSIVE_BINDING_DEPTH` | A binding tree is nested beyond the limit |
| `EXCESSIVE_BINDING_COUNT` | A binding tree contains too many bindings |
| `INVALID_BINDING_PATH` | An object field name or structure is invalid |
| `INVALID_CONSTANT` | A constant, alias, filter, or block declaration is invalid |
| `OUTPUT_TYPE_MISMATCH` | An endpoint field is missing, unknown, or bound to the wrong type |
| `INVALID_TIMESTAMP_CONFIGURATION` | Timestamp format, host pattern ID, or timezone configuration is incoherent |

Unknown schema versions and unknown polymorphic variants fail closed during decoding.

The ZIP inspector decodes and validates the selected schema before installation can publish a package. Unknown versions, malformed polymorphic variants, invalid request plans, and output-shape mismatches therefore fail before runtime initialization.

## Complete deterministic fixture

The complete no-network fixture is committed at:

```text
sample-plugins/selector-v2-fixture/selector.json
```

It covers all four Catalog endpoints, all six Content endpoints, every static filter type, nested objects/lists, timestamps, and every supported chapter block variant.
