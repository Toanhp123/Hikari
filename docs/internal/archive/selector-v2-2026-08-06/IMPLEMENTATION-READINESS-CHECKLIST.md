# Implementation Readiness Checklist

Do not start production implementation until every item below is checked.

## Repository and baseline

- [ ] Commit `05bd13e` is present in the target branch history.
- [ ] Worktree is clean.
- [ ] Reviewed design spec is committed separately.
- [ ] No later-wave feature work is mixed into the branch.

## Contract decisions

- [ ] Additive manifest `declarativeOrigin` shape is approved.
- [ ] V1/V2 decoder JSON strategy is written with concrete examples.
- [ ] Every binding has a stable `@SerialName`.
- [ ] Public serialized AST uses concrete non-generic models.
- [ ] Endpoint-to-DTO matrix covers all current Catalog and Content methods.
- [ ] Static-only Catalog filter rule is accepted.

## Shared policies

- [ ] `PluginUrlPolicy` API and module ownership are specified.
- [ ] Gateway migration to `PluginUrlPolicy` has regression tests.
- [ ] Output validators use the same URL policy without network requests.
- [ ] Language-tag validation reuse is identified.
- [ ] Diagnostic token rules accept all proposed field paths.

## Parser boundary

- [ ] `HtmlDocumentAdapter` V2 API is specified.
- [ ] Ordered DOM traversal is testable without exposing Jsoup types.
- [ ] Semantic emphasis/strong span offset algorithm is specified.
- [ ] Node/text counters are shared with endpoint budgets.

## Budgets and cancellation

- [ ] Default and maximum values are specified for every V2 limit.
- [ ] Nested bindings cannot reset global counters.
- [ ] Large loops have cancellation checkpoints.
- [ ] Network cancellation/resource release remains covered.
- [ ] No partial DTO can escape after cancellation.

## TDD and commits

- [ ] Each commit has one focused failing test before production code.
- [ ] Contract commits precede host evaluator/mappers.
- [ ] Shared validators precede mapper integration.
- [ ] Unified endpoint integration is a separate final commit.
- [ ] Every commit runs focused tests and the affected module suite.
- [ ] Final checkpoint runs clean checkout verification.

## Wave 04 exit

- [ ] Selector V1 remains compatible.
- [ ] All Catalog selector endpoints return wire DTOs.
- [ ] All Content selector endpoints return wire DTOs.
- [ ] JavaScript and selector fixtures pass the same validators.
- [ ] Diagnostics contain no fixture secrets or raw content.
- [ ] Wave 04 checkpoint is reviewed before Wave 05 begins.
