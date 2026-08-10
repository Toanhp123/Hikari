# Architecture Baseline 2 R5 Ownership Audit

Date: 2026-08-10
Status: ACCEPTED

## Final module ownership

| Module | Owned models and state | Public interfaces | Allowed callers |
|---|---|---|---|
| `:app` | Navigation routes/back stack and Android bootstrap configuration | `OpenStoryApp`, `AppNavHost`, `AppNavigator`, Hilt bindings | Android framework entry points only; it is the composition root and may bind every production module |
| `:core:common` | `StoryId`, `PluginId`, `Outcome`, clocks, and the narrow dispatcher abstraction | Stable IDs, `Outcome`, `Clock`, `AppDispatchers` | Any production module when the primitive is genuinely cross-capability |
| `:catalog` | Story/catalog/source models, match evidence, ranking, refresh/search/details results and mutation commands | `CatalogRepository`, `CatalogSource`, `CatalogSourceRegistry`, catalog services and policies | `:feature:catalog`; `:storage:room` implements persistence ports; `:app` binds concrete services |
| `:feature:catalog` | Home/Search/Story UI state, transient failures, recent searches, selection state, and Compose presentation | Screens, ViewModels, `StoryAssistedArgs`, cover rendering seam | `:app` navigation/composition only |
| `:storage:room` | Database schema 1, private entities, DAOs, converters, and Room-backed adapter state | `OpenStoryDatabase` plus catalog/runtime persistence adapter implementations | `:app` composition; it implements `:catalog` and runtime persistence contracts and exposes no DAO/entity |
| `:plugins:api` | Manifest, protocol/version, catalog/content wire DTOs, package artifact, and repository index models | Strict serializers and `PluginProtocolValidator` | `:plugins:runtime` and `:catalog`; storage sees protocol types only when carried by the runtime persistence SPI |
| `:plugins:runtime` | Installed plugin/runtime results, execution limits, capability requests, package lifecycle, and persisted runtime-state contracts | `PluginRuntime`, `PluginCallResult`, installer/update surfaces, and `persistence` SPI | `:catalog` uses the runtime facade; `:app` composes implementations; `:storage:room` imports only `persistence` |

## Exclusive owners

- Persistence owner: `:storage:room`. No Room entity or DAO crosses its module boundary.
- Plugin execution owner: `:plugins:runtime`. JavaScript, HTTP/HTML/log capabilities,
  package installation, update, rollback, and execution budgets remain inside this module.
- Transaction owner: `:storage:room` owns Room transactions; `:plugins:runtime` owns
  atomic package-directory staging/activation. Catalog mutation types describe semantic
  commits but do not execute storage transactions.
- UI-state owner: `:feature:catalog`. `:app` owns navigation state, not Home/Search/Story state.
- Wire-contract owner: `:plugins:api`. Runtime and catalog adapters consume the tested DTOs.

No responsibility is shared by convenience.

## Structural review

- No production Kotlin file exceeds 300 lines; the 500-line hard gate has no exception.
- No production structural suppression or Detekt baseline remains.
- No `Utils`, `Helpers`, `Misc`, `Part1`, or `Part2` production bucket exists.
- `CatalogRefreshService.commit` mixed matching, mutation assembly, and persistence error
  containment. R5 split those responsibilities into `resolveEntries`, section mapping,
  and `commitMutation`; Detekt is clean afterward.
- `HomeScreen` is the remaining function-length review signal. It is a route-level Compose
  list declaration whose branches only place already-separated header, switcher, row, and
  failure components; it owns no data access or business decisions, so it remains cohesive.
- `SourceDetails` has thirteen fields because it is the complete normalized source-details
  aggregate. Splitting it would create partial detail ownership and weaken protocol mapping.
- Wide imports in Hilt modules, `AppNavHost`, Compose screens, the JavaScript engine,
  catalog orchestration, and `OpenStoryDatabase` correspond to composition/framework
  boundaries. Their dependencies stay within the exact module/package rules and do not
  expose a generic coordinator abstraction.
- `PluginRequestPolicy` groups one identity/allowlist pair and six bounded HTTP budgets;
  the values change and validate as one security policy.

## Final direction audit

- `:feature:catalog -> :catalog`; no feature-to-storage/runtime edge.
- `:catalog -> :plugins:runtime -> :plugins:api`.
- `:storage:room -> :catalog` and `:plugins:runtime.persistence` only at source level.
- `:plugins:api` remains pure Kotlin/JVM and imports no host model.
- `:app` is the only all-module composition root.

The exact seven-module policy and package checks enforce these directions.
