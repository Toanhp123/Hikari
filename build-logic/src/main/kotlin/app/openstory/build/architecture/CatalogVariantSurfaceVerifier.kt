package app.openstory.build.architecture

import java.io.File

internal object CatalogVariantSurfaceVerifier {
    fun verify(root: File): List<ArchitectureViolation> = buildList {
        addAll(requiredFileViolations(root))
        addAll(bindingContractViolations(root))
        addAll(fixtureAssetViolations(root))
        addAll(releaseFixtureReferenceViolations(root))
        releaseBindingViolation(root)?.let(::add)
        addAll(benchmarkSourceMappingViolations(root))
        duplicateNonMinifiedFixtureViolation(root)?.let(::add)
    }

    private fun requiredFileViolations(root: File) = REQUIRED_VARIANT_FILES.mapNotNull { path ->
        path.takeUnless { File(root, path).isFile }?.let {
            violation("step2_surface.variant_binding_missing", it)
        }
    }

    private fun bindingContractViolations(root: File) = CONCRETE_BINDINGS.mapNotNull { path ->
        val file = File(root, path)
        path.takeIf { file.isFile && !VARIANT_BINDING_CONTRACT.containsMatchIn(file.readText()) }
            ?.let { violation("step2_surface.variant_binding_contract", it) }
    }

    private fun fixtureAssetViolations(root: File) = REQUIRED_FIXTURE_ASSETS.mapNotNull { path ->
        val file = File(root, path)
        when {
            !file.isFile -> violation("step2_surface.fixture_asset_missing", path)
            !file.isCompressedWebp() -> violation("step2_surface.fixture_asset_invalid", path)
            else -> null
        }
    }

    private fun releaseFixtureReferenceViolations(root: File): List<ArchitectureViolation> {
        val releaseRoot = File(root, RELEASE_SOURCE_ROOT)
        if (!releaseRoot.isDirectory) return emptyList()
        return releaseRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension.lowercase() in SOURCE_EXTENSIONS }
            .filter { RELEASE_FIXTURE_REFERENCE.containsMatchIn(it.readText()) }
            .map { violation("step2_surface.release_fixture", it.relativeTo(root).invariantSeparatorsPath) }
            .toList()
    }

    private fun releaseBindingViolation(root: File): ArchitectureViolation? {
        val file = File(root, RELEASE_BINDING)
        return RELEASE_BINDING.takeIf {
            file.isFile && !RELEASE_NULL_BINDING.containsMatchIn(file.readText())
        }?.let { violation("step2_surface.release_fixture", it) }
    }

    private fun benchmarkSourceMappingViolations(root: File): List<ArchitectureViolation> =
        REQUIRED_BENCHMARK_SOURCE_MAPPINGS.mapNotNull { (module, requirement) ->
            val buildFile = File(root, requirement.buildFile)
            if (!buildFile.isFile) return@mapNotNull null
            val text = buildFile.readText()
            val missing = requirement.fragments.filterNot(text::contains)
            missing.takeIf { it.isNotEmpty() }?.let {
                violation(
                    code = "step2_surface.benchmark_source_mapping",
                    module = module,
                    detail = it.sorted().joinToString(","),
                )
            }
        }

    private fun duplicateNonMinifiedFixtureViolation(root: File): ArchitectureViolation? {
        val duplicateRoot = File(root, NON_MINIFIED_SOURCE_ROOT)
        return NON_MINIFIED_SOURCE_ROOT.takeIf {
            duplicateRoot.isDirectory && duplicateRoot.walkTopDown().any(File::isFile)
        }?.let { violation("step2_surface.non_minified_fixture_duplicate", it) }
    }

    private fun File.isCompressedWebp(): Boolean {
        val header = inputStream().use { it.readNBytes(WEBP_HEADER_BYTES) }
        return header.size == WEBP_HEADER_BYTES &&
            header.copyOfRange(RIFF_START, RIFF_END).decodeToString() == "RIFF" &&
            header.copyOfRange(WEBP_START, WEBP_END).decodeToString() == "WEBP" &&
            header.copyOfRange(CHUNK_START, CHUNK_END).decodeToString() in WEBP_CHUNKS
    }

    private fun violation(
        code: String,
        detail: String,
        module: String = ":feature:catalog",
    ) = ArchitectureViolation(
        code = code,
        module = module,
        detail = detail,
    )

    private const val FEATURE_BUILD_FILE = "feature/catalog/build.gradle.kts"
    private const val RUNTIME_BUILD_FILE = "catalog/runtime/build.gradle.kts"
    private const val NON_MINIFIED_SOURCE_ROOT = "feature/catalog/src/nonMinifiedRelease"
    private const val RELEASE_SOURCE_ROOT = "feature/catalog/src/release"
    private const val RELEASE_BINDING =
        "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt"
    private val REQUIRED_VARIANT_FILES = setOf(
        "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogVariantBinding.kt",
        "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
        "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/seed/LocalSeedCatalogSource.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkCatalogSource.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkCatalogFixture.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkCatalogPreparation.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkRetentionPreparation.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkCoverPreparation.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
            "BenchmarkPinPruneSource.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/" +
            "BenchmarkCoverFixture.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/" +
            "BenchmarkCatalogDiagnostics.kt",
        "feature/catalog/src/benchmarkRelease/AndroidManifest.xml",
        RELEASE_BINDING,
    )
    private val CONCRETE_BINDINGS = setOf(
        "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
        "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
        RELEASE_BINDING,
    )
    private val REQUIRED_FIXTURE_ASSETS = setOf(
        "feature/catalog/src/debug/res/drawable-nodpi/catalog_debug_manga_a.webp",
        "feature/catalog/src/debug/res/drawable-nodpi/catalog_debug_manga_b.webp",
        "feature/catalog/src/debug/res/drawable-nodpi/catalog_debug_light_novel_a.webp",
        "feature/catalog/src/debug/res/drawable-nodpi/catalog_debug_light_novel_b.webp",
        "feature/catalog/src/benchmarkRelease/res/drawable-nodpi/catalog_benchmark_manga_a.webp",
        "feature/catalog/src/benchmarkRelease/res/drawable-nodpi/catalog_benchmark_manga_b.webp",
        "feature/catalog/src/benchmarkRelease/res/drawable-nodpi/catalog_benchmark_light_novel_a.webp",
        "feature/catalog/src/benchmarkRelease/res/drawable-nodpi/catalog_benchmark_light_novel_b.webp",
    )
    private val REQUIRED_BENCHMARK_SOURCE_MAPPINGS = mapOf(
        ":feature:catalog" to BenchmarkSourceMapping(
            buildFile = FEATURE_BUILD_FILE,
            fragments = setOf(
                "listOf(\"benchmarkRelease\", \"nonMinifiedRelease\")",
                "kotlin.directories.add(\"src/benchmarkRelease/kotlin\")",
                "res.srcDir(\"src/benchmarkRelease/res\")",
                "manifest.srcFile(\"src/benchmarkRelease/AndroidManifest.xml\")",
            ),
        ),
        ":catalog:runtime" to BenchmarkSourceMapping(
            buildFile = RUNTIME_BUILD_FILE,
            fragments = setOf(
                "listOf(\"benchmarkRelease\", \"nonMinifiedRelease\")",
                "kotlin.directories.add(\"src/benchmarkRelease/kotlin\")",
            ),
        ),
    )
    private data class BenchmarkSourceMapping(
        val buildFile: String,
        val fragments: Set<String>,
    )
    private val RELEASE_NULL_BINDING = Regex("""\boverride\s+val\s+binding\s*=\s*null\b""")
    private val RELEASE_FIXTURE_REFERENCE = Regex(
        """(?i)(seed|benchmark.*(?:fixture|diagnostic|preparation)|localseedcatalogsource|plugin.*harness)""",
    )
    private val VARIANT_BINDING_CONTRACT = Regex(
        """\bobject\s+VariantCatalogBinding\s*:\s*CatalogVariantBinding\b""",
    )
    private val WEBP_CHUNKS = setOf("VP8 ", "VP8L", "VP8X")
    private val SOURCE_EXTENSIONS = setOf("kt", "java")
    private const val WEBP_HEADER_BYTES = 16
    private const val RIFF_START = 0
    private const val RIFF_END = 4
    private const val WEBP_START = 8
    private const val WEBP_END = 12
    private const val CHUNK_START = 12
    private const val CHUNK_END = 16
}
