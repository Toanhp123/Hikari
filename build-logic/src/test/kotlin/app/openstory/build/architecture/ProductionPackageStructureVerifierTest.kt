package app.openstory.build.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductionPackageStructureVerifierTest {
    @Test
    fun acyclicPackagesAreAccepted() {
        val violations = ProductionPackageStructureVerifier.verify(
            mapOf(
                ":catalog:domain" to mapOf(
                    "Model.kt" to """
                        package app.openstory.catalog.domain.model

                        import app.openstory.catalog.domain.limits.CatalogLimits
                    """.trimIndent(),
                    "Limits.kt" to """
                        package app.openstory.catalog.domain.limits

                        object CatalogLimits
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(emptyList(), violations)
    }

    @Test
    fun packageCycleIsRejected() {
        val violations = ProductionPackageStructureVerifier.verify(
            mapOf(
                ":catalog:runtime" to mapOf(
                    "A.kt" to """
                        package app.openstory.catalog.runtime.a

                        import app.openstory.catalog.runtime.b.B
                    """.trimIndent(),
                    "B.kt" to """
                        package app.openstory.catalog.runtime.b

                        import app.openstory.catalog.runtime.a.A
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(
            listOf(
                ArchitectureViolation(
                    code = "step2_structure.package_cycle",
                    module = ":catalog:runtime",
                    detail =
                        "packages=app.openstory.catalog.runtime.a," +
                            "app.openstory.catalog.runtime.b",
                ),
            ),
            violations,
        )
    }
}
