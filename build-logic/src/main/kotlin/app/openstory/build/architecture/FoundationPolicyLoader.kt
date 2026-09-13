package app.openstory.build.architecture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

internal object FoundationPolicyLoader {
    private val json = Json { ignoreUnknownKeys = false }
    private val versionOneKeys = setOf(
        "schemaVersion",
        "maxProductionKotlinLines",
        "forbiddenSourceTokens",
        "forbiddenBuildTokens",
        "forbiddenBroadTypeSuffixes",
        "forbiddenManifestPermissions",
        "allowedStartupInitializers",
    )
    private val versionTwoKeys = versionOneKeys + "productionKotlinLineBudgets"
    private val qualifiedClassName = Regex(
        """^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$""",
    )

    fun parse(text: String): FoundationPolicy {
        val root = json.parseToJsonElement(text).jsonObject
        val schemaVersion = root.getValue("schemaVersion").jsonPrimitive.int
        require(schemaVersion in 1..2) { "v2_foundation.schema:$schemaVersion" }
        val expectedKeys = if (schemaVersion == 1) versionOneKeys else versionTwoKeys
        require(root.keys == expectedKeys) {
            "v2_foundation.policy_keys: expected=$expectedKeys actual=${root.keys}"
        }

        fun stringSet(name: String): Set<String> {
            val values = root.getValue(name).jsonArray.map { element ->
                val primitive = element as? JsonPrimitive
                require(primitive?.isString == true) {
                    "v2_foundation.non_string_value:$name"
                }
                primitive.content
            }
            require(values.all { it.isNotBlank() }) {
                "v2_foundation.blank_value:$name"
            }
            require(values.size == values.toSet().size) {
                "v2_foundation.duplicate_value:$name"
            }
            return values.toCollection(linkedSetOf())
        }

        val maxLines = root.getValue("maxProductionKotlinLines").jsonPrimitive.int
        require(maxLines > 0) { "v2_foundation.max_lines:$maxLines" }

        val lineBudgets = if (schemaVersion == 1) {
            mapOf("*" to maxLines)
        } else {
            root.getValue("productionKotlinLineBudgets").jsonObject
                .mapValues { (prefix, value) ->
                    require(prefix == "*" || PREFIX_PATH.matches(prefix)) {
                        "v2_foundation.line_budget_prefix:$prefix"
                    }
                    value.jsonPrimitive.int.also { budget ->
                        require(budget > 0) {
                            "v2_foundation.line_budget:$prefix=$budget"
                        }
                    }
                }
                .also { budgets ->
                    require("*" in budgets) {
                        "v2_foundation.line_budget_catch_all"
                    }
                }
        }

        val initializers = stringSet("allowedStartupInitializers")
        require(initializers.all(qualifiedClassName::matches)) {
            "v2_foundation.initializer_name"
        }

        return FoundationPolicy(
            schemaVersion = schemaVersion,
            maxProductionKotlinLines = maxLines,
            productionKotlinLineBudgets = lineBudgets,
            forbiddenSourceTokens = stringSet("forbiddenSourceTokens"),
            forbiddenBuildTokens = stringSet("forbiddenBuildTokens"),
            forbiddenBroadTypeSuffixes = stringSet("forbiddenBroadTypeSuffixes"),
            forbiddenManifestPermissions = stringSet("forbiddenManifestPermissions"),
            allowedStartupInitializers = initializers,
        )
    }

    private val PREFIX_PATH = Regex(
        """^[A-Za-z_][A-Za-z0-9_-]*(/[A-Za-z_][A-Za-z0-9_-]*)*/$""",
    )
}
