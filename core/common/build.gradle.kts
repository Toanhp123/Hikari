plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}