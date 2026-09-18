package app.universalmedia.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification task has no reusable output")
abstract class VerifySecurityBaselineTask : DefaultTask() {
    @get:Input
    abstract val manifestContents: MapProperty<String, String>

    @get:Input
    abstract val forbiddenPermissions: ListProperty<String>

    @get:Input
    abstract val appManifestKey: Property<String>

    @TaskAction
    fun verify() {
        val manifests = manifestContents.get()
        val forbidden = forbiddenPermissions.get()

        manifests.forEach { (modulePath, manifest) ->
            forbidden.forEach { permission ->
                check(permission !in manifest) {
                    "Forbidden Phase-0 permission in $modulePath/src/main/AndroidManifest.xml: $permission"
                }
            }
        }

        val appKey = appManifestKey.get()
        val appManifest = checkNotNull(manifests[appKey]) {
            "Missing app manifest input for $appKey"
        }

        check("android:allowBackup=\"false\"" in appManifest) {
            "Platform backup must stay disabled until an explicit extraction-rules matrix is approved."
        }
        check("android:usesCleartextTraffic=\"false\"" in appManifest) {
            "Cleartext traffic must be disabled by default."
        }

        val exportedTrue = manifests.values.sumOf { manifest ->
            Regex("""android:exported="true"""").findAll(manifest).count()
        }
        check(exportedTrue == 1) {
            "Expected exactly one exported=true component across main manifests; found $exportedTrue."
        }
        check(
            "android:name=\".MainActivity\"" in appManifest &&
                "android:exported=\"true\"" in appManifest,
        ) {
            "The only exported component must be the launcher MainActivity."
        }
    }
}
