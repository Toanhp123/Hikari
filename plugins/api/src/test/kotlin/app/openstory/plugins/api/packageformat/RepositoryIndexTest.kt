package app.openstory.plugins.api.packageformat

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RepositoryIndexTest {
    private fun artifact(
        sha256: String = "a".repeat(64),
        url: String = "https://plugins.example/p.osp",
        signature: String? = null,
    ) = PluginArtifact("org.example.plugin", "1.0.0", url, sha256, signature)

    @Test
    fun invalidSha256IsRejected() {
        assertFailsWith<IllegalArgumentException> { artifact(sha256 = "not-a-sha") }
    }

    @Test
    fun artifactUrlMustBeHttps() {
        assertFailsWith<IllegalArgumentException> { artifact(url = "http://plugins.example/p.osp") }
    }

    @Test
    fun duplicatePluginVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            RepositoryIndex(schema = 1, artifacts = listOf(artifact(), artifact()))
        }
    }

    @Test
    fun blankSignatureIsRejected() {
        assertFailsWith<IllegalArgumentException> { artifact(signature = " ") }
    }
}
