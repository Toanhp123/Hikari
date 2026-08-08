package app.openstory.plugin.host.diagnostics

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.model.PluginId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.host.DefaultPluginHost
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHostSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PluginDiagnosticsRepositoryTest {
    @Test
    fun diagnosticRedactsSensitiveDetails() {
        val diagnostic = PluginDiagnostic.fromFailure(
            pluginId = "fixture",
            operation = "chapter",
            code = "network.http_401",
            unsafeDetail = "Cookie: token=secret https://a.example/x?q=private <html>body</html>",
        )

        val serialized = diagnostic.toString()

        assertFalse("secret" in serialized)
        assertFalse("q=private" in serialized)
        assertFalse("<html>" in serialized)
    }

    @Test
    fun historyIsCappedPerPluginAndGlobally() = runTest {
        val repository = PluginDiagnosticsRepository(
            store = InMemoryDiagnosticStore(),
            clock = Clock { 10L },
            perPluginLimit = 2,
            globalLimit = 3,
            degradedAfterConsecutiveFailures = 2,
        )

        repository.record(failure("plugin-a", 1L))
        repository.record(failure("plugin-a", 2L))
        repository.record(failure("plugin-a", 3L))

        assertEquals(
            listOf(3L, 2L),
            repository.recent("plugin-a").map(PluginDiagnostic::recordedAtEpochMillis),
        )

        repository.record(failure("plugin-b", 4L))
        repository.record(failure("plugin-b", 5L))

        assertEquals(
            listOf(5L, 4L, 3L),
            repository.recent().map(PluginDiagnostic::recordedAtEpochMillis),
        )
        assertEquals(
            listOf(3L),
            repository.recent("plugin-a").map(PluginDiagnostic::recordedAtEpochMillis),
        )
    }

    @Test
    fun repeatedFailuresDegradeHealthWithoutDisablingPlugin() = runTest {
        val repository = PluginDiagnosticsRepository(
            store = InMemoryDiagnosticStore(),
            clock = Clock { 10L },
            degradedAfterConsecutiveFailures = 3,
        )

        repeat(3) { index ->
            repository.record(failure("plugin-a", index.toLong()))
        }

        assertEquals(PluginHealthState.DEGRADED, repository.health("plugin-a"))
        assertEquals(
            PluginHealthState.DISABLED_BY_USER,
            repository.health("plugin-a", disabledByUser = true),
        )

        repository.record(
            PluginDiagnostic.success(
                pluginId = "plugin-a",
                version = "1.0.0",
                operation = "sync",
                durationMillis = 8L,
                recordedAtEpochMillis = 4L,
            ),
        )

        assertEquals(PluginHealthState.HEALTHY, repository.health("plugin-a"))
    }

    @Test
    fun batchHostCallsSkipOnlyFailingPluginAndRecordDiagnostic() = runTest {
        val diagnostics = PluginDiagnosticsRepository(
            store = InMemoryDiagnosticStore(),
            clock = Clock { 42L },
        )
        val healthy: HostedPlugin<CatalogPlugin> = HostedPlugin(
            id = PluginId("healthy.plugin"),
            version = "1.0.0",
            instance = FixtureCatalogPlugin,
        )
        val host = DefaultPluginHost(
            source = object : PluginHostSource {
                override suspend fun catalog(
                    id: PluginId,
                ): AppResult<HostedPlugin<CatalogPlugin>> =
                    when (id.value) {
                        healthy.id.value -> AppResult.Success(healthy)
                        "broken.plugin" -> AppResult.Failure(
                            AppError.Plugin(
                                code = "plugin.runtime_load_failed",
                                retryable = false,
                            ),
                        )
                        else -> error("Cookie: token=secret")
                    }

                override suspend fun content(id: PluginId) =
                    error("Content runtimes are not used by this fixture.")

                override suspend fun enabledCatalogIds(): List<PluginId> =
                    listOf(
                        healthy.id,
                        PluginId("broken.plugin"),
                        PluginId("throwing.plugin"),
                    )

                override suspend fun enabledContentIds(): List<PluginId> = emptyList()
            },
            diagnostics = diagnostics,
        )

        val plugins = host.enabledCatalogs()

        assertEquals(listOf(healthy), plugins)
        assertEquals(
            setOf("broken.plugin", "throwing.plugin"),
            diagnostics.recent().map(PluginDiagnostic::pluginId).toSet(),
        )
        assertEquals(
            setOf("plugin.runtime_load_failed"),
            diagnostics.recent().mapNotNull(PluginDiagnostic::errorCode).toSet(),
        )
        assertFalse("secret" in diagnostics.recent().toString())
    }

    @Test
    fun batchIsolationSurvivesDiagnosticStorageFailure() = runTest {
        val host = DefaultPluginHost(
            source = failingCatalogSource(),
            diagnostics = PluginDiagnosticsRepository(
                store = object : PluginDiagnosticStore {
                    override suspend fun record(
                        diagnostic: PluginDiagnostic,
                        perPluginLimit: Int,
                        globalLimit: Int,
                    ) = error("database unavailable")

                    override suspend fun recent(limit: Int): List<PluginDiagnostic> = emptyList()

                    override suspend fun recent(
                        pluginId: String,
                        limit: Int,
                    ): List<PluginDiagnostic> = emptyList()
                },
            ),
        )

        assertEquals(emptyList(), host.enabledCatalogs())
    }
}

private fun failingCatalogSource(): PluginHostSource = object : PluginHostSource {
    override suspend fun catalog(
        id: PluginId,
    ): AppResult<HostedPlugin<CatalogPlugin>> = AppResult.Failure(
        AppError.Plugin(
            code = "plugin.runtime_load_failed",
            retryable = false,
        ),
    )

    override suspend fun content(id: PluginId) =
        error("Content runtimes are not used by this fixture.")

    override suspend fun enabledCatalogIds(): List<PluginId> =
        listOf(PluginId("broken.plugin"))

    override suspend fun enabledContentIds(): List<PluginId> = emptyList()
}

private class InMemoryDiagnosticStore : PluginDiagnosticStore {
    private val diagnostics = mutableListOf<PluginDiagnostic>()

    override suspend fun record(
        diagnostic: PluginDiagnostic,
        perPluginLimit: Int,
        globalLimit: Int,
    ) {
        diagnostics += diagnostic
        diagnostics
            .filter { it.pluginId == diagnostic.pluginId }
            .sortedByDescending(PluginDiagnostic::recordedAtEpochMillis)
            .drop(perPluginLimit)
            .forEach(diagnostics::remove)
        diagnostics
            .sortedByDescending(PluginDiagnostic::recordedAtEpochMillis)
            .drop(globalLimit)
            .forEach(diagnostics::remove)
    }

    override suspend fun recent(limit: Int): List<PluginDiagnostic> =
        diagnostics.sortedByDescending(PluginDiagnostic::recordedAtEpochMillis).take(limit)

    override suspend fun recent(
        pluginId: String,
        limit: Int,
    ): List<PluginDiagnostic> = diagnostics
        .filter { it.pluginId == pluginId }
        .sortedByDescending(PluginDiagnostic::recordedAtEpochMillis)
        .take(limit)
}

private fun failure(
    pluginId: String,
    recordedAtEpochMillis: Long,
): PluginDiagnostic = PluginDiagnostic.fromFailure(
    pluginId = pluginId,
    version = "1.0.0",
    operation = "sync",
    code = "network.timeout",
    durationMillis = 20L,
    recordedAtEpochMillis = recordedAtEpochMillis,
)

private object FixtureCatalogPlugin : CatalogPlugin {
    override suspend fun home(request: CatalogHomeRequest): AppResult<List<CatalogSection>> =
        error("Not used by this fixture.")

    override suspend fun search(request: CatalogSearchRequest): AppResult<Page<CatalogCard>> =
        error("Not used by this fixture.")

    override suspend fun details(sourceId: String): AppResult<CatalogDetails> =
        error("Not used by this fixture.")

    override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> =
        error("Not used by this fixture.")
}
