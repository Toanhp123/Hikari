# Wave 03 Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze a complete, versioned plugin contract boundary, including Selector V2 wire bindings for every Catalog and Content endpoint, before Wave 04 executes plugin definitions.

**Architecture:** Preserve Selector V1 unchanged. Add a version-aware V2 envelope, a closed non-executable binding AST, and endpoint-specific Catalog/Content output declarations in `:core:plugin-api`. Validate decoded V1/V2 selector definitions in the package inspector before installation, harden manifest/package/repository security fields, and close the wave with deterministic JVM contract gates; runtime evaluation remains in `:core:plugin-host`.

**Tech Stack:** Kotlin, kotlinx.serialization, Kotlin/JVM tests, Gradle test fixtures, ZIP/package metadata, Ed25519 metadata.

## Global Constraints

- Wave 02 is the approved baseline.
- Selector V1 JSON and semantics remain unchanged.
- Selector V2 is declarative and non-Turing-complete.
- No reflection, class names, callbacks, regex source, filesystem, Android API, network execution, or JavaScript is added to the contract AST.
- Every Catalog and Content plugin method has a corresponding endpoint declaration.
- Relative request URLs require one explicit HTTPS `declarativeOrigin` whose host is in `allowedHosts`.
- Install-time validation is bounded to 12 levels and 512 bindings.
- Unknown schema versions and unknown polymorphic variants fail closed.
- Package/repository security fields are validated; unknown optional repository fields still round-trip.
- Wave 03 checkpoint is JVM-only and deterministic.

---

### Task 1: Manifest and wire DTO invariants

**Files:**
- Modify: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginApiVersion.kt`
- Modify: `core/plugin-api/src/main/kotlin/app/openstory/plugin/api/PluginManifest.kt`
- Modify: Catalog/content DTO files and tests.

- [x] Write focused failing tests for API version bounds, kind/capability/host consistency, declarative origin, stable IDs, filters, sync overlaps, and chapter spans.
- [x] Run focused tests and confirm expected failures.
- [x] Implement minimal invariants without changing serialized field names.
- [ ] Run `:core:plugin-api:test` on JDK 17 (pending target checkout/CI).

### Task 2: Selector V2 envelope and binding core

**Files:**
- Create: `SelectorDefinitionDecoder.kt`, `SelectorPluginDefinitionV2.kt`, `SelectorBinding.kt`, `SelectorRequestPlan.kt`.
- Modify: `SelectorValidation.kt` and selector tests.

- [x] Write failing versioning/round-trip/bounds tests.
- [x] Preserve V1 decoder/encoding behavior.
- [x] Implement the closed binding AST and request-limit DTOs.
- [x] Implement bounded core validation.
- [ ] Run focused and module Gradle tests on JDK 17 (pending target checkout/CI).

### Task 3: Catalog endpoint contracts

**Files:**
- Create: `selector/catalog/CatalogSelectorDefinition.kt` and `CatalogSelectorValidation.kt` plus tests.

- [x] Write failing field-shape and filter tests.
- [x] Implement home/search/details/filters endpoint DTOs.
- [x] Validate required/optional fields and binding types.
- [x] Add complete encode/decode fixture coverage.

### Task 4: Content endpoint contracts

**Files:**
- Create: `selector/content/ContentSelectorDefinition.kt` and `ContentSelectorValidation.kt` plus tests.

- [x] Write failing release/chapter/sync field-shape tests.
- [x] Implement search/story/latest/allChapters/sync/chapter DTOs.
- [x] Add closed chapter-block variants and span modes.
- [x] Add complete encode/decode fixture coverage.

### Task 5: Package and repository hardening

**Files:**
- Modify package format production/tests.

- [x] Write failing Windows traversal, invalid size/overflow, entry-runtime mismatch, Ed25519 length, invalid provenance, HTTPS/security-field, and rollback tests.
- [x] Implement fail-closed validation with stable error enums.
- [x] Preserve unknown optional repository fields.

### Task 6: Deterministic fixtures and checkpoint

**Files:**
- Create Selector V2 sample JSON and contract tests.
- Create `scripts/verify-wave-03-checkpoint.sh`, contract test, CI job, and checkpoint evidence.
- Modify `core/plugin-host/.../ZipPackageArchiveInspector.kt` and `PackageVerifier.kt`; add a pre-install selector validation test.

- [x] Validate one complete Catalog+Content fixture through decode, validation, encode, decode, equality.
- [x] Add JVM checkpoint commands for `:core:plugin-api:test`, `:core:plugin-host:test`, and `:test:fixtures:test`.
- [x] Add CI dependency and documentation.
- [x] Run static checks and full verification where the environment permits.
