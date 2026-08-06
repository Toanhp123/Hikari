plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.plugin.host"
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncy.castle.provider)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
