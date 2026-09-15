package app.openstory.catalog.feature.artwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.artwork.policy.ArtworkPolicy
import app.openstory.artwork.policy.ArtworkPolicyResolver
import app.openstory.artwork.request.ArtworkAuthorityKey
import app.openstory.artwork.runtime.ArtworkLoader
import app.openstory.artwork.runtime.ArtworkRuntime
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.VariantCatalogBinding
import app.openstory.catalog.feature.runtime.CatalogRuntimeAccess
import app.openstory.catalog.runtime.CatalogRuntimeHost
import app.openstory.common.execution.ProcessWorkAdmission

@Composable
fun rememberCatalogArtworkLoader(
    runtimeAccess: CatalogRuntimeAccess,
    admission: ProcessWorkAdmission,
): ArtworkLoader {
    val applicationContext = LocalContext.current.applicationContext
    val factory = remember(applicationContext, runtimeAccess) {
        CatalogArtworkRuntimeHolder.factory {
            ArtworkRuntime(
                context = applicationContext,
                localResolver = VariantLocalCoverAssets,
                policyResolver = catalogArtworkPolicyResolver(runtimeAccess.holder.runtime),
                admission = admission,
                remoteTransport = VariantCatalogBinding.artworkTransport(applicationContext),
                callbacks = VariantCatalogBinding.diagnostics.artworkRuntimeCallbacks,
            )
        }
    }
    return viewModel<CatalogArtworkRuntimeHolder>(factory = factory).runtime
}

internal class CatalogArtworkRuntimeHolder(
    val runtime: ArtworkRuntime,
) : ViewModel() {
    override fun onCleared() = runtime.close()

    companion object {
        fun factory(createRuntime: () -> ArtworkRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CatalogArtworkRuntimeHolder::class.java))
                    return CatalogArtworkRuntimeHolder(createRuntime()) as T
                }
            }
    }
}

internal fun catalogArtworkPolicyResolver(
    runtime: CatalogRuntimeHost,
): ArtworkPolicyResolver = ArtworkPolicyResolver { authority ->
    val sourceKey = runCatching { CatalogSourceKey(authority.value) }.getOrNull()
        ?: return@ArtworkPolicyResolver null
    runtime.descriptor(sourceKey)?.artworkPolicy?.let { policy ->
        ArtworkPolicy(
            authority = ArtworkAuthorityKey(policy.catalogSourceKey.value),
            allowedHttpsHosts = policy.allowedHttpsHosts,
        )
    }
}
