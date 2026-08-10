# Wave 04 Task 03 - Selector Schema 1 Runtime Continuation

Date: 2026-08-08
Status: **HISTORICAL / IMPLEMENTATION PRESENT**

Completion note: Task 03 is implemented and its focused/module verification contributes
to the accepted Wave 04 checkpoint recorded in
`../internal/checkpoints/wave-04-plugin-host-and-security.md`. The responsibility list
below is retained as the implementation record, not as current work.

This continuation starts from the pre-MVP Baseline 1 source tree. It does not restore
development-generation compatibility or the removed linear extraction pipeline.

## Implemented baseline

- Selector Schema 1 typed Catalog and Content contracts.
- Canonical `SelectorRequestPlan` for bounded document acquisition.
- Closed `SelectorBinding` definitions and install-time output-shape validation.
- `SelectorDocumentLoader` with network, document, node, operation, and time budgets.
- `HtmlDocumentAdapter` as the opaque DOM boundary.
- No legacy selector runtime exists.

## Wave 04 Task 03 responsibilities

Implement these responsibilities in dependency order:

1. shared validation-only `PluginUrlPolicy`;
2. endpoint-wide `SelectorEvaluationBudget`;
3. typed `SelectorBindingEvaluator`;
4. `CatalogSelectorMapper`;
5. `ContentSelectorMapper`;
6. shared `PluginWireDtoValidator`;
7. `SelectorCatalogPlugin`, `SelectorContentPlugin`, and `SelectorPluginFactory`;
8. cancellation, redaction, and deterministic fixture checkpoint.

Each production behavior follows focused RED/GREEN/refactor verification before the
affected module suite. Routine tests use deterministic local HTML and never fetch live
third-party sites.

## Runtime flow

```text
validated SelectorDefinition (schema 1)
  -> endpoint request plan
  -> SelectorDocumentLoader
  -> HtmlDocument
  -> SelectorBindingEvaluator
  -> Catalog/Content mapper
  -> PluginWireDtoValidator
  -> CatalogPlugin or ContentPlugin result
```

The request loader and binding evaluator have separate budgets. The endpoint-wide
evaluation budget must cover total traversal, produced values, chapter blocks, text,
and wall-clock work so nested bindings cannot multiply per-binding limits.

## Required boundaries

- `PluginUrlPolicy` validates schemes, hosts, redirects, and output URLs without
  performing network access.
- Only `PluginHttpGateway` performs plugin network I/O.
- Binding evaluation operates through opaque DOM handles; plugin-facing code never
  receives Jsoup types.
- URL bindings resolve relative URLs against the fetched document base URL and then
  use the same host policy as network requests.
- Catalog and Content mapping remain independent even when one package exposes both.
- Final DTO validation reuses the public plugin contract invariants.
- Diagnostics contain stable codes and bounded metadata only; never cookies, headers,
  raw HTML, chapter text, private URLs, or cursor values.
- Cancellation propagates through HTTP acquisition, DOM evaluation, mapping, and
  adapter dispatch.

## Endpoint coverage

Catalog coverage:

- home;
- search;
- details;
- filters.

Content coverage:

- search;
- story details;
- latest releases;
- all chapters;
- incremental sync;
- chapter document.

The historical selector fixture referenced by this implementation record was deleted
during Architecture Baseline 2 R5. Current contract fixtures live with `:plugins:api` and
describe only the JavaScript protocol/runtime.

## Checkpoint

Wave 04 Task 03 closes only when focused and module suites prove:

- all ten endpoint shapes map to their public wire DTOs;
- malformed, excessive, or type-mismatched output fails closed;
- request and evaluation budgets remain bounded across a complete endpoint;
- relative and absolute output URLs use the shared validation policy;
- cancellation stops active work;
- diagnostics are redacted and deterministic;
- the canonical fixture executes against deterministic HTML on JDK 17;
- no generation-labelled compatibility layer or removed runtime symbol returns.

## Non-goals

This task does not implement JavaScript execution, WebView authentication, Wave 05 UI,
story matching, chapter aggregation, or any package schema field that is absent from
the current contract.
