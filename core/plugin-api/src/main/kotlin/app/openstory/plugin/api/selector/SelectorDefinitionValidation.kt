package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import app.openstory.plugin.api.selector.catalog.CatalogSelectorValidation
import app.openstory.plugin.api.selector.content.ContentSelectorEndpoints
import app.openstory.plugin.api.selector.content.ContentSelectorValidation

internal object SelectorDefinitionValidation {
    fun validate(
        definition: SelectorDefinition,
        manifest: PluginManifest,
    ): Result<Unit> = runCatching {
        if (definition.schemaVersion != SelectorDefinition.CURRENT_SCHEMA_VERSION) {
            selectorFail(
                SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                "Unsupported selector schema version ${definition.schemaVersion}.",
            )
        }
        if (manifest.runtime != PluginRuntime.DECLARATIVE) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_DEFINITION,
                "Selector definition requires a declarative plugin manifest.",
            )
        }
        if (definition.catalog == null && definition.content == null) {
            selectorFail(
                SelectorValidationErrorCode.EMPTY_DEFINITION,
                "Selector definition must declare at least one endpoint group.",
            )
        }
        definition.catalog?.let {
            requireManifestKind(manifest, PluginKind.CATALOG)
            validateCatalog(it, manifest)
        }
        definition.content?.let {
            requireManifestKind(manifest, PluginKind.CONTENT)
            validateContent(it, manifest)
        }
    }

    private fun validateCatalog(
        endpoints: CatalogSelectorEndpoints,
        manifest: PluginManifest,
    ) {
        if (
            listOf(
                endpoints.home,
                endpoints.search,
                endpoints.details,
                endpoints.filters,
            ).all { it == null }
        ) {
            emptyGroup("Catalog")
        }
        endpoints.home?.let {
            validateRequest(it.request, manifest)
            CatalogSelectorValidation.validateHome(it).getOrThrow()
        }
        endpoints.search?.let {
            validateRequest(it.request, manifest)
            CatalogSelectorValidation.validateSearch(it).getOrThrow()
        }
        endpoints.details?.let {
            validateRequest(it.request, manifest)
            CatalogSelectorValidation.validateDetails(it).getOrThrow()
        }
        endpoints.filters?.let {
            CatalogSelectorValidation.validateFilters(it).getOrThrow()
        }
    }

    private fun validateContent(
        endpoints: ContentSelectorEndpoints,
        manifest: PluginManifest,
    ) {
        if (
            listOf(
                endpoints.search,
                endpoints.story,
                endpoints.latest,
                endpoints.allChapters,
                endpoints.sync,
                endpoints.chapter,
            ).all { it == null }
        ) {
            emptyGroup("Content")
        }
        endpoints.search?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateSearch(it).getOrThrow()
        }
        endpoints.story?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateStory(it).getOrThrow()
        }
        endpoints.latest?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateReleases(it).getOrThrow()
        }
        endpoints.allChapters?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateReleases(it).getOrThrow()
        }
        endpoints.sync?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateSync(it).getOrThrow()
        }
        endpoints.chapter?.let {
            validateRequest(it.request, manifest)
            ContentSelectorValidation.validateChapter(it).getOrThrow()
        }
    }

    private fun validateRequest(
        request: SelectorRequestPlan,
        manifest: PluginManifest,
    ) {
        SelectorRequestPlanValidation.validate(request, manifest).getOrThrow()
    }

    private fun requireManifestKind(
        manifest: PluginManifest,
        kind: PluginKind,
    ) {
        if (kind !in manifest.kinds) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_DEFINITION,
                "Selector endpoint group is not declared by the manifest.",
            )
        }
    }

    private fun emptyGroup(name: String): Nothing = selectorFail(
        SelectorValidationErrorCode.EMPTY_ENDPOINT_GROUP,
        "$name endpoint group must not be empty.",
    )
}
