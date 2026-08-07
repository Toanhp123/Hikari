<!--
DOCUMENT LIFECYCLE
Status: HISTORICAL / SUPERSEDED BY SELECTOR SCHEMA 1 BASELINE
Canonical SDK contract: ../../plugin-sdk/declarative-plugin-schema.md
The original design text below is retained for audit and must not be executed.
-->

# Historical Selector Output Bindings Design

## Status

Approved Wave 03 remediation design. This document owns only the public serialized contracts and install-time validation; runtime evaluation remains Wave 04.

This specification closes the Wave 03 public-contract gap by adding a versioned, typed output-binding schema capable of describing every current Catalog and Content plugin wire DTO. Wave 04 consumes these contracts to execute selectors.

The bounded selector interpreter introduced by commit `05bd13e` remains the
low-level execution engine. Schema version 1 remains supported without semantic
reinterpretation.

## Context

Selector schema version 1 is a bounded linear pipeline:

```text
NONE -> DOCUMENT -> ELEMENTS -> TEXT
```

It supports host-owned request execution, HTML parsing, CSS selection,
attribute extraction, document cleanup, and fixed whitespace normalization.

Version 1 does not describe:

- endpoint identity;
- output DTO type;
- item grouping;
- object fields;
- nested lists or objects;
- required, optional, and default values;
- conversion to numeric, enum, timestamp, URL, or structured chapter values;
- mapping into Catalog or Content wire contracts.

Wave 04 Task 03 requires a deterministic bounded selector runtime that returns
plugin wire DTOs. The Wave 04 checkpoint also requires selector and JavaScript
fixture plugins to return the same contract DTOs. A versioned output-binding
contract is therefore required before Task 03 can be closed.


## Wave ownership boundary

Wave 03 owns:

- manifest origin and compatibility invariants;
- schema V2 envelope and version-aware decoding;
- closed serialized binding DTOs;
- Catalog and Content endpoint declarations;
- install-time structural/semantic validation;
- package/repository hardening and deterministic fixtures;
- a JVM contract checkpoint.

Wave 04 owns networking, HTML parsing, binding evaluation, DTO mapping, runtime budgets, cancellation, and final host output validation.

## Goals

Version 2 must:

1. Produce every current Catalog and Content plugin wire DTO.
2. Preserve version 1 semantics and decoding behavior.
3. Remain declarative and non-Turing-complete.
4. Use host-owned networking, parsing, transforms, conversion, mapping, and
   validation.
5. Produce deterministic typed errors with safe nested field paths.
6. Enforce operation, document, node, element, text, regex, binding, output,
   and wall-clock budgets.
7. Share final wire DTO validation with the JavaScript runtime.
8. Propagate cancellation without partial output.
9. Never expose raw HTML, chapter content, credentials, or raw cursors in
   diagnostics.
10. Support deterministic fixture tests without live third-party websites.

## Non-goals

Version 2 does not add:

- JavaScript or another scripting language;
- reflection or dynamic class loading;
- arbitrary class names or serializers;
- filesystem or Android API access;
- plugin-provided regular expressions;
- callbacks or executable replacement expressions;
- direct networking outside the scoped gateway;
- automatic conversion of version 1 definitions;
- host inference that zips unrelated version 1 text pipelines into DTOs;
- live website dependencies in tests.

## Versioning model

Definitions are decoded through a version-aware envelope.

```text
SelectorDefinitionDecoder
├── schemaVersion = 1 -> SelectorPluginDefinition
├── schemaVersion = 2 -> SelectorPluginDefinitionV2
└── otherwise         -> UNSUPPORTED_SCHEMA_VERSION
```

The decoder reads only `schemaVersion` first, then delegates to the correct
serializer and validator. Version-specific fields are not combined into one
nullable data class.

### Version 1

The existing `SelectorPluginDefinition` remains unchanged.

Its existing operations keep their current meanings:

- `HttpGet`
- `RemoveElements`
- `SelectAll`
- `SelectText`
- `SelectAttribute`
- `NormalizeWhitespace`

Version 1 continues to produce the internal `SelectorValue` model. The host
does not reinterpret a final `TEXT` value as a Catalog or Content DTO.

### Version 2

`SelectorPluginDefinitionV2` is endpoint-oriented:

```text
SelectorPluginDefinitionV2
├── catalogEndpoints
└── contentEndpoints
```

A declarative package exposed through the unified `CatalogPlugin` or
`ContentPlugin` host facade must provide the corresponding version 2 endpoint
bindings.

## Declarative origin contract

Relative request URLs require one declared plugin origin.

The current manifest contains `allowedHosts` but no origin field. Version 2
therefore requires an additive manifest contract:

```kotlin
val declarativeOrigin: String? = null
```

Rules:

- required for a declarative plugin that uses any relative request template;
- absolute HTTPS URI;
- no user information, query, or fragment;
- normalized host must be present in `allowedHosts`;
- passed by the installer/registry into `SelectorExecutionContext`;
- never inferred from iteration order of `allowedHosts`;
- never accepted from a runtime request parameter.

Version 1 remains callable with an explicit host-owned
`SelectorExecutionContext`. Migration tooling should warn when a version 1
package uses relative URLs without a persisted origin.

## Architecture

```text
Version 2 endpoint
        |
        v
versioned decode and install-time validation
        |
        v
runtime context and URL policy validation
        |
        v
allowlisted request execution
        |
        v
bounded HTML document
        |
        v
typed binding evaluator
        |
        v
internal SelectorBoundValue
        |
        v
CatalogDtoMapper / ContentDtoMapper
        |
        v
shared PluginWireDtoValidator
        |
        v
plugin-api wire DTO
```

### Contract layer

Located in `core:plugin-api`.

Responsibilities:

- version 2 serializable models;
- endpoint declarations;
- closed binding declarations;
- stable schema validation error codes;
- install-time structural and semantic validation.

### Shared URL policy

Located in `core:network`.

Introduce a validation-only component shared by the gateway and output
validators:

```text
PluginUrlPolicy
├── resolve(baseUri, candidate)
├── parse
├── require supported scheme
├── require declared host
└── return ValidatedPluginUrl
```

It performs no network request.

`AllowlistedHttpGateway` must use the same policy before sending requests.
Catalog and Content URL/reference validators use it when returning source,
cover, chapter, or image URLs.

This prevents duplicated host/scheme logic and avoids using the gateway merely
to validate an output URL.

### Document adapter

Located in `core:plugin-host`.

`HtmlDocumentAdapter` remains the isolation boundary around Jsoup or another
host parser. Version 2 extends the adapter with bounded operations needed by
nested binding and chapter mapping:

- select child elements relative to an element;
- read text and attributes with presence information;
- preserve document order;
- inspect host-approved semantic tags;
- create text plus safe emphasis/strong spans;
- normalize relative URLs against the parsed document base URI;
- count visited nodes and emitted characters.

Feature and domain modules never depend on Jsoup.

### Binding evaluator

Located in `core:plugin-host`.

Responsibilities:

- evaluate a closed binding AST;
- maintain current document/element/item context;
- apply global endpoint budgets;
- preserve deterministic ordering;
- check cancellation in large traversals;
- return internal bound values;
- attach safe field paths to failures.

### DTO mappers

Located in `core:plugin-host`.

Responsibilities:

- map internal values into existing plugin-api DTOs;
- catch conversion and constructor failures;
- apply endpoint-specific default rules;
- produce typed `AppResult` failures;
- avoid reflection and arbitrary serializers.

### Shared wire DTO validator

Located in `core:plugin-host`.

Responsibilities:

- validate final Catalog and Content outputs;
- enforce collection and text limits;
- reject duplicate stable IDs;
- validate URLs and declared hosts through `PluginUrlPolicy`;
- validate score, language, timestamp, sync, block, and span invariants;
- serve both selector and JavaScript runtimes.

## Binding core

The public serialized AST uses concrete non-generic classes. Generic Kotlin
types may be used internally but are not part of the serialized plugin schema.

Closed binding categories:

```text
SelectorBinding
├── TextBinding
├── AttributeBinding
├── ConstantTextBinding
├── IntegerBinding
├── LongBinding
├── DoubleBinding
├── BooleanBinding
├── EnumBinding
├── TimestampBinding
├── UrlBinding
├── OptionalBinding
├── TextListBinding
├── TextSetBinding
├── ObjectBinding
└── ListBinding
```

Bindings contain data only. They cannot contain callbacks, scripts, class
names, reflection targets, filesystem paths, Android references, or arbitrary
regex patterns.

### Scalar sources

Text values may originate from:

- selected element text;
- selected element attributes;
- constants;
- explicitly declared request inputs.

All text is subject to host-owned character limits. Whitespace normalization
uses only fixed host transforms.

### Required fields

A required field that yields no value, an absent attribute, or a blank value
after required normalization returns:

```text
plugin.selector_field_missing
```

Example:

```text
field_path = items.2.title
```

### Optional fields

An optional binding that produces no value maps to `null`. It does not create
an empty placeholder string.

### Lists and sets

Lists preserve document order.

Sets preserve first occurrence order while removing duplicates.

Nested lists share the endpoint-wide budget; they do not reset operation,
element, text, or output counters.

### Numeric and enum conversion

Numeric and enum conversions are explicit and host-owned.

Failures return:

```text
plugin.selector_field_invalid
```

No parser or DTO constructor exception escapes the runtime.

### Timestamp conversion

Timestamp bindings use a closed format identifier:

```text
EPOCH_MILLIS
EPOCH_SECONDS
ISO_8601
HOST_PATTERN_ID
```

`HOST_PATTERN_ID` references a host-supported format table. Plugins do not
supply arbitrary date patterns or regexes.

A value without an offset must declare a validated timezone identifier. Device
locale and device timezone are never implicit inputs.

### URL values and opaque tokens

Bindings explicitly distinguish:

```text
OPAQUE
URL
```

Opaque values are never guessed to be URLs.

URL values are resolved and validated through `PluginUrlPolicy`. Output URL
validation does not fetch the resource.

## Internal bound values

The evaluator produces an internal model:

```text
SelectorBoundValue
├── Null
├── Text
├── Integer
├── Long
├── Double
├── Boolean
├── List
└── Object
```

Objects use deterministic insertion-ordered keys.

This model is private to the host and is not serialized into plugin packages.

## Catalog endpoints

Version 2 supports every current `CatalogPlugin` endpoint:

```text
home    -> List<CatalogSection>
search  -> Page<CatalogCard>
details -> CatalogDetails
filters -> List<CatalogFilterDefinition>
```

### Catalog home

A home endpoint contains:

- a request plan;
- a section collection binding;
- nested card collection bindings.

Required section fields:

- `sourceId`;
- `title`.

Nested diagnostics use paths such as:

```text
sections.1.items.4.title
```

Budgets include section count, items per section, and total items.

### Catalog search

A search endpoint contains:

- a request plan;
- a card collection binding;
- an optional next-token binding;
- an explicit next-token kind.

The runtime produces `Page<CatalogCard>`.

Duplicate `sourceId` values within a page are rejected.

### Catalog card

Required:

- `sourceId`;
- `title`.

Defaults:

- `authors = emptyList()`.

Optional:

- `image`;
- `score`.

`CatalogImageReference.declaredHost` is derived from the validated URL unless
an explicit bound value is supplied. An explicit value must equal the actual
URL host.

`CatalogScore` values must satisfy the DTO invariants.

### Catalog details

Required:

- `sourceId`;
- `title`;
- `contentType`;
- `languageTags`.

Optional and collection fields follow the current DTO contract.

`sourceUrl` and image URLs use the shared URL policy.

`popularityRank`, when present, must be non-negative.

### Catalog filters

Initial version 2 filter definitions are static constants for:

- select;
- multi-select;
- range;
- text;
- sort.

Install-time validation checks:

- non-blank unique filter IDs;
- unique option values;
- valid range bounds;
- positive range step;
- required labels and values;
- known filter discriminator.

Remote HTML does not define public filter IDs in the initial scope.

## Content endpoints

Version 2 supports every current `ContentPlugin` endpoint:

```text
search      -> Page<ContentStoryCandidate>
story       -> ContentStoryDetails
latest      -> List<SourceChapterRelease>
allChapters -> List<SourceChapterRelease>
sync        -> ChapterSyncDelta
chapter     -> ChapterDocument
```

### Content search

Required:

- `sourceStoryId`;
- `title`;
- `contentType`;
- `languageTags`.

Optional:

- `sourceUrl`.

Default:

- `authors = emptyList()`.

Duplicate `sourceStoryId` values are rejected.

### Content story details

Required:

- `sourceStoryId`;
- `sourceUrl`;
- `title`;
- `contentType`;
- `languageTags`.

Direct catalog mappings are data only. They never trigger another plugin call.

Duplicate mapping pairs are rejected.

### Source chapter releases

Required:

- `sourceReleaseId`;
- `sourceUrl`;
- `languageTag`;
- `rawTitle`.

`kindHint` defaults to `UNKNOWN`.

Raw numbering/title fields are preserved. The selector runtime does not invent
normalized hints.

Normalized hints use host validators. Duplicate release IDs are rejected.

### Chapter sync delta

Maps:

- upserts;
- tombstone source release IDs;
- optional next cursor.

An ID cannot appear in both upserts and tombstones.

Blank tombstone IDs, duplicate upserts, and conflicts return typed failures.

The cursor is opaque unless explicitly declared as a URL.

### Chapter document

A chapter document contains:

- optional title;
- ordered chapter blocks.

Supported block variants:

- paragraph;
- heading;
- divider;
- image;
- note.

The block evaluator traverses selected DOM nodes in document order. Variant
matching uses bounded CSS matchers only.

No XPath, script, callback, or arbitrary predicate is allowed.

### Chapter text and spans

Chapter text supports:

```text
NONE
SEMANTIC_HTML
```

`SEMANTIC_HTML` maps only:

```text
em, i       -> EMPHASIS
strong, b   -> STRONG
```

Raw HTML and arbitrary styles are discarded.

Generated spans satisfy:

```text
0 <= start < endExclusive <= text.length
```

Span order is deterministic. Nested approved styles may produce overlapping
spans when permitted by the DTO.

### Chapter images

Image URLs are normalized and validated without downloading the image.

`declaredHost` must match the actual URL host and be declared in the plugin
manifest.

Unsupported schemes such as `data:` and `javascript:` are rejected.

## Request plans

Version 2 request plans reuse the bounded host request model.

Relative templates resolve against the validated manifest
`declarativeOrigin`.

Absolute templates must use HTTPS and target an allowed host.

The allowlisted gateway validates every initial request and every redirect
through the shared `PluginUrlPolicy`.

## Validation lifecycle

### Phase 1: Envelope decoding

Read only `schemaVersion`.

Unknown versions fail with:

```text
UNSUPPORTED_SCHEMA_VERSION
```

### Phase 2: Install-time validation

Before package activation, validate:

- endpoint compatibility with plugin kinds;
- duplicate endpoints;
- known binding types;
- required output fields;
- DTO field/binding compatibility;
- CSS and attribute names;
- request templates and template variables;
- manifest origin and allowed-host relationship;
- constant enum, content type, language, and filter values;
- binding depth and count;
- chapter block variants;
- absence of executable constructs.

### Phase 3: Runtime-context validation

Before network access, validate:

- execution context was derived from installed package state;
- origin is present when required;
- input values match declared template parameters;
- runtime limits are positive and within host maxima;
- endpoint exists and matches the invoked host method.

### Phase 4: Bounded extraction

Request, parse, selection, and nested binding evaluation share one endpoint
budget. A nested field or item cannot reset counters.

### Phase 5: Typed mapping

Internal values map through endpoint-specific host code.

All conversion and constructor errors become typed failures.

### Phase 6: Shared output validation

Every selector and JavaScript result passes through
`PluginWireDtoValidator`.

## Error model

### Install-time errors

Stable codes include:

```text
UNSUPPORTED_SCHEMA_VERSION
UNKNOWN_ENDPOINT
INVALID_BINDING_TYPE
BLANK_SELECTOR
INVALID_BINDING_PATH
INVALID_CONSTANT
DUPLICATE_ENDPOINT
DUPLICATE_FIELD_BINDING
OUTPUT_TYPE_MISMATCH
EXCESSIVE_BINDING_DEPTH
EXCESSIVE_BINDING_COUNT
INVALID_DECLARATIVE_ORIGIN
```

### Selector runtime errors

```text
plugin.selector_origin_required
plugin.selector_operation_limit
plugin.selector_document_limit
plugin.selector_node_limit
plugin.selector_element_limit
plugin.selector_text_limit
plugin.selector_regex_input_limit
plugin.selector_output_limit
plugin.selector_timeout
plugin.selector_type_mismatch
plugin.selector_field_missing
plugin.selector_field_invalid
plugin.selector_field_duplicate
plugin.selector_output_conflict
plugin.selector_execution_failed
```

### Shared output errors

```text
plugin.output_invalid
plugin.output_limit
plugin.output_duplicate_id
plugin.output_invalid_url
plugin.output_undeclared_host
plugin.output_contract_mismatch
```

## Diagnostics

Diagnostics contain only safe machine tokens.

Supported keys:

```text
endpoint
binding_index
operation_index
item_index
field_path
output_type
error_reason
```

Field paths use deterministic dot notation:

```text
sections.1.items.4.title
items.3.image.declaredHost
releases.8.publishedAtEpochMillis
blocks.5.text.spans.1
```

Diagnostics never include:

- raw HTML;
- story or chapter text;
- titles;
- query values;
- authorization headers;
- cookies;
- raw cursors;
- JavaScript source.

## Budgets

Version 2 reuses existing limits and adds output limits.

General:

```text
maxOperations
maxDocumentCharacters
maxDocumentNodes
maxElements
maxTextValues
maxRegexInputCharacters
maxWallClockMillis
maxEndpoints
maxBindings
maxBindingDepth
maxBoundFields
maxOutputItems
```

Catalog:

```text
maxOutputSections
maxOutputItemsPerSection
maxTotalOutputItems
```

Content:

```text
maxReleaseItems
maxTombstoneIds
maxChapterBlocks
maxChapterTextCharacters
maxSpansPerBlock
maxTotalSpans
```

Host defaults are authoritative. A plugin may request a lower limit but cannot
raise a host maximum.

## Cancellation

Cancellation propagates through:

- gateway execution;
- response body handling;
- document traversal;
- nested list evaluation;
- field binding;
- DTO mapping;
- chapter block traversal;
- output validation.

Large loops check coroutine cancellation periodically.

Cancellation returns no partial DTO. Network/response resources are released.
The HTML parser is bounded by the pre-parse document-size limit and exposes no
persistent external resource after parsing.

## Shared wire DTO validation

Focused validators:

```text
CatalogWireDtoValidator
ContentWireDtoValidator
PageWireDtoValidator
RemoteReferenceValidator
ChapterDocumentValidator
```

They validate:

- required strings;
- collection limits;
- duplicate stable IDs;
- URL and declared-host consistency;
- score invariants;
- language tags;
- timestamps and normalized hints;
- sync conflicts;
- chapter blocks and spans.

## Migration policy

Version 1 remains supported without semantic changes.

Version 1 is not automatically converted to version 2.

The host does not zip independent version 1 outputs into objects.

Plugin authors explicitly declare version 2 endpoint bindings.

Migration tooling may provide examples and validation feedback, but the plugin
author confirms mappings.

## Implementation sequence

The work is split into independently reviewable commits.

### Commit 1 — Design

```text
docs: specify selector v2 output bindings
```

### Commit 2 — Envelope, origin, and binding core

```text
plugin-api: add selector v2 binding core
```

Includes:

- additive manifest `declarativeOrigin`;
- versioned envelope decoder;
- concrete serialized binding AST;
- core validation;
- serialization and compatibility tests.

### Commit 3 — Catalog contracts

```text
plugin-api: add catalog selector bindings
```

### Commit 4 — Content contracts

```text
plugin-api: add content selector bindings
```

### Commit 5 — Shared URL and output validation

```text
plugin-host: add shared plugin output validation
```

Includes:

- `PluginUrlPolicy` contract/implementation;
- gateway adoption of shared policy;
- common wire DTO validators;
- validation fixtures.

### Commit 6 — Typed binding evaluator

```text
plugin-host: evaluate typed selector bindings
```

Includes:

- `SelectorBoundValue`;
- nested evaluator;
- richer document adapter;
- global binding/output budgets;
- cancellation and field-path diagnostics.

### Commit 7 — Catalog mapper

```text
plugin-host: map selector output to catalog DTOs
```

### Commit 8 — Content mapper

```text
plugin-host: map selector output to content DTOs
```

### Commit 9 — Unified runtime integration

```text
plugin-host: expose selector v2 plugin endpoints
```

Includes:

- version 1 compatibility path;
- version 2 endpoint dispatch;
- shared validators;
- fixture selector plugin;
- parity tests with the JavaScript contract boundary where available.

Task 04 must reuse the same output validators rather than creating a separate
validation model.

## Test strategy

Every new behavior follows red-green-refactor.

Required test groups:

### Versioning and schema

- version 1 decode remains unchanged;
- version 2 round-trip serialization;
- unknown version rejection;
- invalid origin rejection;
- binding depth/count limits;
- unknown binding or endpoint rejection.

### Catalog

- home sections;
- paged cards and next tokens;
- details;
- all filter variants;
- missing required fields;
- optional/default values;
- duplicate IDs;
- nested field paths;
- invalid score, URL, host, language, and rank;
- output limits.

### Content

- paged story candidates;
- story details;
- latest and all chapter releases;
- sync delta;
- timestamp formats;
- normalized hints;
- all chapter block variants;
- DOM order;
- semantic spans;
- image host validation;
- sync conflicts;
- output limits.

### Security and resilience

- no raw payload in diagnostics;
- cross-host and unsupported-scheme rejection;
- redirects use shared URL policy;
- cancellation releases network resources;
- no partial DTO after cancellation;
- deterministic fixtures only.

## Acceptance criteria

Task 03 is complete only when:

1. Version 1 decodes and executes unchanged.
2. Unknown schema versions are rejected.
3. Every Catalog endpoint produces the current plugin-api wire DTO.
4. Every Content endpoint produces the current plugin-api wire DTO.
5. Required, optional, list, set, and default rules work.
6. Nested field paths identify the failing DTO field.
7. Origin, URL, and declared-host policies are enforced.
8. Operation, document, node, regex, binding, output, and wall-clock budgets
   are enforced.
9. Chapter block order is preserved.
10. Chapter spans satisfy DTO invariants.
11. Cancellation returns no partial output and releases network resources.
12. Diagnostics contain no raw page or chapter data.
13. Selector output passes through shared wire DTO validators.
14. Fixture selector and JavaScript plugins return the same contract DTOs at
    the Wave 04 checkpoint.
15. Plugin API and plugin host verification tasks pass.
16. The worktree is clean after the final commit.
