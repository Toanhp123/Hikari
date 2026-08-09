package app.openstory.build.architecture

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int

object ModuleBoundaryPolicyLoader {
    private const val CURRENT_SCHEMA_VERSION = 2

    private val rootFields = setOf(
        "schemaVersion",
        "modules",
    )

    private val moduleFields = setOf(
        "path",
        "platform",
        "dependencyMode",
        "productionDependencies",
        "testDependencies",
        "forbiddenProductionImports",
    )

    fun load(file: File): ModuleBoundaryPolicy {
        require(file.isFile) {
            "module_policy.file_missing: ${file.path}"
        }

        val root = Json.parseToJsonElement(file.readText()) as? JsonObject
            ?: throw IllegalArgumentException("module_policy.root_not_object")
        root.requireKnownFields(rootFields, "root")

        val schemaVersion = root.requiredPrimitive("schemaVersion").int
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "module_policy.unsupported_schema: $schemaVersion"
        }

        val modules = parseModules(root.requiredObject("modules"))
        validateModuleReferences(modules)

        return ModuleBoundaryPolicy(
            schemaVersion = schemaVersion,
            modules = modules,
        )
    }

    private fun parseModules(
        modulesObject: JsonObject,
    ): Map<String, ModuleBoundaryRule> {
        require(modulesObject.isNotEmpty()) {
            "module_policy.empty_modules"
        }

        return modulesObject.entries
            .sortedBy { it.key }
            .associateTo(linkedMapOf()) { (name, value) ->
                require(moduleNamePattern.matches(name)) {
                    "module_policy.invalid_module_name: $name"
                }

                val rule = value as? JsonObject
                    ?: throw IllegalArgumentException(
                        "module_policy.module_not_object: $name",
                    )
                rule.requireKnownFields(moduleFields, name)

                name to parseRule(name, rule)
            }
    }

    private fun parseRule(
        module: String,
        rule: JsonObject,
    ): ModuleBoundaryRule {
        val path = rule.requiredPrimitive("path").content
        require(path.isNotBlank() && !path.startsWith('/') && ".." !in path) {
            "module_policy.invalid_path: $module -> $path"
        }

        val production = rule.stringSet("productionDependencies")
        val test = rule.stringSet("testDependencies")
        require((production intersect test).isEmpty()) {
            "module_policy.ambiguous_dependency_scope: $module"
        }

        return ModuleBoundaryRule(
            path = path,
            platform = ModulePlatform.fromPolicyValue(
                rule.requiredPrimitive("platform").content,
            ),
            dependencyMode = rule.optionalPrimitive("dependencyMode")
                ?.content
                ?.let(DependencyMode::fromPolicyValue)
                ?: DependencyMode.EXACT,
            productionDependencies = production,
            testDependencies = test,
            forbiddenProductionImports = rule.stringSet(
                "forbiddenProductionImports",
            ),
        )
    }

    private fun validateModuleReferences(
        modules: Map<String, ModuleBoundaryRule>,
    ) {
        val declaredNames = modules.keys
        val duplicatePaths = modules.entries
            .groupBy { it.value.path }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatePaths.isEmpty()) {
            "module_policy.duplicate_path: ${duplicatePaths.sorted()}"
        }

        modules.forEach { (module, rule) ->
            (rule.productionDependencies + rule.testDependencies)
                .forEach { dependency ->
                    require(dependency in declaredNames) {
                        "module_policy.unknown_dependency: $module -> $dependency"
                    }
                    require(dependency != module) {
                        "module_policy.self_dependency: $module"
                    }
                }
        }
    }

    private fun JsonObject.requiredPrimitive(name: String): JsonPrimitive =
        get(name) as? JsonPrimitive
            ?: throw IllegalArgumentException(
                "module_policy.missing_or_invalid_field: $name",
            )

    private fun JsonObject.requiredObject(name: String): JsonObject =
        get(name) as? JsonObject
            ?: throw IllegalArgumentException(
                "module_policy.missing_or_invalid_field: $name",
            )

    private fun JsonObject.optionalPrimitive(name: String): JsonPrimitive? =
        get(name)?.let { value ->
            value as? JsonPrimitive
                ?: throw IllegalArgumentException(
                    "module_policy.missing_or_invalid_field: $name",
                )
        }

    private fun JsonObject.stringSet(name: String): Set<String> {
        val values = get(name) as? JsonArray
            ?: throw IllegalArgumentException(
                "module_policy.missing_or_invalid_field: $name",
            )

        return values.map { element ->
            (element as? JsonPrimitive)?.content
                ?: throw IllegalArgumentException(
                    "module_policy.non_string_value: $name",
                )
        }.also { entries ->
            require(entries.none(String::isBlank)) {
                "module_policy.blank_value: $name"
            }
            require(entries.distinct().size == entries.size) {
                "module_policy.duplicate_value: $name"
            }
        }.toCollection(linkedSetOf())
    }

    private fun JsonObject.requireKnownFields(
        allowed: Set<String>,
        location: String,
    ) {
        val unknown = keys - allowed
        require(unknown.isEmpty()) {
            "module_policy.unknown_field: $location -> ${unknown.sorted()}"
        }
    }

    private val moduleNamePattern = Regex(
        pattern = """^:[a-z0-9][a-z0-9-]*(?::[a-z0-9][a-z0-9-]*)*$""",
    )
}
