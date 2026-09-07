package app.openstory.build.architecture

object AppStructuralVerifier {
    fun verify(
        sources: Map<String, String>,
        policy: FoundationPolicy,
    ): List<FoundationViolation> = buildList {
        val productionKotlinLines = sources.entries
            .filter { (path, _) -> path.endsWith(".kt", ignoreCase = true) }
            .sumOf { (_, text) -> text.sourceLineCount() }
        if (productionKotlinLines > policy.maxProductionKotlinLines) {
            add(
                FoundationViolation(
                    code = "v2_structure.line_budget_exceeded",
                    detail =
                        "actual=$productionKotlinLines " +
                            "max=${policy.maxProductionKotlinLines}",
                ),
            )
        }

        sources.toSortedMap().forEach { (path, text) ->
            TYPE_DECLARATION.findAll(text)
                .map { match -> match.groupValues[1] }
                .filter { name ->
                    policy.forbiddenBroadTypeSuffixes.any(name::endsWith)
                }
                .distinct()
                .forEach { name ->
                    add(
                        FoundationViolation(
                            code = "v2_structure.broad_authority",
                            detail = "$path:$name",
                        ),
                    )
                }

            TEST_ONLY_API_MARKERS
                .filter(text::contains)
                .forEach { marker ->
                    add(
                        FoundationViolation(
                            code = "v2_structure.test_only_production_api",
                            detail = "$path:$marker",
                        ),
                    )
                }
        }

        packageCycles(sources).forEach { packages ->
            add(
                FoundationViolation(
                    code = "v2_structure.package_cycle",
                    detail = "packages=${packages.joinToString(",")}",
                ),
            )
        }
    }.distinct().sorted()

    private fun packageCycles(sources: Map<String, String>): List<List<String>> {
        val parsedSources = sources.values.map { text ->
            ParsedSource(
                packageName = PACKAGE_DECLARATION.find(text)?.groupValues?.get(1),
                imports = PROJECT_IMPORT.findAll(text)
                    .map { match -> match.groupValues[1] }
                    .toList(),
            )
        }
        val declaredPackages = parsedSources
            .mapNotNull(ParsedSource::packageName)
            .toSortedSet()
        val graph = declaredPackages.associateWith { linkedSetOf<String>() }

        parsedSources.forEach { source ->
            val sourcePackage = source.packageName ?: return@forEach
            source.imports.forEach { importedName ->
                val targetPackage = declaredPackages
                    .asSequence()
                    .filter { candidate ->
                        importedName == candidate || importedName.startsWith("$candidate.")
                    }
                    .maxByOrNull(String::length)
                if (targetPackage != null && targetPackage != sourcePackage) {
                    graph.getValue(sourcePackage).add(targetPackage)
                }
            }
        }

        return stronglyConnectedComponents(graph)
            .filter { component -> component.size > 1 }
            .map { component -> component.sorted() }
            .sortedBy { component -> component.joinToString("\u0000") }
    }

    private fun stronglyConnectedComponents(
        graph: Map<String, Set<String>>,
    ): List<Set<String>> {
        val visited = mutableSetOf<String>()
        val finishOrder = mutableListOf<String>()

        fun visit(node: String) {
            if (!visited.add(node)) return
            graph.getValue(node).sorted().forEach(::visit)
            finishOrder += node
        }

        graph.keys.sorted().forEach(::visit)

        val reverseGraph = graph.keys.associateWith { linkedSetOf<String>() }
        graph.forEach { (source, targets) ->
            targets.forEach { target ->
                reverseGraph.getValue(target).add(source)
            }
        }

        visited.clear()
        return buildList {
            fun collect(node: String, component: MutableSet<String>) {
                if (!visited.add(node)) return
                component += node
                reverseGraph.getValue(node).sorted().forEach { dependency ->
                    collect(dependency, component)
                }
            }

            finishOrder.asReversed().forEach { node ->
                if (node !in visited) {
                    val component = linkedSetOf<String>()
                    collect(node, component)
                    add(component)
                }
            }
        }
    }

    private fun String.sourceLineCount(): Int =
        if (isEmpty()) 0 else count { character -> character == '\n' } + 1

    private data class ParsedSource(
        val packageName: String?,
        val imports: List<String>,
    )

    private val PACKAGE_DECLARATION = Regex(
        pattern = """(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\b""",
    )
    private val PROJECT_IMPORT = Regex(
        pattern = """(?m)^\s*import\s+(app\.openstory\.[A-Za-z_][A-Za-z0-9_.*]*)""",
    )
    private val TYPE_DECLARATION = Regex(
        pattern =
            """(?m)^\s*""" +
                """(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^\r\n]*\))?\s+)*""" +
                """(?:(?:public|protected|private|internal|expect|actual|final|open|""" +
                """abstract|sealed|non-sealed|external|inner|enum|annotation|data|""" +
                """value|inline|fun|companion|static|strictfp|native)\s+)*""" +
                """(?:class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
    )
    private val TEST_ONLY_API_MARKERS = listOf("createForTest(", "forTest(")
}
