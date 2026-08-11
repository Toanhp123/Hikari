plugins {
    id("openstory.android.application")
    id("openstory.hilt")
    id("openstory.compose")
    alias(libs.plugins.kotlin.serialization)
}

val packageMyAnimeListPlugin by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir("../bundled-plugins/myanimelist-catalog")) {
        include("manifest.json", "main.js")
    }
    destinationDirectory.set(layout.projectDirectory.dir("src/main/assets/plugins"))
    archiveFileName.set("myanimelist-catalog.osp")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

val packageMangaDexPlugin by tasks.registering(Zip::class) {
    from(layout.projectDirectory.dir("../bundled-plugins/mangadex-content")) {
        include("manifest.json", "main.js")
    }
    destinationDirectory.set(layout.projectDirectory.dir("src/main/assets/plugins"))
    archiveFileName.set("mangadex-content.osp")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.named("preBuild") {
    dependsOn(packageMangaDexPlugin)
}

val myAnimeListClientId = providers.gradleProperty("openstory.malClientId")
    .orElse(providers.environmentVariable("OPENSTORY_MAL_CLIENT_ID"))
    .orElse("")

android {
    namespace = "app.openstory"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "app.openstory"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "MYANIMELIST_CLIENT_ID",
            "\"${myAnimeListClientId.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":catalog"))
    implementation(project(":library"))
    implementation(project(":chapters"))
    implementation(project(":reader"))
    implementation(project(":downloads"))
    implementation(project(":storage:room"))
    implementation(project(":storage:files"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:reader"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.client)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.javascriptengine)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
}
