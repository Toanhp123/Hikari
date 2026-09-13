package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder

class MergedManifestStartupVerifierTest {
    @Test
    fun rejectsUnclassifiedStartupProviderAndBackgroundService() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider
                  android:name="androidx.startup.InitializationProvider"
                  android:authorities="app.openstory.androidx-startup">
                  <meta-data
                    android:name="androidx.work.WorkManagerInitializer"
                    android:value="androidx.startup" />
                </provider>
                <service android:name="example.BackgroundService" />
              </application>
            </manifest>
        """.trimIndent()

        val violations = MergedManifestStartupVerifier.verify(
            xml,
            foundationTestPolicy(),
        )

        assertTrue(
            violations.any { it.code == "v2_manifest.initializer_unclassified" },
        )
        assertTrue(
            violations.any { it.code == "v2_manifest.service_forbidden" },
        )
    }

    @Test
    fun acceptsOnlyClassifiedProfileInstallerInitializer() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider
                  android:name="androidx.startup.InitializationProvider"
                  android:authorities="app.openstory.androidx-startup">
                  <meta-data
                    android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                    android:value="androidx.startup" />
                  <meta-data
                    android:name="example.ResourceBackedMetadata"
                    android:resource="@xml/example_config" />
                </provider>
              </application>
            </manifest>
        """.trimIndent()

        assertTrue(
            MergedManifestStartupVerifier.verify(
                xml,
                foundationTestPolicy(),
            ).isEmpty(),
        )
    }

    @Test
    fun rejectsStepOneNetworkAndNotificationPermissions() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <uses-permission android:name="android.permission.INTERNET" />
              <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
              <application />
            </manifest>
        """.trimIndent()

        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
            ),
            MergedManifestStartupVerifier.verify(xml, foundationTestPolicy())
                .filter { it.code == "v2_manifest.permission_forbidden" }
                .map { it.detail }
                .toSet(),
        )
    }

    @Test
    fun acceptsSingleActivityShellManifest() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application android:name="app.openstory.HikariApplication">
                <activity android:name="app.openstory.MainActivity" />
              </application>
            </manifest>
        """.trimIndent()

        assertTrue(
            MergedManifestStartupVerifier.verify(
                xml,
                foundationTestPolicy(),
            ).isEmpty(),
        )
    }

    @Test
    fun acceptsExplicitVariantScopedProvider() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider android:name="app.openstory.benchmark.BenchmarkDiagnosticsProvider" />
              </application>
            </manifest>
        """.trimIndent()

        assertTrue(
            MergedManifestStartupVerifier.verify(
                xml = xml,
                policy = foundationTestPolicy(),
                allowedProviders = setOf(
                    "app.openstory.benchmark.BenchmarkDiagnosticsProvider",
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun rejectsProviderOutsideAndroidxStartup() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider android:name="example.HiddenStartupProvider" />
              </application>
            </manifest>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundationViolation(
                    code = "v2_manifest.provider_forbidden",
                    detail = "example.HiddenStartupProvider",
                ),
            ),
            MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()),
        )
    }

    @Test
    fun rejectsAndroidxStartupProviderWithoutInitializerMetadata() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider android:name="androidx.startup.InitializationProvider" />
              </application>
            </manifest>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundationViolation(
                    code = "v2_manifest.initializer_missing",
                    detail = "androidx.startup.InitializationProvider",
                ),
            ),
            MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()),
        )
    }

    @Test
    fun rejectsUnclassifiedInitializerBesideAllowedInitializer() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider android:name="androidx.startup.InitializationProvider">
                  <meta-data
                    android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                    android:value="androidx.startup" />
                  <meta-data
                    android:name="androidx.lifecycle.ProcessLifecycleInitializer"
                    android:value="androidx.startup" />
                </provider>
              </application>
            </manifest>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundationViolation(
                    code = "v2_manifest.initializer_unclassified",
                    detail = "androidx.lifecycle.ProcessLifecycleInitializer",
                ),
            ),
            MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()),
        )
    }

    @Test
    fun rejectsInitializerMetadataWithoutValueOrResource() {
        val xml = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application>
                <provider android:name="androidx.startup.InitializationProvider">
                  <meta-data
                    android:name="androidx.lifecycle.ProcessLifecycleInitializer" />
                  <meta-data
                    android:name="androidx.profileinstaller.ProfileInstallerInitializer"
                    android:value="androidx.startup" />
                </provider>
              </application>
            </manifest>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundationViolation(
                    code = "v2_manifest.metadata_value_missing",
                    detail = "androidx.lifecycle.ProcessLifecycleInitializer",
                ),
            ),
            MergedManifestStartupVerifier.verify(xml, foundationTestPolicy()),
        )
    }

    @Test
    fun gradleTaskReportsAllMergedManifestViolations() {
        val root = createTempDirectory("merged-manifest-startup").toFile()
        try {
            val policyFile = File(root, "config/architecture/v2-foundation-policy.json")
            policyFile.parentFile.mkdirs()
            policyFile.writeText(
                """
                {
                  "schemaVersion": 1,
                  "maxProductionKotlinLines": 300,
                  "forbiddenSourceTokens": [],
                  "forbiddenBuildTokens": [],
                  "forbiddenBroadTypeSuffixes": [],
                  "forbiddenManifestPermissions": ["android.permission.INTERNET"],
                  "allowedStartupInitializers": []
                }
                """.trimIndent(),
            )
            val manifestFile = File(root, "build/intermediates/merged_manifest/AndroidManifest.xml")
            manifestFile.parentFile.mkdirs()
            manifestFile.writeText(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                  <uses-permission android:name="android.permission.INTERNET" />
                  <application>
                    <provider android:name="example.HiddenProvider" />
                    <service android:name="example.BackgroundService" />
                  </application>
                </manifest>
                """.trimIndent(),
            )
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.register(
                "verifyMergedManifestStartup",
                VerifyMergedManifestStartupTask::class.java,
            ).get().apply {
                this.policyFile.set(policyFile)
                mergedManifest.set(manifestFile)
            }

            val error = assertFailsWith<GradleException> {
                task.verifyManifest()
            }

            assertTrue("v2_manifest.permission_forbidden" in error.message.orEmpty())
            assertTrue("v2_manifest.provider_forbidden" in error.message.orEmpty())
            assertTrue("v2_manifest.service_forbidden" in error.message.orEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
