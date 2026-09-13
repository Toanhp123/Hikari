package app.openstory.build.architecture

import java.io.File

object Step2BuildSurfaceVerifier {
    fun verify(
        rootDirectory: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        addAll(graphViolations(policy))
        addAll(buildDependencyViolations(rootDirectory, policy))
        addAll(sourceViolations(rootDirectory, policy))
        addAll(androidLibraryVariantViolations(rootDirectory))
        addAll(CatalogVariantSurfaceVerifier.verify(rootDirectory))
    }.distinct().sorted()

    private fun graphViolations(
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        val actualModules = policy.modules.keys
        if (actualModules != EXPECTED_GRAPH.keys) {
            add(
                ArchitectureViolation(
                    code = "step2_surface.module_set",
                    module = null,
                    detail =
                        "expected=${EXPECTED_GRAPH.keys.sorted().joinToString(",")};" +
                            "actual=${actualModules.sorted().joinToString(",")}",
                ),
            )
        }

        (actualModules intersect EXPECTED_GRAPH.keys).sorted().forEach { module ->
            val expected = EXPECTED_GRAPH.getValue(module)
            val rule = policy.modules.getValue(module)
            if (rule.productionDependencies != expected.production) {
                add(
                    ArchitectureViolation(
                        code = "step2_surface.production_graph",
                        module = module,
                        detail =
                            "$module expected=${expected.production.sorted().joinToString(",")} " +
                                "actual=${rule.productionDependencies.sorted().joinToString(",")}",
                    ),
                )
            }
            if (rule.testDependencies != expected.test) {
                add(
                    ArchitectureViolation(
                        code = "step2_surface.test_graph",
                        module = module,
                        detail =
                            "$module expected=${expected.test.sorted().joinToString(",")} " +
                                "actual=${rule.testDependencies.sorted().joinToString(",")}",
                    ),
                )
            }
        }
    }

    private fun buildDependencyViolations(
        root: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        policy.modules.toSortedMap().forEach { (module, rule) ->
            val buildFile = File(root, "${rule.path}/build.gradle.kts")
            if (!buildFile.isFile) return@forEach
            val text = buildFile.readText()

            if (ROOM_TOKEN.containsMatchIn(text) && module != ":catalog:storage") {
                add(violation("step2_surface.room_owner", module, buildFile))
            }
            if (COIL_TOKEN.containsMatchIn(text) && module != ":feature:catalog") {
                add(violation("step2_surface.coil_owner", module, buildFile))
            }
            if (COIL_NETWORK_TOKEN.containsMatchIn(text)) {
                add(violation("step2_surface.coil_network_forbidden", module, buildFile))
            }
            if (HTTP_DEPENDENCY_TOKEN.containsMatchIn(text)) {
                add(violation("step2_surface.http_dependency", module, buildFile))
            }
            if (ROOM_CONVENTION_TOKEN.containsMatchIn(text)) {
                add(violation("step2_surface.room_convention_forbidden", module, buildFile))
            }
            if (module == ":core:designsystem" &&
                DESIGN_SYSTEM_FORBIDDEN_DEPENDENCY_TOKEN.containsMatchIn(text)
            ) {
                add(violation("step2_surface.designsystem_dependency", module, buildFile))
            }
            addAll(pluginHarnessDependencyViolations(module, buildFile, text))
        }
    }

    private fun pluginHarnessDependencyViolations(
        module: String,
        buildFile: File,
        text: String,
    ): List<ArchitectureViolation> = buildList {
        PLUGIN_API_DEPENDENCY.findAll(text)
            .filterNot { match -> match.isAllowedAndroidTestDependency(module) }
            .forEach { add(violation("step2_surface.plugin_api_scope", module, buildFile)) }
        JAVASCRIPT_ENGINE_DEPENDENCY.findAll(text)
            .filterNot { match -> match.isAllowedAndroidTestDependency(module) }
            .forEach { add(violation("step2_surface.javascriptengine_scope", module, buildFile)) }
        if (module == ":feature:catalog" &&
            REQUIRED_PLUGIN_HARNESS_DEPENDENCIES.any { dependency ->
                dependency.findAll(text).count() != 1
            }
        ) {
            add(
                violation(
                    "step2_surface.plugin_harness_dependency_missing",
                    module,
                    buildFile,
                ),
            )
        }
    }

    private fun MatchResult.isAllowedAndroidTestDependency(module: String): Boolean =
        module == ":feature:catalog" && groupValues[1] == ANDROID_TEST_IMPLEMENTATION

    private fun sourceViolations(
        root: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        val productionModules = policy.modules.filterKeys { module ->
            module == ":app" || module in STEP2_MODULES
        }
        productionModules.toSortedMap().forEach { (module, rule) ->
            val moduleRoot = File(root, rule.path)
            if (!moduleRoot.isDirectory) return@forEach
            moduleRoot.walkTopDown()
                .filter(File::isFile)
                .filter { file -> PRODUCTION_SOURCE_PATH.containsMatchIn(file.invariantSeparatorsPath) }
                .forEach { file ->
                    val relativePath = file.relativeTo(root).invariantSeparatorsPath

                    if (RELEASE_FIXTURE_NAME.containsMatchIn(file.nameWithoutExtension)) {
                        add(
                            ArchitectureViolation(
                                code = "step2_surface.release_fixture",
                                module = module,
                                detail = relativePath,
                            ),
                        )
                    }
                    if (file.extension.lowercase() !in SOURCE_EXTENSIONS) {
                        return@forEach
                    }
                    val text = file.readText()
                    if (module == ":feature:catalog" &&
                        REMOTE_TRANSPORT_IMPLEMENTATION.containsMatchIn(text)
                    ) {
                        add(
                            ArchitectureViolation(
                                code = "step2_surface.remote_transport_implementation_forbidden",
                                module = module,
                                detail = relativePath,
                            ),
                        )
                    }
                    HTTP_IMPORT.findAll(text).forEach { match ->
                        add(
                            ArchitectureViolation(
                                code = "step2_surface.http_import",
                                module = module,
                                detail = "$relativePath:${match.groupValues[1]}",
                            ),
                        )
                    }
                    COIL_IMPORT.findAll(text).forEach { match ->
                        if (!relativePath.startsWith("feature/catalog/src/main/") ||
                            ".assets." !in packageName(text).orEmpty()
                        ) {
                            add(
                                ArchitectureViolation(
                                    code = "step2_surface.coil_import_owner",
                                    module = module,
                                    detail = "$relativePath:${match.groupValues[1]}",
                                ),
                            )
                        }
                    }
                    if (module == ":app") {
                        CATALOG_IMPORT.findAll(text).forEach { match ->
                            val imported = match.groupValues[1]
                            if (imported != ALLOWED_APP_CATALOG_IMPORT) {
                                add(
                                    ArchitectureViolation(
                                        code = "step2_surface.app_catalog_import",
                                        module = module,
                                        detail = "$relativePath:$imported",
                                    ),
                                )
                            }
                        }
                        DESIGN_SYSTEM_IMPORT.findAll(text).forEach { match ->
                            val imported = match.groupValues[1]
                            if (imported != ALLOWED_APP_DESIGN_SYSTEM_IMPORT) {
                                add(
                                    ArchitectureViolation(
                                        code = "step2_surface.app_designsystem_import",
                                        module = module,
                                        detail = "$relativePath:$imported",
                                    ),
                                )
                            }
                        }
                    }
                    if (module == ":core:designsystem" &&
                        DESIGN_SYSTEM_FORBIDDEN_IMPORT.containsMatchIn(text)
                    ) {
                        add(
                            ArchitectureViolation(
                                code = "step2_surface.designsystem_import",
                                module = module,
                                detail = relativePath,
                            ),
                        )
                    }
                }
        }
    }

    private fun androidLibraryVariantViolations(root: File): List<ArchitectureViolation> {
        val convention = File(
            root,
            "build-logic/src/main/kotlin/app/openstory/build/AndroidLibraryConventionPlugin.kt",
        )
        if (!convention.isFile) return emptyList()
        val text = convention.readText()
        return REQUIRED_LIBRARY_BUILD_TYPES
            .filterNot(text::contains)
            .map { buildType ->
                ArchitectureViolation(
                    code = "step2_surface.library_variant_missing",
                    module = null,
                    detail = buildType,
                )
            }
    }

    private fun violation(code: String, module: String, file: File) =
        ArchitectureViolation(code, module, file.invariantSeparatorsPath)

    private fun packageName(text: String): String? =
        PACKAGE_DECLARATION.find(text)?.groupValues?.get(1)

    private data class ExpectedEdges(
        val production: Set<String> = emptySet(),
        val test: Set<String> = emptySet(),
    )

    private val EXPECTED_GRAPH = linkedMapOf(
        ":app" to ExpectedEdges(
            setOf(":core:designsystem", ":feature:catalog"),
            setOf(":benchmark"),
        ),
        ":core:common" to ExpectedEdges(),
        ":core:designsystem" to ExpectedEdges(),
        ":catalog:model" to ExpectedEdges(setOf(":core:common")),
        ":catalog:engine" to ExpectedEdges(setOf(":catalog:model", ":core:common")),
        ":reader:engine" to ExpectedEdges(setOf(":core:common")),
        ":plugins:api" to ExpectedEdges(),
        ":benchmark" to ExpectedEdges(test = setOf(":app")),
        ":catalog:domain" to ExpectedEdges(setOf(":core:common")),
        ":catalog:storage" to ExpectedEdges(setOf(":catalog:domain", ":core:common")),
        ":catalog:runtime" to ExpectedEdges(setOf(":catalog:domain", ":catalog:storage")),
        ":feature:catalog" to ExpectedEdges(
            setOf(":catalog:domain", ":catalog:runtime", ":core:designsystem"),
            setOf(":plugins:api"),
        ),
    )
    private val STEP2_MODULES = setOf(
        ":core:designsystem",
        ":catalog:domain",
        ":catalog:storage",
        ":catalog:runtime",
        ":feature:catalog",
    )
    private val SOURCE_EXTENSIONS = setOf("kt", "java")
    private val REQUIRED_LIBRARY_BUILD_TYPES = setOf(
        "debug",
        "release",
        "benchmarkRelease",
        "nonMinifiedRelease",
    )
    private const val ALLOWED_APP_CATALOG_IMPORT =
        "app.openstory.catalog.feature.CatalogEntryPoint"
    private const val ALLOWED_APP_DESIGN_SYSTEM_IMPORT =
        "app.openstory.designsystem.theme.HikariTheme"
    private const val ANDROID_TEST_IMPLEMENTATION = "androidTestImplementation"
    private val ROOM_TOKEN = Regex("""(?i)(androidx[.-]room|libs\.androidx\.room|libs\.room)""")
    private val COIL_TOKEN = Regex("""(?i)(libs\.coil|io\.coil-kt)""")
    private val COIL_NETWORK_TOKEN = Regex("""(?i)(coil[-.]network|coil\.network)""")
    private val HTTP_DEPENDENCY_TOKEN = Regex("""(?i)(okhttp|ktor[-.]client|httpclient)""")
    private val ROOM_CONVENTION_TOKEN = Regex("""openstory\.room""")
    private val DESIGN_SYSTEM_FORBIDDEN_DEPENDENCY_TOKEN = Regex(
        """(?i)(project\s*\(|room|coil|okhttp|java\.net|workmanager|androidx\.work|""" +
            """javascriptengine|backdrop|blur|roborazzi|robolectric)""",
    )
    private val PLUGIN_API_DEPENDENCY = Regex(
        """(?m)^\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*project\s*\(""" +
            """\s*\":plugins:api\"\s*\)\s*\)""",
    )
    private val JAVASCRIPT_ENGINE_DEPENDENCY = Regex(
        """(?m)^\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*libs\.androidx\.javascriptengine\s*\)""",
    )
    private val REQUIRED_PLUGIN_HARNESS_DEPENDENCIES = listOf(
        Regex(
            """(?m)^\s*androidTestImplementation\s*\(\s*project\s*\(""" +
                """\s*\":plugins:api\"\s*\)\s*\)""",
        ),
        Regex(
            """(?m)^\s*androidTestImplementation\s*\(\s*libs\.androidx\.javascriptengine\s*\)""",
        ),
        Regex(
            """(?m)^\s*androidTestImplementation\s*\(\s*libs\.kotlinx\.serialization\.json\s*\)""",
        ),
        Regex(
            """(?m)^\s*androidTestImplementation\s*\(\s*libs\.kotlinx\.coroutines\.core\s*\)""",
        ),
    )
    private val PRODUCTION_SOURCE_PATH = Regex("""/src/(main|release)/""")
    private val RELEASE_FIXTURE_NAME = Regex(
        """(?i)(seed|benchmark.*(?:fixture|diagnostic|preparation)|plugin.*harness|harness.*plugin)""",
    )
    private val REMOTE_TRANSPORT_IMPLEMENTATION = Regex(
        """(?ms)^\s*(?:(?:public|internal|private|protected|abstract|final|open|data|sealed|value)\s+)*""" +
            """(?:class\s+[A-Za-z_][A-Za-z0-9_]*(?:\s*<[^>{}]*>)?(?:\s*\(.*?\))?""" +
            """|object(?:\s+[A-Za-z_][A-Za-z0-9_]*)?)\s*:\s*""" +
            """(?:app\.openstory\.catalog\.feature\.assets\.)?RemoteCoverTransport\b""",
    )
    private val HTTP_IMPORT = Regex(
        """(?m)^\s*import\s+((?:okhttp3\.|java\.net\.(?:HttpURLConnection|URL)\b|org\.apache\.http\.)[^\s;]*)""",
    )
    private val COIL_IMPORT = Regex("""(?m)^\s*import\s+(coil\.[A-Za-z0-9_.*]+)""")
    private val CATALOG_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.catalog\.[A-Za-z0-9_.*]+)""",
    )
    private val DESIGN_SYSTEM_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.designsystem\.[A-Za-z0-9_.*]+)""",
    )
    private val DESIGN_SYSTEM_FORBIDDEN_IMPORT = Regex(
        """(?m)^\s*import\s+(?:androidx\.(?:lifecycle|room|work|javascriptengine)\.|""" +
            """coil\.|okhttp3\.|java\.net\.|kotlinx\.coroutines\.|""" +
            """app\.openstory\.(?:catalog|plugins)\.)""",
    )
    private val PACKAGE_DECLARATION = Regex(
        """(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\b""",
    )
}
