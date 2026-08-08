package app.openstory.plugin.host.selector.validation

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy

internal class OutputValidationSupport(
    private val urlPolicy: PluginUrlPolicy,
    val limits: PluginOutputLimits,
) {
    fun validateUrl(
        value: String,
        path: String,
        declaredHost: String? = null,
    ) {
        when (val decision = urlPolicy.resolve(value)) {
            is AppResult.Failure -> {
                val code = if (decision.error.code == "plugin.domain_denied") {
                    "plugin.output_undeclared_host"
                } else {
                    "plugin.output_invalid_url"
                }
                throw OutputValidationFailure(code, path)
            }
            is AppResult.Success -> if (
                declaredHost != null && decision.value.host != declaredHost
            ) {
                throw OutputValidationFailure("plugin.output_undeclared_host", path)
            }
        }
    }

    fun requireUnique(
        values: List<String>,
        path: String,
        field: String,
    ) {
        val seen = mutableSetOf<String>()
        values.forEachIndexed { index, value ->
            if (!seen.add(value)) {
                throw OutputValidationFailure(
                    code = "plugin.output_duplicate_id",
                    path = "$path.$index.$field",
                )
            }
        }
    }

    fun requireLimit(
        withinLimit: Boolean,
        path: String,
    ) {
        if (!withinLimit) {
            throw OutputValidationFailure("plugin.output_limit", path)
        }
    }
}

internal class OutputValidationFailure(
    val code: String,
    val path: String,
) : RuntimeException(null, null, false, false)

internal inline fun <T> validateOutput(
    value: T,
    block: () -> Unit,
): AppResult<T> = try {
    block()
    AppResult.Success(value)
} catch (failure: OutputValidationFailure) {
    AppResult.Failure(
        AppError.Plugin(
            code = failure.code,
            retryable = false,
            diagnostic = AppError.Diagnostic.of("field_path" to failure.path),
        ),
    )
}
