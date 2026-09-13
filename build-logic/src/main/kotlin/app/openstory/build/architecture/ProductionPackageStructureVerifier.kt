package app.openstory.build.architecture

object ProductionPackageStructureVerifier {
    fun verify(
        moduleSources: Map<String, Map<String, String>>,
    ): List<ArchitectureViolation> = moduleSources
        .toSortedMap()
        .flatMap { (module, sources) ->
            packageCycles(sources).map { packages ->
                ArchitectureViolation(
                    code = "step2_structure.package_cycle",
                    module = module,
                    detail = "packages=${packages.joinToString(",")}",
                )
            }
        }
        .sorted()

    private fun packageCycles(
        sources: Map<String, String>,
    ): List<List<String>> {
        val parsed = sources.values.map { text ->
            ParsedSource(
                packageName = PACKAGE_DECLARATION.find(text)
                    ?.groupValues
                    ?.get(1),
                imports = PROJECT_IMPORT.findAll(text)
                    .map { match -> match.groupValues[1] }
                    .toList(),
                declaredTypeNames = TYPE_DECLARATION.findAll(text)
                    .map { match -> match.groupValues[1] }
                    .toSet(),
            )
        }
        val packages = parsed.mapNotNull(ParsedSource::packageName).toSortedSet()
        val declaredTypesByPackage = parsed
            .filter { source -> source.packageName != null }
            .groupBy { source -> checkNotNull(source.packageName) }
            .mapValues { (_, packageSources) ->
                packageSources.flatMapTo(linkedSetOf(), ParsedSource::declaredTypeNames)
            }
        val graph = packages.associateWith { linkedSetOf<String>() }

        parsed.forEach { source ->
            val sourcePackage = source.packageName ?: return@forEach
            source.imports.forEach { importedName ->
                packages
                    .filter { candidate ->
                        importedNameTargetsPackage(
                            importedName = importedName,
                            candidate = candidate,
                            declaredTypeNames = declaredTypesByPackage.getValue(candidate),
                        )
                    }
                    .maxByOrNull(String::length)
                    ?.takeIf { target -> target != sourcePackage }
                    ?.let(graph.getValue(sourcePackage)::add)
            }
        }

        return stronglyConnectedComponents(graph)
            .filter { component -> component.size > 1 }
            .map(Set<String>::sorted)
            .sortedBy { component -> component.joinToString("\u0000") }
    }

    private fun importedNameTargetsPackage(
        importedName: String,
        candidate: String,
        declaredTypeNames: Set<String>,
    ): Boolean {
        val importedTarget = importedName.removeSuffix(".*")
        if (importedTarget == candidate) return true
        if (!importedTarget.startsWith("$candidate.")) return false

        val relativeTarget = importedTarget.removePrefix("$candidate.")
        val directOwner = importedTarget.substringBeforeLast('.')
        return directOwner == candidate || relativeTarget.substringBefore('.') in declaredTypeNames
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

        val reverse = graph.keys.associateWith { linkedSetOf<String>() }
        graph.forEach { (source, targets) ->
            targets.forEach { target -> reverse.getValue(target).add(source) }
        }

        visited.clear()
        return buildList {
            fun collect(node: String, component: MutableSet<String>) {
                if (!visited.add(node)) return
                component += node
                reverse.getValue(node).sorted().forEach { collect(it, component) }
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

    private data class ParsedSource(
        val packageName: String?,
        val imports: List<String>,
        val declaredTypeNames: Set<String>,
    )

    private val PACKAGE_DECLARATION = Regex(
        """(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\b""",
    )
    private val PROJECT_IMPORT = Regex(
        """(?m)^\s*import\s+(app\.openstory\.[A-Za-z_][A-Za-z0-9_.*]*)""",
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
}
