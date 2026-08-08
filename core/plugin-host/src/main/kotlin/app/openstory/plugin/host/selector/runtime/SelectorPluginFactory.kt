package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.api.selector.SelectorDefinition
import app.openstory.plugin.api.selector.SelectorValidation
import app.openstory.plugin.host.selector.HtmlDocumentAdapter
import app.openstory.plugin.host.selector.JsoupHtmlDocumentAdapter
import app.openstory.plugin.host.selector.SelectorDocumentLoader
import app.openstory.plugin.host.selector.SelectorExecutionContext
import app.openstory.plugin.host.selector.SelectorLimits
import app.openstory.plugin.host.selector.binding.SelectorBindingEvaluator
import app.openstory.plugin.host.selector.mapper.CatalogSelectorMapper
import app.openstory.plugin.host.selector.mapper.ContentSelectorMapper
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator

data class SelectorPlugins(
    val catalog: CatalogPlugin?,
    val content: ContentPlugin?,
)

class SelectorPluginFactory(
    private val html: HtmlDocumentAdapter = JsoupHtmlDocumentAdapter(),
    private val limits: SelectorLimits = SelectorLimits(),
) {
    fun create(
        manifest: PluginManifest,
        definition: SelectorDefinition,
        http: PluginHttpGateway,
    ): AppResult<SelectorPlugins> {
        if (SelectorValidation.validate(definition, manifest).isFailure) {
            return invalidDefinition()
        }
        val urlPolicy = PluginUrlPolicy(
            allowedHosts = manifest.allowedHosts,
            baseUrl = manifest.declarativeOrigin,
        )
        val evaluator = SelectorBindingEvaluator(html, urlPolicy)
        val outputValidator = PluginWireDtoValidator(urlPolicy)
        val executor = SelectorEndpointExecutor(
            loader = SelectorDocumentLoader(http, html, limits),
            evaluator = evaluator,
            context = SelectorExecutionContext(manifest.declarativeOrigin),
            limits = limits,
        )
        val catalogMapper = CatalogSelectorMapper(outputValidator, urlPolicy)
        val contentMapper = ContentSelectorMapper(
            outputValidator,
            urlPolicy,
            html,
            evaluator,
        )
        return AppResult.Success(
            SelectorPlugins(
                catalog = definition.catalog?.let {
                    SelectorCatalogPlugin(it, executor, catalogMapper)
                },
                content = definition.content?.let {
                    SelectorContentPlugin(it, executor, contentMapper)
                },
            ),
        )
    }
}

private fun invalidDefinition(): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = "plugin.selector_definition_invalid",
        retryable = false,
    ),
)

internal fun <T> unavailable(endpoint: String): AppResult<T> = AppResult.Failure(
    AppError.Plugin(
        code = "plugin.selector_endpoint_unavailable",
        retryable = false,
        diagnostic = AppError.Diagnostic.of("endpoint" to endpoint),
    ),
)
