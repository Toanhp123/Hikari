package app.openstory.plugin.api.testing

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun ContractReport.toMarkdown(): String = buildString {
    appendLine("# Plugin Contract Report")
    appendLine()
    appendLine("Violations: ${violations.size}")

    violations.forEach { violation ->
        appendLine()
        appendLine("## ${violation.code}")
        appendLine()
        appendLine("- Method: `${violation.method}`")
        appendLine(
            "- Source ID: `${violation.sourceId ?: "unknown"}`",
        )
    }
}

fun ContractReport.toJson(): String =
    buildJsonObject {
        put(
            "violations",
            buildJsonArray {
                violations.forEach { violation ->
                    add(
                        buildJsonObject {
                            put(
                                "code",
                                JsonPrimitive(violation.code),
                            )
                            put(
                                "method",
                                JsonPrimitive(violation.method),
                            )
                            put(
                                "sourceId",
                                violation.sourceId
                                    ?.let(::JsonPrimitive)
                                    ?: JsonNull,
                            )
                        },
                    )
                }
            },
        )
    }.toString()
