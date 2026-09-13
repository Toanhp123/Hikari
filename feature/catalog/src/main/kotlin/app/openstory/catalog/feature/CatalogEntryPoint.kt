package app.openstory.catalog.feature

import androidx.compose.runtime.Composable
import app.openstory.catalog.domain.model.CatalogMediaType

@Composable
fun CatalogRootEntryPoint(mediaType: CatalogMediaType) {
    CatalogComposition(mediaType)
}
