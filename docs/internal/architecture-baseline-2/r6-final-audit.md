# Architecture Baseline 2 R6 Final Audit

Date: 2026-08-10
Status: COMPLETE

## Source assertions

- The production graph is exactly `:app`, `:core:common`, `:catalog`,
  `:feature:catalog`, `:storage:room`, `:plugins:api`, and `:plugins:runtime`.
- No tracked source remains under `core/model`, `core/database`, `core/matching`,
  `core/plugin-api`, `core/plugin-host`, `core/network`, `feature/home`, or
  `feature/story`. Local `feature/home/build` and `feature/story/build` directories are
  ignored historical Gradle output, not modules or source.
- Production source contains no `OpenStoryAppGraph`, `LambdaViewModelFactory`, or
  `PluginRuntime.DECLARATIVE` reference.
- No bundled or installed source package contains `selector.json`. The remaining literal
  in `PackageArchiveInspector` and its tests is the fail-closed rule that rejects legacy
  selector archives; it is not an executable selector contract.
- `verifyArchitecture`, `verify-architecture-baseline-2.sh`, package-boundary checks, and
  structural hard policies pass against the final source tree.

The raw recursive grep shown in the R6 plan also scans negative regression fixtures and
the fail-closed archive validator. Those expected references were reviewed by role rather
than removed or obscured to make a textual assertion green.

## Public surface audit

| Module | Public declarations | Justification and allowed callers |
|---|---|---|
| `:app` | `MainActivity`, `OpenStoryApplication`, `OpenStoryApp`, `AppNavHost`, `AppNavigator`, route/destination types, Hilt modules and runtime entry point | Android framework and Hilt require entry points and composition declarations. Navigation/UI entry functions are consumed only inside `:app`; the module is the sole all-module composition root. |
| `:core:common` | `StoryId`, `PluginId`, `StableId`, `Outcome`, `Clock`, `SystemClock`, `FakeClock`, `AppDispatchers`, `FixedAppDispatchers` | Stable IDs, outcome transport, time, and dispatching are the only cross-capability primitives. Callers are limited by exact Gradle edges; no Android or feature model is owned here. |
| `:catalog` | Story/catalog/source models; `CatalogRepository`; `CatalogSource` and registry/result contracts; refresh, home, search, and details services/results; matching policy/result types; `StoryMatcher`; ranking types and `AggregateRanking` | These declarations form the catalog capability boundary. `:feature:catalog` consumes queries/services, `:storage:room` implements persistence contracts, `:app` composes them, and plugin adapters consume the runtime/API contracts. |
| `:feature:catalog` | Home/Search/Story ViewModels, UI-state/model/failure types, Compose screens/cards/filters, `StoryAssistedArgs`, and `HomeCoverRenderer` | `:app` navigation is the only cross-module caller. These declarations convert catalog application state into lifecycle-aware UI state and Compose output; they expose no Room or plugin type. |
| `:storage:room` | `OpenStoryDatabase`, `RoomCatalogRepository`, `RoomPluginStateStore`, and `RoomPluginDiagnosticsSink` | `:app` constructs the database and adapters. Each adapter keeps its DAO-based primary constructor internal and exposes a public database-based secondary constructor for cross-module composition. Room entities, DAOs, converters, and mappers remain internal. |
| `:plugins:api` | Manifest/capability/version models, package artifact and repository index, `PluginOperation`, protocol validator/violation, page transport, and all Catalog/Content protocol DTO families | This is the pure Kotlin wire/package contract. `:plugins:runtime` executes it and `:catalog` maps Catalog DTOs. It imports no Android, host model, storage, or runtime implementation. |
| `:plugins:runtime` | `PluginRuntime`, `PluginCallResult`, installed-plugin state; JavaScript engine/operation runner; bounded HTTP/HTML/log capability models; installer/verifier/storage/provisioning/update/rollback surfaces; `PluginStateStore` and `PluginDiagnosticsSink` persistence SPI | `:catalog` calls the runtime facade, `:app` composes concrete implementations, and `:storage:room` sees only the persistence SPI. Execution, capabilities, package lifecycle, budgets, and update decisions remain runtime-owned. |

Default-public declarations were retained only where framework creation or a declared
cross-module edge needs them. Storage records and DAOs, runtime parsing helpers, response
budget helpers, catalog conversion helpers, and app-specific MyAnimeList wiring are
`internal` or `private`.

## Ownership answers

1. **Model owner:** the owning capability defines its models; only stable cross-capability
   identity and outcome/time primitives belong to `:core:common`.
2. **Allowed caller:** callers are the consumers named in
   `config/architecture/module-boundaries.json`; `:app` is the composition root, not a
   bypass for feature or persistence logic.
3. **Persistence owner:** `:storage:room` stores catalog and plugin-runtime state behind
   capability ports. Entities and DAOs never cross the adapter boundary.
4. **Plugin execution owner:** `:plugins:runtime` owns JavaScript isolation, operation
   dispatch, bounded host capabilities, installation, update, rollback, and diagnostics.
5. **Canonical matching owner:** `:catalog` owns normalization, match policy, evidence,
   resolution, and aggregate ranking.
6. **Transaction owner:** `:storage:room` owns Room transactions. Runtime package storage
   separately owns atomic filesystem staging/activation; catalog mutations describe the
   semantic commit but do not control storage transactions.
7. **UI-state owner:** `:feature:catalog` converts catalog state/results into Home, Search,
   and Story UI state. `:app` owns navigation state only.

## Structural review

- No production Kotlin file exceeds 300 lines, so no size exception remains.
- `SourceDetails` has thirteen constructor properties. They are the single normalized
  source-details aggregate required for exact protocol-to-catalog mapping; splitting them
  would create partial ownership without reducing responsibility.
- `HomeScreen` contains the only reported function over 50 lines at 58 lines. It is a
  route-level lazy-list declaration that delegates cards, filters, headers, source
  switching, and failure rendering; it owns no repository call or business decision.
- Wide-import signals occur at framework/composition boundaries (`PluginRuntimeModule`,
  `AppNavHost`, `OpenStoryDatabase`, JavaScript engine), catalog orchestration services,
  and focused Compose screens. Each remains inside the exact package/module policy and
  has one named responsibility.
- No structural suppression, Detekt baseline, generic helper bucket, or mixed transaction,
  execution, persistence, and UI-state owner remains.

## Acceptance conclusion

Every retained model, caller, persistence operation, plugin execution path, matching
decision, transaction, and UI-state conversion has one explicit owner. The final source
matches the seven-module Baseline 2 design and has no unresolved mixed responsibility.
