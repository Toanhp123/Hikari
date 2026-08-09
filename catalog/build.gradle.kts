plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
