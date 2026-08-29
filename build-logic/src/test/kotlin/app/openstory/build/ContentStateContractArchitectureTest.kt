package app.openstory.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContentStateContractArchitectureTest {
    private val root = File("..").canonicalFile
    private val stateRoot = "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/state"

    private val migratedRoots = listOf(
        "downloads",
        "updates",
        "library",
        "dashboard",
        "chapters",
        "discover",
        "story",
        "search",
        "mapping",
        "review",
    ).map { "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/$it" }

    private val migratedViewModels = listOf(
        "downloads/DownloadsViewModel.kt",
        "updates/UpdatesViewModel.kt",
        "library/LibraryViewModel.kt",
        "dashboard/HomeDashboardViewModel.kt",
        "chapters/ChapterListViewModel.kt",
        "discover/DiscoverViewModel.kt",
        "story/StoryViewModel.kt",
        "search/SearchViewModel.kt",
        "mapping/MappingViewModel.kt",
        "review/ReconciliationReviewViewModel.kt",
    ).map { "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/$it" }

    private val migratedUiStates = listOf(
        "downloads/DownloadsUiState.kt",
        "updates/UpdatesUiState.kt",
        "library/LibraryUiState.kt",
        "dashboard/HomeDashboardUiState.kt",
        "chapters/ChapterListUiModel.kt",
        "discover/DiscoverUiState.kt",
        "story/StoryUiState.kt",
        "search/SearchUiState.kt",
        "mapping/MappingUiState.kt",
        "review/ReconciliationReviewUiState.kt",
    ).map { "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/$it" }

    @Test
    fun migratedCatalogOwnersContainNoLegacyRetainedHelpers() {
        val offenders = migratedProductionFiles().flatMap { file ->
            LEGACY_RETAINED_HELPER.findAll(file.readText())
                .map { match -> "${file.repositoryPath()} -> ${match.value.trim()}" }
                .toList()
        }

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun migratedReadinessOwnersContainNoIndependentLoadingAuthority() {
        val uiStateOffenders = migratedUiStates.filter { relative ->
            STORED_UI_LOADING_BOOLEAN.containsMatchIn(File(root, relative).readText())
        }
        val viewModelOffenders = migratedViewModels.filter { relative ->
            MUTABLE_LOADING_AUTHORITY.containsMatchIn(File(root, relative).readText())
        }
        val offenders = uiStateOffenders + viewModelOffenders

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun migratedReadinessOwnersRetainTheContentStateContract() {
        val offenders = (migratedViewModels + migratedUiStates).filterNot { relative ->
            CATALOG_CONTENT_STATE_IMPORT.containsMatchIn(File(root, relative).readText())
        }

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun migratedCatalogOwnersContainNoSyntheticObservationFallback() {
        val reviewedController =
            "feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryReconciliationController.kt"
        val offenders = migratedProductionFiles()
            .filterNot { it.repositoryPath() == reviewedController }
            .filter { containsFlowCatch(it.readText()) }
            .map { it.repositoryPath() }
            .toMutableList()
        val controllerCatches = flowCatchBodies(File(root, reviewedController).readText())
        val reviewedFallbackIsExact = controllerCatches.singleOrNull()?.let { body ->
            OPERATION_FAILURE_UPDATE.containsMatchIn(body) && NULL_EMISSION.containsMatchIn(body)
        } == true
        if (!reviewedFallbackIsExact) offenders += "$reviewedController -> reviewed fallback changed"

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun cscFoundationRemainsUniqueFeatureLocalAndDomainNeutral() {
        val expected = mapOf(
            "ContentState" to ApprovedDeclaration(
                "$stateRoot/ContentState.kt",
                Regex("""\bsealed\s+interface\s+ContentState\b"""),
            ),
            "CatalogUiFailure" to ApprovedDeclaration(
                "$stateRoot/CatalogUiFailure.kt",
                Regex("""\bdata\s+class\s+CatalogUiFailure\b"""),
            ),
            "RefreshState" to ApprovedDeclaration(
                "$stateRoot/RefreshState.kt",
                Regex("""\bdata\s+class\s+RefreshState\b"""),
            ),
            "RetainedObservation" to ApprovedDeclaration(
                "$stateRoot/RetainedObservation.kt",
                Regex("""\binternal\s+class\s+RetainedObservation\b"""),
            ),
        )
        val missing = expected.values.map(ApprovedDeclaration::path).filterNot { File(root, it).isFile }
        val productionFiles = productionKotlinFiles(root).toList()
        val invalidDeclarations = expected.flatMap { (type, approved) ->
            val declaration = declarationNamed(type)
            val locations = productionFiles
                .filter { CATALOG_STATE_PACKAGE.containsMatchIn(it.readText().withoutNonCode()) }
                .filter { declaration.containsMatchIn(it.readText().withoutNonCode()) }
                .map { it.repositoryPath() }
                .toList()
            buildList {
                if (File(root, approved.path).isFile &&
                    !approved.shape.containsMatchIn(File(root, approved.path).readText().withoutNonCode())
                ) {
                    add("${approved.path} -> invalid $type declaration")
                }
                if (locations != listOf(approved.path)) {
                    add("$type -> ${locations.joinToString().ifEmpty { "missing" }}")
                }
            }
        }
        val invalidImports = expected.values.map(ApprovedDeclaration::path).flatMap { relative ->
            val file = File(root, relative)
            val imports = if (file.isFile) IMPORT.findAll(file.readText()).map { it.groupValues[1] } else emptySequence()
            imports.filterNot { it.startsWith("kotlin.") || it.startsWith("kotlinx.coroutines.") }
                .map { "$relative -> $it" }
        }

        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
        assertTrue(invalidDeclarations.isEmpty(), invalidDeclarations.joinToString("\n"))
        assertTrue(invalidImports.isEmpty(), invalidImports.joinToString("\n"))
    }

    @Test
    fun designSystemAndReaderDoNotAdoptCatalogCsc() {
        val excludedRoots = listOf(
            "core/designsystem/src/main",
            "feature/reader/src/main",
            "reader/src/main",
            "reader/engine/src/main",
        )
        val offenders = excludedRoots
            .flatMap { productionKotlinFiles(File(root, it)).toList() }
            .filter { CATALOG_STATE_REFERENCE.containsMatchIn(it.readText()) }
            .map { it.repositoryPath() }

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    private fun migratedProductionFiles(): List<File> = migratedRoots
        .flatMap { productionKotlinFiles(File(root, it)).toList() }

    private fun productionKotlinFiles(directory: File): Sequence<File> = directory
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { "${File.separator}build${File.separator}" in it.path }
        .filterNot { "${File.separator}src${File.separator}test${File.separator}" in it.path }
        .filterNot { "${File.separator}src${File.separator}androidTest${File.separator}" in it.path }

    private fun File.repositoryPath(): String = relativeTo(root).invariantSeparatorsPath

    private fun flowCatchBodies(source: String): List<String> {
        val executable = source.executableSource()
        return FLOW_CATCH_CALL.findAll(executable).mapNotNull { match ->
            val openingBrace = executable.indexOf('{', match.range.last + 1)
            openingBrace.takeIf { it >= 0 }?.let { executable.balancedBlock(it) }
        }.toList()
    }

    private fun containsFlowCatch(source: String): Boolean =
        FLOW_CATCH_CALL.containsMatchIn(source.executableSource())

    private fun String.executableSource(): String = withoutNonCode()
        .lineSequence()
        .filterNot { line -> line.trimStart().startsWith("import ") }
        .joinToString("\n")

    private fun String.balancedBlock(openingBrace: Int): String? {
        var depth = 0
        for (index in openingBrace..lastIndex) {
            when (this[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return substring(openingBrace + 1, index)
                }
            }
        }
        return null
    }

    private fun String.withoutNonCode(): String =
        TRIPLE_QUOTED_STRING.replace(this, "")
            .let { QUOTED_STRING.replace(it, "") }
            .let { CHARACTER_LITERAL.replace(it, "") }
            .let { BLOCK_COMMENT.replace(it, "") }
            .let { LINE_COMMENT.replace(it, "") }

    private fun declarationNamed(type: String): Regex = Regex(
        """\b(?:typealias|object|(?:enum\s+)?class|interface)\s+$type\b""",
    )

    private data class ApprovedDeclaration(
        val path: String,
        val shape: Regex,
    )

    private companion object {
        val IMPORT = Regex("""^import\s+([^\s]+)""", RegexOption.MULTILINE)
        val CATALOG_CONTENT_STATE_IMPORT = Regex(
            """^import\s+app\.openstory\.catalog\.ui\.state\.ContentState\s*$""",
            RegexOption.MULTILINE,
        )
        val CATALOG_STATE_PACKAGE = Regex(
            """^package\s+app\.openstory\.catalog\.ui\.state\s*$""",
            RegexOption.MULTILINE,
        )
        val CATALOG_STATE_REFERENCE = Regex("""app\.openstory\.catalog\.ui\.state(?:\.|\b)""")
        val LEGACY_RETAINED_HELPER = Regex("""\bpreserveLatest\w*\s*\(""")
        val STORED_UI_LOADING_BOOLEAN = Regex(
            """\b(?:val|var)\s+\w*[Ll]oading\w*\s*:\s*Boolean\b(?!\s*get\s*\()""",
        )
        val MUTABLE_LOADING_AUTHORITY = Regex(
            """\b(?:val|var)\s+\w*[Ll]oading\w*\s*(?:(?::\s*(?:(?:Mutable)?StateFlow\s*<\s*Boolean\s*>|Boolean))?\s*(?:=|by)\s*(?:(?:MutableStateFlow|mutableStateOf)(?:\s*<\s*Boolean\s*>)?\s*\(|(?:true|false)\b))""",
        )
        val FLOW_CATCH_CALL = Regex("""\.catch\b""")
        val OPERATION_FAILURE_UPDATE = Regex("""operation\.update\s*\{""")
        val NULL_EMISSION = Regex("""emit\s*\(\s*null\s*\)""")
        val TRIPLE_QUOTED_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val QUOTED_STRING = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
        val CHARACTER_LITERAL = Regex("'(?:\\\\.|[^'\\\\])'")
        val BLOCK_COMMENT = Regex("/\\*[\\s\\S]*?\\*/")
        val LINE_COMMENT = Regex("//[^\\r\\n]*")
    }
}
