package app.openstory.catalog.feature.artwork

import app.openstory.artwork.request.ArtworkAuthorityKey
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogAuthorityDescriptor
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.domain.source.CatalogCapabilitySet
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeHost
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogArtworkPolicyResolverTest {
    @Test
    fun policyLookupReadsDescriptorWithoutActivatingCatalog() {
        val sourceKey = CatalogSourceKey("mangaupdates")
        val activations = AtomicInteger()
        val host = object : CatalogRuntimeHost {
            override fun descriptor(sourceKey: CatalogSourceKey): CatalogAuthorityDescriptor? =
                CatalogAuthorityDescriptor(
                    sourceKey = sourceKey,
                    displayName = "MangaUpdates",
                    mediaTypes = setOf(CatalogMediaType.MANGA),
                    capabilities = CatalogCapabilitySet(discover = true, storyDetail = true),
                    artworkPolicy = SourceAssetPolicy(sourceKey, setOf("images.example")),
                )

            override fun authorityResolver() = CatalogAuthorityResolver { null }

            override suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation {
                activations.incrementAndGet()
                return CatalogCapabilityActivation.Unavailable()
            }

            override fun close() = Unit
        }

        val policy = catalogArtworkPolicyResolver(host)
            .policyFor(ArtworkAuthorityKey(sourceKey.value))

        assertEquals(setOf("images.example"), policy?.allowedHttpsHosts)
        assertEquals(0, activations.get())
    }
}
