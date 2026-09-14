plugins {
    id("openstory.android.library")
    id("openstory.compose")
}

android {
    namespace = "app.openstory.story.feature"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("benchmarkRelease")) { variantBuilder ->
        variantBuilder.hostTests["UnitTest"]?.enable = true
    }
    finalizeDsl { extension ->
        listOf("benchmarkRelease", "nonMinifiedRelease").forEach { sourceSetName ->
            extension.sourceSets.getByName(sourceSetName).apply {
                kotlin.directories.add("src/benchmarkRelease/kotlin")
                res.srcDir("src/benchmarkRelease/res")
                manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
            }
        }
    }
}

dependencies {
    implementation(project(":catalog:domain"))
    implementation(project(":catalog:runtime"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
