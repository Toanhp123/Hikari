# Self-Review Report

## Verdict

**Approved with incorporated revisions.**

The core direction—schema V2, endpoint-oriented typed bindings, host-owned
mapping, and shared wire DTO validation—is the correct solution to the gap
between the existing V1 `TEXT` pipeline and Wave 04's requirement to return
plugin wire DTOs.

The reviewed spec in this ZIP incorporates the required corrections below.

## Evidence from the approved planning baseline

The Wave 04 Task 03 plan requires:

- a bounded deterministic selector interpreter;
- operation, document-size, node-count, regex, and wall-clock budgets;
- typed field errors;
- safe cancellation;
- source-relative URL normalization;
- plugin wire DTO output.

The Wave 03 selector plan also requires schema additions to be versioned and
forbids silently reinterpreting existing operations.

The Wave 04 checkpoint requires selector and JavaScript fixture plugins to
return the same contract DTOs.

These constraints make a V2 output-binding contract necessary.

## Findings and corrections

### 1. Missing source for the declared plugin origin — corrected

Problem:

- V1 documentation permits relative URLs.
- Runtime execution needs a base origin.
- The current manifest exposes `allowedHosts` but not a declared origin.
- Selecting the first host from a `Set` would be nondeterministic and cannot
  represent a base path.

Correction in reviewed spec:

- add optional `declarativeOrigin` to the manifest;
- require it for declarative packages using relative request templates;
- validate HTTPS, host, user-info, query, and fragment rules;
- derive runtime context from installed package state.

Risk level before correction: **blocking**.

### 2. Output URL validation was coupled to a request gateway — corrected

Problem:

- `PluginHttpGateway` currently exposes request execution, not validation-only
  URL resolution.
- Output URLs must be validated without fetching.
- Reimplementing scheme/host logic in selector mappers would create drift.

Correction in reviewed spec:

- introduce shared `PluginUrlPolicy` in `core:network`;
- make the gateway and output validators use the same policy;
- return a typed validated URL without issuing a request.

Risk level before correction: **blocking security architecture gap**.

### 3. Chapter semantic spans need a richer parser adapter — corrected

Problem:

- The current adapter returns flattened text and attributes.
- `ChapterTextSpan` generation requires ordered text-node traversal and
  host-approved tag inspection.
- Generic text extraction cannot reconstruct correct offsets safely.

Correction in reviewed spec:

- extend `HtmlDocumentAdapter` for ordered child traversal and safe semantic
  text/span extraction;
- keep Jsoup isolated behind the adapter;
- count visited nodes and emitted characters against endpoint budgets.

Risk level before correction: **blocking for full Content DTO coverage**.

### 4. Original commit order put validators after mappers — corrected

Problem:

- Catalog and Content mappers depend on URL and final DTO validation.
- The original sequence placed shared validators after the mappers.
- It also lacked an explicit final endpoint-dispatch integration commit.

Correction in reviewed spec:

1. Design.
2. Envelope/origin/binding core.
3. Catalog contracts.
4. Content contracts.
5. Shared URL/output validation.
6. Evaluator.
7. Catalog mapper.
8. Content mapper.
9. Unified runtime integration.

Risk level before correction: **high integration risk**.

### 5. Generic serialized binding classes were underspecified — corrected

Problem:

- Generic Kotlin binding models can be awkward or ambiguous with
  `kotlinx.serialization`.
- DTO-specific fields need stable JSON discriminators and concrete models.

Correction:

- public package schema uses concrete non-generic serializable classes;
- generics are permitted only in internal host implementation.

Risk level: **medium API/tooling risk**.

### 6. Timestamp pattern naming was ambiguous — corrected

Problem:

- `DECLARED_HOST_PATTERN` could be interpreted as a plugin-supplied pattern,
  conflicting with the ban on arbitrary regex/pattern execution.

Correction:

- use `HOST_PATTERN_ID`, referencing a closed host-owned format table.

Risk level: **medium security/compatibility ambiguity**.

### 7. Cancellation guarantee needed qualification — corrected

Problem:

- Jsoup parsing is synchronous and may not cooperatively stop mid-parse.
- Claiming hard cancellation of arbitrary CPU parsing would be stronger than
  the implementation can guarantee.

Correction:

- enforce decoded-document character limits before parse;
- ensure no parser-owned external resource persists after parse;
- check cancellation during all large post-parse traversals;
- retain hard resource release guarantees for network/response handling.

Risk level: **medium correctness wording**.

## Scope review

Supporting every current Catalog and Content DTO is materially larger than the
original five-file Task 03 sketch. The scope is acceptable only when preserved
as separate reviewable commits and TDD cycles.

Do not implement all V2 contracts, evaluator, mappers, and validators in one
commit.

## Remaining risks for implementation planning

These are not unresolved design blockers, but implementation plans must address
them explicitly:

1. Binary/source compatibility of the additive manifest field.
2. JSON discriminator names and stable wire examples for every binding.
3. Maximum default values for each new budget.
4. Language-tag validator reuse location.
5. Exact output shape for sealed `CatalogFilterDefinition`.
6. Fixture package layout and schema entry-file handling for V1 versus V2.
7. Shared validator API that can accept both typed selector results and decoded
   JavaScript messages.
8. Complexity and performance of semantic chapter span extraction.
9. Full clean-checkout verification command and dependency metadata updates.

## Final recommendation

Proceed to a detailed implementation plan only after placing this reviewed spec
in the repository and committing it as a documentation-only change.

The first production commit must be the versioned envelope/origin/binding core,
not a mapper or evaluator.
