# Architecture Baseline 2 Test Migration Inventory

Date: 2026-08-09
Status: Normative migration intent for legacy Wave 01-05 suites

Tests move by invariant rather than by historical module ownership. A suite marked
`KEEP_UNTIL_REPLACED` remains active until the named replacement runtime or owner is green.

| Current test | Action | New owner / reason |
|---|---|---|
| `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt` | REWRITE | `:plugins:api`; new pure manifest |
| `plugins/api/src/test/kotlin/app/openstory/plugins/api/testing/MyAnimeListReferenceContractTest.kt` | REWRITE | `:plugins:api`; protocol contract suite |
| `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/execution/PluginOperationRunnerTest.kt` | REWRITE | `:plugins:runtime`; isolated operation runtime |
| `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/PackageVerifierTest.kt` | DELETE_WITH_OWNER | Selector runtime removed in R2 |
| `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt` | REWRITE | `:storage:room`; semantic catalog commit contract |
| `catalog/src/test/kotlin/app/openstory/catalog/matching/StoryMatcherTest.kt` | REWRITE | `:catalog`; pure matcher |
| `feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt` | REWRITE | `:catalog`; refresh service |
| `feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt` | REWRITE | `:catalog`; search service |
| `feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt` | REWRITE | R4 `:feature:catalog` ViewModel |
| `app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt` | REWRITE | community-style MAL reference contract |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/PackageVerifierTest.kt` | KEEP_UNTIL_REPLACED | preserve archive traversal and package-integrity denial through R2B replacement |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/install/ZipPackageArchiveInspectorLimitsTest.kt` | KEEP_UNTIL_REPLACED | preserve archive size/count/path budgets through R2B replacement |
| `core/network/src/test/kotlin/app/openstory/network/AllowlistedHttpGatewayTest.kt` | KEEP_UNTIL_REPLACED | preserve redirect and undeclared-host denial through runtime HTTP capability cutover |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/update/PluginRollbackServiceTest.kt` | KEEP_UNTIL_REPLACED | preserve atomic rollback until the new immutable registry lifecycle is green |
| `core/plugin-host/src/test/kotlin/app/openstory/plugin/host/diagnostics/PluginDiagnosticsRepositoryTest.kt` | KEEP_UNTIL_REPLACED | preserve secret redaction and per-plugin failure isolation through runtime diagnostics cutover |
