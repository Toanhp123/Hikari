plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.plugin.host"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncy.castle.provider)
    implementation(libs.jsoup)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
