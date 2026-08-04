plugins {
    id("openstory.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:common"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
}
