plugins {
    id("openstory.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
}
