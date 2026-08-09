plugins {
    id("openstory.android.application")
    id("openstory.hilt")
    id("openstory.compose")
    alias(libs.plugins.kotlin.serialization)
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

}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:plugin-api"))
    implementation(project(":core:plugin-host"))
    implementation(project(":core:matching"))
    implementation(project(":feature:home"))
    implementation(project(":feature:story"))
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
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.room.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.javascriptengine)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
}
