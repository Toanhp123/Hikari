# Canonical Catalog Reconciliation & Fusion — Phase 0 Checkpoint

Date: 2026-08-21
Status: **VERIFIED — PHASE 0 CLOSED**

## Scope

This checkpoint records the first four tasks of the Canonical Catalog Reconciliation & Fusion Engine implementation plan. It is a source/test/documentation checkpoint only: Room remains at schema 8, no canonical-generation persistence exists yet, reconciliation remains the legacy matcher, and no destructive Story merge path is enabled.

Normative design:
`../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`

Implementation plan:
`../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`

## Patched boundary

### Task 1 — opaque latest-update labels

- `CatalogLatestUpdateDto.releaseLabel` is documented as complete provider-formatted opaque presentation text.
- Discover no longer prepends `"Ch. "` to the provider label, eliminating the `"Ch. Ch. 56"` regression.
- Protocol and Compose characterization/regression tests are present.
- `docs/plugin-sdk/catalog-protocol.md` now documents listing/details optional-metadata behavior and latest-update label semantics.

### Task 2 — bounded external identifier facts

- `WireCatalogIdentifierScope` and `CatalogExternalIdentifierDto` exist with bounded namespace/value validation.
- Catalog listing/details payloads accept at most 32 external identifiers and remain backward-compatible through empty defaults.
- Host `ExternalIdentifierScope`, `ExternalIdentifier`, and shared `identity.SourceKey` contracts exist.
- `SourceItem`, `SourceDetails`, and `CatalogEntry` carry identifier facts.
- Home/Details/Search ingestion carries identifiers into legacy `CatalogMatchCandidate` / `CatalogMatchEvidence` without interpreting them.
- The legacy matcher intentionally ignores external-identifier semantics. Strong-identifier reasoning remains Phase 3 work.
- Schema-8 Room does **not** persist identifiers in this patch; durable identifier persistence remains Task 7 after the approved schema foundation.

### Task 3 — evidence normalization and independent fingerprints

- `CatalogEvidenceNormalizer.comparisonKey()` performs Unicode NFKC normalization, trim, whitespace collapse, and `Locale.ROOT` lowercase.
- Identity fingerprinting includes title, aliases, authors, content type, and external identifiers while excluding presentation-only changes such as cover, score, status, and latest update.
- Fusion fingerprinting includes presentation fields plus Summary/Full provenance and is independent of collection iteration order.
- Fingerprints are lowercase SHA-256 over deterministic length-delimited canonical encoding.
- `CatalogSourceRecord` carries source identity, Story ownership, raw `CatalogEntry`, Summary/Full provenance, and both fingerprints.

### Task 4 — legacy source-selection characterization

Characterization tests now explicitly lock the behavior that Phase 2 is expected to replace:

- Search selection currently loads Details from only the first search-source card.
- Story AUTO presentation and initial Full request currently follow alphabetical `(pluginId, sourceId)` ordering.
- Discover currently uses feature-local completeness ordering plus field-specific local selection for cover/genres/status/score/latest update.
- `CatalogStoryProjection` currently uses the first sorted catalog entry for title/cover.

Every characterization that encodes an undesired long-term provider-choice rule is marked:

```kotlin
// Characterization only: Phase 2 replaces this with CanonicalGeneration policy.
```

## Explicit non-claims

This checkpoint does **not** claim any of the following are implemented:

- Room schema 9 foundation;
- durable external-identifier persistence;
- canonical Story state or immutable canonical generations;
- primary-source hysteresis or user pinning;
- field-level canonical fusion;
- durable reconciliation cases/revisions;
- strong-identifier reconciliation semantics;
- retroactive reassessment after richer Details evidence;
- redirects, merge audit, atomic Story graph merge, or reversal;
- canonical read-path cutover for Story/Search/Discover/Library;
- event/background orchestration.

## Verification evidence

Phase 0 was verified on the developer checkout on 2026-08-21 after applying the implementation patch and the small Detekt cleanup for fingerprint hex encoding.

| Check | Result |
|---|---|
| `./gradlew :plugins:api:test --tests app.openstory.plugins.api.protocol.catalog.CatalogProtocolTest` | PASS — `BUILD SUCCESSFUL` |
| focused Catalog evidence/Search/projection tests | PASS — `BUILD SUCCESSFUL` |
| focused Discover semantics/projection + Story ViewModel tests | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :plugins:api:test :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :storage:room:testDebugUnitTest` | PASS — `BUILD SUCCESSFUL` |
| `./scripts/verify.sh` | PASS after replacing Detekt-rejected fingerprint hex magic literals with named constants |
| repository static architecture/package/source/UI/plugin-SDK gates inside `verify.sh` | PASS |
| Room schema stability | PASS — current architecture remains 14 production modules and Room schemas 1..8 |

The first full `verify.sh` run exposed exactly two new Detekt `MagicNumber` findings in `CatalogEvidenceFingerprints.kt` for the unsigned-byte mask and hexadecimal radix. They were replaced with named constants (`UNSIGNED_BYTE_MASK`, `HEX_RADIX`) without changing the encoded SHA-256 output. The focused fingerprint test and full repository gate were then reported green on the developer checkout.

The earlier offline-sandbox `kotlinc`, static-gate, schema-stability, and patch-replay checks remain supplemental evidence only; the developer Gradle/repository verification above is the acceptance evidence for this checkpoint.

### TDD process-evidence note

The prepared patch was applied to the developer checkout after tests and production changes had already been authored. Therefore the pre-fix runtime RED executions cannot be reconstructed honestly from this checkout and remain unchecked in the implementation plan. GREEN behavior and the full acceptance gate are verified. No RED execution is retroactively claimed.

## Decision

**Phase 0 Tasks 1–4 are accepted and closed.** The repository remains on Room schema 8, destructive reconciliation/merge remains disabled, and no Phase-1 canonical persistence is claimed. Task 5 — defining canonical domain/read contracts before Room persistence — is the next implementation boundary. Task 6 must remain the first schema-changing task and is the only approved point in this plan to consume `8 -> 9` if it lands before Wave 10.
