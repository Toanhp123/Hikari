plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:plugin-api"))
    implementation(libs.okhttp.client)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
