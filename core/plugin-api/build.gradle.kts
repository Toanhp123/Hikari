plugins {
    id("openstory.kotlin.jvm")
    id("java-test-fixtures")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(libs.kotlinx.serialization.json)

    testImplementation(project(":test:fixtures"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
