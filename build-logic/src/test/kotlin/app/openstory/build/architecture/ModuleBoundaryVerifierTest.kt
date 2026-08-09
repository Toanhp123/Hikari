package app.openstory.build.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBoundaryVerifierTest {
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
