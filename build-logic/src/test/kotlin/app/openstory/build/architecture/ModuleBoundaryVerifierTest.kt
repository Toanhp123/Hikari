package app.openstory.build.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBoundaryVerifierTest {
    @Test
    fun designSystemIsAProjectIndependentAndroidUiFoundation() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        val designSystem = policy.modules.getValue(":core:designsystem")

        assertEquals(ModulePlatform.ANDROID_LIBRARY, designSystem.platform)
        assertTrue(designSystem.productionDependencies.isEmpty())
        assertTrue(designSystem.testDependencies.isEmpty())
        assertTrue(
            ":core:designsystem" in
                policy.modules.getValue(":app").productionDependencies,
        )
    }

    @Test
    fun currentPolicyContainsR2Boundaries() {
        val policy = ModuleBoundaryPolicyLoader.load(File("../config/architecture/module-boundaries.json"))
        assertTrue(":plugins:api" in policy.modules)
        assertTrue(":plugins:runtime" in policy.modules)
        assertTrue(":catalog" in policy.modules)
        assertEquals(
            setOf(":core:common", ":plugins:api", ":plugins:runtime"),
            policy.modules.getValue(":catalog").productionDependencies.toSet(),
        )
    }
}
