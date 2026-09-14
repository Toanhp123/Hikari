package app.openstory.build.architecture

import java.io.File

internal object Step3CatalogVariantSurfaceVerifier {
    fun verify(root: File): List<ArchitectureViolation> =
        CatalogVariantSurfaceVerifier.verify(
            root = root,
            releasePolicy = CatalogReleaseSurfacePolicy.LIVE_STEP_THREE,
        )
}
