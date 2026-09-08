plugins {
    alias(libs.plugins.androidx.baselineprofile)
    id("openstory.android.application")
    id("openstory.compose")
    id("openstory.foundation")
}

android {
    namespace = "app.openstory"

    defaultConfig {
        applicationId = "app.openstory"
        versionCode = 1
        versionName = "2.0-step1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".v2dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
        }
        create("benchmarkRelease") {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".v2benchmark"
        }
        create("nonMinifiedRelease") {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".v2benchmark"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
                manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
            }
        }
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

dependencies {
    "baselineProfile"(project(":benchmark"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
