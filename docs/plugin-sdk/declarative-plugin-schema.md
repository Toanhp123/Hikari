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
