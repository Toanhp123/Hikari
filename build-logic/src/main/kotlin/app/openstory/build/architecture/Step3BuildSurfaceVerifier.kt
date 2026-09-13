package app.openstory.build.architecture

import java.io.File

object Step3BuildSurfaceVerifier {
    fun verify(
        rootDirectory: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        addAll(buildDependencyViolations(rootDirectory, policy))
        addAll(sourceViolations(rootDirectory, policy))
        addAll(androidLibraryVariantViolations(rootDirectory))
        val catalogFeature = policy.modules[":feature:catalog"]
        if (catalogFeature != null && File(rootDirectory, catalogFeature.path).isDirectory) {
            addAll(CatalogVariantSurfaceVerifier.verify(rootDirectory))
        }
    }.distinct().sorted()

    private fun buildDependencyViolations(
        root: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        policy.modules.toSortedMap().forEach { (module, rule) ->
            val buildFile = File(root, "${rule.path}/build.gradle.kts")
            if (!buildFile.isFile) return@forEach
            val text = buildFile.readText()

            if (ROOM_TOKEN.containsMatchIn(text) && module !in STORAGE_OWNERS) {
                add(violation("step3_surface.room_owner", module, buildFile, root))
            }
            if (HTTP_DEPENDENCY_TOKEN.containsMatchIn(text) && module != CORE_NETWORK) {
                add(violation("step3_surface.http_dependency_owner", module, buildFile, root))
            }
            if (COIL_TOKEN.containsMatchIn(text) && module !in ARTWORK_OWNERS) {
                add(violation("step3_surface.artwork_dependency_owner", module, buildFile, root))
            }
            if (NAVIGATION_DEPENDENCY_TOKEN.containsMatchIn(text) && module != APP_MODULE) {
                add(
                    violation(
                        "step3_surface.navigation_dependency_owner",
                        module,
                        buildFile,
                        root,
                    ),
                )
            }
            if (ROOM_CONVENTION_TOKEN.containsMatchIn(text)) {
                add(violation("step3_surface.room_convention_forbidden", module, buildFile, root))
            }
            if (module == DESIGN_SYSTEM &&
                DESIGN_SYSTEM_FORBIDDEN_DEPENDENCY_TOKEN.containsMatchIn(text)
            ) {
                add(violation("step3_surface.designsystem_dependency", module, buildFile, root))
            }
            addAll(pluginHarnessDependencyViolations(module, buildFile, text, root))
        }
    }

    private fun pluginHarnessDependencyViolations(
        module: String,
        buildFile: File,
        text: String,
        root: File,
    ): List<ArchitectureViolation> = buildList {
        PLUGIN_API_DEPENDENCY.findAll(text)
            .filterNot { match ->
                module == ":feature:catalog" &&
                    match.groupValues[1] == ANDROID_TEST_IMPLEMENTATION
            }
            .forEach {
                add(violation("step3_surface.plugin_api_scope", module, buildFile, root))
            }
        JAVASCRIPT_ENGINE_DEPENDENCY.findAll(text)
            .filterNot { match ->
                module == ":feature:catalog" &&
                    match.groupValues[1] == ANDROID_TEST_IMPLEMENTATION
            }
            .forEach {
                add(
                    violation(
                        "step3_surface.javascriptengine_scope",
                        module,
                        buildFile,
                        root,
                    ),
                )
            }
    }

    private fun sourceViolations(
        root: File,
        policy: ModuleBoundaryPolicy,
    ): List<ArchitectureViolation> = buildList {
        policy.modules.toSortedMap().forEach { (module, rule) ->
            val moduleRoot = File(root, rule.path)
            if (!moduleRoot.isDirectory || rule.platform == ModulePlatform.ANDROID_TEST) {
                return@forEach
            }
            moduleRoot.walkTopDown()
                .filter(File::isFile)
                .filter { file ->
                    PRODUCTION_SOURCE_PATH.containsMatchIn(file.invariantSeparatorsPath)
                }
                .forEach { file ->
                    val relativePath = file.relativeTo(root).invariantSeparatorsPath
                    if (RELEASE_FIXTURE_NAME.containsMatchIn(file.nameWithoutExtension)) {
                        add(
                            ArchitectureViolation(
                                code = "step3_surface.release_fixture",
                                module = module,
                                detail = relativePath,
                            ),
                        )
                    }
                    if (file.extension.lowercase() !in SOURCE_EXTENSIONS) return@forEach

                    val text = file.readText()
                    HTTP_IMPORT.findAll(text).forEach { match ->
                        if (module != CORE_NETWORK) {
                            add(
                                ArchitectureViolation(
                                    code = "step3_surface.http_import_owner",
                                    module = module,
                                    detail = "$relativePath:${match.groupValues[1]}",
                                ),
                            )
                        }
                    }
                    NAVIGATION_IMPORT.findAll(text).forEach { match ->
                        if (module != APP_MODULE ||
                            !relativePath.isAppPackagePath("navigation")
                        ) {
                            add(
                                ArchitectureViolation(
                                    code = "step3_surface.navigation_import_scope",
                                    module = module,
                                    detail = "$relativePath:${match.groupValues[1]}",
                                ),
                            )
                        }
                    }
                    if (module == APP_MODULE) {
                        addAll(appImportViolations(relativePath, text))
                    }
                    if (module == DESIGN_SYSTEM &&
                        DESIGN_SYSTEM_FORBIDDEN_IMPORT.containsMatchIn(text)
                    ) {
                        add(
                            ArchitectureViolation(
                                code = "step3_surface.designsystem_import",
                                module = module,
                                detail = relativePath,
                            ),
                        )
                    }
                }
        }
    }

    private fun appImportViolations(
        relativePath: String,
        text: String,
    ): List<ArchitectureViolation> = buildList {
        APP_STORAGE_IMPORT.findAll(text).forEach { match ->
            add(appViolation("step3_surface.app_storage_import", relativePath, match))
        }
        APP_RUNTIME_IMPORT.findAll(text).forEach { match ->
            if (!relativePath.isAppPackagePath("composition")) {
                add(
                    appViolation(
                        "step3_surface.app_runtime_import_scope",
                        relativePath,
                        match,
                    ),
                )
            }
        }
        APP_SOURCE_IMPORT.findAll(text).forEach { match ->
            if (!relativePath.isAppPackagePath("composition")) {
                add(
                    appViolation(
                        "step3_surface.app_source_import_scope",
                        relativePath,
                        match,
                    ),
                )
            }
        }
    }

    private fun String.isAppPackagePath(packageName: String): Boolean =
        replace('\\', '/').contains("/app/openstory/$packageName/")

    private fun appViolation(
        code: String,
        relativePath: String,
        match: MatchResult,
    ) = ArchitectureViolation(
        code = code,
        module = APP_MODULE,
        detail = "$relativePath:${match.groupValues[1]}",
    )

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
                    code = "step3_surface.library_variant_missing",
                    module = null,
                    detail = buildType,
                )
            }
    }

    private fun violation(
        code: String,
        module: String,
        file: File,
        root: File,
    ) = ArchitectureViolation(
        code,
        module,
        file.relativeTo(root).invariantSeparatorsPath,
    )

    private const val APP_MODULE = ":app"
    private const val CORE_NETWORK = ":core:network"
    private const val DESIGN_SYSTEM = ":core:designsystem"
    private const val ANDROID_TEST_IMPLEMENTATION = "androidTestImplementation"
    private val STORAGE_OWNERS = setOf(
        ":catalog:storage",
        ":library:storage",
        ":reading:storage",
    )
    private val ARTWORK_OWNERS = setOf(":core:artwork", ":feature:catalog")
    private val SOURCE_EXTENSIONS = setOf("kt", "java")
    private val REQUIRED_LIBRARY_BUILD_TYPES = setOf(
        "debug",
        "release",
        "benchmarkRelease",
        "nonMinifiedRelease",
    )
    private val ROOM_TOKEN = Regex("""(?i)(androidx[.-]room|libs\.androidx\.room|libs\.room)""")
    private val COIL_TOKEN = Regex("""(?i)(libs\.coil|io\.coil-kt)""")
    private val HTTP_DEPENDENCY_TOKEN = Regex("""(?i)(okhttp|ktor[-.]client|httpclient)""")
    private val NAVIGATION_DEPENDENCY_TOKEN = Regex("""libs\.androidx\.navigation3""")
    private val ROOM_CONVENTION_TOKEN = Regex("""openstory\.room""")
    private val DESIGN_SYSTEM_FORBIDDEN_DEPENDENCY_TOKEN = Regex(
        """(?i)(project\s*\(|room|coil|okhttp|java\.net|workmanager|androidx\.work|""" +
            """javascriptengine|backdrop|blur|roborazzi)""",
    )
    private val PLUGIN_API_DEPENDENCY = Regex(
        """(?m)^\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*project\s*\(""" +
            """\s*\":plugins:api\"\s*\)\s*\)""",
    )
    private val JAVASCRIPT_ENGINE_DEPENDENCY = Regex(
        """(?m)^\s*([A-Za-z][A-Za-z0-9]*)\s*\(\s*libs\.androidx\.javascriptengine\s*\)""",
    )
    private val PRODUCTION_SOURCE_PATH = Regex("""/src/(main|release)/""")
    private val RELEASE_FIXTURE_NAME = Regex(
        """(?i)(seed|benchmark.*(?:fixture|diagnostic|preparation)|plugin.*harness|harness.*plugin)""",
    )
    private val HTTP_IMPORT = Regex(
        """(?m)^\s*import\s+((?:okhttp3\.|java\.net\.(?:HttpURLConnection|URL)\b|""" +
            """org\.apache\.http\.)[^\s;]*)""",
    )
    private val APP_STORAGE_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.(?:catalog|library|reading)\.storage\.[^\s;]+)""",
    )
    private val APP_RUNTIME_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.(?:catalog|library|reading|settings)\.runtime\.[^\s;]+)""",
    )
    private val APP_SOURCE_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.sources\.[^\s;]+)""",
    )
    private val NAVIGATION_IMPORT = Regex(
        """(?m)^\s*import\s+(androidx\.navigation3\.[^\s;]+)""",
    )
    private val DESIGN_SYSTEM_FORBIDDEN_IMPORT = Regex(
        """(?m)^\s*import\s+(?:androidx\.(?:lifecycle|room|work|javascriptengine)\.|""" +
            """coil\.|okhttp3\.|java\.net\.|kotlinx\.coroutines\.|""" +
            """app\.openstory\.(?:catalog|library|reading|settings|sources|plugins)\.)""",
    )
}
